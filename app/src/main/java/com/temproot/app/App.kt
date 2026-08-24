package com.temproot.app

import android.app.Application

/**
 * 全局初始化：
 * - CrashHandler 必须在此安装——从通知冷启动 PairingDialogActivity（MainActivity 未运行）时
 *   也要能捕获崩溃，否则闪退无日志可查
 * - AdbShell 同理，任意入口进入前证书已就绪
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        AdbShell.init(this)
    }
}
