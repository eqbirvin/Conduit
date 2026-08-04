# Conduit — Code Audit & Architecture Review

**Auditor role:** Senior Android developer review
**Scope:** `Antigravity/app/src/main/` (v2.02.02, versionCode 183) — read-only audit; no project files were modified
**Focus:** Performance, efficiency, and best practices
**Date:** 2026-07-11

---

## 1. Code Audit & Grading

### Overall Grade: **C** — "Impressive feature velocity on a foundation that won't scale"

Conduit is a genuinely ambitious app: a notification hub with a Room-backed log, swipe actions, reply-from-app via `RemoteInput`, a home-screen widget with action chips, an overlay "Bracket/Hanger", bubbles, work-profile support, and per-channel filtering. For an AI-IDE-built app, the *feature completeness* is remarkable, and several things are done right:

**What's good**

- Room + Flow + `collectAsState` for the notification list (`MainActivity.kt:277-278`) — reactive persistence is the correct pattern.
- Deduplication logic in the listener is thoughtful: group-summary suppression (`HubNotificationListenerService.kt:696`), a `Mutex` around insert-or-update (`HubNotificationListenerService.kt:800`), self-reply interception (`HubNotificationListenerService.kt:724-768`).
- `remember(...)` is used to memoize derived lists in composition (`MainActivity.kt:760`, `1147-1155`), and `derivedStateOf` appears where appropriate (`MainActivity.kt:2828`).
- Caching intent exists (`actionCache`, `contentIntentCache` at `HubNotificationListenerService.kt:75-76`; `appLabelCache`/`appIconCache` at `ProfileUtils.kt:14-15`) — the instinct is right, the implementations are incomplete (see §2/§3).
- KSP (not KAPT) for Room (`app/build.gradle.kts:4,72`), edge-to-edge, Material 3, dynamic color.

**What drags the grade down**

| Area | Grade | Why |
|---|---|---|
| Architecture | **D+** | No ViewModel, no repository, no layering. `MainActivity.kt` is a 3,506-line god-file holding ~30 `mutableStateOf` prefs, all four screens, DB writes, and business logic directly inside composables. |
| Concurrency | **D** | `kotlinx.coroutines.GlobalScope.launch` for database writes in at least 8 places (`MainActivity.kt:119, 136, 338, 344, 350, 798, 811, 833, 1018`) plus ad-hoc `CoroutineScope(Dispatchers.IO).launch` (`MainActivity.kt:2111`, `ConduitWidgetProvider.kt:44`, `ConduitWidgetClickActivity.kt:29`). Unstructured, uncancellable, swallows failures. |
| UI ↔ Service coupling | **D** | The UI reaches into a static service singleton (`HubNotificationListenerService.instance`, declared `HubNotificationListenerService.kt:43`) from inside composables and performs **synchronous binder IPC during composition** (`MainActivity.kt:1794-1799`, `1834`, `2007`). This is the direct cause of the scrolling bug (§3). |
| Data layer | **C-** | Correct Room usage, but: `fallbackToDestructiveMigration()` (`AppDatabase.kt:23`) silently wipes user data on every schema bump; **zero indices** on a table queried by `notificationKey`, `packageName`, `title`, `text`, `isArchived` (`HubNotification.kt`, `NotificationDao.kt:55-62`); no pruning — the table grows forever and every query loads the entire history into memory. |
| Performance | **C-** | Multiple main-thread IPC calls per list item; unbounded full-resolution icon bitmap cache; `SimpleDateFormat` allocated per item per frame; O(n) `contains` in headers. Details in §2 and §3. |
| Build config | **C** | `isMinifyEnabled = false` in release (`app/build.gradle.kts:26`) — unshrunken Compose release builds are measurably slower and much larger. Compose BOM 2023.10.01 / compiler 1.5.4 is ~2.5 years stale and pins you to deprecated APIs (`SwipeToDismiss`, `animateItemPlacement`, `rememberRipple`). No baseline profile. |
| Testing | **F** | Only template dependencies; no actual unit or UI tests exist for dedup logic, block rules, or DAO queries — exactly the code that keeps regressing in notification apps. |

