package com.xanichka.xacode.data

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PythonRuntime(private val context: Context, private val workspace: WorkspaceRepository) {
    fun run(projectUri: String, relativePath: String, arguments: JSONArray = JSONArray()): String {
        require(relativePath.lowercase().endsWith(".py")) { "run_python accepts a .py file" }
        val runRoot = File(context.cacheDir, "python-runs/${UUID.randomUUID()}")
        require(runRoot.mkdirs()) { "Не удалось подготовить Python workspace" }
        return try {
            workspace.exportProject(projectUri, runRoot)
            val entry = File(runRoot, relativePath.replace('\\', '/'))
            require(entry.isFile && entry.canonicalPath.startsWith(runRoot.canonicalPath + File.separator)) { "Python-файл не найден: $relativePath" }
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
            val json = Python.getInstance().getModule("xacode_runner")
                .callAttr("run_file", runRoot.absolutePath, relativePath, arguments.toString())
                .toJava(String::class.java)
            syncProjectWithRetry(projectUri, runRoot)
            val result = JSONObject(json)
            buildString {
                append(if (result.optBoolean("ok")) "Python finished successfully" else "Python failed")
                result.optString("stdout").takeIf { it.isNotBlank() }?.let { append("\nstdout:\n").append(it.take(64_000)) }
                result.optString("stderr").takeIf { it.isNotBlank() }?.let { append("\nstderr:\n").append(it.take(64_000)) }
            }
        } finally {
            runRoot.deleteRecursively()
        }
    }

    private fun syncProjectWithRetry(projectUri: String, runRoot: File) {
        var lastFailure: Throwable? = null
        repeat(3) { attempt ->
            try {
                workspace.syncProject(projectUri, runRoot)
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                Log.w(TAG, "Project sync failed on attempt ${attempt + 1}/3", failure)
                if (attempt < 2) Thread.sleep(250L * (attempt + 1))
            }
        }
        throw lastFailure ?: IllegalStateException("Project sync failed")
    }

    private companion object { const val TAG = "XaCodePython" }
}
