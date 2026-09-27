# nop-code 功能缺口跟踪矩阵(Feature Gap Matrix)

> 建立日期:2026-09-27
> 来源:`ai-dev/backlog/nop-code-feature-completion-roadmap.md`(authoring review R1 REVISE → R2 CONSENSUS,2026-09-25 激活)
> 用途:NG.1 收口对照物——roadmap 关闭前逐 WI 对照本矩阵确认"零残留"。状态列与 roadmap §2 动态状态区保持一致(roadmap 是唯一权威,本矩阵是只读对照投影)。
> 基线 commit:a5bfac83cd(2026-09-27)
> 对照材料:`ai-dev/design/nop-code/` 9 份设计文档(00-vision / 01-architecture-baseline / query-api / search-integration / graph-analysis / graph-discovery-and-export / semantic-edge / flow-analysis / ai-e2e-acceptance)、`ai-dev/analysis/2026-09-23-codegraph-survey-vs-nop-code.md` §8.1(P0#1-5 / P1#6-13 / P2#14-20)

## 一、绿色基线记录

| 项 | 值 |
|----|----|
| 基线命令 | `./mvnw test -pl nop-code -am -T 1C` |
| 基线前状态(2026-09-27 02:05,a5bfac83cd) | **FAILURE**:nop-auth-web `NopAuthWebPagesTest.testValidateAllPages`(空 `<layout/>` 渲染 get-prop-on-null-obj),nop-code-app SKIPPED;nop-code 12 模块 SUCCESS |
| 解锁修复 | `web.xlib`/`flux-web.xlib` GenFormBody+GenAccordion 的 `formModel.layout.*` 访问加 `?.` 防护(plan `ai-dev/plans/nop-code/01-n0-1-feature-gap-matrix-and-green-baseline.md` Phase 1) |
| 基线后状态(2026-09-27 权威数字) | **BUILD SUCCESS(exit 0)**:reactor 全模块 SUCCESS,含 nop-code 13 个模块、nop-auth-web、nop-code-app;模块级汇总 Tests run: 13153 / Failures: 0 / Errors: 0 / Skipped: 96(模块级 Results 汇总行与单测试类行两种独立口径合计一致,closure audit 交叉验证;初记 26306/192 为双计错误已更正);Wall Clock 3:26 min(增量编译) |

## 二、WI ↔ 设计文档 ↔ 调研建议 对照矩阵

> 状态列初始值取自 roadmap §2(2026-09-27);WI 完成后由 roadmap 驱动更新为 `done`,本矩阵不独立维护状态真相。

| WI | 名称 | Item Type | 设计文档出处 | survey §8.1 | 状态 |
|----|------|-----------|-------------|-------------|------|
| N0.1 | 缺口跟踪矩阵与绿色基线 | Proof | README(实现状态待迁移项汇总);01-baseline §6 | —(治理前提) | done(2026-09-27,plan `ai-dev/plans/nop-code/01-n0-1-feature-gap-matrix-and-green-baseline.md`) |
| N0.2 | 文档-代码 drift 修正(7 处) | Fix | 全部 9 份逐一 drift 定位(roadmap N0.2 条目内列明 7 处文件+位置) | — | todo |
| N1.1 | `IGraph` 边属性投影增强(typed edges + attrs) | Fix | 01-baseline §4.4.1(当前只投影 CALLS 不填 attrs);graph-discovery §3.0 前置3/前置4 | P0#1 | done(2026-09-27,plan `ai-dev/plans/nop-code/03-n1-1-typed-edge-projection.md`) |
| N1.2 | 全局算法结果持久化 | Fix | 01-baseline §4.4.1/§6.2(社区/中心性/入口点评分索引期物化);graph-discovery §3.0 前置1/前置2;query-api §七#3 | P1#6(状态化前置) | done(2026-09-27,plan `ai-dev/plans/nop-code/04-n1-2-graph-metric-materialization.md`) |
| N1.3 | 查询路径去全量 rebuild | Fix | 00-vision 约束8/不变量9(目标);01-baseline §6.2;query-api §七#3 | P1#6 | done(2026-09-27,plan `ai-dev/plans/nop-code/05-n1-3-query-path-materialized.md`;按映射声明口径:全局算法查询物化优先,重建降级为自愈回退;结构查询移交 N6.2/N1.4) |
| N1.4 | 分析缓存语义对齐 | Fix | 01-baseline §6.1(AnalysisCache 读缓存语义);query-api §七 | P0#2(缓存面收尾) | done(2026-09-27,plan `ai-dev/plans/nop-code/06-n1-4-cache-semantics-alignment.md`;术语映射:AnalysisCache=DB 派生视图读缓存,度量行经 GraphMetricStore 直查 DB) |
| N2.1 | 意外连接分析 | Fix | graph-discovery §3.1(`ISurprisingConnectionAnalyzer`/`SurprisingConnectionDTO`);query-api §4.2[目标];graph-analysis §六 | —(graphify 对标) | done(2026-09-27,plan `ai-dev/plans/nop-code/07-n2-1-surprising-connections.md`) |
| N2.2 | 图谱问题生成 | Fix | graph-discovery §3.2(`IGraphQuestionGenerator`/`ExplorationQuestionDTO`/`no_signal`);query-api §4.2[目标] | P0#3(suggestedNextQueries 语义,原 Token 效率项的可执行子集) | done(2026-09-27,plan `ai-dev/plans/nop-code/08-n2-2-exploration-questions.md`) |
| N2.3 | 图谱 Wiki 导出 | Fix | graph-discovery §3.3(`IGraphWikiExporter`/`GraphWikiDTO`);query-api §4.2[目标] | P2#19(Wiki 生成) | done(2026-09-27,plan `ai-dev/plans/nop-code/09-n2-3-graph-wiki-export.md`) |
| N2.4 | 自动重建触发 | Fix | graph-discovery §3.4(`triggerRebuildFromCommit` mutation;manifestPath vs commitish 未决项在 plan 期裁定) | P1#12(watcher 的触发面替代:不做本地 watch,做外部触发) | todo |
| N3.1 | 增量依赖传播(2-hop) | Fix | 01-baseline §6.2(增量更新依赖传播);`IncrementalDetector` 现状无 hop 传播 | — | todo |
| N3.2 | 边类型扩展(TESTED_BY/REFERENCES) | Fix | 01-baseline §五(TESTED_BY/REFERENCES 复用 `nop_code_usage.kind`);graph-analysis §三(未测试热点依赖 TESTED_BY);00-vision 约束5 | — | todo |
| N4.1 | 搜索引擎默认装配 + 端到端验证 | Fix | search-integration(头部状态:双路径已实现,缺默认装配) | P0#2(nop-search 集成收口) | todo |
| N4.2 | 向量嵌入生产实现(`ITextEmbedding`) | Fix | search-integration(向量嵌入节);01-baseline §6.2(nop-search 向量/混合) | P1#13 | todo |
| N4.3 | 混合搜索 RRF(`SearchType.HYBRID`) | Fix | search-integration(TEXT→HYBRID 切换,k=60) | P0#2(混合面) | todo |
| N5.1 | TypeScript 调用图补全 | Fix | 01-baseline §6.1(TS 暂无调用图);README(实现状态) | P2#10(语言能力面) | todo |
| N5.2 | 框架适配迁出核心(SPI 装配) | Fix | 00-vision 约束9/不变量10;flow-analysis §一(框架模式注册:目标 IoC 注册 vs 现状硬编码);01-baseline §4.5/§6.2 | P1(框架路由感知的架构化) | todo |
| N5.3 | DSL 驱动框架适配器 | Fix | 00-vision 约束9(DSL 为远期选项);01-baseline §6.2 | P2#15 | todo |
| N5.4 | Go 语言扩展 | Fix | 00-vision §六决策点1(新增语言需人工评估,roadmap 已裁决吸收) | P1#9(10+ 语言的 +3 子集);P1#10 | todo |
| N5.5 | Rust 语言扩展 | Fix | 同 N5.4 | 同上 | todo |
| N5.6 | C# 语言扩展 | Fix | 同 N5.4 | 同上 | todo |
| N6.1 | 数据库图后端选型决策 | Decision | 01-baseline §4.4.1(ltree/CTE/AGE 开放决策);00-vision §一(待决策) | P1#7 | todo |
| N6.2 | `IGraph` 数据库实现 | Fix | 01-baseline §4.4.1(未来数据库实现行)/§6.2 | P1#7 | todo |
| N6.3 | 集群索引构建——分发与工作区 | Fix | 00-vision §一(集群索引目标);01-baseline §6.2(源码分发/分片/原子发布) | P1#6 | todo |
| N6.4 | 集群索引构建——原子发布与一致性模型 | Fix | 同 N6.3 | P1#6 | todo |
| N6.5 | 多租户隔离与访问控制(ask-first) | Fix | —(集群化衍生安全面,`allowedLocalRoot` 细化) | — | todo |
| N7.1 | 语义边 LLM 增强 | Fix | semantic-edge §4.3(`LlmSemanticExtractor`/异步/成本预算/SHA256 缓存/`requiresLlm()`);01-baseline §6.2;00-vision §二#4 | P2#18 | todo |
| N7.2 | GraphRAG 集成契约裁定 | Decision | —(对外集成面;`knowledge-rag-roadmap.md` 未就绪则保持 todo) | P2#17 | todo |
| N8.1 | 评测框架(任务级指标 + JMH 构建性能) | Fix | —(工具自身质量度量) | P2#20 | todo |
| N9.1 | 自我索引与规模基线 | Proof | ai-e2e-acceptance §3.2 | — | todo |
| N9.2 | 验收设计与对照基线定义 | Decision | ai-e2e-acceptance §3.2/§3.5 | — | todo |
| N9.3 | 场景 A 基线对照组执行 | Proof | ai-e2e-acceptance §3.3 | — | todo |
| N9.4 | 场景 A nop-code 组执行与对照评分 | Proof | ai-e2e-acceptance §3.3/§3.5 | — | todo |
| N9.5 | 场景 B 基线对照组执行 | Proof | ai-e2e-acceptance §3.4 | — | todo |
| N9.6 | 场景 B nop-code 组执行与对照评分 | Proof | ai-e2e-acceptance §3.4/§3.5 | — | todo |
| N9.7 | 验收报告与缺口回灌 | Proof | ai-e2e-acceptance §3.6(失败回灌闭环) | — | todo |
| NG.1 | 全量验证 + 独立 closure audit | Proof | —(roadmap 收口门禁,对照本矩阵) | — | todo |
| NG.2 | docs-for-ai 终态化同步 | Fix | — | — | todo |

## 三、范围外豁免登记(与 roadmap §1 一致,勿立项)

| 豁免项 | 否决出处 |
|--------|---------|
| MCP 服务层 | 用户裁决 2026-09-23 + `00-vision.md` non-goals + `query-api-design.md` §八 |
| Hypergraph(超边) | `graph-discovery-and-export-design.md` §四(`flow_membership` 已覆盖执行流特例) |
| Obsidian Vault 导出 / 双链语法 | `graph-analysis-design.md` §四 + `graph-discovery` §四 |
| Neo4j Cypher 导出 | `graph-analysis-design.md` 导出节(嵌入式存储,无 Neo4j 部署) |
| Elasticsearch | `00-vision.md` non-goals(嵌入式 Lucene 足够) |
| IDE 集成 / LSP | `00-vision.md` non-goals |
| 代码生成 / 代码重构 | `00-vision.md` non-goals(只读索引服务) |
| 运行时分析 / 性能剖析 | `00-vision.md` non-goals |
| 交互式图可视化(D3/Cytoscape 前端) | `00-vision.md` §四 + `query-api-design.md` §八 |
| Token 效率分级(detailLevel/minimalContext) | `query-api-design.md` §八(Selection Set 已提供字段裁剪;suggestedNextQueries 语义由 N2.2 承载) |
| Swift↔ObjC / RN Bridge 跨语言桥 | survey P2#16 豁免(语言面无 Swift/ObjC 解析器) |
| AI Workflow 预置提示库 | survey §7.3 豁免(属 AI agent 侧资产,非索引服务能力) |
| SVG 导出 | `graph-analysis-design.md` §四 |
| 本地文件监听 daemon(watch)/ 内建 webhook 入口 | `graph-discovery-and-export-design.md` §四(N2.4 只做外部触发的 mutation) |
| LLM 驱动的问题生成 / 无目标的纯文案问题 | `graph-discovery-and-export-design.md` §四(N2.2 须机器可执行) |

## 四、设计文档待做项反向覆盖检查

> 检查方法:9 份设计文档中全部"待做/目标/未实现/远期/待决策"条目逐一映射到上表 WI 或第三节豁免登记。

| 文档 | 待做项 | 映射 | 孤儿? |
|------|--------|------|-------|
| README.md | 语义边 LLM 集成 / 向量混合 / 全局算法持久化 / 查询无状态 / 集群索引 / 框架适配迁出 / 数据库图索引待决策 | N7.1 / N4.2+N4.3 / N1.2 / N1.3 / N6.3-N6.4 / N5.2 / N6.1 | 无 |
| query-api-design.md | `getSurprisingConnections` / `getExplorationQuestions` / `exportGraphWiki` / §七#3 全量 rebuild | N2.1 / N2.2 / N2.3 / N1.3 | 无 |
| search-integration-design.md | 向量/混合搜索 + `ITextEmbedding` 实现 | N4.2 / N4.3 | 无 |
| graph-analysis-design.md | 未测试热点检测(依赖 TESTED_BY)/ 意外连接发现 | N3.2 / N2.1 | 无 |
| graph-discovery-and-export-design.md | 前置1 社区持久化 / 前置2 介数持久化 / 前置3 边投影 / 前置4 INFERRED/AMBIGUOUS 投影 / 前置5 多仓文档节点 / §3.1-§3.4 四能力 / `triggerIncrementalIndex` 签名未决 | N1.2 / N1.2 / N1.1 / N1.1 / **见下** / N2.1-N2.4 / N2.4 plan 期裁定 | 前置5:多仓/文档节点模型在 roadmap 无专 WI——**登记为待裁决孤儿**(详见第五节) |
| semantic-edge-design.md | LLM 增强提取器 / 语义边参与 Leiden 加权(§5.2)/ 影响传播走语义边(§5.3) | N7.1 / **见下** / **见下** | §5.2/§5.3 语义边参与图算法在 roadmap 无专 WI——**登记为待裁决孤儿**(详见第五节) |
| flow-analysis-design.md | 框架模式 IoC 注册 / test_gap 维度接入测试覆盖率 | N5.2 / N3.2(TESTED_BY 落地后开放) | test_gap 覆盖率数据接入为衍生项,N3.2 后由 roadmap 裁决——**登记为 watch-only**(详见第五节) |
| ai-e2e-acceptance-design.md | §3.2-§3.6 全部(索引基线/验收设计/场景 A/B/评分/回灌) | N9.1-N9.7 | 无 |
| 00-vision.md | 约束8 查询无状态 / 约束9 框架适配迁出 / §六决策点3 LLM 成本预算 | N1.3 / N5.2 / N7.1(plan 期含预算配置) | 无 |
| 01-architecture-baseline.md | §6.2 全部 8 行 | N1.2 / N1.3 / N6.1 / N6.2 / N5.2+N5.3 / N7.1 / N4.2+N4.3 / N3.1 | 无 |

## 五、待裁决孤儿登记(不阻塞 N0.1,回灌 roadmap 由 roadmap 流程裁决)

1. **多仓/文档节点模型**(graph-discovery §3.0 前置5):roadmap 裁定吸收面不含此项(影响 `ambiguous_edge`/跨仓维度的评分维度适用性)。当前裁决:依赖 N1.1/N1.2 落地后按降级原则运行(未满足维度显式跳过或 `no_signal`),与设计 §3.0 处理原则一致,不新增 WI。Classification: watch-only residual。
2. **语义边参与图算法**(semantic-edge §5.2 加权聚类 / §5.3 影响传播):roadmap 的 N7.1 仅覆盖 LLM 提取器;设计目标 5 的管线集成面未立 WI。当前裁决:属算法增强,确定性功能已可用;待 N7.1 落地后视 M9 验收结果决定是否回灌。Classification: watch-only residual。
3. **test_gap 覆盖率数据接入**(flow-analysis 关键度评分第 5 维):依赖 N3.2 TESTED_BY 边;当前固定 1.0。Classification: watch-only residual,successor 由 N3.2 完成后的 roadmap 裁决承载。
