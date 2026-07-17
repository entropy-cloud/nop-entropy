# AgentScope vs Nop 文件系统抽象：接口能力对比与 Delta 分析

> 本篇为 `agentscope-harness-vs-nop-ai-agent-comparison.md` 的补充，聚焦接口层对比，并以 Nop Delta 视角做深度分析。

---

## 1. 接口方法签名对比

### AgentScope `AbstractFilesystem`

所有方法签名携带 `RuntimeContext`，返回结果对象（不抛异常）：

```
LsResult      ls(RuntimeContext, String path)
ReadResult    read(RuntimeContext, String filePath, int offset, int limit)
WriteResult   write(RuntimeContext, String filePath, String content)
EditResult    edit(RuntimeContext, String filePath, String oldString, String newString, boolean replaceAll)
GrepResult    grep(RuntimeContext, String pattern, String path, String glob)
GlobResult    glob(RuntimeContext, String pattern, String path)
WriteResult   delete(RuntimeContext, String path)
WriteResult   move(RuntimeContext, String fromPath, String toPath)
boolean       exists(RuntimeContext, String path)
List<FileUploadResponse>   uploadFiles(RuntimeContext, List<Map.Entry<String, byte[]>>)
List<FileDownloadResponse> downloadFiles(RuntimeContext, List<String> paths)
```

额外方法在 `AbstractSandboxFilesystem`（子接口）：
```
ExecuteResponse execute(RuntimeContext, String command, Integer timeoutSeconds)
String          id()
```

### Nop `IToolFileSystem`

无 `RuntimeContext`，使用异常或 null 返回值：

```
String        normalizePath(String path)
boolean       isPathAllowed(String path)
boolean       exists(String path)
boolean       isFile(String path)
boolean       isDirectory(String path)
TextResult    readText(String path, int maxChars)
LineResult    readLines(String path, int fromLine, int toLine, int maxLineLength)
int           countLines(String path, int maxLines)
void          writeText(String path, String content, boolean append)
List<FileInfo> listDirectory(String dirPath, int depth, int maxCount)
void          mkdirs(String path)
void          delete(String path, boolean recursive, boolean force)
void          move(String fromPath, String toPath, boolean overwrite)
void          copy(String fromPath, String toPath, boolean recursive, boolean overwrite)
List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults)
List<SearchMatch> grep(String pattern, String searchDir, boolean recursive,
                       boolean ignoreCase, int maxMatchesPerFile, int maxFiles, int maxDepth)
```

---

## 2. 接口能力逐项对比

