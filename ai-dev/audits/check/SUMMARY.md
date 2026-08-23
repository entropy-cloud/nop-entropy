# nop-entropy 全模块实现代码检查汇总（check 系列）

- 检查周期: 2026-08-19 ~ 2026-08-21
- 覆盖范围: 根 pom.xml 全部 41 个模块组（nop-kernel / nop-core-framework / nop-persistence / nop-service-framework / 业务骨架 / 可复用业务 / nop-ai / nop-stream / nop-code / nop-graph / 集成外围 / 工具与示例），共 54 个检查单元、54 份独立报告
- 执行方式: 每单元一个独立子代理，按 8 维度（正确性/资源/并发/错误处理/安全/性能/平台规范/契约）单轮扫描 + 逐条源码验证；不读历史审计记录，只审 live code；未修改任何产品代码
- 本文件角色: 全量发现的总索引与跨模块模式聚类。逐条证据见各单元报告（同目录），本文件不复制证据

## 总计（按各报告统计表 awk 汇总）

| Phase | 范围 | P0 | P1 | P2 | P3 | 小计 |
|-------|------|----|----|----|----|------|
| 1 | 框架主干（kernel/core-framework/persistence/service-framework） | 7 | 58 | 107 | 98 | 270 |
| 2 | 业务骨架（auth/sys/job/task/wf） | 6 | 18 | 27 | 31 | 82 |
| 3 | 可复用业务（report/rule/batch/dyn/file/retry/tcc/metadata/format 系） | 6 | 42 | 66 | 65 | 179 |
| 4 | 大型子系统（ai/stream/code/graph/datav/search） | 10 | 49 | 70 | 66 | 195 |
| 5 | 集成外围（network/integration/message/cluster/credential/runner/spring/quarkus） | 5 | 26 | 46 | 37 | 114 |
| 6 | 工具与示例（autotest/utils/dev-tools/frontend/benchmark/demo/migration） | 2 | 16 | 39 | 33 | 90 |
| **合计** | | **36** | **209** | **355** | **330** | **930** |

## 全部 36 条 P0（按模块）

### 安全类（9 条——建议最高优先级处理）

| # | 模块 | 问题 | 影响 |
|---|------|------|------|
| 1 | nop-auth | `max-login-fail-count` 配为 0/负数时整个凭证校验块（密码/SMS/账号状态）被 `if (maxFailCount > 0)` 一并跳过 | 已知用户名+任意密码完全认证绕过 |
| 2 | nop-wf | `transferActorsAsync` 批量转办公开 mutation 全链路无鉴权 | 任意登录用户可改派他人审批任务并审批通过 |
| 3 | ai-rest | 扫码登录 `loginByScanAsync` 为 publicAccess 端点，身份完全取自调用方可控 payload（无渠道签名、ticket 不绑定身份） | 任意用户账号接管 |
| 4 | gateway-bizauth | `gateway-defaults.beans.xml` 以 `ioc:default="true"` 硬编码公开测试 API Key（sk-test-key-1/2） | 未覆盖配置的应用等于发布源码可见的有效凭证 |
| 5 | ai-toolkit-skills | ThoughtStorage 把客户端可控的 `nop-chat-session-Id` header 直接拼 `new File(storageDir, sessionId + ".json")`，无清洗 | 已认证聊天用户可任意读写服务器 .json 文件 |
| 6 | nop-search | `SearchEngine__indexDir` 接受服务器任意绝对路径建索引，`checkAllowAccess` 空实现 | 远程可达的任意本地文件读取面 |
| 7 | format-office | sharedStrings 解析按不可信 `uniqueCount` 属性预分配 ArrayList | 几十字节恶意 xlsx 触发 OOM（文件体积限制拦不住） |
| 8 | nop-integration | 腾讯短信 `areaCode` 未设置必现 NPE（平台主链路 `sendSms` 从不设置） | 部署腾讯短信后每次验证码发送崩溃 |
| 9 | nop-integration | JavaEmailSender 把所有发送/连接异常吞成日志，`sendEmail` 永不抛错，MFA 流程无条件标记 emailSent(true) | SMTP 故障时用户收不到验证码但显示已发送（静默功能性失败） |

### 数据正确性类（16 条）

