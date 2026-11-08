package com.maan.voterapp

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream

/**
 * Lightweight XLSX reader using Android's built-in XML parser.
 * Reads the first worksheet, shared strings and inline strings without desktop AWT dependencies.
 */
object ExcelImporter {
    fun read(context: Context, uri: Uri): List<Voter> {
        val entries = linkedMapOf<String, ByteArray>()
        val input = context.contentResolver.openInputStream(uri) ?: error("Excel file नहीं खुल सकी")
        input.use { source ->
            ZipInputStream(source).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" ||
                                entry.name == "xl/worksheets/sheet1.xml" ||
                                entry.name == "xl/workbook.xml" ||
                                entry.name == "xl/_rels/workbook.xml.rels")) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("पहली worksheet नहीं मिली। कृपया .xlsx फाइल चुनें।")
        val shared = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val rawRows = readSheet(sheet, shared)
        require(rawRows.size > 1) { "Excel में header के बाद voter data नहीं मिला" }

        fun norm(value: String) = value.trim().lowercase()
            .replace(" ", "").replace("_", "").replace("-", "")
            .replace(".", "")
        val headers = rawRows.first().mapIndexed { index, value -> norm(value) to index }.toMap()
        fun column(vararg keys: String): Int = keys.firstNotNullOfOrNull { headers[norm(it)] } ?: -1
        val serialCol = column("serial number", "serial", "sno", "क्रमांक", "क्र.सं.")
        val nameCol = column("voter name", "name", "मतदाता का नाम", "मतदाता")
        val fatherCol = column("father name", "पिता का नाम")
        val motherCol = column("mother name", "माता का नाम")
        val husbandCol = column("husband name", "पति का नाम")
        val relationCol = column("relation", "father/mother/husband name", "father name / mother name / husband name", "father name/mother name/husband name", "पिता/माता/पति का नाम")
        val epicCol = column("epic number", "epic", "voter id", "epic no")
        val wardCol = column("ward number", "ward", "वार्ड नंबर", "वार्ड")
        require(nameCol >= 0 && wardCol >= 0) {
            "Excel में Voter Name और Ward Number कॉलम जरूरी हैं।"
        }
        fun cell(row: List<String>, index: Int) = if (index in row.indices) row[index].trim() else ""
        val result = rawRows.drop(1).mapIndexedNotNull { index, row ->
            val name = cell(row, nameCol)
            val ward = cell(row, wardCol)
            if (name.isBlank() || ward.isBlank()) null else {
                val relation = listOf(fatherCol, motherCol, husbandCol, relationCol)
                    .map { cell(row, it) }.firstOrNull { it.isNotBlank() }.orEmpty()
                Voter(
                    serial = cell(row, serialCol).ifBlank { (index + 1).toString() },
                    name = name,
                    relation = relation,
                    epic = cell(row, epicCol),
                    ward = ward
                )
            }
        }
        require(result.isNotEmpty()) { "Excel में कोई वैध voter record नहीं मिला" }
        return result
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        val values = mutableListOf<String>()
        var inItem = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; current.setLength(0) }
                    "t" -> if (inItem) current.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    values += current.toString()
                    inItem = false
                }
            }
            event = parser.next()
        }
        return values
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        val rows = mutableListOf<List<String>>()
        var row = mutableMapOf<Int, String>()
        var cellIndex = 0
        var cellType = ""
        var inCell = false
        var inValue = false
        var inInlineText = false
        var value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> {
                        inCell = true
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellIndex = columnIndex(parser.getAttributeValue(null, "r").orEmpty())
                        value = StringBuilder()
                    }
                    "v" -> if (inCell) inValue = true
                    "t" -> if (inCell && cellType == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT -> if (inCell && (inValue || inInlineText)) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = value.toString()
                        row[cellIndex] = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                        inCell = false
                    }
                    "row" -> if (row.isNotEmpty()) {
                        val max = row.keys.maxOrNull() ?: -1
                        rows += (0..max).map { row[it].orEmpty() }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile { it.isLetter() }.uppercase()
        var result = 0
        letters.forEach { result = result * 26 + (it - 'A' + 1) }
        return (result - 1).coerceAtLeast(0)
    }
}
