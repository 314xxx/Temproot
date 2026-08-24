package com.temproot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 处理通知内输入的配对码（RemoteInput 直接回复）。
 *
 * 关键：绝不使用 goAsync()——它的 PendingResult 有 ~10 秒硬限制，
 * 配对（TLS 握手）+ 连接（mDNS 发现 + 握手 + 验证）串行执行轻松超限，
 * 超时后系统直接杀进程（"配对成功后闪退"的元凶）。
 *
 * 正确做法：onReceive 同步读取 RemoteInput 后立即返回，
 * 重活交给全局 scope——进程存活由 PairingService 前台服务保证。
 */
class PairingReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PairingNotifier.ACTION_PAIRING_REPLY) return

        val port = intent.getIntExtra(PairingNotifier.EXTRA_PORT, -1)
        val code = PairingNotifier.getPairingCodeFromIntent(intent)

        // RemoteInput 回复后系统可能重新投递旧 intent（罕见），无输入时忽略
        if (code.isNullOrEmpty() || code.length != 6 || port <= 0) return

        val appContext = context.applicationContext
        AdbShell.init(appContext)

        // 全局 scope（非 goAsync）：不受 receiver 10 秒限制，PairingService 保活进程
        receiverScope.launch {
            try {
                PairingNotifier.showPairingProgress(appContext, port)

                val paired = AdbShell.pair(code, port)
                if (paired.isFailure) {
                    PairingNotifier.showResult(
                        appContext, false,
                        "配对失败: ${paired.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                // 配对成功，自动发现连接端口并连接
                //（若对话框路径已连上，AdbShell.connect 内部 tryLock 会立即返回，
                // 不会与进行中的连接流程打架）
                val connected = AdbShell.connect()
                PairingNotifier.showResult(
                    appContext, true,
                    if (connected.isSuccess) "配对成功，ADB 已连接，可回到应用执行一键 Root"
                    else "配对成功，但连接失败: ${connected.exceptionOrNull()?.message}"
                )
                // 配对流程结束，停止监听服务
                PairingService.stop(appContext)
            } catch (t: Throwable) {
                // 安全网：任何异常转为通知反馈，绝不让其逃逸导致闪退
                runCatching {
                    PairingNotifier.showResult(
                        appContext, false,
                        "发生错误: ${t.message ?: t.javaClass.simpleName}"
                    )
                }
                runCatching { PairingService.stop(appContext) }
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
