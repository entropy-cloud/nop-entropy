# Dispatch Log — CRA WI 5.5 nop-rule-metadata 并发审计

- **Plan**: `ai-dev/plans/2486-cra-wi5.5-nop-rule-metadata.md`（active；R1 agent_dc62f129 2 Major+4 Minor 全修，R2 agent_14a72e20 0 Blocker/0 Major）
- **基线**: commit 81a7c228e7；基线文件 `_tmp/cra-baseline-nop-rule-metadata.txt`（附录 C 全命令组 + C4 平台 job 专项节 + C7 调度站点清单 + Phase 1 锚点核实补记）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: nop-rule-api 16f/1,154 + nop-metadata-api 30f/1,747 plan-first——审计只读不受限；P0 即修仅限 impl 叶

## Phase 1 锚点核实结论（主 agent，2026-10-22）

| 锚点 | 实测 | 结论 |
|------|------|------|
| C4 main 面原语 | 附录 C C4a 8 命中**全部 src/test**（TestCheckpointActionDispatcherConcurrency 等） | main 面 0 命中成立，预注① 确认 |
| C1 粗口径 | C1a 1253（含 static 方法声明，附录 C 粗口径）；main 面 static 可变**字段**直接命中≈0；C1b 31（分布 12 main 文件）；ThreadLocal 1 | C1 审计面转为实例字段×单例/BizModel 载体 + 注册表/holder 间接形态 |
| C5 候选 | query 聚合处理器 holder 模式 5 站点 + full-read 1 站点（NopMetaModuleBizModel） | C5 重点 |
| C7 四件套 | quality 8f/reconciliation 3f/调度器 jobName 前缀 + extConfig.schedule cron 键（:74 实测）/CheckpointActionDispatcher=webhook 分发落点（67 命中） | C7 主战场确认 |
| C2/C3/C6 overlay | C2a 5/C2b 8/C3 9/C6a 75 | overlay 面清单入基线 |

## owner doc 漂移登记

- `docs-for-ai/03-modules/nop-rule.md`、`nop-metadata.md` 均存在——漂移实测由各维度初审对照（发现级登记），单元级无预登记 drift。

## 历史审计对照口径

- `ai-dev/audits/cra-index.md` 对 nop-rule/nop-metadata **0 提及**（grep 实测）——全单元首报。

## 维度级派发记录

1. C1 批 A+B（agent_b418a9dc，串行 1/7）：载体盘点 27 项 → 发现 3（P2×3：C1-001 ensure* check-then-act 族 9 处含 dispatcher 发布-配置倒序/C1-002 DriverManager.setLoginTimeout 全局副作用/C1-003 RuleRuntime 输出容器不对称）+ CL-001~003 + 跨批移交 4 组。
2. C1 批 C+D（agent_bedcf549，串行 2/7）：载体 12 行 → 发现 2（P2×2：C1-101 CrossDbConfigHolder.maxCrossDbRows public static 非 final + 快照/活读双口径、C1-102 单例懒初始化族 10 字段×8 载体）+ CL-101/102。编号 101 段隔离。
3. C4 全单元（agent_73bdf4f7，串行 3/7）：执行模型盘点 9 项（7 项判已防护不立案：平台 job 共享池 4 线程/严格串行 fire/checkpointRunLocks fail-fast/分发同步+30s 超时/对账纯同步/nop-rule 全同步/告警同步）→ 发现 2（C4-001 P2 ensure* 缝 5 处、C4-002 P3 检查点编排占共享调度池无隔离）+ CL-201~204（含 204 基线口径订正）。
4. C5 批 A+C（agent_b292a7c7，串行 4/7）：清单 24 行（14 有界/4 声明边界）→ 发现 6（P2×1 syncExternalTables 全量物化 + P3×5）+ CL-301~303。
5. C5 批 B+D（agent_e60ee015，串行 5/7）：清单 18 行 → 发现 7（P2×3：C5-101 同库 JOIN 无硬闸+注释契约漂移/C5-103 对账 JSON blob 组合上界/C5-105 search 5 处 findAll 全量物化；P3×4）+ CL-401~404。编号 101 段隔离。
6. C7 批 B 主战场（agent_fd30ceab，串行 6/7）：清单 24 行（12 已防护）→ 发现 7（**P1×2：C7-001 外部查询零 statement timeout→质量监控静默停摆 F5、C7-004 六张 append-only 时序表零清理 F4**；P2×3；P3×2）+ CL-501~503。
7. C7 批 A+C+D（agent_45be47dc，串行 7/7）：清单 7 增长源+4 挂死点 → 发现 3（**P1×1：C7-102 外部库语句全链无 queryTimeout→checkpoint 永久锁死+worker 泄漏**、C7-101 P2 时序快照表族、C7-103 P3）+ CL-601~604。编号 101 段隔离。

**Phase 2 汇总**：初审发现 30 = P1×3 + P2×14 + P3×13；CL 线索 23；格式门禁抽查通过（全字段覆盖）；已知跨批重复对（去重归 Phase 4/6）：ensure* 族（C1-001/C1-102/C4-001）、maxCrossDbRows 族（C1-101/C5-102/CL-601）、append-only 表族（C7-004/C7-101）、queryTimeout 族（C7-001/C7-102）。预注未被订正（C4 main 面 0 原语成立）。


