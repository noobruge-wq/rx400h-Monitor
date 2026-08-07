package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParsersTest {

    @Test
    fun decodeStandard_parsesRpmAndSpeedFromMultiPidBlock() {
        val payload = listOf(0x41, 0x0C, 0x1A, 0xF8, 0x0D, 0x2B, 0x00, 0x00)
        val decoded = ObdParsers.decodeStandard(isoTpLines("7E8", payload), "7E8")
        assertNotNull(decoded)
        val d = decoded!!
        assertEquals(1726.0, d.rpm!!, 0.001)
        assertEquals(43.0, d.speedKph!!, 0.001)
        assertNull(d.coolantC)
    }

    @Test
    fun decodeStandard_parsesCoolant() {
        val payload = listOf(0x41, 0x05, 0x50)
        val decoded = ObdParsers.decodeStandard(isoTpLines("7E8", payload), "7E8")
        assertNotNull(decoded)
        assertEquals(40.0, decoded!!.coolantC!!, 0.001)
    }

    @Test
    fun decode21CdF3_parsesIceTorque() {
        val d = MutableList(17) { 0 }
        d[3] = 0x82
        d[11] = 0xF3
        val payload = listOf(0x61, 0xCD) + d
        val decoded = ObdParsers.decode21CdF3(isoTpLines("7E8", payload))
        assertNotNull(decoded)
        assertEquals(4.0, decoded!!.iceTorqueNm, 0.001)
    }

    @Test
    fun decode21C3_parsesSocVoltageCurrentPower() {
        val d = MutableList(37) { 0 }
        d[14] = 0x80
        d[24] = 160
        d[26] = 128
        val payload = listOf(0x61, 0xC3) + d
        val decoded = ObdParsers.decode21C3(isoTpLines("7EA", payload))
        assertNotNull(decoded)
        val c3 = decoded!!
        assertEquals(50.196, c3.socPct, 0.001)
        assertEquals(320.0, c3.hvVoltageV, 0.001)
        assertEquals(0.0, c3.hvCurrentA, 0.001)
        assertEquals(0.0, c3.hvPowerKw, 0.001)
    }

    @Test
    fun decode21C4_parsesWarmupFlag() {
        val d = MutableList(27) { 0 }
        d[1] = 0x01
        val payload = listOf(0x61, 0xC4) + d
        val decoded = ObdParsers.decode21C4(isoTpLines("7EA", payload))
        assertNotNull(decoded)
        assertTrue(decoded!!.warmupActive)
    }

    @Test
    fun decode21CF_parsesBatteryTemperatures() {
        val d = MutableList(25) { 0 }
        d[8] = 0x80
        d[9] = 0xC8
        val payload = listOf(0x61, 0xCF) + d
        val decoded = ObdParsers.decode21CF(isoTpLines("7EA", payload))
        assertNotNull(decoded)
        val cf = decoded!!
        assertEquals(20.0, cf.batteryTempsC[0], 0.001)
        assertEquals(20.0, cf.batteryTempMinC, 0.001)
        assertEquals(20.0, cf.batteryTempMaxC, 0.001)
        assertEquals(20.0, cf.batteryTempAvgC, 0.001)
    }

    @Test
    fun adapterVoltage_parsesVoltageText() {
        assertEquals(12.8, ObdParsers.adapterVoltage(listOf("12.8V"))!!, 0.001)
    }

    private fun isoTpLines(canId: String, payload: List<Int>): List<String> {
        val lines = mutableListOf<String>()
        val firstData = payload.take(6)
        val length = payload.size
        lines.add(format(canId, listOf(0x10, length and 0xFF) + firstData))
        var seq = 1
        var rest = payload.drop(6)
        while (rest.isNotEmpty()) {
            val chunk = rest.take(7)
            lines.add(format(canId, listOf(0x20 or (seq and 0x0F)) + chunk))
            rest = rest.drop(chunk.size)
            seq++
        }
        return lines
    }

    private fun format(canId: String, bytes: List<Int>): String {
        val body = bytes.joinToString("") { "%02X".format(it) }
        return "$canId $body"
    }
}
