# C3 深挖第 2 轮 — nop-ai-rest（WI 3.4，仅 rag）

- **审计日期**: 2026-10-20
- **审计员**: C3 深挖第 2 轮子代理（合并批 C1/C3[rag]/C6 之 C3 部分；盲审，仅读派单指定的第 1 轮三文件）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀 + C3 正文 + 附录 A 13 字段
- **编号**: 续接第 1 轮 C3-001…C3-004，本轮从 C3-005 起；`PgVectorStore.schemaReady`（C1-002/C3-004/C6-001 三登记）不再重复

## 深挖范围与方法

按派单四个深挖面逐一审读（全部 rag 源码 + 契约接口按「只审使用方式」口径）：

1. **RagSynthesizeService 的 LLM 调用链异常传播**：`chatService.call(request, null)`（L46）的异常路径有二——(a) 阶段异常（`FutureHelper.syncGet` 解包后以 NopException 抛出，向上传播到 biz/GraphQL，fail-loud 正常）；(b) **调用成功返回但 `ChatResponse` 本身是错误响应**（`error != null` / `isSuccess() == false`，nop-ai-api 的设计内返回形态：`ChatResponse.error` 两工厂 L330/L346、`errorClassification` javadoc L81-88 明示「core 产出 → ChatResponse 携带 → agent 消费」信号通路）——`synthesize` 不检查 `isSuccess()`，`outputText()`（`ChatResponse.java:129-148`，messages 为空返回 null）→ `answer == null` 兜底 `""`（L47-48）→ **provider 失败静默降级为空字符串**，立案 C3-005。
2. **EmbeddingModelBasedClassifier 分类链**：构造器（L50-57）经 `EmbeddingExampleConfig.getExampleEmbeddings`（L55-63）同步预嵌入全部示例（`embedAll` = `FutureHelper.syncGet`，同族站点，并入扩展 C3-003）；`classifyDocument`（L65-93）对每标签求 mean/max 分——**空 examples 列表触发 `meanScore /= 0` → NaN → `NaN >= minScore` 恒 false → 标签静默消失**（F9），立案 C3-006。
3. **NopAiRagBizModel 入口异步边界**：三 action（ingestDocument/search/synthesize）均为同步方法，自身无 CompletableFuture/线程创建；异步边界即第 1 轮 C3-003 已立案的 SPI 默认方法 syncGet 桥接（biz 无新增异步面）。附带装配事实：biz 构造器 inline `new RagIngestService(new SimpleTextSplitter(), ...)`（L33-34），`rag-defaults.beans.xml` 仅注册 biz/VectorStore/TextEmbedding 三 bean——`RagIngestService` 的 `@Singleton/@Inject` 装配元数据在当前部署为死配置，`IAiTextSplitter` 的 IoC 定制不可达（非并发装配线索，CL-016）。
4. **C3-003 sync-over-async 同族其他站点**：全模块 `embed|embedAll|call(` 调用点穷举——第 1 轮清单（synthesize:46 / search:34 / ingest:68）之外发现 **classifier 链 2 站点**（`EmbeddingModelBasedClassifier.java:66`、`EmbeddingExampleConfig.java:58`，后者在构造器路径）——以「扩展 C3-003」并入；rag 内 syncGet 桥接站点总账至此闭环为 5 处。

**上下文传播复核**：classifier 两个新站点的回调只做数组转换/集合装配，不触 `ContextProvider`/ThreadLocal——维持第 1 轮「零暴露」排除结论。

## 新增发现

