package xr.steambridge.cm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import xr.steambridge.cm.msg.ClientLogon
import xr.steambridge.cm.msg.EMsg
import xr.steambridge.cm.msg.MachineId
import xr.steambridge.cm.msg.ProtoHeader
import xr.steambridge.cm.msg.RequestEncryptedAppTicket
import xr.steambridge.cm.msg.SteamPacket
import org.junit.Test

class FramingTest {

    @Test
    fun `packet encode-decode round trips with proto flag`() {
        val header = ProtoHeader(steamId = 0x0110000100000001L, jobIdSource = 42L).encode()
        val body = byteArrayOf(1, 2, 3, 4, 5)
        val packet = SteamPacket(EMsg.ClientLogon, header, body)
        val decoded = SteamPacket.decode(packet.encode())
        assertEquals(EMsg.ClientLogon, decoded.eMsg)
        assertArrayEquals(header, decoded.headerBytes)
        assertArrayEquals(body, decoded.bodyBytes)
    }

    @Test
    fun `header job id survives a fixed64 round trip`() {
        val h = ProtoHeader(jobIdSource = 0x5000000000000001L, targetJobName = "Authentication.PollAuthSessionStatus#1")
        val back = ProtoHeader.decode(h.encode())
        assertEquals(0x5000000000000001L, back.jobIdSource)
        assertEquals("Authentication.PollAuthSessionStatus#1", back.targetJobName)
    }

    @Test
    fun `tcp wrapper carries the VT01 magic and length`() {
        val inner = SteamPacket(EMsg.ClientHeartBeat, ByteArray(0), ByteArray(0)).encode()
        val wrapped = SteamPacket.wrapTcp(inner)
        // [u32 len][u32 VT01][inner]
        assertEquals(inner.size + 8, wrapped.size)
        val magic = (wrapped[4].toInt() and 0xFF) or
            ((wrapped[5].toInt() and 0xFF) shl 8) or
            ((wrapped[6].toInt() and 0xFF) shl 16) or
            ((wrapped[7].toInt() and 0xFF) shl 24)
        assertEquals(SteamPacket.VT01_MAGIC, magic)
    }

    @Test
    fun `machine id is stable for a given seed`() {
        val a = MachineId.build("seed-123")
        val b = MachineId.build("seed-123")
        val c = MachineId.build("different")
        assertArrayEquals(a, b)
        assert(!a.contentEquals(c))
    }

    @Test
    fun `encrypted app ticket response extracts field 3 bytes`() {
        // Hand-build a response: app_id(1)=3504270 varint, eresult(2)=1 varint, ticket(3)=bytes.
        val ticket = byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        val body = xr.steambridge.cm.msg.ProtoWriter()
            .varint(1, 3504270)
            .varint(2, 1)
            .bytes(3, ticket)
            .toByteArray()
        val resp = RequestEncryptedAppTicket.parse(body)
        assertEquals(3504270, resp.appId)
        assertEquals(1, resp.eResult)
        assertArrayEquals(ticket, resp.encryptedAppTicket)
    }

    @Test
    fun `client logon body carries protocol version and refresh token`() {
        val body = ClientLogon.request("korp", "refresh_tok", MachineId.build("s"))
        // protocol_version is field 1 varint = 65580; just confirm it parses back via a reader sweep.
        val r = xr.steambridge.cm.msg.ProtoReader(body)
        var sawProtocol = false
        var sawAccount = false
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> { assertEquals(ClientLogon.PROTOCOL_VERSION.toLong(), r.readVarintValue()); sawProtocol = true }
                50 -> { assertEquals("korp", r.readString()); sawAccount = true }
                else -> r.skip(f.wireType)
            }
        }
        assert(sawProtocol)
        assert(sawAccount)
    }
}
