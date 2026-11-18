package com.morph.app

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MorphServiceTest {

    // Gradle runs unit tests from the module dir (app/)
    private fun source(path: String) = File(path).readText()

    @Test
    fun foregroundServiceTypeIsNeverNoneFromAndroid10() {
        for (sdk in 29..37) {
            val type = MorphService.foregroundServiceType(sdk)
            assertNotEquals("SDK $sdk would start a 'type none' service (crash on targetSdk 34+)", 0, type)
            val expected = if (sdk >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            assertEquals("SDK $sdk", expected, type)
        }
        // Android 8-9 have no service types; the 2-argument startForeground is used there
        assertEquals(0, MorphService.foregroundServiceType(26))
        assertEquals(0, MorphService.foregroundServiceType(28))
    }

    @Test
    fun everyTypeWeStartWithIsDeclaredInTheManifest() {
        val manifest = source("src/main/AndroidManifest.xml")
        val serviceTypes = Regex("""android:name="\.MorphService"[^>]*android:foregroundServiceType="([^"]+)"""")
            .find(manifest)?.groupValues?.get(1)?.split('|').orEmpty()
        // type -> (manifest flag, permission suffix)
        val declared = mapOf(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC to ("dataSync" to "DATA_SYNC"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING to ("mediaProcessing" to "MEDIA_PROCESSING")
        )
        val used = (29..37).map { MorphService.foregroundServiceType(it) }.toSet() +
            MorphService.FALLBACK_FOREGROUND_SERVICE_TYPE
        for (type in used) {
            val (flag, permission) = declared.getValue(type)
            assertTrue("$flag missing from MorphService foregroundServiceType", flag in serviceTypes)
            assertTrue("FOREGROUND_SERVICE_$permission permission missing",
                manifest.contains("\"android.permission.FOREGROUND_SERVICE_$permission\""))
        }
        assertTrue(manifest.contains("\"android.permission.FOREGROUND_SERVICE\""))
    }

    @Test
    fun foregroundStartDoesNotGoThroughServiceCompat() {
        // androidx.core 1.18 ServiceCompat ANDs the type with 0x40000FFF (API 34+) or 0xFF (API 29-33):
        // mediaProcessing (0x2000) became 0 = "type none" -> InvalidForegroundServiceTypeException.
        assertEquals(0, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING and 0x40000FFF)
        assertFalse(source("src/main/java/com/morph/app/MorphService.kt").contains("ServiceCompat.startForeground"))
    }
}
