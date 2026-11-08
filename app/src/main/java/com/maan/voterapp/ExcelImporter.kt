package com.maan.voterapp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.WorkbookFactory

object ExcelImporter {
    fun read(context: Context, uri: Uri): List<Voter> {
        val input = context.contentResolver.openInputStream(uri) ?: error("Excel file नहीं खुल सकी")
        input.use { stream ->
            val workbook = WorkbookFactory.create(stream)
            val sheet = workbook.getSheetAt(0)
            if (sheet.lastRowNum < 1) error("Excel में डेटा नहीं मिला")
            val header = sheet.getRow(0)
            fun norm(s: String) = s.trim().lowercase().replace(" ", "").replace("_", "")
            val headers = (0 until header.lastCellNum.toInt()).associateBy { norm(header.getCell(it)?.toString().orEmpty()) }
            fun col(vararg names: String) = names.firstNotNullOfOrNull { headers[norm(it)] } ?: -1
            val serialCol = col("serial number", "serial", "sno", "क्रमांक")
            val nameCol = col("voter name", "name", "मतदाता का नाम")
            val relationCol = col("father name", "mother name", "husband name", "relation", "पिता का नाम", "माता का नाम", "पति का नाम")
            val epicCol = col("epic number", "epic", "voter id")
            val wardCol = col("ward number", "ward", "वार्ड नंबर")
            require(nameCol >= 0 && wardCol >= 0) { "Excel में Voter Name और Ward Number कॉलम जरूरी हैं" }
            fun value(row: org.apache.poi.ss.usermodel.Row, c: Int): String =
                if (c < 0) "" else row.getCell(c)?.toString()?.trim().orEmpty().removeSuffix(".0")
            val result = mutableListOf<Voter>()
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val name = value(row, nameCol)
                val ward = value(row, wardCol)
                if (name.isBlank() || ward.isBlank()) continue
                result += Voter(serial = value(row, serialCol).ifBlank { i.toString() }, name = name,
                    relation = value(row, relationCol), epic = value(row, epicCol), ward = ward)
            }
            workbook.close()
            require(result.isNotEmpty()) { "Excel में कोई वैध voter record नहीं मिला" }
            return result
        }
    }
}
