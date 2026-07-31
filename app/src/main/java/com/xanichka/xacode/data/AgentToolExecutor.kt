package com.xanichka.xacode.data

import org.json.JSONArray
import org.json.JSONObject

/** Safe Android subset of XaCode Desktop tools. Every path is resolved inside one SAF project folder. */
class AgentToolExecutor(
    private val repository: WorkspaceRepository,
    private val projectUri: String,
    private val confirmDestructiveActions: Boolean
) {
    val definitions: JSONArray = JSONArray().apply {
        put(tool("list_directory", "List files and folders inside the current project", JSONObject().put("path", string("Relative directory path; empty means project root"))))
        put(tool("read_file", "Read a text file from the current project", JSONObject().put("path", string("Relative file path")), listOf("path")))
        put(tool("write_file", "Create or overwrite a text file in the current project", JSONObject().put("path", string("Relative file path")).put("content", string("Complete file content")), listOf("path", "content")))
        put(tool("edit_file", "Replace an exact text fragment in a project file", JSONObject().put("path", string("Relative file path")).put("search", string("Exact text to replace")).put("replace", string("Replacement text")), listOf("path", "search", "replace")))
        put(tool("create_directory", "Create a directory and missing parent directories", JSONObject().put("path", string("Relative directory path")), listOf("path")))
        put(tool("search_files", "Find project files and folders by name", JSONObject().put("query", string("Name fragment")), listOf("query")))
        put(tool("rename_file", "Rename a file or directory", JSONObject().put("path", string("Current relative path")).put("newName", string("New name only")), listOf("path", "newName")))
        put(tool("delete_file", "Delete a file or directory recursively", JSONObject().put("path", string("Relative path")), listOf("path")))
    }
    val anthropicDefinitions: JSONArray = JSONArray().apply {
        for (index in 0 until definitions.length()) {
            val function = definitions.getJSONObject(index).getJSONObject("function")
            put(JSONObject().put("name", function.getString("name")).put("description", function.getString("description")).put("input_schema", function.getJSONObject("parameters")))
        }
    }

    fun execute(name: String, arguments: String): String = runCatching {
        val args = JSONObject(arguments.ifBlank { "{}" })
        when (name) {
            "list_directory" -> repository.listRelative(projectUri, args.optString("path")).joinToString("\n") { (if (it.isDirectory) "[DIR] " else "[FILE] ") + it.name }
                .ifBlank { "Directory is empty" }
            "read_file" -> repository.readRelative(projectUri, args.getString("path"))
            "write_file" -> { repository.writeRelative(projectUri, args.getString("path"), args.getString("content")); "File written successfully" }
            "edit_file" -> {
                val path = args.getString("path"); val source = repository.readRelative(projectUri, path); val search = args.getString("search")
                require(source.contains(search)) { "Exact search text was not found" }
                repository.writeRelative(projectUri, path, source.replaceFirst(search, args.getString("replace"))); "File edited successfully"
            }
            "create_directory" -> { repository.createDirectoryRelative(projectUri, args.getString("path")); "Directory created successfully" }
            "search_files" -> repository.search(projectUri, args.getString("query")).joinToString("\n").ifBlank { "Nothing found" }
            "rename_file" -> {
                val file = repository.resolve(projectUri, args.getString("path")) ?: error("Path not found")
                require(repository.rename(file.uri.toString(), args.getString("newName"))) { "Rename failed" }; "Renamed successfully"
            }
            "delete_file" -> {
                if (confirmDestructiveActions) "Deletion requires user confirmation. Ask the user to delete it from the project file screen or disable confirmation in settings."
                else { require(repository.deleteRelative(projectUri, args.getString("path"))) { "Delete failed" }; "Deleted successfully" }
            }
            else -> "Unknown tool: $name"
        }
    }.getOrElse { "Tool error: ${it.message}" }

    private fun string(description: String) = JSONObject().put("type", "string").put("description", description)
    private fun tool(name: String, description: String, properties: JSONObject, required: List<String> = emptyList()) =
        JSONObject().put("type", "function").put("function", JSONObject().put("name", name).put("description", description).put("parameters", JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray(required))))
}
