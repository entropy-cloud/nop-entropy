# nop-ai-shell IO 模型与管道设计

**日期**：2026-06-08
**状态**：active

---

## 一、设计结论

1. **采用文本块（chunked text）流模型**，不使用纯行模型，也不使用纯二进制流模型
2. **EOF 通过特殊子类表达**，不使用字符串哨兵（消除 `"__EOF__"` 碰撞风险）
3. **管道阶段通过 `BlockingQueue` 连接**，利用队列的阻塞特性实现自然背压
4. **管道各阶段并发启动**，不建立顺序依赖，数据逐条流过

## 二、背景与动机

### 当前实现的问题

当前 `IShellInput`/`IShellOutput` 是纯行模型（`readLine()`/`println(String)`），存在以下缺陷：

1. **EOF 哨兵碰撞**：`IShellOutput.EOF_MARKER = "__EOF__"` 是普通字符串，命令输出包含该文本时会被截断
2. **管道接线断裂**：`executePipeline` 中 `inputs` 列表存的是引用本身而非前一阶段输出，导致所有阶段读同一个 stdin
3. **管道无流式**：`addDepend` 使每个阶段等待前一阶段完全结束才启动，数据全量缓冲而非逐条流过
4. **`BackgroundExpr` 空操作**：`executeBackground` 直接委托，无后台语义
5. **`FileShellOutput` 截断**：非追加模式下每次 `writeLine` 都带 `TRUNCATE_EXISTING`，只保留最后一行
6. **不支持 `print()` 不换行的场景**：`readLine()` 无法消费 `print()` 产出的不完整行

### 为什么不用纯行模型

纯行模型以 `\n` 为最小传输单位，问题是：
- `print("hello")` 不带换行，下游 `readLine()` 永远阻塞
- 命令需要产出多行结构（JSON）时，行边界不是自然的消费边界

### 为什么不用纯二进制流

- AI shell 的命令几乎全部处理文本，`byte[]` 增加无谓的编码复杂度
- 二进制流无法提供行级便捷 API，每个命令都要自己处理换行

## 三、核心设计

### 3.1 ShellChunk — 数据传输单元

`ShellChunk` 是管道中传输的基本单元，使用类继承（非 record）表达三种类型：

```java
public abstract class ShellChunk {
    private ShellChunk() {}

    public static final class TextChunk extends ShellChunk {
        private final String text;
        public TextChunk(String text) { this.text = text; }
        public String getText() { return text; }
    }

    public static final class BinaryChunk extends ShellChunk {
        private final byte[] data;
        public BinaryChunk(byte[] data) { this.data = data; }
        public byte[] getData() { return data; }
    }

    public static final class EofChunk extends ShellChunk {
        public static final EofChunk INSTANCE = new EofChunk();
        private EofChunk() {}
    }

    public static EofChunk eof() { return EofChunk.INSTANCE; }
    public static TextChunk text(String text) { return new TextChunk(text); }
    public static BinaryChunk binary(byte[] data) { return new BinaryChunk(data); }
}
```

**设计决策**：
- `EofChunk` 是类型安全的 EOF 信号，不存在哨兵碰撞
- `TextChunk` 承载任意文本（可包含或不包含 `\n`），不强制行边界
- `BinaryChunk` 预留二进制扩展点，当前命令无需关心
- 私有构造函数 + final 内部类保证类型密封性（等价于 sealed interface 的效果）

### 3.2 IShellOutput — 输出接口

```java
public interface IShellOutput extends Closeable {
    void write(ShellChunk chunk);

    default void print(String text) {
        write(ShellChunk.text(text));
    }

    default void writeLine(String line) {
        write(ShellChunk.text(line + "\n"));
    }

    default void println(String text) {
        writeLine(text);  // 统一为包含 \n 的单个 TextChunk
    }

    default void println() {
        write(ShellChunk.text("\n"));
    }

    void flush();

    IShellInput asInput();
}
```

**设计决策**：
- 核心方法只有一个 `write(ShellChunk)`，其他全部是 default 便捷方法
- `writeLine` 写入的是包含 `\n` 的 TextChunk，消费端可按 `\n` 拆行
- `println` 委托 `writeLine`，统一为包含 `\n` 的单个 TextChunk
- 保持 `asInput()` 用于管道接线

### 3.3 IShellInput — 输入接口

```java
public interface IShellInput extends Closeable {
    ShellChunk read();

    default String readLine();

    default String readAllText();

    Iterator<ShellChunk> chunks();

    Iterator<String> lines();

    void close();

    boolean isClosed();
}
```

**设计决策**：
- 核心方法 `read()` 返回 `ShellChunk`，读到 `EofChunk` 时返回 null
- `readLine()` 是便捷方法，内部收集 chunk 直到遇到 `\n` 或 EOF
- `lines()` 是行模式迭代器，等价于当前实现的逐行读取
- `chunks()` 是 chunk 模式迭代器，直接透传底层 chunk

### 3.4 readLine() 的语义

`readLine()` 的行为规则：
1. 从内部缓冲区或后续 chunk 中收集文本
2. 遇到 `\n` 时返回换行符之前的文本（不含 `\n`）
3. 遇到 `EofChunk` 时：如果缓冲区有内容，返回剩余内容并标记 `eofSeen`；如果缓冲区为空，直接标记 `eofSeen` 并返回 null
4. `eofSeen == true` 后，所有后续调用直接返回 null

这保证了：
- `print("hello")` 不带换行也能通过 `readLine()` 消费（返回 `"hello"`）
- `println("hello")` 产出的两个 chunk（`"hello"` + `"\n"`）被正确合并为一行