| # | 模块 | 问题 | 影响 |
|---|------|------|------|
| 10 | nop-api-core | `FutureHelper.thenRun` 异步分支仅在 err!=null 时执行回调 | 异步 biz action 成功后缓存永不失效（CacheEvictActionDecorator 脏读） |
| 11 | nop-core | `BeanCopier._copyToArray` 数组拷贝长度硬编码 0 | 同类型数组浅拷贝静默丢失全部元素 |
| 12 | kernel-small | `BaseRecordInput.next()` 先自增再取，off-by-one | 首条记录读不到、完整迭代末次越界；污染 nop-dao 查询缓存路径 |
| 13 | nop-orm | `findPageAndReturnCursor` 无条件 `list.remove(size-1)` | 游标分页每页丢一条；空列表 remove(-1) 抛异常；findPrev 分支 hasPrev 恒 false |
| 14 | db-migration | XML 迁移文件解析后 `type` 恒为 null，`executeChange` 静默 return | 全部 DDL 不执行却记历史 success=true |
| 15 | nop-task | 持久化状态下 Loop 迭代 2+/Fork 分支 2+ 被 continuation-skip（loadStepState 未按 recoverMode 门控） | 恢复后步骤体静默跳过、复用首轮结果 |
| 16 | nop-task | 延迟重试分支 schedule 先执行一次、thenApply 再执行一次 | 每轮延迟重试重复执行业务副作用 |
| 17 | nop-task | `ExecutorTaskStepWrapper` whenComplete 成败分支写反 | executor+异步步骤成功即永久挂死；失败被吞当成功 |
| 18 | nop-sys | `SysDaoResourceLockManager.isExpired` 过期判断条件反向 | 有效锁被抢占删除（互斥失效）、真过期锁永不回收 |
| 19 | nop-report | `ReportDataSet.min` 遇 null 字段值直接放弃整个聚合结果 | 含空值数据集 min 输出错误 |
| 20 | nop-batch | `ListBatchLoader` `subList(offset, n)` 应为 `(offset, offset+n)` | 超过一个 batchSize 的数据静默丢失且任务报成功 |
| 21 | file-retry-tcc | TCC 超时取消失败被误标 `CANCEL_SUCCESS` 终态 | 补偿双重永久放弃，参与者预留资源悬挂 |
| 22 | format-record | nop-tablesaw `ColumnCollectors` 全部 10 个 consumer null 判断颠倒 | 产出的 Table 数据全部静默丢失 |
| 23 | ai-core-api | Gemini/Ollama 流式 parseStreamChunk 不提取 functionCall args（ChatStreamChunk 无承载通道） | 默认流式路径工具调用参数恒为空 |
| 24 | nop-code | 迭代式 Tarjan 回溯时子节点 lowLink 传播被跳过 + 非 returning 帧不弹出 | 环检测静默返回错误数据 |
| 25 | nop-graph | `Edge` 未实现 equals/hashCode，真实调用链每次 new Edge | 图 diff 把所有边同时判为新增+删除 |

### 流处理/状态类（4 条）

| # | 模块 | 问题 | 影响 |
|---|------|------|------|
| 26 | stream-core | `StreamReduceOperator.open()` 无条件重建 `values` map（restore 先于 open） | checkpoint 恢复后 keyed reduce/sum/min/max 聚合状态清零 |
| 27 | stream-cep | watermark/定时器回调在错误的 key 上下文执行（不切 key、只排最后 key 的队列） | 多 key 流事件滞留、超时清理不触发、SharedBuffer 泄漏 |
| 28 | stream-flow-conn | 2PC sink（JDBC+File）无 per-subtask 隔离，deepCopy 共享实例、pendingCommits 互相覆盖 | 并行度>1 时其余 subtask 数据静默丢失，exactly-once 破产 |
| 29 | stream-flow-conn | source `run()` 不重置 `running`/`failed`，region 重启后循环条件立即为假 | 数据流静默停摆且监控显示重启成功 |

### 功能完全失效类（7 条）

