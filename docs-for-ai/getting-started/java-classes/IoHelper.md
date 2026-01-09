# IoHelper 使用指南

## 概述

`IoHelper` 是 Nop 平台提供的 I/O 操作工具类，用于统一处理各类输入/输出操作。它封装了流处理、数据读写、资源安全关闭等常用功能，设计简洁，是平台中处理 I/O 任务的核心组件。

## 核心功能

### 1. 流处理与转换
提供流与读写器之间的转换，并支持缓冲包装以提升性能。
- `toReader(is, encoding)`：将输入流按指定编码转换为 `Reader`。
- `toWriter(os, encoding)`：将输出流按指定编码转换为 `Writer`。
- `toBufferedInputStream(is)`：智能转换为缓冲输入流。仅当流不是BufferedInputStream、ByteArrayInputStream或IBufferedStream时才进行包装，避免重复缓冲。
- `toBufferedOutputStream(os)`：智能转换为缓冲输入流。
- `toBufferedWriter(writer)`：智能转换为缓冲输入流。
- `toBufferedReader(reader)`

### 2. 数据读写与复制
提供高效的数据复制和内容读取方法。
- `copy(is, os)`：复制输入流到输出流。
- `copy(reader, writer)`：复制 `Reader` 内容到 `Writer`。
- `readText(reader)`：从 `Reader` 读取全部文本内容。
- `readText(is, encoding)`：从输入流按编码读取全部文本。
- `readBytes(is)`：从输入流读取全部字节。
- `read(is, bytes,off,len)`：读取最多 `len` 个字节到数组，返回实际读取长度，`-1` 表示流结束。
- `readFully(is,bytes)`：完全填满目标字节数组。
- `readFully(is, bytes, off, len)`：完全读取 `len` 个字节到指定位置。若数据不足，抛出 `NopException`（错误码 `nop.err.commons.io.unexpected-eof`）。
- `readTill(is, endChar, bytes, off)`：读取直到遇到 `endChar` 字节，返回实际读取长度，`-1` 表示流结束。

### 3. 资源安全关闭
- `safeClose(o)`：安全关闭实现了 `AutoCloseable` 接口的资源对象。
- `safeCloseAll(c)`：安全关闭集合中的所有资源对象。

## 示例代码

```java
import io.nop.commons.util.IoHelper;
import io.nop.api.core.exceptions.NopException;
import java.io.*;


InputStream source = null;
InputStream fullyIs1 = null;
InputStream fullyIs2 = null;

try {
    // 示例1：读取文本
    source = new ByteArrayInputStream("文本内容".getBytes("UTF-8"));
    String text = IoHelper.readText(source, "UTF-8");
    System.out.println(text);

    // 示例2：完全读取字节数组
    fullyIs1 = new ByteArrayInputStream("完全读取测试".getBytes());
    byte[] buffer1 = new byte[5];
    IoHelper.readFully(fullyIs1, buffer1, 0, buffer1.length);

    // 示例3：分段读取
    fullyIs2 = new ByteArrayInputStream("分段读取测试".getBytes());
    byte[] buffer2 = new byte[10];
    IoHelper.readFully(fullyIs2, buffer2, 0, 5); // 读取前5字节
    IoHelper.readFully(fullyIs2, buffer2, 5, 5); // 读取后5字节
} catch (IOException e) {
    throw NopException.adapt(e);
} finally {
    // 统一安全关闭资源
    IoHelper.safeClose(source);
    IoHelper.safeClose(fullyIs1);
    IoHelper.safeClose(fullyIs2);
}
```

## 最佳实践

1.  **始终关闭资源**：使用 `try-finally` 块或 `try-with-resources` 语句，并配合 `safeClose` 确保任何情况下资源都被释放。
2.  **使用缓冲包装**：对于频繁的小规模读写操作，使用 `toBufferedXXX` 方法进行包装，可显著提升 I/O 效率。
3.  **明确指定编码**：在文本与字节流转换时，始终明确指定字符编码（如 `UTF-8`），避免依赖平台默认编码，确保行为一致。

## 替代方案

在 Nop 平台项目中，建议统一使用 `IoHelper`替代以下常见方案：
- Apache Commons IO 的 `IOUtils`
- Google Guava 的 `Files` 或 `ByteStreams`
- 项目内自行封装的 I/O 工具类
