# nop-ai-toolkit 设计文档

## 概述

nop-ai-toolkit 是 Nop 平台的 AI 工具执行模块，提供 LLM 与外部工具的集成能力。

## 核心模型

| 类名 | 说明 |
|------|------|
| `AiToolModel` | 工具定义：name、description、schema、examples |
| `AiToolCall` | 单次工具调用请求：id、input、inputFiles、timeoutMs |
| `AiToolCalls` | 批量工具调用：AiToolCall 列表，支持 parallel/maxConcurrency |
| `AiToolCallResult` | 单次调用结果：status、output、error、outputFiles |
| `AiToolCallsResponse` | 批量调用响应：AiToolCallResult 列表 |
| `AiCommonToolCallResult` | 通用工具结果（type=tool-result） |
| `AiAgentCallResult` | Agent 调用结果（type=agent-result，含 sessionId） |

## 工具加载机制

工具定义存储在 VFS 固定路径下，通过 `ResourceComponentManager` 加载，**不需要运行时注册机制**。

### register-model 配置

```xml
<!-- /nop/core/registry/ai-tool.register-model.xml -->
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="ai-tool">
    <loaders>
        <xdsl-loader fileType="tool.xml" schemaPath="/nop/schema/ai/tool/tool.xdef"/>
    </loaders>
</model>
```

### toolName 命名规范

toolName 对应 XML 标签名，必须符合 `StringHelper.isValidXmlName()` 规则：

- 以字母或下划线开头
- 只能包含字母、数字、下划线、连字符、点
- 不允许连续分隔符（如 `--`）
- 不以分隔符结尾
- 推荐使用 kebab-case：`read-file`、`exec-command`

## 核心接口设计

### IToolExecutor - 工具执行器（泛型设计）

**关键设计**：IToolExecutor 使用泛型，每个 Executor 面对的是具体的强类型对象，无需自己转型。外部（IToolManager）根据 AiToolModel 的 schema 定义（解析得到 IXDefinition 对象并缓存）来解析得到强类型对象，再调用 executor。

```java
import java.util.concurrent.CompletionStage;

public interface IToolExecutor {
  /** 工具名称（需符合 XML 命名规范） */
  String getToolName();

  /** 异步执行，接收强类型请求，返回强类型响应 */
  CompletionStage<AiToolCallResult> executeAsync(AiToolCall request, IToolExecuteContext context);
}
```

### Schema 解析机制

每个 tool.xml 文件中的 `<schema>` 节点定义了该工具的调用格式。IToolManager 在加载 AiToolModel 时：

1. 从 `AiToolModel.getSchema()` 获取 schema XNode
2. 使用 `XDefinitionParser` 将 schema 解析为 `IXDefinition` 对象
3. 缓存 `IXDefinition` 供后续调用时使用

```java
// 示例：read-file.tool.xml 中的 schema 定义
<schema>
    <read-file id="!int" explanation="!string"
               path="!full-path" fromLine="int" toLine="int" lastLines="int"/>
</schema>
```

### IToolManager - 工具管理器

```java
import io.nop.ai.toolkit.model.AiToolCallResult;

public interface IToolManager {
  /**
   * 执行单个工具调用
   *
   * 流程：
   * 1. 根据 toolName 加载 AiToolModel
   * 2. 获取缓存的 IXDefinition
   * 3. 使用 DslModelParser 将 input 解析为强类型请求对象
   * 4. 获取对应的 IToolExecutor
   * 5. 调用 executor.executeAsync(request, context)
   */
  CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall request, IToolExecuteContext context);

  /** 批量执行（支持并行） */
  CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context);

  /** 通过 VFS 遍历目录获取可用工具定义 */
  List<AiToolModel> listTools();

  /** 通过 ResourceComponentManager 加载指定工具定义 */
  AiToolModel loadTool(String toolName);
}
```

### IToolExecutorProvider - 执行器提供者

```java
public interface IToolExecutorProvider {
    /** 获取指定工具的执行器，不存在返回 null */
    IToolExecutor getExecutor(String toolName);
}
```

### IToolExecuteContext - 执行上下文

```java
public interface IToolExecuteContext {
    /** 工作目录 */
    File getWorkDir();

    /** 环境变量 */
    Map<String, String> getEnvs();

    /** 过期时间戳（毫秒） */
    long getExpireAt();

    /** 取消令牌 */
    ICancelToken getCancelToken();

    /** 虚拟文件系统 */
    IToolFileSystem getFileSystem();
}
```

### IToolCallInterceptor - 调用拦截器

```java
public interface IToolCallInterceptor {
    /** 调用前拦截，返回 false 则阻止 */
    default boolean beforeCall(String toolName, AiToolCall request, IToolExecuteContext context) {
        return true;
    }

    /** 调用后处理 */
    default void afterCall(String toolName, AiToolCall request, IToolExecuteContext context,
                           AiToolCallResult result) {}
}
```

