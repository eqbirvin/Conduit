package com.conduit.app.data

// Removes hidden-text phrases from notification titles and bodies for display.
// Pure string work: safe to call from any thread, but run large lists off the main thread.
object HiddenTextFilter {

    private val repeatedSpaces = Regex("[ \\t]{2,}")
    private val spaceAroundNewline = Regex("[ \\t]*\\n[ \\t]*")
    private val extraBlankLines = Regex("\\n{3,}")

    fun cleanTitle(packageName: String, title: String?, rules: List<HiddenTextRule>): String? =
        clean(packageName, title, rules, HiddenTextTarget.TITLE)

    fun cleanBody(packageName: String, text: String?, rules: List<HiddenTextRule>): String? =
        clean(packageName, text, rules, HiddenTextTarget.BODY)

    // Removes body phrases without tidying whitespace, so text found afterwards is still a verbatim
    // substring of what a newly added rule will see (rules run in order before tidying).
    fun stripBody(packageName: String, text: String, rules: List<HiddenTextRule>): String =
        removePhrases(packageName, text, rules, HiddenTextTarget.BODY) ?: text

    fun apply(notification: HubNotification, rules: List<HiddenTextRule>): HubNotification {
        if (rules.isEmpty()) return notification
        val title = cleanTitle(notification.packageName, notification.title, rules)
        val text = cleanBody(notification.packageName, notification.text, rules)
        if (title == notification.title && text == notification.text) return notification
        return notification.copy(title = title, text = text)
    }

    fun apply(notifications: List<HubNotification>, rules: List<HiddenTextRule>): List<HubNotification> {
        if (rules.isEmpty()) return notifications
        return notifications.map { apply(it, rules) }
    }

    private fun clean(
        packageName: String,
        value: String?,
        rules: List<HiddenTextRule>,
        field: HiddenTextTarget
    ): String? {
        if (value.isNullOrEmpty() || rules.isEmpty()) return value
        val result = removePhrases(packageName, value, rules, field) ?: return value
        // Tidy the whitespace a removed phrase leaves behind.
        return result
            .replace(repeatedSpaces, " ")
            .replace(spaceAroundNewline, "\n")
            .replace(extraBlankLines, "\n\n")
            .trim()
    }

    // Returns null when no rule matched.
    private fun removePhrases(
        packageName: String,
        value: String,
        rules: List<HiddenTextRule>,
        field: HiddenTextTarget
    ): String? {
        var result: String = value
        var changed = false
        for (rule in rules) {
            if (rule.phrase.isEmpty()) continue
            if (rule.target != HiddenTextTarget.BOTH && rule.target != field) continue
            if (rule.packageNames.isNotEmpty() && packageName !in rule.packageNames) continue
            val ignoreCase = !rule.matchCase
            if (!result.contains(rule.phrase, ignoreCase)) continue
            result = result.replace(rule.phrase, "", ignoreCase)
            changed = true
        }
        return if (changed) result else null
    }
}