**Bottom line:** the app works because the dataset is small and modern phones are fast. Every structural shortcut taken here (God-activity, GlobalScope, service-singleton IPC in composition, unindexed full-table reads) has a compounding cost, and the scrolling bug you're seeing is the first of these bills coming due.

---

## 2. Areas for Improvement

Ordered by user-visible impact.

### 2.1 Main-thread binder IPC inside composition (Critical)

The single worst pattern in the codebase. Three separate call sites do cross-process calls on the UI thread while the list is composing:

1. **`MainActivity.kt:1794-1799`** (`NotificationItem`) — `getReplyAction()` / `getNotificationActions()` each can trigger a full `activeNotifications` dump from `system_server`. Covered in depth in §3.
2. **`MainActivity.kt:2007`** — `remember { mutableStateOf(getAppLabel(context, ...)) }` runs `getAppLabel` **synchronously** on first composition of every item. On cache miss, `getAppLabel` (`ProfileUtils.kt:45-74`) iterates all user profiles and calls `LauncherApps.getActivityList()` — binder IPC — per profile. The `LaunchedEffect` right below it (`MainActivity.kt:2009-2016`) was clearly added to make this async, but the synchronous call at line 2007 still runs first and defeats the purpose.
3. **`MainActivity.kt:1419-1421` and `:774`** — `getRepresentativePackage()` (`ProfileUtils.kt:116-128`) calls `isPackageInstalled()` → `LauncherApps.isPackageEnabled()` IPC per candidate package per user profile, and its results are **never cached**. It runs inside `remember(notifications)` `groupBy` (once per notification on every list change) and inside the dock-filter path (once per notification per filter change).

**Fix:** cache `getRepresentativePackage` results in a `ConcurrentHashMap<String, String>` beside `appLabelCache`; delete the synchronous `getAppLabel` call at line 2007 (initialize from `appLabelCache[packageName]` only, let the `LaunchedEffect` fill it); and fix the action lookup per §3.

### 2.2 Heavy work on every notification posted device-wide

`onNotificationPosted` (`HubNotificationListenerService.kt:692-848`) ends with `updatePersistentNotification()` at line 847 — **outside** the supported-channel check, so it runs for *every notification from every app on the phone*. `updatePersistentNotification` (`HubNotificationListenerService.kt:1425-1522`) then:

- calls `activeNotifications` (full IPC dump, line 1433),
- allocates a `Bitmap` + `Canvas` and draws a badge **per package** (lines 1472-1488),
- rebuilds `RemoteViews` and re-notifies.

All of this on the process main thread (NLS callbacks are delivered on the main looper). A burst of 10 notifications = 10 full rebuilds. **Fix:** early-return when the package isn't a supported channel, debounce rebuilds (e.g., 500 ms coalescing via the existing `handler`), and cache the rendered badge bitmaps per (package, count).

### 2.3 Unbounded database + unindexed queries

- `HubNotification.kt:6-19` — no `indices` on the entity, yet `NotificationDao.kt:22` filters by `notificationKey`, `:55-59` by `packageName`/`title`/`text`, and both list queries filter by `isArchived` and sort by timestamps. `getMostRecentExactMatch` (`NotificationDao.kt:58-59`) runs a full-table scan **on every intercepted notification** (`HubNotificationListenerService.kt:802`).
- Nothing ever deletes old rows. `getAllNotifications()`/`getArchivedNotifications()` load the entire history into memory and the unified view then concatenates and re-sorts both lists in composition (`MainActivity.kt:770, 777`).
- `AppDatabase.kt:23` — `fallbackToDestructiveMigration()` deletes the user's entire notification log on any version bump. For an app whose whole value proposition is "your notification history," this is the most dangerous line in the project.

**Fix:** add `@Entity(indices = [Index("notificationKey"), Index("isArchived", "timestamp"), Index("packageName")])`; add a retention job (e.g., delete archived rows older than 30 days on service connect); write real `Migration`s.

