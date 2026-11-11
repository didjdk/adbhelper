package com.rel.adbhelper.core.apps

data class AppEntry(val pkg: String, val isSystem: Boolean, val isEnabled: Boolean)

data class AppDetails(
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val firstInstallTime: String,
    val lastUpdateTime: String,
    val requestedPermissions: List<String>,
)

/** `pm list packages [-3|-s]` -> package names. */
fun parsePmList(raw: String): List<String> =
    raw.lines()
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").trim() }
        .filter { it.isNotBlank() }

/** Third-party/system classification from the two `pm list` outputs. */
fun classifyApps(all: List<String>, thirdParty: Set<String>): List<AppEntry> =
    all.map { AppEntry(pkg = it, isSystem = it !in thirdParty, isEnabled = true) }

/**
 * Fragments of `dumpsys package <pkg>`: version/sdk line, install times, and the
 * `requested permissions:` block (stops at the first non-permission line after it).
 */
fun parseDumpsysPackage(raw: String): AppDetails {
    val lines = raw.lines().map { it.trim() }
    fun after(prefix: String): String? = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
    val versionLine = after("versionCode=")?.split(Regex("\\s+")) ?: emptyList()
    fun field(name: String): Int? =
        versionLine.firstOrNull { it.startsWith("$name=") }?.removePrefix("$name=")?.toIntOrNull()

    val permissions = mutableListOf<String>()
    var inRequestedBlock = false
    for (line in lines) {
        when {
            line == "requested permissions:" -> inRequestedBlock = true
            inRequestedBlock && line.startsWith("android.permission.") -> permissions.add(line)
            inRequestedBlock -> inRequestedBlock = false
        }
    }
    return AppDetails(
        versionName = after("versionName=")?.trim().orEmpty(),
        minSdk = field("minSdk") ?: 0,
        targetSdk = field("targetSdk") ?: 0,
        firstInstallTime = after("firstInstallTime=").orEmpty(),
        lastUpdateTime = after("lastUpdateTime=").orEmpty(),
        requestedPermissions = permissions,
    )
}
