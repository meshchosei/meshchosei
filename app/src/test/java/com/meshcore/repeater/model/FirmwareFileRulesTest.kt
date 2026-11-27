package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FirmwareFileRulesTest {
    private fun zip(vararg names: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> names.forEach { z.putNextEntry(ZipEntry(it)); z.write(byteArrayOf(1, 2, 3)); z.closeEntry() } }
        return bos.toByteArray()
    }

    @Test fun onlyZipExtensionIsAccepted() {
        assertEquals(FirmwareFileRules.Check.Ok, FirmwareFileRules.checkMeta("RAK_4631_repeater-v1.11.zip", 400_000))
        assertEquals(FirmwareFileRules.Check.Ok, FirmwareFileRules.checkMeta("FIRMWARE.ZIP", 400_000))
        assertEquals(FirmwareFileRules.Check.NotZip, FirmwareFileRules.checkMeta("firmware.uf2", 400_000))
        assertEquals(FirmwareFileRules.Check.NotZip, FirmwareFileRules.checkMeta("firmware.zip.txt", 400_000))
        assertEquals(FirmwareFileRules.Check.NotZip, FirmwareFileRules.checkMeta("backup.json", 400_000))
    }

    @Test fun sizeLimits() {
        assertEquals(FirmwareFileRules.Check.Empty, FirmwareFileRules.checkMeta("a.zip", 0))
        assertEquals(FirmwareFileRules.Check.TooLarge, FirmwareFileRules.checkMeta("a.zip", FirmwareFileRules.MAX_SIZE_BYTES + 1))
        assertEquals(FirmwareFileRules.Check.Ok, FirmwareFileRules.checkMeta("a.zip", FirmwareFileRules.MAX_SIZE_BYTES))
    }

    @Test fun nordicPackageWithManifest() {
        assertEquals(FirmwareFileRules.Check.Ok, FirmwareFileRules.checkZip(ByteArrayInputStream(zip("manifest.json", "firmware.bin", "firmware.dat"))))
    }

    @Test fun legacyPackageWithoutManifestNeedsBinAndDat() {
        assertEquals(FirmwareFileRules.Check.Ok, FirmwareFileRules.checkZip(ByteArrayInputStream(zip("app.bin", "app.dat"))))
        assertEquals(FirmwareFileRules.Check.NotDfuPackage, FirmwareFileRules.checkZip(ByteArrayInputStream(zip("app.bin"))))
        assertEquals(FirmwareFileRules.Check.NotDfuPackage, FirmwareFileRules.checkZip(ByteArrayInputStream(zip("backup.json", "readme.txt"))))
    }

    @Test fun notAZipIsCorrupt() {
        assertEquals(FirmwareFileRules.Check.Corrupt, FirmwareFileRules.checkZip(ByteArrayInputStream("hello".toByteArray())))
        assertEquals(FirmwareFileRules.Check.Corrupt, FirmwareFileRules.checkZip(ByteArrayInputStream(ByteArray(0))))
    }

    @Test fun nestedEntryNamesAreMatchedByFileName() {
        assertEquals(FirmwareFileRules.Check.Ok, FirmwareFileRules.checkEntries(listOf("pkg/manifest.json")))
    }

    @Test fun formatSize() {
        assertEquals("512 B", FirmwareFileRules.formatSize(512))
        assertEquals("402 kB", FirmwareFileRules.formatSize(412_000))
        assertEquals("1.5 MB", FirmwareFileRules.formatSize(1_572_864))
    }
}
