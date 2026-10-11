# DeepWiki Plan — nop-jq

> Status: approved
> Target: /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-jq @ a3403c8935
> Depth: standard（受限页面集：恒含 5 + flows 2 + modules 2 + topics 1 = 10 页；其余页面按需后续生成）
> Language: zh
> Coverage: 模块 127 个主源文件全部在扫描范围（上一版全量口径；本轮受限页面集以其子集为证据面）

## 1. 概念分析（两段式：本节即 ≤1024 token 压缩结论）

**领域概念**：jq 程序（文本查询语言）→ AST（JqAstNode sealed 42 节点）→ 求值（JqExecutor 流式语义：每表达式把输入值映射为输出值 List）→ 输出（JqPrinter）。值世界封闭于 JqValue 7 实现。旁路子系统 JSON Path（Segment 编译管线，与 jq 线共享面仅 jsonpath 根包消费）。核心不变式：管道左端完整求值为临时列表、右端逐左输出求值；`|=` 经 JqPathEval 求 concrete path 后函数式更新（空流=删除、倒序写回）。资源预算：调用深度 2000/输出量 100 万。错误体系双轨：jsonpath 线 NopJqErrors（NOP_JQ-001..008）/ jq 线 JqRuntimeException。编译缓存 1000 条（JqEngine）。

**架构分层**：io.nop.jq 根（取值抽象 JsonAccessor+jq 门面）/ jsonvalue（对外值模型，无生产消费者）/ jsonpath（独立编译求值线）/ jq.jq（前端 lexer→parser→AST）/ jq.jq.runtime（求值+内建 145 名+路径赋值+打印）。

**核心数据流**：jq 查询文本 → JqEngine.compile（缓存）→ JqLexer.tokenize → JqParser.parse（12 层优先级）→ JqDirectQuery（绑专属 JqExecutor）→ apply：JqValue.of 边界转换 → 匿名 visitor 分发（42 visit）→ List<JqValue> → toJava/JqPrinter。
**核心子机制**：路径赋值 `|=`（UpdateAssignNode → JqPathEval.evalPaths → compareObjectPaths 倒序 deleteAt/setAt，空流记删除）。
**关键系统**：JSON Path 编译管线（Parser→Compiler(LocalCache 1000)→SegmentOptimizer 融合→Executor 列表传播）。
**消费者**：JqToolExecutor（nop-ai-toolkit，唯一活跃）；JPath 墓碑（nop-core，plan 361 后经 SPI 委托 NopJsonPath）。

## 2. 模块地图（证据来源，非章节轴）

| 模块 | 职责 | 关键入口 | fan-in | 供证页面 |
|------|------|---------|--------|---------|
| jq.ast | AST 契约（42 sealed 节点+Visitor） | JqAstNode(53)/JqAstVisitor(44) | — | flows/query-pipeline, modules/jq-runtime |
| jq（前端） | 词法/语法/编译门面 | JqEngine, JqParser(1047行), JqLexer | — | flows/query-pipeline |
| jq.runtime | 求值/内建/路径赋值/打印 | JqExecutor(1058), JqBuiltins(1899), JqPathEval(603) | — | flows/*, modules/jq-runtime |
| jsonpath | JSON Path 编译求值线 | NopJsonPath, NopCompiledJsonPath, Segment(16) | — | modules/jsonpath |
| 根包 | JsonAccessor(20)/门面/错误 | NopJsonPath, NopJqErrors | — | topics/error-model, modules/jsonpath |
| 平台消费 | AI 工具暴露 | JqToolExecutor | — | overview |

## 3. 页面契约（路径锁定——"Page paths are final once submitted"）

| 路径 | 所属章 | 标题 | 职责（回答什么问题） | 源文件（≥5） | relatedPages | 计划图表 |
|------|--------|------|---------------------|--------------|--------------|----------|
| overview.md | 指南 | nop-jq 总览：平台里的 jq 查询引擎 | 定位/能力边界/平台角色 | pom, JqEngine, NopJsonPath, JqToolExecutor, NopJqErrors | architecture, flows/query-pipeline | flowchart（平台位置） |
| quickstart.md | 指南 | 快速上手 | 构建/测试/API 最短路径 | pom, JqEngine, NopJsonPath, TestJqOfficial, JqToolExecutor | overview | 无图豁免 |
| glossary.md | 指南 | 术语表 | 精确定义与易混划界 | JqAstNode, Segment, JqValue, JqEnvironment, NopCompiledJsonPath | modules/* | 无图豁免 |
| reading-guide.md | 指南 | 阅读指南 | 三类读者路径（fan-in 排序） | fan-in 数据+入口类 | 全部 | flowchart |
| architecture.md | 指南 | 架构与数据流 | 分层/依赖方向/两线交汇 | JqEngine, JqDirectQuery, JqExecutor, NopJsonPath, JsonAccessor | flows/*, modules/* | flowchart+sequence |
| flows/query-pipeline.md | 机制 | 查询管线：一条 jq 程序从文本到输出流 | 编译缓存→词法→语法→AST→求值→打印 全链路每一步发生什么 | JqEngine, JqLexer, JqParser, JqDirectQuery, JqExecutor, JqPrinter | modules/jq-runtime, modules/jq-frontend | flowchart+sequence |
| flows/path-assign.md | 机制 | 路径赋值：`|=` 如何改写文档 | evalPaths→路径列表→倒序更新的机制与边界（空流删除/null 生长） | JqPathEval, UpdateAssignNode, JqExecutor, JqEnvironment, JqValues | modules/jq-runtime | flowchart |
| modules/jq-runtime.md | 模块 | 求值运行时与内建函数库 | 求值模型/145 内建分发/环境预算 | JqExecutor, JqBuiltins, JqEnvironment, JqValues, JqPrinter | flows/*, glossary | sequence+flowchart+class |
| modules/jsonpath.md | 模块 | JSON Path 编译求值线 | 语法面/编译优化/求值/写语义 | JsonPathParser, JsonPathCompiler, SegmentOptimizer, NopCompiledJsonPath, JsonPathExecutor | json-value 概念, topics/error-model | flowchart+class |
| topics/error-model.md | 主题 | 错误模型：双轨错误体系 | NopJqErrors 错误码 vs JqRuntimeException 的分工与选择 | NopJqErrors, NopJqException, JqRuntimeException, NopJsonPath, JqEngine | modules/* | 无强制（≥1 保持） |

## 4. 生成顺序
批 1a（叶子）：modules/jq-runtime、modules/jsonpath、flows/query-pipeline、flows/path-assign → 批 1b：topics/error-model、glossary → 批 2（综合，附子页实产清单）：architecture → overview、quickstart、reading-guide

## 5. 覆盖缺口与风险
- fan-in 为文件级 grep 口径（非符号级）；本轮受限页面集未含 modules/jq-frontend、modules/json-value-model（旧版已覆盖其主题，全量生成时补）；JqTimeFunctions/JqRegexSupport 仅在 runtime 页带过。
- 官方套件 430 用例为语义基线（quickstart 页引用）。