## 执行流程

```
callTool(toolName, inputXml, context)
     │
     ├─→ 1. loadTool(toolName) 获取 AiToolModel
     │
     ├─→ 2. getToolSchema(toolName) 获取缓存的 IXDefinition
     │        └─→ 首次调用时解析 schema XNode 并缓存
     │
     ├─→ 3. new DslModelParser().parseWithXDef(xdef, inputNode)
     │        └─→ 将 XML 输入解析为强类型请求对象 R
     │
     ├─→ 4. interceptor.beforeCall(toolName, request, context)
     │        └─→ 返回 false 则终止
     │
     ├─→ 5. provider.getExecutor(toolName)
     │        └─→ 不存在则返回异常结果
     │
     ├─→ 6. executor.executeAsync(request, context)
     │        └─→ 检查 context.expireAt 超时
     │        └─→ 检查 context.cancelToken 取消
     │
     └─→ 7. interceptor.afterCall(toolName, request, context, result)
```

## 具体工具实现示例

### ReadFileExecutor

```java
public class ReadFileExecutor
    implements IToolExecutor<ReadFileRequest, ReadFileResult> {

    public static final String TOOL_NAME = "read-file";

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public Class<ReadFileRequest> getRequestClass() {
        return ReadFileRequest.class;
    }

    @Override
    public Class<ReadFileResult> getResultClass() {
        return ReadFileResult.class;
    }

    @Override
    public CompletableFuture<ReadFileResult> executeAsync(
            ReadFileRequest request, IToolExecuteContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                IToolFileSystem fs = context.getFileSystem();

                // 根据 fromLine/toLine/lastLines 决定读取方式
                if (request.getLastLines() != null && request.getLastLines() > 0) {
                    int totalLines = fs.countLines(request.getPath(), request.getLastLines());
                    int fromLine = Math.max(1, totalLines - request.getLastLines() + 1);
                    LineResult result = fs.readLines(request.getPath(), fromLine, totalLines, 1000);
                    return buildResult(request, result);
                } else {
                    TextResult result = fs.readText(request.getPath(), 100000);
                    return buildResult(request, result);
                }
            } catch (Exception e) {
                return buildError(request, e.getMessage());
            }
        });
    }

    // ... helper methods
}
```

### ReadFileRequest（自动生成或手写）

```java
// 从 schema 生成的请求类
public class ReadFileRequest extends AiToolCall {
    private String path;
    private Integer fromLine;
    private Integer toLine;
    private Integer lastLines;

    // getters and setters
}
```

## 虚拟文件系统

### IToolFileSystem

为文件相关工具提供统一的文件操作接口。所有限制性参数由调用方（tool 定义）传入。

```java
public interface IToolFileSystem {

    // ==================== 路径 ====================

    /** 规范化路径（解析 . 和 ..，统一为正斜杠） */
    String normalizePath(String path);

    /** 路径是否在允许范围内 */
    boolean isPathAllowed(String path);

    boolean exists(String path);
    boolean isFile(String path);
    boolean isDirectory(String path);

    // ==================== 文件读取 ====================

    /** 读取全文，maxChars 限制最大字符数，超长截断 */
    TextResult readText(String path, int maxChars);

    /** 读取指定行范围（1-based，包含），maxLineLength 限制单行长度 */
    LineResult readLines(String path, int fromLine, int toLine, int maxLineLength);

    /** 获取文件总行数，超过 maxLines 返回 maxLines */
    int countLines(String path, int maxLines);

    // ==================== 文件写入 ====================

    /** append=true 时追加，否则覆盖 */
    void writeText(String path, String content, boolean append);

    // ==================== 目录 ====================

    /** depth=0 列一级，depth>0 递归指定深度，maxCount 限制总数 */
    List<FileInfo> listDirectory(String dirPath, int depth, int maxCount);

    void mkdirs(String path);

    // ==================== 删除/移动/复制 ====================

    /** 删除文件或目录，recursive=true 时递归删除目录 */
    void delete(String path, boolean recursive);

    void move(String fromPath, String toPath);

    /** 复制文件或目录 */
    void copy(String fromPath, String toPath);

    // ==================== 搜索 ====================

    /** glob 搜索，maxCount 限制结果数 */
    List<FileInfo> glob(String pattern, int maxCount);

    /** 内容搜索，maxCount 限制结果数，maxLineLength 限制返回行长度 */
    List<SearchMatch> grep(String pattern, String searchDir, boolean recursive,
                           int maxCount, int maxLineLength);
}
```

### FileInfo - 文件信息

