package com.xanichka.xacode.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject

data class WorkspaceEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val size: Long
)

/** Read/write access constrained to the folder explicitly granted through Android SAF. */
class WorkspaceRepository(private val context: Context) {
    private fun directory(uri: String): DocumentFile? {
        val parsed = Uri.parse(uri)
        return DocumentFile.fromTreeUri(context, parsed)?.takeIf { it.isDirectory }
            ?: DocumentFile.fromSingleUri(context, parsed)?.takeIf { it.isDirectory }
    }

    fun list(treeUri: String): List<WorkspaceEntry> {
        if (treeUri.isBlank()) return emptyList()
        val root = directory(treeUri) ?: return emptyList()
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
        val parent = directory(parentUri)
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
        val parent = directory(parentUri)
            ?: error("Рабочая папка недоступна")
        return (parent.findFile(name) ?: parent.createDirectory(name))?.uri?.toString()
            ?: error("Не удалось создать папку")
    }

    fun delete(documentUri: String): Boolean =
        DocumentFile.fromSingleUri(context, Uri.parse(documentUri))?.delete() == true

    fun rename(documentUri: String, newName: String): Boolean =
        DocumentFile.fromSingleUri(context, Uri.parse(documentUri))?.renameTo(newName) == true

    fun createManagedProject(rootUri: String, name: String): WorkspaceEntry {
        val safeName = name.trim().replace(Regex("[\\/:*?\"<>|]"), "-").ifBlank { "Новый проект" }
        val root = directory(rootUri) ?: error("Папка проектов недоступна")
        val folder = root.findFile(safeName) ?: root.createDirectory(safeName)
            ?: error("Не удалось создать папку проекта")
        require(folder.isDirectory) { "Объект с таким именем уже существует" }
        return WorkspaceEntry(folder.name ?: safeName, folder.uri.toString(), true, 0)
    }

    fun resolve(rootUri: String, relativePath: String): DocumentFile? {
        var current = directory(rootUri) ?: return null
        val parts = relativePath.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
        if (parts.any { it == ".." }) return null
        for (part in parts) current = current.findFile(part) ?: return null
        return current
    }

    fun listRelative(rootUri: String, relativePath: String = ""): List<WorkspaceEntry> {
        val folder = if (relativePath.isBlank()) directory(rootUri) else resolve(rootUri, relativePath)?.takeIf { it.isDirectory }
        return folder?.listFiles()?.map { file -> WorkspaceEntry(file.name ?: "Без имени", file.uri.toString(), file.isDirectory, file.length()) }
            ?.sortedWith(compareByDescending<WorkspaceEntry> { it.isDirectory }.thenBy { it.name.lowercase() }).orEmpty()
    }

    fun readRelative(rootUri: String, relativePath: String, startLine: Int? = null, endLine: Int? = null): String {
        val text = resolve(rootUri, relativePath)?.takeIf { it.isFile }?.let { readText(it.uri.toString()) }
            ?: error("Файл не найден: $relativePath")
        if (startLine == null && endLine == null) return text
        val lines = text.lines()
        val from = ((startLine ?: 1) - 1).coerceIn(0, lines.size)
        val to = (endLine ?: lines.size).coerceIn(from, lines.size)
        return lines.subList(from, to).mapIndexed { index, line -> "${from + index + 1}: $line" }.joinToString("\n")
    }

    fun writeRelative(rootUri: String, relativePath: String, content: String) {
        val normalized = relativePath.replace('\\', '/').trim('/')
        require(normalized.isNotBlank() && !normalized.split('/').contains("..")) { "Недопустимый путь" }
        val parentPath = normalized.substringBeforeLast('/', "")
        val name = normalized.substringAfterLast('/')
        val parent = ensureDirectories(rootUri, parentPath)
        val existing = parent.findFile(name)
        val file = existing ?: parent.createFile("text/plain", name) ?: error("Не удалось создать файл")
        require(file.isFile) { "По этому пути находится папка" }
        updateText(file.uri.toString(), content)
    }

    fun createDirectoryRelative(rootUri: String, relativePath: String) {
        ensureDirectories(rootUri, relativePath)
    }

