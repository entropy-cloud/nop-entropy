# C8 深挖第 2 轮 — nop-ai-toolkit-tools-shell（WI 3.2）

- 审计日期：2026-10-20（深挖第 2 轮，合并批 C1/C4/C6/C8）
- 定位：第 1 轮 C8 共 10 条（C8-001…C8-010）集中于 shell 队列/输出捕获；本轮按派发单指令在">1024 挂死已立案"之外寻找同族生产消费边界并核对缓冲上界，编号续接 C8-011。

## 深挖范围与方法

| # | 指定深挖面 | 核对方法与结论（file:line 为 live code） |
|---|---|---|
| 1 | mavenvfs 下载/解压缓冲 | 逐文件核实 `mavenvfs/**`：`VfsMavenCli.execute` 为 stub（VfsMavenCli.java:129-142，不执行进程）；全包无网络下载、无解压、无缓冲聚集代码——**该边界不存在**，无发现。 |
| 2 | Jq/Graphql 响应缓冲 | `JqToolExecutor.doExecute`：输入为调用参数 `data`（:58-61，受会话上下文体量约束）、输出经 jq 引擎 output-limit 检查点 + expireAt deadline（类 javadoc :21-25 与 :63-70，声明有界）；`GraphqlQueryExecutor`/`HttpRequestExecutor` 的 `getBodyAsString()` 全量物化已由第 1 轮 C8-007 立案，本轮复核无新增兄弟（SearchEngineExecutor 经 `request.setLimit` 下推，:69，已有界）。 |
| 3 | 沙箱 capturedRef 缓冲上界 | `HostBashSandbox.drainStream:144-181` / `DockerBashSandbox.drainStream:277-314`：`maxBytes - captured.length()` 到顶即 `truncated=true` 并**继续排空不阻塞子进程**（:157-159,:293-295），1MiB 默认上界经 `BashSandboxConfig` 构造期校验（BashSandboxConfig.java:62-64，不可变 final 字段）——对照声明**合格**（维持第 1 轮边界清单 B7 判定）；发布节奏拷贝（每 50ms 一份副本）的分配浪费归 CL-019，不重复。 |
| 4 | 后台 job 输出积压 | shell `&`（executeBackground:350-370）：job body 内每条命令经 `executeSimpleCommand` 使用**自建的局部队列**（:221-222）并在 body 内 close+collect 完成收集，ExecutionResult 经 `IBackgroundJobSink` 契约通道交付（javadoc :27-29"delivered through the sink's own completion channel"）——模块内无第二积压面；toolkit 后台 bash（BashExecutor.doExecuteBackground:216-244）输出为沙箱 1MiB 捕获的合成结果交 `registry.complete`，有界。无新发现。 |
| 5 | 同族生产消费边界补查（>1024 挂死族外延） | 对 `io/*` 全部 11 个类 + `executeSimpleCommand/applyRedirects/handleFd*Redirect` 逐拓扑核对，发现两个新边界：**C8-011**（FD_INPUT `0<&1` 自引用消费拓扑）与 **C8-012**（chunk 计数有界、字节无界——readLine/readAllText 聚合无帽）。全部 io 类（TeeOutput/DuplexShellOutput/PrintStreamShellOutput/ListShellOutput/ListShellInput/FileShellInput/FileShellOutput/BlockingQueue 双件/AbstractShellInput/ShellChunk）逐一过账，状态见两发现失效面。 |

---

## 新增发现

### [C8-011] `0<&1` FD_INPUT 自引用拓扑：命令消费自己的 stdout 队列，EOF 永不到达——读 stdin 的命令确定性永久挂死

- **文件**: 接线点 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:519-530`（handleFdInputRedirect）；挂死机制 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:58-93`（adapter.take）+ `ShellCommandExecutor.java:221-231`（EOF 仅在命令返回后 close 投递）
- **证据片段**:
  ```java
  // ShellCommandExecutor.java:526-528 —— stdin 接到自己的 stdout 队列
  if (sourceFd == 0 && targetFd == 1) {
      streams.stdin = streams.stdout.asInput();   // 自引用：读端=写端
  }
  // BlockingQueueShellOutput$Adapter.read():70-76 —— 无 EOF 则 take() 永久阻塞
  ShellChunk chunk = queue.take();
  ```
