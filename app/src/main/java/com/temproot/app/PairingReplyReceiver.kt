package com.temproot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 处理通知内输入的配对码（RemoteInput 直接回复）。
 * 收到输入 → 后台配对 → 自动连接 → 通知结果。
 */
class PairingReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PairingNotifier.ACTION_PAIRING_REPLY) return

        val port = intent.getIntExtra(PairingNotifier.EXTRA_PORT, -1)
        val code = PairingNotifier.getPairingCodeFromIntent(intent)

        // RemoteInput 回复后系统可能重新投递旧 intent（罕见），无输入时忽略
        if (code.isNullOrEmpty() || code.length != 6 || port <= 0) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AdbShell.init(appContext)
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
                val connected = AdbShell.connect()
                PairingNotifier.showResult(
                    appContext, true,
                    if (connected.isSuccess) "配对成功，ADB 已连接，可回到应用执行一键 Root"
                    else "配对成功，但连接失败: ${connected.exceptionOrNull()?.message}"
                )
            } finally {
                pending.finish()
            }
        }
    }
}
