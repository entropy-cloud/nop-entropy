# 全仓代码审查报告索引

> 轮次目录：`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/`
> 关联 roadmap：`ai-dev/backlog/full-code-review-roadmap.md`
> 纪律：每份审计报告产出即在本索引登记一行；closure audit 通过后才允许把对应工作项标 `done`（在 roadmap 中回填）。

## 报告清单

| 工作项 | 报告文件 | P0 | P1 | P2 | P3 | 报告状态 | closure audit |
|---|---|---|---|---|---|---|---|
| 0.1–0.4 | `00-scope-and-dimension-matrix.md` | — | — | — | — | done（编制期产物） | —（编制期自查，roadmap 对抗审查替代） |
| 0.5 | `00-scope-and-dimension-matrix.md`（§1/§2/§6 基线刷新 + v6 新增子模块登记表；执行载体 plan 2307） | — | — | — | — | done（矩阵刷新，无新发现） | 独立子 agent（见 plan 2307 Closure 段） |
| 1.1 | `1.1-dependency-graph-and-boundaries.md` | 0 | 0 | 2 | 3 | done（裁决：通过——零环/api 零污染/T1 装配零越界；P2×2 入 R1.0，P3×3 watch；初版 P2-1.1-003 经 closure audit 证伪撤回） | 独立子 agent 四轮（FAIL→FAIL→残留→PASS，见 plan 2308 Closure 段） |
| 1.2 | `1.2-xlang-structure-pipeline-audit.md` | 0 | 1 | 10 | 18 | done（裁决：通过——链路全连通正结论；P1×1 包级 SCC 三档治理 + P2×10 进 R1.0；JsPromise already-fixed；XLangASTOptimizer 实为生成物） | 独立子 agent（见 plan 2309 Closure 段） |
| 1.3 | `1.3-nop-core-structure-audit.md` | 0 | 0 | 3 | 29 | done（裁决：通过——四依赖边合规/VFS 链连通/初始化幂等正结论；P2×3 进 R1.0：SCC(10) 两红线治理+disabled-initializers 死配置+reinitialize 失败残留；watch×5） | 独立子 agent（见 plan 2310 Closure 段） |
| 1.4 | `1.4-kernel-utils-codegen-xdefs-audit.md` | 0 | 0 | 6 | 34 | done（裁决：通过——模板链 16/16 抽样、xdef 114/114 闭合正结论；P2×6 工具巨类治理进 R1.0；docs 移交 4 项；驳回 1（LittleEndian 证伪）） | 独立子 agent（见 plan 2311 Closure 段） |
| 1.5 | `1.5-xlang-backend-misc-audit.md` | 0 | 0 | 5 | 6 | done（裁决：通过——ExecToJavaTranslator 保留+monitor、truffle 差集 0 缺口、e2e 位置合理三项正结论；P2×5 测试缺口带修复精算进 R1.0；驳回 1；过程安全事件 1 起已登记） | 独立子 agent（见 plan 2312 Closure 段） |
| 1.6 | `1.6-core-framework-structure-audit.md` | 0 | 0 | 3 | 9 | done（裁决：通过——组内无环/职责纯度全合格正结论；CORE-02 still-deferred、弱加密 WARN 现状、CORE-01 docs 闭环三项标定；P2×3 进 R1.0） | 独立子 agent（见 plan 2313 Closure 段） |
| 1.7 | `1.7-persistence-servicefw-boundary-audit.md` | 0 | 0 | 13 | 18 | done（裁决：通过——分层零违规/单桥/api 纯净正结论；P2×13 进 R1.0（AST 模板包死代码 3494 行 plan-first 工单队首）；降级 3——初审 P1×2 经复核降 P2） | 独立子 agent（见 plan 2314 Closure 段） |
| 1.8 | `1.8-integration-periphery-audit.md` | 0 | **1** | 6 | 20 | done（裁决：通过附 P1×1——file/credential-app 半拆进 R1.0；红旗法 0 反向边正结论；根级资产 47 项归属裁定 3 条；降级 1） | 独立子 agent（见 plan 2315 Closure 段） |
| 1.9 | `1.9-jq-refactor-first-audit.md` | 0 | **1** | 9 | 21 | done（裁决：通过附 P1×1——jq 工具接缝资源耗尽（OOM 双通路实证+默认链无门禁）进 R1.0；430/430 基线、refactor 四段全通正结论） | 独立子 agent（见 plan 2316 Closure 段） |
| 1.10 | `1.10-bytecode-duckdb-first-audit.md` | 0 | **2** | 5 | 26 | done（裁决：通过附 P1×2——bytecode exit 1 契约缺口+ObligationToken 死循环（第 1 轮正结论被推翻并落账）进 R1.0；duckdb 契约与 doc 一致正结论） | 独立子 agent（见 plan 2317 Closure 段） |
| 2.1 | `2.1-orm-ormmodel-deep-audit.md` | 0 | **2** | 15 | 41 | done（裁决：通过附 P1×2——detach 一缓契约混杂+DELETING 装配重置（会话状态机通道）进 R1.0；模型校验矩阵全绿/乐观锁闭环正结论；check2 三 P1 全部 already-fixed；降级 1——dim04-17 探针实证后 P1→P2） | 独立子 agent（见 plan 2318 Closure 段） |
| 2.2 | — | | | | | todo | |
| 2.3 | — | | | | | todo | |
| 2.4 | — | | | | | todo | |
| 2.5 | — | | | | | todo | |
| 2.6 | — | | | | | todo | |
| 2.7 | — | | | | | todo | |
| 3.1 | — | | | | | todo | |
| 3.2 | — | | | | | todo | |
| 3.3 | — | | | | | todo | |
| 3.4 | — | | | | | todo | |
| 3.5 | — | | | | | todo | |
| 3.6 | — | | | | | todo | |
| 3.7 | — | | | | | todo | |
| 3.8 | — | | | | | todo | |
| 3.9 | — | | | | | todo | |
| 3.10 | — | | | | | todo | |
| 3.11 | — | | | | | todo | |
| 4.1 | — | | | | | todo | |
| 4.2 | — | | | | | todo | |
| 4.3 | — | | | | | todo | |
| 4.4 | — | | | | | todo | |
| 4.5 | — | | | | | todo | |
| 5.1 | — | | | | | todo | |
| 5.2 | — | | | | | todo | |
| 5.3 | — | | | | | todo | |
| 6.1 | — | | | | | todo | |
| 6.2 | — | | | | | todo | |
| 6.3 | — | | | | | todo | |
| 6.4 | — | | | | | todo | |
| 6.5 | — | | | | | todo | |
| 6.6 | — | | | | | todo | |
| 6.7 | — | | | | | todo | |
| 6.8 | — | | | | | todo | |
| 6.9 | — | | | | | todo | |
| 7.1 | — | | | | | todo | |
| 7.2 | — | | | | | todo | |
| 7.3 | — | | | | | todo | |
| 7.4 | — | | | | | todo | |
| 7.5 | — | | | | | todo | |
| 7.6 | — | | | | | todo | |
| 7.7 | — | | | | | todo | |