- **严重程度**: P2
- **并发场景**: `executeSimpleCommand`（单命令）或 pipeline 阶段：命令在读 stdin 的同时其 stdout 是 1024 容量 `BlockingQueueShellOutput`；EOF 由外层在**命令返回之后**才 `close()` 投递（L227-228 / pipeline 阶段 finally L268-270）——命令若先读 stdin，读到的只能是自己写入的 chunk，EOF 结构性不可达。语法可达性：`BashLexer.java:121-122` 产出 REDIRECT_FD_INPUT、`BashSyntaxParser.java:301-302` 解析 `<&`、`Redirect.java:120` 构造 `new Redirect(sourceFd, FD_INPUT, target)`，`0<&1` 为一等解析入口（对照真实 bash 中 `0<&1` 同为合法形式）。
- **触发时序**: T1 调用线程执行 `cat 0<&1`（或任何先读后写的注册命令/接通后的外部命令）：`applyRedirects` 使 stdin = 自身 stdout 队列的 adapter → 命令进入 `stdin().read()` → `queue.take()` 阻塞（队列为空，且只会被它自己的写填充）→ cat 写不产出、EOF 在 L227 而它永不返回 → 调用线程永久挂死；`cancelToken` 在 `executeSimpleCommandWithContext` 全程无检查点，无超时兜底。pipeline 变体 `a 0<&1 | b`：阶段 a 自食其尾，阶段 b 因 EOF 永不到来阻塞在 `take()`——双线程连锁挂死（与 C8-003 的泄漏形态叠加）。
- **现状**: FD_INPUT 仅处理 `0←1` 一种组合（其余组合静默忽略）；自引用拓扑无任何检测或拒绝（F10：未声明的危险组合无守卫）。内建 cd/echo/ls 不读 stdin，故默认装配不触发——与 C8-001 同一前提条件（命令集是开放扩展点，外部命令适配器接通后升级为普通负载）。
- **风险**: 与 C8-001 同后果级：worker/调用线程永久挂死且不可取消；因触发语法罕见（`0<&1` 重定向形态），判 P2 而非 P1；一旦触发无任何日志可定位（线程停在 `take()`）。
- **建议**: (1) `handleFdInputRedirect` 对 `0<&1` 显式拒绝（stderr 提示 + 退出码 1，fail-loud）或改为空输入（`/dev/null` 语义）；(2) 顺手补齐其余 FD_INPUT/FD_OUTPUT 组合的显式拒绝，消除静默忽略；(3) 与 C8-001 的取消检查点修复共用：命令执行循环或 write/take 路径接入 cancelToken。
- **失效面枚举**（F3）：FD 重定向处理全清单——`handleFdInputRedirect:519-530`（`0<&1` 自引用，本条；其余组合静默忽略，同修）；`handleFdOutputRedirect:497-517`（`1>&2`/`2>&1` 均指向他方队列/输出，无自引用面，通过）；`handleMergeRedirect:532-543`（单实例双写，M6-P1 已修复注释在案，通过）；`handleOutputRedirect/handleInputRedirect:485-495`（File 双向，无队列拓扑，通过——输入侧全量载入归 CL-010）。
- **信心水平**: 确定（拓扑接线逐行可核；`cat` 类读 stdin 命令存在时为确定性触发，非竞态）
- **误报排除**: 非"bash 同语法也如此"——真实 bash 的 `cat 0<&1` 经 fd 复制读终端/上游，无队列拓扑，不会自锁；非"有界队列即合规"——1024 上限与自引用消费组合正是第 1 轮校准条款的矛盾项；前提条件（需读 stdin 的命令）已如实标注并映射到 C8-001 的同批修复。
- **复核状态**: 未复核
- 复核状态: 保留(P2，条件化标注：stdin 读命令/外部适配器接通即回 P1)｜新发现——回码核验成立（handleFdInputRedirect L526-528 `streams.stdin = streams.stdout.asInput()` 自引用、EOF 结构性不可达：L227 close 在命令返回后才投递）；语法链核实：BashLexer（model/BashLexer.java:121-122，条目未写全路径、行号正确）→ BashSyntaxParser:301-302/551-552 → Redirect.java:46/120/171 正则——`0<&1` 为一等解析入口；内建命令无 stdin 读点核实（commands/impl 三类 grep 无 read/stdin 调用）——当前装配不可触发，P2 底判级成立并附回 P1 条件；挂死族成员随 C6-004 族案（同 shell 零生产接线条件面）；FD 组合静默忽略面（L519-529 其余组合）同修范围确认

