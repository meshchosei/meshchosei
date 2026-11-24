package com.meshcore.repeater.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "power"-Antwort aus MeshCore/ZephCore CommonCLI: "batt:%u mV usb:%s solar_chg:%s ext:%s charger:%s". */
class PowerTelemetryTest {
    private fun parse(s: String) = BatterySolarTelemetry.parsePowerReply(s)!!

    @Test
    fun batteryOnlyNodeIsNotShownAsUsb() {
        val t = parse("batt:3900 mV usb:no solar_chg:n/a ext:no charger:no")
        assertEquals(PowerSource.BATTERY, t.powerSource)
        assertEquals(70, t.batteryPercent)
        assertEquals(false, t.usbPowered)
        assertNull(t.solarCharging)
    }

    @Test
    fun detectsUsbSolarAndExternal() {
        assertEquals(PowerSource.USB, parse("batt:4150 mV usb:yes solar_chg:no ext:no charger:yes").powerSource)
        assertTrue(parse("batt:4150 mV usb:yes solar_chg:no ext:no charger:yes").charging)
        assertEquals(PowerSource.SOLAR, parse("batt:4000 mV usb:no solar_chg:yes ext:no charger:yes").powerSource)
        assertEquals(PowerSource.EXTERNAL, parse("batt:0 mV usb:n/a solar_chg:n/a ext:yes charger:no").powerSource)
    }

    @Test
    fun noBatteryAndNoDetectionIsUnknownNotUsb() {
        val t = parse("batt:0 mV usb:n/a solar_chg:n/a ext:no charger:no")
        assertTrue(t.queried)
        assertFalse(t.hasBattery)
        assertEquals(PowerSource.UNKNOWN, t.powerSource)
    }

    @Test
    fun notQueriedIsUnknownAndForeignLinesAreIgnored() {
        assertEquals(PowerSource.UNKNOWN, BatterySolarTelemetry().powerSource)
        assertNull(BatterySolarTelemetry.parsePowerReply("869.618,62.5,8,8"))
        assertNull(BatterySolarTelemetry.parsePowerReply("OK"))
    }

    @Test
    fun percentIsClamped() {
        assertEquals(0, BatterySolarTelemetry.percentFromMv(3000))
        assertEquals(100, BatterySolarTelemetry.percentFromMv(4300))
    }
}
