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
 * - 发现失败自动重启扫描（指数退避 / indefinite 模式持续重试）
 * - 校验解析出的地址是否为本机地址（只连自己的 adbd，不连局域网其他设备）
 * - 校验端口在 127.0.0.1 上真实被占用（过滤幻影服务）
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
    ): Int? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val appContext = context.applicationContext
            val main = Handler(Looper.getMainLooper())

            // 关键：NsdManager 必须在带 Looper 的线程上调用，
            // 直接在 IO 线程调用会抛 IllegalStateException（闪退根因），因此统一 post 到主线程
            main.post {
                lateinit var mdns: AdbMdns
                mdns = AdbMdns(appContext, serviceType, indefinite) { port ->
                    if (port > 0 && cont.isActive) {
                        cont.resume(port)
                        mdns.stop()
                    }
                    // port == -1 表示服务丢失，继续扫描
                }
                cont.invokeOnCancellation { main.post { mdns.stop() } }
                mdns.start()
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
        private var serviceName: String? = null
        private val listener = DiscoveryListener(this)
        private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
        private val handler = Handler(Looper.getMainLooper())
        private var restartScheduled = false
        private var attempts = 0

        fun start() {
            if (running) return
            running = true
            attempts = 0
            if (!registered) {
                runCatching {
                    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                }.onFailure { Log.w(TAG, "discoverServices failed: ${it.message}") }
            }
        }

        fun stop() {
            if (!running) return
            running = false
            handler.removeCallbacksAndMessages(null)
            if (registered) {
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
        }

        fun onDiscoveryStart() { registered = true }

        fun onDiscoveryStop() { registered = false }

        fun onServiceFound(info: NsdServiceInfo) {
            runCatching { nsdManager.resolveService(info, ResolveListener(this)) }
        }

        fun onServiceLost(info: NsdServiceInfo) {
            if (info.serviceName == serviceName) observer(-1)
        }

        fun onServiceResolved(resolvedService: NsdServiceInfo) {
            if (running && isLocalDevice(resolvedService) && isPortInUse(resolvedService.port)) {
                serviceName = resolvedService.serviceName
                observer(resolvedService.port)
            } else if (running && (indefinite || attempts < 5) && !restartScheduled) {
                // 未命中本机服务：重启扫描（indefinite 模式每 2s 持续重试）
                attempts++
                restartScheduled = true
                val delay = if (indefinite) 2000L else attempts * 1000L
                handler.postDelayed({
                    if (registered) runCatching { nsdManager.stopServiceDiscovery(listener) }
                    handler.postDelayed({
                        if (!registered && running) {
                            runCatching {
                                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                            }
                        }
                        restartScheduled = false
                    }, 100L)
                }, delay)
            }
        }

        /** 解析出的服务地址是否为本机地址（避免连到局域网其他 Android 设备） */
        private fun isLocalDevice(info: NsdServiceInfo): Boolean = try {
            val hostAddr = info.host?.hostAddress ?: return false
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .any { it.hostAddress == hostAddr }
        } catch (e: Exception) {
            false
        }

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
        }

        override fun onDiscoveryStopped(serviceType: String) {
            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")
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
        }

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(nsdServiceInfo)
        }
    }
}
