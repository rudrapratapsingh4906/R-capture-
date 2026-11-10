package com.rcapture.data

import android.content.Context
import android.net.Uri
import com.rcapture.intelligence.ExtractedEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

data class ExportPackage(
    val version: String = "1.0",
    val exportDate: String,
    val totalCaptures: Int,
    val captures: List<ExportCapture>
)

data class ExportCapture(
    val id: Long,
    val text: String,
    val timestamp: String,
    val source: String,
    val entities: List<ExtractedEntity>
)

class ExportManager(private val context: Context) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val exportAdapter = moshi.adapter(ExportPackage::class.java)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    suspend fun exportToJson(uri: Uri, captures: List<CaptureEntity>) = withContext(Dispatchers.IO) {
        try {
            val exportCaptures = captures.map { capture ->
                ExportCapture(
                    id = capture.id,
                    text = capture.text,
                    timestamp = dateFormat.format(Date(capture.timestamp)),
                    source = capture.source,
                    entities = EntityJsonAdapter.fromJson(capture.entitiesJson).map { 
                        // Ensure sensitive data is masked in export by default
                        if (it.isSensitive) it.copy(value = it.maskedValue) else it
                    }
                )
            }

            val pkg = ExportPackage(
                exportDate = dateFormat.format(Date()),
                totalCaptures = exportCaptures.size,
                captures = exportCaptures
            )

            val json = exportAdapter.indent("  ").toJson(pkg)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToCsv(uri: Uri, captures: List<CaptureEntity>) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                    // Headers
                    writer.write("ID,Timestamp,Source,Text,Entities\n")
                    
                    captures.forEach { capture ->
                        val id = capture.id.toString()
                        val time = dateFormat.format(Date(capture.timestamp))
                        val src = escapeCsv(capture.source)
                        val txt = escapeCsv(capture.text)
                        
                        val entities = EntityJsonAdapter.fromJson(capture.entitiesJson)
                        val entitiesStr = escapeCsv(
                            entities.joinToString("; ") { 
                                val valToUse = if (it.isSensitive) it.maskedValue else it.value
                                "${it.category.tag}: $valToUse" 
                            }
                        )
                        
                        writer.write("$id,$time,$src,$txt,$entitiesStr\n")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(value: String): String {
        var result = value.replace("\n", " ").replace("\r", " ")
        
        // Protection against spreadsheet formula injection
        if (result.startsWith("=") || result.startsWith("+") || result.startsWith("-") || result.startsWith("@")) {
            result = "'$result"
        }
        
        if (result.contains(",") || result.contains("\"")) {
            result = "\"" + result.replace("\"", "\"\"") + "\""
        }
        return result
    }
}