## P0 发现追踪（即时通道）

> 审计中发现 P0 必须当即处理（就地修复或异步注入修复 plan），不得留到批量修复波次。发现编号格式：`P0-<工作项号>-<序号>`。

| Finding ID | 报告 | 描述（一句话） | 修复路径 | 修复状态 |
|---|---|---|---|---|

## P1 发现汇总（待 R1.0 展开）

> 本表由各报告产出时回填，是 R1.0 的快捷视图；**R1.0 汇聚以 Wave 1 报告全集为准**，与本报表不一致时先调和再展开（差异记为流程缺陷）。仍 live 的 P1 经 R1.0 转为 roadmap 修正工作项（R1.x）并在此回填去向。

| Finding ID | 报告 | 目标 R1.x | 修复状态 |
|---|---|---|---|
| P1-1.2-001 | `1.2-xlang-structure-pipeline-audit.md` | R1.x（包级 SCC 三档治理，expr→compile 单边割离性价比最高） | open |
| P1-1.8-001 | `1.8-integration-periphery-audit.md` | R1.0（file/credential-app 半拆命运裁决：恢复 start()+web-orm-starter 或正式休眠+文档声明） | open |
| P1-1.9-001 | `1.9-jq-refactor-first-audit.md` | R1.0（jq 工具接缝资源耗尽：4 叶节点预算+cancelToken 接线+env-supplier，精算在案） | open |
| P1-1.10-001 | `1.10-bytecode-duckdb-first-audit.md` | R1.x（bytecode 分析层异常收口+CLI 兜底 ~17 行；CI 接线前必修） | open |
| P1-1.10-002 | `1.10-bytecode-duckdb-first-audit.md` | R1.x（ObligationToken equals/hashCode ~10 行+虚假 javadoc 修正+CLI 防线） | open |
| P1-2.1-001 | `2.1-orm-ormmodel-deep-audit.md` | R1.x（detach 对齐 evict 语义或文档契约+checkValid，补回归） | open |
| P1-2.1-002 | `2.1-orm-ormmodel-deep-audit.md` | R1.x（装配路径对非 MANAGED 状态响亮失败/跳过，补 delete→select→flush 回归） | open |

## P2 裁决台账（例外通道）

> 仅当审计报告显式开辟 P2 裁决例外时在此登记；R1.0 逐项裁决（修 / deferred 且带 Why Not Blocking Closure + Successor），裁决结果同步回 roadmap 行。

| Finding ID | 报告 | 描述 | 裁决结果 | Successor |
|---|---|---|---|---|

## MR/MV/MG 状态与收口证据

> Wave 2/3 工作项（R1.0、R1.x 动态行、R2.0、V.1–V.3、G.1–G.3）的状态翻转与收口证据在此登记；R1.x 展开时逐行追加。

| 工作项 | 状态 | 证据 / 结论 |
|---|---|---|
| R1.0 | todo | |
| R2.0 | todo | |
| V.1 | todo | |
| V.2 | todo | |
| V.3 | todo | |
| G.1 | todo | |
| G.2 | todo | |
| G.3 | todo | |

## 汇总

- `summary.md`：MV 里程碑收口时撰写（总体发现分布、修复闭环率、残留风险清单）。
