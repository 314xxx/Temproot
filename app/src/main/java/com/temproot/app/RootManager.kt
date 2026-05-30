package com.temproot.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class RootManager(private val context: Context) {

    private val tag = "RootManager"
    
    // 兼容的设备列表 (根据酷安原帖更新)
    private val supportedDevices = listOf(
        // K60 系列
        "socrates",      // Redmi K60 Pro
        "mondrian",      // Redmi K60 / POCO F5 Pro
        "rembrandt",     // Redmi K60E
        
        // K50 系列
        "rubens",        // Redmi K50
        "matisse",       // Redmi K50 Pro
        "diting",        // Redmi K50 至尊版 / 小米12T Pro
        "ingres",        // Redmi K50 电竞版 / POCO F4 GT
        
        // K40 系列
        "munch",         // Redmi K40S / POCO F4
        
        // Note 系列
        "marble",        // Redmi Note 12 Turbo / POCO F5
        
        // 小米12S 系列
        "mayfly",        // 小米12S
        
        // 小米13 系列
        "fuxi"           // 小米13
    )
    
    // 安全补丁截止日期 (2025-02-01)
    private val safePatchDate = "2025-02-01"

    suspend fun checkEnvironment(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 检查 Shizuku 权限
            if (!checkShizukuPermission()) {
                return@withContext Result.failure(Exception("Shizuku 权限未授予"))
            }

            // 2. 检查设备型号
            val device = Build.DEVICE ?: "unknown"
            if (!supportedDevices.contains(device)) {
                return@withContext Result.failure(Exception("不支持的设备: $device"))
            }

            // 3. 检查安全补丁日期
            val patch = Build.VERSION.SECURITY_PATCH
            if (patch > safePatchDate) {
                return@withContext Result.failure(Exception("安全补丁日期 ($patch) 过高，需要 <= $safePatchDate"))
            }

            // 4. 准备文件 (从 assets 复制到 /data/local/tmp)
            prepareFiles()

            return@withContext Result.success("环境检查通过: $device, Patch: $patch")
        } catch (e: Exception) {
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

    // 使用 Shizuku 执行 Shell 命令
    private suspend fun executeCommand(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append("ERROR: ").append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            Pair(exitCode, output.toString().trim())
        } catch (e: Exception) {
            Pair(-1, "Exception: ${e.message}")
        }
    }

    // 从 assets 复制文件到 /data/local/tmp
    private suspend fun prepareFiles() = withContext(Dispatchers.IO) {
        val files = listOf("cf", "ksud")
        files.forEach { fileName ->
            val targetFile = File("/data/local/tmp", fileName)
            context.assets.open(fileName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            // 设置权限
            executeCommand("chmod 777 ${targetFile.absolutePath}")
            executeCommand("chown shell:shell ${targetFile.absolutePath}")
        }
    }

    // 执行 SELinux 临时宽容 (cf 漏洞)
    suspend fun setSELinuxPermissive(
        maxRetries: Int = 50,
        onLog: (String) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("=== 开始执行 SELinux 宽容注入 (cf) ===")
        onLog("提示：此过程可能需要多次尝试，请耐心等待")
        
        var count = 0
        
        while (count < maxRetries) {
            count++
            onStatusUpdate("尝试 $count/$maxRetries")
            
            // 导出环境变量并执行 cf
            val cmd = """
                export SELINUX_VIRTUAL=0xffffffc00aa42b90
                /data/local/tmp/cf
            """.trimIndent()
            
            val (exitCode, output) = executeCommand(cmd)
            onLog("[尝试 $count] cf 执行完成 (exit: $exitCode)")
            
            // 检查 SELinux 状态
            val (_, statusOutput) = executeCommand("getenforce")
            val currentStatus = statusOutput.trim()
            onLog("[尝试 $count] SELinux: $currentStatus")
            
            if (currentStatus.equals("Permissive", ignoreCase = true)) {
                onLog("")
                onLog("================================")
                onLog("✅ SELinux 宽容成功！")
                onLog("总尝试次数: $count")
                onLog("================================")
                return@withContext true
            }
            
            if (output.contains("ERROR") || output.contains("Exception")) {
                onLog("[尝试 $count] 执行出错: $output")
            }
            
            // 失败重试间隔 1 秒
            delay(1000)
        }
        
        onLog("❌ 达到最大重试次数 ($maxRetries)，SELinux 宽容失败")
        false
    }

    // 执行 MQSAS 服务注入 (ksud)
    suspend fun injectKSUD(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        onLog("=== 开始执行 MQSAS 服务注入 (ksud) ===")
        
        // 构建 service call 命令
        val cmd = """
            service call miui.mqsas.IMQSNative 21 i32 1 s16 "/data/local/tmp/ksud" i32 1 s16 "late-load" s16 "/sdcard/ksulog.txt" i32 600
        """.trimIndent()
        
        val (exitCode, output) = executeCommand(cmd)
        onLog("命令执行结果 (exit: $exitCode)")
        onLog("输出: $output")
        
        // 等待一下让 ksud 启动
        delay(2000)
        
        // 检查 ksud 是否运行
        val (_, psOutput) = executeCommand("ps -ef | grep ksud | grep -v grep")
        val isRunning = psOutput.contains("ksud")
        
        if (isRunning) {
            onLog("✅ ksud 进程已成功启动")
            onLog("请查看 /sdcard/ksulog.txt 获取详细日志")
            return@withContext true
        } else {
            onLog("⚠️ ksud 进程未检测到，但注入命令已执行")
            onLog("请检查 /sdcard/ksulog.txt 或手动验证 Root 状态")
            return@withContext false
        }
    }

    // 检查当前 Root 状态
    suspend fun checkRootStatus(): Map<String, String> = withContext(Dispatchers.IO) {
        val status = mutableMapOf<String, String>()
        
        // SELinux 状态
        val (_, selinux) = executeCommand("getenforce")
        status["selinux"] = selinux.trim()
        
        // ksud 进程
        val (_, ps) = executeCommand("ps -ef | grep ksud | grep -v grep")
        status["ksud_running"] = if (ps.contains("ksud")) "运行中" else "未运行"
        
        // su 可用性
        val (_, suTest) = executeCommand("su -c id 2>/dev/null || echo 'no_root'")
        status["root_available"] = if (suTest.contains("uid=0")) "已获取" else "未获取"
        
        status
    }
}