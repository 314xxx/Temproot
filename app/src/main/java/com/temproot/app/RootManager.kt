package com.temproot.app

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class RootManager(private val context: Context) {

    companion object {
        private const val TAG = "RootManager"
        private const val TARGET_DIR = "/data/local/tmp"

        // 外部设备支持表路径（优先于内置配置，免发版适配新机型）
        const val EXTERNAL_CONFIG_PATH = "/sdcard/temproot_devices.json"

        // 已知 KernelSU 管理器包名（复用其 libksud.so，保证版本与管理器一致）
        private val KSU_MANAGERS = listOf(
            "me.weishu.kernelsu" to "KernelSU",
            "com.resukisu.resukisu" to "ReSukiSU"
        )
    }

    private data class DeviceEntry(
        val codename: String,
        val name: String,
        val kernels: List<String>
    )

    private data class DeviceConfig(
        val safePatchDate: String,
        val devices: List<DeviceEntry>,
        val source: String
    )

    // ==================== 设备支持表 ====================

    // 加载设备支持表：外部 JSON 优先，失败回退内置 assets
    private fun loadDeviceConfig(): DeviceConfig {
        val external = File(EXTERNAL_CONFIG_PATH)
        if (external.canRead()) {
            try {
                parseConfig(external.readText(), "外部配置")?.let { return it }
            } catch (e: Exception) {
                Log.w(TAG, "外部配置加载失败: ${e.message}")
            }
        }
        val json = context.assets.open("supported_devices.json")
            .bufferedReader().use { it.readText() }
        return parseConfig(json, "内置配置")
            ?: throw IllegalStateException("内置设备支持表解析失败")
    }

    private fun parseConfig(json: String, source: String): DeviceConfig? = try {
        val obj = JSONObject(json)
        val devices = mutableListOf<DeviceEntry>()
        obj.optJSONArray("devices")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val kernels = mutableListOf<String>()
                d.optJSONArray("kernels")?.let { ka ->
                    for (j in 0 until ka.length()) {
                        kernels.add(ka.getString(j))
                    }
                }
                devices.add(
                    DeviceEntry(
                        codename = d.getString("codename"),
                        name = d.optString("name", d.getString("codename")),
                        kernels = kernels
                    )
                )
            }
        }
        DeviceConfig(
            safePatchDate = obj.optString("safePatchDate", "2025-02-01"),
            devices = devices,
            source = source
        )
    } catch (e: Exception) {
        Log.w(TAG, "配置解析失败 ($source): ${e.message}")
        null
    }

    // ==================== 环境检查（分阶段） ====================

    suspend fun checkEnvironment(onLog: (String) -> Unit = {}): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 阶段 1/5: ADB 连接
            onLog("[1/5] 检查 ADB 连接...")
            if (!AdbShell.isConnected) {
                val connectResult = AdbShell.connect(onLog)
                if (connectResult.isFailure) {
                    onLog("  ❌ ADB 未连接: ${connectResult.exceptionOrNull()?.message}")
                    onLog("  ℹ 请确认已开启「无线调试」且 Wi-Fi 已连接")
                    return@withContext Result.failure(Exception("ADB 未连接: ${connectResult.exceptionOrNull()?.message}"))
                }
            }
            val (_, idOut) = AdbShell.exec("id")
            onLog("  ✓ ADB 已连接 ($idOut)")

            // 阶段 2/5: 设备 + 内核版本匹配
            onLog("[2/5] 检查设备与内核版本...")
            val config = loadDeviceConfig()
            val device = Build.DEVICE ?: "unknown"
            val entry = config.devices.find { it.codename == device }
            if (entry == null) {
                onLog("  ❌ 不支持的设备: $device")
                onLog("  ℹ 可创建 $EXTERNAL_CONFIG_PATH 添加支持")
                return@withContext Result.failure(Exception("不支持的设备: $device"))
            }
            onLog("  ✓ 设备: ${entry.name} ($device)")

            val kernel = getKernelVersion()
            if (entry.kernels.isNotEmpty()) {
                val matched = entry.kernels.any { kernel.startsWith(it) }
                if (!matched) {
                    onLog("  ❌ 内核不匹配: $kernel")
                    return@withContext Result.failure(
                        Exception("内核版本不匹配: $kernel (需要: ${entry.kernels.joinToString()})")
                    )
                }
            }
            onLog("  ✓ 内核: ${kernel.ifBlank { "未知(跳过匹配)" }}")

            // 阶段 3/5: 安全补丁
            onLog("[3/5] 检查安全补丁日期...")
            val patch = Build.VERSION.SECURITY_PATCH
            if (patch > config.safePatchDate) {
                onLog("  ❌ 补丁 $patch > ${config.safePatchDate}")
                return@withContext Result.failure(
                    Exception("安全补丁日期 ($patch) 过高，需要 <= ${config.safePatchDate}")
                )
            }
            onLog("  ✓ 补丁: $patch (支持表: ${config.source})")

            // 阶段 4/5: ksud 来源检测
            onLog("[4/5] 检测 ksud 来源...")
            val managerKsud = findManagerKsud()
            if (managerKsud != null) {
                onLog("  ✓ 复用已装管理器的 ksud (版本与管理器一致)")
                onLog("    来源: $managerKsud")
            } else {
                onLog("  ℹ 未检测到 KernelSU 管理器，使用内置 ksud")
                onLog("    提示: 安装 KernelSU/ReSukiSU 管理器可自动跟随其版本")
            }

            // 阶段 5/5: 文件传输
            onLog("[5/5] 传输文件到 $TARGET_DIR ...")
            prepareFiles(managerKsud, onLog)

            Result.success("${entry.name}, Patch: $patch")
        } catch (e: Exception) {
            onLog("  ❌ ${e.message}")
            Result.failure(e)
        }
    }

    // 获取内核版本 (uname -r)
    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        try {
            val (_, out) = AdbShell.exec("uname -r")
            out.trim().ifBlank { "" }
        } catch (e: Exception) {
            ""
        }
    }

    // 查找已安装管理器提供的 libksud.so，返回其路径（未找到返回 null）
    private suspend fun findManagerKsud(): String? {
        for ((pkg, label) in KSU_MANAGERS) {
            val (_, apkOut) = AdbShell.exec(
                "pm path $pkg 2>/dev/null | head -1 | sed 's/package://'"
            )
            val apk = apkOut.trim()
            if (apk.isBlank() || !apk.startsWith("/data/")) continue

            // native lib 目录（系统安装时自动解压）
            val libDir = apk.substringBeforeLast('/')
            val (_, libOut) = AdbShell.exec(
                "ls $libDir/lib/*/libksud.so 2>/dev/null | head -1"
            )
            val lib = libOut.trim()
            if (lib.isNotBlank() && lib.startsWith("/data/") && !lib.contains("ERROR")) {
                Log.i(TAG, "ksud from $label: $lib")
                return lib
            }
        }
        return null
    }

    // ==================== 文件传输 ====================

    // 传输文件：assets 提取到缓存目录后，通过 ADB push 直达目标路径
    // ksud 优先使用已装管理器版本（管理器升级后自动跟随，无需更新本应用）
    private suspend fun prepareFiles(managerKsud: String?, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "payload").apply { mkdirs() }

        // 1. cf: 始终使用内置 assets
        val cfTemp = extractAsset("cf", tempDir)
        if (!AdbShell.push(cfTemp, "$TARGET_DIR/cf")) {
            throw Exception("推送 cf 失败")
        }
        verifyFile("$TARGET_DIR/cf", cfTemp.length(), "cf", onLog)
        cfTemp.delete()

        // 2. ksud: 管理器版本优先（shell 身份直接 cp）
        if (managerKsud != null) {
            val (_, statOut) = AdbShell.exec("stat -c %s '$managerKsud' 2>/dev/null")
            val managerSize = statOut.trim().toLongOrNull()
            if (managerSize != null && managerSize > 0) {
                val (cpExit, cpOut) = AdbShell.exec("cp -f '$managerKsud' '$TARGET_DIR/ksud'")
                if (cpExit == 0) {
                    verifyFile("$TARGET_DIR/ksud", managerSize, "ksud", onLog)
                    return@withContext
                }
                onLog("  ⚠ cp 管理器 ksud 失败 ($cpOut)，回退内置版本")
            } else {
                onLog("  ⚠ 管理器 ksud 不可读，回退内置版本")
            }
        }
        val ksudTemp = extractAsset("ksud", tempDir)
        if (!AdbShell.push(ksudTemp, "$TARGET_DIR/ksud")) {
            throw Exception("推送 ksud 失败")
        }
        verifyFile("$TARGET_DIR/ksud", ksudTemp.length(), "ksud", onLog)
        ksudTemp.delete()
    }

    // 从 assets 提取到应用缓存目录
    private fun extractAsset(name: String, tempDir: File): File {
        val tempFile = File(tempDir, name)
        context.assets.open(name).use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    // 设置权限 + 大小校验
    private suspend fun verifyFile(path: String, expectedSize: Long, name: String, onLog: (String) -> Unit) {
        AdbShell.exec("chmod 777 $path")
        AdbShell.exec("chown shell:shell $path")

        val (_, sizeOut) = AdbShell.exec("stat -c %s $path 2>/dev/null || wc -c < $path")
        val actual = sizeOut.trim().toLongOrNull() ?: -1L
        if (actual != expectedSize) {
            throw Exception("$name 大小不匹配: 期望 $expectedSize, 实际 $actual")
        }
        onLog("  ✓ $name → $path ($actual bytes)")
    }

    // ==================== Root 执行 ====================

    // 执行 SELinux 临时宽容
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

            val (exitCode, output) = AdbShell.exec(cmd)
            onLog("[尝试 $count] cf 执行完成 (exit: $exitCode)")

            // 检查 SELinux 状态
            val (_, statusOutput) = AdbShell.exec("getenforce")
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

        val (exitCode, output) = AdbShell.exec(cmd)
        onLog("命令执行结果 (exit: $exitCode)")
        onLog("输出: $output")

        // 等待一下让 ksud 启动
        delay(2000)

        // 检查 ksud 是否运行
        val (_, psOutput) = AdbShell.exec("ps -ef | grep ksud | grep -v grep")
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

        // 内核版本
        status["kernel"] = getKernelVersion().ifBlank { "未知" }

        // SELinux 状态
        val (_, selinux) = AdbShell.exec("getenforce")
        status["selinux"] = selinux.trim()

        // ksud 进程
        val (_, ps) = AdbShell.exec("ps -ef | grep ksud | grep -v grep")
        status["ksud_running"] = if (ps.contains("ksud")) "运行中" else "未运行"

        // su 可用性
        val (_, suTest) = AdbShell.exec("su -c id 2>/dev/null || echo 'no_root'")
        status["root_available"] = if (suTest.contains("uid=0")) "已获取" else "未获取"

        status
    }
}