### [C3-005] synthesize 不检查 ChatResponse.isSuccess——LLM provider 错误响应被静默降级为空字符串

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/synthesize/RagSynthesizeService.java:44-48`；契约依据 `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatResponse.java:73-100`（error/errorCode/errorClassification/retryAfterMs 字段）、`:283-285`（isSuccess）、`:330-331/:346-357`（error 工厂）
- **证据片段**:
  ```java
  ChatResponse response = chatService.call(request, null);        // :46
  String answer = response != null ? response.outputText() : null; // :47 —— 不看 isSuccess()
  return answer != null ? answer : "";                             // :48 错误响应 → ""
  // ChatResponse.java:129-148：outputText() 在 messages 为空（错误响应的常态）时返回 null
  // ChatResponse.java:283-285：isSuccess() = error == null
  ```
- **严重程度**: P2（生产装配请求主路径；可达性条件化——取决于 nop-ai-core 的 ChatServiceImpl 在重试/回退耗尽后**返回 error ChatResponse** 还是抛异常，本批范围外；但 error-response 是 nop-ai-api 的设计内返回形态而非理论构造）
- **并发场景**: 不依赖并发；失败传播面——LLM provider 429/401/5xx 经 `dialect.parseErrorResponse` 规范化为 error ChatResponse（`ErrorClassification` RETRY/STOP/FALLBACK，javadoc L81-88 明示该形态「供上层可靠性层消费」）时，rag 是该响应的消费方却不消费任何错误信号。
- **触发时序**: T1: 请求线程 `synthesize("idx","q",4)` → `chatService.call` 完成为一个 **error ChatResponse**（如 `errorClassification=RATE_LIMITED`、`httpStatus=429`、messages 为空）→ T2: L47 `outputText()` 返回 null → L48 返回 `""` → GraphQL 200 成功返回空串。调用方（前端/上游 agent）无法区分「模型回答为空」与「provider 限流/鉴权失败」；检索嵌入已计费、LLM 调用已计费，失败对监控与调用方均不可见（F9 静默吞失败 + F2 半个成功：检索成功、合成失败无中间态标记）。
- **现状**: 模块其余入口均为 fail-loud（空 query 抛 `ERR_AI_RAG_EMPTY_QUERY`，`AiModelTextEmbedding` javadoc :23-24 明文反对静默退化），唯独最外层合成出口对 LLM 失败静默；`errorClassification`/`retryAfterMs` 的重试信号在 rag 层无任何消费点。
- **风险**: 检索投毒/配额耗尽/凭证失效等故障表现为「RAG 永远返回空答案」的静默质量劣化，无错误码、无告警面、无重试入口；上游对话 agent 把空串当模型观点继续推理。
- **建议**: L46-48 增加 `if (response == null || !response.isSuccess()) throw new NopException(ERR_AI_RAG_SYNTHESIZE_FAILED).param(ARG_DETAIL, response.getError())`（错误码按 `NopAiRagErrors` 新增，携 `errorClassification`/`retryAfterMs` 供上层重试决策）；或在 rag 层消费 `errorClassification==RETRYABLE` 做一次受控重试。禁止以 `""` 兜底错误响应。
- **失效面枚举**（F3 全类清扫）: rag 内 `chatService.call` 消费点全仓 grep 仅 `RagSynthesizeService:46` 一处（缺陷本体）；`ChatResponse` 的其他消费形态——`outputToolCalls/getFullContent`（api 根，无 rag 调用方）；跨模块同族消费点（nop-ai-agent，他批范围仅登记）：agent 侧按 `isSuccess()`/`ErrorClassification` 消费为正确示范。同步姊妹出口 `RagSearchService.search`（无 LLM 调用，N/A）。
- **信心水平**: 缺陷形态确定；触发依赖 core 返回 error-response 形态为很可能（API 契约与 javadoc 证据链支持，实现证据在本批范围外）
- **误报排除**: 非「吞异常」泛化误报——这里没有任何异常被抛出，失败以**合法返回值**形态流入，恰因无异常而绕过全部 fail-loud 通路；非已声明边界——无 owner 文档声明 synthesize 允许把 provider 失败折叠为空串。
- **复核状态**: 未复核
- 复核状态: **保留并升格(P1)**｜新发现·已知未修复——初审「可达性条件化」经复核解除：ChatServiceImpl.callAsync 非 200 分支明文「响应级错误走 ChatResponse，传输级错误才抛异常」并 `future.complete(errResponse)`（ChatServiceImpl.java:146-149）；缺省 `stream=true` 路径（:117-123，rag 传 null options 即命中）的 onError 对响应级错误同样 complete 错误响应（:417-427）；5 方言均有 error 产出点。provider 429/401/5xx 下 synthesize 确定性以 200 成功返回空串——「检索成功、合成失败无中间态标记」命中 F2 半个成功（判级表 P1 条款）+ F9；同族先例 WI 3.1 C7-002 / WI 3.3 C3-001 均 P1。rag 请求触达面不降档。详见 C3-async-chains.md 复核节

### [C3-006] EmbeddingModelBasedClassifier 对空 examples 标签除零得 NaN——标签静默消失而非报错

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/core/api/classifier/EmbeddingModelBasedClassifier.java:69-84`（除零点 :78；过滤点 :81-83）；配置源 `classifier/EmbeddingExampleConfig.java:21, 55-63`
- **证据片段**:
  ```java
  exampleEmbeddingsByLabel.forEach((label, exampleEmbeddings) -> {
      double meanScore = 0;
      ...
      for (VectorData exampleEmbedding : exampleEmbeddings) { ... }
      meanScore /= exampleEmbeddings.size();          // :78 空列表 → 0/0 = NaN

      double aggregateScore = aggregatedScore(meanScore, maxScore);  // NaN 传播
      if (aggregateScore >= minScore) {               // :81 NaN >= x 恒 false
          scoredLabels.add(new ScoredLabel(label, aggregateScore));
      }
  });
  ```
