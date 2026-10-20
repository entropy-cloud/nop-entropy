# C5 内存有界性与大数据量 — nop-ai-core-nop-ai-agent（WI 3.1）

## 审计范围与口径

- **本批次（X1/3）覆盖分区**：`nop-ai/nop-ai-core/src/main/java` 全量（119 个 .java，其中 16 个 `_gen/` 生成类按纪律排除为发现对象；实际审读 103 个手写文件）。基线 `_tmp/cra-baseline-nop-ai-core-nop-ai-agent.txt` C5b 节中出现的 `nop-ai-agent` 分区文件（`DBMessageService`、`SessionFileReader`、`CheckpointSnapshotReader` 等）属后续批次（X2/X3）分区，本批次不重复登记。
- **方法**：重点包逐文件全文审读（persist / service / prompt / prompt.node / response / file / dialect / model 手写部分 / reliability / commons.splitter / simplifier / command / initialize / api.support / api.xdef / utils），辅以 `readAllBytes|readString|readText|readLines|new byte[]` 全量物化站点 grep 与「随输入增长容器」（static Map / computeIfAbsent / StringBuilder 累积）grep 双向扫盲；对每个疑似站点回读完整调用链确认可达性。
- **口径纪律**：严重度按共享提示词校准；内存有界是行为性缺陷，单线程调用不豁免；非竞态类资源界缺陷不适用「时序未具体化降 P2」条款（该条款只约束并发交错类发现），但无具体触发链的理论资源风险仍降档并标注；对照 owner doc `docs-for-ai/03-modules/nop-ai.md` 的声明项逐条核对（`nop.ai.service.cache-ttl=0` 惰性过期语义已被声明——在 C5-002 中如实标注，按「守卫缺失/包络未声明」口径保留发现）；`_gen/` 生成类与 nop-kernel 平台基础设施自身实现不重审，只审本模块使用方式。
- **C5a 数量口径人工复现**：基线 C5a 节因脚本对目录执行 `wc` 失败而为空。人工复现「随输入增长容器」清单共 20 项：StreamAggregator textBuilder / reasoningBuilder / toolCallAccumulators / argumentsBuilder（4 项无界）；DefaultAiChatResponseCache 缓存目录文件（无界，磁盘）；DefaultChatLogger 日志目录文件（无界，磁盘）；LlmConfigHelper.secretCache（键=已配置 provider 集，有界）；ChatServiceImpl.rateLimiters、EmbeddingServiceImpl.rateLimiters（键=provider，且 loadConfig 先于 checkRateLimit 校验，有界）；ProviderFailoverQueue.health（键=provider，有失败记忆无收缩，有界）；ThresholdBreaker.entries（prone-shrink 语义，有界，实现正确）；LlmDialectFactory.DIALECTS（枚举键，有界）；DefaultSystemPromptLoader.promptCache（无容量配置，键=workMode 可扩展）；LocalFileOperator readFileContent 全文 String / readLines 清单 / grepFiles 每文件行清单 / globGrep 文件清单（4 项无界，输入=文件系统）；IChatExchangePersister.load 全文 String（无界，输入=缓存条目）；EmbeddingServiceImpl.parseSuccess 全响应 boxed 树（有界但放大 5-8×）；AiCommand/AiXDefHelper/prompt 模板 RCM 平台缓存（平台所有，不重审）。无界/边界缺陷收敛为 7 条发现（C5-001…C5-007）。
- **C5b 复现**：基线 C5b 对本分区命中 3 文件 7 行（`LocalFileOperator.readLines` ×2 站点、L406 grep readLines、`IChatExchangePersister:14` readText、`DefaultChatExchangePersister:40` deserialize）。全部复核确认，并扩展出基线未覆盖的站点：`LocalFileOperator.readFileContent:102`（切片前整读，最重）、`FileOperatorHelper.fileNormalizedMd5:67`、`DefaultSystemPromptLoader.loadPrompt:60`、`PromptSyntaxParser.handleIncludeExpr:267`（include 全文读 + 平台 deps 栈有深度守卫，闭环不报）。
- **本批次（X2/3）覆盖分区**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent` 下 `security`（74f，校验链/沙箱/租户/账本）/ `plan`（44f 手写，`_gen/` 24f 排除）/ `engine`（47f）/ `runtime`（34f）/ `model`（17f 手写，`_gen/` 16f 排除）/ `reliability`（30f）共 6 包，逐文件审读。基线 C5a 节对 nop-ai-agent ROOT 同为空（脚本 wc 目录失败，与前批一致），人工复现「随输入增长容器」共 20 项：FileBackedCheckpointManager 5 map（bySession/byWatermark/snapshotCache/saveCounters/loadedSessions）、DBCheckpointManager 3 map、ToolExecutionCheckpoint 2 map、DefaultDenialLedger.counts、FingerprintPostDenialGuard.deniedFingerprints、DefaultWaitCoordinator.sessions、SessionGoalTracker.entries、AgentActor.receivedMessages、InMemoryActorRuntime.executors、engine sessionMailboxes/sessionMailboxSubscriptions、AgentExecutionContext.steeringQueue、InMemorySpillStore.store（engine 写侧）、InSessionCompactionSnapshotArchive.store（engine 写侧）、InSessionCompactionArchive.store、DefaultAgentEventPublisher.failureCounts、CheckpointJournalReader.parseAll 瞬态（全文 String + substring + sections[] + 每段 lines[]/fields ≈4× 放大）。无界/边界缺陷收敛为 7 条发现（C5-008…C5-014）+ 5 条集外线索。C5b 命中本分区 2 文件（`CheckpointSnapshotReader:48`、`CheckpointJournalReader:116`）全部复核确认——前者 snapshot.json 仅元数据字段（有界，随观察）；6 包内 readAllBytes/readString 类站点仅此 2 处（grep 全量核对）。plan/runtime 有 safetyMaxCycles/maxRecoveries 双重循环界（有界，不报）；model 包为纯值模型无容器字段；security 校验链（RuleBased/Default/Parent 系 checker、DockerSandboxBackend、PermissionMatrix/Provider）均为无状态或有界配置集。基线 C5b 中 DBMessageService/AgentMessageEnvelopeJson/SessionFileReader/DBSessionStore/AiAgentSessionTable 行属 message/session 分区（X3），本批不登记；`guardrail`/`compact`/`session`/`message`/`tool` 等包非本批分区，仅作为写侧证据回读（缺陷主体登记以本批 6 包写侧调用点为准并注明 store 归属）。
- **本批次（X3/3，C5 收官批）覆盖分区**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent` 下 `team`（55f，含 flow/scheduler）/ `tool`（21f）/ `session`（21f）/ `message`（18f）/ `memory`（17f）五大包 + 16 小包与根（budget 3 / compact 14 / completion 7 / conflict 7 / contribution 6 / guardrail 14 含 rule/ / hook 7 / middleware 7 / quota 6 / recipe 1 / repair 8 / router 5 / skill 14 / talent 2 / usage 6 / 根 3），共 242 个手写 .java（本分区无 `_` 前缀文件），逐包审读 + 分区级 `readAllBytes|Files.readString|Files.readAllLines|ResourceHelper.readText|readString` 与「随输入增长容器」双向 grep 扫盲。基线 C5a 对 agent ROOT 仍为空（与前两批同因）；C5b 命中本分区行全部复核：`SessionFileReader:102`（单会话文件全读，有界于单会话尺寸，随观察）、`FileBackedSessionStore:455`（journal readAllLines，FOLD_THRESHOLD=64 行有界，随观察）、`DBMessageService:356/619`（findPending LIMIT=maxBatch=10，瞬态反序列化，闭环不报）、`AgentMessageEnvelopeJson`（每消息瞬态解析，闭环不报）、`DBSessionStore:292/601`（升级为 C5-016 全量物化发现的证据行）、`AiAgentSessionTable:18`（注释行，无 IO）、`RuleSetLoader:78-119`（readString 为 map 取值 helper 非 IO；L59 配置文件全读有界）。人工复现「随输入增长容器」共 26 项：三实现 session store 的 7 张会话键 map（sessions×3 / persistedStates×2 / sessionLocks×2）、memory 两 provider 的 stores map、DefaultBackgroundTaskRegistry.tasks、team 四表（teams / sessionToTeamId / taskStore tasks+teamIndex）、conflict intentsByPath、LocalAgentMessenger replySubscriptions（pendingRequests 有 whenComplete 终态清理，对照组）、DeferredAckMailbox pending/inFlight/deadLetter/keysByCorrelationId、InMemoryAiMemoryStore.items、InMemoryStorageAdapter.items、InMemoryVectorAdapter.index、DBMessageService.consumers（cancel 对称清理，对照组）、TaskDispatchCoordinator.inFlightDispatches（whenComplete 排空 + 超时保证 settle，对照组）、AgentSession.seenInputKeys（1024 FIFO 有界）/pendingInjections（256 fail-fast 有界，对照组）、SmartModelRouter.tierSequences（builder 枚举键配置面，有界）、DefaultHookRegistry 三表（注册面对称，有界）、InMemoryContributionRegistry byType/bySource（unregisterSource 对称，有界）。无界/边界缺陷收敛为 8 条发现（C5-015…C5-022）+ 5 条集外线索。**至此 C5 维度三批次（X1 nop-ai-core + X2 agent 6 包 + X3 本批）对 nop-ai-core-nop-ai-agent 双工程的覆盖收官**。

---



> 批次 X2（B+C 分区 security/plan/engine/runtime/model/reliability）产出 C5-008…014 + CL-045…049 已落盘；批次覆盖声明行因配额中断缺失——X2 覆盖完整性由 Phase 3 深挖批复核确认（主 agent 记录）。

## 发现

### [C5-001] 流式响应聚合无界累积 + 双层订阅关闭背压 + 线上可控键累加器无上界

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:483-529, 575-595（聚合器）；202-233, 391-440（订阅与背压）`
- **证据片段**:
  ```java
  // L484-486：随流式 chunk 无限增长的三组容器
  private final StringBuilder textBuilder = new StringBuilder();
  private final StringBuilder reasoningBuilder = new StringBuilder();
  private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();
  // L522-524：键 itemIndex 来自线上数据，键空间无上界
  Integer index = chunk.getItemIndex() != null ? chunk.getItemIndex() : 0;
  ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(index, k -> new ToolCallAccumulator());
  // L208 / L403：两级订阅均关闭背压
  subscription.request(Long.MAX_VALUE);      // callStream 源订阅
  subscription.request(Integer.MAX_VALUE);   // aggregateStreamToResponse 订阅
  ```
- **严重程度**: P1
- **并发场景**: 单请求内为 HTTP 客户端读线程 → SubmissionPublisher 投递 → CompletableFuture 回调链的顺序消费（无跨线程数据竞争问题）；风险面是多请求并发下的堆峰值叠加（每请求一个 StreamAggregator，峰值 = 并发数 × 单响应累积量）。
- **触发时序**: T1：请求带 `options.accountBaseUrl=https://attacker`（账号链特性明确支持任意 baseUrl 覆盖，见 `ChatServiceImpl.resolveBaseUrl` L315-336）或任一被劫持/失控的 provider 端点；T2：`aggregateStreamToResponse` L403 发出 `request(Integer.MAX_VALUE)`，端点持续下发 `StreamItemType.text` delta 且不终止——每一 chunk 在 L513 `textBuilder.append(delta)` 无限追加；本地无任何响应长度守卫（`resolveMaxTokens` L422-440 仅把 maxTokens 写进请求体，由 provider 自觉执行，响应侧无对应上界）→ 堆持续增长至 OOM，请求永不完成。变体：端点下发 N 个不同 `itemIndex` 的 tool_call ADDED 事件 → L523 computeIfAbsent 创建 N 个 ToolCallAccumulator（各含 callId/name/StringBuilder），键空间 = 线上整数全域。
- **现状**: 聚合语义本身要求物化最终 ChatResponse（不可避免），但模块缺少响应累积的任何可配置上界（max response chars/bytes、max tool-call 数），且双层 `request(MAX_VALUE)` 使 SubmissionPublisher 的有界缓冲（默认 256）满载策略永不生效——缓冲保护被显式绕过。
- **风险**: 主路径（`ChatOptions.stream` 缺省 true → `callAsync` L117-124 默认走 `aggregateStreamToResponse`）。恶意/失控端点下单请求即可 OOM；正常长响应（推理模型数十万字 reasoning）下峰值 = 完整响应 × ~2（builder 翻倍扩容）× 并发请求数，无任何背压与预算控制。模块未声明响应长度包络，无「声明包络内必然 OOM」与「超出包络」的区分线——本地防线整体缺位。
- **建议**: ①为 StreamAggregator 增加可配置累积上界（复用 `io.nop.commons.cache.CacheConfig` 风格的容量配置注入；超界 fail-fast 抛 `NopException(ERR_AI_RESPONSE_TOO_LARGE)`，错误码入 `NopAiCoreErrors`）；②对 `toolCallAccumulators` 设最大条目数（超界拒绝并 WARN）；③两级订阅改为有限 request 窗口（如按 SubmissionPublisher 默认 buffer 大小逐批 request），恢复背压——平台 `HighWatermarkSemaphore`（io.nop.commons.concurrent.semaphore）可作为水位闸。
- **失效面枚举**: ①`StreamAggregator.textBuilder`（L484）——本发现主对象；②`StreamAggregator.reasoningBuilder`（L485）——同构，同修；③`StreamAggregator.toolCallAccumulators`（L486）——键空间无界，本发现；④`ToolCallAccumulator.argumentsBuilder`（L578）——单 tool call arguments 无上界，随③同界；⑤`callStream` L208 `request(Long.MAX_VALUE)`——同修点；⑥`aggregateStreamToResponse` L403 `request(Integer.MAX_VALUE)`——同修点；⑦非流式 `callAsync` L153/167 `getBodyAsString()` + `dialect.parseResponse`——同为全量物化但单次瞬态、上界=HTTP body（http client 层配置），登记观察不单列；⑧`AbstractLlmDialect.parseBodyMap` L311-324 错误响应全量 JSON.parse——同⑦；⑨五方言 `parseResponse`/`parseStreamChunk`（OpenAiDialect L193/254 及 Anthropic/Gemini/Ollama/Responses 同位）——每 chunk 瞬态解析，随⑦口径。
- **信心水平**: 确定
- **误报排除**: 不属于「无状态单例被并发调用是设计意图」豁免——这是资源上界问题而非数据竞争；不属于「SubmissionPublisher 本身有界」误报——`request(MAX_VALUE)` 使其有界性失效是本发现的一部分；不属于「聚合必然全量物化」误报——发现对象是上界缺失而非物化本身。
- **复核状态**: 保留(P1)｜新发现——回码核验成立：textBuilder/reasoningBuilder/toolCallAccumulators 三容器无任何上界（addChunk 直核）、itemIndex 线上键 computeIfAbsent 直核、callStream request(Long.MAX_VALUE) 与聚合订阅 request(Integer.MAX_VALUE) 双点直核、callAsync stream 缺省 true 直核——响应侧无任何长度守卫，与同站点 C3-002(P1)/C4-002(P1) 判级一致

### [C5-002] AI 响应缓存磁盘无界：TTL 缺省关闭 + 惰性单条过期无清扫 + 无容量上界 + 读取整条内存物化

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/DefaultAiChatResponseCache.java:22, 44-67, 69-88；persist/IChatExchangePersister.java:13-15；resources/_vfs/nop/ai/beans/ai-defaults.beans.xml:17-21`
- **证据片段**:
  ```java
  // DefaultAiChatResponseCache L50-54：过期仅在被读取时惰性判定并删除单条
  if (cacheTtlSeconds > 0 && isExpired(resource)) {
      LOG.info("nop.ai.cache-expired:...");
      resource.delete();
      return null;
  }
  // L83-85：键 = 请求内容 md5，任意不同请求内容 → 新文件
  String hash = makeRequestHash(request);
  cachePath += "/" + hash.substring(0, 2) + '/' + hash.substring(2, 4) + '/' + hash + "-exchange.json";
  // IChatExchangePersister L13-15：读取 = 全文件进内存
  default ChatExchange load(IResource resource) {
      return deserialize(ResourceHelper.readText(resource));
  }
  ```
- **严重程度**: P2
- **并发场景**: 多请求线程并发 save/load 同一目录（无内存共享状态，`chatExchangePersister` 单例无锁写文件）；磁盘面为跨请求长时运行累积，与线程模型无关。
- **触发时序**: T1：AiCommand 启用响应缓存（`AiCommand.useResponseCache(true)`，AiCommand.java L229-236/309-315）后每次有效响应写一个 `{md5}-exchange.json`；T2：生产 prompt 含时间戳/会话变量等变化内容 → 每请求 md5 不同 → 文件只增不删；TTL=0（`ai-defaults.beans.xml:19` `@cfg:nop.ai.service.cache-ttl|0`）时连惰性删除也不存在；TTL>0 时仅「再次被读取的条目」命中 L50-54 被删，不再被读取的条目永存 → 目录无上界增长。读取路径上，单条 = 完整 ChatExchange JSON（request 全部消息 + response 全部消息 + outputs），`readText` 整条物化，单条大小 = LLM 全响应尺寸，无单条上限校验。
- **现状**: owner doc（`docs-for-ai/03-modules/nop-ai.md:142`）已声明「0=永不过期（兼容默认）……读取时惰性过期……不主动清扫」的机制语义；但未声明任何容量包络（条数上限/目录字节预算/清扫任务），也无运维清理入口——机制声明不等于安全包络声明。F4（无界账本只增不删）族。
- **风险**: 缓存启用部署长时运行后磁盘耗尽（写失败 → `saveCachedResponse` 抛异常 → `AiCommand.executeAsync` L310-315 thenApply 链中断）；单条恶意大缓存文件被命中时内存瞬时放大（文件大小 ×~2：文本 + 解析后对象树）。加密开启（`encryptEnabled`）时 `maybeEncrypt` 再生成一份密文串，单条峰值 ×3。
- **建议**: ①补 `nop.ai.service.cache-max-entries`/`cache-max-dir-bytes` 上界，写入时用平台 `GlobalCacheRegistry` 风格登记并在超界时按 LRU 清理（复用 `io.nop.commons.cache` 设施）；②补后台/启动期目录清扫（TTL 过期条目批量删除），或至少在 owner doc 声明「缓存目录需外部运维清理」的边界；③load 侧增加单条大小上限（超界视为 miss 并删除）。
- **失效面枚举**: ①`DefaultAiChatResponseCache.saveCachedResponse`（L69-73）——写侧无界，本发现；②`DefaultAiChatResponseCache.loadCachedResponse`（L44-62）——读侧惰性过期 + 整条物化，本发现；③`IChatExchangePersister.load`（L13-15）——全量 readText，本发现同修；④`DefaultChatExchangePersister.serialize`（L35-37）——写侧全量 JSON 串 + 可选加密副本，随①；⑤`DefaultChatExchangePersister.calcRequestHash`（L48-50）——每键计算对全部消息全量序列化（瞬态，随④口径）；⑥`DefaultChatLogger` 日志目录——同族磁盘无界，独立登记为 C5-003；⑦nop-ai-agent 分区 `DBSessionStore`/`SessionFileReader` 全文读回（基线 C5b 已列）——属后续批次，此处仅标注不重复报告。
- **信心水平**: 确定
- **误报排除**: 「有界声明存在的缓存（CacheConfig 配置了上限）」豁免不适用——本缓存无任何 CacheConfig/上限；owner doc 声明的是过期机制语义而非容量边界，按纪律第 6 条「守卫缺失、包络未声明才是缺陷」保留。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：ai-defaults.beans.xml:19 cache-ttl|0 直核、惰性单条过期（仅读时判定）直核、md5 键路径写入无任何容量/清扫直核、load 经 readText 全条物化直核；owner doc 声明的是过期机制语义而非容量包络，口径第 6 条适用

### [C5-003] Chat 请求/响应日志按请求双文件落盘且无任何轮转/清理，磁盘无界

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultChatLogger.java:42-79；service/ChatLogHelper.java:29-36`
- **证据片段**:
  ```java
  // DefaultChatLogger L47-57：每次 logRequest 写一个整请求 JSON 文件
  if (isValidLogDir()) {
      IResource resource = getResource(request, "-request.yaml");
      ...
      ResourceHelper.writeText(resource, redacted);
  }
  // ChatLogHelper L32-34：路径按 日期/sessionId/时间戳 逐请求展开
  String fileName = today + '/' + sessionId + '/' + request.getRequestTime()
          + '-' + request.getRetryTimes() + '-' + request.getRequestId() + postfix;
  ```
