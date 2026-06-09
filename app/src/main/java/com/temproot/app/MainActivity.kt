package com.temproot.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.temproot.app.ui.theme.TempRootAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREF_NAME = "temp_root_prefs"
        const val KEY_MAX_RETRIES = "max_retries"
        const val DEFAULT_MAX_RETRIES = 50
    }

    private val prefs by lazy {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private var shizukuReady = mutableStateOf(false)
    private var shizukuPermission = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        shizukuReady.value = Shizuku.getBinder() != null && Shizuku.pingBinder()
        shizukuPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

        setContent {
            TempRootAppTheme {
                AppNavigation(
                    prefs = prefs,
                    shizukuReady = shizukuReady.value,
                    shizukuPermission = shizukuPermission.value
                )
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        shizukuReady.value = true
        shizukuPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shizukuReady.value = false
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                shizukuPermission.value = true
                Toast.makeText(this, "Shizuku 权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }
}

@Composable
fun AppNavigation(
    prefs: android.content.SharedPreferences,
    shizukuReady: Boolean,
    shizukuPermission: Boolean
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                prefs = prefs,
                shizukuReady = shizukuReady,
                shizukuPermission = shizukuPermission,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: android.content.SharedPreferences,
    shizukuReady: Boolean,
    shizukuPermission: Boolean,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rootManager = remember { RootManager(context) }
    val scope = rememberCoroutineScope()

    val maxRetries = prefs.getInt(MainActivity.KEY_MAX_RETRIES, MainActivity.DEFAULT_MAX_RETRIES)

    var isProcessing by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var currentStatus by remember { mutableStateOf("就绪") }
    var rootStatus by remember { mutableStateOf(mapOf("selinux" to "未知", "ksud_running" to "未知", "root_available" to "未知")) }
    var environmentCheck by remember { mutableStateOf<String?>(null) }
    var isCheckingStatus by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(shizukuReady, shizukuPermission) {
        isCheckingStatus = true
        if (shizukuReady && shizukuPermission) {
            rootStatus = rootManager.checkRootStatus()
        } else {
            rootStatus = mapOf("selinux" to "未知", "ksud_running" to "未知", "root_available" to "未知")
        }
        isCheckingStatus = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "TempRoot",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = {
                        if (!isProcessing) {
                            scope.launch {
                                isCheckingStatus = true
                                rootStatus = rootManager.checkRootStatus()
                                isCheckingStatus = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Shizuku 连接状态
            ShizukuStatusBar(shizukuReady, shizukuPermission)

            Spacer(modifier = Modifier.height(12.dp))

            // 当前状态卡片
            StatusCard(rootStatus, isCheckingStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // 设备信息
            DeviceInfoCard()

            Spacer(modifier = Modifier.height(20.dp))

            // Root 按钮
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                RootButton(
                    isProcessing = isProcessing,
                    enabled = shizukuReady && shizukuPermission,
                    onClick = {
                        isProcessing = true
                        logs = emptyList()
                        currentStatus = "检查环境..."
                        environmentCheck = null

                        scope.launch {
                            fun log(msg: String) {
                                logs = logs + msg
                            }

                            log("开始环境检查...")
                            val checkResult = rootManager.checkEnvironment()

                            if (checkResult.isFailure) {
                                val errMsg = checkResult.exceptionOrNull()?.message ?: "未知错误"
                                log("环境检查失败: $errMsg")
                                environmentCheck = errMsg
                                currentStatus = "检查失败"
                                isProcessing = false
                                return@launch
                            }

                            log("环境检查通过")
                            environmentCheck = null

                            currentStatus = "执行 SELinux 宽容..."
                            val selinuxSuccess = rootManager.setSELinuxPermissive(
                                maxRetries = maxRetries,
                                onLog = { log(it) },
                                onStatusUpdate = { currentStatus = it }
                            )

                            if (!selinuxSuccess) {
                                log("SELinux 宽容失败")
                                currentStatus = "失败"
                                isProcessing = false
                                return@launch
                            }

                            currentStatus = "MQSAS 注入中..."
                            rootManager.injectKSUD(onLog = { log(it) })

                            currentStatus = "检查结果..."
                            delay(1000)
                            rootStatus = rootManager.checkRootStatus()

                            if (rootStatus["root_available"] == "已获取") {
                                log("")
                                log("======== Root 成功 ========")
                                log("注意：重启后失效")
                                currentStatus = "Root 成功"
                            } else {
                                log("")
                                log("注入完成但未检测到 Root")
                                log("请检查 /sdcard/ksulog.txt")
                                currentStatus = "完成"
                            }

                            isProcessing = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 状态文字
            Text(
                text = currentStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentStatus.contains("成功"))
                    MaterialTheme.colorScheme.primary
                else if (currentStatus.contains("失败"))
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // 环境检查错误
            AnimatedVisibility(visible = environmentCheck != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = environmentCheck ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 日志标题
            Text(
                text = "执行日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 日志区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C1C1E)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "点击按钮开始执行",
                            color = Color(0xFF636366),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                color = when {
                                    log.contains("成功") || log.contains("Root 成功") -> Color(0xFF34C759)
                                    log.contains("失败") || log.contains("出错") -> Color(0xFFFF453A)
                                    log.contains("注意") || log.contains("请检查") -> Color(0xFFFF9F0A)
                                    log.contains("===") -> Color(0xFF0A84FF)
                                    else -> Color(0xFFE5E5EA)
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShizukuStatusBar(ready: Boolean, permission: Boolean) {
    val bgColor = when {
        ready && permission -> MaterialTheme.colorScheme.primaryContainer
        ready && !permission -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val textColor = when {
        ready && permission -> MaterialTheme.colorScheme.onPrimaryContainer
        ready && !permission -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !ready -> "Shizuku 服务未连接"
                    !permission -> "Shizuku 权限未授予"
                    else -> "Shizuku 已连接"
                },
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RootButton(
    isProcessing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isProcessing) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Button(
        onClick = onClick,
        enabled = enabled && !isProcessing,
        modifier = Modifier
            .size(150.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isProcessing) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else if (!enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
            disabledElevation = 1.dp
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isProcessing) "执行中" else if (!enabled) "不可用" else "一键 Root",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusCard(status: Map<String, String>, isLoading: Boolean) {
    val bgColor = if (status["root_available"] == "已获取")
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前状态",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "检查中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusItem("SELinux", status["selinux"] ?: "未知")
                    StatusItem("Root", status["root_available"] ?: "未知")
                    StatusItem("ksud", status["ksud_running"] ?: "未知")
                }
            }
        }
    }
}

@Composable
fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                value.contains("Permissive") || value.contains("已获取") || value.contains("运行中") ->
                    Color(0xFF34C759)
                value.contains("Enforcing") || value.contains("未获取") || value.contains("未运行") ->
                    Color(0xFFFF453A)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun DeviceInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            val infoColor = MaterialTheme.colorScheme.onSurfaceVariant
            DeviceInfoRow("型号", android.os.Build.MODEL, infoColor)
            DeviceInfoRow("设备代号", android.os.Build.DEVICE, infoColor)
            DeviceInfoRow("系统版本", "Android ${android.os.Build.VERSION.RELEASE}", infoColor)
            DeviceInfoRow("安全补丁", android.os.Build.VERSION.SECURITY_PATCH, infoColor)
        }
    }
}

@Composable
fun DeviceInfoRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}