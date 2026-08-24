# TempRoot - HyperOS 一键临时 Root 应用

一个基于 KernelSU late-load 注入的 Android 应用，内置 ADB 无线自配对（无需 Shizuku），一键获取临时 Root 权限。界面采用 Miuix / HyperOS 风格设计。

## 功能特性

- ✅ **无需 Shizuku** - 内置 ADB 客户端，通过无线调试自配对独立获取 shell 权限
- ✅ **mDNS 自动发现端口** - 参考 AxManager/Shizuku 方案，配对只需输入 6 位配对码，连接全自动
- ✅ **配对一次永久使用** - ADB 证书持久化保存，重启应用后无需重复配对
- ✅ 一键获取临时 Root（重启失效）
- ✅ 自动执行 SELinux 宽容注入（cf 漏洞利用）
- ✅ 自动执行 MQSAS 服务注入（ksud）
- ✅ ksud 版本自动跟随已安装的 KernelSU 管理器（避免版本不匹配）
- ✅ 设备支持表外置 JSON，新增机型无需重新发版
- ✅ Miuix / HyperOS 风格界面（小米橙主色 + 大圆角卡片）
- ✅ 分阶段日志实时输出
- ✅ 可配置重试次数与 ADB 端口

## 支持设备

### 适用机型

| 设备代号 | 型号 | 发布时间 | 状态 |
|---------|------|---------|------|
| socrates | Redmi K60 Pro | 2022-12-27 | ✅ 完全支持 |
| mondrian | Redmi K60 / POCO F5 Pro | 2022-12-27 | ✅ 完全支持 |
| rembrandt | Redmi K60E | 2022-12-27 | ✅ 完全支持 |
| rubens | Redmi K50 | 2022-03-17 | ✅ 完全支持 |
| matisse | Redmi K50 Pro | 2022-03-17 | ✅ 完全支持 |
| diting | Redmi K50 至尊版 / 小米12T Pro | 2022-08-11 | ✅ 完全支持 |
| ingres | Redmi K50 电竞版 / POCO F4 GT | 2022-02-16 | ✅ 完全支持 |
| munch | Redmi K40S / POCO F4 | 2022-03-17 | ✅ 完全支持 |
| marble | Redmi Note 12 Turbo / POCO F5 | 2023-03-28 | ✅ 完全支持 |
| mayfly | 小米12S | 2022-07-04 | ✅ 完全支持 |
| fuxi | 小米13 | 2022-12-11 | ✅ 完全支持 |

### 小米12 系列

| 设备代号 | 型号 | 发布时间 | 状态 |
|---------|------|---------|------|
| cupid | 小米12 | 2021-12-28 | ⚠️ 需要二月补丁之前 |
| psyche | 小米12X | 2021-12-28 | ❌ 不支持 |
| zeus | 小米12 Pro | 2021-12-28 | ❌ 不支持 |
| daumier | 小米12 Pro 天玑版 | 2022-07-04 | ❌ 不支持 |

### 支持的处理器

- 骁龙 8+ Gen 1
- 骁龙 8 Gen 1
- 骁龙 7+ Gen 2

**其他机型请勿使用！**

**要求**: 安全补丁日期 ≤ 2025-02-01

## 安装与使用

### 1. 安装应用

从 [Releases](../../releases) 下载最新 APK 并安装。

### 2. 首次配对（一次性）

1. 打开手机「设置 → 开发者选项 → 无线调试」并开启
2. 回到 TempRoot，点击「配对」
3. 在手机上点击「使用配对码配对设备」——应用会**自动检测配对服务**（mDNS 发现，无需输入端口）
4. 输入弹窗显示的 **6 位配对码**，点击「配对」
5. 配对成功后自动发现连接端口并连接

> 配对端口与连接端口均由 mDNS 自动发现，全程无需手动输入任何端口。
> 配对证书已持久化保存，之后无需再次配对。

### 3. 一键 Root

1. 打开 TempRoot，确认 ADB 状态卡显示绿色"已连接"
2. 点击「一键 Root」按钮
3. 等待执行完成（SELinux 宽容可能需要多次尝试，请耐心等待）
4. 完成后查看日志与 Root 状态

### 4. 验证 Root

```bash
# 在终端中执行
su -c id
# 应显示 uid=0(root)
```

## 技术原理

