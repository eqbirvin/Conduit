package com.conduit.app

fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
    val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) {
            return p1.compareTo(p2)
        }
    }
    return 0
}

fun isVersionNewer(newVersion: String, oldVersion: String): Boolean {
    return compareVersions(newVersion, oldVersion) > 0
}