- **严重程度**: P2
- **并发场景**: 多请求线程并发写不同文件（文件名含 requestId，无碰撞）；磁盘面跨请求长时运行累积；`logRequest` 中 `JSON.serialize(request, true)` + 三条正则 `replaceAll` 对全量请求串做 4 次全串拷贝（内存瞬态 ×5）。
- **触发时序**: T1：`nopChatLogger` bean 缺省装配 `logDir=/nop/ai/log`（`ai-defaults.beans.xml:15` + `@cfg:nop.ai.service.log-dir|/nop/ai/log`），且 `CFG_AI_SERVICE_LOG_MESSAGE` 与 provider `logMessage` 开启时每次调用 LLM 均触发；T2：每请求写 `-request.yaml` + `-response.yaml` 两文件，路径按日期再按 sessionId 层层展开，系统内无任何 TTL/轮转/删除代码路径（全模块 grep 无清理调用）→ 长时运行磁盘无界增长。
- **现状**: 日志面与 C5-002 同为 F4 族但独立成点：默认开启、无关闭轮转、单条大小 = 完整请求/响应 JSON（含全部历史消息时可达 MB 级）。
- **风险**: 高频 agent 场景下日志目录膨胀至磁盘耗尽；`LOG.info("...content=\n{}", ...)`（L45/63-65）同时把完整内容写入 slf4j 主日志，双重放大。
- **建议**: ①复用 C5-002 的上界/清扫设施对日志目录做日期分区过期；②提供内置轮转（按日期目录已具备分区基础，补删除策略即可）；③`LOG.info` 侧改用 `StringHelper.limitLen(content, N)` 截断。
- **失效面枚举**: ①`DefaultChatLogger.logRequest`（L42-59）——本发现；②`DefaultChatLogger.logResponse`（L61-79）——同构，同修；③`ChatLogHelper.getSessionResource`（L29-36）——路径生成（sessionId 已有 SAFE_SESSION_ID 白名单，路径安全面闭环，仅磁盘面在列）；④`redactIfNeeded`（L81-88）——正则全串扫描内存瞬态，随①②同修；⑤C5-002 响应缓存目录——同族不同面，见 C5-002。
- **信心水平**: 确定
- **误报排除**: 「低频路径粗粒度日志」豁免不适用——这是每次 LLM 调用都命中的主路径写盘；与 logback 托管日志不同，此为自管文件无任何外部轮转机制接管。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：logRequest/logResponse 双文件写盘与按日期/sessionId/时间戳路径展开直核，全文件 grep 无任何 delete/rotate/clean/purge——自管文件无外部轮转接管成立

### [C5-004] 文件操作符全量物化族：切片读取先整读（接口 limit 契约被实现违反）、分页读行整读、grep 每文件整读、globGrep 文件清单无上界

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/file/LocalFileOperator.java:92-122, 125-155, 377-445；file/IFileOperator.java:42-47, 76-84, 117-125`
- **证据片段**:
  ```java
  // LocalFileOperator L102/L115：先物化全文件，再做 offset/limit 切片
  String content = FileHelper.readText(file, "UTF-8");
  ...
  content = content.substring((int) offset, (int) offset + limit);
  // L143：分页读行 = 全量行清单后 subList（视图仍持有全清单）
  List<String> allLines = readLines(path);
  // L441：globGrep 内部硬编码 0 = 文件清单无上界
  List<String> filePaths = findFilesByGlob(directory, globPattern, 0); // 0表示无限制
  ```
- **严重程度**: P2
- **并发场景**: agent FileTool（javadoc IFileOperator L13-14 指明消费方为 FileTool BizModel 与 DSL tooling）多请求并发调用；单调用峰值 = 2×文件大小（全文 String UTF-16 ≈ 2×磁盘字节 + substring 切片副本），与 `limit` 参数值无关；并发 N 个读请求峰值线性叠加。
- **触发时序**: T1：agent 对 workspace 内大文件（数百 MB 日志/数据文件，大小仅受环境约束）发起带 `offset/limit` 的读取；T2：`readFileContent` L102 无条件 `FileHelper.readText` 物化全文件，L115 切片后原全量串仍可达至方法返回 → 峰值内存 ≈ 2×文件大小，接口承诺的读取上界（IFileOperator L43-47「maximum number of characters read per file」、L79-80「limit: the maximum number of characters to read」）在 I/O 层完全未生效。变体 A：`readLines(path, startLines, lineCount)` L143 全量行清单物化后仅返回 subList 视图（底层数组全量驻留，若调用方持有返回值则全清单不可回收）。变体 B：`grepFiles` L406 每文件全量行清单（limitPerFile/totalLimit 只限匹配数不限内存）；`globGrep` L441 自传 `maxFileCount=0` → 文件清单无上界。变体 C：`offset` 为 long 而 L113/115 强转 `(int)`，offset > 2^31 时截断为负/错值 → `StringIndexOutOfBoundsException` 或静默错切片（大数据量行为缺陷）。
- **现状**: `readFileContent`/`readLines`/`grepFiles` 均为「limit 只约束返回物、不约束物化过程」；`globGrep` 的 0 值是接口声明语义（L122「<=0 means unlimited」）下的缺陷调用点（内部唯一调用方自弃上界）。
- **风险**: agent 读取路径单请求即可吃掉与文件等比的堆；`globGrep` 在大仓库（数十万文件）下清单 + 逐文件全量行双重无界。同类风险已在更新代 `IToolFileSystem`（javadoc L20-23「char-count limited DTO results, depth/max bounded glob/grep」）中修复，遗留路径未回迁。
- **建议**: ①`readFileContent` 改为按 offset/limit 流式读取（`Files.newBufferedReader` 跳行/限读），或至少在读取前用 `file.length()` 与 limit 比较、超界拒绝；②`readLines(startLines, lineCount)` 改流式跳行读取；③`grepFiles` 逐行流式读取（`BufferedReader.readLine`）替代 `readLines`；④`globGrep` 传入调用方可控的有限 maxFileCount；⑤offset 校验 `<= Integer.MAX_VALUE` 或改用流式偏移。
- **失效面枚举**: ①`readFileContent`（L92-122）——切片前整读 + (int) 截断，本发现主对象；②`readLines(path)`（L125-139）——全量行清单，接口固有（单文件读取语义声明为「all lines」），随包络声明观察；③`readLines(path, startLines, lineCount)`（L142-155）——分页契约下整读，本发现；④`grepFiles`（L377-435，L406 整读）——本发现；⑤`globGrep`（L437-445，L441 maxFileCount=0）——本发现；⑥`FileOperatorHelper.fileNormalizedMd5`（L66-69）——整文件读入求 md5（哈希语义必须全读，但可流式 digest，随包观察）；⑦`IFileOperator.readFileContents(paths, maxLengthPerFile)` default 方法（L49-58）——N 文件 × 每文件整读，N 与单文件大小均无上界（maxLengthPerFile<=0 时声明无限制），随①同修；⑧`mergeFile`（L348-369）——XNode 全量解析合并，输入为编辑器级文本，有界包络，不报。
- **信心水平**: 确定
- **误报排除**: 「maxFileCount<=0 = 无限制」是接口明文声明，故 findFilesBy* 本体不报；报的是 globGrep 内部调用点自弃上界与 limit 契约被实现违反——两者均为「守卫缺失/守卫可绕过」而非「已声明边界」。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：readFileContent 无条件全读后 substring 切片直核（峰值≈2×文件与 limit 无关）、readLines(startLines,lineCount) 全量清单 subList 直核、globGrep findFilesByGlob(...,0) 自弃上界直核——「limit 只约束返回物不约束物化」定性准确

### [C5-005] Embedding 响应全量物化 + 装箱中间树 5-8× 放大

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:391-437`
- **证据片段**:
  ```java
  // L392：全响应 JSON 文本 → 全量 Map 树
  Map<String, Object> body = JsonTool.parseMap(resp.getBodyAsString());
  // L400-401：data 列表整体拷贝
  List<Map<String, Object>> sorted = new ArrayList<>(data);
  sorted.sort(...);
  // L417-421：boxed List<Double> 逐元素拆箱到 double[]（原始 boxed 树全程可达）
  List<Object> embedding = (List<Object>) embObj;
  double[] vector = new double[embedding.size()];
  for (int j = 0; j < embedding.size(); j++) { ... vector[j] = ...; }
  ```
- **严重程度**: P3
- **并发场景**: 单请求回调链内瞬态；RAG 批量导入场景下并发 embedding 请求叠加峰值。
- **触发时序**: T1：`embedAllAsync(docs)` 把全部 docs 文本作为单请求 `input` 下发（L290-293），批次大小由调用方决定、无分批强制；T2：响应 = N 条 × D 维浮点的 JSON 文本（每数 ~14-20 字节）→ `parseMap` 产出全量 boxed 树（每 `Double` 对象 ~16-24 字节 + 引用开销，JSON 文本 ≈ 2×，boxed 树 ≈ 4-6× 纯 `double[]` 体积）→ L418 起逐条转 `double[]`，期间 `body`/`data`/`sorted`/boxed 树全程可达 → 峰值 ≈ (JSON 文本 + boxed 树 + double[]) 三重叠加 ≈ 纯向量体积的 5-8×。
- **现状**: 转换结果正确（index 排序、数量校验齐备），纯内存放大问题；无批量上限声明。
- **风险**: 大批量 embedding（全库导入，10 万 chunk × 1024 维）时单请求峰值可达数 GB 级；无分批防线。
- **建议**: ①`embedAllAsync` 增加批次文本总量上限（复用 owner doc 声明的 `maxContextTokens` 风格配置）；②解析侧改流式（Jackson streaming 按 `data[]` 逐条读）或将 `sorted` 排序改为索引排序避免整表拷贝；③至少在转换循环后及时释放 `data`/`sorted` 引用（置 null 不彻底，根治靠流式）。
- **失效面枚举**: ①`EmbedCall.parseSuccess`（L391-437）——本发现；②`EmbedCall.buildRequest`（L283-295）——请求体全量序列化（transient，有界于输入文本总量，随①包络声明）；③`ChatServiceImpl.StreamAggregator`（同模块同类放大）——已单列 C5-001；④五方言 `parseResponse` 全响应 Map——transient、上界=HTTP body，随 C5-001 失效面⑦口径。
- **信心水平**: 确定
- **误报排除**: 「JSON 解析必然有中间物化」豁免不适用——豁免针对的是无法避免的瞬态解析，本发现对象是装箱树存活期覆盖整个转换循环的可量化放大（方法论 C5 步骤 3 明列「装箱放大倍数」为审计对象）。
- **复核状态**: 保留(P3)｜新发现——回码核验成立：parseSuccess parseMap 全量 boxed 树 + 逐元素拆箱 double[]（原始树全程可达）直核；转换正确（排序/校验齐备）、纯放大定性一致，P3 校准成立

### [C5-006] 系统提示词缓存无容量配置且键空间随 workMode 无界扩展

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/prompt/DefaultSystemPromptLoader.java:11-14, 20-49, 55-61`
- **证据片段**:
  ```java
  // L13-14：第三参 null = 无 CacheConfig 容量配置；键 = workMode 拼接路径
  private final ResourceLoadingCache<String> promptCache = new ResourceLoadingCache<>("system-prompt-cache",
          this::loadPrompt, null);
  // L55-57：键路径由调用方 workMode 字符串直接构造
  protected String buildPromptPath(String workMode, String fileName) {
      return "/nop/ai/prompts/system/" + workMode + "/" + fileName + ".md";
  }
  ```
- **严重程度**: P3
- **并发场景**: 多请求线程共享单例 `_instance` 的 `promptCache`（ResourceLoadingCache 平台实现，线程安全）；无上界容量的键空间增长是跨请求累积面。
- **触发时序**: T1：调用方（`AiCommand.addSystemPrompt` L400-401，workMode 来自 task-xml/脚本）以变化字符串反复调用 `loadSystemPrompt(workMode,...)`；T2：每个新 workMode → 新缓存键（3 个后缀 × 每 mode）→ 缓存条目只增不删（无容量/无淘汰配置）→ 每键驻留整段 prompt 文本（命中时为全文，未命中为 null/异常占位，取决于 ResourceLoadingCache 负缓存语义）→ 键空间无上界增长。
- **现状**: workMode 在常规部署中来自有限配置集（工程上有界），故 P3；但代码层面无键合法性校验与容量防线，键空间对调用方开放。
- **风险**: workMode 被外部输入影响的集成（脚本透传用户输入）下缓存退化为无界键值表；每键为全量 prompt 文本，放大明显。
- **建议**: ①workMode 入参白名单校验（对齐 `ChatLogHelper.SAFE_SESSION_ID` 的 allow-list 模式）；②`promptCache` 传入容量配置（`CacheConfig` 上限 + LRU）；③或缓存键先归一化（workMode → 枚举）。
- **失效面枚举**: ①`DefaultSystemPromptLoader.promptCache`（L13-14）——本发现；②`loadSystemPrompt` StringBuilder（L25-48）——有界于 3 段 prompt 之和，不报；③`PromptTemplateManager`/RCM 模板缓存——平台所有，不重审；④`LlmConfigHelper.secretCache`（L42）——键=provider 且 provider 先经 loadConfig 校验，有界，不报。
- **信心水平**: 很可能（键空间可扩展性确定；实际增长速率取决于 workMode 来源是否可被外部输入影响）
- **误报排除**: 「低频管理路径」豁免不适用——`loadSystemPrompt` 在每次启用了 work-mode 系统提示的 LLM 调用主路径上；非「synchronized 粗粒度」类问题。
- **复核状态**: 保留(P3)｜新发现——回码核验成立：promptCache 第三参 null（无 CacheConfig 容量）直核、buildPromptPath workMode 未消毒直拼直核；workMode 常规为有限配置集故 P3，键空间对调用方开放定性成立

### [C5-007] AiXDefHelper xdef:ref 循环引用无环守卫：无限互递归 + 每层全文件重解析直至 StackOverflowError

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/api/xdef/AiXDefHelper.java:97-105, 144-161`
- **证据片段**:
  ```java
  // L101-104：每次调用全量重解析（无按路径缓存），并新建 per-call MapCache
  public static XNode loadXDefForAi(String path, boolean keepXDefAttr) {
      IResource resource = VirtualFileSystem.instance().getResource(path);
      XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
      return transformForAi(node, keepXDefAttr, new MapCache<>());
  }
  // L145-148：xdef:ref 直接递归 loadXDefForAi，无 visited 集
  String refPath = defNode.attrText("xdef:ref");
  if (refPath != null && refPath.endsWith(".xdef")) {
      ...
      XNode refNode = loadXDefForAi(refPath, removeXDefAttr);
  ```
