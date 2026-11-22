package world.lunlun.furnmigrate.common

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

/** Minimal NBT reader (FurnitureLib stores models and placed objects as Base64(GZip(NBT))). */
object Nbt {
    fun readBase64Gzip(base64: String): Map<String, Any> =
        readGzip(Base64.getDecoder().decode(base64.trim()))

    fun readGzip(bytes: ByteArray): Map<String, Any> =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { read(it) }

    @Suppress("UNCHECKED_CAST")
    fun read(input: DataInputStream): Map<String, Any> {
        val type = input.readUnsignedByte()
        require(type == 10) { "root tag must be a compound, got $type" }
        input.readUTF()
        return readPayload(input, type) as Map<String, Any>
    }

    private fun readPayload(input: DataInputStream, type: Int): Any = when (type) {
        1 -> input.readByte()
        2 -> input.readShort()
        3 -> input.readInt()
        4 -> input.readLong()
        5 -> input.readFloat()
        6 -> input.readDouble()
        7 -> ByteArray(input.readInt()).also { input.readFully(it) }
        8 -> input.readUTF()
        9 -> {
            val elementType = input.readUnsignedByte()
            val n = input.readInt()
            List(n) { readPayload(input, elementType) }
        }
        10 -> {
            val map = LinkedHashMap<String, Any>()
            while (true) {
                val t = input.readUnsignedByte()
                if (t == 0) break
                val name = input.readUTF()
                map[name] = readPayload(input, t)
            }
            map
        }
        11 -> IntArray(input.readInt()) { input.readInt() }
        12 -> LongArray(input.readInt()) { input.readLong() }
        else -> error("unknown NBT tag type $type")
    }
}
