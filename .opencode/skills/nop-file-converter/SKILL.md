---
name: nop-file-converter
description: 使用 NOP CLI 工具进行XLang模型文件格式转换，支持 XDSL 模型文件在不同格式（XML/JSON/YAML/XLSX）之间转换
---

## 我会做什么

- 使用 NOP CLI 的 `convert` 命令在不同 DSL 模型文件格式之间进行转换
- 支持 XML、JSON、YAML、XLSX 等格式之间的相互转换
- 从 Word 文档（docx）中提取附件（如图片）到指定目录
- 正确处理 Nop 框架的 fileType 概念，确保模型文件类型识别准确

## 什么时候用我

**当用户要求进行以下操作时，直接使用本技能，无需额外探索代码库：**

- 将 ORM 模型文件从 XLSX 转换为 XML（或反向）
- 将 API 模型文件在不同格式间转换（XML/JSON/YAML/XLSX）
- 转换任何 XDSL 模型文件的格式

本技能提供 `nop-cli convert` 命令的标准用法，已在技能描述中包含完整示例。

## 核心功能

### 文件格式转换
- **命令**: `nop-cli convert`
- **参数**: 
  - `inputFile`: 输入文件路径（必填）
  - `-o`: 输出文件路径（必填）
  - `-a`: 附件目录路径（可选，用于 docx 文件）

### 重要概念：fileType

Nop 框架使用 `fileType` 来识别文件类型，与普通文件扩展名不同：

- **复合文件类型**：对于 `orm.xml`、`api.xlsx` 等文件名，fileType 取最后两个部分
- **简单文件类型**：对于 `xlsx`、`xml` 等文件名，fileType 就是文件扩展名

**为什么重要**：Nop 框架大量使用复合文件名（如 `app.orm.xml`），fileType 能区分普通文件和专用于特定模型的文件。ResourceComponentManager 根据 fileType 查找对应的 FileLoader，确保模型文件正确解析。

## 使用示例

### 转换 ORM 模型文件
```bash
nop-cli convert input.orm.xml -o output.orm.json
```

### 转换 API 模型文件
```bash
nop-cli convert input.api.xlsx -o output.api.yaml
```

### 转换 Word 文档并提取附件
```bash
nop-cli convert input.docx -o output.md -a media
```