- **严重程度**: P3
- **并发场景**: 单线程递归（模型加载/模板解析线程）；与其他加载请求无共享可变状态（`MapCache` per-call 新建）。
- **触发时序**: T1：加载引用了 `loadXDefForAi` 的路径（prompt 模板 `{{include:xxx.xdef}}` 经 `PromptSyntaxParser.handleIncludeExpr` L261-263 的 `.xdef` 分支——注意该分支在 collectDepends 包装之外，平台 `ResourceDependsStack` 深度守卫不覆盖；或 MCP/coder 的 xdef 文档转换入口）；T2：a.xdef 含 `xdef:ref=b.xdef`、b.xdef 反向 `xdef:ref=a.xdef`（或 a 自引用）→ `resolveXDefRef` L148 递归 `loadXDefForAi` → `DslNodeLoader.loadFromResource`（nop-xlang，已核实 L35-44 无按路径缓存，每次全量 XNode 解析 + xtend）→ 无限互递归，每层分配完整文件树 → 栈与堆同步增长直至 `StackOverflowError`，加载线程死亡。
- **现状**: 递归无 visited-set、无深度上限；循环 xdef:ref 属配置缺陷输入，但 fail 方式为 SOE 而非可诊断错误。
- **风险**: 配置错误（两个互相 ref 的 xdef）导致首次加载线程 SOE；在延迟加载部署下可由首个命中请求触发，表现为不可解释的线程死亡；SOE 对调用链是 `Error`，可能跳过局部 finally 语义外的清理。
- **建议**: ①`loadXDefForAi` 增加线程级 loading-stack（对齐 `ResourceDependsStack` 模式）检测环并 fail-fast 报 `ERR_AI_XDEF_REF_CYCLE`；②或对 `.xdef` include 分支也纳入 `collectDepends` 包装借用平台深度守卫；③短期最低成本：递归深度计数器 + 上限。
- **失效面枚举**: ①`AiXDefHelper.resolveXDefRef`（L144-161）——本发现；②`AiXDefHelper.loadXDefForAi`（L97-105）——无缓存重解析放大层，同修；③`transformForAi`（L108-142）——递归但树有界（树上深度优先、无环可能——XNode 树不存在父子环），不报；④`PromptSyntaxParser.handleIncludeExpr` 的 `.md/.json/.yaml` 分支（L264-271）——模板 include 环已被平台 `ResourceDependsStack.push` 深度上限（`CFG_RESOURCE_MAX_DEPS_STACK_SIZE`，fail-fast `ERR_RESOURCE_EXCEED_MAX_DEPS_STACK_SIZE`）覆盖，不报；⑤`FileContents.fromText`/`fromNode`（FileContents.java L21-36）——树遍历有界，不报。
- **信心水平**: 确定（代码路径无歧义；`DslNodeLoader` 无按路径缓存已核实源码）
- **误报排除**: 不以「平台已有环检测」为由误排——已核实 `ResourceDependsStack` 守卫仅作用于 currentDepends() 非空的 collectDepends 包装内，而本递归全程在 AiXDefHelper 自有帧内、且 `.xdef` include 分支在包装之外；不属于「平台设施自身不重审」——缺陷主体 `resolveXDefRef` 是本模块代码。
- **复核状态**: 保留(P3)｜新发现——回码核验成立：loadXDefForAi 每次全量重解析 + per-call MapCache 直核、resolveXDefRef 递归无 visited 集直核；循环 ref 属配置缺陷输入但 SOE 失败方式不可诊断，P3 成立

### [C5-008] Checkpoint journal 恢复路径全量物化（~4× 放大）+ byWatermark 全量常驻 + journal 磁盘无界（F4/F7）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointJournalReader.java:59-68, 86-120, 122-147；reliability/FileBackedCheckpointManager.java:80-81, 101-105, 298-326；reliability/CheckpointJournalWriter.java:33-36, 53-73`
- **证据片段**:
  ```java
  // CheckpointJournalReader L116 / L127-128：整文件读入 → substring 全量副本 → split 全量分段
  return Files.readString(journalFile, StandardCharsets.UTF_8);
  ...
  String body = content.substring(headerEnd);
  String[] sections = body.split("(?=" + Pattern.quote(SECTION_DELIMITER) + ")");
  // FileBackedCheckpointManager L298-302：注释明言无条件全量装载
  // Always load all journal entries so the byWatermark index is complete
  List<Checkpoint> checkpoints = new ArrayList<>(journalReader.readAll(journalFile));
  // L308-310：全部 checkpoint 放入 byWatermark 常驻（audit/debug 能力保留是显式决定）
  for (Checkpoint cp : checkpoints) { byWatermark.put(cp.getWatermark(), cp); }
  // L80-81：javadoc 自认磁盘面无界
  // <b>Retention</b>: the journal grows unbounded (no rotation/cap). At test scale this is safe
  ```
- **严重程度**: P1
- **并发场景**: 行为性资源界缺陷（非竞态）。装载发生在任意线程对该 session 的首次访问（`ensureSessionLoaded` L279-283 `loadedSessions.putIfAbsent` 一次性）；多会话并发首访时峰值按会话数线性叠加；`CheckpointJournalWriter.ioLock` 只串行化单文件追加。
- **触发时序**: T1：进程重启（或 `remove` 后再访问）后，resume/heartbeat/`getLatestCheckpoint` 任一操作首触某 session → `loadSessionFromDisk` → `readAll`：①`Files.readString` 物化整文件（journal 字节数 J）；②`substring(headerEnd)` 再复制 ≈J；③`split` 产出全部段落 `String[]`（≈J，与结果列表同时可达）；④每段再 `split("\n",-1)` + `LinkedHashMap` 字段（≈J）→ 装载峰值 ≈ 4-5×J。装载完成后 `byWatermark` 全量 `Checkpoint` 常驻至会话终态 `remove`——每条 checkpoint 携带 `inputSummary`（原始 `argumentsText`，未截断，`AgentToolDispatcher.java:828`）与 `outputSummary`（spill/截断后行内文本 ≤8k chars，`AgentToolDispatcher.java:770, 829`；LLM_TURN 侧 8k 截断见 `ReActAgentExecutor.java:1111-1115`），UTF-16 下单条 ≈ 32KB 级。T2：`appendCheckpoint` 只 APPEND 不轮转（Writer L66-67），journal 跨执行无限增长——长时会话 K 次执行 × 每轮 ≤maxIterations×(1 LLM_TURN + N 工具 TOOL_EXECUTION + DISPATCHED/EXECUTING phase 条目) → 数千段 × ~16KB ≈ 数十 MB journal；1000 段 journal 的单会话装载峰值 ≈ 64-80MB，`byWatermark` 驻留 ≈ 数十 MB。
- **现状**: 磁盘无界已在实现 javadoc 自认（"no rotation/cap... non-blocking follow-up"），但 owner doc（`docs-for-ai/03-modules/nop-ai.md`）无任何 journal 容量/轮转包络声明——按「守卫缺失、包络未声明」口径保留；内存侧的「全量装载 + byWatermark 全量保留」是实现注释里的显式设计决定（L298-301, L305-307），但同样无规模包络声明与上限防线。
- **风险**: 长时运行部署中 journal 无限增长（F4），每次进程重启后全量会话首访触发 4-5× 峰值物化（F7 OOM 同族）；多会话叠加可击穿堆预算；`readAfter`（L86-109）虽为快照加速恢复设计，但仍是先全量 `parseAll` 再 `subList`，且 L108 `unmodifiableList(all.subList(...))` 视图持有完整 `all` 底层列表（增量读并不省内存）。
- **建议**: ①`CheckpointJournalReader` 改流式逐段解析（`BufferedReader` 按 `## CP-` 边界迭代），`readAll` 语义保留但峰值降为单段；②`FileBackedCheckpointManager` 增加 journal 轮转/归档（按 seq 或字节阈值切割，复用平台 `io.nop.commons.cache` 风格容量配置）并在 owner doc 声明包络；③`byWatermark` 改为有界 LRU（`CacheConfig`）或仅保留 COMPACTION 之后的活动集（audit 全历史走磁盘流式查）；④`readAfter` 返回拷贝而非 subList 视图；⑤为 `inputSummary` 补 `ToolResultTruncator.truncate` 截断。
- **失效面枚举**: ①`CheckpointJournalReader.readAll`（L59-68）——本发现主对象；②`CheckpointJournalReader.readTextIfExists`（L111-120）——整读站点（C5b 命中行 L116），同修；③`CheckpointJournalReader.parseAll`（L122-147）——4× 瞬态放大，同修；④`CheckpointJournalReader.readAfter`（L86-109）——全解析 + subList 持底，同修；⑤`CheckpointSnapshotReader.readIfExists`（L39-53，L48 readString）——snapshot.json 仅含元数字段（snapshotId/watermark/counts），单文件有界，随观察不报；⑥`FileBackedCheckpointManager.loadSessionFromDisk`（L285-326）——全量装载调用点，同修；⑦`byWatermark`/`bySession`/`snapshotCache`/`saveCounters`/`loadedSessions`（L101-105）——常驻面（终态 `remove` L201-218 已实现，暂停/废弃会话不清理部分并入 C5-013 口径）；⑧`CheckpointJournalWriter.appendCheckpoint`（L53-73）——append-only 写侧无轮转，同修；⑨`DBCheckpointManager.loadSessionFromDb`（L349-376）——同族 DB 装载（SELECT 全行入内存 + `byWatermark.putIfAbsent` 全量），与 DB 侧常驻问题独立登记为 C5-009；⑩`CompactionAwareTruncation.truncateToLatestCompaction`（L70-89）——1× 列表副本，随⑥口径。
- **信心水平**: 确定
- **误报排除**: 「有界声明存在的缓存」豁免不适用——javadoc 自认无界且 owner doc 无包络声明；「单线程不豁免」——内存有界是行为性缺陷，与线程模型无关；「snapshot.json 整读」不报是因为其内容被 `CheckpointSnapshotWriter` 契约限定为固定元数字段（有界），与 journal 的累积性正文有本质区别。
- **复核状态**: 保留(P1)｜新发现——回码核验成立：readString 整读 + substring/split 全量分段（≈4× 瞬态）直核、loadSessionFromDisk 注释明言 Always load all journal entries + byWatermark 全量 put 直核、Writer append-only 直核、javadoc 自认 grows unbounded (no rotation/cap) 直核——磁盘/内存双无界面闭合

### [C5-009] DBCheckpointManager 未覆写 remove()——引擎终态清理调用落空，三张会话键缓存跨会话永久累积（F3 修实例不修类别）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java:93-95, 206-210, 349-376；reliability/ICheckpointManager.java:96-115；engine/AgentSessionLifecycle.java:418-424, 578-583, 978-983；engine/DefaultAgentEngine.java:546, 1055-1060`
- **证据片段**:
  ```java
  // DBCheckpointManager L93-95：与 FileBacked 同构的三张缓存（该文件 grep 无任何 remove 覆写）
  private final ConcurrentHashMap<String, Checkpoint> byWatermark = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<Checkpoint>> bySession = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> loadedSessions = new ConcurrentHashMap<>();
  // ICheckpointManager L108-115：remove 的 default 实现是 no-op
  // The default implementation is a no-op, so existing implementations
  // (NoOp, DB) and future stubs continue to compile and behave identically.
  // engine 终态清理调用点（AgentSessionLifecycle L418 等 9 处）：
  config.getCheckpointManager().remove(sessionId);
  ```
- **严重程度**: P2
- **并发场景**: singleton 管理器被多请求线程并发读写三张 `ConcurrentHashMap`；缺陷面是跨会话生命周期累积（与线程模型无关的行为性资源缺陷）。
- **触发时序**: T1：任一会话到达终态（completed/failed/cancelled/...）→ 引擎清理路径调用 `config.getCheckpointManager().remove(sessionId)`（9 个调用点）→ `DBCheckpointManager` 未覆写，落到 `ICheckpointManager` 的 default no-op → 三张缓存中的该会话条目原样保留。T2：长时运行处理数千会话 → `saveCheckpoint` L206/209-210 与 `loadSessionFromDb` L361/376 持续 put → `byWatermark`（每会话全部 checkpoint，各含 ≤8k 字符摘要串）+ `bySession` + `loadedSessions` 单调增长永不回收 → 堆缓慢耗尽。
- **现状**: Plan 278（AR-10「清理无界缓存」）给接口补了 `remove` 契约并明确说「so the cache does not grow unbounded across long-running deployments」，且三个内存态兄弟中两个已实现（`ToolExecutionCheckpoint.remove` L90-98、`FileBackedCheckpointManager.remove` L201-218），唯独 DB 实现漏修——教科书式 F3 同族漏修。附带：`loadSessionFromDb` 注释明言「The ai_agent_checkpoint table rows are never deleted」（L369-370 附近）——DB 面同样只增不删（F4，无 TTL/归档）。
- **风险**: 选用 `DBCheckpointManager`（跨进程持久需要时的显式选择）的部署中，进程生命周期内每个触达过的会话的全部 checkpoint 对象永久驻留；1000 会话 × 每会话数百条 × 32KB 级摘要串可达 GB 级。DB 表同步无界增长拖慢 `loadSessionRowsFromDb` 全行装载。
- **建议**: ①`DBCheckpointManager` 覆写 `remove(sessionId)`：按 `bySession.get(sessionId)` 逐 watermark 清 `byWatermark` 并移除三张表条目（对齐 `FileBackedCheckpointManager.remove` L207-217 逐条清理模式）；②为 `ai_agent_checkpoint` 表补基于终态会话/时间阈值的清理任务（可挂入 `ScheduledRecoveryManager` 既有调度节拍）或至少 owner doc 声明运维清理边界；③给 `ICheckpointManager.remove` 的 javadoc 契约加「覆写义务」提示，防后续新实现再漏。
- **失效面枚举**: ①`DBCheckpointManager`（缺 remove）——本发现主对象；②`ToolExecutionCheckpoint.remove`（L90-98）——已实现，对照组；③`FileBackedCheckpointManager.remove`（L201-218）——已实现，对照组；④`NoOpCheckpoint`——无状态，default no-op 语义正确，不报；⑤engine 9 个 `remove` 调用点（AgentSessionLifecycle L418/424/578/583/978/983、DefaultAgentEngine L546/1055/1060）——调用侧无缺陷，属被落空的契约方；⑥`ai_agent_checkpoint` 表 append-only——DB 面 F4，随①同修；⑦`DBSessionStore.sessions` 写穿缓存（session 包，X3 分区）——同族会话键无界缓存，此处仅标注不重复报告。
- **信心水平**: 确定
- **误报排除**: 「低频管理路径」豁免不适用——`saveCheckpoint` 在每次工具执行/每 LLM 轮主路径上被调用；「接口 default no-op 是向后兼容设计」不成立为豁免——契约文本自身声明了防无界增长意图，漏实现使该意图对 DB 后端失效；不属于「平台设施自身不重审」——`DBCheckpointManager` 是本模块代码。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：DBCheckpointManager 全类无 remove 覆写（方法面直核）、ICheckpointManager default no-op 及「so the cache does not grow unbounded」契约文本直核、引擎 9 个 remove 调用点逐一命中；缺省装配为 NoOpCheckpoint（DefaultAgentEngineConfig:125）故按非缺省面校准 P2——同站点 C7-013(P1)/C1-011(P2) 判级差留 7.1 归并裁定

### [C5-010] 压缩快照档案全量累积：每次 compaction 将全量历史 List.copyOf 入无上界 per-session HashMap，永不删除（F4/F7）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCompactionCoordinator.java:57-63, 90-106；session/InSessionCompactionSnapshotArchive.java:38, 47-57；session/AgentSession.java:499-506, 540-556`
- **证据片段**:
  ```java
  // AgentCompactionCoordinator L91-93：每次真压缩前归档全量历史
  if (snapshotArchive != null && !ctx.getMessages().isEmpty()) {
      snapshotId = snapshotArchive.put(ctx.getMessages());
  }
  // L98：CompactionContext 再持一份全量拷贝（瞬态）
  CompactionContext compactCtx = new CompactionContext(new ArrayList<>(ctx.getMessages()), ...);
  // InSessionCompactionSnapshotArchive L38 / L55：HashMap 无上限、防御性全拷贝、无删除方法
  private final Map<String, List<ChatMessage>> store = new HashMap<>();
  store.put(snapshotId, List.copyOf(messages));
  ```
- **严重程度**: P1
- **并发场景**: 写侧在 ReAct 执行线程（同会话被 `runningExecutions` 单槽守卫串行化）；档案挂在 `DBSessionStore`/`InMemorySessionStore` 缓存锚定的同一 `AgentSession` 实例上（`getOrCreateCompactionSnapshotArchive` 惰性装配，L540-556），跨压缩调用持续累积；多会话并发各自独立累积、峰值线性叠加。
- **触发时序**: T1：长时会话运行中，每轮结束 `shouldTriggerCompaction`（L57-63）判定 `tokensUsed > 80%×maxContextTokens`（engine 默认 128000，`ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS` L141）或 `messages > 30` → `performCompaction`；T2：每次真压缩把当时全量消息历史 `List.copyOf` 进 archive 的 `HashMap`（无容量、无淘汰、无 delete API）→ 压缩 K 次后档案驻留 ΣK 份历史副本；上下文每份 ≈ 128k tokens（≈0.5-1MB 文本，UTF-16 ×2）→ K≈百次的长会话单会话驻留可达数百 MB，且压缩越频繁（消息数>30 触发线很低）K 增长越快。
- **现状**: 归档是 design §8.3 Decision B/E 的显式可回溯性设计（「failed compaction leaves the original verifiably intact under snapshotId」），但 owner doc 与实现均未声明任何档案容量包络/清理策略；档案无删除 API，`read-ref`/`read-snapshot` 之外无任何回收路径；会话终态引擎只清 checkpointManager（且见 C5-009 对 DB 后端落空），不清 session 对象上的档案。
- **风险**: 防压缩 OOM 的机制自身成为 OOM 源：历史每压缩一次即固化一份副本，长会话 + 高频压缩触发线下驻留无限增长；与 C5-008 的 journal 常驻、C5-011 的 spill 驻留三者对同一长会话叠加。
- **建议**: ①`InSessionCompactionSnapshotArchive` 增加容量上界（保留最近 N 份 / 字节预算，超界淘汰最旧——对齐平台 `CacheConfig` LRU 语义），或改为落盘引用式归档；②归档内容降为压缩差量或摘要引用而非全量副本；③会话终态清理路径（`DefaultAgentEngine` finally / `AgentSessionLifecycle` L418 同族）增加 session 内档案释放挂钩；④在 owner doc 声明档案包络。
- **失效面枚举**: ①`InSessionCompactionSnapshotArchive.store/put`（L38, L47-57）——本发现主对象；②`AgentCompactionCoordinator.performCompaction` 归档调用点（L90-93）——写侧同修；③`CompactionContext` 的 `new ArrayList<>(ctx.getMessages())`（L98）——瞬态 1× 拷贝，随②口径不单列；④`InSessionCompactionArchive.store`（compact 包，内容寻址 SHA-256 + `putIfAbsent` 去重，L36-47）——同族参考式档案，去重使键空间收敛于不同摘要内容、增长慢于①，登记观察不单列；⑤`resolveArchive`/`resolveSnapshotArchive`（L257-288）——装配路径无缺陷；⑥`ToolResultTruncator.applyBudget`（compact 包）——有界截断，实现正确，不报。
- **信心水平**: 确定（写侧调用链与 store 无界性均已读实；`DBSessionStore` 缓存锚定同实例语义已核实——sessions map write-through cache 返回同一 AgentSession）
- **误报排除**: 「归档是有意设计」不成立为豁免——发现对象是容量上界与清理路径缺失（方法论 C5 步骤 4：有上限缓存需淘汰策略，此物连上限都没有），不是归档行为本身；「单会话有界（历史被压缩截断）」不成立——被截断的是 ctx 活动历史，档案副本不随压缩收缩。
- **复核状态**: 保留(P1)｜新发现——回码核验成立：store 裸 HashMap + put List.copyOf 全拷贝 + 全文件无 delete/remove/clear 直核、触发线常量（0.8×128000 tokens / 30 messages，ReActAgentExecutor:139-141）直核、终态路径仅 checkpointManager.remove + sessionStore.save（save 反向 pin 驻留）直核——压缩即固化全量历史副本，长会话确定性增长成立

