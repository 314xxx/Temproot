package com.temproot.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * ADB 配对通知（主流方案：RemoteInput 直接回复，像短信那样在通知栏输入配对码）。
 *
 * 流程：
 * 1. 应用检测到系统配对服务广播（用户打开了「使用配对码配对设备」弹窗）
 * 2. 发送带输入框的通知——用户无需切回应用，直接在通知内输入 6 位配对码
 * 3. [PairingReplyReceiver] 收到输入，后台完成配对并连接
 * 4. 通知更新为配对结果
 */
object PairingNotifier {

    private const val CHANNEL_ID = "adb_pairing"
    private const val NOTIFICATION_ID = 1001

    const val ACTION_PAIRING_REPLY = "com.temproot.app.ACTION_PAIRING_REPLY"
    const val EXTRA_PORT = "pairing_port"
    const val KEY_PAIRING_CODE = "pairing_code"

    /** 通知渠道（幂等） */
    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ADB 配对",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "ADB 无线配对请求与结果"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** 通知权限是否可用（Android 13+ 需运行时授权；低版本总是 true） */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 发送配对输入通知：检测到配对服务后调用。
     * 用户在通知内直接输入 6 位配对码（RemoteInput），无需切回应用。
     */
    fun showPairingRequest(context: Context, port: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("输入 6 位配对码")
            .build()

        val replyIntent = Intent(context, PairingReplyReceiver::class.java).apply {
            action = ACTION_PAIRING_REPLY
            putExtra(EXTRA_PORT, port)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, port, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_notify,
            "输入配对码",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("ADB 配对请求")
            .setContentText("已检测到配对服务，点击下方按钮输入配对码")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("已检测到配对服务（端口 $port）。\n直接在通知内输入系统弹窗显示的 6 位配对码即可完成配对，无需切回应用。"))
            .addAction(replyAction)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 更新通知为"配对中" */
    fun showPairingProgress(context: Context, port: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("正在配对...")
            .setContentText("正在与无线调试服务握手（端口 $port）")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 更新通知为配对结果（成功时附带连接状态） */
    fun showResult(context: Context, success: Boolean, detail: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(if (success) "配对成功" else "配对失败")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 取消配对通知（弹窗关闭时调用） */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** 从通知回复 Intent 中提取配对码 */
    fun getPairingCodeFromIntent(intent: Intent): String? {
        return RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_PAIRING_CODE)?.toString()?.trim()
    }
}
