# WI 3.2 工具输出/路径束（R4-23，plan 2557）设计裁定

> Status: active
> Last Reviewed: 2026-11-11
> Source: `ai-dev/audits/2026-10/2026-10-20-0829-deep-audit-nop-ai-toolkit-tools-shell-concurrency/`（10/10 原样 fresh-session 取证 @ 2026-11-11；plan 2501 锚点漂移已核）

## 一、设计结论（十条裁定 A-J）

1. **裁定 A（MA-07 pathQueues 生命周期）**：`ToolManagerImpl.pathQueues`（:37-38，唯一写点 compute :157-170）补驱逐——尾完成且无新等待者时 `remove(pathKey, tail)` 两参条件移除（防 ABA：若 compute 已换新尾则不移除）；`QueueOverflowPolicy.ERROR` 语义护栏（R1-m5 勘正——枚举无 FAIL 常量；同 path 积压超上界显式错误，上界可配缺省 64，per-path pending 计数 CHM<String,AtomicInteger> 随驱逐同域增减）+ `pathQueueSize()` 指标。与 C1-004 清理钩子面同批核对（裁定 B）。

2. **裁定 B（C1-004 todoLists 复合原子化；R1-Major-1 修订——F4 清理面移出）**：`UpdateTodosExecutor.todoLists`（static CHM :25）clear/write 收敛 `compute(listKey,…)` 单次原子决策（getOrDefault→put 丢单更新面消除）。**会话条目清理面移出本批**：toolkit 内无 session-lifecycle seam（grep 零命中，cross-module agent 侧 seam 属 7.1 域）——Deferred 登记 successor。护栏 C10-017 并发用例。

3. **裁定 C（C1-005 原子写模板复用）**：四处写点照抄 `ThoughtStorage.atomicWriteText` 模式（temp + ATOMIC_MOVE，降级 REPLACE_EXISTING）——`DeltaVirtualFileSystem.newOutputStream`（:164-167 TRUNCATE_EXISTING）/`copyToVirtual`（:190-191 REPLACE_EXISTING）；扩展面 `DslToolImpl.saveDslFile:81`/`AiFileTool` 保存路径（ResourceHelper.writeText 就地写改原子写）；**DslToolImpl virtual-resource 分支（VFS 资源非本地文件）保持原样——原子模板仅适用 FileResource 分支**（R1-m7）。mavenvfs 核心子面休眠条件化不降档。护栏 C10-018（失败保旧+并发读写，模板测试同源）。

4. **裁定 D（C4-007 submit→run 回滚）**：`BashExecutor`（:209 submit → :220 run）run 抛 UOE（IBackgroundTaskRegistry :42 default 契约）时 try/catch → `complete(taskId, STATUS_FAILED)` 回滚（RUNNING 永久滞留面消除）；不前置 supportsRun 守卫（保持最小 diff，回滚覆盖）。护栏 C10-013（StubRegistry UOE 变体）。

5. **裁定 E（C6-007 ThoughtStorage 生命周期）**：`clearHistory`（:139-146）改删文件（`Files.deleteIfExists`，更新既有「清内容不删文件」断言为删文件语义——C10-021）；TTL 清扫**宿主=saveSession 入口**（R1-m8：once-per-date 节流确定性触发点，可配 `nop.ai.tools.thought-retention-days` 缺省 0=off）；headerless 放大面留 7.1 注记。

6. **裁定 F（C6-009 cancel 条目标记+延迟清理）**：`BashExecutionTracker.cancelExecution`（:84-101）按裁定表方案——**cancel 条目标记 + 延迟清理，不改 remove 顺序外部契约**（协调方约束）：销毁段（:89-90 descendants/destroyForcibly）加 killTree 同款 UOE/SecurityException 守卫（模板 HostBashSandbox.killTree）；销毁失败→注册项保留（`inFlight.putIfAbsent(callId, execution)` 回填 + cancel-pending 标记）+ **rethrow**（boolean 外部契约保持——dispatcher :131 已处理 throw 路径，对齐 cancelAction rethrow 语义；二次 cancel 可重试，失败≠「无在途」由异常表达）；2501 safeClose 补偿在守卫降级路径下仍执行。宽口径兜底留 7.1。**回归面补 nop-ai-agent**（TestE2EChainCancelAbort_2519/TestAgentToolDispatcherKillPropagation 等钉 cancelExecution 行为）。护栏 C10-022。

