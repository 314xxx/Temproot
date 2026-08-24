package com.temproot.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.temproot.app.ui.theme.TempRootAppTheme
import kotlinx.coroutines.launch

/**
 * 系统样式配对对话框 Activity（Shizuku 同款）。
 *
 * 对话框主题的独立 Activity，可从两处启动：
 * 1. 应用内点「配对」——浮在主界面上
 * 2. 点击配对通知——无论当前在哪个应用/系统设置，直接弹出系统样式对话框输入配对码
 *
 * 配对流程：
 * ① [打开无线调试设置] 按钮拉起系统设置（发送 Intent 消息）
 * ② 后台监听配对服务广播，检测到后发通知（可在通知栏直接输入）
 * ③ 在对话框内输入 6 位配对码，或点击通知打开本对话框输入
 * ④ 配对成功后自动连接，结果通过通知反馈
 */
class PairingDialogActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PORT = "pairing_port"
    }

    /** 通知路径传入的已检测端口（服务已确认存在时无需重新监听） */
    private val portState = mutableStateOf<Int?>(null)

    // Android 13+ 通知权限被拒时配对通知静默失败，进入对话框时补请求
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AdbShell 已在 App（Application）中初始化
        portState.value = intent?.getIntExtra(EXTRA_PORT, -1)?.takeIf { it > 0 }
        // 启动前台监听服务：用户跳到系统设置后监听不中断，检测到服务必发通知
        PairingService.start(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            TempRootAppTheme {
                PairingDialogScreen(
                    initialPort = portState.value,
                    onFinished = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop：通知重复点击时更新端口
        portState.value = intent.getIntExtra(EXTRA_PORT, -1).takeIf { it > 0 }
    }

    override fun onDestroy() {
        // 仅在未完成配对时停止监听服务：
        // 通知路径配对+连接可能仍在后台进行（进程存活性依赖前台服务），
        // 此时停掉服务会让进程被系统冻结/杀死（"配对成功后闪退"的第二个根因）
        if (!AdbShell.isConnected) {
            PairingService.stop(this)
        }
        super.onDestroy()
    }
}

@Composable
fun PairingDialogScreen(initialPort: Int?, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var codeText by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf(initialPort?.toString() ?: "") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var scanning by remember { mutableStateOf(initialPort == null) }
    var detectedPort by remember { mutableStateOf(initialPort) }
    var scanKey by remember { mutableStateOf(0) }

    // 无已知端口时监听配对服务广播（系统配对弹窗弹出时立即捕获端口）
    LaunchedEffect(scanKey, initialPort) {
        if (initialPort != null) return@LaunchedEffect
        scanning = true
        detectedPort = null
        val port = MdnsDiscovery.discoverPort(
            context, MdnsDiscovery.TLS_PAIRING,
            timeoutMs = 120_000, indefinite = true
        )
        scanning = false
        if (port != null) {
            detectedPort = port
            if (portText.isBlank()) portText = port.toString()
            // 主流方式：发送通知，用户可在通知栏直接输入，或点击通知打开本对话框
            PairingNotifier.showPairingRequest(context, port)
        }
    }

    // 对话框关闭时取消未完成的配对输入通知
    //（已连接时跳过：通知路径的结果通知用的是同一 ID，不能被误删）
    DisposableEffect(Unit) {
        onDispose {
            if (!AdbShell.isConnected) PairingNotifier.cancel(context)
        }
    }

    // 通知路径（RemoteInput）配对成功后自动连接 → 状态变为 Connected 时自动关闭
    val adbStateForDialog by AdbShell.state.collectAsState()
    LaunchedEffect(adbStateForDialog) {
        if (adbStateForDialog is AdbShell.State.Connected && working) {
            message = null
            onFinished()
        }
    }

    val effectivePort = detectedPort ?: portText.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    Dialog(
        onDismissRequest = { if (!working) onFinished() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = !working)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("ADB 无线配对", style = MaterialTheme.typography.titleLarge)

                // ① 拉起系统无线调试设置
                Text("① 打开「无线调试」", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { openWirelessDebuggingSettings(context) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("打开无线调试设置") }

                // ② 配对服务监听状态
                Text("② 点「使用配对码配对设备」", style = MaterialTheme.typography.titleSmall)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            scanning -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "正在搜索配对服务...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            detectedPort != null -> {
                                StatusDot(color = Color(0xFF34C759))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "已检测到配对服务（端口 $detectedPort）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                StatusDot(color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "未自动检测到，请在下方手动填写端口",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    message = null
                                    scanKey++
                                }) { Text("重试") }
                            }
                        }
                    }
                }

                // ③ 配对码 + 端口输入
                Text("③ 输入系统弹窗显示的配对码", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("6 位配对码") },
                    singleLine = true,
                    enabled = !working,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = {
                        Text(
                            if (detectedPort != null) "配对端口（已自动检测）"
                            else "配对端口（系统弹窗中 IP:端口 的端口）"
                        )
                    },
                    singleLine = true,
                    enabled = !working,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 按钮区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onFinished, enabled = !working) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (codeText.length != 6) {
                                message = "配对码应为 6 位数字"
                                return@Button
                            }
                            val port = effectivePort ?: run {
                                message = "请填写配对端口"
                                return@Button
                            }
                            working = true
                            message = null
                            scope.launch {
                                try {
                                    val result = AdbShell.pair(codeText, port)
                                    if (result.isSuccess) {
                                        PairingNotifier.showResult(context, true, "配对成功，正在连接...")
                                        val connected = AdbShell.connect()
                                        working = false
                                        if (connected.isSuccess) {
                                            // 全部工作已完成，停止监听服务（对话框路径的收尾）
                                            PairingService.stop(context)
                                            onFinished()
                                        } else {
                                            message = "配对成功但连接失败: ${connected.exceptionOrNull()?.message}\n可回到应用点「连接」重试"
                                        }
                                    } else {
                                        working = false
                                        message = "配对失败: ${result.exceptionOrNull()?.message}"
                                        // 配对弹窗可能已关闭或端口已变化，重新搜索
                                        scanKey++
                                    }
                                } catch (t: Throwable) {
                                    // 安全网：任何异常转为界面提示，绝不让其逃逸导致闪退
                                    working = false
                                    message = "发生错误: ${t.message ?: t.javaClass.simpleName}"
                                }
                            }
                        },
                        enabled = !working && effectivePort != null && codeText.length == 6,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (working) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (working) "配对中..." else "配对")
                    }
                }
            }
        }
    }
}

/** 拉起系统「无线调试」设置页（发送 Intent 消息），失败回退开发者选项 */
private fun openWirelessDebuggingSettings(context: Context) {
    val actions = listOf(
        "android.settings.WIRELESS_DEBUGGING_SETTINGS",
        "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
    )
    for (action in actions) {
        try {
            context.startActivity(Intent(action))
            return
        } catch (_: Exception) {
        }
    }
}
