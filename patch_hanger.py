import re

with open('app/src/main/java/com/conduit/app/HubNotificationListenerService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add sealed class HangerItem and update pendingIntentList
item_class_code = """
    sealed class HangerItem {
        data class BundleItem(val packageName: String) : HangerItem()
        data class NotificationItem(val pendingIntent: android.app.PendingIntent?, val packageName: String) : HangerItem()
    }
    private var pendingIntentList = mutableListOf<HangerItem>()
    private var expandedBundlePackageName: String? = null
    private val expandBundleRunnable = Runnable {
        val index = hoverIndex
        if (index >= 0 && index < pendingIntentList.size) {
            val item = pendingIntentList[index]
            if (item is HangerItem.BundleItem) {
                if (expandedBundlePackageName != item.packageName) {
                    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val manager = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                        manager.defaultVibrator
                    } else {
                        getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else {
                        vibrator.vibrate(50)
                    }
                    expandedBundlePackageName = item.packageName
                    populateHangerNotifications()
                }
            }
        }
    }
"""

content = re.sub(
    r'private var pendingIntentList = mutableListOf<Pair<PendingIntent\?, String>>\(\)',
    item_class_code.strip(),
    content
)

# 2. Update ACTION_UP to handle HangerItem
action_up_old = """
                                val item = pendingIntentList[hoverIndex]
                                if (item.first != null) {
                                    item.first!!.send(this@HubNotificationListenerService, 0, null, null, null, null, getBasBundle())
                                } else {
                                    throw Exception("No intent")
                                }
                            } catch (e: Exception) {
                                val pkg = pendingIntentList[hoverIndex].second
                                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
"""

action_up_new = """
                                val item = pendingIntentList[hoverIndex]
                                if (item is HangerItem.BundleItem) {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(launchIntent)
                                    }
                                } else if (item is HangerItem.NotificationItem) {
                                    if (item.pendingIntent != null) {
                                        item.pendingIntent.send(this@HubNotificationListenerService, 0, null, null, null, null, getBasBundle())
                                    } else {
                                        throw Exception("No intent")
                                    }
                                }
                            } catch (e: Exception) {
                                val pkg = if (pendingIntentList[hoverIndex] is HangerItem.BundleItem) (pendingIntentList[hoverIndex] as HangerItem.BundleItem).packageName else (pendingIntentList[hoverIndex] as HangerItem.NotificationItem).packageName
                                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
"""

content = content.replace(action_up_old.strip(), action_up_new.strip())

# 3. Update hoverState to trigger expandBundleRunnable
hover_state_old = """
        if (newHoverIndex != hoverIndex) {
            if (newHoverIndex != -1) {
"""

hover_state_new = """
        if (newHoverIndex != hoverIndex) {
            handler.removeCallbacks(expandBundleRunnable)
            if (newHoverIndex != -1) {
                val item = pendingIntentList.getOrNull(newHoverIndex)
                if (item is HangerItem.BundleItem && expandedBundlePackageName != item.packageName) {
                    handler.postDelayed(expandBundleRunnable, 400)
                }
"""

content = content.replace(hover_state_old.strip(), hover_state_new.strip())

# 4. In closeHanger, clear expandedBundlePackageName
close_hanger_old = """
        hangerLinearLayout?.removeAllViews()
        pendingIntentList.clear()
"""

close_hanger_new = """
        hangerLinearLayout?.removeAllViews()
        pendingIntentList.clear()
        expandedBundlePackageName = null
        handler.removeCallbacks(expandBundleRunnable)
"""

content = content.replace(close_hanger_old.strip(), close_hanger_new.strip())

# 5. In populateHangerNotifications, fix pendingIntentList.add
populate_old = """
            val pi = notif.contentIntent
            pendingIntentList.add(Pair(pi, packageName))
"""

populate_new = """
            val pi = notif.contentIntent
            pendingIntentList.add(HangerItem.NotificationItem(pi, packageName))
"""

content = content.replace(populate_old.strip(), populate_new.strip())

with open('app/src/main/java/com/conduit/app/HubNotificationListenerService.kt', 'w', encoding='utf-8') as f:
    f.write(content)
