# FileHelper 使用指南

## 概述

FileHelper是Nop平台提供的**文件处理工具类**，用于统一处理各种文件相关操作，支持文件读写、复制、移动、删除、目录操作、文件属性处理等功能。FileHelper设计简洁易用，提供了丰富的文件运算API，是Nop平台中处理文件的核心组件。

## 核心功能

### 1. 文件读写
- `readBytes(file)`：读取文件字节
- `writeBytes(file, bytes)`：写入文件字节
- `readText(file, encoding)`：读取文件文本
- `readLines(file, encoding)`：读取文件行
- `writeText(file, text, encoding)`：写入文件文本
- `writeText(file, text, encoding, append)`：写入文件文本，支持追加
- `writeTextIfNotMatch(file, text, encoding)`：仅当内容不匹配时写入文件
- `readProperties(file)`：读取属性文件，返回Properties对象
- `countLines(file)`：统计文件行数

### 2. 文件复制和移动
- `copyFile(srcFile, dstFile)`：复制单个文件
- `moveFile(srcFile, dstFile)`：移动单个文件
- `copyWithFilter(dir1, dir2, filter)`：复制整个目录，允许通过`BiFunction<File,File,Boolean>`传入可选的过滤条件，两个参数分别是源文件和目标文件，返回true表示允许复制。


### 3. 目录操作
- `isEmptyDir(dir)`：判断目录是否为空
- `assureParent(file)`：确保父目录存在
- `assureFileExists(file)`：确保文件存在
- `walk(file, fn)`：深度遍历文件目录，`fn`参数类型为`Function<File, FileVisitResult>`，返回`java.nio.file.FileVisitResult`控制遍历流程
- `walk2(dir1, dir2, fn)`：以第一个目录为基准，深度遍历两个匹配的目录，`fn`参数类型为`BiFunction<File, File, FileVisitResult>`，返回`FileVisitResult`控制遍历流程。第二个参数targetFile不会为null，但是exists()可能是false，表示在dir2中不存在对应路径的文件。


### 4. 文件路径处理
- `currentDir()`：获取当前目录
- `setCurrentDir(dir)`：设置当前目录
- `resolveFile(path)`：根据文件路径（可以是fileUrl）确定对应File对象
- `resolveRelativeFile(baseDir, fileName)`：根据baseDir确定相对路径对应的文件对象
- `getAbsolutePath(file)`：获取标准化的绝对路径
- `getAbsoluteFile(file)`：获取标准化的绝对文件
- `getCanonicalPath(file)`：获取规范化路径
- `getRelativePath(base, file)`：获取相对路径

### 5. 文件查找
- `findFilesByAntPath(dir, pattern, recursive)`：根据Ant路径模式查找文件,返回`List<File>`
- `findFilePaths(dir, pattern, recursive, returnRelativePath)`：根据Ant路径模式查找文件,返回`List<String>`
    - `dir`：搜索根目录
    - `pattern`：Ant风格模式（如 `**/*.txt`, `docs/*.md`）
    - `recursive`：是否递归搜索子目录
    - `returnRelativePath`：true返回相对于dir的相对路径，false返回绝对路径

### 6. 文件删除
- `deleteAll(file)`：递归删除文件或目录
- `deleteIfExists(file)`：如果文件存在则删除

### 7. 符号链接操作
- `symlink(targetFile, linkFile, overwrite)`：创建符号链接
- `readSymbolicLink(symlinkFile)`：读取符号链接目标

### 8. 文件权限
- `chmod(file, mode)`：修改文件权限

### 9. URL和路径转换
- `toURL(file)`：转换为URL
- `getFileUrl(file)`：获取`file://xx`协议对应的url字符串
- `buildFileUrl(path)`：根据文件路径构建`file://xx`协议的url字符串
- `getJarEntryUrl(file, entryName)`：获取JAR条目URL，返回`jar:file://xx!/entryName`格式的url字符串

### 10. 文件内容处理
- `calculateMD5(file)`：计算文件MD5值
- `removeChildWithPrefix(dir, prefix)`：删除指定前缀的子文件
- `createNewFile(file)`：创建新文件

## 示例代码

```java
import io.nop.commons.util.FileHelper;
import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.Properties;

// 文件读写
byte[] bytes = FileHelper.readBytes(new File("test.txt"));
FileHelper.writeBytes(new File("test.txt"), bytes);

String text = FileHelper.readText(new File("test.txt"), "UTF-8");
FileHelper.writeText(new File("test.txt"), "new content", "UTF-8");
FileHelper.writeText(new File("test.txt"), "append content", "UTF-8", true);

// 仅当内容不匹配时写入
FileHelper.writeTextIfNotMatch(new File("test.txt"), "new content", "UTF-8");

// 统计文件行数
int lines = FileHelper.countLines(new File("test.txt"));

// 读取属性文件
Properties props = FileHelper.readProperties(new File("config.properties"));

// 文件复制和移动
File srcFile = new File("src.txt");
File dstFile = new File("dst.txt");
FileHelper.copyFile(srcFile, dstFile);

// 带过滤条件的文件复制
FileHelper.copyWithFilter(new File("src_dir"), new File("dst_dir"), 
    (sourceFile, targetFile) -> sourceFile.getName().endsWith(".txt"));

boolean success = FileHelper.moveFile(srcFile, dstFile);

// 目录操作
boolean isEmpty = FileHelper.isEmptyDir(new File("dir"));
FileHelper.assureParent(new File("dir/file.txt"));
FileHelper.assureFileExists(new File("file.txt"));

// 遍历文件目录
FileHelper.walk(new File("dir"), file -> {
    if (file.isFile()) {
        System.out.println("File: " + file.getName());
    }
    return FileVisitResult.CONTINUE;
});

// 文件路径处理
File currentDir = FileHelper.currentDir();
FileHelper.setCurrentDir(new File("new/dir"));

// 解析文件路径
File resolvedFile = FileHelper.resolveFile("./test.txt");

// 获取相对路径
String relativePath = FileHelper.getRelativePath(new File("base_dir"), new File("base_dir/sub/file.txt")); // 返回sub/file.txt

// 文件查找
List<String> txtFiles = FileHelper.findFilePaths(new File("dir"), "**/*.txt", true, true);

// 文件删除
FileHelper.deleteAll(new File("temp_dir"));
FileHelper.deleteIfExists(new File("temp.txt"));

// 计算文件MD5值
String md5 = FileHelper.calculateMD5(new File("file.txt"));
```

## 最佳实践

1. **优先使用**：所有文件操作优先使用FileHelper，避免直接使用Java IO API
2. **异常处理**：FileHelper会将IOException包装为NopException，便于统一异常处理
3. **编码处理**：默认使用UTF-8编码，可通过参数指定其他编码
4. **父目录确保**：在创建或写入文件前，使用assureParent()确保父目录存在
5. **条件写入**：使用writeTextIfNotMatch()避免不必要的文件写入，提高性能
6. **文件路径管理**：使用currentDir()和setCurrentDir()管理当前工作目录

## 注意事项

- 所有方法都是静态的，直接调用
- 异常处理：所有方法都会将IOException包装为NopException
- 编码支持：支持多种编码，默认使用UTF-8
- 平台兼容性：支持不同平台的文件路径格式

## 替代方案

避免使用以下第三方库：
- Apache Commons IO
- Google Guava Files
- 自定义文件工具类