### [C5-011] 超大工具结果 spill 驻留：全文本写入无上界 per-session InMemorySpillStore 且读后不删，预算防线只约束行内预览

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:1039-1070；compact/InMemorySpillStore.java:26-53, 60-65；session/AgentSession.java:499-506；tool/ReadSpillExecutor.java:100-120`
- **证据片段**:
  ```java
  // AgentToolDispatcher L1055-1060：超预算全文本 PUT，行内仅留有界预览
  String spillId = session.getOrCreateSpillStore().put(resultText);
  String preview = ToolResultTruncator.applyBudget(resultText, maxChars);
  return preview + "\n\n[SPILL_REF id=" + spillId + "] ...";
  // InMemorySpillStore L27 / L51：普通 HashMap，无容量/淘汰
  private final Map<String, String> store = new HashMap<>();
  store.put(spillId, content);
  ```
- **严重程度**: P2
- **并发场景**: 写侧 ReAct 执行线程（同会话单槽串行）；store 挂在缓存锚定的 `AgentSession` 实例上，跨执行累积；多会话并发各自叠加。
- **触发时序**: T1：工具返回超预算文本（默认阈值 8000 chars，`ToolOutputBudgetResolver` 解析 per-call/per-tool 覆盖）→ `spillIfOversized` 把全文本 `put` 进 `InMemorySpillStore`，行内只保留 ≤maxChars 预览 + `[SPILL_REF]`；T2：agent 每轮用 `read-spill` 读回（`ReadSpillExecutor` L109 取同一 store）——读侧 grep 全文件无 `delete/remove` 调用，条目永久驻留 → 长会话内每次超大工具输出（文件读取/命令输出/网页抓取）的全文本都在堆内累积，ctx 行内有界性是预览层面具，堆层面无界。
- **现状**: design §3.3 声明了 spill 语义与 put 失败回退（L1061-1065 降级为截断），但未声明 store 容量包络；`InMemorySpillStore` javadoc 自述「Lives for the duration of a single AgentSession」——会话级生命周期有界，但会话内无任何条目回收路径（唯一删除入口 `delete` L60-65 无生产调用方）。
- **风险**: 工具输出密集型会话（代码库检索、日志分析）单会话堆驻留随超预算输出条数线性增长；`read-spill` 读完即弃的使用模式完全不复收内存；与 C5-008/C5-010 同会话叠加。
- **建议**: ①`read-spill` 读后默认 `delete`（或提供 TTL/引用计数）；②`InMemorySpillStore` 增加容量上界（条数/字节，超界拒绝 put 走既有截断回退——回退路径已存在，只需让容量检查触发它）；③会话终态清理挂钩释放 store；④owner doc 声明包络。
- **失效面枚举**: ①`InMemorySpillStore.put/store`（L46-53, L27）——本发现主对象；②`AgentToolDispatcher.spillIfOversized`（L1039-1070）——写侧调用点，同修；③`ReadSpillExecutor`（L109 取 store 无删除）——读侧同修；④`InMemorySpillStore.delete`（L60-65）——已有 API 无调用方，修复即接线；⑤`ToolResultTruncator.applyBudget`/`truncate`——截断路径有界正确，不报；⑥`NON_TRUNCATABLE_TOOLS`（ask-oracle/ask-human）全文本行内驻留 ctx——design §3.3 已声明豁免（「exempt, full text stays inline」），按已声明边界不报；⑦`ToolOutputBudgetResolver`——配置解析有界，不报。
- **信心水平**: 确定
- **误报排除**: 「spill 就是防 OOM 的机制」不成立为豁免——机制把无界性从 ctx 行内转移到 store，行内预算不等于堆有界；「会话结束即 GC」只覆盖会话终止后的回收，不覆盖长会话内的无界增长（本仓库 agent 会话以 resume 长跑为一等场景，engine 的 checkpoint/journal 设计即为长会话服务）。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：全文本 put + 行内 applyBudget 预览直核、store 无容量 HashMap 直核、delete API 全模块 grep 零生产调用方、ReadSpillExecutor 仅 store.get 无删除直核——读后不弃、会话内只增成立

### [C5-012] engine 会话邮箱双表 put-only：sessionMailboxes / sessionMailboxSubscriptions 无任何生命周期清理（F4）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionSupport.java:31-32, 52-70, 113；engine/DefaultAgentEngine.java:329-330`
- **证据片段**:
  ```java
  // AgentSessionSupport L31-32：两张会话键表（引擎生命周期 = singleton bean 生命周期）
  private final java.util.concurrent.ConcurrentHashMap<String, IMailbox> sessionMailboxes;
  private final java.util.concurrent.ConcurrentHashMap<String, IMessageSubscription> sessionMailboxSubscriptions;
  // L56 / L67：仅有的两个写入口，put 后再无 remove（全模块 grep 无 sessionMailboxes.remove）
  sessionMailboxes.computeIfAbsent(sessionId, sid -> { ... });
  sessionMailboxSubscriptions.put(sid, subscription);
  ```
- **严重程度**: P2
- **并发场景**: 多请求线程经 `computeIfAbsent` 并发创建（原子）；缺陷面是跨会话累积——`DefaultAgentEngine` L329-330 构造时 `new ConcurrentHashMap<>()`，与 singleton 引擎同生命周期。
- **触发时序**: T1：每个会话首次执行/resume 时 `ensureSessionMailbox` 创建 mailbox（工厂默认 `DeferredAckMailbox`——内部 `ArrayDeque pending` 无界，message 包 X3 分区）并注册 subscription；T2：会话到达终态后，引擎终态清理只清 `runningExecutions`/`runningInjectionTargets`/checkpointManager（L546 等）——grep 全模块无任何 `sessionMailboxes.remove`/`sessionMailboxSubscriptions.remove` 调用 → 每个历史会话的 mailbox 对象（含未消费 pending 队列与订阅句柄）永久驻留。
- **现状**: 引擎对其他会话键状态（`runningExecutions`、`runningInjectionTargets`——注释明言「removed symmetrically in the terminal finally」）都执行了终态对称清理，唯独邮箱双表遗漏；actor 侧的 registry 有 `destroyActor`→`unregister` 对称路径，邮箱表没有对应物。
- **风险**: 长时运行部署中表大小 = 历史会话总数，每条目挂一个 mailbox（含无界 pending 队列，投递未消费即滞留）+ 订阅实现对象；高会话流失率场景（每请求一会话）退化为按请求泄漏。
- **建议**: ①会话终态清理路径（`DefaultAgentEngine` finally 与 `AgentSessionLifecycle` 终态分支）增加 `sessionSupport.removeSessionMailbox(sessionId)`：关闭 subscription、清理 pending 后移除双表条目；②或为双表接入平台 `GlobalCacheRegistry`/`CacheConfig` 有界缓存；③`DeferredAckMailbox` 侧补 pending 上限（`QueueOverflowPolicy` 平台设施）作为纵深防御。
- **失效面枚举**: ①`AgentSessionSupport.sessionMailboxes`（L31, L56）——本发现；②`AgentSessionSupport.sessionMailboxSubscriptions`（L32, L67）——同族同修；③`AgentSessionSupport.getSessionMailbox`（L113）——只读无缺陷；④`InMemoryActorRuntime.executors`（runtime 包，L133）——同族会话/actor 键表但有 destroy 清理路径，FAILED 滞留子案登记为 CL-046；⑤`DeferredAckMailbox.pending`（message 包，X3 分区）——同族无界队列，此处仅标注不重复报告；⑥`runningExecutions`/`runningInjectionTargets`（engine L121/129）——终态对称清理已实现，对照组不报。
- **信心水平**: 确定
- **误报排除**: 「mailbox 数量 = 活跃会话数是有界」不成立——表从不随会话终结收缩，驻留的是历史全会话；「subscription 自身关闭即释放」不成立——map 条目与 mailbox 对象仍被强引用；不属于「平台设施自身不重审」——双表是本模块引擎自有字段。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：双表仅有 computeIfAbsent/put 两个写入口（全文件无 remove）直核、DefaultAgentEngine:329-330 构造直核、终态清理清单（checkpointManager+runningExecutions+runningInjectionTargets）不含双表直核——同站点 C3-016(P1) 双登记留 7.1 并单

### [C5-013] 会话键安全/可靠状态图无终态清扫族：四张 singleton ConcurrentHashMap 仅 resume 路径有 reset，终态/废弃会话条目永驻（F4/F8）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultDenialLedger.java:48, 76, 105-110；security/FingerprintPostDenialGuard.java:39, 81, 85-90；reliability/DefaultWaitCoordinator.java:47, 83-96, 115-123；reliability/SessionGoalTracker.java:60, 97-117；engine/AgentSessionLifecycle.java:316-331`
- **证据片段**:
  ```java
  // DefaultDenialLedger L48 / FingerprintPostDenialGuard L39：singleton 会话键表
  private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Set<String>> deniedFingerprints = new ConcurrentHashMap<>();
  // DefaultWaitCoordinator L47 / SessionGoalTracker L60：同族，且两者连 reset 方法都没有
  private final ConcurrentMap<String, WaitState> sessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, SessionState> entries = new ConcurrentHashMap<>();
  // 唯一清理调用点 = resumeSession（AgentSessionLifecycle L322/331）
  config.getDenialLedger().reset(sessionId);
  config.getPostDenialGuard().reset(sessionId);
  ```
- **严重程度**: P3
- **并发场景**: 四张表均在 singleton 安全/可靠组件上被多请求线程并发读写（实现线程安全正确）；缺陷面是跨会话生命周期累积。
- **触发时序**: T1：会话产生拒绝记录/指纹/等待注册/工具签名 → 各表 `computeIfAbsent/put` 建条目；T2：会话终态时引擎清理清单不含这四张表（终态清理仅 checkpointManager + runningExecutions + runningInjectionTargets），`reset(sessionId)` 的全部调用点只有 resumeSession（暂停恢复）一处 → 被放弃/已完结会话的条目永久驻留；`DefaultWaitCoordinator` 额外面：`checkWait` 消费条件只置 `state.condition = null`（L106-107），`WaitState` 壳与 `wakePayload`（任意 Object，可含大负载）仍留表中，且类无 remove API。`SessionGoalTracker` 同理窗口有界（windowSize=5）但会话键条目永驻。
- **现状**: 单条目体积小（AtomicInteger / ≤N×32-hex 指纹 / WaitState 壳 / 5 条签名），故 P3；但键空间 = 历史全会话数，与 C5-009/C5-012 同为「会话键容器缺终态清理」族——该族在引擎清理路径上应有一次性的统一挂钩。
- **风险**: 长时运行按会话总数缓慢泄漏；`DefaultWaitCoordinator.wakePayload` 若被注入方放大（如携带工具输出引用）则单条目体积不可控。
- **建议**: ①在引擎终态清理路径增加统一的安全/可靠状态释放挂钩（`denialLedger.reset` + `postDenialGuard.reset` + waitCoordinator/goalTracker 新增 remove），与既有 checkpointManager.remove 并列；②`DefaultWaitCoordinator.deliverWake/checkWait` 消费后移除整条 `WaitState`；③wakePayload 消费后置 null。
- **失效面枚举**: ①`DefaultDenialLedger.counts`（L48）——本发现（有 reset，调用面缺）；②`FingerprintPostDenialGuard.deniedFingerprints`（L39）——同族同修；③`DefaultWaitCoordinator.sessions`（L47）——同族且无 remove API，最重子案；④`SessionGoalTracker.entries`（L60）——同族无 remove；⑤`DBDenialLedger`（DB 面，L139-198 append-only INSERT、reset DELETE 仅 resume 路径 L216-238）——同族 DB 面登记为 CL-049；⑥`ThresholdBreaker.entries`（nop-ai-core，X1 批次已按 prone-shrink 有界口径不报）——对照组；⑦`InMemoryActorRegistry` 双索引——有 `unregister` 对称路径，对照组不报。
- **信心水平**: 确定（表无终态清理已 grep 全模块核实；实际增长速率取决于会话流失率，条目体积小故 P3）
- **误报排除**: 「entries 小就无害」不作为误报理由——登记为 P3 而非不报，因其与 C5-009/C5-012 构成同一清理缺口族，修复应一次收口；「NoOp 实现是默认」不适用——`DefaultDenialLedger`/`FingerprintPostDenialGuard`/`DefaultWaitCoordinator` 均标注 `@SecureDefault` 为出厂默认。
- **复核状态**: 保留(P3)｜新发现——回码核验成立：DenialLedger/PostDenialGuard 的 reset 唯一调用点为 resumeSession（Lifecycle:322/331 直核）、WaitCoordinator/GoalTracker 全类无 reset/remove API（grep 零命中）直核；单条目小故 P3 与族内校准一致

### [C5-014] AgentActor.receivedMessages 观测日志无界增长 + 快照读全量拷贝（F4）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/AgentActor.java:89, 195-208；runtime/InMemoryActorRuntime.java:445, 498-504`
- **证据片段**:
  ```java
  // AgentActor L89：消费日志只增不删，随 actor 存活期累积
  private final List<MailboxEntry> receivedMessages = Collections.synchronizedList(new ArrayList<>());
  // L195-197：每条 poll 消息必追加（L445 消费循环调用）
  public void addReceivedMessage(MailboxEntry entry) {
      receivedMessages.add(Objects.requireNonNull(entry, "entry"));
  }
  // L204-208：读侧每次全量防御拷贝
  public List<MailboxEntry> getReceivedMessages() {
      synchronized (receivedMessages) { return new ArrayList<>(receivedMessages); }
  ```
- **严重程度**: P3
- **并发场景**: 写侧为 actor 单消费线程（`InMemoryActorRuntime.runConsumptionLoop`）；读侧任意观测线程经 synchronized 快照；风险面是长驻 actor 的累积与读放大。
- **触发时序**: T1：actor 绑定 mailbox 后消费循环以 pollIntervalMs（默认 1000ms）节拍持续 poll；T2：每条消息 `addReceivedMessage` 追加完整 `MailboxEntry`（含 envelope payload 原对象引用），无裁剪/窗口化 → 长驻 actor（7×24 网关型部署）随消息量线性增长；每次 `getReceivedMessages()`（观测/管理接口轮询）全量拷贝 O(n)，轮询频率 f 下产生 O(n×f) 的周期性分配与锁持有时间膨胀。
- **现状**: javadoc L42-47 声明「Retained for observability even when steering injection is active (plan 220)」——保留是显式设计，但未声明任何容量包络/裁剪窗口；`MailboxEntry` 因 payload 是原对象引用，消息体大时放大显著。
- **风险**: 高吞吐邮箱场景下观测日志成为 actor 内最大驻留体；观测接口高频轮询放大 GC 压力；与 mailbox pending 队列（message 包）叠加。
- **建议**: ①改为有界环形窗口（保留最近 N 条，`ArrayDeque` + 容量驱逐，或平台 `CacheConfig` 风格配置）；②观测读改为增量/游标接口（since deliveryId）替代全量拷贝；③或至少 payload 存摘要引用而非原对象。
- **失效面枚举**: ①`AgentActor.receivedMessages`（L89）——本发现主对象；②`AgentActor.addReceivedMessage`（L195-197）——写侧无界点，同修；③`AgentActor.getReceivedMessages`（L204-208）——全量拷贝读面，同修；④`InMemoryActorRuntime` L445 调用点——消费循环侧无缺陷；⑤`DeferredAckMailbox.pending`（message 包，X3 分区）——同族无界队列仅标注；⑥`InMemoryActorRegistry.byActorId/sessionToActorId`（L43-45）——有 unregister 对称清理，对照组不报。
- **信心水平**: 很可能（无界性确定；实际增长速率取决于 mailbox 启用部署的消息量——mailbox 为可选装配，NoOp 默认不消费）
- **误报排除**: 「观测日志就该全量保留」不是已声明边界——javadoc 只声明了保留意图，未声明容量边界（方法论第 6 条：包络未声明才是缺陷）；「单消费线程无并发问题」不适用——本发现是资源界缺陷，单线程不豁免。
- **复核状态**: 保留(P3)｜新发现——回码核验成立：receivedMessages 只增（唯一写点 addReceivedMessage）+ getReceivedMessages 全量防御拷贝直核；javadoc 仅声明保留意图未声明容量包络，方法论第 6 条口径适用——mailbox 可选装配注记维持 P3

### [C5-015] 会话存储三实现 put-only：引擎终态清理不含 sessionStore，全历史会话（含完整消息历史）堆内永驻（F4/F8）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/InMemorySessionStore.java:21, 33-36；session/FileBackedSessionStore.java:106, 112, 122, 176-203；session/DBSessionStore.java:79, 86, 93, 197-235；engine/DefaultAgentEngine.java:146`
- **证据片段**:
  ```java
  // InMemorySessionStore L21：默认实现（DefaultAgentEngine L146 显式 new）——只有这一张 map，无任何淘汰
  private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
  // FileBackedSessionStore L106/L112/L122 与 DBSessionStore L79/L86/L93 同构三张：
  private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, SessionPersistenceState> persistedStates = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
  // remove() 三个实现都正确清理了各自条目（DB L198-234 连 DB 行带 journal 行）——但全模块 grep
  // （sessionStore.remove / store.remove / deleteSession / removeSession）无任何调用方。
  ```