| # | 模块 | 问题 | 影响 |
|---|------|------|------|
| 30 | nop-dao | `JdbcBatcher.flush()` 两个 finally 均不 close PreparedStatement | 所有实体批量保存每批泄漏一个 Statement，长事务耗尽游标 |
| 31 | format-pdf-svg | `ResourceDocumentParser.open()` 对同一 InputStream 双读，memoryRestrict 默认开启 | PDF 解析整体不可用 |
| 32 | net-misc | GZip/Deflate `decodeBuf` 构造解压流后仍从原始压缩流拷贝 | 解压功能完全失效（返回压缩字节） |
| 33 | net-misc | SocketServer 连接表 add 用 `getHostAddress()`、remove 用 `toString()`（带 `/` 前缀） | 每个断开客户端永久泄漏，broadcast 持续写死连接 |
| 34 | net-misc | Vert.x MQTT `MqttConnection` 从不调用 `endpoint.accept()` | 服务端无法完成任何客户端连接 |
| 35 | nop-utils | `UnifiedDiffLine` 构造 Guard.notEmpty(content)，git diff 空行 content 为空串 | 任何含空行的真实 diff 解析必崩（AI 补丁场景常态） |
| 36 | nop-utils | `ShellCommand.create` Unix 分支缺 `-c`，`sh mvn ...` 把首参数当脚本文件 | 非 Windows 平台命令完全不可用 |

## 跨模块模式聚类（P0/P1 的共性根因）

1. **静默失败是最大族群**。约三分之一的 P0/P1 不是崩溃而是"不报错地做错事"：迁移 DDL 记成功（#14）、批处理丢数据报成功（#20）、任务假完成（stream-runtime waitForInvokable 30s 超时假成功）、邮件假发送（#9）、EQL NOT 语义丢失、autotest 变体输出表永不被校验（假绿）。这类问题测试难拦截、生产难发现，建议作为平台级改进主题（fail-loud 原则）。
2. **复制粘贴/变量误用**。`min` 与 `max` 不对称（#19）、`subList(offset, n)`（#20）、`getRow(rowIndex)` 应为 `getRow(i)`（nop-excel）、`Array.getLength(index)` 应为 value（dev-tools 调试器）、null 分支互换（#22）。集中在"对称结构只改了一半"的场景。
3. **分支/条件写反**。whenComplete 成败反写（#17）、isExpired 反向（#18）、ColumnCollectors null 颠倒（#22）、utils 的 TriePathRouter 通配注册失效、nop-dyn 的 merge 分支反置。与族群 2 合计约 15 条 P0/P1。
4. **并发与生命周期**。恢复/重启路径（stream #26/#29、nop-task #15）、region restart、checkpoint restore 是系统性盲区——正常运行路径正确，"从持久化状态恢复"路径大量未测试。另有锁序死锁（nop-dyn InMemoryCodeCache）、共享模板可变污染（format-office OfficePackage.copy 浅拷贝）、无 CAS 状态更新（TCC）。
5. **不可信输入的内存放大**。format 系三个 OOM 面（sharedStrings uniqueCount、单元格列索引无上界、MetaTableProfiler 无 LIMIT 全列拉取）共同指向"解析用户文件时缺少资源上限"。
6. **认证/授权边界**。9 条安全 P0 中 6 条（#1-#6）属于"边界条件下的信任假设失效"：配置为 0 跳过校验、publicAccess 端点信任 payload、默认装配携带测试凭证、客户端可控值进文件路径。
7. **方言/Provider 适配层质量分层明显**。Gemini/Ollama/Anthropic dialect、TDengine 驱动、Vert.x MQTT 等适配层的缺陷密度显著高于平台自研核心（nop-biz/nop-metadata/nop-ai-agent 的 grep 层面几乎零违例）。适配层多为单遍移植，缺往返测试。

## 修复进展（fix-ai-check 分支，2026-08-21）

dev-tools / file-retry-tcc / format-misc / format-office / format-pdf-svg / format-record 六个单元的全部 118 条发现已在独立分支 `fix-ai-check` 完成处置：74 条已修复（含全部 P0/P1，附回归测试）、27 条已确认暂缓（需设计决策）、4 条复查后维持现状、1 条审计前提有误（xlsx 列引用放大，CellPosition 既有上限）、其余文档化/删除死代码。逐条处置见各报告条目末尾的"处置（fix-ai-check 分支）"标注。两个超出审计的发现：ModelBasedPacketCodec 编码帧长公式与解码恒差 lengthFieldEndOffset 字节；readLine EOF 语义应统一为 null 而非空串（消费方以 null 判结束）。