- **严重程度**: P3（Reserved 零生产消费者条件化——类 javadoc 明示全仓 main/test 零 import，SPI 参考实现；一旦接线即升 P2 面）
- **并发场景**: 不依赖并发；失败传播面——`EmbeddingExampleConfig.examplesByLabel` 为 `@DataBean` 可配置 Map，label→空 List 是合法配置（XML/JSON 装配可自然产生）；无任何入口校验拒绝空示例集。
- **触发时序**: 单线程确定性复现：T1: 以 `{labelA:["a"], labelB:[]}` 构造 `EmbeddingExampleConfig` → classifier 构造器对 labelB 预嵌入得空列表 → T2: `classifyText("x")` 对 labelB 执行 :78 `0/0` → `aggregateScore=NaN` → :81 判定 false → labelB 从结果中**无声消失**——调用方拿到的是「labelB 不相关」而非「labelB 配置缺失」，分类结果系统性偏差无任何日志/异常。
- **现状**: langchain4j 同源实现假设 examples 非空；本移植未加 Guard。NaN 不会进入排序比较器（:81 已过滤），故无 TimSort contract 风险——缺陷限于静默丢标签；姊妹路径 `getExampleEmbeddings` 对 label 集为 null 的配置则直接 NPE（fail-loud），两相邻路径失败语义不一致。
- **风险**: 分类器接线后，配置错误的标签（空示例）表现为分类能力静默缺失，排查成本高（无信号点）。
- **建议**: 构造器或 `getExampleEmbeddings` 入口 `Guard.notEmpty(examples, "examples of label " + label)` fail-loud（对齐模块 ERR_AI_CORE_INVALID_ARGUMENT 风格）；或在 :78 前跳过空标签并 `LOG.warn` 计数。
- **失效面枚举**（F3 全类清扫）: `classifyDocument` 内数值面全清单——:78 除零（缺陷本体）；:74-77 max/mean 累加（空列表安全跳过）；:89 `comparingDouble(1 - score)`（NaN 不可达——:81 过滤在前，安全）；`CosineSimilarity.between`（维度不匹配抛 `ERR_AI_VECTOR_LENGTH_MISMATCH`，fail-loud 正确）；`RelevanceScore.fromCosineSimilarity`（纯算术，N/A）。家族内仅 :78 一处。
- **信心水平**: 确定（单线程确定性路径）
- **误报排除**: 非「数学边界自然行为」——同一表达式对配置错误的反应是静默改变分类语义而非报错，违背模块 fail-loud 基调；非测试专用代码（main 源码根的公共 SPI 实现，Reserved 裁定已判级条件化）。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现·已知未修复（Reserved 零生产消费者条件化成立：类 javadoc L36-40 Reserved 裁定亲证；NaN 路径 L69-84 与空 examples → embedAll 空列表链亲证；接线即升 P2 面的条件标注成立）

## 扩展已有发现

### 扩展 C3-003 —— classifier 链 2 个同族 sync-over-async 站点（第 1 轮 F3 清单遗漏）