- **严重程度**: P1
- **并发场景**: 行为性资源界缺陷（非竞态）。singleton store 被多请求线程并发读写（`computeIfAbsent`/`putIfAbsent` 原子性正确）；缺陷面是跨会话生命周期累积——store 与 singleton 引擎同生命周期。
- **触发时序**: T1：每个会话经 `execute`/`getOrCreate` 在 store 中建立条目（`DefaultAgentEngine.java:146` 缺省装配 `InMemorySessionStore`，`DefaultAgentEngineConfig` 同样缺省）；T2：会话到达终态（completed/failed/...）时引擎终态清理清单仅含 checkpointManager + runningExecutions + runningInjectionTargets（C5-009/012/013 已登记的三族），`ISessionStore.remove(sessionId)` 接口存在、三个实现均正确实现，但引擎与全部服务层**零调用** → 每个历史会话的完整 `AgentSession`（消息历史 + metadata + 已物化的 spill/snapshot archive 引用）+ 每 save 一条的 `persistedStates` 前缀指纹 `List<Long>`（装箱，每消息一条）永久驻留堆内；且 C5-020 的 memory provider、C5-021 的 background tasks 均以 sessionId 为键从同一会话流派生，叠加放大。
- **现状**: store 持久层（DB 行/文件）保留会话是功能语义（跨进程恢复），但内存侧三张缓存（及 InMemory 实现本体）没有任何终态回收路径、没有 TTL/容量配置入口、owner doc（`docs-for-ai/03-modules/nop-ai.md`）仅有「生命周期由 runtime 管理」一句而 runtime 实际从不管理——按「守卫缺失、包络未声明」口径保留。与 C5-009（DB checkpoint 缓存缺 remove 覆写）、C5-012（邮箱双表）、C5-013（安全状态图）同属会话键容器缺终态清理族，但本条主体是 store 层自身且条目体积大（MB 级/会话）。
- **风险**: 长时运行部署堆占用随历史会话总数线性增长：每会话 ≈ 上下文包络内 0.5-1MB 消息历史（UTF-16 ×2）+ 指纹表；高会话流失率（每请求一会话）下退化为按请求泄漏，万级历史会话即 GB 级驻留；`persistedStates` 的装箱 Long 指纹表额外放大（每消息 16-24B 对象）。
- **建议**: ①引擎终态清理路径（`DefaultAgentEngine` finally / `AgentSessionLifecycle` 终态分支）增加 `sessionStore.remove(sessionId)`——对已正确实现的 remove 是纯接线修复；若终态会话仍需短期可查，可改为延迟清理（TTL 后 remove）或先 `sessions` 缓存淘汰 + 保留持久层；②`persistedStates` 指纹表随 remove 同步清理（remove 已实现，接线即覆盖）；③owner doc 补会话保留包络声明（保留多久、谁负责清理）。
- **失效面枚举**: ①`InMemorySessionStore.sessions`（L21）——本发现主对象（缺省实现）；②`FileBackedSessionStore.sessions/persistedStates/sessionLocks`（L106/112/122）——同族，remove 已实现未接线；③`DBSessionStore.sessions/persistedStates/sessionLocks`（L79/86/93）——同族（注意：C5-009 登记的是同文件三张 checkpoint 缓存缺 remove 覆写，本条是 store 侧 remove 存在但无调用方，两个缺陷互补而不重复）；④`DefaultAgentEngine`/`AgentSessionLifecycle` 终态清理清单——调用侧缺口，同修点；⑤`InMemorySessionStore.save` no-op 语义（L79-83）——声明清晰，不报；⑥`AgentSession.seenInputKeys/pendingInjections`（L38-89，1024 FIFO / 256 fail-fast）——有界对照组，不报；⑦C5-020 memory provider / C5-021 background tasks——同会话流派生键，独立登记。
- **信心水平**: 确定（三实现 remove 与全模块零调用均已 grep 核实）
- **误报排除**: 「store 就是该持有会话」不成立——持有是持久层职责（DB 行/文件），内存缓存的目的注释是「read performance」（DBSessionStore L55-58），性能缓存无淘汰即缺陷；「remove 是外部生命周期的事」不成立——全模块无任何外部入口调用，API 为死代码；不属于「平台设施自身不重审」——三实现均为本模块代码。
- **复核状态**: 保留(P1)｜新发现——回码核验成立：三实现 remove 均已正确实现（InMemory:34-35/FileBacked:177-182/DB:198-203）而全模块双轮 grep（含宽化 .remove(sessionId) 模式）零外部调用方直核、DefaultAgentEngine:146 缺省 new InMemorySessionStore() 直核、终态路径 sessionStore.save 反向 pin 直核——缺省装配 + MB 级/会话 + 零回收，族首 P1 成立

### [C5-016] restorePendingSessions/listAllSessions 全量物化：DB 全表 CLOB 扫描 / 全盘目录扫描，所有会话整载 + 缓存全量 pin（F7）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/DBSessionStore.java:271-314；session/FileBackedSessionStore.java:244-288；engine/AgentSessionLifecycle.java:1034-1061`
- **证据片段**:
  ```java
  // DBSessionStore L274-275：SELECT 全表 CLOB，无 LIMIT/分页
  String selectAll = "SELECT " + AiAgentSessionTable.COL_SESSION_DATA + " FROM " + AiAgentSessionTable.TABLE_NAME;
  // L286-301：while(rs.next()) 逐行全量反序列化 + putIfAbsent 全量 pin 进缓存
  while (rs.next()) {
      String json = rs.getString(1);
      SessionFileReader.PersistedSnapshot snapshot = SessionFileReader.deserializeSnapshot(json);
      ...
      AgentSession raced = sessions.putIfAbsent(sid, loaded);
  // FileBackedSessionStore L249-282：Files.list 全目录 → 每会话 session.json 全读 + journal replay + pin
  ```
- **严重程度**: P2
- **并发场景**: 恢复编排路径（`restorePendingSessions`，操作员/启动触发）在任意线程执行；`while (rs.next())` 逐行物化期间 JDBC 连接持有全结果集；多实例同时恢复时各自全量物化叠加。
- **触发时序**: T1：操作员调用恢复编排（或部署配置的自动恢复）→ `AgentSessionLifecycle:1037` 调 `sessionStore.listAllSessions()`；T2：DB 面单条 SQL 拉回全部 `SESSION_DATA` CLOB（每行 = 完整会话 JSON，0.5-1MB 级）→ 每行经历「CLOB String + JsonTool 全量 parse Map 树 + AgentSession 对象」三重瞬时叠加（≈3× CLOB 尺寸）且解析成功的行全部 `putIfAbsent` 进缓存永驻（与 C5-015 复合：一次性把全历史会话 pin 进堆）；文件面同样全目录 `Files.list` + 逐文件全读。1000 会话 × 1MB CLOB → 单次调用堆峰值 GB 级。
- **现状**: javadoc 声明了全表 discovery 语义与损坏隔离（warn-继续），但未声明任何规模包络/分页/批处理策略；恢复目标本只需要 `status ∈ {running, pending}` 的子集（AgentSessionLifecycle L1065 起逐个过滤），却物化了全部终态会话。
- **风险**: 会话历史增长后恢复操作本身成为 OOM/长事务源（长事务期间 DB 连接被占）；终态会话白白物化后仍被 pin（C5-015）。与 CL-048（recovery 扫描三处 SELECT 无 LIMIT 但仅 ID 列表）不同面：本条是全状态 CLOB 物化。
- **建议**: ①SELECT 投影增加 `STATUS` 列并 `WHERE STATUS IN ('running','pending')`（恢复目标本就是未完会话），或至少先拉 ID+STATUS 轻列清单再逐个 `get`；②分页/分批迭代（复用 `RecoveryScanResult` 分桶风格）；③文件面可在 discovery 前置一个轻量 status 探针（仅读 session.json 头部字段）。
- **失效面枚举**: ①`DBSessionStore.listAllSessions`（L271-314）——本发现主对象；②`DBSessionStore.getAll`（L237-247，tenant 模式转发①）——同修；③`FileBackedSessionStore.listAllSessions`（L244-288）——同族文件面，同修；④`InMemorySessionStore.listAllSessions`（L57-59）——=getAll 本体（无独立持久层），随 C5-015 口径；⑤`AgentSessionLifecycle.restorePendingSessions`（L1034-1061）——消费侧全量快照 `new ArrayList<>(discovered)`（1× 引用拷贝，有界于①返回），随①同修；⑥`loadFromDb`（L581-622）——单会话 WHERE 查询，有界，不报。
- **信心水平**: 确定
- **误报排除**: 「管理路径低频」不豁免——单次调用的峰值本身即超堆预算风险（方法论 C5 步骤 2「无 LIMIT 的查询结果全量载入」明列审计对象）；「恢复本来就要读全部」不成立——恢复只需未终态子集，投影与过滤即可消除大头。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：SELECT SESSION_DATA 全表（WHERE 仅租户过滤）直核、while(rs.next()) 逐行全量反序列化 + putIfAbsent pin 直核——恢复只需未终态子集而物化全部会话，投影/过滤即可消除大头的论证成立

### [C5-017] DeferredAckMailbox：deadLetter 列表完全无界永驻（capacity 检查之外、无清空 API）+ inFlight 无崩溃回收扫描 + 默认无界 pending（F4/F2）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DeferredAckMailbox.java:54-60, 74-76, 106, 134, 158-210；message/IMailbox.java:52-58；对照 message/DBMessageService.java:107, 533-566`
- **证据片段**:
  ```java
  // L60：dead-letter 列表——capacity>0 也约束不到它，且无任何清空 API
  private final List<MailboxEntry> deadLetter = new ArrayList<>();
  // L106：容量检查只覆盖 pending + inFlight
  if (capacity > 0 && (pending.size() + inFlight.size()) >= capacity) { return false; }
  // L134：poll 后条目进 inFlight——消费线程死亡时无任何扫描把它放回 pending
  inFlight.put(inFlightEntry.getDeliveryId(), inFlightEntry);
  // 对照：DBMessageService L107/533-566 有 stale-CLAIMED sweep（staleClaimTimeoutMs=5min），
  // 内存邮箱没有任何对应物。
  ```
- **严重程度**: P2
- **并发场景**: offer（messenger 投递线程/引擎线程）与 poll/ack/nack（actor 消费线程）经 `synchronized(lock)` 正确互斥；缺陷面是三张内部容器与 mailbox 生命周期（=引擎生命周期，C5-012 已登记邮箱表不清理）的累积组合。
- **触发时序**: dead-letter 面——T1：毒消息（消费方持续 nack(requeue=true)）经 5 次（DEFAULT_MAX_DELIVERY_ATTEMPTS）重投后进 `deadLetter`（L165-179）；T2：每个新毒消息永久追加一条（含完整 envelope payload），`deadLetterCount()/deadLetterEntries()` 只读，无 purge/容量/TTL——即便部署方正确配置了 `capacity>0`，deadLetter 仍无界。inFlight 面——T3：消费线程在 poll 之后、ack/nack 之前死亡（InMemoryActorRuntime 三处 FAILED 出口不 nack，见 CL-046）；T4：条目永留 `inFlight`，无 stale 扫描回放，堆驻留 + 该消息永久滞留（对照 DB 面同模块已有 5min stale-claim sweep 的平台级先例）。pending 面——T5：默认构造 `capacity=0` 无界（javadoc 已声明语义），mailbox 常驻 + 长 WAIT 期（CL-047 同型时序）持续 offer → pending 无界增长，每条持完整 `ChatMessage` payload。
- **现状**: capacity 语义与 dead-letter 保留意图（「retained for inspection; never redelivered」）均已声明，但 dead-letter 的保留未声明任何容量包络；inFlight 的崩溃恢复契约缺失未声明（同为 at-least-once 语义实现，DB 兄弟有 sweep，内存实现没有——F3 同族平台对齐缺口）。
- **风险**: 毒消息流（上游格式变更等）下 deadLetter 无限吃堆；消费循环崩溃后 inFlight 条目既不重投也不回收（消息丢失 + 堆滞留双后果）；与 C5-012（邮箱表不清理）、C5-014（receivedMessages 观测日志）对同一 mailbox 三面叠加。
- **建议**: ①`deadLetter` 设容量上界（保留最近 N 条，超界淘汰最旧并计数上报，复用平台 `CacheConfig` LRU 语义）；②为 inFlight 增加 stale 扫描（对齐 DBMessageService 的 staleClaimTimeoutMs 语义：poll 超时未 ack/nack 的条目 nack(requeue=true) 回 pending）——消费循环节拍或独立调度均可；③引擎装配邮箱 factory 时显式传有界 capacity（平台 `QueueOverflowPolicy`/`IBlockingQueue` 设施可用）。
- **失效面枚举**: ①`DeferredAckMailbox.deadLetter`（L60, L174）——本发现主对象；②`DeferredAckMailbox.inFlight`（L59, L134）——无回收扫描面，本发现；③`DeferredAckMailbox.pending`（L58）——默认无界面（X2 批次 C5-012/014/CL-047 三处预告移交本批，此处正式登记），同修；④`keysByCorrelationId`（L67）——ack/dead-letter/nack(false) 三路对称清理，对照组不报；⑤`MailboxMessageHandler.offer 失败 WARN+丢弃`（有界满时）——已声明 fire-and-forget 语义，不报；⑥`DBMessageService` 的 CLAIMED/PENDING——DB 面独立登记为 CL-050；⑦`NoOpMailbox`——无状态，不报。
- **信心水平**: 确定
- **误报排除**: 「capacity<=0 是声明的语义」只豁免 pending 的默认无界声明，不豁免 deadLetter（capacity 约束不到它且声明中无保留包络）与 inFlight（崩溃恢复契约缺失，DB 兄弟实现证明该契约是本模块自设标准）；「3-phase 协议本身正确」不豁免——原语使用正确、复合生命周期缺陷才是发现对象。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：deadLetter 裸 ArrayList 在 capacity 检查（仅 pending+inFlight）之外且无 purge 直核、poll 后 inFlight.put 无 stale 回放直核、DB 兄弟 DBMessageService 5min stale-claim sweep（L107/217）对照直核——F3 平台对齐缺口 + deadLetter 无界成立

### [C5-018] LocalAgentMessenger.replySubscriptions：按 caller 可控 senderId 派生 reply topic 建订阅，只增不撤且泄漏传导进平台消息服务（F4）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/LocalAgentMessenger.java:51-57, 88-89, 110-111, 123-134`
- **证据片段**:
  ```java
  // L57：replyTopic → subscription，仅 put 无 remove（全文件 grep 无 cancel/unsubscribe 调用）
  private final Map<String, IMessageSubscription> replySubscriptions = new ConcurrentHashMap<>();
  // L88：key 由请求方的 senderId 直接派生——caller 可控键空间
  String replyTopic = AgentMessageTopics.replyTopic(requestEnvelope.getSenderId());
  // L128-133：double-checked 锁内 subscribe 并永久保留
  IMessageSubscription subscription = messageService.subscribe(replyTopic, new ReplyDemultiplexer());
  replySubscriptions.put(replyTopic, subscription);
  ```
- **严重程度**: P2
- **并发场景**: singleton messenger 被多请求线程并发 `request`（ensureReplySubscription 有 double-checked 锁，原子性正确）；缺陷面是键空间随请求方无界扩展 + 订阅句柄跨请求永驻。
- **触发时序**: T1：调用方 A（senderId=会话 X）发起 `request` → 为 `reply-X` 建订阅，请求完成（L105 whenComplete / L110 orTimeout）只清理 `pendingRequests` 条目，**不撤订阅**；T2：每个新 senderId（新会话/新 agent/伪造字符串）首次 request 都沉淀一条新订阅——订阅对象本身 + 平台 `IMessageService`（LocalMessageService/DBMessageService）消费者注册表中的对应条目同步永驻；长时运行多租户网关下 map 大小 = 历史请求方总数，且每个 ReplyDemultiplexer 对该 topic 上后续到达的任何 RESPONSE 持续回调。
- **现状**: 共享订阅是声明的设计（「One subscription per reply topic, shared by all in-flight requests」），但「per reply topic」的 topic 空间由 caller 逐请求扩展、共享订阅却从不回收——共享设计只节省了同 key 重复订阅，没有解决键空间生命周期；无 TTL/LRU/上限配置。
- **风险**: 键空间无界增长（C5-001 已按同型「线上可控键累加器」定 P1，本条单条目小（订阅句柄 + 空 demux 实例）故 P2）；DBMessageService 后端下每个泄漏订阅还在 `ai_agent_message` 表持续产生轮询（pollTopic 遍历 consumers.keySet()），放大 DB 扫描面（CL-050）。
- **建议**: ①引用计数：`pendingRequests` 放空该 topic 时撤订阅（`subscription.cancel()` + map remove——与 C5-017 的 DbSubscription.cancel 对称 API 已存在）；②或 reply topic 键白名单化（senderId 校验为已知会话）+ `CacheConfig` 有界 LRU 兜底。
- **失效面枚举**: ①`LocalAgentMessenger.replySubscriptions`（L57, L123-134）——本发现主对象；②`LocalAgentMessenger.pendingRequests`（L51）——L105 `whenComplete` 终态移除 + L110 orTimeout 保证 settle，对称清理对照组不报；③`DBMessageService.consumers`（L114）——`DbSubscription.cancel → unregisterConsumer`（L310-313）对称，对照组不报；④`HandlerAdapter`（L147-190）——每次 registerHandler 新建、生命周期随返回的 subscription（调用方管理），随①口径观察；⑤平台 LocalMessageService 自身订阅表——平台设施不重审，泄漏由①传导登记。
- **信心水平**: 确定（全文件无任何 replySubscriptions 移除路径已核实）
- **误报排除**: 「共享订阅已是优化」不成立为豁免——发现对象是键空间生命周期而非单 key 重复订阅；「in-flight 数有界所以订阅数有界」不成立——订阅在请求终态后仍保留，驻留的是历史全体；不属于「平台设施自身不重审」——map 与生命周期管理是本模块代码。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：replySubscriptions 仅 ensureReplySubscription put（DCL）无任何 cancel/remove（全文件 grep 直核）、replyTopic 由 caller 可控 senderId 派生直核、pendingRequests whenComplete 终态清理对照组直核——同站点 C3-024(P1)/C7-026 双登记留 7.1 并单