    fun deleteRelative(rootUri: String, relativePath: String): Boolean {
        require(relativePath.isNotBlank()) { "Нельзя удалить корень проекта" }
        return resolve(rootUri, relativePath)?.delete() == true
    }

    fun search(rootUri: String, query: String, limit: Int = 40): List<String> {
        val result = mutableListOf<String>()
        fun walk(folder: DocumentFile, prefix: String, depth: Int) {
            if (depth > 8 || result.size >= limit) return
            folder.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                if (name.contains(query, true)) result += path
                if (child.isDirectory) walk(child, path, depth + 1)
            }
        }
        directory(rootUri)?.let { walk(it, "", 0) }
        return result.take(limit)
    }

    fun fileInfo(rootUri: String, relativePath: String): String {
        val file = resolve(rootUri, relativePath)
        return JSONObject().put("exists", file != null).apply {
            if (file != null) { put("name", file.name); put("isDirectory", file.isDirectory); put("size", file.length()); put("mimeType", file.type ?: "") }
        }.toString()
    }

    fun findFiles(rootUri: String, glob: String, limit: Int = 100): List<String> {
        val regex = globToRegex(glob.ifBlank { "**/*" })
        return walkPaths(rootUri, limit).filter { regex.matches(it) }.take(limit)
    }

    fun searchCode(rootUri: String, pattern: String, limit: Int = 80): List<String> {
        val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE))
        val result = mutableListOf<String>()
        walkPaths(rootUri, 250).forEach { path ->
            if (result.size >= limit) return@forEach
            val file = resolve(rootUri, path) ?: return@forEach
            if (!file.isFile || file.length() > 512_000) return@forEach
            runCatching { readText(file.uri.toString(), 512_000).lineSequence().forEachIndexed { index, line -> if (result.size < limit && regex.containsMatchIn(line)) result += "$path:${index + 1}: ${line.take(240)}" } }
        }
        return result
    }

    fun inspectWorkspace(rootUri: String): String {
        val paths = walkPaths(rootUri, 160)
        val markers = listOf("package.json", "build.gradle.kts", "build.gradle", "requirements.txt", "pyproject.toml", "Cargo.toml", "go.mod")
        val detected = markers.filter { marker -> paths.any { it.endsWith(marker, true) } }
        return buildString { append("Project tree:\n"); paths.take(120).forEach { append("- ").append(it).append('\n') }; append("Detected manifests: ").append(detected.ifEmpty { listOf("none") }.joinToString()) }
    }

    private fun walkPaths(rootUri: String, limit: Int): List<String> {
        val result = mutableListOf<String>()
        fun walk(folder: DocumentFile, prefix: String, depth: Int) {
            if (depth > 10 || result.size >= limit) return
            folder.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                result += path
                if (child.isDirectory) walk(child, path, depth + 1)
            }
        }
        directory(rootUri)?.let { walk(it, "", 0) }
        return result.take(limit)
    }

    private fun globToRegex(glob: String): Regex {
        val normalized = glob.replace('\\', '/')
        val pattern = buildString {
            append('^'); var index = 0
            while (index < normalized.length) {
                when {
                    normalized.startsWith("**/", index) -> { append("(?:.*/)?"); index += 3 }
                    normalized.startsWith("**", index) -> { append(".*"); index += 2 }
                    normalized[index] == '*' -> { append("[^/]*"); index++ }
                    normalized[index] == '?' -> { append("[^/]"); index++ }
                    else -> { append(Regex.escape(normalized[index].toString())); index++ }
                }
            }
            append('$')
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun ensureDirectories(rootUri: String, relativePath: String): DocumentFile {
        var current = directory(rootUri) ?: error("Папка проекта недоступна")
        relativePath.replace('\\', '/').split('/').filter { it.isNotBlank() }.forEach { part ->
            require(part != "..") { "Недопустимый путь" }
            current = current.findFile(part)?.takeIf { it.isDirectory } ?: current.createDirectory(part)
                ?: error("Не удалось создать папку $part")
        }
        return current
    }
}