| 能力维度 | AgentScope `AbstractFilesystem` | Nop `IToolFileSystem` | 差异分析 |
|----------|-------------------------------|----------------------|----------|
| **会话感知** | ✅ 每个操作传入 `RuntimeContext`（userId, sessionId 可做隔离） | ❌ 无上下文参数，全局单例 | AgentScope 天然支持多租户；Nop 需在实现层自行处理 |
| **错误处理** | ✅ 统一结果对象（`isSuccess()`/`error()`），从不抛异常 | ❌ 抛出 `IllegalArgumentException`/`NopException` | AgentScope 更适合 LLM 消费（错误信息可直接返回给模型）；Nop 的传统异常风格 |
| **读取模式** | 基于行分页：`offset`(0-based)+`limit` | 两种模式：`readText`(maxChars) + `readLines`(1-based Lines) | 本质等价，但参数风格不同。最大字符限制 Nop 以参数传入，AgentScope 在实现中隐含 |
| **搜索模式** | `grep`：**字面模式**（非正则），可选 glob 过滤 | `grep`：**正则模式**，参数更丰富（ignoreCase/maxMatchesPerFile/maxFiles/maxDepth） | Nop 更灵活（正则 vs 字面是互换的），AgentScope 的字面搜索可能更安全 |
| **文件写入** | `write`：**仅创建**（已存在报错） | `writeText`：支持 `append` 标志 | Nop 更灵活（追加模式）；AgentScope 需用 `edit` 修改已有文件 |
| **文件编辑** | ✅ 专用 `edit`（精确字符串替换 + replaceAll） | ❌ 无内置编辑（通过独立的 `PatchFileExecutor` 做 diff 应用） | AgentScope 的 edit 是 LLM-friendly 的设计；Nop 依赖外部 patch 工具 |
| **删除** | `delete(path)`：目录自动递归 | `delete(path, recursive, force)`：显式参数控制 | Nop 更精细（force 标志覆盖只读） |
| **移动/复制** | 仅 `move` | `move`(overwrite) + `copy`(recursive, overwrite) | Nop 完整支持复制 |
| **Shell 执行** | ✅ 在 `AbstractSandboxFilesystem` 子接口 | ❌ 无（由独立 `BashExecutor` 工具提供） | AgentScope 将 shell 作为 filesystem 的第一公民能力，Nop 分离为独立工具 |
| **二进制上传/下载** | ✅ `uploadFiles`/`downloadFiles`（byte[] 批量） | ❌ 无（纯文本操作） | AgentScope 的代码助手场景需要此能力 |
| **目录创建** | ❌ 无（通过 shell `mkdir` 实现） | ✅ `mkdirs` | Nop 直接支持 |
| **路径安全** | 静态 `validatePath()`（拒绝 `..`） | `isPathAllowed()`（canonical path 前缀匹配） | 两种策略不同：AgentScope 黑名单，Nop 白名单 |
| **行数统计** | ❌ 无 | ✅ `countLines` | Nop 可直接感知文件大小，帮助 LLM 决策读取范围 |
| **返回类型** | 丰富的模型类（`ReadResult`/`WriteResult`/`EditResult`/`GrepResult`/`GlobResult`） | 简单的数据类（`TextResult`/`LineResult`/`FileInfo`/`SearchMatch`） | AgentScope 更结构化（携带成功/失败状态 + 元信息） |

---

## 3. 关键设计差异分析

### 3.1 `RuntimeContext` 的有无——架构级别的分歧

AgentScope 每个文件操作都传递 `RuntimeContext`。这不是偶然——它的 filesystem 是 **session-aware** 的。`RuntimeContext` 携带 `userId`、`sessionId` 以及可注入的 `SandboxContext`，使得：

```java
// AgentScope: 同一个 filesystem 实例，不同 session 看到不同的文件视图
// (通过 IsolationScope + NamespaceFactory 做路径重映射)
backend.read(ctx("userA", "session1"), "/memory/MEMORY.md")
backend.read(ctx("userB", "session2"), "/memory/MEMORY.md") // 不同路径
```

Nop 的 `IToolFileSystem` 没有此参数。`LocalToolFileSystem` 构造时绑定一个 `workDir`，所有工具共享同一个文件视图。多租户隔离完全不存在。

**这是 AgentScope 最显著的亮点，但也增加了复杂性**——每个操作都要传递 `RuntimeContext`，并且所有实现都必须处理它。

### 3.2 错误处理哲学

AgentScope 选择了 **Result Object** 模式——每个操作返回特定结果类型（`ReadResult`/`WriteResult` 等），通过 `isSuccess()`/`error()` 判断成功与否。这直接服务于 LLM 消费场景：LLM 调用的工具出错时，错误消息被包装在结果对象中，可以作为 tool result 返回给 LLM，让它自行决策后续操作。

Nop 使用标准的 Java 异常机制。这意味着工具执行器必须 catch 异常并转换为 `AiToolCallResult`。实践中每个工具执行器都有 try-catch 模板代码。

### 3.3 AgentScope 的 `edit` 操作

AgentScope 的 `edit(RuntimeContext, filePath, oldString, newString, replaceAll)` 是一个面向 LLM 的精确字符串替换操作。这是代码助手场景的典型需求——LLM 通过"找到 X 替换为 Y"来编辑文件，而不是写出全文。Nop 的 `IToolFileSystem` 没有这个能力，而是通过独立的 `PatchFileExecutor`（unified diff）实现类似功能。

