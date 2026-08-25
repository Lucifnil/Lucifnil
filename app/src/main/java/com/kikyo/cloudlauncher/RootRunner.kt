package com.kikyo.cloudlauncher

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

enum class RootState {
    Checking,
    Granted,
    Denied
}

enum class RunPhase {
    Ready,
    RequiresCardKey,
    Starting,
    Succeeded,
    Failed
}

enum class LogStream {
    StandardOut,
    StandardError
}

data class CommandLog(
    val timestampMillis: Long,
    val stream: LogStream,
    val text: String
)

data class RootCheck(
    val granted: Boolean,
    val detail: String
)

data class CommandResult(
    val exitCode: Int?,
    val timedOut: Boolean,
    val detail: String
)

/** Result of looking for launchable shared objects in the fixed launcher directory. */
data class SoFileScanResult(
    val files: List<String>,
    val detail: String
)

/** Runs the selected native executable directly through the device's root manager. */
class RootRunner {
    suspend fun checkRoot(): RootCheck = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(ROOT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext RootCheck(false, "Root 检测超时")
            }

            // `id` only produces a short line, so it is safe to read after the
            // timed wait. Reading before waitFor could otherwise block forever
            // while a root manager is still displaying its authorization prompt.
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }

            val granted = process.exitValue() == 0 && output.contains("uid=0")
            RootCheck(
                granted = granted,
                detail = if (granted) "Root 已授权" else output.ifBlank { "未获得 Root 授权" }
            )
        } catch (error: Exception) {
            RootCheck(false, error.message ?: "无法调用 su")
        }
    }

    suspend fun runSelectedSo(
        directory: String,
        cardKey: String,
        soFileName: String,
        onLog: (CommandLog) -> Unit
    ): CommandResult = withContext(Dispatchers.IO) {
        try {
            if (!isSafeSoFileName(soFileName)) {
                return@withContext CommandResult(null, false, "无效的 .so 文件名")
            }
            coroutineScope {
                // Migrated from the former /data/adb/嗯嗯启动器.sh contract:
                // chmod 700 <selected.so> && exec <selected.so> -k <key> --record-mirror
                // The app now owns that fixed contract directly; no external
                // launcher script is required on the device.
                val command = directLaunchCommand(
                    directory = directory,
                    soFileName = soFileName,
                    cardKey = cardKey,
                )
                val process = ProcessBuilder("su", "-c", command).start()

                val stdout = async(Dispatchers.IO) {
                    pump(process.inputStream, LogStream.StandardOut, cardKey, onLog)
                }
                val stderr = async(Dispatchers.IO) {
                    pump(process.errorStream, LogStream.StandardError, cardKey, onLog)
                }

                val finished = process.waitFor(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                val result = if (finished) {
                    val exitCode = process.exitValue()
                    if (exitCode == 0) {
                        CommandResult(exitCode, false, "程序已成功结束")
                    } else {
                        CommandResult(exitCode, false, "程序退出码为 " + exitCode)
                    }
                } else {
                    onLog(
                        CommandLog(
                            timestampMillis = System.currentTimeMillis(),
                            stream = LogStream.StandardError,
                            text = "超过 5 分钟未退出，已停止等待。"
                        )
                    )
                    process.destroy()
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        process.waitFor(2, TimeUnit.SECONDS)
                    }
                    CommandResult(null, true, "程序运行超时")
                }

                stdout.await()
                stderr.await()
                result
            }
        } catch (error: Exception) {
            CommandResult(null, false, error.message ?: "启动 root 命令失败")
        }
    }

    /**
     * Lists direct `.so` children of [directory] as root.
     *
     * This deliberately uses the shell glob instead of `find`: Android devices
     * ship different toybox versions, while the glob is supported by the system
     * shell. `-f` accepts both ordinary files and a symlink whose target is a
     * regular file. We do not require the execute bit here because the app
     * grants the selected entry execute permission immediately before launch.
     */
    suspend fun listSoFiles(directory: String): SoFileScanResult = withContext(Dispatchers.IO) {
        try {
            val script = buildString {
                append("directory=")
                append(shellQuote(directory))
                append('\n')
                append("if [ ! -d \"\$directory\" ]; then\n")
                append("  printf '%s\\n' '__CLOUD_SCAN_ERROR__:目录不存在：'\"\$directory\"\n")
                append("  exit 2\n")
                append("fi\n")
                append("found=0\n")
                append("for so_path in \"\$directory\"/*.so; do\n")
                append("  [ -f \"\$so_path\" ] || continue\n")
                append("  so_name=\${so_path##*/}\n")
                append("  printf '__CLOUD_SO__:%s\\n' \"\$so_name\"\n")
                append("  found=\$((found + 1))\n")
                append("done\n")
                append("if [ \"\$found\" -eq 0 ]; then\n")
                append("  printf '%s\\n' '__CLOUD_SCAN_INFO__:没有找到以 .so 结尾的文件'\n")
                append("fi\n")
            }
            val command = "/system/bin/sh -c " + shellQuote(script)
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext SoFileScanResult(emptyList(), "读取 /data/adb 超时")
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val files = lines
                .filter { it.startsWith(SO_MARKER) }
                .map { it.removePrefix(SO_MARKER) }
                .filter(::isSafeSoFileName)
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)

            val reportedDetail = lines
                .firstOrNull { it.startsWith(SCAN_ERROR_MARKER) }
                ?.removePrefix(SCAN_ERROR_MARKER)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: lines
                    .firstOrNull { it.startsWith(SCAN_INFO_MARKER) }
                    ?.removePrefix(SCAN_INFO_MARKER)
                    ?.trim()
                    ?.takeIf(String::isNotBlank)

            when {
                process.exitValue() != 0 -> SoFileScanResult(
                    emptyList(),
                    reportedDetail ?: lines.joinToString(" · ").ifBlank { "Root 扫描命令失败" }
                )
                files.isNotEmpty() -> SoFileScanResult(
                    files,
                    "已读取到 ${files.size} 个 .so 文件"
                )
                else -> SoFileScanResult(emptyList(), reportedDetail ?: "没有找到 .so 文件")
            }
        } catch (error: Exception) {
            SoFileScanResult(emptyList(), error.message ?: "无法读取 /data/adb")
        }
    }

    private fun pump(
        stream: InputStream,
        logStream: LogStream,
        cardKey: String,
        onLog: (CommandLog) -> Unit
    ) {
        stream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val safeLine = if (cardKey.isNotEmpty()) {
                    line.replace(cardKey, REDACTED_CARD_KEY)
                } else {
                    line
                }
                onLog(CommandLog(System.currentTimeMillis(), logStream, safeLine))
            }
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun directLaunchCommand(
        directory: String,
        soFileName: String,
        cardKey: String,
    ): String = buildString {
        append("target_dir=").append(shellQuote(directory))
        append("; selected_name=").append(shellQuote(soFileName))
        append("; so_file=\"\$target_dir/\$selected_name\"")
        append("; if [ ! -f \"\$so_file\" ]; then ")
        append("printf '%s\\n' '启动失败：所选 .so 不存在或不是普通文件' >&2; exit 2; fi")
        append("; chmod 700 \"\$so_file\" || { ")
        append("printf '%s\\n' '启动失败：无法设置 .so 执行权限' >&2; exit 3; }")
        append("; exec \"\$so_file\" -k ").append(shellQuote(cardKey))
        append(" --record-mirror")
    }

    private fun isSafeSoFileName(name: String): Boolean {
        return name.endsWith(".so") &&
            name.isNotBlank() &&
            '/' !in name &&
            '\\' !in name &&
            '\n' !in name &&
            '\r' !in name &&
            '\u0000' !in name &&
            name != ".so"
    }

    private companion object {
        const val ROOT_CHECK_TIMEOUT_SECONDS = 8L
        const val SCAN_TIMEOUT_SECONDS = 12L
        const val RUN_TIMEOUT_SECONDS = 300L
        const val REDACTED_CARD_KEY = "••••"
        const val SO_MARKER = "__CLOUD_SO__:"
        const val SCAN_ERROR_MARKER = "__CLOUD_SCAN_ERROR__:"
        const val SCAN_INFO_MARKER = "__CLOUD_SCAN_INFO__:"
    }
}
