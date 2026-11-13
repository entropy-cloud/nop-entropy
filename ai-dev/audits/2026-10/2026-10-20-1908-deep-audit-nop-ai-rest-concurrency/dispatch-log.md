# Dispatch Log — CRA WI 3.4 nop-ai-rest 并发审计

- **Plan**: `ai-dev/plans/2476-cra-wi3.4-nop-ai-rest.md`（active；R1 agent_9d26fdba 4 Major + 3 Minor 全修——rag 异步前提失真/coder 幻影锚点/nop-ai-api Protected Area/锚点底稿；R2 agent_15fdb9d2 0 Blocker/0 Major）
- **基线**: commit 8dd63bedc1，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-ai-rest.txt`（17 命令组 × 8 源码根 = 294 命中行；C3a 节加 embedAsync/embedAllAsync 死代码面定向）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`（含本单元目录）；仅 Phase 4 复核允许读历史审计与 cra-index 台账
- **串行约束**: 运营者要求——全部子代理严格串行派发，无并行批次
- **Protected Area**: nop-ai-api 属 AGENTS.md「nop-*-api plan-first」——审计只读不受限；Phase 7 P0 若触碰须起草修复 plan 并声明授权来源

## 源码根实测（Phase 1 回填，手写口径不含 `_` 前缀与 target/）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-ai/nop-ai-api/src/main/java | 29f/3,917 | 包根 io.nop.ai.api.{credential,chat,mcp,exceptions}；零 static 可变集合；**Protected Area** |
| nop-ai/nop-ai-coder/src/main/java | 21f/2,050 | 21 转换器类（模型→Java 批处理）；`_cases/`·`_dump/` 在 src 外不入圈 |
| nop-ai/nop-ai-dsl-orm/src/main/java | 4f/226 | 近零面 |
| nop-ai/nop-ai-rag/src/main/java | 20f/1,810 | **零 CompletableFuture**；embedAsync/embedAllAsync 零生产调用方；io.nop.ai.core.api.* split package 物理在此 |
| nop-ai/nop-ai-code-analyzer/src/main/java | 33f/3,450 | **入圈裁定成立**（pom.xml:33 聚合器直含；roadmap 无其他工作项覆盖） |
| nop-ai/nop-ai-dao/src/main/java | 46f/614 | 实体外壳；entity/_gen 22 文件全 `_` 前缀已排除 |
| nop-ai/nop-ai-service/src/main/java | 27f/863 | 22 个 *BizModel；`_dump/` 在 src 外不入圈 |
| nop-ai/nop-ai-app/src/main/java | 1f/32 | 近零面 |
| **合计** | **181f/12,962** | `_gen/` 非下划线残留 0（8 模块同查）；codegen/meta/web 零 java 源登记零审计面 |

## 预注锚点核实（Phase 1，grep 实测——R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| rag 异步面 | CompletableFuture 0 命中；embedAsync:71/embedAllAsync:77 零生产调用方；RagSearchService:34 同步 embed、RagIngestService:68 同步 embedAll | C3 重锚为死代码面+边界契约+VectorStore 并发面；近空产显式空执行 |
| rag 并发原语 | AiModelTextEmbedding/PgVectorStore/InMemoryVectorStore 三类命中 | C1 重点 |
| coder session/state | grep 零命中 | 幻影锚点已订正——审转换器实例字段 + JavaMethodReplacer 文件写面 |
| code-analyzer 并发原语 | JavaSourceFileFinder ConcurrentHashMap:36 / ExtensionLanguageMapper static HashMap:12 / CodeSymbolInterning String.intern 池 / LanguageStatsAggregator 聚合状态；synchronized 零命中 | C1 重点 |
| nop-ai-api Protected Area | AGENTS.md:144 | Phase 7 条款在案 |

## owner doc 漂移登记

- `docs-for-ai/03-modules/nop-ai.md`：子模块表无 dsl-orm/code-analyzer/app/codegen/meta 行——**覆盖缺口按 drift 登记**，这些模块规模对照以实测为准；已覆盖模块契约按 javadoc 校准。

## 历史审计对照口径

- `ai-dev/audits/` 下 8 模块**并发主题**直接审计 0 命中（按并发/thread/async 主题过滤；WI 3.1 C5-005 为 nop-ai-core 侧 P3 对照指针）。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-20，3 批次串行）