### 3.4 Shell 执行的位置

AgentScope 将 shell 执行放在 `AbstractSandboxFilesystem` 子接口中，将其作为"沙箱文件系统"的一个自然扩展——在沙箱里执行 shell 命令和在沙箱里读写文件是同一层抽象。

Nop 将 bash 作为独立的 `IToolExecutor`（工具名 `"bash"`），与 filesystem 完全解耦。这意味着 Nop 的 bash 工具无法直接与文件系统共享路径安全策略——它有自己的 `workingDir` 参数，路径安全检查需要 BashExecutor 自身实现。

---

## 4. 结合 Nop Delta 的深度分析

### 4.1 Delta 是通用多层覆盖抽象，不是"框架扩展工具"

Nop Delta 的本质极其简单：

```
getResource(path):
  for layer in layers (高优先级 → 低优先级):
    if layer.exists(path): return layer.getResource(path)
  return base.getResource(path)
  
saveResource(path, data):
  topLayer.saveResource(path, data)  // 写操作永远走最上层
```

这套机制不限定任何语义——layer 可以是 `_delta/default`（当前框架用法），可以是 `workspace`（代码助手的工作区），可以是 `_tenant/{tid}`（多租户），可以是 `sandbox`（安全隔离）。区别只在于层从哪里来、存到哪里去。

**当前 Nop 平台中有两个文件操作抽象，它们是按职责不同有意分离的，不是"分裂"：**

```
Delta 抽象 (多层路径空间 + 上层覆盖下层)
  │
  ├── VFS 实现: DeltaResourceStore + IResource
  │     ├── 面向: 框架配置加载（getResource/readText）
  │     ├── 能力: 流式读写、目录枚举
  │     ├── 分层: 由 DeltaLayerIds 配置驱动
  │     └── 定位: 通用资源层，抽象存储后端和路径映射
  │
  └── AI 工具实现: IToolFileSystem
        ├── 面向: AI Agent 工具（readLines/glob/grep/writeText/edit）
        ├── 能力: 行级读写、glob、grep、diff apply、edit
        ├── 设计意图: 收窄接口，明确限定 Agent 所需的文件操作能力
        ├── 分层: ❌ 当前 LayeredToolFileSystem 不存在
        └── 现状: LocalToolFileSystem 只有单层 workDir，但内部路径解析可直接复用 VFS
```

两个抽象服务于不同的调用者（框架代码 vs AI Agent），有不同的接口粒度需求。关键在于：**IToolFileSystem 内部的路径解析和分层策略可以复用 VFS，也可以单独实现**——这是实现选择，不是架构问题。

### 4.2 两条抽象路径的接口关系——按职责分离，不是"能力缺口"

```
             平台框架代码（VFS）                 AI Agent 工具
             ─────────────                       ──────────────
读配置/模板:  IResource.readText()              IToolFileSystem.readText()
读行级内容:   IResource readStream → 应用层解析    IToolFileSystem.readLines()
找文件:       IResourceLoader.findAll()          IToolFileSystem.glob()
搜内容:       无（框架不需要）                    IToolFileSystem.grep()
写文件:       IResourceStore.saveResource()      IToolFileSystem.writeText()
编辑文件:     无（框架不需要）                    PatchFileExecutor / 无 edit
分层覆盖:     ✅ DeltaResourceStore              ❌ 无（LayeredToolFileSystem 待实现）
```

**这不是"能力缺口"问题，而是接口责任分离问题。**

`IResource` 不需要 `readLines`、`grep`、`edit`——框架配置加载没有这些需求。`IToolFileSystem` 需要它们——因为 AI Agent 操作的是用户项目的源文件，不是框架配置。

两条抽象服务于不同的需求空间，交集只有"读/写文件内容"这一基本操作。区别在于：