### [C5-019] InMemoryWriteIntentRegistry 默认单例：releaseSession 全模块零调用，写意图跨会话永久累积 + 桶扫描随规模退化（F8 清理不对称——API 在、调用面缺）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/conflict/InMemoryWriteIntentRegistry.java:40, 53, 61-69, 74-87；engine/AgentSecurityConsultation.java:556-585；engine/DefaultAgentEngineConfig.java:145`
- **证据片段**:
  ```java
  // InMemoryWriteIntentRegistry L40/L53/L68：path 键控桶，注册即追加
  private final Map<String, List<WriteIntent>> intentsByPath = new ConcurrentHashMap<>();
  List<WriteIntent> bucket = intentsByPath.computeIfAbsent(path, k -> new ArrayList<>());
  bucket.add(intent);
  // L74-87：内建批量清理入口——grep 全模块（engine/*/session/*/tool/*）无任何调用方
  public void releaseSession(String sessionId) { ... bucket.removeIf(i -> sessionId.equals(i.getSessionId())); ... }
  // DefaultAgentEngineConfig L145：缺省装配（非 opt-in）
  private IWriteIntentRegistry writeIntentRegistry = new InMemoryWriteIntentRegistry();
  ```
- **严重程度**: P2
- **并发场景**: 多会话并发经 `AgentSecurityConsultation` 派发写类工具（参数含路径键的每次 tool call）注册意图；per-path bucket monitor 串行化同路径并发注册（TOCTOU 防护正确）；缺陷面是跨会话生命周期累积与读扫描退化。
- **触发时序**: T1：会话 A/B/C… 各自执行写文件工具 → `registerAndGetConflicting` 在 L68 追加 `WriteIntent(sessionId, path, ...)`；T2：会话终态时引擎清理清单（C5-015 已列）不含 writeIntentRegistry，内建的 `releaseSession(sessionId)` 批量清理钩子**全模块零调用** → 意图条目跨会话永久累积。复合退化：每次注册 L62-66 对目标桶全量 `sameSession` 扫描——高频写入路径（同会话对同一文件的 N 次编辑）桶长 O(N) 增长，注册成本 O(N) 递增；跨会话冲突判定在已终会话的僵尸意图上继续做无效比较（僵尸意图还可能造成假冲突：终态会话的意图永不过期，新会话写同路径永远看到「跨会话冲突」，FailFast 策略下误拒）。
- **现状**: 类 javadoc 声明「intents accumulate per session and are removed in bulk by releaseSession when the session terminates」——清理契约已声明但引擎侧未接线（F8 教科书形态：release 与 register 不对称）；空桶保留有显式理由（monitor 稳定性，L28-33）且每桶廉价，可接受。
- **风险**: 长时运行多会话下 registry 无界增长（内存小但永驻）；同路径重复写会话的注册扫描线性退化；最重的是**正确性面**——终态会话的僵尸意图使跨会话写冲突检测产生永久假阳性（FailFastStrategy 下新会话写已被历史会话碰过的路径被误拒），行为性缺陷随时间必然浮现。
- **建议**: ①引擎终态清理路径接线 `releaseSession(sessionId)`（与 C5-015 的 sessionStore.remove 接线同一次改动收口）；②或注册侧携带 TTL（`now` 字段已有，注册时惰性剔除过期条目）；③`WriteIntent` 增加终态感知（登记时订阅会话终态事件主动释放）。
- **失效面枚举**: ①`InMemoryWriteIntentRegistry.intentsByPath`（L40）——本发现主对象；②`InMemoryWriteIntentRegistry.registerAndGetConflicting`（L43-71）——注册面无缺陷，扫描退化随①；③`InMemoryWriteIntentRegistry.releaseSession`（L74-87）——已实现未接线，修复即调用；④`AgentSecurityConsultation` 注册调用点（L585）——调用侧无缺陷；⑤`FailFastStrategy`（L43 注释）——策略本体无状态不报，假阳性后果经它显现；⑥`InMemoryContributionRegistry`（contribution 包，byType/bySource）——有 `unregisterSource`（L112-138）且源为配置面注册（skill/talent provider），有界对照组不报；⑦`DefaultHookRegistry` 三表（hook 包 L58/70/81）——注册面生命周期对称，有界，不报。
- **信心水平**: 确定（releaseSession 零调用 + 缺省装配均已 grep 核实）
- **误报排除**: 「意图条目小」不作为不报理由——僵尸意图造成跨会话假冲突是行为缺陷而非纯内存问题；「空桶保留已声明」只豁免桶骨架，不豁免桶内条目与 releaseSession 未接线；不属于「单线程/低频」豁免——写派发是 agent 主路径。
- **复核状态**: 已驳回｜新发现——**复核证伪核心事实**：原条目断言 releaseSession「全模块零调用」，实测 grep 命中 4 个执行出口 finally 接线——DefaultAgentEngine:1019（doExecute）+ AgentSessionLifecycle:393（resumeSession）/559（wakeSession）/958（restore 路径），注释明言「Safe to call on every exit path」；git -S 追溯接线自 2f32515894（2026-08-01）即入基线——「跨会话永久累积/僵尸意图假冲突/F8 release 不对称」三主张全部不成立；残余面仅单次执行内同路径 O(N) 扫描（执行退出即释放），已由同站点 C1-026（P3）覆盖，不独立立案

### [C5-020] memory provider 会话键映射无释放契约：接口无 remove，缺省 InMemoryMemoryStoreProvider 按会话永久累积工作记忆（F4/F8）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/IMemoryStoreProvider.java:19-21；memory/InMemoryMemoryStoreProvider.java:20-27；memory/AdapterBackedMemoryStoreProvider.java:51, 72-77；engine/DefaultAgentEngineConfig.java:126；engine/AgentSessionLifecycle.java:204-211；engine/AgentToolDispatcher.java:543-545`
- **证据片段**:
  ```java
  // IMemoryStoreProvider L19-21：接口契约只有 getOrCreate——无任何 release/remove 方法
  public interface IMemoryStoreProvider {
      IAiMemoryStore getOrCreate(String sessionId);
  }
  // InMemoryMemoryStoreProvider L20-27：会话键 computeIfAbsent，无淘汰
  private final ConcurrentHashMap<String, InMemoryAiMemoryStore> stores = new ConcurrentHashMap<>();
  // AgentSessionLifecycle L206：系统提示词记忆自动注入——每次执行必经，与是否使用记忆工具无关
  io.nop.ai.agent.memory.IAiMemoryStore store = config.getMemoryStoreProvider().getOrCreate(sessionId);
  items = store.readBudgeted(config.getMemoryInjectionBudgetTokens(), context);
  ```
- **严重程度**: P2
- **并发场景**: singleton provider 被多请求线程并发 `computeIfAbsent`（原子性正确）；缺陷面是跨会话累积 + 每条目内含无容量上界的 items map（`InMemoryAiMemoryStore` L38 `ConcurrentHashMap`，javadoc 显式声明「retention / TTL / capacity limits」out of scope——条目级无界是已声明边界，本发现对象是 **provider 层的会话键映射生命周期**，该声明不覆盖）。
- **触发时序**: T1：每个会话首次执行 → 系统提示词自动注入路径（`AgentSessionLifecycle:206`，缺省开启的预算化记忆注入）调用 `getOrCreate(sessionId)` → provider map 新增一条；该调用在**每次执行**都发生、与用户是否使用 read-memory/write-memory 无关（`AgentToolDispatcher:545` 派发上下文装配同样经 getOrCreate）；T2：会话终态清理清单不含 provider（且接口根本没有释放方法可调）→ store 对象（含该会话全部记忆条目——write-memory 每次调用追加，无容量上界）永驻。长时运行下 map 大小 = 历史会话总数。
- **现状**: `InMemoryAiMemoryStore` javadoc 声明条目级 retention/TTL/容量为 out of scope（已声明边界，本条不报条目级）；`InMemoryMemoryStoreProvider` javadoc 声明数据不持久化，但未声明 provider 映射的会话生命周期包络；接口契约（IMemoryStoreProvider javadoc「Behaviour contract」仅两条）无释放语义——契约层缺口。
- **风险**: 缺省接线（`DefaultAgentEngineConfig:126`）下零配置部署即按历史会话总数累积工作记忆 store；启用 write-memory 的高频会话单条目还可无界膨胀（已声明边界内的已知风险传导至永不回收的 map 条目 = 声明边界被生命周期缺口放大）。
- **建议**: ①`IMemoryStoreProvider` 增加 `release(String sessionId)` default 方法 + 两实现清理 stores 条目；②引擎终态清理路径接线（与 C5-015/C5-019 同一终态挂钩统一收口）；③可选：provider 接 `CacheConfig` 有界 LRU 兜底。
- **失效面枚举**: ①`InMemoryMemoryStoreProvider.stores`（L20）——本发现主对象（缺省装配）；②`AdapterBackedMemoryStoreProvider.stores`（L51）——同族，同修；③`IMemoryStoreProvider` 接口（L19-21）——契约缺释放方法，同修点；④`InMemoryAiMemoryStore.items`（L38）——条目级无界为 javadoc 已声明边界，本条不报（声明边界内风险经①放大已在风险栏注明）；⑤`AgentSessionLifecycle:206` / `AgentToolDispatcher:545` 两个 getOrCreate 调用点——调用侧无缺陷，触发链证据；⑥`InMemoryStorageAdapter.items` / `InMemoryVectorAdapter.index`（memory 包 L49/L46）——随④口径（会话内条目，声明边界），不单列；⑦`WriteMemoryExecutor.clear`（tool 包）——清除条目但不移除 provider 条目，不构成回收路径。
- **信心水平**: 确定（接口无释放方法 + 两调用点每次执行必经均已核实）
- **误报排除**: 「条目级无界已声明 out of scope」——本发现对象是 provider 会话键映射（未被该声明覆盖），按口径第 6 条区分声明面与缺陷面；「会话结束 GC 回收」只覆盖进程内显式 remove 后的回收，不覆盖永驻 map 条目。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：接口仅 getOrCreate 无释放方法直核、两 provider stores computeIfAbsent 无移除直核、DefaultAgentEngineConfig:126 缺省 new InMemoryMemoryStoreProvider() 直核、AgentSessionLifecycle:206 + AgentToolDispatcher:545 两个每次执行必经调用点直核——缺省接线 + 零回收成立

### [C5-021] DefaultBackgroundTaskRegistry.tasks put-only：后台任务 Entry（8KB tail + Future 句柄 + latch）跨任务永久累积（F4）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/DefaultBackgroundTaskRegistry.java:59, 97-102, 134-170, 183-206, 304-306`
- **证据片段**:
  ```java
  // L59：进程级（Builder-owned）注册表——submit 后唯一写入口，无 remove/淘汰/TTL
  private final ConcurrentHashMap<String, Entry> tasks = new ConcurrentHashMap<>();
  // L100：每个 runInBackground 工具调用建一条
  tasks.put(taskId, new Entry(taskId, toolName, sessionId, callId, timeoutMs));
  // L134-170：complete() 只做状态迁移 + output 捕获 + 桥通知，从不移除条目
  ```
- **严重程度**: P2
- **并发场景**: singleton 注册表被多会话的 dispatch 线程并发 submit/complete（per-entry `synchronized(entry)` 状态机正确）；缺陷面是跨任务生命周期累积。
- **触发时序**: T1：agent 会话使用 runInBackground（每次后台化工具调用）→ L100 `submit` 建 Entry（含 8KB 有界 output tail——单条有界是显式声明；+ `Future` 句柄 + `CountDownLatch`）；T2：任务终态（COMPLETED/FAILED/TIMEOUT，NOTIFICATION_FAILED 附加桥重试态）后条目永驻——complete/stop/snapshot/readOutput 全部只读不改 map；全文件无 remove 调用（`size()` 仅观测）。长时运行 agent 下 map 大小 = 历史后台任务总数。
- **现状**: 输出捕获的有界性（8KB tail）与「file offload registered as a Known Residual」已声明；条目级生命周期有界，但注册表级无任何清理/上限/TTL 声明（javadoc 与 owner doc 均无）。NOTIFICATION_FAILED 条目额外保留懒重投语义（每次 snapshot/readOutput 重试桥回调）——永驻条目使其重试面也无界。
- **风险**: 高频后台化部署（每会话数十次后台任务）下 Entry 总量随业务量线性增长；每条 8KB tail × 万级任务 = 数十 MB 慢性增长 + map 扫描（无遍历路径，主要是驻留）；`entry.future` 终态后为 done 状态不持线程，泄漏限于 Entry 壳。
- **建议**: ①终态 + 桥通知成功后延迟移除（TTL，如终态后 N 分钟 `tasks.remove(taskId, entry)`——快照/读取语义允许 null 即「过期」）；②或 `CacheConfig` 有界 LRU + 超界淘汰最旧终态条目；③owner doc 声明注册表保留包络。
- **失效面枚举**: ①`DefaultBackgroundTaskRegistry.tasks`（L59）——本发现主对象；②`DefaultBackgroundTaskRegistry.submit`（L97-102）——写入口无缺陷，随①；③`complete/stop/snapshot/readOutput`（L134-233）——终态语义正确、均不移除，同修点；④`Entry.outputTail`（L341）——单条 8KB 有界已声明，不单列；⑤`retryBridgeIfNeeded`（L235-256）——懒重投有 notificationSent 单次成功收敛，逻辑正确，仅随①驻留；⑥`SmartModelRouter.tierSequences`（router 包 L69）——builder 配置面枚举键有界，对照组不报；⑦`TaskDispatchCoordinator.inFlightDispatches`（scheduler L46）——whenComplete 排空 + 有界超时保证 settle（javadoc 自证），对照组不报。
- **信心水平**: 确定
- **误报排除**: 「单条有界（8KB tail）」不豁免注册表级无界——方法论 C5 步骤 5（单请求有界 × 键基数 = 全局峰值）明列；「任务可查询是特性」无声明支撑——javadoc 未声明保留包络（对照 C5-014 的 receivedMessages 至少声明了保留意图）。
- **复核状态**: 保留(P2)｜新发现——回码核验成立：tasks 唯一写入口 submit-put、全类方法面（submit/run/complete/snapshot/readOutput/stop/attachFuture/size/rawStatus）无 remove 直核、DefaultAgentEngine:654 缺省装配（customRegistry 空兜底 new）直核——缺省注册表跨任务累积成立；同站点 C3-023(P1)/C4-012(P1)/C7-027/C1-017 多维登记留 7.1 并单

### [C5-022] team 任务存储无删除（InMemory 双索引 + DB 表）+ scheduler 每 scanInterval 对全量任务（含终态）重建拓扑 + teamIndex O(n²) 拷贝写（F4）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/InMemoryTeamTaskStore.java:59-60, 85-89；team/DbTeamTaskStore.java:222, 531（无 DELETE 语句，全文件 grep）；team/scheduler/TeamTaskSchedulerDaemon.java:278-334`
- **证据片段**:
  ```java
  // InMemoryTeamTaskStore L59-60：主索引 + team 二级索引，接口仅有 CAS 状态迁移、无 deleteTask
  private final ConcurrentHashMap<String, TeamTask> tasks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<String>> teamIndex = new ConcurrentHashMap<>();
  // L85-89：每次 createTask 整表拷贝重写 team 索引（copy-on-write）
  teamIndex.compute(teamId, (id, list) -> {
      List<String> mutable = list != null ? new ArrayList<>(list) : new ArrayList<>();
      mutable.add(taskId);
  // TeamTaskSchedulerDaemon L327-334：每次扫描拉全队任务（含全部终态）重建拓扑
  List<TeamTask> tasks = taskStore.getTasksByTeam(teamId);
  TeamTaskTopology topology = new TeamTaskTopology(tasks);
  List<TeamTask> ready = topology.getReadyTasks();
  ```
- **严重程度**: P2
- **并发场景**: singleton store 被会话线程（team-task-create 等 executor）与 scheduler daemon 扫描线程并发读写（compute CAS 正确）；缺陷面是任务总量只增 + 周期扫描的读放大叠加。
- **触发时序**: T1：团队任务经 `createTask`（会话工具路径）或 DB INSERT（`DbTeamTaskStore:222`）持续建立——COMPLETED/ABANDONED 终态只改状态不删除，InMemory 与 DB 两实现均无任何删除/归档 API（全文件 grep 无 DELETE/移除）；T2：daemon 按 `scanIntervalSec` 永久周期扫描 → 每轮对每个活跃团队 `getTasksByTeam`（全量含终态）物化 `List<TeamTask>` + `new TeamTaskTopology(tasks)` 全图重建（就绪判定要遍历 blockedBy 依赖边）→ 单轮扫描成本与常驻内存随累计任务数线性增长，扫描永不收敛。复合放大：`createTask` 的 copy-on-write 使 teamIndex 每次建任务复制整个 id 列表——n 个任务的团队累计 O(n²) 拷贝分配。
- **现状**: 任务 ID 均为 UUID、终态机（claim/complete/abandon/reclaim）CAS 语义正确；`ITeamTaskStore` 接口契约无清理方法，DB 表（`ai_agent_team_task`）无 TTL/归档声明（store-layer 契约声明表生命周期由 runtime 管理，但 runtime 无对应实现——与 C5-015 同型「声明管理、实际不管」）。
- **风险**: 长期运行的团队部署中扫描耗时随历史任务数爬升（每 `scanIntervalSec` 一次，CPU 慢性退化）；单轮扫描物化全队任务列表（TeamTask 含 subject/description 全文，任务描述大时单轮峰值可观）；DB 面同步无界增长拖慢 `getTasksByTeam` 查询。
- **建议**: ①扫描侧 `getTasksByTeam` 增加状态过滤投影（就绪判定只需 CREATED + 其 blockedBy 依赖的状态——两跳查询）；②为终态任务增加归档/删除路径（终态后 N 天清理，挂 daemon 既有调度节拍）；③teamIndex 改 `CopyOnWriteArrayList`-append 或批量重建替代整表拷贝；④`ITeamTaskStore` 接口补清理契约（对齐 `ICheckpointManager.remove` 的 Plan 278 先例）。
- **失效面枚举**: ①`InMemoryTeamTaskStore.tasks`（L59）——本发现主对象；②`InMemoryTeamTaskStore.teamIndex`（L60, L85-89）——O(n²) 拷贝写 + 无界，同修；③`DbTeamTaskStore`（INSERT L222 / SELECT L531，无 DELETE）——DB 面同族，同修；④`TeamTaskSchedulerDaemon.scanOnce`（L278-334）——读放大消费点，随①②同修；⑤`TeamTaskTopology`（flow 包）——每轮重建的纯派生对象，无独立缺陷；⑥`InMemoryTeamManager.teams/sessionToTeamId`（L66-67）——disband 仅状态翻转永驻（javadoc L47-49 声明保留意图但无包络）+ disband 不解绑 sessionToTeamId——保留意图已声明故降出发现，登记为 CL-052；⑦`ExecutionRecorder`（flow 包 L41-46）——per-run throwaway，有界对照组不报；⑧`DbTeamManager` team/member 行——member 行有 DELETE（L508），team 行随⑥口径。
- **信心水平**: 确定（无删除 API / 无 DELETE 语句 / 全量扫描均已核实）
- **误报排除**: 「终态任务保留是审计需要」无声明支撑——接口与 javadoc 均无保留包络声明（对照 `FileBackedCheckpointManager` javadoc 至少显式自认「grows unbounded」）；「单轮扫描是瞬态」不豁免——瞬态峰值随累计任务数无界增长（F7 判定式：路径 × 增长源 × 无声明上界）。
- **复核状态**: 已降级(P3)｜新发现（降级：P2→P3——事实面回码全部成立[无删除 API/无 DELETE 语句/daemon 每轮全量重建拓扑均直核]，但 DefaultAgentEngineConfig:149 缺省 teamTaskStore=NoOpTeamTaskStore、daemon 仅显式装配——任务存储为 opt-in 特性路径而非缺省面，按同站点 C1-024（P3）与本单元 opt-in 降档口径[先例 C1-005]校准；团队特性实际装配真实 store + daemon 即回 P2）

