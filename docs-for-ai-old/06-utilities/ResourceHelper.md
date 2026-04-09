# ResourceHelper 使用指南

## 概述

ResourceHelper是Nop平台提供的**资源处理工具类**，用于统一处理各种资源相关操作，支持虚拟文件系统、资源路径管理、资源读写、压缩和解压缩等功能。ResourceHelper设计简洁易用，提供了丰富的资源运算API，是Nop平台中处理资源的核心组件。

## 核心功能

### 1. 资源路径处理
- `getAppProvider(path)`：获取应用提供者
- `getModuleId(path)`：获取模块ID
- `getModuleName(path)`：获取模块名称
- `getModuleIdFromModuleName(moduleName)`：从模块名称获取模块ID
- `getModuleNameFromModuleId(moduleId)`：从模块ID获取模块名称
- `getModuleIdFromStdPath(path)`：从标准路径获取模块ID
- `genDayRandPath()`：生成日期随机路径
- `getCpPath(path)`：获取类路径
- `resolveRelativeStdPath(currentPath, relativePath)`：解析相对标准路径
- `getPathNamespace(path)`：获取路径命名空间
- `startsWithNamespace(path, ns)`：判断路径是否以指定命名空间开头
- `removeNamespace(path, ns)`：移除路径中的命名空间
- `buildNamespacePath(ns, path)`：构建命名空间路径
- `buildDeltaPath(deltaLayerId, path)`：构建增量路径
- `buildTenantPath(tenantId, path)`：构建租户路径
- `isDeltaPath(path)`：判断是否为增量路径
- `isTenantPath(path)`：判断是否为租户路径
- `isNormalVirtualPath(path)`：判断是否为普通虚拟路径
- `newDynamicPath()`：生成动态路径
- `isTextResource(path)`：判断是否为文本资源
- `getSubPath(path)`：获取子路径
- `getName(path)`：获取资源名称
- `getStdPath(path)`：获取标准路径
- `getDeltaLayerId(path)`：获取增量层ID
- `getDumpPath(path)`：获取转储路径
- `getParentPath(path)`：获取父目录路径
- `normalizePath(path)`：标准化路径

### 2. 资源访问和创建
- `resolve(path)`：解析资源路径，获取资源对象
- `resolveSibling(resource, relativeName)`：解析兄弟资源
- `resolveSiblingWithExt(resource, ext)`：解析指定扩展名的兄弟资源
- `resolveRelativeResource(resource, relativePath, allowParent)`：解析相对资源
- `resolveChildResource(resource, subPath)`：解析子资源
- `getTempResource()`：获取临时资源
- `getTempResource(prefix)`：获取指定前缀的临时资源
- `getDumpResource(path)`：获取转储资源
- `getDumpResourceWithExt(path, ext)`：获取指定扩展名的转储资源
- `getRelatedResource(resource, postfix)`：获取相关资源
- `getSibling(resource, relativeName)`：获取兄弟资源
- `getSiblingWithExt(resource, ext)`：获取指定扩展名的兄弟资源

### 3. 资源读写
- `readText(resource)`：读取资源文本
- `readText(resource, encoding)`：读取资源文本，指定编码
- `readTextHeader(resource, encoding, maxChars)`：读取资源文本头部
- `writeText(resource, text)`：写入资源文本
- `writeText(resource, text, encoding)`：写入资源文本，指定编码
- `readBytes(resource)`：读取资源字节
- `writeBytes(resource, bytes)`：写入资源字节
- `writeStream(resource, is)`：从流写入资源
- `saveFromStream(resource, is)`：从流保存资源
- `saveFromStream(resource, is, listener)`：从流保存资源，支持进度监听
- `readProperties(resource)`：读取属性文件
- `writeProperties(resource, props)`：写入属性文件
- `readJson(resource)`：读取JSON资源
- `readJson(resource, type)`：读取JSON资源，指定类型
- `writeJson(resource, obj)`：写入JSON资源
- `writeJson(resource, obj, encoding, indent)`：写入JSON资源，指定编码和缩进
- `readXml(resource)`：读取XML资源
- `readXml(resource, encoding)`：读取XML资源，指定编码
- `readXml(resource, encoding, forHtml, keepComment)`：读取XML资源，支持HTML和注释保留
- `writeXml(resource, node)`：写入XML资源
- `writeXml(resource, node, encoding, indent)`：写入XML资源，指定编码和缩进
- `readObject(resource)`：读取对象资源
- `readObject(resource, serializer)`：读取对象资源，指定序列化器
- `writeObject(resource, obj)`：写入对象资源
- `writeObject(resource, obj, serializer)`：写入对象资源，指定序列化器
- `readState(resource, obj)`：读取状态到对象
- `readState(resource, obj, serializer)`：读取状态到对象，指定序列化器
- `writeState(resource, obj)`：写入对象状态
- `writeState(resource, obj, serializer)`：写入对象状态，指定序列化器