- **读行级内容**：VFS 可以通过 `readStream` + 应用层解析实现，只是没有封装为一次性接口。`IToolFileSystem` 把 `readLines` 作为一等操作暴露给 LLM。
- **glob/grep**：框架从不搜索用户文件，不需要这些能力。
- **分层覆盖**：这是两个抽象都应该具备的，但当前 `IToolFileSystem` 还没有分层实现。**这是唯一的真实差距。**

当前 "AI 工具没有分层覆盖" 的问题不同——DeltaResourceStore 的分层能力是通用的，`IToolFileSystem` 的分层实现完全可以在不依赖 VFS 的情况下用同样的语义自行实现（或者直接复用 `IResource` 做后端路径解析）。这是实现工作，不是架构或接口问题。

### 4.3 最优解：增强 IResource，让 Delta 对 AI 工具可用

```
IResource (增强后)
  ├── readText()                    ← 已有
  ├── readLines(fromLine, toLine)   ← 新增（行级操作）
  ├── countLines()                  ← 新增
  ├── writeText(content, append)    ← 已有（需要确认 append 支持）
  ├── glob(pattern, ...)            ← 新增
  ├── grep(pattern, ...)            ← 新增
  └── children() / walk()           ← 已有
  
DeltaResourceStore
  └── 基于增强后的 IResource 实现所有操作 → 对 AI 工具同样可用
```

这样 `IToolFileSystem` 可以退化为 `DeltaResourceStore` 的一个包装：

```
ToolFileSystemFromVFS implements IToolFileSystem {
    DeltaResourceStore store;

    readLines(path, from, to) {
        return store.getResource(path).readLines(from, to);  // delta 感知
    }

    glob(pattern, dir, ...) {
        // DeltaResourceStore 遍历各层，合并结果
        return store.glob(pattern, dir, ...);
    }

    writeText(path, content, append) {
        store.saveResource(path, content, append);  // 写入最上层
    }
}
```

**优势**：
- Delta 的租户隔离（`/_tenant/{tid}/`）、多层覆盖（`/_delta/{layerId}/`）、`super:` 语义全部对 AI 工具可用
- 不再有两条平行抽象路径
- `IResource` 的增强对整个平台都有利（不只为 AI 工具）

### 4.4 次优解：LayeredToolFileSystem + Delta 思想复用

如果 `IResource` 无法增强（改动大、风险高），则在 `IToolFileSystem` 层面复用 Delta 的思想骨架：

```
LayeredToolFileSystem implements IToolFileSystem {
    List<IToolFileSystem> layers;       // 高优先级在前
    List<String> layerIds;              // 复用 Delta 的层标识

    readText(path, maxChars) {
        for (layer : layers)
            if (layer.exists(path))
                return layer.readText(path, maxChars);
        throw notFound;
    }

    writeText(path, content, append) {
        layers.get(0).writeText(path, content, append);  // 永远写最上层
    }

    edit(path, oldStr, newStr, replaceAll) {
        for (layer : layers)
            if (layer.exists(path))
                return layer.edit(path, oldStr, newStr, replaceAll);
        // copy-on-write: 所有层都不存在，写最上层（创建新文件）
        layers.get(0).writeText(path, content, false);
        return layers.get(0).edit(path, oldStr, newStr, replaceAll);
    }

    glob(pattern, dir, ...) {
        // 跨层搜索，上层优先去重
        Set<String> seen = new HashSet<>();
        List<FileInfo> results = new ArrayList<>();
        for (layer : layers) {
            for (FileInfo fi : layer.glob(pattern, dir, ...)) {
                if (seen.add(fi.path()))
                    results.add(fi);
            }
        }
        return results;
    }

    grep(pattern, dir, ...) {
        // 同上去重策略
    }

    delete(path, recursive, force) {
        for (layer : layers)
            if (layer.exists(path))
                layer.delete(path, recursive, force);  // 只删找到的那层
        // 或删除所有层（取决于语义）
    }
}
```

**写操作永远在最上层**——这就是 Delta 的通用语义。不需要按路径前缀区分目标层（那是 `ProjectAwareOverlay` 的特化写保护策略，不是通用分层模型）。

### 4.5 与 AgentScope 的对应关系

