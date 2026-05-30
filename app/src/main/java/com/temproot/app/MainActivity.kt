package com.temproot.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        
        setContent {
            TempRootAppTheme {
                AppNavigation(prefs)
            }
        }
    }
    
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { }
    
    private val requestPermissionResultListener = 
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku 权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要 Shizuku 权限才能执行", Toast.LENGTH_LONG).show()
            }
        }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: android.content.SharedPreferences,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rootManager = remember { RootManager(context) }
    val scope = rememberCoroutineScope()
    
    val maxRetries = prefs.getInt(MainActivity.KEY_MAX_RETRIES, MainActivity.DEFAULT_MAX_RETRIES)
    
    var isProcessing by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var currentStatus by remember { mutableStateOf("就绪") }
    var rootStatus by remember { mutableStateOf(mapOf<String, String>()) }
    var environmentCheck by remember { mutableStateOf<String?>(null) }
    
    val listState = rememberLazyListState()
    
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    LaunchedEffect(Unit) {
        rootStatus = rootManager.checkRootStatus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TempRoot", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        scope.launch { rootStatus = rootManager.checkRootStatus() }
                    }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            StatusCard(rootStatus)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            DeviceInfoCard()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        if (isProcessing) return@Button
                        
                        isProcessing = true
                        logs = emptyList()
                        currentStatus = "检查环境..."
                        
                        scope.launch {
                            fun log(msg: String) { logs = logs + msg }
                            
                            log("🔍 开始环境检查...")
                            val checkResult = rootManager.checkEnvironment()
                            
                            if (checkResult.isFailure) {
                                log("❌ 环境检查失败: ${checkResult.exceptionOrNull()?.message}")
                                environmentCheck = checkResult.exceptionOrNull()?.message
                                currentStatus = "检查失败"
                                isProcessing = false
                                return@launch
                            }
                            
                            log("✅ 环境检查通过")
                            environmentCheck = null
                            
                            currentStatus = "执行 SELinux 宽容..."
                            val selinuxSuccess = rootManager.setSELinuxPermissive(
                                maxRetries = maxRetries,
                                onLog = { log(it) },
                                onStatusUpdate = { currentStatus = it }
                            )
                            
                            if (!selinuxSuccess) {
                                log("❌ SELinux 宽容失败")
                                currentStatus = "失败"
                                isProcessing = false
                                return@launch
                            }
                            
                            currentStatus = "执行 MQSAS 注入..."
                            rootManager.injectKSUD(onLog = { log(it) })
                            
                            currentStatus = "检查结果..."
                            delay(1000)
                            rootStatus = rootManager.checkRootStatus()
                            
                            if (rootStatus["root_available"] == "已获取") {
                                log("")
                                log("🎉🎉🎉 临时 Root 成功！")
                                log("注意：重启后失效")
                                currentStatus = "Root 成功"
                            } else {
                                log("")
                                log("⚠️ 注入完成，但未检测到 Root")
                                log("请手动检查或查看 /sdcard/ksulog.txt")
                                currentStatus = "完成 (未确认)"
                            }
                            
                            isProcessing = false
                        }
                    },
                    modifier = Modifier
                        .size(160.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape),
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProcessing) MaterialTheme.colorScheme.secondary 
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (isProcessing) "执行中..." else "一键 Root",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "状态: $currentStatus",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            if (environmentCheck != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = "⚠️ $environmentCheck",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "执行日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = when {
                                log.contains("✅") || log.contains("SUCCESS") -> Color(0xFF4CAF50)
                                log.contains("❌") || log.contains("失败") -> Color(0xFFF44336)
                                log.contains("⚠️") || log.contains("警告") -> Color(0xFFFFC107)
                                else -> Color(0xFFE0E0E0)
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(status: Map<String, String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status["root_available"] == "已获取")
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "当前状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusItem("SELinux", status["selinux"] ?: "未知")
                StatusItem("Root", status["root_available"] ?: "未知")
                StatusItem("ksud", status["ksud_running"] ?: "未知")
            }
        }
    }
}

@Composable
fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                value.contains("Permissive") || value.contains("已获取") || value.contains("运行中") -> MaterialTheme.colorScheme.primary
                value.contains("Enforcing") || value.contains("未获取") || value.contains("未运行") -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun DeviceInfoCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "设备信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("型号: ${android.os.Build.MODEL}")
            Text("设备: ${android.os.Build.DEVICE}")
            Text("系统: Android ${android.os.Build.VERSION.RELEASE}")
            Text("安全补丁: ${android.os.Build.VERSION.SECURITY_PATCH}")
        }
    }
}