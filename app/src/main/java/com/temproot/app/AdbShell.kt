package com.temproot.app

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ADB 连接管理器：基于 Kadb 实现无线调试自配对，无需 Shizuku。
 *
 * 使用流程：
 * 1. 用户开启系统"无线调试"，点"使用配对码配对设备"获取 配对端口 + 6位配对码
 * 2. [pair] 完成配对（一次性，证书持久化到应用私有目录）
 * 3. [connect] 连接无线调试主端口，获得 shell 权限
 */
object AdbShell {

    private const val PREF_NAME = "adb_prefs"
    private const val KEY_PORT = "adb_port"

    /** ADB 主机固定为本机回环地址（无线调试在 localhost 监听） */
    private const val HOST = "127.0.0.1"

    sealed interface State {
        data object Disconnected : State
        data object Connected : State
    }

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state

    private var kadb: Kadb? = null
    private val mutex = Mutex()
    private var appContext: Context? = null

    /** 初始化：恢复持久化的 ADB 证书（应用启动时调用一次） */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        val certFile = File(context.filesDir, "kadb_cert.pem")
        val keyFile = File(context.filesDir, "kadb_key.pem")
        if (certFile.exists() && keyFile.exists()) {
            runCatching {
                KadbCert.set(certFile.readBytes(), keyFile.readBytes())
            }
        }
    }

    /** 将当前内存中的证书持久化到应用私有目录 */
    private fun persistCert() {
        val ctx = appContext ?: return
        runCatching {
            val (cert, key) = KadbCert.getOrError()
            File(ctx.filesDir, "kadb_cert.pem").writeBytes(cert)
            File(ctx.filesDir, "kadb_key.pem").writeBytes(key)
        }
    }

    /** 已保存的连接端口（自动发现失败时的备用端口） */
    fun getSavedPort(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PORT, 0)
    }

    fun savePort(context: Context, port: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PORT, port).apply()
    }

    /** 是否已完成过配对（证书已持久化） */
    fun isPaired(context: Context): Boolean {
        return File(context.filesDir, "kadb_cert.pem").exists() &&
                File(context.filesDir, "kadb_key.pem").exists()
    }

    /**
     * 配对：只需输入无线调试"使用配对码配对设备"弹窗中显示的 6 位配对码。
     * 配对端口通过 mDNS 自动发现（参考 AxManager/Shizuku 方案）。
     * @param discoveredPort 调用方已发现的配对端口；null 时内部自动发现
     * 成功后证书持久化，之后无需再次配对（除非清除应用数据或重置 adb 授权）。
     */
    suspend fun pair(pairingCode: String, discoveredPort: Int? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(pairingCode.isNotBlank()) { "配对码不能为空" }
            val ctx = appContext ?: error("AdbShell 未初始化")

            val port = discoveredPort
                ?: MdnsDiscovery.discoverPort(ctx, MdnsDiscovery.TLS_PAIRING, timeoutMs = 15_000)
                ?: throw Exception("未发现配对服务，请先打开「使用配对码配对设备」弹窗")

            Kadb.pair(HOST, port, pairingCode.trim())
        }.onSuccess {
            // 配对成功后保存证书，重启应用后无需重新配对
            persistCert()
        }
    }

    /**
     * 连接本机 adbd：mDNS 自动发现当前端口（无线调试每次开关端口会变，自动发现免手动更新）。
     * 发现失败时回退到上次成功连接的备用端口。
     */
    suspend fun connect(onLog: (String) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val ctx = appContext ?: error("AdbShell 未初始化")

                val discovered = MdnsDiscovery.discoverPort(ctx, MdnsDiscovery.TLS_CONNECT, 8_000)
                val port = discovered
                    ?: getSavedPort(ctx).takeIf { it > 0 }
                    ?: throw Exception("未发现无线调试服务\n请确认「无线调试」已开启且 Wi-Fi 已连接")

                if (discovered != null) {
                    onLog("✓ 发现无线调试端口: $discovered")
                } else {
                    onLog("ℹ 自动发现失败，使用备用端口: $port")
                }

                closeLocked()
                val k = Kadb.create(HOST, port)
                val resp = k.shell("id")
                check(resp.exitCode == 0) { "连接验证失败: ${resp.allOutput.trim()}" }

                if (discovered != null) savePort(ctx, discovered)
                kadb = k
                _state.value = State.Connected
            }.onFailure {
                closeLocked()
            }
        }
    }

    /** 断开连接 */
    fun disconnect() {
        kadb?.let { runCatching { it.close() } }
        kadb = null
        _state.value = State.Disconnected
    }

    private fun closeLocked() {
        kadb?.let { runCatching { it.close() } }
        kadb = null
        _state.value = State.Disconnected
    }

    /** 执行 shell 命令，返回 (exitCode, output) */
    suspend fun exec(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val k = kadb ?: return@withContext Pair(-1, "ADB 未连接")
        runCatching {
            val resp = k.shell(command)
            Pair(resp.exitCode, resp.allOutput.trim())
        }.getOrElse { e ->
            // 连接失效时更新状态
            _state.value = State.Disconnected
            Pair(-1, "执行失败: ${e.message}")
        }
    }

    /** 通过 ADB sync 协议直接推送文件到设备路径（shell 权限可达处，如 /data/local/tmp） */
    suspend fun push(src: File, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val k = kadb ?: return@withContext false
        runCatching {
            k.push(src, remotePath)
            true
        }.getOrElse {
            _state.value = State.Disconnected
            false
        }
    }

    val isConnected: Boolean get() = _state.value is State.Connected
}