- **文件**: `io/nop/ai/core/api/classifier/EmbeddingModelBasedClassifier.java:66`；`classifier/EmbeddingExampleConfig.java:55-63`（:58 `embedAll` 在 **classifier 构造器路径**）
- **补充证据**: `embeddingModel.embed(document, embeddingOptions)` / `embeddingModel.embedAll(...)` 均经 `IEmbeddingModel` 默认方法 `FutureHelper.syncGet(embedAsync(...))`（`nop-ai-core IEmbeddingModel.java:29-35`）同步阻塞收尾，与 C3-003 已立案三站点同根：无超时包装 + 无取消面。构造器路径站点在 bean 装配期执行时属低频（降档不减），但 `EmbeddingModelBasedClassifier` 若按参考实现被 per-request 构造，则预嵌入全部示例为每请求阻塞点。
- **附加 SPI 观察登记**（nop-ai-core 范围，供交叉引用）: `IEmbeddingModel.embedAsync/embedAllAsync` 签名**无 ICancelToken 参数**（对照 `IChatService.callAsync(request, cancelToken)` 有）——嵌入链的取消面在 SPI 设计层即缺失，rag/分类器侧无从透传取消信号；C3-003 建议中「透传 ICancelToken」对嵌入链需先改 SPI。
- **失效面更正**: C3-003 失效面枚举中「rag 内 syncGet 站点 = 3 处」应更正为 **5 处**（+classifier 2 处）；其余不变。

## 维度集外线索（overlay）

（编号续接全维度共享流水；CL-015 见 C6-round2。）

### [nop-ai-rest-CL-016] （归 C9/装配一致性，非并发）NopAiRagBizModel 硬编码 SimpleTextSplitter——RagIngestService 的 @Singleton/@Inject 装配元数据为死配置

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/biz/NopAiRagBizModel.java:31-37`；对照 `ingest/RagIngestService.java:25, 33-39`；装配 `resources/_vfs/nop/ai/rag/beans/rag-defaults.beans.xml`
- **线索**: biz 构造器 inline `new RagIngestService(new SimpleTextSplitter(), embeddingModel, vectorStore)`，无视 `RagIngestService` 上的 `@Singleton + @Inject` 构造器（其声明的 `IAiTextSplitter` 注入点在当前部署不可达——beans.xml 未注册该 bean，且即便注册，biz 也用的是自己的手工实例）。后果：平台/集成方注册的 `IAiTextSplitter` 定制实现（如 code-analyzer 的 `JavaFileSplitter`）对 RAG 摄取切分永不生效；512 chunk 硬编码于 `DEFAULT_CHUNK_SIZE`。无并发后果（两实例均无状态），归装配一致性线索，移交 owner 批次裁定（删除死注解 or biz 改注入 IAiTextSplitter）。

## 继续判定

- 新增发现：2 条（P2×1 = C3-005；P3×1 = C3-006）；扩展 1 条（C3-003 站点清单 3→5 + SPI 无取消面观察）；无 P0/P1。
- rag 全模块异步站点总账闭环：`CompletionStage` 委托 2 处（embedAsync/embedAllAsync，C3-001/002）+ syncGet 桥接 5 处（C3-003 及扩展）+ biz 入口 0 新增——无未审异步站点；派单四个深挖面全部收敛。
- **判定：终止信号**——新增全为 P2/P3 且异步面清单已闭环（收敛证据：全模块 grep 穷举 `CompletableFuture|CompletionStage|embed|call\(|thenApply|syncGet` 无残余站点），无需第 3 轮 C3。

---

## 复核结论（WI 3.4 独立复核，2026-10-20）

- **C3-005：保留并升格 P2→P1**。初审的可达性条件（「取决于 core 返回 error ChatResponse 还是抛异常」）经复核以三步证据链闭合：方言产出 error 响应 → callAsync 非 200 与缺省 stream 路径均 `complete(errResponse)` 正常返回 → rag 折叠为 `""` 成功返回。升格依据 = F2 半个成功判级条款 + 同族先例对齐（WI 3.1 C7-002 / WI 3.3 C3-001 均 P1）。详见 C3-async-chains.md 复核节。
- **C3-006：保留(P3)**——Reserved 条件化与 NaN 路径均亲证成立。
- **扩展 C3-003（classifier 2 站点 + SPI 无取消面观察）复核确认**：5 站点总账经全量 grep 复核闭合无误；SPI 取消面缺失的交叉登记保留。
- 本轮终止信号复核成立：新增 2 条均已落定、异步站点总账闭合，无重启深挖事由。
- 全维度复核裁定与工单结构见 C3-async-chains.md「维度复核结论」（C3-004 吸收裁定、CL-008 吸收、CL-006/007 移交维持）。