1. **ADB 无线自配对**: 内置 [Kadb](https://github.com/flyfishxu/Kadb) 客户端（SPAKE2 + TLS + RSA），直连本机 `127.0.0.1` 的 adbd 获取 shell 权限，替代 Shizuku
2. **mDNS 端口自动发现**: 参考 [AxManager](https://github.com/fahrez182/AxManager)/Shizuku 方案，通过 NsdManager 监听 `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp` 服务广播，配对与连接端口全自动获取（含本机地址与端口占用双重校验）
3. **cf 漏洞利用**: 通过 GPU DMA 时序漏洞修改 SELinux 策略为宽容模式
4. **MQSAS 注入**: 利用小米系统服务漏洞启动 ksud 守护进程
5. **KernelSU late-load**: 在系统启动后加载内核级 Root 方案

## 项目结构

```
TempRoot/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── cf                 # SELinux 宽容注入二进制
│   │   │   ├── ksud               # KernelSU 管理器守护进程
│   │   │   └── supported_devices.json  # 设备支持表
│   │   ├── java/com/temproot/app/
│   │   │   ├── MdnsDiscovery.kt     # mDNS 端口自动发现（NsdManager）
│   │   │   ├── AdbShell.kt        # ADB 连接管理（配对/连接/命令/push）
│   │   │   ├── RootManager.kt     # Root 流程（环境检查/注入/状态）
│   │   │   ├── MainActivity.kt    # 主界面（Miuix 风格）
│   │   │   └── SettingsScreen.kt  # 设置页
│   │   ├── res/                   # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts           # 应用构建配置
├── README.md                      # 本文档
├── BINARY_FILES_README.md         # 二进制文件说明
└── settings.gradle.kts            # 项目设置
```

## 配置说明

### 应用内设置

- **备用端口（可选）**: mDNS 自动发现失败时使用的连接端口，一般无需填写
- **cf 最大重试次数**: SELinux 宽容注入的尝试上限（推荐 50-100）

### 设备支持表（外部 JSON）

放置 `/sdcard/temproot_devices.json` 可覆盖内置支持表，**新增机型无需重新发版**：

```json
{
  "safePatchDate": "2025-02-01",
  "devices": [
    { "codename": "socrates", "name": "Redmi K60 Pro", "kernels": [] }
  ]
}
```

- `codename`: 设备代号（`Build.DEVICE`）
- `kernels`: 可选，支持的内核版本前缀列表（`uname -r`），留空则不校验内核

### ksud 版本策略

检测到已安装 KernelSU / ReSukiSU 管理器时，自动复用其 `libksud.so`，保证 ksud 与管理器版本一致；否则使用内置版本。

## 注意事项

⚠️ **重要警告**:

1. **临时 Root**: 重启手机后 Root 权限将失效
2. **风险提示**: 使用不当可能导致系统不稳定或数据丢失
3. **仅限特定机型**: 不支持的设备请勿使用
4. **安全补丁**: 安全补丁日期高于 2025-02-01 的设备可能无法成功
5. **备份数据**: 使用前请备份重要数据

## 隐私声明

- 本应用不收集任何用户数据
- 所有操作均在本地执行（ADB 连接目标为本机回环地址 127.0.0.1）

## 开发说明

### 构建环境

- JDK 17
- AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.9
- Jetpack Compose (BOM 2024.09.03) + Material3
- [Kadb](https://github.com/flyfishxu/Kadb) 1.2.1（ADB 客户端）

### 编译步骤

1. 克隆项目到本地
2. 用 Android Studio 打开
3. 同步 Gradle 依赖
4. 构建 APK：Build → Build Bundle(s) / APK(s) → Build APK(s)

或命令行：`./gradlew assembleDebug`

## 致谢

- [KernelSU](https://github.com/tiann/KernelSU) - 内核级 Root 方案
- [Kadb](https://github.com/flyfishxu/Kadb) - Kotlin ADB 客户端（无线配对）
- [AxManager](https://github.com/fahrez182/AxManager) - mDNS 端口自动发现方案参考
- [Uotan Wiki](https://wiki.uotan.cn) - 小米设备代号参考

## 许可证

本项目仅供学习研究使用，请勿用于非法用途。

## 免责声明

使用本工具造成的一切后果由使用者自行承担。开发者不对任何数据丢失、设备损坏或法律问题负责。

## 参考链接

- KernelSU: https://github.com/tiann/KernelSU
- Kadb: https://github.com/flyfishxu/Kadb
- 小米设备代号: https://wiki.uotan.cn/index.php?title=小米手机设备代号名称对照表
