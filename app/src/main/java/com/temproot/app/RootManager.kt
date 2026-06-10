package com.temproot.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class RootManager(private val context: Context) {

    private val tag = "RootManager"

    private val supportedDevices = listOf(
        "socrates", "mondrian", "rembrandt", "rubens", "matisse",
        "diting", "ingres", "munch", "marble", "mayfly", "fuxi"
    )

    private val safePatchDate = "2026-02-01"

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.getBinder() != null && Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkEnvironment(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isShizukuReady()) {
                return@withContext Result.failure(Exception("Shizuku 服务未连接"))
            }
            if (!checkShizukuPermission()) {
                return@withContext Result.failure(Exception("Shizuku 权限未授予"))
            }
            val device = Build.DEVICE ?: "unknown"
            if (!supportedDevices.contains(device)) {
                return@withContext Result.failure(Exception("不支持的设备: $device"))
            }
            val patch = Build.VERSION.SECURITY_PATCH
            if (patch > safePatchDate) {
                return@withContext Result.failure(Exception("安全补丁日期 ($patch) 过高，需要 <= $safePatchDate"))
            }
            prepareFiles()
            return@withContext Result.success("环境检查通过: $device, Patch: $patch")
        } catch (e: Exception) {
            Log.e(tag, "checkEnvironment error", e)
            Result.failure(e)
        }
    }

    private fun checkShizukuPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun getService(): IShizukuService {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku 服务未连接")
        return IShizukuService.Stub.asInterface(binder)
    }

    private suspend fun executeCommand(command: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            try {
                val service = getService()
                val remote: IRemoteProcess =
                    service.newProcess(arrayOf("sh", "-c", command), null, null)

                val inputStream = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                val errorStream = ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val errorReader = BufferedReader(InputStreamReader(errorStream))

                val output = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                while (errorReader.readLine().also { line = it } != null) {
                    output.append("ERROR: ").append(line).append("\n")
                }

                val exitCode = remote.waitFor()
                inputStream.close()
                errorStream.close()
                Pair(exitCode, output.toString().trim())
            } catch (e: Exception) {
                Log.e(tag, "executeCommand failed", e)
                Pair(-1, "Exception: ${e.message}")
            }
        }

    private suspend fun prepareFiles() = withContext(Dispatchers.IO) {
        val files = listOf("cf", "ksud")
        files.forEach { fileName ->
            val targetPath = "/data/local/tmp/$fileName"

            // 1. 从 assets 提取到应用外部存储目录（shell 可访问）
            val tempDir = context.getExternalFilesDir(null)
                ?: throw Exception("无法访问外部存储目录")
            val tempFile = File(tempDir, fileName)
            context.assets.open(fileName).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 2. 使用 cp 复制到目标路径（与参考脚本相同的方式）
            val sourcePath = tempFile.absolutePath
            val (cpExit, cpOutput) = executeCommand("cp -f '$sourcePath' '$targetPath'")
            if (cpExit != 0) {
                // cp 失败时，尝试通过 cat 管道传输
                Log.w(tag, "cp 失败 (exit: $cpExit), 尝试 cat 管道: $cpOutput")
                transferViaPipe(fileName, targetPath)
            }

            // 3. 设置权限
            executeCommand("chmod 777 $targetPath")
            executeCommand("chown shell:shell $targetPath")

            // 4. 验证文件大小
            val expectedSize = tempFile.length()
            val (_, sizeOutput) = executeCommand("stat -c %s $targetPath 2>/dev/null || wc -c < $targetPath")
            val fileSize = sizeOutput.trim().toLongOrNull() ?: 0L
            if (fileSize != expectedSize) {
                throw Exception("$fileName 文件大小不匹配: 期望 $expectedSize, 实际 $fileSize")
            }

            // 5. 清理临时文件
            tempFile.delete()
        }
    }

    /**
     * 通过 cat stdin 管道传输文件（cp 不可用时的备用方案）
     */
    private suspend fun transferViaPipe(fileName: String, targetPath: String) =
        withContext(Dispatchers.IO) {
            val bytes = context.assets.open(fileName).use { it.readBytes() }
            val service = getService()
            val remote: IRemoteProcess = service.newProcess(
                arrayOf("sh", "-c", "cat > $targetPath"),
                null,
                null
            )

            try {
                val os = ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
                // 分块写入
                val bufferSize = 4096
                var offset = 0
                while (offset < bytes.size) {
                    val length = minOf(bufferSize, bytes.size - offset)
                    os.write(bytes, offset, length)
                    offset += length
                }
                os.flush()
                os.close()
            } catch (e: Exception) {
                try { remote.destroy() } catch (_: Exception) {}
                throw Exception("管道传输 $fileName 失败: ${e.message}")
            }

            val exitCode = remote.waitFor()
            if (exitCode != 0) {
                throw Exception("管道传输 $fileName 失败 (exit: $exitCode)")
            }
        }

    suspend fun setSELinuxPermissive(
        maxRetries: Int = 50,
        onLog: (String) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("=== 开始 SELinux 宽容注入 ===")

        var count = 0

        while (count < maxRetries) {
            count++
            onStatusUpdate("尝试 $count/$maxRetries")

            val cmd =
                "cd /data/local/tmp && export SELINUX_VIRTUAL=0xffffffc00aa42b90 && ./cf"
            val (exitCode, output) = executeCommand(cmd)
            onLog("[#$count] cf 执行完成 (exit: $exitCode)")

            val (_, statusOutput) = executeCommand("getenforce")
            val currentStatus = statusOutput.trim()
            onLog("[#$count] SELinux: $currentStatus")

            if (currentStatus.equals("Permissive", ignoreCase = true)) {
                onLog("")
                onLog("================================")
                onLog("SELinux 宽容成功！总尝试: $count 次")
                onLog("================================")
                return@withContext true
            }

            if (output.contains("ERROR") || output.contains("Exception")) {
                onLog("[#$count] 出错: $output")
            }

            delay(1000)
        }

        onLog("达到最大重试次数 ($maxRetries)，SELinux 宽容失败")
        false
    }

    suspend fun injectKSUD(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        onLog("=== 开始 MQSAS 服务注入 ===")

        val cmd =
            "service call miui.mqsas.IMQSNative 21 i32 1 s16 '/data/local/tmp/ksud' i32 1 s16 'late-load' s16 '/sdcard/ksulog.txt' i32 600"

        val (exitCode, output) = executeCommand(cmd)
        onLog("执行结果 (exit: $exitCode)")
        onLog("输出: $output")

        delay(2000)

        val (_, psOutput) = executeCommand("ps -ef | grep ksud | grep -v grep")
        val isRunning = psOutput.contains("ksud")

        if (isRunning) {
            onLog("ksud 进程已成功启动")
            onLog("日志: /sdcard/ksulog.txt")
            return@withContext true
        } else {
            onLog("ksud 进程未检测到，注入命令已执行")
            onLog("请检查 /sdcard/ksulog.txt 或手动验证 Root")
            return@withContext false
        }
    }

    suspend fun checkRootStatus(): Map<String, String> = withContext(Dispatchers.IO) {
        val status = mutableMapOf(
            "selinux" to "未知",
            "ksud_running" to "未知",
            "root_available" to "未知"
        )

        if (!isShizukuReady()) {
            return@withContext status
        }
        if (!checkShizukuPermission()) {
            return@withContext status
        }

        try {
            val (_, selinux) = executeCommand("getenforce")
            status["selinux"] = selinux.trim().ifEmpty { "未知" }

            val (_, ps) = executeCommand("ps -ef | grep ksud | grep -v grep")
            status["ksud_running"] = if (ps.contains("ksud")) "运行中" else "未运行"

            val (_, suTest) =
                executeCommand("su -c id 2>/dev/null || echo 'no_root'")
            status["root_available"] = if (suTest.contains("uid=0")) "已获取" else "未获取"
        } catch (e: Exception) {
            Log.e(tag, "checkRootStatus error", e)
        }

        status
    }
}