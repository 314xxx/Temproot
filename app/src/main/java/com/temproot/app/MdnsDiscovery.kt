package com.temproot.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * mDNS 服务发现：自动发现本机 adbd 广播的无线调试端口。
 * 参考 AxManager / Shizuku 方案：
 * - NsdManager 发现局域网内的 adb mDNS 服务
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

    /**
     * 发现本机无线调试端口。
     * @return 端口号；超时或失败返回 null
     */
    suspend fun discoverPort(
        context: Context,
        serviceType: String,
        timeoutMs: Long = 10_000
    ): Int? = runCatching {
        withTimeoutOrNull(timeoutMs) {
            callbackFlow {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                val handler = Handler(Looper.getMainLooper())
                val resolving = AtomicBoolean(false)

                fun tryResolve(info: NsdServiceInfo) {
                    // NsdManager 同一时刻只允许一个 resolve，忙时延迟重试
                    if (!resolving.compareAndSet(false, true)) {
                        handler.postDelayed({ tryResolve(info) }, 200)
                        return
                    }
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            resolving.set(false)
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            resolving.set(false)
                            if (isLocalDevice(info) && isPortInUse(info.port)) {
                                trySend(info.port)
                            }
                        }
                    })
                }

                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        tryResolve(serviceInfo)
                    }
                }

                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

                awaitClose {
                    handler.removeCallbacksAndMessages(null)
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                }
            }.first()
        }
    }.getOrNull()

    /** 解析出的服务地址是否为本机地址（避免连到局域网其他 Android 设备） */
    private fun isLocalDevice(info: NsdServiceInfo): Boolean = runCatching {
        val hostAddr = info.host?.hostAddress ?: return@runCatching false
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .any { it.hostAddress == hostAddr }
    }.getOrDefault(false)

    /** 端口在 127.0.0.1 上是否真实被监听（占用 = true，用于过滤幻影服务） */
    private fun isPortInUse(port: Int): Boolean = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (e: Exception) {
        true
    }
}
