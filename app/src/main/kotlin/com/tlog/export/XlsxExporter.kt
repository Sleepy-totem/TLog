package com.tlog.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DayRow(
    val dayOfWeek: DayOfWeek,
    val date: LocalDate,
    val clockIn: LocalTime?,
    val clockOut: LocalTime?,
    val hours: Double,
    val jobSite: String = "",
    val projectNumber: String = "",
    val notes: String = "",
    val lunchHours: Double = 0.0,
    val clientApproval: String = "",
    val perDiem: Boolean = false
)

data class ExportData(
    val employeeName: String,
    val employeeSignature: String,
    val clientName: String,
    val stateOfWork: String,
    val weekEndingDate: LocalDate,
    val days: List<DayRow>,
    val exportDate: LocalDate,
    val totalHours: Double
)

object XlsxExporter {
    private const val TEMPLATE_ASSET = "Standard_TS_Mon-Sun.xlsx"
    private const val SHEET_PATH = "xl/worksheets/sheet1.xml"

    private val DAY_ROWS = mapOf(
        DayOfWeek.MONDAY to 14,
        DayOfWeek.TUESDAY to 17,
        DayOfWeek.WEDNESDAY to 20,
        DayOfWeek.THURSDAY to 23,
        DayOfWeek.FRIDAY to 26,
        DayOfWeek.SATURDAY to 29,
        DayOfWeek.SUNDAY to 32
    )

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val fileFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun export(context: Context, data: ExportData): Uri {
        val templateBytes = context.assets.open(TEMPLATE_ASSET).use { it.readBytes() }
        val filled = fillTemplate(templateBytes, data)
        val fileName = "TLog_WeekEnding_${data.weekEndingDate.format(fileFmt)}.xlsx"
        return saveToDocumentsTLog(context, filled, fileName)
    }

    private fun fillTemplate(zipBytes: ByteArray, data: ExportData): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries[e.name] = zis.readBytes()
                zis.closeEntry()
                e = zis.nextEntry
            }
        }

        val sheetXml = entries[SHEET_PATH]?.toString(Charsets.UTF_8)
            ?: error("$SHEET_PATH missing from template")
        val editor = SheetEditor(sheetXml)

        editor.setInline("J1", data.employeeName)
        editor.setInline("J4", data.clientName)
        editor.setInline("J6", data.stateOfWork)
        editor.setInline("J9", data.weekEndingDate.format(dateFmt))

        for (day in data.days) {
            val row = DAY_ROWS[day.dayOfWeek] ?: continue
            editor.setInline("B$row", day.date.format(dateFmt))
            if (day.projectNumber.isNotBlank()) editor.setInline("C$row", day.projectNumber)
            if (day.jobSite.isNotBlank()) editor.setInline("D$row", day.jobSite)
            day.clockIn?.let { editor.setInline("E$row", it.format(timeFmt)) }
            day.clockOut?.let { editor.setInline("G$row", it.format(timeFmt)) }
            if (day.hours > 0.0) editor.setNumber("H$row", day.hours)
            // Daily Per Diem — per-day toggle, defaults to N
            editor.setInline("I$row", if (day.perDiem) "Y" else "N")
            if (day.notes.isNotBlank()) editor.setInline("J$row", day.notes)
        }

        editor.setNumber("H35", data.totalHours)

        // Signature line is row 37: A=first, B=middle, C=last, D=date.
        val (first, middle, last) = splitName(data.employeeSignature)
        editor.setInline("A37", first)
        editor.setInline("B37", middle)
        editor.setInline("C37", last)
        editor.setInline("D37", "Date: ${data.exportDate.format(dateFmt)}")
        editor.setInline("E37", "")
        editor.setInline("F37", "")

        entries[SHEET_PATH] = editor.build().toByteArray(Charsets.UTF_8)

        entries.remove("xl/calcChain.xml")
        entries["xl/_rels/workbook.xml.rels"]?.let { raw ->
            val updated = raw.toString(Charsets.UTF_8)
                .replace(Regex("""<Relationship[^/]*calcChain[^/]*/>"""), "")
            entries["xl/_rels/workbook.xml.rels"] = updated.toByteArray(Charsets.UTF_8)
        }
        entries["[Content_Types].xml"]?.let { raw ->
            val updated = raw.toString(Charsets.UTF_8)
                .replace(Regex("""<Override[^/]*calcChain[^/]*/>"""), "")
            entries["[Content_Types].xml"] = updated.toByteArray(Charsets.UTF_8)
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun formatHours(h: Double): String =
        if (h == h.toLong().toDouble()) h.toLong().toString() else String.format("%.2f", h)

    private fun splitName(name: String): Triple<String, String, String> {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (parts.size) {
            0 -> Triple("", "", "")
            1 -> Triple(parts[0], "", "")
            2 -> Triple(parts[0], "", parts[1])
            else -> Triple(parts[0], parts[1], parts.drop(2).joinToString(" "))
        }
    }

    private fun saveToDocumentsTLog(context: Context, bytes: ByteArray, fileName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = Environment.DIRECTORY_DOCUMENTS + "/TLog"
            val existingUri = findExisting(context, fileName, relativePath)
            existingUri?.let { resolver.delete(it, null, null) }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("Could not create export file")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not open output stream for $uri")
            return uri
        }
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docs, "TLog").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun findExisting(context: Context, fileName: String, relativePath: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(fileName, "$relativePath%")
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }
}

private class SheetEditor(initialXml: String) {
    private var xml: String = initialXml

    fun setInline(ref: String, value: String) {
        val cell = findCell(ref) ?: return
        val style = styleOf(cell.tagText)
        val esc = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val styleAttr = style?.let { " s=\"$it\"" } ?: ""
        val newTag =
            """<c r="$ref"$styleAttr t="inlineStr"><is><t xml:space="preserve">$esc</t></is></c>"""
        xml = xml.replaceRange(cell.start, cell.end, newTag)
    }

    fun setNumber(ref: String, value: Double) {
        val cell = findCell(ref) ?: return
        val style = styleOf(cell.tagText)
        val styleAttr = style?.let { " s=\"$it\"" } ?: ""
        val formatted = if (value == value.toLong().toDouble()) value.toLong().toString()
                        else value.toString()
        val newTag = """<c r="$ref"$styleAttr><v>$formatted</v></c>"""
        xml = xml.replaceRange(cell.start, cell.end, newTag)
    }

    fun build(): String = xml

    private data class CellSpan(val start: Int, val end: Int, val tagText: String)

    private fun findCell(ref: String): CellSpan? {
        val pattern = Regex("""<c\s+r="$ref"[^>]*?(?:/>|>)""")
        val m = pattern.find(xml) ?: return null
        val openEnd = m.range.last + 1
        val end = if (m.value.endsWith("/>")) {
            openEnd
        } else {
            val close = xml.indexOf("</c>", openEnd)
            if (close < 0) openEnd else close + 4
        }
        return CellSpan(m.range.first, end, xml.substring(m.range.first, end))
    }

    private fun styleOf(tag: String): String? =
        Regex("""s="(\d+)"""").find(tag)?.groupValues?.get(1)
}