### 4. 资源验证和检查
- `isValidModuleName(moduleName)`：验证模块名称
- `checkValidModuleName(moduleName)`：检查模块名称有效性
- `isValidModuleId(moduleId)`：验证模块ID
- `checkValidModuleId(moduleId)`：检查模块ID有效性
- `checkValidModuleIds(ids)`：检查多个模块ID有效性
- `isValidRelativeName(name)`：验证相对名称
- `checkNormalVirtualPath(path)`：检查普通虚拟路径
- `checkValidRelativeName(path)`：检查有效相对名称
- `assertDirectory(resource)`：断言资源为目录
- `hasNamespace(path)`：检查是否有命名空间
- `hasNamespace(path, ns)`：检查是否有指定命名空间

### 5. 压缩和解压缩
- `zipDir(dir, target, options)`：压缩目录
- `zipLocalDir(dir, targetFile, options)`：压缩本地目录
- `zipDirToStream(dir, target, options)`：压缩目录到流
- `unzipToDir(resource, dir, options)`：解压到目录
- `unzip(resource)`：解压资源
- `getZipTool()`：获取ZIP工具
- `registerZipTool(zipTool)`：注册ZIP工具

### 6. 资源转换
- `toCharReader(resource, encoding)`：转换为字符读取器
- `buildTextScanner(resource, encoding)`：构建文本扫描器
- `toReader(resource, encoding)`：转换为读取器
- `toReader(resource, encoding, supportZip)`：转换为读取器，支持ZIP
- `toWriter(resource, encoding)`：转换为写入器
- `toWriter(resource, encoding, supportZip)`：转换为写入器，支持ZIP
- `toOutputStream(resource, supportZip)`：转换为输出流，支持ZIP

### 7. 目录操作
- `copyDir(srcDir, destDir)`：复制目录
- `copyDir(srcDir, destDir, filter, listener)`：复制目录，支持过滤和进度监听
- `copy(srcFile, destFile)`：复制文件
- `copy(srcFile, destFile, filter, listener)`：复制文件，支持过滤和进度监听
- `deleteAll(resource)`：递归删除资源

### 8. 调试和转储
- `dumpResource(resource, source)`：转储资源

## 示例代码

