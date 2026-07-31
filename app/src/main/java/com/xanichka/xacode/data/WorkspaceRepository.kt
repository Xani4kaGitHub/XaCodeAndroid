package com.xanichka.xacode.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class WorkspaceEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val size: Long
)

/** Read/write access constrained to the folder explicitly granted through Android SAF. */
class WorkspaceRepository(private val context: Context) {
    fun list(treeUri: String): List<WorkspaceEntry> {
        if (treeUri.isBlank()) return emptyList()
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return emptyList()
        return root.listFiles().map { file ->
            WorkspaceEntry(
                name = file.name ?: "Без имени",
                uri = file.uri.toString(),
                isDirectory = file.isDirectory,
                size = file.length()
            )
        }.sortedWith(compareByDescending<WorkspaceEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun readText(documentUri: String, maxChars: Int = 256_000): String {
        return context.contentResolver.openInputStream(Uri.parse(documentUri))?.bufferedReader()?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8_192)
            while (result.length < maxChars) {
                val count = reader.read(buffer, 0, minOf(buffer.size, maxChars - result.length))
                if (count <= 0) break
                result.append(buffer, 0, count)
            }
            result.toString()
        }.orEmpty()
    }

    fun writeText(parentUri: String, fileName: String, content: String): String {
        val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
            ?: error("Рабочая папка недоступна")
        val file = parent.findFile(fileName) ?: parent.createFile("text/plain", fileName)
            ?: error("Не удалось создать файл")
        context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) }
            ?: error("Не удалось открыть файл для записи")
        return file.uri.toString()
    }

    fun updateText(documentUri: String, content: String) {
        context.contentResolver.openOutputStream(Uri.parse(documentUri), "wt")?.bufferedWriter()?.use { it.write(content) }
            ?: error("Не удалось открыть файл для записи")
    }

    fun createDirectory(parentUri: String, name: String): String {
        val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
            ?: error("Рабочая папка недоступна")
        return (parent.findFile(name) ?: parent.createDirectory(name))?.uri?.toString()
            ?: error("Не удалось создать папку")
    }

    fun delete(documentUri: String): Boolean =
        DocumentFile.fromSingleUri(context, Uri.parse(documentUri))?.delete() == true
}
