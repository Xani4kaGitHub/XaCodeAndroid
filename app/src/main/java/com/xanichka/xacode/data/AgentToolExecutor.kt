package com.xanichka.xacode.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection

/** Safe Android subset of XaCode Desktop tools. Every path is resolved inside one SAF project folder. */
class AgentToolExecutor(
    private val repository: WorkspaceRepository,
    private val projectUri: String,
    private val pythonRuntime: PythonRuntime,
    private val termuxBridge: TermuxBridge,
    private val destructiveToolsEnabled: Boolean = false,
    private val networkDownloadsEnabled: Boolean = false,
    private val pythonExecutionEnabled: Boolean = false,
    private val termuxExecutionEnabled: Boolean = false
) {
    private val backups = linkedMapOf<String, String>()
    private val todos = linkedMapOf<Int, String>()
    private var nextTodoId = 1
    val definitions: JSONArray = JSONArray().apply {
        put(tool("list_directory", "List files and folders inside the current project", JSONObject().put("path", string("Relative directory path; empty means project root"))))
        put(tool("read_file", "Read a text file, optionally by inclusive line range", JSONObject().put("path", string("Relative file path")).put("startLine", integer("First line, starting at 1")).put("endLine", integer("Last line, inclusive")), listOf("path")))
        put(tool("write_file", "Create or overwrite a text file in the current project", JSONObject().put("path", string("Relative file path")).put("content", string("Complete file content")), listOf("path", "content")))
        put(tool("edit_file", "Replace an exact text fragment in a project file", JSONObject().put("path", string("Relative file path")).put("search", string("Exact text to replace")).put("replace", string("Replacement text")), listOf("path", "search", "replace")))
        put(tool("create_directory", "Create a directory and missing parent directories", JSONObject().put("path", string("Relative directory path")), listOf("path")))
        put(tool("find_files", "Find files by glob such as src/**/*.ts", JSONObject().put("glob", string("Glob pattern")), listOf("glob")))
        put(tool("file_info", "Get file existence, size, type and kind", JSONObject().put("path", string("Relative path")), listOf("path")))
        put(tool("search_code", "Regex search through text files", JSONObject().put("pattern", string("Regular expression")), listOf("pattern")))
        put(tool("inspect_workspace", "Inspect project tree and detect common language manifests", JSONObject()))
        put(tool("rename_file", "Rename a file or directory", JSONObject().put("path", string("Current relative path")).put("newName", string("New name only")), listOf("path", "newName")))
        if (destructiveToolsEnabled) put(tool("delete_file", "Delete a file or directory recursively", JSONObject().put("path", string("Relative path")), listOf("path")))
        put(tool("apply_patch", "Apply a unified diff patch to one text file", JSONObject().put("path", string("Relative file path")).put("patch", string("Unified diff text")), listOf("path", "patch")))
        put(tool("undo_file", "Restore the last in-memory backup made during this task", JSONObject().put("path", string("Relative file path")), listOf("path")))
        put(tool("manage_todos", "Manage a task-local todo list", JSONObject().put("action", enumString("add", "list", "complete", "delete")).put("textOrId", string("Todo text or numeric id")), listOf("action")))
        put(tool("finish_task", "Finish after checking that requested work is complete", JSONObject().put("summary", string("Short result summary")), listOf("summary")))
        if (networkDownloadsEnabled) put(tool("http_download", "Download a file from a public HTTPS URL into the current project", JSONObject().put("url", string("Public HTTPS source URL")).put("path", string("Relative destination path with extension")), listOf("url", "path")))
        if (pythonExecutionEnabled) put(tool("run_python", "Run a Python .py file from the current Android project and return stdout and stderr", JSONObject().put("path", string("Relative .py entry file")).put("arguments", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))), listOf("path")))
        if (termuxExecutionEnabled) put(tool("run_command", "Run a shell command with Termux in the current Android project. Use this for node, npm, git, compilers and project tests. Return stdout, stderr and exit code.", JSONObject().put("command", string("Shell command to run inside the project directory")).put("timeoutSeconds", integer("Timeout from 1 to 180 seconds")), listOf("command")))
    }
    val anthropicDefinitions: JSONArray = JSONArray().apply {
        for (index in 0 until definitions.length()) {
            val function = definitions.getJSONObject(index).getJSONObject("function")
            put(JSONObject().put("name", function.getString("name")).put("description", function.getString("description")).put("input_schema", function.getJSONObject("parameters")))
        }
    }
    val toolNames: String
        get() = (0 until definitions.length()).joinToString(", ") { index ->
            definitions.getJSONObject(index).getJSONObject("function").getString("name")
        }

    fun execute(name: String, arguments: String): String = runCatching {
        val args = JSONObject(arguments.ifBlank { "{}" })
        when (name) {
            "list_directory" -> repository.listRelative(projectUri, args.optString("path")).joinToString("\n") { (if (it.isDirectory) "[DIR] " else "[FILE] ") + it.name }
                .ifBlank { "Directory is empty" }
            "read_file" -> repository.readRelative(projectUri, args.getString("path"), args.optInt("startLine").takeIf { args.has("startLine") }, args.optInt("endLine").takeIf { args.has("endLine") })
            "write_file" -> { val path = args.getString("path"); backup(path); repository.writeRelative(projectUri, path, args.getString("content")); "File written successfully" }
            "edit_file" -> {
                val path = args.getString("path"); val source = repository.readRelative(projectUri, path); val search = args.getString("search")
                require(source.contains(search)) { "Exact search text was not found" }
                backups[path] = source
                repository.writeRelative(projectUri, path, source.replaceFirst(search, args.getString("replace"))); "File edited successfully"
            }
            "create_directory" -> { repository.createDirectoryRelative(projectUri, args.getString("path")); "Directory created successfully" }
            "find_files" -> repository.findFiles(projectUri, args.getString("glob")).joinToString("\n").ifBlank { "Nothing found" }
            "file_info" -> repository.fileInfo(projectUri, args.getString("path"))
            "search_code" -> repository.searchCode(projectUri, args.getString("pattern")).joinToString("\n").ifBlank { "Nothing found" }
            "inspect_workspace" -> repository.inspectWorkspace(projectUri)
            "rename_file" -> {
                val newName = args.getString("newName"); require('/' !in newName && '\\' !in newName) { "newName must not contain a path" }
                require(repository.renameRelative(projectUri, args.getString("path"), newName)) { "Rename failed" }; "Renamed successfully"
            }
            "delete_file" -> {
                require(destructiveToolsEnabled) { "Destructive tools are disabled in settings" }
                require(repository.deleteRelative(projectUri, args.getString("path"))) { "Delete failed" }; "Deleted successfully"
            }
            "apply_patch" -> { val path = args.getString("path"); val source = repository.readRelative(projectUri, path); backups[path] = source; repository.writeRelative(projectUri, path, applyUnifiedDiff(source, args.getString("patch"))); "Patch applied successfully" }
            "undo_file" -> { val path = args.getString("path"); val content = backups.remove(path) ?: error("No backup for $path"); repository.writeRelative(projectUri, path, content); "File restored" }
            "manage_todos" -> manageTodos(args)
            "finish_task" -> "Task finished: ${args.getString("summary")}"
            "http_download" -> { require(networkDownloadsEnabled) { "Network downloads are disabled in settings" }; download(args.getString("url"), args.getString("path")) }
            "run_python" -> { require(pythonExecutionEnabled) { "Python execution is disabled in settings" }; pythonRuntime.run(projectUri, args.getString("path"), args.optJSONArray("arguments") ?: JSONArray()) }
            "run_command" -> {
                require(termuxExecutionEnabled) { "Termux execution is disabled in settings" }
                termuxBridge.run(projectUri, args.getString("command"), args.optLong("timeoutSeconds", 90).coerceIn(1, 180)).display()
            }
            else -> "Unknown tool: $name"
        }
    }.getOrElse { "Tool error: ${it.message}" }

    private fun string(description: String) = JSONObject().put("type", "string").put("description", description)
    private fun integer(description: String) = JSONObject().put("type", "integer").put("minimum", 1).put("description", description)
    private fun enumString(vararg values: String) = JSONObject().put("type", "string").put("enum", JSONArray(values.toList()))
    private fun tool(name: String, description: String, properties: JSONObject, required: List<String> = emptyList()) =
        JSONObject().put("type", "function").put("function", JSONObject().put("name", name).put("description", description).put("parameters", JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray(required))))

    private fun backup(path: String) { runCatching { repository.readRelative(projectUri, path) }.getOrNull()?.let { backups[path] = it } }

    private fun manageTodos(args: JSONObject): String = when (args.getString("action")) {
        "add" -> { val id = nextTodoId++; todos[id] = args.getString("textOrId"); "Added todo $id" }
        "complete", "delete" -> { val id = args.getString("textOrId").toInt(); todos.remove(id); "Removed todo $id" }
        "list" -> todos.entries.joinToString("\n") { "${it.key}. ${it.value}" }.ifBlank { "No todos" }
        else -> "Unknown todo action"
    }

    private fun applyUnifiedDiff(source: String, patch: String): String {
        var result = source
        patch.split(Regex("(?=^@@)", RegexOption.MULTILINE)).filter { it.startsWith("@@") }.forEach { hunk ->
            val old = mutableListOf<String>(); val replacement = mutableListOf<String>()
            hunk.lineSequence().drop(1).forEach { line ->
                when { line.startsWith("-") && !line.startsWith("---") -> old += line.drop(1); line.startsWith("+") && !line.startsWith("+++") -> replacement += line.drop(1); line.startsWith(" ") -> { old += line.drop(1); replacement += line.drop(1) } }
            }
            val needle = old.joinToString("\n"); require(needle.isNotEmpty() && result.contains(needle)) { "Patch context was not found" }
            result = result.replaceFirst(needle, replacement.joinToString("\n"))
        }
        return result
    }

    private fun download(sourceUrl: String, path: String): String {
        var url = NetworkSecurity.publicDownloadUrl(sourceUrl)
        repeat(6) { redirectCount ->
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = false
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirectCount < 5) { "Too many redirects" }
                    val location = connection.getHeaderField("Location") ?: error("Redirect has no location")
                    url = NetworkSecurity.publicDownloadUrl(url.toURI().resolve(location).toString())
                    return@repeat
                }
                require(status in 200..299) { "Download failed with HTTP $status" }
                val declared = connection.contentLengthLong
                require(declared < 0 || declared <= 25L * 1024 * 1024) { "File is larger than 25 MB" }
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16_384)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 25 * 1024 * 1024) { "File is larger than 25 MB" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                repository.writeBytesRelative(projectUri, path, bytes)
                return "Downloaded ${bytes.size} bytes to $path"
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }
}