```java
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.IResource;
import io.nop.api.core.util.progress.IProgressListener;
import io.nop.core.lang.xml.XNode;

// 资源路径处理
String moduleId = ResourceHelper.getModuleId("/xapp/oa/_sys"); // "xapp/oa"
String moduleName = ResourceHelper.getModuleName("/xapp/oa/_sys"); // "xapp-oa"
String moduleIdFromPath = ResourceHelper.getModuleIdFromStdPath("/xapp/oa/_sys"); // "xapp/oa"
String dayRandPath = ResourceHelper.genDayRandPath(); // 生成日期随机路径
String cpPath = ResourceHelper.getCpPath("config.properties"); // 获取类路径
String relativePath = ResourceHelper.resolveRelativeStdPath("/a/b/", "../c.txt"); // "/a/c.txt"
String normalizedPath = ResourceHelper.normalizePath("/a/../b/c.txt"); // "/b/c.txt"

// 资源访问
IResource resource = ResourceHelper.resolve("classpath:config.properties");
IResource sibling = ResourceHelper.resolveSibling(resource, "config-dev.properties");
IResource siblingWithExt = ResourceHelper.resolveSiblingWithExt(resource, ".yaml");
IResource relativeResource = ResourceHelper.resolveRelativeResource(resource, "../other/config.properties", true);
IResource childResource = ResourceHelper.resolveChildResource(resource, "sub/config.properties");

// 资源读写
String text = ResourceHelper.readText(resource, "UTF-8");
ResourceHelper.writeText(resource, "new content", "UTF-8");
String textHeader = ResourceHelper.readTextHeader(resource, "UTF-8", 100);
byte[] bytes = ResourceHelper.readBytes(resource);
ResourceHelper.writeBytes(resource, bytes);

// JSON处理
Object jsonObj = ResourceHelper.readJson(resource);
Object typedJson = ResourceHelper.readJson(resource, Map.class);
ResourceHelper.writeJson(resource, jsonObj, "UTF-8", "  ");
ResourceHelper.writeJson(resource, jsonObj); // 简写形式

// XML处理
XNode xmlNode = ResourceHelper.readXml(resource, "UTF-8");
XNode htmlNode = ResourceHelper.readXml(resource, "UTF-8", true, true); // HTML模式，保留注释
ResourceHelper.writeXml(resource, xmlNode, "UTF-8", true);
ResourceHelper.writeXml(resource, xmlNode); // 简写形式

// 对象读写
Object obj = ResourceHelper.readObject(resource);
ResourceHelper.writeObject(resource, obj);

// 压缩和解压缩
ResourceHelper.zipDir(sourceDir, targetResource, null);
ResourceHelper.zipLocalDir(new File("src"), new File("target.zip"), null);
ResourceHelper.unzipToDir(zipResource, targetDir, null);

// 目录操作
ResourceHelper.copyDir(sourceDir, targetDir);
ResourceHelper.copyDir(sourceDir, targetDir, file -> file.getName().endsWith(".txt"), null);
ResourceHelper.copy(sourceFile, targetFile);
ResourceHelper.deleteAll(resource);

// 临时资源
IResource tempResource = ResourceHelper.getTempResource("temp/");
IResource tempResourceWithPrefix = ResourceHelper.getTempResource("prefix/");

// 资源转换
Reader reader = ResourceHelper.toReader(resource, "UTF-8");
Writer writer = ResourceHelper.toWriter(resource, "UTF-8");
OutputStream os = ResourceHelper.toOutputStream(resource, true);
```

## 最佳实践

1. **优先使用**：所有资源操作优先使用ResourceHelper，避免直接使用Java IO API
2. **命名空间管理**：合理使用命名空间，区分不同类型的资源
3. **路径标准化**：使用getStdPath()获取标准路径，确保路径一致性
4. **资源验证**：在使用资源前，使用验证方法确保资源路径和名称的有效性
5. **临时资源管理**：使用getTempResource()获取临时资源，避免资源泄露
6. **压缩和解压缩**：使用内置的压缩和解压缩功能，避免依赖第三方库
7. **调试和转储**：在调试模式下，使用dumpResource()转储资源，便于调试

## 注意事项

- 所有方法都是静态的，直接调用
- null值处理：大部分方法会处理null值，返回合理结果
- 资源路径格式：资源路径格式为[namespace:][path]，例如classpath:config.properties
- 编码处理：默认使用UTF-8编码，可通过参数指定其他编码
- 压缩支持：支持GZIP压缩，文件名以.gz结尾时自动启用
- 虚拟文件系统：ResourceHelper基于虚拟文件系统，支持多种资源类型

## 替代方案

避免使用以下第三方库：
- Apache Commons IO
- Google Guava Files
- 自定义资源处理工具类

ResourceHelper提供了全面的资源操作功能，能够满足大多数场景的需求，同时保持了代码的简洁性和易用性。