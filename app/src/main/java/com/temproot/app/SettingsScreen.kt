package com.temproot.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val maxRetries = prefs.getInt(MainActivity.KEY_MAX_RETRIES, MainActivity.DEFAULT_MAX_RETRIES)
    var retriesText by remember { mutableStateOf(maxRetries.toString()) }

    var portText by remember {
        mutableStateOf(AdbShell.getSavedPort(context).takeIf { it > 0 }?.toString() ?: "")
    }
    var portSaved by remember { mutableStateOf(false) }

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
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }

            // ADB 连接设置
            MiuixCard {
                Text(text = "ADB 连接", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "连接端口由 mDNS 自动发现，一般无需手动设置。仅当自动发现失败时填写「无线调试」主界面显示的端口号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it.filter { c -> c.isDigit() }.take(5)
                            portSaved = false
                        },
                        label = { Text("备用端口（可选）") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            portText.toIntOrNull()?.let { port ->
                                if (port in 1..65535) {
                                    AdbShell.savePort(context, port)
                                    portSaved = true
                                }
                            }
                        },
                        enabled = portText.toIntOrNull() in 1..65535 && !portSaved,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(if (portSaved) "已保存" else "保存")
                    }
                }
            }

            // cf 重试次数
            MiuixCard {
                Text(text = "cf 最大重试次数", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SELinux 宽容注入的尝试次数上限。成功率较低，建议设置较高值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = retriesText,
                    onValueChange = {
                        retriesText = it.filter { c -> c.isDigit() }.take(3)
                        it.toIntOrNull()?.let { value ->
                            prefs.edit().putInt(MainActivity.KEY_MAX_RETRIES, value).apply()
                        }
                    },
                    label = { Text("重试次数") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "推荐值: 50-100",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 路径信息
            MiuixCard {
                Text(text = "高级选项", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                SettingInfoRow("日志保存路径", "/sdcard/ksulog.txt")
                SettingInfoRow("二进制文件路径", "/data/local/tmp/")
                SettingInfoRow("ADB 密钥", "应用私有目录")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "设备支持表: ${RootManager.EXTERNAL_CONFIG_PATH}（可选，优先于内置表）\n安装 KernelSU/ReSukiSU 管理器后，ksud 将自动复用其版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}
