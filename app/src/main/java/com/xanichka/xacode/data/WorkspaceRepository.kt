package com.xanichka.xacode.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File

data class WorkspaceEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val size: Long
)

/** Read/write access constrained to the folder explicitly granted through Android SAF. */
class WorkspaceRepository(private val context: Context) {
    private val projectAdjectives = listOf("bright", "calm", "clever", "cosmic", "crisp", "gentle", "lucky", "rapid", "silent", "vivid")
    private val projectNouns = listOf("badger", "falcon", "forest", "harbor", "meteor", "otter", "pixel", "rocket", "studio", "willow")
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
        val safeLimit = maxChars.coerceIn(1, 1_000_000)
        return context.contentResolver.openInputStream(Uri.parse(documentUri))?.bufferedReader()?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8_192)
            while (result.length < safeLimit) {
                val count = reader.read(buffer, 0, minOf(buffer.size, safeLimit - result.length))
                if (count <= 0) break
                result.append(buffer, 0, count)
            }
            result.toString()
        }.orEmpty()
    }

    fun writeText(parentUri: String, fileName: String, content: String): String {
        validateName(fileName)
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "Файл больше 2 МБ" }
        val parent = directory(parentUri)
            ?: error("Рабочая папка недоступна")
        val file = parent.findFile(fileName) ?: parent.createFile(mimeType(fileName), fileName)
            ?: error("Не удалось создать файл")
        context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) }
            ?: error("Не удалось открыть файл для записи")
        return file.uri.toString()
    }

    fun updateText(documentUri: String, content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "Файл больше 2 МБ" }
        context.contentResolver.openOutputStream(Uri.parse(documentUri), "wt")?.bufferedWriter()?.use { it.write(content) }
            ?: error("Не удалось открыть файл для записи")
    }

    fun createDirectory(parentUri: String, name: String): String {
        validateName(name)
        val parent = directory(parentUri)
            ?: error("Рабочая папка недоступна")
        return (parent.findFile(name) ?: parent.createDirectory(name))?.uri?.toString()
            ?: error("Не удалось создать папку")
    }

    fun delete(documentUri: String): Boolean =
        DocumentFile.fromSingleUri(context, Uri.parse(documentUri))?.delete() == true

    fun rename(documentUri: String, newName: String): Boolean {
        validateName(newName)
        val uri = Uri.parse(documentUri)
        return DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null
    }

    fun createManagedProject(rootUri: String, name: String): WorkspaceEntry {
        val safeName = name.trim().replace(Regex("[\\/:*?\"<>|]"), "-").ifBlank { "Новый проект" }
        val root = directory(rootUri) ?: error("Папка проектов недоступна")
        val folder = root.findFile(safeName) ?: root.createDirectory(safeName)
            ?: error("Не удалось создать папку проекта")
        require(folder.isDirectory) { "Объект с таким именем уже существует" }
        return WorkspaceEntry(folder.name ?: safeName, folder.uri.toString(), true, 0)
    }

    fun createRandomManagedProject(rootUri: String): WorkspaceEntry {
        val root = directory(rootUri) ?: error("Папка проектов недоступна")
        repeat(50) { attempt ->
            val suffix = if (attempt > 12) "-${(10..99).random()}" else ""
            val name = "${projectAdjectives.random()}-${projectNouns.random()}$suffix"
            if (root.findFile(name) == null) {
                val folder = root.createDirectory(name) ?: error("Не удалось создать папку проекта")
                return WorkspaceEntry(name, folder.uri.toString(), true, 0)
            }
        }
        return createManagedProject(rootUri, "project-${System.currentTimeMillis()}")
    }

    fun resolve(rootUri: String, relativePath: String): DocumentFile? {
        var current = directory(rootUri) ?: return null
        val parts = runCatching { safeSegments(relativePath, allowEmpty = true) }.getOrNull() ?: return null
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
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "Файл больше 2 МБ" }
        val normalized = safeSegments(relativePath).joinToString("/")
        val parentPath = normalized.substringBeforeLast('/', "")
        val name = normalized.substringAfterLast('/')
        val parent = ensureDirectories(rootUri, parentPath)
        val existing = parent.findFile(name)
        val file = existing ?: parent.createFile(mimeType(name), name) ?: error("Не удалось создать файл")
        require(file.isFile) { "По этому пути находится папка" }
        updateText(file.uri.toString(), content)
    }

    fun createDirectoryRelative(rootUri: String, relativePath: String) {
        ensureDirectories(rootUri, relativePath)
    }

    fun deleteRelative(rootUri: String, relativePath: String): Boolean {
        val normalized = safeSegments(relativePath).joinToString("/")
        return resolve(rootUri, normalized)?.delete() == true
    }

    fun renameRelative(rootUri: String, relativePath: String, newName: String): Boolean {
        validateName(newName)
        val normalizedPath = safeSegments(relativePath).joinToString("/")
        val source = resolve(rootUri, normalizedPath) ?: error("Путь не найден: $relativePath")
        if (runCatching { rename(source.uri.toString(), newName) }.getOrDefault(false)) return true
        require(source.isFile) { "Этот Android-провайдер не поддерживает переименование папок" }
        val parentPath = normalizedPath.substringBeforeLast('/', "")
        val parent = (if (parentPath.isBlank()) directory(rootUri) else resolve(rootUri, parentPath))
            ?: error("Родительская папка недоступна")
        require(parent.findFile(newName) == null) { "Файл с таким названием уже существует" }
        val target = parent.createFile(mimeType(newName), newName) ?: error("Не удалось создать файл с новым названием")
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { output -> input.copyTo(output) }
                ?: error("Не удалось записать переименованный файл")
        } ?: error("Не удалось прочитать исходный файл")
        if (!source.delete()) { target.delete(); error("Не удалось удалить исходный файл") }
        return true
    }

    fun writeBytesRelative(rootUri: String, relativePath: String, bytes: ByteArray) {
        require(bytes.size <= MAX_BINARY_BYTES) { "Файл больше 25 МБ" }
        val normalized = safeSegments(relativePath).joinToString("/")
        val parent = ensureDirectories(rootUri, normalized.substringBeforeLast('/', ""))
        val name = normalized.substringAfterLast('/')
        val file = parent.findFile(name) ?: parent.createFile(mimeType(name), name) ?: error("Не удалось создать файл")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
            ?: error("Не удалось записать файл")
    }

    fun exportProject(rootUri: String, destination: File) {
        val root = directory(rootUri) ?: error("Папка проекта недоступна")
        fun copy(folder: DocumentFile, target: File, depth: Int) {
            require(depth <= 20) { "Слишком глубокая структура проекта" }
            target.mkdirs()
            folder.listFiles().forEach { child ->
                val safeName = child.name?.takeIf { it != "." && it != ".." } ?: return@forEach
                if (child.isDirectory && safeName in setOf(".git", ".gradle", "build", "node_modules", "__pycache__")) return@forEach
                val output = File(target, safeName)
                if (child.isDirectory) copy(child, output, depth + 1)
                else context.contentResolver.openInputStream(child.uri)?.use { input -> output.outputStream().use(input::copyTo) }
            }
        }
        copy(root, destination, 0)
    }

    fun syncProject(rootUri: String, source: File) {
        require(source.isDirectory) { "Python workspace недоступен" }
        source.walkTopDown().filter { it.isFile && it.extension.lowercase() != "pyc" && "__pycache__" !in it.invariantSeparatorsPath }.forEach { file ->
            val relative = file.relativeTo(source).invariantSeparatorsPath
            writeBytesRelative(rootUri, relative, file.readBytes())
        }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "py" -> "text/x-python"
        "js", "mjs", "cjs" -> "text/javascript"
        "ts", "tsx" -> "application/typescript"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "md" -> "text/markdown"
        "kt", "kts" -> "text/x-kotlin"
        "java" -> "text/x-java-source"
        "txt", "log", "csv" -> "text/plain"
        else -> "application/octet-stream"
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
        require(glob.length <= 256 && '\u0000' !in glob) { "Слишком сложный шаблон" }
        val regex = globToRegex(glob.ifBlank { "**/*" })
        return walkPaths(rootUri, limit).filter { regex.matches(it) }.take(limit)
    }

    fun searchCode(rootUri: String, pattern: String, limit: Int = 80): List<String> {
        require(pattern.length in 1..200) { "Регулярное выражение должно содержать 1–200 символов" }
        require(!Regex("\\([^)]*[+*][^)]*\\)[+*{]").containsMatchIn(pattern)) { "Потенциально опасное регулярное выражение" }
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
        safeSegments(relativePath, allowEmpty = true).forEach { part ->
            current = current.findFile(part)?.takeIf { it.isDirectory } ?: current.createDirectory(part)
                ?: error("Не удалось создать папку $part")
        }
        return current
    }

    private fun safeSegments(path: String, allowEmpty: Boolean = false): List<String> {
        require(path.length <= 1024 && '\u0000' !in path && !path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path)) {
            "Недопустимый путь"
        }
        val segments = path.replace('\\', '/').split('/').filter { it.isNotBlank() }
        require(allowEmpty || segments.isNotEmpty()) { "Недопустимый путь" }
        segments.forEach(::validateName)
        return segments
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." && name.length <= 255 &&
            '/' !in name && '\\' !in name && name.none { it.code < 32 }
        ) { "Недопустимое название" }
    }

    private companion object {
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        const val MAX_BINARY_BYTES = 25 * 1024 * 1024
    }
}