7. **裁定 G（C8-007 响应体上界）**：`HttpRequestExecutor`（:246 getBodyAsString）与 `GraphqlQueryExecutor`（:156/:158 错误+成功双路径）截断-续排空模式 + `@InjectValue` 配置化（缺省对齐 read-file 100K 帽族：`nop.ai.tool.http-max-response-chars` 缺省 100_000；graphql 同键）——超帽截断 + truncated 标记。护栏 C10-009。

8. **裁定 H（C8-008 七失效面服务端 clamp）**：①SearchContentExecutor maxMatchesPerFile/maxFiles clamp（命中 ≤1万）②SearchFilesExecutor maxResults clamp ③ReadFileExecutor 行区间总量帽（≤100K 行）④PatchFileExecutor `Integer.MAX_VALUE` → 复用 ApplyDelta 10M 帽 ⑥FileToolBizModel.readFiles `<=0` 兜底缺省（limit-after-load → 前置 clamp）；⑤⑦已正确不动；**ApplyDelta 既有帽补回归测试同批**。护栏 C10-010。

9. **裁定 I（C8-010 重定向分块落盘；R1-Major-3 修订）**：`FileShellOutput`（:29 无界 buffer/:34-40 整体 flush）改阈值分块落盘——**append 模式陷阱显式处理**：原 append=false 时首次 flush 以 append=false（截断语义保持）写、后续 flush 切 append=true；字节帽 2MiB 缺省（对齐 bash 先例）**超帽 = 命令失败**（显式错误 fail-loud，非静默截断）；与 C8-012 同批；CL-020 同批核对不并入。

10. **裁定 J（C8-012 shell IO 字节帽；R1-Major-4 修订——可观测契约显式化）**：`BlockingQueueShellOutput.write` 入口 maxChunkChars 拆分；`AbstractShellInput.readLine` 复用 SafeLineReader **帽语义**（SafeLineReader 为 Reader 基，仅语义复用）：超帽行**跨后续 readLine 分片返回**（分片可拼接复原+每分片有界——可观测契约，不改 IShellInput 签名）；`readAllText` 总帽超限**抛 NopException fail-loud**（rule 24）；FileShellInput 单巨 chunk 拆分。C10-008 一张保护面盖 I/J 两单；CL-010 同批核对不并入。

## 二、载体表

| 工单 | 载体 | 模块 | 类型 |
|---|---|---|---|
| MA-07 | `TestToolPathQueue` 扩展（N path 驱逐→map 回基线 + 超界显式错误[ERROR 语义]护栏 + 串行化不被驱逐误伤） | nop-ai-toolkit/manager | 行为钉 |
| C1-004 | `TestUpdateTodosAtomicity`（并发 clear+write 终态一致） | nop-ai-toolkit/tools | 行为钉 |
| C1-005 | `TestToolVfsAtomicWrite`（失败保旧 + 并发读写；mavenvfs 两站点 + 扩展面两站点） | nop-ai-toolkit/mavenvfs + nop-ai-tools/file | 行为钉 |
| C4-007 | `TestBashExecutorRunRollback`（UOE → FAILED 终态非 RUNNING 残留） | nop-ai-toolkit/tools | 行为钉 |
| C6-007 | `TestThoughtStorageRetention`（clearHistory 删文件回基线 + TTL 节流） | nop-ai-tools/sequential_thinking | 行为钉 |
| C6-009 | `TestBashCancelRetriable`（destroy UOE → 注册项保留 + 二次 cancel 可重试 + 失败≠无在途） | nop-ai-toolkit/tools | 行为钉 |
| C8-007 | `TestHttpGraphqlResponseCap`（http + graphql 双路径 ≥帽×2 body → 截断+标记） | nop-ai-toolkit/tools | 行为钉 |
| C8-008 | `TestFileToolOutputClamps`（7 失效面边界值 + ApplyDelta cap 回归） | nop-ai-toolkit/tools + fs + nop-ai-tools/file | 行为钉 |
| C8-010/012 | `TestShellIoBounds`（重定向 2MB 分块落盘 + 字节帽 + readLine 超帽分片可观测 + readAllText 总帽） | nop-ai-shell/io | 行为钉 |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-tools,nop-ai/nop-ai-shell -am`（纯 in-JVM，无 docker）。
- 硬接缝：① ToolManagerImpl（2501 锚点漂移，以 :37-38/:157-170 为准）；② BashExecutionTracker 守卫须包住 destroy 段且 2501 safeClose 补偿共存；③ nop-ai-shell io 包 C8-010/012 同批协调（CL-010/CL-020 同批核对不并入）；④ C1-005 跨 toolkit/tools 两模块（模板同源 TestLocalToolFileSystemAtomicWrite）。
