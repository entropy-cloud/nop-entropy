# DeepWiki Plan — nop-batch

> Status: approved
> Target: /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-batch @ HEAD
> Depth: standard（受限页面集：恒含 5 + flows 2 + modules 2 + topics 1 = 10 页）
> Language: zh
> Coverage: 模块 252 个源文件（core 85/dsl 55/exp 23/dao 23/gen 15/biz 15/api 12/jdbc 8/service 8/orm 7，meta/sys/web 为空壳）在扫描范围

## 1. 概念分析（两段式压缩结论）

**形态判定**：`framework-repo`（chunked 批处理框架的实现仓库，类 mini-Spring-Batch）——叙事=框架内部机制（chunk 读写消费管线、任务构建、断点续跑），不讲"如何用 nop-batch 做批处理"。

**领域概念**：批处理抽象为 Loader（读）→ Processor（处理）→ Consumer（写）三段 Provider 管线（IBatchLoaderProvider 31 / IBatchProcessorProvider 16 / IBatchConsumerProvider 36），执行期上下文 IBatchChunkContext(67)/IBatchTaskContext(53) 贯穿；IBatchTask(9) 为任务抽象，BatchTaskBuilder(456 行) 组装，BatchTaskContextImpl(607 行) 核心状态。DSL 线：BatchTaskModel + ModelBasedBatchTaskBuilderFactory(741 行) + batch.xlib/batch-record.xlib/batch-gen.xlib 三件套（XLang 集成）。可靠性资产：NopBatchTask/NopBatchRecordResult/NopBatchTaskState/NopBatchTaskVar（DAO 实体，断点续跑与记录追溯）+ IBatchRecordHistoryStore(9)/IBatchRecordFilter(9)/BatchCancelException(9) 取消链。exp 模块（ImportDbTool 416/ExportDbTool 392）是框架之上的导入导出应用工具。

**核心数据流**：任务定义（DSL 或编程式）→ BatchTaskBuilder 构建 IBatchTask → BatchTaskContextImpl 驱动 chunk 循环（Loader 出数据 → Processor 加工 → Consumer 写出）→ record 结果落库 → 完成/取消（BatchCancelException）。
**核心子机制**：断点续跑与记录（record/result/history 三实体 + TaskState + 重入）。
**关键系统**：DSL 模型线（xdef/xlib → Model → Factory）与 exp 工具线（Import/Export DB）。

## 2. 模块地图（证据来源，非章节轴）

| 模块 | 职责 | 关键类型（fan-in） | 供证页面 |
|------|------|-------------------|---------|
| nop-batch-core | chunk 管线执行引擎（85 文件） | IBatchChunkContext(67)/IBatchTaskContext(53)/BatchTaskContextImpl(607行)/BatchTaskBuilder(456行)/PartitionDispatchQueue(350行) | flows/batch-pipeline, modules/batch-core |
| nop-batch-dsl | DSL 模型与 XLang 集成（55 文件） | BatchTaskModel, ModelBasedBatchTaskBuilderFactory(741行), batch*.xlib | modules/batch-dsl |
| nop-batch-dao/orm/jdbc | 任务/记录/断点落库 | NopBatchTask, NopBatchRecordResult, NopBatchTaskState | flows/checkpoint-recovery |
| nop-batch-api | 对外契约（12 文件） | NopBatchTaskOutputBean(485行), NopBatchTaskInputBean(379行) | modules/batch-core |
| nop-batch-exp | 导入导出工具 | ImportDbTool(416行), ExportDbTool(392行) | overview 带过 |
| nop-batch-biz/service/gen | 业务/服务/生成 | — | modules/batch-dsl 带过 |
| 平台消费 | XLang xlib 三件套 | batch.xlib | modules/batch-dsl |

## 3. 页面契约（路径锁定）

| 路径 | 所属章 | 标题 | 职责 | 源文件（≥5） | relatedPages | 计划图表 |
|------|--------|------|------|--------------|--------------|----------|
| overview.md | 指南 | nop-batch 总览：chunked 批处理框架 | 定位/形态/能力边界/关键数字 | BatchTaskBuilder, IBatchChunkContext, BatchTaskModel, NopBatchTask, pom | architecture, flows/batch-pipeline | flowchart |
| quickstart.md | 指南 | 快速上手 | 构建/测试/最小批任务定义 | pom, BatchTaskBuilder, BatchTaskModel, 测试类, batch.xlib | overview | 豁免 |
| glossary.md | 指南 | 术语表 | Chunk/Loader/Processor/Consumer/Record 等定义与划界 | IBatchChunkContext, IBatchLoaderProvider, IBatchConsumerProvider, BatchErrors, NopBatchRecordResult | modules/* | 豁免 |
| reading-guide.md | 指南 | 阅读指南 | 三类读者路径（fan-in 排序） | fan-in 数据+入口 | 全部 | flowchart |
| architecture.md | 指南 | 架构与数据流 | 17 子模块分层/两条线（引擎+DSL）/exp 工具位置 | BatchTaskContextImpl, BatchTaskBuilder, ModelBasedBatchTaskBuilderFactory, NopBatchTask, pom | flows/*, modules/* | flowchart+sequence |
| flows/batch-pipeline.md | 机制 | chunk 管线：数据如何从 Loader 流向 Consumer | 三 Provider 段管线/ChunkContext 上下文/分区分发 | IBatchChunkContext, IBatchTaskContext, IBatchLoaderProvider, IBatchProcessorProvider, IBatchConsumerProvider, BatchTaskContextImpl | modules/batch-core | flowchart+sequence |
| flows/checkpoint-recovery.md | 机制 | 断点续跑与记录：失败后从哪里再来 | record/result/state 三实体/取消链/重入语义 | NopBatchTask, NopBatchRecordResult, NopBatchTaskState, IBatchRecordHistoryStore, BatchCancelException, BatchErrors | modules/batch-core | flowchart |
| modules/batch-core.md | 模块 | 核心执行引擎 | 任务构建/上下文/分区分发/取消 | BatchTaskBuilder, BatchTaskContextImpl, IBatchTask, PartitionDispatchQueue, BatchErrors, NopBatchTaskOutputBean | flows/*, glossary | sequence+class+flow |
| modules/batch-dsl.md | 模块 | DSL 模型与 XLang 集成 | BatchTaskModel/xlib 三件套/Model→Factory 生成链 | ModelBasedBatchTaskBuilderFactory, BatchTaskModel, batch.xlib, batch-record.xlib, batch-gen.xlib | flows/batch-pipeline, modules/batch-core | flowchart+class |
| topics/error-model.md | 主题 | 错误模型：BatchErrors 与取消链 | 错误码分工/取消语义/记录侧错误落库 | BatchErrors, BatchCancelException, NopBatchRecordResult, IBatchRecordHistoryStore | modules/batch-core | 无强制 |

## 4. 生成顺序
批 1a：modules/batch-core、modules/batch-dsl、flows/batch-pipeline、flows/checkpoint-recovery → 批 1b：topics/error-model、glossary → 批 2：architecture → overview、quickstart、reading-guide

## 5. 覆盖缺口与风险
- 受限页面集不含 nop-batch-biz/gen/service/orm/jdbc 独立页（并入 modules 两页与 overview 带过）；exp 工具在 overview 带过。
- fan-in 文件级口径。