### 3.5 BlockingQueueShellOutput — 管道连接器

管道阶段间的核心连接器，基于 `LinkedBlockingQueue<ShellChunk>`：

**写入端**（生产命令所在线程）：
```java
public void write(ShellChunk chunk) {
    if (closed) throw new IllegalStateException("output closed");
    queue.put(chunk);  // 阻塞，自然背压
}

public void close() {
    closed = true;
    queue.offer(ShellChunk.eof());  // 非阻塞发送 EOF
}
```

**读取端**（消费命令所在线程，通过 `asInput()`）：
```java
private volatile boolean eofReceived = false;

public ShellChunk read() {
    if (eofReceived) return null;  // 防止 EOF 后再次 take() 永远阻塞

    ShellChunk chunk = queue.take();  // 阻塞等待
    if (chunk instanceof ShellChunk.EofChunk) {
        eofReceived = true;
        return null;  // EOF
    }
    return chunk;
}
```

**背压机制**：`BlockingQueueShellOutput` 默认使用有界队列（容量 1024）。生产者 `put()` 满时阻塞，消费者 `take()` 空时阻塞。队列容量可通过构造参数配置。无界队列（`LinkedBlockingQueue()`）不可用于管道——它没有背压，大数据量会 OOM。

### 3.6 管道接线：正确的执行流程

```
executePipeline(pipeline, context, cancelToken):

  IShellInput currentInput = context.stdin();
  BlockingQueueShellOutput prevOutput = null;

  for (i = 0; i < commands.size(); i++) {
      BlockingQueueShellOutput output = new BlockingQueueShellOutput();

      // 关键：阶段 i 读前一阶段的输出
      IShellInput cmdInput = (i == 0) ? currentInput : prevOutput.asInput();

      // 关键：不建立顺序依赖，直接提交到线程池
      CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
          try {
              return executeSimpleCommandWithContext(cmd, cmdInput, output, ...);
          } finally {
              output.close();  // 发送 EOF 给下游
          }
      }, executor);

      prevOutput = output;
      lastFuture = future;
  }

  // 等最后一个阶段完成，收集输出
  return lastFuture.thenApply(exitCode -> {
      String stdout = collectTextFromOutput(prevOutput);
      // collectTextFromOutput: 从 IShellInput 读取所有 TextChunk 拼接为 String
      return new ExecutionResult(exitCode, stdout, "");
  });
```

**与当前实现的关键差异**：

| 方面 | 当前（错误） | 目标（正确） |
|------|-------------|-------------|
| 阶段输入 | `inputs.get(i-1)` = 同一个 stdin | `prevOutput.asInput()` = 前一阶段输出 |
| 启动方式 | `addDepend` 顺序等待 | `supplyAsync` 并发启动 |
| EOF 发送 | 无 | `finally { output.close() }` |
| 数据模型 | 行（`String`） | chunk（`ShellChunk`） |

### 3.7 命令分类与执行模式

| 命令类型 | 代表 | stdin 消费 | stdout 产出 | 特殊处理 |
|----------|------|-----------|------------|----------|
| 状态修改 | `cd`、`export` | 不读 | 不写 | 同步执行，直接修改 executor 的 `currentWorkingDir`/`exportedEnv` |
| 快速输出 | `echo`、`pwd`、`env` | 不读 | 写后立即返回 | 同步执行 |
| 流式过滤 | `grep`、`sort`、`wc`、`cat` | 逐行/逐 chunk 消费 | 逐条产出 | 流式执行，stdin 未 EOF 时不退出 |
| AI 工具 | 外部调用的命令 | 可能读 | 可能写 | 异步执行 |

**不需要对 `cd` 等做特殊调度**：它们在管道中自然表现为"读空 stdin、写空 stdout"，语义与 Bash 一致。`cd` 的副作用（修改工作目录）由 executor 在 `updateContextFromResult` 中处理。

## 四、拒绝了什么

### 4.1 纯二进制流（`InputStream`/`OutputStream`）

**拒绝理由**：
- 所有内置命令都是文本处理，`byte[]` API 增加每个命令的编码负担
- `InputStream.read()` 的字节级粒度对文本命令无用
- 失去行级便捷 API（`readLine()`/`println()`），每个命令都要自己处理换行

### 4.2 纯行模型（当前实现）

**拒绝理由**：
- `print()` 产出的不完整行导致 `readLine()` 阻塞
- EOF 哨兵字符串 `"__EOF__"` 与数据碰撞
- 无法支持未来可能的二进制数据传递

### 4.3 基于 Reactive Streams（Publisher/Subscriber）的模型

**拒绝理由**：
- 引入额外的响应式框架依赖（Reactor/RxJava）
- 对 AI shell 的简单管道场景过度设计
- `BlockingQueue` 已提供足够的背压和流控

### 4.4 基于 PipedInputStream/PipedOutputStream

**拒绝理由**：
- `PipedOutputStream` 关闭后无法重新打开，不适合多消费者场景
- 管道缓冲区固定（默认 1024），不可配置
- 跨线程检测机制（"pipe broken"）增加错误处理复杂度
- 与 shell 的 chunk 模型不匹配，需要额外适配层

## 五、与已有设计的关系

- AST 模型设计：`04-bash-syntax.md`
- 执行器与异步：`ai-dev/design/nop-ai-shell/03-executor-and-async.md`
- `TaskExecutionGraph`：`nop-core` 提供的通用 DAG 执行框架
