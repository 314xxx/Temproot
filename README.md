# TempRoot - MIUI/HyperOS 一键临时 Root 应用

一个基于 KernelSU late-load 注入的 Android 应用，通过 Shizuku 实现一键获取临时 Root 权限。

## 功能特性

- ✅ 一键获取临时 Root（重启失效）
- ✅ 自动执行 SELinux 宽容注入（cf 漏洞利用）
- ✅ 自动执行 MQSAS 服务注入（ksud）
- ✅ 实时日志输出（带颜色区分）
- ✅ 设备兼容性检查
- ✅ 安全补丁日期验证
- ✅ Material You 设计风格
- ✅ 支持动态取色（Android 12+）
- ✅ 可配置重试次数

## 支持设备

| 设备代号 | 型号 |
|---------|------|
| marble | Redmi K60 |
| mondrian | Redmi K60 Pro |
| mayfly | Redmi K60 Ultra |
| diting | Redmi K50 |
| socrates | Xiaomi 13 |

**要求**: 安全补丁日期 ≤ 2025-02-01

## 前提条件

1. **已解锁 Bootloader**
2. **安装 Shizuku** - 用于获取 ADB Shell 权限
3. **激活 Shizuku** - 通过无线调试或 ADB 激活

## 安装与使用

### 1. 安装应用

下载 APK 文件并安装到设备上。

### 2. 激活 Shizuku

1. 打开 Shizuku 应用
2. 选择"通过无线调试启动"
3. 按照提示操作

### 3. 使用 TempRoot

1. 打开 TempRoot 应用
2. 授予 Shizuku 权限（首次使用会提示）
3. 点击「一键 Root」按钮
4. 等待执行完成（可能需要多次尝试）
5. 成功后会显示 Root 状态

### 4. 验证 Root

```bash
# 在终端中执行
su -c id
# 应显示 uid=0(root)
```

## 技术原理

1. **cf 漏洞利用**: 通过 GPU DMA 时序漏洞修改 SELinux 策略
2. **MQSAS 注入**: 利用小米系统服务漏洞启动 ksud 守护进程
3. **KernelSU late-load**: 在系统启动后加载内核级 Root 方案

## 项目结构

```
TempRoot/
├── app/
│   ├── src/main/
│   │   ├── assets/          # 内置二进制文件 (cf, ksud)
│   │   ├── java/           # Kotlin 源代码
│   │   ├── res/            # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts    # 应用构建配置
├── README.md               # 本文档
└── settings.gradle.kts     # 项目设置
```

## 配置说明

在应用设置中可以调整：

- **cf 最大重试次数**: SELinux 宽容注入的尝试上限（推荐 50-100）
- **日志保存路径**: 默认 `/sdcard/ksulog.txt`

## 注意事项

⚠️ **重要警告**:

1. **临时 Root**: 重启手机后 Root 权限将失效
2. **风险提示**: 使用不当可能导致系统不稳定或数据丢失
3. **仅限特定机型**: 不支持的设备请勿使用
4. **安全补丁**: 安全补丁日期高于 2025-02-01 的设备可能无法成功
5. **备份数据**: 使用前请备份重要数据

## 隐私声明

- 本应用不收集任何用户数据
- 所有操作均在本地执行
- 不需要网络权限（仅用于可能的更新检查）

## 开发说明

### 构建环境

- Android Studio Hedgehog | 2023.1.1
- Kotlin 1.9.0
- Jetpack Compose 1.5.1
- Shizuku API 13.1.5

### 编译步骤

1. 克隆项目到本地
2. 用 Android Studio 打开
3. 同步 Gradle 依赖
4. 构建 APK：Build → Build Bundle(s) / APK(s) → Build APK(s)

## 致谢

- [KernelSU](https://github.com/tiann/KernelSU) - 内核级 Root 方案
- [Shizuku](https://github.com/RikkaApps/Shizuku) - ADB Shell 权限管理

## 许可证

本项目仅供学习研究使用，请勿用于非法用途。

## 免责声明

使用本工具造成的一切后果由使用者自行承担。开发者不对任何数据丢失、设备损坏或法律问题负责。