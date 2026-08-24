package com.temproot.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.coroutines.resume

/**
 * mDNS 服务发现：自动发现本机 adbd 广播的无线调试端口。
 * 框架移植自 AxManager（Shizuku 同源方案）：
 * - NsdManager 调用统一在主线程 Looper 上执行（子线程调用会抛 IllegalStateException 导致闪退）
 * - 同一时刻只 resolve 一个服务（NsdManager 限制，并发 resolve 必定失败）
 * - 所有回调防御式捕获，任何异常都不允许逃逸到主线程 Looper
 * - 发现启动失败 / 未命中本机服务时自动重启扫描（退避重试 / indefinite 持续重试）
 *
 * 系统行为：
 * - 「无线调试」开启时广播 _adb-tls-connect._tcp（主连接端口）
 * - 「使用配对码配对设备」弹窗打开时才广播 _adb-tls-pairing._tcp（配对端口）
 */
object MdnsDiscovery {

    /** 无线调试主连接服务 */
    const val TLS_CONNECT = "_adb-tls-connect._tcp"

    /** 配对服务（配对码弹窗打开时才广播） */
    const val TLS_PAIRING = "_adb-tls-pairing._tcp"

    private const val TAG = "MdnsDiscovery"

    /**
     * 发现本机无线调试端口（挂起函数，内部在主线程运行 NsdManager）。
     * @param indefinite true 时持续重试扫描直到超时（适用于配对码弹窗监听场景）
     * @return 端口号；超时或失败返回 null
     */
    suspend fun discoverPort(
        context: Context,
        serviceType: String,
        timeoutMs: Long = 10_000,
        indefinite: Boolean = false
    ): Int? {
        val appContext = context.applicationContext
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val main = Handler(Looper.getMainLooper())
                // 实例容器：取消回调可能早于初始化 post 执行，不能使用 lateinit
                val holder = arrayOfNulls<AdbMdns>(1)

                // 关键：NsdManager 必须在带 Looper 的线程上调用，
                // 直接在 IO 线程调用会抛 IllegalStateException（闪退根因），因此统一 post 到主线程
                main.post {
                    if (cont.isActive) {
                        holder[0] = AdbMdns(appContext, serviceType, indefinite) { port ->
                            if (port > 0 && cont.isActive) cont.resume(port)
                        }.also { it.start() }
                    }
                }
                cont.invokeOnCancellation {
                    main.post { holder[0]?.let { m -> runCatching { m.stop() } } }
                }
            }
        }
    }

    // ==================== 以下移植自 AxManager AdbMdns ====================

    private class AdbMdns(
        context: Context,
        private val serviceType: String,
        private val indefinite: Boolean,
        private val observer: (Int) -> Unit
    ) {
        private var registered = false
        private var running = false
        private var resolving = false
        private var restartScheduled = false
        private var attempts = 0
        private var serviceName: String? = null
        private val listener = DiscoveryListener(this)
        private val nsdManager: NsdManager? =
            runCatching { context.getSystemService(NsdManager::class.java) }.getOrNull()
        private val handler = Handler(Looper.getMainLooper())

        fun start() {
            if (running) return
            running = true
            attempts = 0
            discover()
        }

        fun stop() {
            if (!running) return
            running = false
            handler.removeCallbacksAndMessages(null)
            if (registered) {
                registered = false
                runCatching { nsdManager?.stopServiceDiscovery(listener) }
            }
        }

        private fun discover() {
            runCatching {
                nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure {
                Log.w(TAG, "discoverServices failed: ${it.message}")
                scheduleRestart()
            }
        }

        private fun scheduleRestart() {
            if (!running || restartScheduled) return
            if (!indefinite && attempts >= 5) return
            attempts++
            restartScheduled = true
            val delay = if (indefinite) 2_000L else attempts * 1_000L
            handler.postDelayed({
                restartScheduled = false
                if (!running) return@postDelayed
                if (registered) {
                    // 先停掉当前会话再重启，规避部分机型上的会话冲突
                    registered = false
                    runCatching { nsdManager?.stopServiceDiscovery(listener) }
                    handler.postDelayed({ if (running) discover() }, 300L)
                } else {
                    discover()
                }
            }, delay)
        }

        // ---- NsdManager 回调（主线程），全部防御式，异常不得逃逸 ----

        fun onDiscoveryStart() {
            registered = true
        }

        fun onDiscoveryStartFailed() {
            registered = false
            scheduleRestart()
        }

        fun onDiscoveryStop() {
            registered = false
        }

        fun onServiceFound(info: NsdServiceInfo) {
            // NsdManager 同一时刻只允许一个 resolveService，占用中直接跳过（下轮扫描会再发现）
            if (resolving) return
            resolving = true
            runCatching {
                nsdManager?.resolveService(info, ResolveListener(this))
            }.onFailure {
                resolving = false
                Log.w(TAG, "resolveService failed: ${it.message}")
            }
        }

        fun onServiceLost(info: NsdServiceInfo) {
            if (info.serviceName == serviceName) observer(-1)
        }

        fun onResolveFailed() {
            resolving = false
        }

        fun onServiceResolved(resolvedService: NsdServiceInfo) {
            resolving = false
            if (!running) return
            if (isLocalDevice(resolvedService) && isPortInUse(resolvedService.port)) {
                serviceName = resolvedService.serviceName
                stop()
                observer(resolvedService.port)
            } else {
                scheduleRestart()
            }
        }

        /** 解析出的服务地址是否为本机地址（避免连到局域网其他 Android 设备） */
        private fun isLocalDevice(info: NsdServiceInfo): Boolean = runCatching {
            val hostAddr = info.host?.hostAddress ?: return false
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .any { it.hostAddress == hostAddr }
        }.getOrDefault(false)

        /** 端口在 127.0.0.1 上是否真实被监听（占用 = true，用于过滤幻影服务） */
        private fun isPortInUse(port: Int) = try {
            ServerSocket().use {
                it.bind(InetSocketAddress("127.0.0.1", port), 1)
                false
            }
        } catch (e: IOException) {
            true
        }
    }

    private class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            adbMdns.onDiscoveryStart()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStartDiscoveryFailed: $serviceType, $errorCode")
            adbMdns.onDiscoveryStartFailed()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")
            adbMdns.onDiscoveryStop()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "onServiceFound: ${serviceInfo.serviceName}")
            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceLost(serviceInfo)
        }
    }

    private class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "onResolveFailed: ${nsdServiceInfo.serviceName}, $errorCode")
            adbMdns.onResolveFailed()
        }

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(nsdServiceInfo)
        }
    }
}
