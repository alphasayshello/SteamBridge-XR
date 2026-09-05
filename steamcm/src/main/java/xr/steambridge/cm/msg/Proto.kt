package xr.steambridge.cm.msg

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal protobuf wire-format writer/reader — enough to hand-roll the CM messages we use, so there's
 * no generated-code build step. Only the wire types Steam uses: varint, fixed64, fixed32, length-delimited.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream(64)

    fun varint(field: Int, value: Long): ProtoWriter = apply {
        tag(field, 0); writeVarint(value)
    }

    // Sign-extend to 64 bits so a negative int32 emits the canonical 10-byte varint protobuf expects
    // (masking to 32 bits produced a non-canonical 5-byte varint). Positives are unaffected.
    fun varint(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = varint(field, if (value) 1L else 0L)

    fun fixed64(field: Int, value: Long): ProtoWriter = apply {
        tag(field, 1)
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
    }

    fun fixed32(field: Int, value: Int): ProtoWriter = apply {
        tag(field, 5)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        tag(field, 2); writeVarint(value.size.toLong()); out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint((field.toLong() shl 3) or wireType.toLong())

    private fun writeVarint(value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) out.write(b or 0x80) else { out.write(b); break }
        }
    }
}

/** Reads a protobuf message field-by-field. */
class ProtoReader(data: ByteArray) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    data class Field(val number: Int, val wireType: Int)

    fun hasNext(): Boolean = buf.hasRemaining()

    fun nextField(): Field {
        val tag = readVarint()
        return Field((tag ushr 3).toInt(), (tag and 0x7L).toInt())
    }

    fun readVarintValue(): Long = readVarint()
    fun readFixed64(): Long = buf.long
    fun readFixed32(): Int = buf.int

    fun readBytes(): ByteArray {
        val len = readVarint().toInt()
        val b = ByteArray(len)
        buf.get(b)
        return b
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    /** Skip a field whose value we don't consume, by its wire type. */
    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> buf.position(buf.position() + 8)
            2 -> { val len = readVarint().toInt(); buf.position(buf.position() + len) }
            5 -> buf.position(buf.position() + 4)
            else -> throw IllegalArgumentException("unknown wire type $wireType")
        }
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = buf.get().toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result
    }
}
