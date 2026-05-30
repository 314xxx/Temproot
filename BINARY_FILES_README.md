# 二进制文件说明

## 需要手动添加的文件

本仓库需要以下两个二进制文件才能正常工作：

### 1. cf (SELinux exploit tool)
- **大小**: 25,616 字节
- **用途**: 通过 GPU DMA 时序漏洞修改 SELinux 策略
- **来源**: 参考项目 `K60_AxManager一键免解root插件v1.1`

### 2. ksud (KernelSU daemon)
- **大小**: 4,010,944 字节
- **用途**: KernelSU 守护进程，提供 Root 权限
- **来源**: 参考项目 `K60_AxManager一键免解root插件v1.1`

## 添加步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/314xxx/Temproot.git
   cd Temproot
   ```

2. **获取二进制文件**
   
   从参考项目中获取 `cf` 和 `ksud` 文件：
   - 下载 `K60_AxManager一键免解root插件v1.1.zip`
   - 解压后找到 `files/cf` 和 `files/ksud`

3. **复制文件到正确位置**
   ```bash
   # 复制 cf 文件
   cp /path/to/cf app/src/main/assets/cf
   
   # 复制 ksud 文件
   cp /path/to/ksud app/src/main/assets/ksud
   ```

4. **提交更改**
   ```bash
   git add app/src/main/assets/
   git commit -m "Add binary files (cf, ksud)"
   git push
   ```

## 文件结构

添加完成后，assets 目录结构应如下：
```
app/src/main/assets/
├── cf      # SELinux exploit (25KB)
└── ksud    # KernelSU daemon (4MB)
```

## 注意事项

⚠️ **重要**: 
- 这些二进制文件是特定架构的（ARM64）
- 仅适用于 MIUI/HyperOS 系统
- 使用前请确保设备兼容
- 临时 Root 重启后失效

## 参考链接

- 酷安原帖: https://www.coolapk.com/feed/71852731
- KernelSU: https://github.com/tiann/KernelSU