```java
public final class FileInfo {
    private final String path;
    private final String name;
    private final boolean directory;
    private final long size;
    private final long lastModified;

    public FileInfo(String path, String name, boolean directory, long size, long lastModified) {
        this.path = path;
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
    }

    public String getPath() { return path; }
    public String getName() { return name; }
    public boolean isDirectory() { return directory; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
}
```

### TextResult - 文本读取结果

```java
public final class TextResult {
    private final String path;
    private final String content;
    private final boolean truncated;

    public TextResult(String path, String content, boolean truncated) {
        this.path = path;
        this.content = content;
        this.truncated = truncated;
    }

    public String getPath() { return path; }
    public String getContent() { return content; }
    public boolean isTruncated() { return truncated; }
}
```

### LineResult - 行读取结果

```java
public final class LineResult {
    private final String path;
    private final int totalLines;
    private final int fromLine;
    private final int toLine;
    private final List<Line> lines;

    public LineResult(String path, int totalLines, int fromLine, int toLine, List<Line> lines) {
        this.path = path;
        this.totalLines = totalLines;
        this.fromLine = fromLine;
        this.toLine = toLine;
        this.lines = lines;
    }

    public String getPath() { return path; }
    public int getTotalLines() { return totalLines; }
    public int getFromLine() { return fromLine; }
    public int getToLine() { return toLine; }
    public List<Line> getLines() { return lines; }
}
```

### Line - 单行内容

```java
public final class Line {
    private final int lineNumber;
    private final String content;
    private final boolean truncated;

    public Line(int lineNumber, String content, boolean truncated) {
        this.lineNumber = lineNumber;
        this.content = content;
        this.truncated = truncated;
    }

    public int getLineNumber() { return lineNumber; }
    public String getContent() { return content; }
    public boolean isTruncated() { return truncated; }
}
```

### SearchMatch - 搜索匹配结果

```java
public final class SearchMatch {
    private final String filePath;
    private final int lineNumber;
    private final String line;
    private final String matchedText;
    private final boolean truncated;

    public SearchMatch(String filePath, int lineNumber, String line,
                       String matchedText, boolean truncated) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.line = line;
        this.matchedText = matchedText;
        this.truncated = truncated;
    }

    public String getFilePath() { return filePath; }
    public int getLineNumber() { return lineNumber; }
    public String getLine() { return line; }
    public String getMatchedText() { return matchedText; }
    public boolean isTruncated() { return truncated; }
}
```

## 组件关系

```
                          ┌──────────────┐
                          │ IToolManager │
                          └──────┬───────┘
                                 │
       ┌─────────────────────────┼─────────────────────────┐
       │                         │                         │
       ▼                         ▼                         ▼
┌──────────────┐         ┌────────────────┐        ┌──────────────┐
│  VFS 遍历     │         │IXDefinition    │        │IInterceptor  │
│  listTools() │         │  Schema 解析    │        │   拦截器      │
└──────┬───────┘         └───────┬────────┘        └──────┬───────┘
       │                         │                        │
       ▼                         ▼                        │
┌─────────────────┐      ┌─────────────────┐             │
│ Resource         │      │ DslModelParser  │             │
│ ComponentManager │      │ XML → 强类型对象 │             │
│ loadTool()      │      └───────┬─────────┘             │
└─────────────────┘              │                       │
                                 ▼                       │
                         ┌───────────────────┐           │
                         │ IToolExecutor<R,T>│◄──────────┘
                         │   executeAsync()  │
                         └─────────┬─────────┘
                                   │
                                   ▼
                         ┌───────────────────┐
                         │IToolExecuteContext│
                         │ workDir/envs/     │
                         │ expireAt/cancel   │
                         │ fileSystem        │
                         └─────────┬─────────┘
                                   │
                                   ▼
                         ┌───────────────────┐
                         │ IToolFileSystem   │
                         │    文件操作        │
                         └───────────────────┘
```

## XDef 模型文件

| 文件 | 定义 |
|------|------|
| `tool.xdef` | AiToolModel、AiToolExample |
| `tool-call.xdef` | AiToolCall、AiToolInputFile |
| `call-tools.xdef` | AiToolCalls（批量调用） |
| `call-tools-response.xdef` | AiToolCallsResponse、AiToolCallResult 等 |
| `avaliable-skills.xdef` | 可用技能列表 |

## 典型工具列表

| 工具名 | 说明 |
|--------|------|
| `read-file` | 读取文件内容 |
| `write-file` | 写入文件 |
| `list-dir` | 列出目录 |
| `glob` | 文件名搜索（glob） |
| `grep` | 内容搜索（grep） |
| `move-file` | 移动文件 |
| `copy-file` | 复制文件 |
| `delete-file` | 删除文件 |
| `create-dir` | 创建目录 |
