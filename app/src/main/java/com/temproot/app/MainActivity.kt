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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.temproot.app.ui.theme.TempRootAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREF_NAME = "temp_root_prefs"
        const val KEY_MAX_RETRIES = "max_retries"
        const val DEFAULT_MAX_RETRIES = 50
    }

    private val prefs by lazy {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        AdbShell.init(this)
        setContent {
            TempRootAppTheme {
                AppNavigation(prefs)
            }
        }
    }
}

@Composable
fun AppNavigation(prefs: android.content.SharedPreferences) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                prefs = prefs,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ==================== Miuix 基础组件 ====================

// Miuix 风格卡片：白色大圆角
@Composable
fun MiuixCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

// 状态指示点
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

// ==================== 主界面 ====================

@Composable
fun MainScreen(
    prefs: android.content.SharedPreferences,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val rootManager = remember { RootManager(context) }

    val adbState by AdbShell.state.collectAsState()
    val isProcessing = remember { mutableStateOf(false) }
    val currentStatus = remember { mutableStateOf("空闲") }
    val logs = remember { mutableStateOf(listOf<String>()) }
    val showPairingDialog = remember { mutableStateOf(false) }

    fun log(msg: String) {
        logs.value = logs.value + msg
    }

    val maxRetries = prefs.getInt(MainActivity.KEY_MAX_RETRIES, MainActivity.DEFAULT_MAX_RETRIES)

    // Android 13+ 请求通知权限（用于通知栏内输入配对码）
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== 顶栏 =====
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TempRoot",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "一键临时 Root · 重启即失效",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
                }
            }

            // ===== ADB 连接状态卡 =====
            MiuixCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = if (adbState is AdbShell.State.Connected)
                            Color(0xFF34C759) else Color(0xFFBDBDC6)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (adbState is AdbShell.State.Connected) "ADB 已连接" else "ADB 未连接",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                adbState is AdbShell.State.Connected -> "已连接 · shell 权限就绪"
                                AdbShell.isPaired(context) -> "点击连接 · 自动发现端口"
                                else -> "首次使用需配对 · 无需电脑"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        when {
                            adbState is AdbShell.State.Connected -> AdbShell.disconnect()
                            AdbShell.isPaired(context) -> scope.launch {
                                currentStatus.value = "连接 ADB..."
                                log("ℹ 开始连接 ADB...")
                                val r = AdbShell.connect { log(it) }
                                log(if (r.isSuccess) "✓ ADB 已连接"
                                    else "✗ ADB 连接失败: ${r.exceptionOrNull()?.message}")
                                currentStatus.value = "空闲"
                            }
                            else -> showPairingDialog.value = true
                        }
                    }) {
                        Text(
                            text = when {
                                adbState is AdbShell.State.Connected -> "断开"
                                AdbShell.isPaired(context) -> "连接"
                                else -> "配对"
                            }
                        )
                    }
                }
            }

            // ===== Root 操作卡 =====
            MiuixCard {
                Text(
                    text = "临时 Root",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentStatus.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                // HyperOS 风格大圆按钮
                Button(
                    onClick = {
                        if (isProcessing.value) return@Button
                        if (adbState !is AdbShell.State.Connected) {
                            showPairingDialog.value = true
                            return@Button
                        }
                        isProcessing.value = true
                        scope.launch {
                            try {
                                log("🔍 开始环境检查...")
                                val checkResult = rootManager.checkEnvironment(onLog = { log(it) })
                                if (checkResult.isFailure) {
                                    log("❌ 环境检查失败: ${checkResult.exceptionOrNull()?.message}")
                                    currentStatus.value = "检查失败"
                                    return@launch
                                }
                                log("✅ 环境检查通过: ${checkResult.getOrDefault("")}")

                                currentStatus.value = "执行 SELinux 宽容..."
                                val selinuxSuccess = rootManager.setSELinuxPermissive(
                                    maxRetries = maxRetries,
                                    onLog = { log(it) },
                                    onStatusUpdate = { currentStatus.value = it }
                                )
                                if (!selinuxSuccess) {
                                    log("⚠️ SELinux 宽容失败")
                                    log("ℹ 降级策略：继续尝试 ksud 注入（未宽容时成功率较低）")
                                }

                                currentStatus.value = "执行 MQSAS 注入..."
                                rootManager.injectKSUD(onLog = { log(it) })

                                currentStatus.value = "完成"
                            } finally {
                                isProcessing.value = false
                            }
                        }
                    },
                    enabled = !isProcessing.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isProcessing.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = if (isProcessing.value) "执行中..." else "一键 Root",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ===== 设备信息卡 =====
            DeviceInfoCard()

            // ===== 日志卡 =====
            if (logs.value.isNotEmpty()) {
                MiuixCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "运行日志",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { logs.value = emptyList() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = logs.value.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                                .heightIn(max = 320.dp)
                        )
                    }
                }
            }

            // ===== 崩溃日志卡（闪退排查，存在崩溃记录时才显示）=====
            var crashText by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { crashText = CrashHandler.lastCrash(context) }
            crashText?.let { crash ->
                MiuixCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "上次崩溃日志",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            CrashHandler.clear(context)
                            crashText = null
                        }) { Text("清除") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = crash,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                                .heightIn(max = 260.dp)
                        )
                    }
                }
            }
        }
    }

    // 配对弹层
    if (showPairingDialog.value) {
        PairingDialog(
            onDismiss = { showPairingDialog.value = false },
            onPaired = {
                showPairingDialog.value = false
                scope.launch {
                    currentStatus.value = "连接 ADB..."
                    val r = AdbShell.connect { log(it) }
                    log(if (r.isSuccess) "✓ 配对完成，ADB 已连接"
                       else "✗ 配对成功但连接失败: ${r.exceptionOrNull()?.message}")
                    currentStatus.value = "空闲"
                }
            }
        )
    }
}