| AgentScope 实现 | 本质 | 对应 Nop Delta 概念 | 当前状态 |
|:---|:---|:---|---|
| `OverlayFilesystem(lower, upper)` | 通用 2 层覆盖，写上层、读上层优先 | `DeltaResourceStore`（2 层版本） | Nop 有 `DeltaResourceStore` 但没有 `IToolFileSystem` 实现 |
| `ProjectAwareOverlay` | Overlay + 写保护特化（部分路径只写项目层） | — | 代码助手场景的写保护策略，可在 `LayeredToolFileSystem` 中通过 `writeFilter` 实现 |
| `CompositeFilesystem` | 按路径前缀分发到不同后端（非覆盖） | `CompositeResourceStore` | Nop 已有 |
| `NamespaceFactory` | 按 `RuntimeContext` 动态确定路径前缀 | `_tenant/{tenantId}` | Nop 已有但 `IToolFileSystem` 未使用 |

### 4.6 对两个抽象的关系总结

这个分析揭示了一个重要的设计选择：

**`IToolFileSystem` 是为了收窄接口、明确限定 Agent 所需的能力而存在的。** 它不是一个补丁——它是一个独立的抽象，服务于不同的调用者和不同的需求空间 (`agentscope-java` uses a similar pattern with `AbstractFilesystem` as a dedicated interface for agent tools). 把它的方法合并到 `IResource` 不是正确的方向——那会让 `IResource` 膨胀，承担它不该承担的责任（行级编辑、搜索等从来不是框架配置加载的需求）。

正确的方向是：
1. **`IToolFileSystem` 内部的路径解析和分层策略可以复用 VFS**——也可以单独实现。Delta 是一个通用思想，不依赖于具体实现。
2. **`IToolFileSystem` 的分层实现（LayeredToolFileSystem）** 是当前真正的缺失——无论是基于 `DeltaResourceStore` 还是基于独立的 `LayeredToolFileSystem + LocalToolFileSystem` 实现。

**Delta 本身不依赖于 VFS 的 `IResource` 接口**——它的核心是层顺序、优先级、读写策略。这些可以在 `LayeredToolFileSystem` 中用完全相同的方式实现，服务于 Agent 场景。当前 Nop 缺少的不是"IResource 增强"，而是"IToolFileSystem 的 Delta 化实现"。

---

## 5. 建议

### P0：将 LokalToolFileSystem 替换为 LayeredToolFileSystem（单层兼容）

当前 `LocalToolFileSystem` 改为以 `LayeredToolFileSystem` 为内部实现，只传入一个层，保持单层行为完全不变：

```java
// 当前（隐式）：
IToolFileSystem fs = new LocalToolFileSystem(workDir);

// 改为（行为完全一致）：
IToolFileSystem fs = new LayeredToolFileSystem(List.of(
    new LayerEntry("work", new LocalToolFileSystem(workDir), true)
));
```

### P1：为代码助手场景定义两层 YAML 配置

```
# nop-ai-agent.yaml
agent:
  fileSystem:
    layers:
      - id: workspace
        baseDir: ${nop.ai.agent.workspace.dir}  # 工作区
        writable: true
      - id: project
        baseDir: ${user.dir}                     # 项目目录
        writable: false                           # 项目层只读
```

`LayeredToolFileSystem` 加载配置后：
- 写操作写 `workspace` 层
- 读操作先查 `workspace` 再查 `project`
- glob/grep 跨两层搜索，去重

这与 AgentScope 的 `OverlayFilesystem` + `ProjectAwareOverlay` 行为完全一致，但通过 Delta 的通用层配置实现，不写死前缀列表。

### P2：增强 IResource 接口，消除两条平行抽象

这是真正的长期优化——如果 `IResource` 增加 `readLines`、`countLines`、`glob`、`grep` 方法，`DeltaResourceStore` 自然获得这些能力，`IToolFileSystem` 可以退化为一个非常薄的适配层，甚至完全由 `DeltaResourceStore` 替代。

