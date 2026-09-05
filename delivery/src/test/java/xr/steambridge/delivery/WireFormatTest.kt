package xr.steambridge.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireFormatTest {

    @Test
    fun `emits the exact block steamshim parses`() {
        val ticket = byteArrayOf(0x00, 0x0f.toByte(), 0xa0.toByte(), 0xff.toByte())
        val out = WireFormat.build(76561197960265728uL, "Korp", ticket)
        val text = String(out, Charsets.US_ASCII)
        assertEquals("STEAMID:76561197960265728\nNAME:Korp\nTICKET:000fa0ff\n", text)
    }

    @Test
    fun `hex is lowercase with no interior delimiter`() {
        // steamshim.cpp:204 stops hex decode at the first non-hex char, so the TICKET run must be pure.
        val ticket = ByteArray(256) { it.toByte() }
        val out = String(WireFormat.build(1uL, "x", ticket), Charsets.US_ASCII)
        val hex = out.substringAfter("TICKET:").trimEnd('\n')
        assertEquals(512, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `newlines in persona are stripped so the NAME field is not truncated`() {
        val out = String(WireFormat.build(1uL, "bad\nname\r2", ByteArray(1) { 1 }), Charsets.US_ASCII)
        assertTrue(out.contains("NAME:badname2\n"))
    }

    @Test
    fun `empty persona falls back to player to satisfy the null-name guard`() {
        val out = String(WireFormat.build(1uL, "", ByteArray(1) { 1 }), Charsets.US_ASCII)
        assertTrue(out.contains("NAME:player\n"))
    }

    @Test
    fun `name is capped to the shim buffer`() {
        val long = "a".repeat(200)
        val name = WireFormat.sanitizeName(long)
        assertTrue(name.toByteArray(Charsets.US_ASCII).size <= WireFormat.MAX_NAME_BYTES)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized ticket is rejected`() {
        WireFormat.build(1uL, "x", ByteArray(WireFormat.MAX_TICKET_BYTES + 1))
    }
}
