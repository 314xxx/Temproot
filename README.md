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
| fuxi | 小米13 | 2022-12-12 | ✅ 完全支持 |

### 小米12 系列

| 设备代号 | 型号 | 发布时间 | 状态 |
|---------|------|---------|------|
| cupid | 小米12 | 2021-12-28 | ⚠️ 需要二月补丁之前 |
| psyche | 小米12X | 2021-12-28 | ❌ 不支持 |
| zeus | 小米12 Pro | 2021-12-28 | ❌ 不支持（天玑版） |
| daumier | 小米12 Pro 天玑版 | 2022-07-04 | ❌ 不支持 |

### 支持的处理器

- 骁龙 8+ Gen 1
- 骁龙 8 Gen 1
- 骁龙 7+ Gen 2
- 骁龙 870
- 骁龙 888

**其他机型请勿使用！**

**要求**: 
- 安全补丁日期 ≤ 2025-02-01
- 已解锁 Bootloader
- 已安装 Shizuku

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
├── BINARY_FILES_README.md  # 二进制文件说明
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
- [Uotan Wiki](https://wiki.uotan.cn) - 小米设备代号参考

## 许可证

本项目仅供学习研究使用，请勿用于非法用途。

## 免责声明

使用本工具造成的一切后果由使用者自行承担。开发者不对任何数据丢失、设备损坏或法律问题负责。

## 参考链接

- 酷安原帖: https://www.coolapk.com/feed/71852731
- KernelSU: https://github.com/tiann/KernelSU
- 小米设备代号: https://wiki.uotan.cn/index.php?title=小米手机设备代号名称对照表