## Phase 3 深挖派发记录

8. C1 R2（agent_0f5e72b4，串行）：扩大面 10/10 全安全清扫——**0 新增，显式终止**（含跨单元外部消费者 RuleBasedSelectionStrategy 核查、EqlASTParser 无状态核实）。
9. C4 R2（agent_66b41d13，串行）：新增 2（C4-R2-001 **P2** 外部 JDBC socket 读无超时+监视器内同步占池、C4-R2-002 P3 连接零复用）+ CL-205/206；5 扩大面全查闭合，终止。
10. C5 R2（agent_8cc798fa，串行）：新增 2（P3×2：C5-R2-001 lineage 每请求全图物化 churn、C5-R2-002 取 1~2 列全实体载入）+ F4 移交 C7；六项扩大面闭合，终止。
11. C7 R2（agent_fd640932，串行）：新增 4（**C7-R2-001 P1** 规则热更新失效传播缺口——traceDepends 仅钉定义行不钉节点行；P3×3）+ CL 3；触发必派条件。
12. C7 R3（agent_bd26f310，串行）：新增 1（**C7-R3-001 P1** wf.start 孤儿半程实例无恢复语义）+ R2-001 同族放大闭合 + C7-001 触发链修正（cron tick 静默跳过）；再触发必派条件。
13. C7 R4（agent_7e137ec9，串行，窄域收尾）：**0 新增，显式终止**；CL-504（search 提交前窗口 watch）；P1 四案共同根因层第四形态穷举闭合。C7 四轮累计 15 立案（P1×4/P2×4/P3×7）。

**Phase 3 汇总**：全单元累计 39 立案（Phase 3 时点分维口径 P1×5/P2×15/P3×19——C7-R3-001 当时未降级；终态 P1×4/P2×13/P3×21 见 merge-adjudication §三 勘误 1/2）——C1 5/C4 4/C5 15/C7 15；C1 一轮即终止，C7 四轮收敛，各维度均有显式终止信号。

## Phase 4 复核派发记录

14. C1 复核（agent_89e0cd3e，串行 1/4）：5/5 保留（驳回 0）——C1-101 降级 P2→P3（零写者实证，对齐批 A 移交先例）；C1-102 簿记修正 11 字段×8 载体；主登记建议 C1-102/次登记 C1-001；CL 5/5 确认。覆盖缺口 3 项登记（security 包/field 2 文件/api+core 隐含覆盖）。
15. C4 复核（agent_0dac8f9f，串行 2/4）：4/4 保留（P2×2/P3×2）；C4-R2-001 专项核实平台无兜底（setNetworkTimeout main 0 命中 + SimpleDataSource.setLoginTimeout no-op）不降级；盘点表 3/3 抽查通过；CL 6/6 确认。覆盖 10/10 源码根有痕迹。
16. C5 复核（agent_6c7e1a0f，串行 3/4）：15 → 14 实体 → 13 工单（P2×2/P3×11；驳回 0、降级 2[C5-103/105 附激活回升条款]、并入 1[C5-106→C5-001 同站点复登]）；R2-002 局部修正（in(ids) 过滤已存在）并入 R2-001；主登记建议 maxCrossDbRows 族=C1-101；CL 7/7 确认。覆盖缺口 3 项登记。
17. C7 复核（agent_46209134，串行 4/4）：15/15 保留（驳回 0、降级 1[C7-R3-001 P1→P2 对齐环境触发+可恢复先例]）；**P1 超时族裁定同一缺陷三后果层**——主登记 C7-102（失效面并集）/次登记 C7-001/C4-R2-001 跨维关联，判级建议统一 P1（C4 已裁 P2 分歧登记 7.1）；C7-002 Error 击穿经平台源码独立证实；CL 8/8 确认。覆盖缺口 2 项登记。

**Phase 4 汇总**：39 立案全保留 0 驳回；降级 4（C1-101、C5-103、C5-105、C7-R3-001）；并入 1（C5-106）；实体 38；P1×4（3 独立缺陷：外部 JDBC 无超时族双登记、append-only 表族、规则热更新失效族）；实体级 P2×13、P3×21。CL 线索 26/26 确认 0 驳回。（原「P2×10/P3×24」系算术滑差，勘误见 merge-adjudication §三.7）

## Phase 5 C10 派发记录

18. C10（agent_17899bd2）：38 行矩阵——**会红 0/38**（逐条核实非预设）；本单元有 3 真并发测试类但保护目标全为历史已修缺陷回归面；无保护 27/邻接 10/反向钉定 1（C1-002——TestMetaDataSourceConnectionSecurity:776 断言全局 setLoginTimeout==5，修复阻力）；缺保护 38 全登记（P1×4/P2×13/P3×21）。
19. C10 复核（agent_bcd3b197）：抽样 9 行全 PASS（P1×4 重演含两个否定性结论）；**PASS with corrections**——3 处修正（引文笔误/P2 表错位互换/P3 聚合计数 16→15）。

**Phase 5 汇总**：会红 0/38；反向钉定 1；缺保护 38。
