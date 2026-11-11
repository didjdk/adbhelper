package com.rel.adbhelper.core.apps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppParsersTest {

    private val pmFixture = """
package:com.rel.adbhelper
package:com.tranzmate
package:com.didjdk.adbhelper
package:com.android.systemui
package:com.keshet.mako.VOD
    """.trimIndent()

    private val thirdPartyFixture = """
package:com.tranzmate
package:com.didjdk.adbhelper
    """.trimIndent()

    private val dumpsysFixture = """
    versionCode=37 minSdk=37 targetSdk=37
    versionName=17
    lastUpdateTime=2026-10-26 13:54:36
      firstInstallTime=2026-10-26 13:54:36
      requested permissions:
        android.permission.INTERNET
        android.permission.ACCESS_WIFI_STATE
        android.permission.ACCESS_NETWORK_STATE
      install permissions:
        android.permission.INTERNET
    """.trimIndent()

    @Test
    fun `parsePmList reads package lines`() {
        assertEquals(
            listOf("com.rel.adbhelper", "com.tranzmate", "com.didjdk.adbhelper", "com.android.systemui", "com.keshet.mako.VOD"),
            parsePmList(pmFixture),
        )
    }

    @Test
    fun `classifyApps flags system apps by set membership`() {
        val apps = classifyApps(
            parsePmList(pmFixture),
            parsePmList(thirdPartyFixture).toSet(),
        )
        assertEquals(5, apps.size)
        assertTrue(apps.first { it.pkg == "com.android.systemui" }.isSystem)
        assertTrue(apps.first { it.pkg == "com.tranzmate" }.let { !it.isSystem })
    }

    @Test
    fun `parseDumpsysPackage reads version sdk times and requested permissions`() {
        val details = parseDumpsysPackage(dumpsysFixture)
        assertEquals("17", details.versionName)
        assertEquals(37, details.minSdk)
        assertEquals(37, details.targetSdk)
        assertEquals("2026-10-26 13:54:36", details.firstInstallTime)
        assertEquals("2026-10-26 13:54:36", details.lastUpdateTime)
        assertEquals(
            listOf("android.permission.INTERNET", "android.permission.ACCESS_WIFI_STATE", "android.permission.ACCESS_NETWORK_STATE"),
            details.requestedPermissions,
        )
    }

    @Test
    fun `app sort orders work`() {
        val apps = listOf(
            AppEntry("com.z.app", isSystem = false, isEnabled = true),
            AppEntry("com.a.app", isSystem = true, isEnabled = false),
        )
        assertEquals("com.a.app", apps.sortedBy { it.pkg }.first().pkg)
    }
}