## 修复进展（fix-ai-check 分支，2026-08-22，剩余全部 P0 清零）

2026-08-21 六单元之外的**剩余 32 条 P0**（分布于 25 份单元报告）已全部处置完毕，每条均按"live code 复核 → 红测试 → 最小修复 → 绿 → 模块全量测试 → 报告条目标注"闭环：

- **安全类 8 条全部修复**：nop-auth 登录锁号与凭证校验解耦（max-login-fail-count=0 不再跳过密码/SMS/账号状态校验）、nop-wf transferActors 增加管理员/本人鉴权 + requireUser 校验、ai-toolkit-skills sessionId 白名单防路径穿越、gateway 默认 API Key 改配置化 fail-closed、nop-search Lucene 数值/path 过滤按索引类型重构查询构造、nop-integration 腾讯短信区号回退默认 + JavaEmailSender 异常穿透。ai-rest 扫码登录实施最小加固（登录身份以服务端 ticket 断言为准、伪造 extId 拒绝、ticketId 改随机 UUID）；完整的渠道签名/OAuth code 换取属设计级变更，标注暂缓。
- **数据正确性 15 条全部修复**：thenRun 异步分支、BeanCopier 数组长度、BaseRecordInput off-by-one、游标分页三缺陷、迁移 type 回填、nop-task 三条（loop/fork continuation-skip 按首实例化门控、延迟重试去重、whenComplete 成败反正）、锁过期反向、min null、ListBatchLoader 分块索引、Tarjan lowLink 传播、Edge equals/hashCode、Gemini/Ollama 流式工具调用 args 通道（ChatStreamChunk 新增 arguments 承载）。
- **流处理/失效类 9 条全部修复**：StreamReduceOperator 恢复后不清零、CepOperator per-key 定时器账本 + watermark/processing-time 回调切 key 上下文（checkpoint 快照新 per-key 格式、兼容旧 flat 格式）、2PC sink per-subtask 隔离（JDBC ledger 加 subtask_id 列，旧 schema 显式报错）、source run() 重置重启、GZip/Deflate decode、SocketServer 连接表 key 统一、MQTT endpoint.accept、JdbcBatcher flush 关闭 Statement、UnifiedDiffLine 空行、ShellCommand Unix `-c`。
- **超出审计的新发现**（未修，已记录在对应报告标注中）：DaoQueryHelper.queryToFindPrevSql 空 filter 时漏 where 生成非法 EQL；db-migration 9 个变更类（createIndex/sql/alterColumn 等）解析期 ClassCastException、insert/update column 解析为 DynamicObject、14 个测试 fixture XML 不符 xdef 契约；nop-task 延迟重试 async 结果未压平（随 P0 一并修复）。
- **门禁维护**：nop-stream output-contract-registry 发射点行号随 CepOperator 改动重钉（507→568、814→960），`check-nop-stream-invariants.mjs` exit 0；`check-nop-stream-audit-manifest.mjs` 的三个分母（钉在 2026-08-07 HEAD）在本次改动前即已过期红屏，属存量状态未处置。
- 各报告 P1/P2/P3 条目尚未处置（保持原状），建议后续按模块分批立项。

## 后续建议

1. **修复分批**：建议按"安全类 9 条 → 数据正确性 16 条 → 流处理/失效类"顺序建修复 plan（`ai-dev/plans/`），每个 plan 引用对应 check 报告作 baseline。P2/P3 可按模块顺带处理。
2. **复检批次（roadmap Phase 7 遗留项）**：以下单元发现数相对体量偏低，建议追加第二轮检查：nop-metadata（441 文件仅 9 条）、nop-ai-agent（963 文件仅 11 条）、nop-rule（8 条）、nop-datav（7 条）、demo-migration（10 条）。
3. **测试盲区**：多个 P0（#12/#13/#15/#25 等）的现有测试恰好无法区分正确与错误实现，修复时应先补"能失败的红测试"再修（Bug Fix Test Coverage Rule）。

## 报告索引

54 份单元报告与本文件同目录，文件名与模块/单元一一对应（见 [ROADMAP.md](ROADMAP.md) 各 Phase 表格的链接列）。
