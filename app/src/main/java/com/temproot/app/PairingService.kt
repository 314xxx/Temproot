package com.temproot.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 配对监听前台服务（Shizuku 同款方案）。
 *
 * 为什么需要前台服务：
 * 配对服务监听若绑定在前台 Activity 上，用户跳转到系统设置打开
 * 「使用配对码配对设备」弹窗时，应用进程会被系统冻结（App Freezer），
 * mDNS 监听随之停止——永远检测不到配对服务，通知也发不出来。
 * 前台服务保持进程活跃，监听持续工作。
 *
 * 流程：
 * 1. 启动 → 常驻通知"正在等待配对服务"
 * 2. 检测到服务 → 发送配对输入通知（RemoteInput / 点击弹对话框）
 * 3. 配对完成或超时 → 自动停止
 */
class PairingService : Service() {

    companion object {
        private const val CHANNEL_ID = "adb_pairing_listening"
        private const val LISTENING_NOTIFICATION_ID = 1000
        const val ACTION_STOP = "com.temproot.app.ACTION_STOP_PAIRING_LISTEN"
        private const val LISTEN_TIMEOUT_MS = 120_000L

        /** 启动配对监听（幂等，已在运行则忽略） */
        fun start(context: Context) {
            val intent = Intent(context, PairingService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** 停止配对监听（配对完成/取消时调用） */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, PairingService::class.java))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 监听协程是否已在跑（对话框反复打开时 onStartCommand 会多次触发，防重复监听） */
    @Volatile
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 常驻"监听中"通知，保持进程不被冻结
        startForegroundInternal()

        if (!listening) {
            listening = true
            scope.launch {
                val port = MdnsDiscovery.discoverPort(
                    this@PairingService, MdnsDiscovery.TLS_PAIRING,
                    timeoutMs = LISTEN_TIMEOUT_MS, indefinite = true
                )
                listening = false
                if (port != null) {
                    // 检测到配对服务：发送配对输入通知（保持服务存活等待配对完成）
                    PairingNotifier.showPairingRequest(this@PairingService, port)
                } else {
                    // 超时未检测到：告知后自动退出
                    showTimeout()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundInternal() {
        val notification = buildListeningNotification("正在等待配对服务…\n请打开系统「使用配对码配对设备」弹窗")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                LISTENING_NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(LISTENING_NOTIFICATION_ID, notification)
        }
    }

    private fun buildListeningNotification(text: String) =
        NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("TempRoot 配对")
            .setContentText("正在等待配对服务")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // 点击通知正文 → 弹出配对对话框
            .setContentIntent(dialogPendingIntent())
            // 通知左侧"取消"动作：停止监听
            .addAction(
                R.drawable.ic_stat_notify, "取消",
                PendingIntent.getService(
                    this, 2,
                    Intent(this, PairingService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun showTimeout() {
        if (!PairingNotifier.canNotify(this)) return
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("未检测到配对服务")
            .setContentText("请确认已打开「使用配对码配对设备」弹窗后重试")
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(LISTENING_NOTIFICATION_ID, notification)
        }
    }

    private fun dialogPendingIntent(): PendingIntent {
        val intent = Intent(this, PairingDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(): String {
        val channel = NotificationChannel(
            CHANNEL_ID, "配对监听", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "等待 ADB 配对服务" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return CHANNEL_ID
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
