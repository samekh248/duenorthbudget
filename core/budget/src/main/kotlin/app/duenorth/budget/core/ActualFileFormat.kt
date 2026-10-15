package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The file Actual keeps on its server: a zip of `db.sqlite` and `metadata.json`
 * (encrypted as a whole when the budget has a key), as Actual's own client uploads it.
 */
object ActualFileFormat {
    private const val DATABASE = "db.sqlite"
    private const val METADATA = "metadata.json"

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    /** The sqlite file inside a downloaded budget. A bare sqlite file (older uploads from this app) passes through. */
    fun database(bytes: ByteArray): ByteArray {
        if (!isZip(bytes)) return bytes
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == DATABASE) return zip.readBytes()
            }
        }
        throw IOException("no $DATABASE in the budget file")
    }

    /** The zip Actual expects on upload. */
    fun pack(
        database: ByteArray,
        budgetId: String,
        name: String,
        cloudFileId: String,
        groupId: String?,
    ): ByteArray {
        val metadata =
            buildJsonObject {
                put("id", budgetId)
                put("budgetName", name)
                put("cloudFileId", cloudFileId)
                if (!groupId.isNullOrBlank()) put("groupId", groupId)
            }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(DATABASE))
            zip.write(database)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(METADATA))
            zip.write(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), metadata).toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