### [C8-012] 输出边界仅按 chunk 计数有界、字节无界：readLine/readAllText 聚合缓冲无帽——1024 chunk 队列的"有界"可被单 chunk 任意放大

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/AbstractShellInput.java:19, 24-62, 65-77`；单巨 chunk 源 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/FileShellInput.java:24-30`；有帽对照模板 `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:404`（`sr.readLine(65536)`）
- **证据片段**:
  ```java
  // AbstractShellInput.java:27-57 —— readLine 聚合到 \n，无行长上界
  while (true) {
      int nlIndex = buffer.indexOf("\n");
      if (nlIndex >= 0) { ... return line; }
      ShellChunk chunk = read();
      ...
      buffer.append(chunk.asText());      // 无 cap 累积
  }
  // FileShellInput.java:26-29 —— 一次 read 返回全部剩余内容为"一个 chunk"
  String remaining = content.substring(position);
  position = content.length();
  return ShellChunk.text(remaining);
  ```
- **严重程度**: P2
- **并发场景**: 消费边界（B1/B2 的消费者侧：`collectOutput`→`readAllText`，及任何行式消费命令的 `readLine`）与生产边界之间只有 **chunk 计数**维度（1024），`write(ShellChunk)` 不做拆分/限长——chunk 的字节尺寸无界；聚合缓冲（`buffer`/`sb`）在消费侧再次无帽累积。
- **触发时序**: 路径 A（单巨 chunk）：T1 `cmd < huge.no-newline` → `handleInputRedirect:491-495` 建 `FileShellInput` → 构造器全量载入（CL-010 立案）且 `read()` 把整个文件作为**一个 chunk** 吐出 → 聚合侧 `readAllText`/`readLine` 的 StringBuilder 承载全文件（峰值 ≥2×：chunk 文本 + 聚合拷贝）。路径 B（洪峰流）：外部命令适配器接通后，生产者持续 `write(ShellChunk.text(noNewlineLine))` → 队列计数有界但消费端 `readLine` 在无 `\n` 时无限聚合（字节数只受生产速率约束）→ 队列 1024 上限从未成为有效护栏。同族：`readAllText`（:65-77）对 collectOutput 的 stdout 全量拼接——第 1 轮 C8-001/002 修复"收集与生产并行化"后，该聚合面成为新的无界驻留点。
- **现状**: 平台 `SafeLineReader` 已提供带帽行读（本模块 `LocalToolFileSystem.grepInFile:404` 即用 `readLine(65536)`），shell 输入侧未复用（F3 一致性缺口）；第 1 轮边界清单 B1/B2 的"1024 chunk ≈ 1024 行"的有界性表述需按本条修正为"计数有界、字节无界"。
- **风险**: 大输入/大输出场景的堆驻留无上界（聚合缓冲 + chunk 双重放大）；与 C8-001 修复（生产消费并行化）叠加后，挂死被消除而**内存放大面留存**，过载行为从"挂死"变为"缓慢 OOM"，更难归因。
- **建议**: (1) `BlockingQueueShellOutput.write` 入口按 `maxChunkChars`（如 64K）拆分或拒绝超长 chunk；(2) `AbstractShellInput.readLine` 复用 `SafeLineReader` 的行长帽语义（超长行截断 + truncated 标记，对齐 `Line.isTruncated` 契约）；(3) `collectOutput`/`readAllText` 增加总字节帽并回写 truncated 标志（对齐沙箱 1MiB 截断-续排空模式）。
- **失效面枚举**（F3）：聚合/无帽消费面全清单——`AbstractShellInput.readLine:24-62`（**本条本体**）、`readAllText:65-77`（同根，collectOutput 依赖它）、`FileShellInput.read:24-30`（单巨 chunk 源，CL-010 已立案构造期载入，本条补记其 chunk 形态放大面）；`ListShellInput`（chunk 由调用方给，聚合面同 ①）；`BlockingQueueShellInput.read`（透传单 chunk，聚合归 ①）；`PrintStreamShellOutput.write`（流式直写无聚合，**通过**）；`LocalToolFileSystem` 全部读路径（SafeLineReader 帽在位，**通过**——修复模板）。
- **信心水平**: 确定（聚合路径逐行可核；路径 A 单调用即可复现，无需竞态）
- **误报排除**: 不与 CL-010 重复——CL-010 主张构造期全量载入（C5 无界读），本条主张**消费边界聚合无帽 + 计数式有界的失效形态**（C8 过载降级缺口），二者同源不同面、修复动作不同（惰性/有界读 vs 拆分/截断协议）；非"已声明边界"——1024 容量未附任何字节维度声明。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立（AbstractShellInput.readLine L24-62/readAllText L65-77 聚合无帽；FileShellInput.read L24-30 单巨 chunk（substring 全量剩余）；BlockingQueueShellOutput.write L30 put 不拆分——队列 1024 计数有界、字节无界）；有帽对照模板核实（LocalToolFileSystem.grepInFile L404 `sr.readLine(65536)`，SafeLineReader 平台设施在位未复用——F3 一致性缺口成立）；路径 A（`<` 重定向）与路径 B（洪峰流）的可达性区分准确（路径 B 依赖外部适配器接通）；与 CL-010（FileShellInput 构造期全量载入，C5 面）同源不同面不重复立项——与 C1 复核对 CL-010 的处置一致；shell 零生产接线条件面同 C8-001 复核行登记（已为 P2 底，不另降）

