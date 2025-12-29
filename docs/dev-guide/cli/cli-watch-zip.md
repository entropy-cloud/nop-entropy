# CliWatchZipCommand 使用说明

## 功能描述

`CliWatchZipCommand` 是一个命令行工具，用于监控指定目录的文件变化，并自动将目录内容打包成 ZIP 文件。当目录中的文件发生创建、修改或删除时，工具会自动更新对应的 ZIP 文件。

经常用于调试xlsx和docx文件格式。

## 命令格式

```bash
nop-cli watch-zip <watchDir> [zipFile] [options]
```

## 参数说明

### 位置参数

- `watchDir`: 要监控的目录路径（必需）
- `zipFile`: 目标 ZIP 文件路径（可选，默认为 `<watchDir>.zip`）

### 选项参数

- `-w, --wait <milliseconds>`: 防抖等待间隔，单位毫秒（默认：100）
- `-r, --recursive`: 是否递归监控子目录（默认：true）
- `-h, --help`: 显示帮助信息
- `-V, --version`: 显示版本信息

## 使用示例

### 基本用法

监控当前目录下的 `myproject` 目录，自动生成 `myproject.zip`：

```bash
nop-cli watch-zip ./myproject
```

### 指定 ZIP 文件名

监控 `src` 目录，生成 `backup.zip`：

```bash
nop-cli watch-zip ./src ./backup.zip
```

### 设置防抖间隔

监控目录并设置 500ms 的防抖间隔：

```bash
nop-cli watch-zip ./myproject --wait 500
```

## 工作原理

1. 工具启动后会立即开始监控指定目录
2. 当检测到文件变化时，会触发防抖机制
3. 在防抖等待时间内，如果有新的变化，会重置计时器
4. 防抖时间结束后，自动执行 ZIP 打包操作
5. 按 Enter 键可以停止监控

## 日志输出

工具会输出以下类型的日志：

- `nop.zip-tool.on-file-change`: 文件修改事件
- `nop.zip-tool.on-file-create`: 文件创建事件
- `nop.zip-tool.on-file-delete`: 文件删除事件
- `nop.zip-tool.updating-zip`: 开始更新 ZIP 文件
- `nop.zip-tool.zip-updated`: ZIP 文件更新完成
- `nop.zip-tool.zip-update-failed`: ZIP 文件更新失败

## 注意事项

1. 确保有足够的磁盘空间存储 ZIP 文件
2. 监控大型目录时，建议适当增加防抖等待时间
3. 工具会覆盖已存在的同名 ZIP 文件
4. 按 Ctrl+C 或 Enter 键可以安全退出程序

## 适用场景

- 开发过程中自动备份项目文件
- 实时同步目录内容到 ZIP 归档
- 自动化构建流程中的文件打包
- 调试和测试 ZIP 文件生成过程
