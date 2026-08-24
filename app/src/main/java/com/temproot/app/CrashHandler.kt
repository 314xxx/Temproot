package com.temproot.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃捕获：未处理异常写入日志文件（应用私有目录 + 外部私有目录副本，
 * 后者可用文件管理器直接取出反馈），随后交回系统默认处理器。
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_FILE = "crash.log"
    private const val MAX_DISPLAY = 4000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("==== TempRoot 崩溃 ====")
            appendLine("时间: $time")
            appendLine("设备: ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
            appendLine("线程: ${thread.name}")
            appendLine(trace)
            appendLine()
        }
        Log.e(TAG, report)
        runCatching { File(context.filesDir, CRASH_FILE).appendText(report) }
        runCatching {
            context.getExternalFilesDir(null)?.let { File(it, CRASH_FILE).appendText(report) }
        }
    }

    /** 最近一次崩溃日志（截尾展示用） */
    fun lastCrash(context: Context): String? = runCatching {
        val f = File(context.filesDir, CRASH_FILE)
        if (!f.exists()) return null
        val text = f.readText()
        // 只取最近一次（最后一个报告块）
        val idx = text.lastIndexOf("==== TempRoot 崩溃 ====")
        (if (idx >= 0) text.substring(idx) else text).take(MAX_DISPLAY).ifEmpty { null }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, CRASH_FILE).delete() }
        runCatching {
            context.getExternalFilesDir(null)?.let { File(it, CRASH_FILE).delete() }
        }
    }
}