---

## 维度集外线索（overlay）

（本轮 C8 深挖未产生新的 C2/C3/C5/C7/C9 集外线索：mavenvfs 下载/解压面不存在、capturedRef 对照声明合格、后台 job 输出在模块内无积压面。CL-020 由 C6-round2 登记，本轮不追加。）

---

## 继续判定

本轮新增 2 条（P2×2：C8-011、C8-012），均属第 1 轮">1024 挂死"同族的生产消费边界外延；同族边界经 B1–B10 + 本轮 io 全类过账后**站点清单穷举完毕**（挂死族：C8-001/002/003 + C8-011；无界族：C8-007/008/010 + C8-012），无新增 P0/P1，且各深挖面均有明确收敛证据——输出 **终止信号**（C8 维度建议收敛，不再开第 3 轮）。

---

## 复核结论（round2 条目）

- 复核人：C8 维度独立复核员（WI 3.2，2026-10-20）；C8-011/C8-012 逐条回码验证（handleFdInputRedirect/BlockingQueueShellOutput.write/AbstractShellInput/FileShellInput/LocalToolFileSystem/BashLexer/Redirect/commands impl 逐行核对），判定与状态行见上文两条目；全维度判定表、跨维指针意见与最终保留项见 `C8-backpressure.md`「维度复核结论」。
- **C8-011：保留 P2（条件化标注：stdin 读命令/外部适配器接通即回 P1）**——拓扑接线与语法链核实成立；一处证据路径勘误：BashLexer.java 实际位于 `model/` 包（条目行号正确、未写全路径）；内建命令无 stdin 读点核实成立，当前装配不可触发，随 C6-004 挂死族条件化口径。
- **C8-012：保留 P2**——聚合无帽 + 单巨 chunk + 计数有界/字节无界三层核验成立；与 CL-010 同源不同面不重复立项（与 C1 复核处置一致）；SafeLineReader 有帽模板在位未复用（F3 一致性缺口）确认。
- round2 收敛建议（站点清单穷举、不开第 3 轮）经复核认可：io 全类过账与 mavenvfs/沙箱捕获/后台 job 三深挖面的收敛证据均回码无误。