| 批次 | 编号 | 结果 |
|------|------|------|
| C1 | C1-001…008；CL-001…005 | **P1×3**（JavaSourceFileFinder parallelStream 并发 add ArrayList/PgVectorStore schemaReady 实例级旗标 vs collection 级 DDL/InMemoryVectorStore removeIf+add 非原子 upsert）+P2×1+P3×4；coder 转换器实例复用假设证伪（逐调用 new） |
| C3（仅 rag） | C3-001…004；CL-006…009 | **P1×1**（C3-004 schemaReady——与 C1-002 同站点）+P2×2（embedAsync 契约分叉/sync-over-async 无界阻塞）+P3×1；非空产未硬凑 |
| C6 | C6-001…003；CL-010…014 | **P1×1**（C6-001 schemaReady——三维度三登记）+P2×2（JavaMethodReplacer 截断覆写无备份/JarTypeSolver 无 close） |
| **Phase 2 合计** | 15 条 + CL×14 | **P0×0/P1×5/P2×5/P3×5** |

- 跨维同站点：**PgVectorStore.schemaReady = C1-002 = C3-004 = C6-001**（三登记单缺陷）；CL-010 = C1-001；CL-008/CL-012 ↔ C1-003
- 继续条件判定：3 维度均含 P1 → 全部必派第 2 轮深挖（合并批）

### Phase 3 深挖（2026-10-20）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C3/C6 | +4（P2×3/P3×1）：C1-009、C3-005/006、C6-004（对 C6-002 幂等结论分歧声明）；扩展 C3-003（站点 3→5）；CL-015/016 | **3 维度全部显式终止** |

- **深挖后累计 19 条**：C1×9（P1×3/P2×1/P3×5）、C3×6（P1×1/P2×3/P3×2）、C6×4（P1×1/P2×3）→ P0×0/**P1×5**/P2×7/P3×7；CL×16（勘误：原记 P2×8/P3×6 系笔误——closure audit 阻断 1 订正）

### Phase 4 独立复核（2026-10-20，三维度合并批）

- 判定：19/19 全保留；**升格 1**（C3-005 P2→P1——synthesize 静默折叠错误响应，可达性三步闭合）；**吸收 2**（schemaReady 三登记 C1-002/C3-004 → C6-001 主登记——同一缺陷三维视角，一处修复闭合三登记）
- C6-002/C6-004 分歧裁定：双保留不合并（同族 F2/F6 非原子写，不同站点不同根因；C6-002 的「幂等可恢复」结论被推翻更正）
- 最终去重口径：**17 工单 P1×4/P2×6/P3×7**；CL-010=C1-001、CL-008=C1-003 吸收关闭；CL-012 与 CL-004/CL-013 线索层重复合并建议
- 现状标注：新发现×19；code-analyzer 零外部消费者亲证（C1-001 并行面内生于方法体不适用单线程降档）

### Phase 5 C10 测试保护力（2026-10-20）

- 保护力矩阵 17 行（覆盖 19 条）：**会红 0 / 假保护 1 / 无测试 16**；缺保护登记 C10-001…008（P2×4/P3×4）
- 反向钉死 1：TestCodeFileInfoStructure intern assertSame（修复 C1-008 必红）
- 独立复核：0 判定差异、9 行回测零误判、8 登记全保留、4 处标注级勘误落账

### Phase 6 合并裁定（2026-10-20）

- 独立裁定子代理串行派发一次；输入 = 19 条保留发现（三维 `## 最终保留项`/`## 维度复核结论`）+ C10 矩阵 + cra-index WI 1.1–3.3 台账 126 行
- 输出 = merge-adjudication.md：19 → 15 工单 + watch 1（MA-01 schemaReady 三登记吸收 2 + MA-02 static 卫生族吸收 1 + 13 独立），P0×0/P1×4/P2×6/P3×5；复核裁定确认无重开 2 处 + 新增结构裁定 2 处；跨单元零重复 + 5 处家族关联；CL 16 全落位（4/3/9/0）；C10 8 全映射；0 张工单触碰 nop-ai-api（Protected Area plan-first 零命中）
- summary.md 由主 agent 按附录 B 撰写；cra-index 登记（报告行/4 P1 行/CL 3.4 小节；P1 累计 126→130）

### Phase 7 P0 即时通道（2026-10-20）

- P0=0 显式空执行；No owner-doc update required；nop-ai-api Protected Area 条款未触发