### 2.4 Recomposition hygiene

- **`channelStates.toMap()` allocated on every recomposition** — `MainActivity.kt:281, 292, 321, 1404`. Each `toMap()` builds a fresh map; passing it as a parameter (`:321`) makes `HubScreen`'s inputs unstable, so the whole screen recomposes whenever the parent does. Pass the `SnapshotStateMap` itself (Compose observes per-key reads) or hoist an immutable map into a single `remember`.
- **`selectedIds.contains(...)` read in every item** (`MainActivity.kt:1204, 1377`) — toggling one checkbox invalidates every visible row. Acceptable at this scale, but pass `isSelected` as a primitive and keep lambdas stable if lists grow.
- **`notifications.contains(it)`** in the sticky-header badge (`MainActivity.kt:1242`) — O(n) structural equality per header item per composition. Precompute a `Set` of unarchived IDs.
- **`SimpleDateFormat` allocated per call** (`MainActivity.kt:2179-2192`), and `formatTimestamp` runs in every `NotificationItem` composition; `formatDateHeader` runs once per item inside `groupBy` (`:1154, 3383`). Hoist the three formatters to top-level `val`s (or use `android.text.format.DateUtils`).
- **Per-item `SharedPreferences` reads** (`MainActivity.kt:1801-1803`) — hoist `smart_mark_read` / target to `HubScreen` and pass down.
- **`prefs.getString("fab_configs", null)` executes on every recomposition** of the root (`MainActivity.kt:231` — note it's read *outside* `remember`; only the parse result is remembered).
- **`items(dockPackagesList.size)`** in the dock `LazyRow` (`MainActivity.kt:1485`) uses positional items with no keys — reorderings recompose every icon.

### 2.5 Icon/bitmap memory

`appIconCache` (`ProfileUtils.kt:15`) stores **full-resolution** `ImageBitmap`s in an unbounded `ConcurrentHashMap` for every app ever displayed. `AppIcon` (`MainActivity.kt:2195-2235`) renders them at 28-50 dp but caches the intrinsic-size bitmap (launcher icons are commonly 192-432 px). **Fix:** convert to an `LruCache` (or cap ~50 entries) and downscale with `drawable.toBitmap(width, height)` at the largest size you actually render.

### 2.6 Concurrency structure

Replace every `GlobalScope.launch` (see §1 list) and ad-hoc `CoroutineScope(Dispatchers.IO)` with:
- a `ViewModel` + `viewModelScope` for UI-triggered writes,
- the service's existing `scope` (`HubNotificationListenerService.kt:72`) for service work — note that scope itself leaks: it's never cancelled in `onDestroy` (`:178-187`),
- `goAsync()` or `WorkManager` for `ConduitWidgetProvider` work instead of a fire-and-forget scope in a `BroadcastReceiver` (`ConduitWidgetProvider.kt:44`) — the process can be killed before the coroutine finishes.

Also: `MainActivity.kt:833-859` runs `updateAllWidgets(context)` **inside the per-id `forEach`** — archiving 20 selected notifications triggers 20 full widget rebuilds. Move it after the loop.

### 2.7 Build & release configuration

- `app/build.gradle.kts:26` — enable `isMinifyEnabled = true` (+ `isShrinkResources`) for release. Unminified Compose ships tens of thousands of unused methods and skips R8's Compose-specific optimizations.
- Upgrade Compose BOM (2023.10.01 → current), which unblocks: `SwipeToDismissBox` (replacing deprecated `SwipeToDismiss`, `MainActivity.kt:1296`), `Modifier.animateItem()` (replacing `animateItemPlacement`, `:1194, 1298, 3418`), `material3.ripple` (replacing `material.rememberRipple`, `:1906`).
- Add a Baseline Profile module — for a scroll-heavy Compose app this alone typically wins 20-30 % on first-scroll jank.
- `AndroidManifest.xml:9` — `QUERY_ALL_PACKAGES` will be rejected by Play review for this use case; you already declare an explicit `<queries>` list (`:13-41`) and hold notification-listener access (which grants package visibility). Remove it.
- Version string `"2.02.02"` is hard-coded in three places (`MainActivity.kt:197, 499, 566`) — use `BuildConfig.VERSION_NAME`.

### 2.8 Robustness details

- `HubNotificationListenerService.kt:162-167` — listening for `ACTION_CLOSE_SYSTEM_DIALOGS` is non-functional on API 31+ (system-only broadcast); the Hanger relies on `onWindowFocusChanged` anyway.
- Fragile persistence formats: FAB configs serialized as `,`/`|`-joined strings (`MainActivity.kt:236-238, 261`) and block rules as `pkg|TYPE|pattern` (`:1624`) — any user text containing a delimiter corrupts the record. Use `kotlinx.serialization` JSON.
- `MainActivity.kt:764-765` — `notif.title.toString()` on a `String?` produces the literal `"null"`, so searching "null" matches titles that are absent. Use `orEmpty()`.
- Channel-to-icon dispatch via a 12-line hardcoded uppercase-string `if` (`MainActivity.kt:1919-1931`) duplicates knowledge already in `supportedApps` — derive it from the map.
- `catch (e: Exception) { e.printStackTrace() }` appears ~40 times across the codebase — at minimum route through `Log.e` with a tag so field debugging is possible.

---

## 3. The Scrolling Bug — laggy scrolling with action chips enabled

### Symptom
Scrolling the notification log stutters/hitches, notably when "Show Action Chips" is on.

### Root cause: a synchronous cross-process call per list item, during composition, that is never cached for exactly the items that dominate your list

Every `NotificationItem` does this on first composition (i.e., **every time a row scrolls into view**, because LazyColumn disposes and recreates off-screen items):

```kotlin
// MainActivity.kt:1794-1799
val replyAction = remember(notification.notificationKey) {
    HubNotificationListenerService.instance?.getReplyAction(notification.notificationKey)
}
val allActions = remember(notification.notificationKey) {
    HubNotificationListenerService.instance?.getNotificationActions(notification.notificationKey)
}
```

`getNotificationActions` (`HubNotificationListenerService.kt:1020-1053`) checks `actionCache`, and on a miss calls:

```kotlin
// HubNotificationListenerService.kt:1027
val active = activeNotifications
```

`NotificationListenerService.activeNotifications` is **not a field read** — it is a blocking binder transaction to `system_server` that parcels and unparcels *every active notification on the device*, including their extras bundles and icons. It routinely costs several milliseconds to tens of milliseconds. You have a 16.6 ms (or 8.3 ms at 120 Hz) frame budget.

Three compounding defects turn this into visible jank:

1. **Negative results are never cached.** Look at the cache write path (`HubNotificationListenerService.kt:1044-1046`): `actionCache[key] = result` only executes **when the key is found** in the active list. Your default view is the *unified* view (`unified_view` defaults to `true`, `MainActivity.kt:225`), which merges archived history into the list (`MainActivity.kt:770, 777`). Archived/dismissed notifications are, by definition, **not** in `activeNotifications` — so the loop scans the whole dump, finds nothing, returns `null` at line 1052, and caches nothing. The next time that row (or any other archived row) composes, the full IPC dump happens again.

2. **It happens twice per row.** `getReplyAction` (`:1015-1018`) internally calls `getNotificationActions`, and then the second `remember` block calls it again. For an uncached (archived) item that is **two full `activeNotifications` dumps + two linear scans per row**, on the main thread, during scroll. Fling through 30 history items → ~60 binder dumps.

3. **The cache is frequently invalidated.** `onNotificationPosted` removes the key (`HubNotificationListenerService.kt:703-704`) — correct for freshness, but combined with (1) it means the cache only ever helps for currently-active notifications.

The chips toggle correlates with the lag because chips make the cost *matter more* (taller items → more incremental composition per frame; up to four extra clickable `Surface`s per row via the `FlowRow` at `MainActivity.kt:2069-2132`; plus the `hasNativeMarkRead` scan at `:1805-1810`) — but note the expensive lookups at `:1794-1799` run **even when `showActionChips` is false**. You are paying most of this cost with chips off, too.

Two secondary contributors on the same code path, per §2.1: the synchronous `getAppLabel` IPC at `MainActivity.kt:2007`, and the `rememberDismissState`/`SwipeToDismiss` machinery from the outdated Material library (`:1261-1296`).

### The concrete fix

**Step 1 — Make the service cache authoritative, populated at post time (eliminates IPC entirely).**
Inside `onNotificationPosted` you already hold the `StatusBarNotification` — its actions are right there, no IPC needed. Instead of *removing* the cache entry (`HubNotificationListenerService.kt:703`), **write** it:

```kotlin
// in onNotificationPosted, replacing actionCache.remove(notificationKey) at line 703:
val list = mutableListOf<Notification.Action>()
it.notification.actions?.let { a -> list.addAll(a) }
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    it.notification.extras
        .getParcelableArrayList<Notification.Action>("android.contextualActions")
        ?.let { c -> list.addAll(c) }
}
actionCache[notificationKey] = list
```

And in `onNotificationRemoved` (`:873-886`), replace the entry with an empty list (`actionCache[it.key] = emptyList()`) rather than leaving it stale. Then `getNotificationActions` becomes a **pure map lookup** — delete the `activeNotifications` fallback loop at `:1026-1048`, or keep it only as a one-time warm-up executed on `onListenerConnected` (build the whole map from one dump, on `scope`, not per-key on the UI thread).

**Step 2 — Cache the miss.** Whatever remains of the lookup path must record negatives: before returning at `HubNotificationListenerService.kt:1052`, add `actionCache[key] = emptyList()`. This one line fixes the pathological archived-item case even if you change nothing else.

**Step 3 — Hoist the lookup out of per-item composition.** In `HubScreen`, resolve actions once per data change and pass them down, so `NotificationItem` (`MainActivity.kt:1779`) becomes a pure function of its inputs:

```kotlin
// HubScreen, alongside displayNotifications (MainActivity.kt:760):
val actionsByKey = remember(displayNotifications, showActionChips) {
    if (!showActionChips) emptyMap()
    else displayNotifications.associate {
        it.notificationKey to
            HubNotificationListenerService.instance?.getNotificationActions(it.notificationKey)
    }
}
// NotificationItem gains: allActions: List<Notification.Action>?  parameter
```

With Step 1 in place this map is built from memory in microseconds; and gating on `showActionChips` means the toggle now genuinely disables the work, not just the pixels.

**Step 4 — Verify.** Enable "Profile HWUI rendering" or use Macrobenchmark's `FrameTimingMetric` on the log screen, fling with chips enabled: before the fix you'll see frame spikes aligned with row entry; after, the bars should sit under budget. Also check `adb shell dumpsys gfxinfo com.conduit.app` janky-frame percentage before/after.

> Note: the widget has the same latent defect — `ConduitWidgetService.kt:98` calls `getNotificationActions` per row in `getViewAt`. It's tolerable there because `RemoteViewsFactory` runs on a binder thread, but Step 1 fixes it for free.

---

## 4. Agent Rules

Copy-paste these into your Antigravity agent system prompts (Gemini Pro/Flash, Claude Sonnet, or OpenAI models). They are written as hard constraints because coding agents follow imperatives far more reliably than suggestions.

```markdown
# Android Engineering Rules (Non-Negotiable)

## Architecture
1. NEVER put business logic, database access, or service calls inside an Activity or
   @Composable. Use ViewModel + repository. Composables receive plain state and emit
   events via lambdas — nothing else.
2. Any file exceeding 500 lines MUST be split before you add more code to it. One
   screen = one file. Shared components get their own files.
3. UI code MUST NOT reference a Service singleton (e.g., `MyService.instance`).
   Expose service data through a repository backed by a Flow, StateFlow, or Room.
4. Screens with more than 8 parameters MUST take a single immutable UiState data
   class plus at most a few event lambdas.

## Concurrency
5. NEVER use GlobalScope or create ad-hoc CoroutineScope(...) instances. Use
   viewModelScope, lifecycleScope, a scope owned and cancelled by the component,
   or WorkManager for fire-and-forget work that must survive the process.
6. NEVER perform disk I/O, SharedPreferences writes-then-reads, binder/IPC calls
   (PackageManager, LauncherApps, NotificationListenerService.activeNotifications,
   AppWidgetManager), or bitmap decoding on the main thread. If an API blocks,
   wrap it in withContext(Dispatchers.IO) and cache the result.
7. In a BroadcastReceiver, never launch a coroutine that outlives onReceive without
   goAsync() or WorkManager.

## Jetpack Compose Performance
8. NEVER make IPC, database, file, or SharedPreferences calls inside composition —
   including inside remember { } blocks of list items. remember only memoizes per
   composition; LazyColumn items are disposed off-screen, so the cost repeats on
   every scroll. Resolve data BEFORE composition (ViewModel) and pass it as
   parameters.
9. Every LazyColumn/LazyRow items() call MUST provide a stable key.
10. Do not create new collections (toMap(), toList(), sortedBy) in composable
    bodies on every recomposition — wrap derivations in remember(keys) or
    derivedStateOf, and pass immutable/stable types as parameters.
11. Hoist SharedPreferences reads, date formatters, and system-service lookups out
    of per-item composables. A list item composable must be a pure function of its
    parameters.
12. Expensive text formatting (SimpleDateFormat, AnnotatedString building) must be
    cached in remember with proper keys, and formatter instances must be allocated
    once, not per call.

## Data Layer
13. Every Room entity MUST declare indices for every column used in a WHERE or
    ORDER BY clause.
14. NEVER ship fallbackToDestructiveMigration() in release code. Write Migrations.
15. Any append-only table MUST have a retention/pruning policy from day one.
16. Do not load unbounded tables into memory; use LIMIT or Paging when a table can
    exceed a few hundred rows.
17. Persist structured data as JSON via kotlinx.serialization — never as
    hand-joined delimiter strings.

## Caching & Memory
18. Bitmap/drawable caches MUST be bounded (LruCache) and store images at the
    display size, not intrinsic size.
19. Cache negative lookups too. A cache that only stores hits does nothing for the
    dominant miss path.
20. Results of cross-process lookups (app labels, icons, package installed state)
    MUST be cached in memory for the process lifetime.

## Build & Release
21. Release builds MUST set isMinifyEnabled = true and isShrinkResources = true,
    with tested ProGuard rules.
22. Keep Compose BOM, AGP, and Kotlin within one year of current. NEVER introduce
    new code using APIs already deprecated in the project's own dependency versions.
23. Reference BuildConfig.VERSION_NAME — never hard-code version strings in UI/logic.
24. Do not request QUERY_ALL_PACKAGES or other broad permissions when a <queries>
    declaration or an already-held privileged role suffices.

## Correctness & Hygiene
25. NEVER write `catch (e: Exception) { e.printStackTrace() }`. Catch the narrowest
    type, log with Log.e(TAG, msg, e), and decide deliberately whether to rethrow,
    recover, or surface to the user.
26. Guard nullable-to-string conversions: use orEmpty()/?: — `nullable.toString()`
    producing "null" is a bug.
27. Event-driven system callbacks (onNotificationPosted, onReceive, sensors) MUST
    filter irrelevant events FIRST and debounce expensive downstream work
    (widget refreshes, RemoteViews rebuilds).
28. When work is triggered inside a loop (e.g., archiving N items), side effects
    like widget updates run ONCE after the loop, not per iteration.
29. For every feature that renders a scrolling list, state the frame budget
    (16.6 ms) and verify: no per-item IPC, keyed items, bounded item work. If you
    cannot verify, say so explicitly in your summary.
30. Never mark a performance issue "fixed" without naming the measurement that
    would prove it (Macrobenchmark FrameTimingMetric, gfxinfo jank %, or profiler
    trace).
```

---

*Report generated by Claude (Fable 5) — audit only; no project files were modified.*