---

## 维度集外线索（overlay C2/C6/C8/C9）

### [nop-ai-core-nop-ai-agent-CL-041] 响应缓存路径 promptName 未消毒——缓存路径注入/穿越（C9）

`DefaultAiChatResponseCache.getCacheResource`（DefaultAiChatResponseCache.java:83-85）中 provider 与 model 均经 `StringHelper.safeFileName` 消毒，但 `promptName` 原样拼入相对路径：`cachePath = safe(provider) + "/" + safe(model) + "/" + promptName`。promptName 来自 `promptTemplate.getName()`（模板 yaml 的 name 字段或 `SimplePromptTemplate` 的 "simple-{md5}"）。若 name 含 `/` 或 `..`，缓存文件写出到 cacheDir 之外的任意相对路径（读路径同理可命中任意既有文件当作缓存命中）。同模块 `ChatLogHelper`（L27）对 sessionId 有 `SAFE_SESSION_ID` 白名单先例——同族消毒缺失。建议对 promptName 补 `safeFileName` + 白名单校验，并补路径穿越回归测试。

### [nop-ai-core-nop-ai-agent-CL-042] PromptTemplateManager 模板缓存 check-then-act 竞态 + 非 volatile 字段（C2）

`PromptTemplateManager.loadPromptTemplateFromPath`（PromptTemplateManager.java:17-25）从 RCM 全局缓存取出共享 `PromptModel`，`getPromptTemplate()==null` 时 `new ModelBasedPromptTemplate` 并 `setPromptTemplate` 回写——check-then-act 无同步；`PromptModel.promptTemplate`（PromptModel.java:23）非 volatile。两线程并发首次加载同一模板时各自构造实例、后写覆盖先写；后续线程可能因可见性延迟反复重建。后果限于重复构造（内存瞬态、行为等价——构造为无副作用），故作 C2 线索而非 C5 发现；复核时建议改为 `synchronized` 或 `AtomicReference` CAS 语义，并核对 `IPromptTemplate` 实现确为无状态（当前 `ModelBasedPromptTemplate` 仅持 final promptModel，成立）。

### [nop-ai-core-nop-ai-agent-CL-043] DefaultSystemPromptLoader workMode 直接拼 VFS 路径——穿越读（C9）

`buildPromptPath`（DefaultSystemPromptLoader.java:55-57）将 workMode 未经消毒拼入 `/nop/ai/prompts/system/{workMode}/{cognitive|meta|system}.md` 并经 `loadPrompt`（L59-61）`ResourceHelper.readText` 读回。workMode 含 `../` 时可越出 prompts 目录读取任意 `.md` 后缀 VFS 资源（读面穿越；且内容直接进入系统提示词 → 间接提示注入面）。与 CL-041 同族：同模块已有 `ChatLogHelper` 白名单先例可复用。建议 workMode 白名单 + `safeFileName` 双重校验。

### [nop-ai-core-nop-ai-agent-CL-044] EmbeddingServiceImpl 重试调度器无关闭路径（C6）

`EmbeddingServiceImpl.scheduler()`（EmbeddingServiceImpl.java:126-142）惰性创建单线程 `ScheduledThreadPoolExecutor`（daemon），bean 无 destroy 挂钩、无 `shutdown()` 调用点（全文件 grep 无 shutdown）。singleton bean 下与 JVM 同生命周期，daemon 属性使进程可退出，但热替换/测试上下文反复重建 bean 时旧调度器线程滞留（每实例一线程，无回收）。建议实现 `AutoCloseable`/IoC destroy 方法调用 `shutdownNow()`，或改为注入共享调度器（对齐 `io.nop.commons.concurrent.executor.IThreadPoolExecutor` 平台设施）。

### [nop-ai-core-nop-ai-agent-CL-045] DefaultAgentEventPublisher.failureCounts 已移除订阅者的计数器永久保留（C6/C8）

`DefaultAgentEventPublisher.publish`（DefaultAgentEventPublisher.java:46-54）在 `REMOVE_AFTER_THRESHOLD` 策略下把持续失败的订阅者从 `subscribers` 移除，但注释明言「the failure counter is retained for introspection」——`failureCounts`（L27，identity 键）中的 `AtomicLong` 条目不移除。每次 addSubscriber(新实例)→失败→自动移除的循环都在 map 中沉淀一个永驻条目；动态订阅/注销频繁的部署下无界增长（单条目小，P3 量级）。同族对称性：`removeSubscriber`（L65-67）也不清 failureCounts。建议 `subscribers.remove(subscriber)` 时同步 `failureCounts.remove`（或将计数器挂在订阅者包装对象上随移除回收）。

### [nop-ai-core-nop-ai-agent-CL-046] InMemoryActorRuntime FAILED actor 的 executors 条目与 registry 条目滞留，依赖同 session 下次 createActor 才回收（C6/C8）

消费循环三处 FAILED 出口（InMemoryActorRuntime.java:434 poll 失败、L494 ack 失败、L524 循环异常）仅 `updateStatus(FAILED)` 后 return——不 `registry.unregister`、不 `executors.remove`，executor 线程已退出但 `ExecutorService` 对象仍被 L133 `executors` map 强引用。回收只有两条被动路径：同 session 再次 `createActor` 时 L264-265 的 terminal-status 分支 `destroyActorInternal`，或进程级 `destroyAll`。被放弃的 FAILED session 的条目（actor + executor 句柄 + receivedMessages 残留）永驻。建议 FAILED 出口直接调 `destroyActorInternal(actor.getActorId())`（或注册到引擎终态清理挂钩），与 C5-013 的统一清理缺口一并收口。

### [nop-ai-core-nop-ai-agent-CL-047] AgentExecutionContext.steeringQueue 无界 ConcurrentLinkedQueue，平台有界队列设施未用（C5 边缘/C2）

`AgentExecutionContext.steeringQueue`（AgentExecutionContext.java:87，`ConcurrentLinkedQueue`）由 actor 消费线程写入（enqueueSteering L316-323 / InMemoryActorRuntime L465），ReAct 循环在轮界/唤醒点排空（drainSteering L368-385）。生产速率受 poll 节拍约束（≤1 条/pollInterval），但长 WAIT 期（等待外部事件/超时条件）排空点不来——数小时等待可积累数千条 `InjectionEntry`（各持完整 ChatMessage payload），无容量上限、无溢出策略。平台 `io.nop.commons.concurrent.IBlockingQueue` + `QueueOverflowPolicy`（有界 + 满策略）未用。数量级温和（1 条/秒 × 等待时长），故作集外线索；复核时建议设容量上限 + 满时按 InjectionSemantics 丢弃/拒绝并计数上报。

### [nop-ai-core-nop-ai-agent-CL-048] recovery 扫描三处 SELECT 无 LIMIT 全量物化会话 ID 清单（C5 边缘）

`ScheduledRecoveryManager.selectOrphanSessions`（ScheduledRecoveryManager.java:498-516）、`selectTimedOutSessions`（L531-549）与 `DefaultTeamTaskRecoveryHandler.selectStuckTaskIds`（DefaultTeamTaskRecoveryHandler.java:212 附近）均 `while (rs.next())` 全量拉取匹配会话 ID 入 `ArrayList`，无 LIMIT/分页。稳态下 orphan/timed-out 会被恢复流转、集合有界；但大规模故障（如 DB 抖动导致锁批量过期 + 心跳停写）时单次扫描物化全体异常会话 ID 清单——ID 为小字符串、峰值为万级条目量，风险有限。建议补 LIMIT + 分批恢复迭代（复用 `RecoveryScanResult` 既有分桶结构），或至少加扫描条数上限告警。

### [nop-ai-core-nop-ai-agent-CL-049] DBDenialLedger 的 ai_agent_denial 表 append-only：DELETE 仅 resumeSession 路径，无 TTL/归档（C5 边缘/F4）

`DBDenialLedger.recordDenial`（DBDenialLedger.java:139-198）每次拒绝 INSERT 一行，唯一删除入口 `reset`（L216-238）仅被 `AgentSessionLifecycle` resumeSession（L322）调用；终态/被放弃会话的拒绝行永久留存于 `ai_agent_denial` 表。单行小、依赖 per-session 索引 COUNT（isPaused/getDenialCount L201-214 每次 COUNT 扫描），长时运行下行数与索引无界增长拖慢 COUNT 与磁盘。与 C5-009 的 `ai_agent_checkpoint` 表无清理问题同族（自建表 store 层均无保留策略声明）。建议补基于会话终态/时间阈值的清理任务（挂 `ScheduledRecoveryManager` 调度节拍），或在 store-layer-contract / owner doc 声明运维清理边界。

### [nop-ai-core-nop-ai-agent-CL-050] ai_agent_message 表 append-only：CONSUMED 行无清理/归档，DB 传输账本只增不删（C5 边缘/F4）

- **现象**：`DBMessageService` 全文件 grep 无任何 DELETE——`markConsumed`（DBMessageService.java:493-515）仅 UPDATE 置 STATUS_CONSUMED，消费完成后消息体（完整 envelope JSON，含 payload 全文）永久留存于 `ai_agent_message` 表；`sendAsync`（L254-294）每消息 INSERT。
- **判定**：PENDING 扫描有 `IDX_*_TOPIC_STATUS` 索引保护（AiAgentMessageTable.java:51-52）故查询不退化，但表体 + MESSAGE_BODY CLOB 随业务量无界增长（磁盘 + 备份面）。
- **同族**：与 CL-049（ai_agent_denial）、C5-009（ai_agent_checkpoint）同族——自建表 store 层普遍无保留策略。
- **建议**：终态消息按时间阈值批量清理（挂 poller 既有 sweep 节拍，`sweepStaleClaimedMessages` 同款模式）或声明运维清理边界。

### [nop-ai-core-nop-ai-agent-CL-051] DbUsageRecorder 的 NopAiChatResponse 用量账本 INSERT-only：每 LLM 调用一行无 TTL/归档（C5 边缘/F4）

- **现象**：`DbUsageRecorder`（usage/DbUsageRecorder.java:76 INSERT INTO NopAiChatResponse）每次 LLM 调用记录一行用量（session/agent/model/tokens/时长），无任何 DELETE/归档路径。
- **判定**：计费/审计账本 append-only 本身是常见语义，但与 CL-049/CL-050 同为自建表无保留策略声明族，且本表增长速率 = LLM 调用速率（全模块最高频写面）；行小但量极大，高频 agent 部署百万级调用即 GB 级表。
- **建议**：按时间分区/滚动归档（月表或 created_at 阈值清理），至少在 store-layer-contract 声明该表运维边界。

### [nop-ai-core-nop-ai-agent-CL-052] InMemoryTeamManager disband 后 Team 永驻（保留意图已声明但无包络）+ disband 不解绑 sessionToTeamId（C5 边缘/F4）

- **现象**：`InMemoryTeamManager.disbandTeam`（InMemoryTeamManager.java:168-186）仅状态翻转 + disbandedAt 记录，javadoc L46-49 声明「the team remains in the registry (queryable for history/audit)」——保留意图已声明（故不作 C5 发现），但无任何 purge/TTL/容量包络。
- **复合面**：teams map 随建团总数无界（每 Team 含成员表 + spec）；`sessionToTeamId` 在 disband 时也不解绑（只有 removeMember L247 与 rebind L298 清理），已散团队成员的会话反向索引条目滞留。
- **DB 面**：`DbTeamManager` 同样仅 member 行有 DELETE（L508），team 行永不删除。
- **建议**：①补 `purgeDisbandedBefore(timestamp)` 归档 API + owner doc 包络声明；②disband 时遍历成员解绑 sessionToTeamId。

### [nop-ai-core-nop-ai-agent-CL-053] 会话 save 路径每 save 全前缀指纹重算（每消息一次完整 JSON 序列化）+ getMessages() 防御性全拷贝——O(n²) 分配抖动（C5 边缘/热路径浪费）

- **现象**：`ChatMessageFingerprints.fingerprint`（ChatMessageFingerprints.java:48-59）= `JsonTool.stringify(message)` 全序列化 + FNV 全串扫描，javadoc L26-28 自认「deliberately traded I/O savings... for deterministic mutation detection」——CPU 权衡已声明，分配面未声明。
- **放大链**：DBSessionStore.tryAppend（DBSessionStore.java:373-377）/ FileBackedSessionStore.tryAppend（FileBackedSessionStore.java:342-346）每 save 对全前缀逐消息重算 → n 消息会话每次 save 产生 n 个完整消息 JSON 瞬时串（LLM_TURN 消息可达数十 KB）；叠加 `AgentSession.getMessages()`（AgentSession.java:240-242）每次调用防御性 `new ArrayList` 全拷贝、每 save 至少 1-2 次 → 长会话累计 O(n²) 分配抖动，轮界 save 是主路径。
- **建议**：①指纹缓存按消息引用 identity 缓存（脏检查用 identity 侧表），仅对新消息/未见引用重算；②`getMessages` 在 save 路径提供免拷贝只读视图（包内 `messagesView()`）。

### [nop-ai-core-nop-ai-agent-CL-054] 工作记忆读路径全量 loadAll 放大：AdapterBackedAiMemoryStore.readBudgeted/clear/size 每调用全表物化（C5 边缘/F7）

- **现象**：`AdapterBackedAiMemoryStore.readBudgeted`（AdapterBackedAiMemoryStore.java:153-182）每次读先 `storage.loadAll(null)` 全量物化再内存分桶预算；`clear`（L227-237）逐条 remove 全表往返；`size`（L239-241）/`getLastN`（L121-131）/`keywordSearch`（L277-284）同型全量。
- **触发面**：系统提示词自动注入每次执行调用 readBudgeted（AgentSessionLifecycle:206-207）——记忆条目多的高频会话每轮全表物化（条目级容量无上界，见 C5-020 失效面④的声明边界注记）；DB/向量后端下即每轮全表 SELECT。
- **建议**：storage adapter 增加 type/priority 投影下推与 limit 支持，readBudgeted 分两段查询（pinned 全量 + others 按 token 预算采样）；clear 改批量 DELETE。

---

## 维度复核结论（WI 3.1 C5 独立复核，2026-10-20）

- **复核性质（续作说明）**：前轮独立复核在状态行回填后被中断且其回填未落盘（盘面 22 条 `复核状态` 行恢复为「未复核」）——本复核按 Phase 4 口径**全量重做**，非增量抽验：22 条发现全部回码重验（file:line 逐一在审计工作树命中），超出任务要求的「P1 全部 + P2/P3 抽验 ≥7」配额。
- **复核方法**：逐条回码独立验证证据行与触发链；关键旁证独立取证（缺省装配点、调用方宽化 grep、兄弟实现对照、git -S 追溯、javadoc 契约文本）；对照 `ai-dev/audits/cra-index.md` WI 1.1–2.5 台账完成查重与同案登记；同单元 C1/C3/C4/C7 维度文件的发现标题用于跨维同站点对账。原条目正文未修改，仅在条目末尾回填复核状态行 + 追加本节。
- **总判定**：保留 19 / 降级 1（C5-022 P2→P3）/ 驳回 1（C5-019）/ 待定 0。第 1 轮 22 条中 20 条证据核验全部通过（无 file:line 失实、无时序虚构、无严重度虚报）；1 条驳回系初审 grep 失误（核心事实证伪）；1 条降级系缺省装配面校准（opt-in 组件口径）。
- **现状标注口径**：21 条保留/降级发现全部为**新发现**——cra-index（WI 1.1–2.5 台账 + P1 汇总 + CL 台账）无任何 nop-ai 单元登记项（与同单元 C1 复核结论一致）；无已知未修复、无已修复。**零 P0**：无「声明包络内必然 OOM / 主路径即泄漏」级站点——四条 P1 均为「长会话/高会话流失率下必然触发的无界增长」档（与共享提示词 P1 校准一致）。

### 逐条判定表

