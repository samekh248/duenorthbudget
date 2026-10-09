package app.duenorth.budget.core

import java.io.ByteArrayOutputStream

/** One cell inside an Actual sync envelope. */
data class CellMessage(
    val dataset: String,
    val row: String,
    val column: String,
    val value: String,
)

/** Protobuf `MessageEnvelope` from Actual's sync.proto. */
class Envelope(
    val timestamp: String,
    val isEncrypted: Boolean,
    val content: ByteArray,
)

data class DecodedRequest(
    val fileId: String,
    val groupId: String,
    val keyId: String,
    val since: String,
    val messages: List<Envelope>,
)

data class DecodedResponse(
    val messages: List<Envelope>,
    val merkle: String,
)

/**
 * Just enough of proto3 to speak Actual's sync.proto. Unknown fields are skipped.
 */
object SyncProto {
    fun encodeMessage(message: CellMessage): ByteArray {
        val writer = ProtoWriter()
        writer.string(1, message.dataset)
        writer.string(2, message.row)
        writer.string(3, message.column)
        writer.string(4, message.value)
        return writer.bytes()
    }

    fun decodeMessage(bytes: ByteArray): CellMessage {
        var dataset = ""
        var row = ""
        var column = ""
        var value = ""
        for ((field, part) in ProtoReader(bytes).fields()) {
            if (part.wire != 2) continue
            val text = part.bytes.toString(Charsets.UTF_8)
            when (field) {
                1 -> dataset = text
                2 -> row = text
                3 -> column = text
                4 -> value = text
            }
        }
        return CellMessage(dataset, row, column, value)
    }

    fun encodeRequest(
        fileId: String,
        groupId: String,
        keyId: String,
        since: String,
        messages: List<Envelope>,
    ): ByteArray {
        val writer = ProtoWriter()
        messages.forEach { writer.message(1, encodeEnvelope(it)) }
        writer.string(2, fileId)
        writer.string(3, groupId)
        writer.string(5, keyId)
        writer.string(6, since)
        return writer.bytes()
    }

    fun decodeRequest(bytes: ByteArray): DecodedRequest {
        var fileId = ""
        var groupId = ""
        var keyId = ""
        var since = ""
        val messages = mutableListOf<Envelope>()
        for ((field, part) in ProtoReader(bytes).fields()) {
            when (field) {
                1 -> if (part.wire == 2) messages.add(decodeEnvelope(part.bytes))
                2 -> if (part.wire == 2) fileId = part.text()
                3 -> if (part.wire == 2) groupId = part.text()
                5 -> if (part.wire == 2) keyId = part.text()
                6 -> if (part.wire == 2) since = part.text()
            }
        }
        return DecodedRequest(fileId, groupId, keyId, since, messages)
    }

    fun encodeResponse(
        merkle: String,
        messages: List<Envelope>,
    ): ByteArray {
        val writer = ProtoWriter()
        messages.forEach { writer.message(1, encodeEnvelope(it)) }
        writer.string(2, merkle)
        return writer.bytes()
    }

    fun decodeResponse(bytes: ByteArray): DecodedResponse {
        var merkle = ""
        val messages = mutableListOf<Envelope>()
        for ((field, part) in ProtoReader(bytes).fields()) {
            when (field) {
                1 -> if (part.wire == 2) messages.add(decodeEnvelope(part.bytes))
                2 -> if (part.wire == 2) merkle = part.text()
            }
        }
        return DecodedResponse(messages, merkle)
    }

    fun encodeEncrypted(
        iv: ByteArray,
        authTag: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val writer = ProtoWriter()
        writer.raw(1, iv)
        writer.raw(2, authTag)
        writer.raw(3, data)
        return writer.bytes()
    }

    fun decodeEncrypted(bytes: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        var iv = ByteArray(0)
        var tag = ByteArray(0)
        var data = ByteArray(0)
        for ((field, part) in ProtoReader(bytes).fields()) {
            if (part.wire != 2) continue
            when (field) {
                1 -> iv = part.bytes
                2 -> tag = part.bytes
                3 -> data = part.bytes
            }
        }
        return Triple(iv, tag, data)
    }

    private fun encodeEnvelope(envelope: Envelope): ByteArray {
        val writer = ProtoWriter()
        writer.string(1, envelope.timestamp)
        if (envelope.isEncrypted) writer.bool(2, true)
        writer.raw(3, envelope.content)
        return writer.bytes()
    }

    private fun decodeEnvelope(bytes: ByteArray): Envelope {
        var timestamp = ""
        var encrypted = false
        var content = ByteArray(0)
        for ((field, part) in ProtoReader(bytes).fields()) {
            when (field) {
                1 -> if (part.wire == 2) timestamp = part.text()
                2 -> if (part.wire == 0) encrypted = part.varint != 0L
                3 -> if (part.wire == 2) content = part.bytes
            }
        }
        return Envelope(timestamp, encrypted, content)
    }
}

private class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun string(
        field: Int,
        value: String,
    ) {
        if (value.isEmpty()) return
        raw(field, value.toByteArray(Charsets.UTF_8))
    }

    fun bool(
        field: Int,
        value: Boolean,
    ) {
        if (!value) return
        key(field, 0)
        varint(1)
    }

    fun raw(
        field: Int,
        value: ByteArray,
    ) {
        if (value.isEmpty()) return
        key(field, 2)
        varint(value.size)
        out.write(value)
    }

    fun message(
        field: Int,
        value: ByteArray,
    ) = raw(field, value)

    fun bytes(): ByteArray = out.toByteArray()

    private fun key(
        field: Int,
        wire: Int,
    ) = varint((field shl 3) or wire)

    private fun varint(value: Int) {
        var rest = value
        while (rest and 0x7F.inv() != 0) {
            out.write((rest and 0x7F) or 0x80)
            rest = rest ushr 7
        }
        out.write(rest)
    }
}

private class ProtoField(
    val wire: Int,
    val varint: Long,
    val bytes: ByteArray,
) {
    fun text(): String = bytes.toString(Charsets.UTF_8)
}

private class ProtoReader(
    private val data: ByteArray,
) {
    private var index = 0

    fun fields(): List<Pair<Int, ProtoField>> {
        val found = mutableListOf<Pair<Int, ProtoField>>()
        while (index < data.size) {
            val key = readVarint().toInt()
            val field = key ushr 3
            val wire = key and 7
            val part =
                when (wire) {
                    0 -> ProtoField(wire, readVarint(), ByteArray(0))
                    1 -> {
                        skip(8)
                        ProtoField(wire, 0, ByteArray(0))
                    }
                    2 -> ProtoField(wire, 0, readBytes())
                    5 -> {
                        skip(4)
                        ProtoField(wire, 0, ByteArray(0))
                    }
                    else -> error("unsupported wire type $wire")
                }
            found.add(field to part)
        }
        return found
    }

    private fun skip(count: Int) {
        index += count
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (index < data.size && shift <= 63) {
            val byte = data[index++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        error("truncated varint")
    }

    private fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        if (length < 0 || index + length > data.size) error("truncated length")
        val slice = data.copyOfRange(index, index + length)
        index += length
        return slice
    }
}
