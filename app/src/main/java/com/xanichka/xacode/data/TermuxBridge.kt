package com.xanichka.xacode.data

import android.Manifest
import android.app.IntentService
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class TermuxCommandResult(val stdout: String, val stderr: String, val exitCode: Int) {
    fun display(): String = buildString {
        if (stdout.isNotBlank()) append(stdout.trimEnd())
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append("stderr:\n").append(stderr.trimEnd())
        }
        if (isEmpty()) append("Command finished with no output")
        append("\n\nExit code: ").append(exitCode)
    }
}

class TermuxBridge(private val context: Context) {
    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
    }.isSuccess

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** RUN_COMMAND needs a real shared-storage path; arbitrary SAF/cloud providers cannot be used as cwd. */
    fun sharedStoragePath(treeUri: String): String? = runCatching {
        val uri = Uri.parse(treeUri)
        if (uri.authority != "com.android.externalstorage.documents") return null
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val volume = documentId.substringBefore(':')
        val relative = documentId.substringAfter(':', "").trim('/')
        val root = if (volume.equals("primary", true)) "/storage/emulated/0" else "/storage/$volume"
        if (relative.isBlank()) root else "$root/$relative"
    }.getOrNull()

    fun run(projectUri: String, command: String, timeoutSeconds: Long = 90): TermuxCommandResult {
        require(isInstalled()) { "Termux не установлен" }
        require(hasPermission()) { "Дайте XaCode разрешение «Run commands in Termux» в настройках Android" }
        require(command.length in 1..32_000 && '\u0000' !in command) { "Недопустимая команда" }
        validateCommand(command)
        val workDir = sharedStoragePath(projectUri)
            ?: error("Для Termux выберите папку проекта во внутренней общей памяти Android")
        val executionId = nextId.incrementAndGet()
        val future = CompletableFuture<TermuxCommandResult>()
        pending[executionId] = future
        val callback = Intent(context, TermuxResultService::class.java).putExtra(EXECUTION_ID, executionId)
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val resultIntent = PendingIntent.getService(context, executionId, callback, flags)
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, RUN_SERVICE)
            putExtra(EXTRA_PATH, "$PREFIX/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(EXTRA_WORKDIR, workDir)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_LABEL, "XaCode project command")
            putExtra(EXTRA_PENDING_INTENT, resultIntent)
        }
        try {
            context.startService(intent)
            return future.get(timeoutSeconds, TimeUnit.SECONDS)
        } finally {
            pending.remove(executionId)
        }
    }

    fun inspectRuntime(projectUri: String): TermuxCommandResult = run(
        projectUri,
        "for tool in bash python node npm git curl tar clang java; do printf '%s: ' \"\$tool\"; if command -v \"\$tool\" >/dev/null 2>&1; then (\"\$tool\" --version 2>&1 | head -n 1) || true; else echo missing; fi; done; pkg --version 2>&1 | head -n 1",
        120
    )

    fun repairNodeRuntime(projectUri: String): TermuxCommandResult = run(
        projectUri,
        "pkg update -y && pkg upgrade -y && pkg reinstall -y openssl nodejs && hash -r && node --version && npm --version",
        600
    )

    fun installPackages(projectUri: String, packages: List<String>): TermuxCommandResult {
        require(packages.isNotEmpty() && packages.size <= 8) { "Укажите от 1 до 8 пакетов" }
        require(packages.all { it in SAFE_PACKAGES }) { "Разрешены пакеты: ${SAFE_PACKAGES.joinToString()}" }
        return run(projectUri, "pkg install -y ${packages.joinToString(" ")}", 600)
    }

    private fun validateCommand(command: String) {
        require(!isCommandBlocked(command)) { "Команда заблокирована защитой XaCode" }
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val RUN_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val PREFIX = "/data/data/com.termux/files/usr"
        val SAFE_PACKAGES = sortedSetOf("nodejs", "nodejs-lts", "openssl", "openssl-tool", "git", "python", "clang", "make", "cmake", "rust", "golang", "openjdk-21", "curl", "wget", "tar", "zip", "unzip")
        private val BLOCKED_COMMAND_PARTS = listOf("rm -rf /", "rm -rf /*", "mkfs", "reboot", "shutdown", "su ", "tsu ", "/data/data/", "/system/", "/proc/", "/dev/")
        internal fun isCommandBlocked(command: String): Boolean = BLOCKED_COMMAND_PARTS.any(command.lowercase()::contains)
        private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        // TermuxConstants.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE is exactly "result".
        internal const val EXTRA_RESULT_BUNDLE = "result"
        internal const val EXTRA_STDOUT = "stdout"
        internal const val EXTRA_STDERR = "stderr"
        internal const val EXTRA_EXIT_CODE = "exitCode"
        internal const val EXTRA_ERROR = "errmsg"
        internal const val EXECUTION_ID = "xacode_execution_id"
        internal val pending = ConcurrentHashMap<Int, CompletableFuture<TermuxCommandResult>>()
        private val nextId = AtomicInteger(2000)
    }
}

@Suppress("DEPRECATION")
class TermuxResultService : IntentService("XaCodeTermuxResult") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val id = intent.getIntExtra(TermuxBridge.EXECUTION_ID, -1)
        val bundle = intent.getBundleExtra(TermuxBridge.EXTRA_RESULT_BUNDLE)
        val future = TermuxBridge.pending.remove(id) ?: return
        if (bundle == null) {
            future.completeExceptionally(IllegalStateException("Termux не вернул результат"))
            return
        }
        val error = bundle.getString(TermuxBridge.EXTRA_ERROR).orEmpty()
        if (error.isNotBlank()) future.completeExceptionally(IllegalStateException(error))
        else future.complete(TermuxCommandResult(
            stdout = bundle.getString(TermuxBridge.EXTRA_STDOUT).orEmpty(),
            stderr = bundle.getString(TermuxBridge.EXTRA_STDERR).orEmpty(),
            exitCode = bundle.getInt(TermuxBridge.EXTRA_EXIT_CODE, -1)
        ))
    }
}