| ID | 判定 | 最终严重度 | 现状标注 | 一句话依据 |
|----|------|-----------|---------|-----------|
| C5-001 | 保留 | P1 | 新发现 | 三容器无上界 + itemIndex 线上键 + 双层 request(MAX_VALUE) + stream 缺省 true 全部直核；响应侧无长度守卫，与同站点 C3-002/C4-002 判级一致 |
| C5-002 | 保留 | P2 | 新发现 | cache-ttl 默认 0 + 惰性单条过期 + md5 键无容量/清扫 + 全条物化直核；owner doc 声明的是过期机制语义而非容量包络 |
| C5-003 | 保留 | P2 | 新发现 | 每请求双文件写盘、无任何轮转/清理代码（grep 直核）；自管文件无外部轮转接管 |
| C5-004 | 保留 | P2 | 新发现 | 无条件全读后切片（峰值≈2×文件、与 limit 无关）+ 分页读行整读 + globGrep 自传 0 直核 |
| C5-005 | 保留 | P3 | 新发现 | parseMap 全量 boxed 树存活覆盖整个转换循环直核；转换正确、纯放大定性，P3 校准成立 |
| C5-006 | 保留 | P3 | 新发现 | promptCache 无容量配置 + workMode 未消毒直拼直核；常规部署键空间工程有界故 P3 |
| C5-007 | 保留 | P3 | 新发现 | 每次全量重解析 + 递归无 visited 直核；循环 ref 属配置缺陷输入、SOE 失败方式不可诊断 |
| C5-008 | 保留 | P1 | 新发现 | 整读+分段 ≈4× 瞬态 + byWatermark 全量常驻 + append-only 无轮转 + javadoc 自认 unbounded 全部直核 |
| C5-009 | 保留 | P2 | 新发现 | DB 实现无 remove 覆写 + default no-op 契约文本 + 9 个引擎调用点直核；缺省装配 NoOpCheckpoint 故非缺省面 P2（与 C7-013 P1 判级差留 7.1） |
| C5-010 | 保留 | P1 | 新发现 | 裸 HashMap + put List.copyOf + 无删除 API + 低触发线（30 条消息/0.8×128k）直核；终态仅 save 反向 pin，压缩即固化全量历史 |
| C5-011 | 保留 | P2 | 新发现 | 全文本 put + delete API 零生产调用方 + read-spill 仅 get 直核；行内预算是预览层面具、堆层无界 |
| C5-012 | 保留 | P2 | 新发现 | 邮箱双表仅 put 无 remove + 引擎构造点 + 终态清理清单不含直核；同站点 C3-016(P1) 双登记 |
| C5-013 | 保留 | P3 | 新发现 | 四表终态无清扫 + reset 仅 resume 路径 + WaitCoordinator/GoalTracker 无 remove API 直核；单条目小故 P3 |
| C5-014 | 保留 | P3 | 新发现 | 观测日志只增 + 读侧全量拷贝直核；仅声明保留意图未声明容量包络（方法论第 6 条），可选装配维持 P3 |
| C5-015 | 保留 | P1 | 新发现 | 三实现 remove 正确而全模块零调用（双轮宽化 grep）+ 缺省装配 InMemorySessionStore + 终态 save 反向 pin 直核；族首 P1 成立 |
| C5-016 | 保留 | P2 | 新发现 | 全表 CLOB SELECT 无状态过滤 + 逐行全量反序列化 + putIfAbsent pin 直核；恢复只需未终态子集 |
| C5-017 | 保留 | P2 | 新发现 | deadLetter 在 capacity 检查之外无 purge + inFlight 无 stale 回放 + DB 兄弟 5min sweep 对照直核；F3 对齐缺口成立 |
| C5-018 | 保留 | P2 | 新发现 | replySubscriptions put-only 无 cancel + senderId 派生键 + pendingRequests 对照组直核；同站点 C3-024(P1)/C7-026 双登记 |
| C5-019 | 已驳回 | —（原 P2） | 新发现 | **核心事实证伪**：releaseSession 实有 4 个执行出口 finally 接线（Engine:1019 + Lifecycle:393/559/958），git -S 证实自 2026-08-01 即在基线——「零调用/永久累积/僵尸假冲突」三主张全部不成立；残余 O(N) 扫描面由同站点 C1-026(P3) 覆盖 |
| C5-020 | 保留 | P2 | 新发现 | 接口无释放方法 + 两 provider 无移除 + 缺省装配（config:126）+ 两个每次执行必经调用点直核 |
| C5-021 | 保留 | P2 | 新发现 | tasks 唯一写入口无 remove + 全类方法面直核 + 缺省装配（Engine:654 空兜底 new）直核；同站点 C3-023/C4-012(P1) 双登记 |
| C5-022 | 已降级 | P3（原 P2） | 新发现 | 事实面全部直核（无删除 API/无 DELETE/全量拓扑重建），但缺省 teamTaskStore=NoOp（config:149）+ daemon 仅显式装配——opt-in 特性路径按同站点 C1-024(P3) 与本单元降档口径（先例 C1-005）降 P3；真实装配即回 P2 |

### F4 族裁定意见（C5 会话终态清理缺口族 × C1 无界账本族）

**裁定：同根成立、互补面各自保留，Phase 6 按物理站点归并单立案——不互相吸收、亦不重复计数。**

- **同根判定**：C5-009/012/013/015/020（C5-019 已驳回退出本族）与 C1-006/011/014/016/017/018/026 共享同一根因——引擎终态清理清单仅含 `checkpointManager.remove`（9 个调用点，且对 DB 后端因 default no-op 落空）+ `runningExecutions` + `runningInjectionTargets`，**无一触达** session store / memory provider / 邮箱双表 / 安全四表 / background registry。这是同一处修复点（终态清理挂钩扩清单）的多个受害载体。
- **互补面判定**：两维登记各有独立证据价值，均不因对方存在而失效——C1 面是**共享状态生命周期与清理不对称**（F8 视角：register/remove 契约、谁持有什么），判 P2/P3；C5 面是**内存有界性定量**（F4/F7 视角：条目体积 × 键基数 × 增长速率），判 P1~P3。两侧修复方向一致（终态统一挂钩 + 容器侧 remove 接线/补实现），无冲突。
- **归并操作建议（供 Phase 6 merge-adjudication）**：以 **C5-015 为族首**（唯一缺省装配 + 条目体积最大 + remove 已实现纯接线）；站点成员 = C5-009（↔C1-011、C7-013）、C5-012（↔C1-014 三分之一、C3-016）、C5-013（↔C1-006、C1-014 三分之二、C7-017、C3-017 异面）、C5-020（↔C1-016、C7-030）、C5-021（↔C1-017、C3-023、C4-012、C7-027）、C5-018（↔C1-018、C3-024、C7-026）、C5-022（↔C1-024，opt-in 外围）。跨单元对照 WI 1.1 C1-006 / WI 1.6 C7-001 / WI 1.7 C7-003（F4 账本族）：载体与模块不同，不重复立项，Phase 8 批量修复按同族统一清理策略设计（与 C1 复核结论的裁定一致）。
- **C5-019 驳回对族的影响**：writeIntentRegistry 是本族唯一**已正确接线 releaseSession** 的容器，不构成清理缺口；C1-026 的「路径键只增不删」应按**桶骨架级**（空桶保留，javadoc 已声明 monitor 稳定性动机）而非条目级理解——建议 Phase 6 对 C1-026 的表述作此口径注记（其 P3 判级不受影响）。

### 高风险条目子项复核说明

- **C5-001（P1）**：子项复核三点。①两处 `request(Long/Integer.MAX_VALUE)`（callStream 源订阅 + 聚合订阅）逐行命中，SubmissionPublisher 默认 256 有界缓冲的满载策略确被显式绕过；②`resolveMaxTokens` 只写请求体由 provider 自觉执行、响应侧无对应上界——「本地防线整体缺位」定性成立；③触发链依赖 attacker/失控 baseUrl（账号链特性）或超长正常响应 × 并发叠加，与同站点 C3-002(P1 背压)/C4-002(P1 publisher 不关闭) 构成三视角同站点，判级自洽。**P1 维持**。
- **C5-008（P1）**：子项复核物化链 4× 验算（readString→substring→split 段→每段 split+LinkedHashMap）与 `readAfter` 的 `unmodifiableList(all.subList(...))` 视图持底逐行成立；`byWatermark` 全量常驻是实现注释显式决定（audit 能力保留）但无规模包络；磁盘面 javadoc 自认 unbounded 且 owner doc 无声明。DB 后端同根面由 C7-013 单列。**P1 维持**。
- **C5-010（P1）**：子项复核触发频率——`messages > 30` 触发线极低（常规 agent 会话必然跨越），每次真压缩固化一份全量历史副本（≈0.5-1MB）且无删除 API、终态不清档——「防压缩 OOM 的机制自身成为 OOM 源」的论证在长会话下确定性成立。与 C7-031（spill/归档三件套长会话累积）为同链不同登记粒度，留 7.1 归并。**P1 维持**。
- **C5-015（P1）**：子项复核双轮 grep——第一轮按原条目四个模式、第二轮宽化 `.remove(sessionId)`/`.remove(sid)` 全模块，命中的仅三实现自身方法体与无关容器；`DefaultAgentEngine:146` 缺省 `new InMemorySessionStore()` 直核；终态路径 `sessionStore.save(session)`（Lifecycle:413 等）反而把终态会话 pin 回缓存。万级历史会话 GB 级驻留的验算成立。**族首 P1 维持**。
- **C5-019（驳回，本复核纠错）**：原条目三条核心主张（releaseSession 零调用 → 跨会话永久累积 → 僵尸意图永久假冲突）系同一条 grep 失误的连锁；实测 4 个执行出口 finally 均接线且注释自证「Safe to call on every exit path」，git -S 证实接线（2f32515894，2026-08-01）早于审计基线。按「核心事实证伪即驳回」口径驳回，残余面（单执行内同路径 O(N) 注册扫描）转 C1-026 口径。**初审证据失效为本次复核唯一驳回项**。
- **C5-022（降级，本复核纠错）**：事实面（无删除 API/无 DELETE/daemon 全量重建）全部成立，但原条目未核 `DefaultAgentEngineConfig:149` 缺省 `NoOpTeamTaskStore` 与 daemon 仅显式装配两处装配事实——组件为 opt-in 特性路径，按同站点 C1-024（P3）与本单元 C1-005 降档先例校准 P2→P3，附激活回升条款（装配真实 store + daemon 即回 P2）。**非证据错误，为装配面校准遗漏**。

### 同案命中清单（cra-index 与跨维对照）

| 本维条目 | 台账同案/关联 | 关系说明 |
|---------|--------------|---------|
| C5-001 | 同站点跨维 C3-002(P1)/C4-002(P1) | 三视角同站点（背压/句柄/内存界），7.1 并单单立案 |
| C5-002、C5-003、C5-004 | 同维相邻 C7-008/009/010 | 同站点跨维双登记（磁盘无界 vs 过期语义/保留期/读取量），7.1 归并 |
| C5-009 | 同站点 C7-013（P1）、C1-011（P2） | 三维同站点；判级差（P1/P2/P2）留 7.1 归并裁定（本维按缺省 NoOp 装配校准 P2） |
| C5-010、C5-011 | 同链 C7-031 | 归档三件套的 C5 定量面，7.1 归并 |
| C5-012 | 同站点 C3-016（P1）、C1-014（部分） | 双/三登记，7.1 并单 |
| C5-013 | 同站点 C1-006、C1-014、C7-017；C3-017（WaitCoordinator 陈旧 deadline 异面） | 治理注册表族，7.1 归并 |
| C5-015、C5-016 | 同根 C7-028（含恢复全量扫描面）；CL-048（recovery SELECT 无 LIMIT 仅 ID 面，不同面） | 会话终结协议族，C5-015 为族首 |
| C5-017 | CL-046（FAILED actor 不 nack→inFlight 滞留）、CL-050（ai_agent_message 表）；C3-016 | mailbox 三面叠加，7.1 归并 |
| C5-018 | 同站点 C3-024（P1）、C7-026、C1-018 | replySubscriptions 四登记，7.1 并单 |
| C5-020 | 同站点 C1-016、C7-030 | memory provider 三登记，7.1 归并 |
| C5-021 | 同站点 C3-023（P1）、C4-012（P1）、C7-027、C1-017 | BackgroundTaskRegistry 五登记（timeout 无执行点面归 C4-012 主处置），7.1 并单 |
| C5-022 | 同站点 C1-024（P3）；CL-052（InMemoryTeamManager） | team 存储族 opt-in 面，7.1 归并 |
| F4 族整体 | 跨单元 WI 1.1 C1-006、WI 1.6 C7-001、WI 1.7 C7-003 | 载体与模块不同不重复立项；Phase 8 批量修复按同族统一清理策略设计（对齐 C1 复核裁定） |
| WI 2.4 C5 族对照 | WI 2.4（nop-persistence-rw）C5 立项集中在 ORM 读取面（全量物化/装箱放大），无会话键容器族 | 无同案：本维 F4 族载体均为 nop-ai-agent 自建容器，不与 2.4 重复 |
| WI 2.1 CL-021 移交面 | raw JDK/Netty 载体绕过设施（点名 ai-agent） | 关联非同案：本维 22 条无载体治理发现；C5-001 的 SubmissionPublisher 属流式原语使用面，修复批次可与 CL-021 nop-ai-agent 移交面同批清扫 |

## 最终保留项（21 条：保留 19 + 降级 1 并入；驳回 1 不列入；最终严重度分布 P1×4 / P2×11 / P3×6）

| ID | 标题 | 最终严重度 | 现状 | 同案指针 |
|----|------|-----------|------|---------|
| C5-001 | 流式响应聚合无界累积 + 双层订阅关闭背压 + 线上可控键累加器无上界 | P1 | 新发现 | 同站点 C3-002/C4-002（7.1 并单） |
| C5-008 | Checkpoint journal 恢复路径全量物化（~4×）+ byWatermark 全量常驻 + journal 磁盘无界 | P1 | 新发现 | C7-013（DB 后端同根）；F4 跨单元族对照 |
| C5-010 | 压缩快照档案全量累积：每次 compaction 固化全量历史副本，无上界无删除 | P1 | 新发现 | C7-031 同链；族成员（族首 C5-015） |
| C5-015 | 会话存储三实现 put-only：终态清理不含 sessionStore，全历史会话堆内永驻 | P1 | 新发现 | **F4 族族首**；C7-028 同根；C1-012/C1-021 同文件异面 |
| C5-002 | AI 响应缓存磁盘无界：TTL 缺省关闭 + 惰性单条过期 + 无容量上界 + 整条物化 | P2 | 新发现 | C7-008 同站点；CL-041 同文件安全面 |
| C5-003 | Chat 请求/响应日志按请求双文件落盘无轮转/清理，磁盘无界 | P2 | 新发现 | C7-009 同站点 |
| C5-004 | 文件操作符全量物化族：切片先整读/分页整读/grep 整读/globGrep 清单无上界 | P2 | 新发现 | C7-010 同站点 |
| C5-009 | DBCheckpointManager 未覆写 remove：终态清理落空，三张会话键缓存永久累积 | P2 | 新发现 | C7-013(P1)/C1-011(P2) 同站点；判级差留 7.1 |
| C5-011 | 超大工具结果 spill 驻留：全文本写入无上界 store 读后不删，预算只约束行内预览 | P2 | 新发现 | C7-031 同链；CL-046 关联 |
| C5-012 | engine 会话邮箱双表 put-only：无任何生命周期清理 | P2 | 新发现 | C3-016(P1)/C1-014 同站点（7.1 并单） |
| C5-016 | restorePendingSessions/listAllSessions 全量物化：DB 全表 CLOB 扫描 + 缓存全量 pin | P2 | 新发现 | C7-028 恢复扫描面；CL-048 异面 |
| C5-017 | DeferredAckMailbox：deadLetter 无界永驻 + inFlight 无崩溃回收 + 默认无界 pending | P2 | 新发现 | C3-016；CL-046/CL-050 |
| C5-018 | LocalAgentMessenger.replySubscriptions：caller 可控键只增不撤，泄漏传导平台消息服务 | P2 | 新发现 | C3-024(P1)/C7-026/C1-018 同站点（7.1 并单） |
| C5-020 | memory provider 会话键映射无释放契约：接口无 remove，缺省装配按会话永久累积 | P2 | 新发现 | C1-016/C7-030 同站点（7.1 归并） |
| C5-021 | DefaultBackgroundTaskRegistry.tasks put-only：终态 Entry 跨任务永久累积 | P2 | 新发现 | C3-023/C4-012(P1)/C7-027/C1-017 同站点（7.1 并单） |
| C5-005 | Embedding 响应全量物化 + 装箱中间树 5-8× 放大 | P3 | 新发现 | — |
| C5-006 | 系统提示词缓存无容量配置且键空间随 workMode 无界扩展 | P3 | 新发现 | CL-043 同文件穿越面 |
| C5-007 | AiXDefHelper xdef:ref 循环引用无环守卫：无限互递归至 StackOverflowError | P3 | 新发现 | — |
| C5-013 | 会话键安全/可靠状态图无终态清扫族（四张 singleton 表仅 resume 路径有 reset） | P3 | 新发现 | C1-006/C1-014/C7-017 同站点（7.1 归并） |
| C5-014 | AgentActor.receivedMessages 观测日志无界增长 + 快照读全量拷贝 | P3 | 新发现 | CL-046 关联 |
| C5-022 | team 任务存储无删除 + scheduler 全量拓扑重建 + teamIndex O(n²) 拷贝写 | P3（原 P2，降级） | 新发现 | C1-024 同站点；CL-052；装配真实 store + daemon 即回 P2 |

> 修复立项建议（供 7.1/Phase 8 参考）：四条 P1 中 C5-015 为 F4 族修复锚点（终态清理挂钩扩清单 + 三 store remove 接线，一次改动收口 C5-009/012/013/020 及 C1 族同名站点）；C5-001 与 C3-002/C4-002 同 PR 三面收口（累积上界 + 背压窗口 + publisher 生命周期）；C5-008/010 按 journal 轮转 + 归档容量上界分立修复。P2 顺位：C5-016（投影/过滤即消除大头，与 C5-015 同 diff 面）→ C5-017（deadLetter 上界 + stale 扫描对齐 DB 兄弟）→ C5-018/C5-021（引用计数撤订 / 终态 TTL 移除）。降级与驳回件（C5-022/C5-019）不进修复队列，分别留激活条款与 C1-026 口径注记。