// ==================== 配对对话框 ====================

// 主流方案（Shizuku / AxManager 同款流程）：
// ① 通过 Intent 拉起系统「无线调试」设置页（即"发送消息"）
// ② 用户点「使用配对码配对设备」，系统弹窗显示 6 位配对码
// ③ 应用监听配对服务广播自动捕获端口，用户只需输入配对码
// ④ mDNS 自动检测失败时，可手动输入系统弹窗中显示的端口兜底
@Composable
fun PairingDialog(
    onDismiss: () -> Unit,
    onPaired: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var codeText by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var scanning by remember { mutableStateOf(true) }
    var detectedPort by remember { mutableStateOf<Int?>(null) }
    var scanKey by remember { mutableStateOf(0) }

    // 打开弹层即开始监听配对服务广播（系统配对弹窗弹出时立即捕获端口）
    LaunchedEffect(scanKey) {
        scanning = true
        detectedPort = null
        val port = MdnsDiscovery.discoverPort(
            context, MdnsDiscovery.TLS_PAIRING,
            timeoutMs = 120_000, indefinite = true
        )
        scanning = false
        if (port != null) {
            detectedPort = port
            // 自动检测成功时填入端口（不覆盖用户已输入的内容）
            if (portText.isBlank()) portText = port.toString()
            // 主流方式：发送通知，用户可直接在通知栏输入配对码，无需切回应用
            PairingNotifier.showPairingRequest(context, port)
        }
    }

    // 弹窗关闭时取消未完成的配对通知
    DisposableEffect(Unit) {
        onDispose { PairingNotifier.cancel(context) }
    }

    // 通知路径配对成功后自动连接 → 状态变为 Connected 时自动关闭弹窗
    val adbStateForDialog by AdbShell.state.collectAsState()
    LaunchedEffect(adbStateForDialog) {
        if (adbStateForDialog is AdbShell.State.Connected) onPaired()
    }

    val effectivePort = detectedPort ?: portText.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("ADB 无线配对", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

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
                Text(
                    text = "检测到配对服务后会发送通知，可直接在通知栏输入配对码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

                // ③ 配对码 + 端口
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
            }
        },
        confirmButton = {
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
                        val result = AdbShell.pair(codeText, port)
                        working = false
                        if (result.isSuccess) {
                            onPaired()
                        } else {
                            message = "配对失败: ${result.exceptionOrNull()?.message}"
                            // 配对弹窗可能已关闭或端口已变化，重新搜索
                            scanKey++
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("取消") }
        }
    )
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

// ==================== 设备信息 ====================

@Composable
fun DeviceInfoCard() {
    MiuixCard {
        Text(
            text = "设备信息",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        InfoRow("型号", android.os.Build.MODEL)
        InfoRow("设备", android.os.Build.DEVICE)
        InfoRow("系统", "Android ${android.os.Build.VERSION.RELEASE}")
        InfoRow("安全补丁", android.os.Build.VERSION.SECURITY_PATCH)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}
