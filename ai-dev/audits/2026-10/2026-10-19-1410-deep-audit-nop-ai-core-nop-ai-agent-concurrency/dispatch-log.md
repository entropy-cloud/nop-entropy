# Dispatch Log — CRA WI 3.1 nop-ai-core-nop-ai-agent 并发审计

- **Plan**: `ai-dev/plans/2473-cra-wi3.1-nop-ai-core-agent.md`（active；R1 agent_7b5cb02a 修 2 Major + 3 Minor——预拆批次手写口径订正/17 批重拆；R2 agent_77a9a2b1 0 Blocker 0 Major）
- **基线**: commit 22dc8bd3df，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-ai-core-nop-ai-agent.txt`（18 命令组 × 2 源码根 = 4,966 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`（含本单元目录）；仅 Phase 4 复核允许读历史审计与 cra-index 台账
- **串行约束**: 运营者要求——全部子代理严格串行派发，无并行批次

## 源码根实测（Phase 1 回填，手写口径不含 `_` 前缀与 target/）

| 分区 | 范围 | 规模 |
|------|------|------|
| A | nop-ai/nop-ai-core/src/main/java（全模块单源码根） | 101f/14,176 |
| B | agent/security + plan | 118f/9,241 |
| C | agent/engine + runtime + model + reliability | 128f/23,675 |
| D | agent/team + tool + session + message + memory | 132f/22,314 |
| E | agent 其余 16 小包 + 根 3 文件 | 115f/11,158 |
| **合计** | 两模块 594f/80,564（五区和对账闭合；`_gen/` 内非下划线残留 0） | |

- precompile/ 裁定：两模块各恰 1 个 `.xgen` 构建期 codegen 模板（gen-ai-xdsl.xgen / gen-agent-xdsl.xgen，生成器脚本非生成物）——**不入圈**。
- 嵌套子包实测：agent 下 12 个嵌套子目录（guardrail/rule、model/_gen、model/recipe、model/recipe/_gen、plan/model、plan/model/_gen、runtime/coordination、runtime/lock、runtime/recovery、team/flow、team/scheduler）——`_gen` 内仅 `_` 前缀文件，不影响手写口径分区。

## 预注锚点核实（Phase 1，grep 实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| 两模块规模 | 101f/14,176 + 493f/66,388 = 594f/80,564 | 计划一致（R1/R2 双实测） |
| agent 27 二级包 + core 15 二级包 | 实测吻合（R2 独立复核） | 预注成立 |
| core/reliability 20f/1,807（failover 面） | 实测吻合 | C7/C3 派发重点有效 |
| WI 2.1 CL-021 移交面点名 ai-agent | cra-index 在案 | Phase 4 复核必查 |
| WI 2.3 CL-022 移交 nop-ai/nop-message | cra-index 在案（Wave 4 单元，相邻分账） | Phase 6 对账单列 |

## owner doc 漂移登记

- `docs-for-ai/03-modules/nop-ai.md`：模块表覆盖 nop-ai-core/nop-ai-agent（L52–53）+ Agent 引擎可靠性配置节（L117–123）——与本单元范围一致，Phase 1 粒度**无漂移**；契约细节按各维度审计中以 javadoc 校准。

## 历史审计对照口径

- `ai-dev/audits/` 下 nop-ai **并发主题**直接审计 0 命中（按并发/thread/async 主题过滤；质量域 arm-MA2.x/MA5.x 等 10+ 份在册不计命中）。

## 维度级派发记录

（Phase 2 起逐条追加）

### C1 初审（2026-10-19，5 原子分区串行）

| 批次 | 分区 | 编号 | 结果 |
|------|------|------|------|
| C1-A | ai-core（101f/14,176） | C1-001…004；CL-001/002 | P2×3+P3×1；PromptTemplateManager 惰性竞态族、7 单例换装面；CL-001 流式异常吞没挂死 |
| C1-B | security+plan（118f/9,241） | C1-005…007；CL-003/004 | **P1×1**（DefaultPermissionProvider 单例字段跨会话覆盖）+P2×2 |
| C1-C | engine+runtime+model+reliability（128f/23,675） | C1-008…015；CL-005/006 | **P1×1**（runningInjectionTargets resume 裸 ArrayList）+P2×4+P3×3 |
| C1-D | team+tool+session+message+memory（132f/22,314） | C1-016…024；CL-007…010 | P2×6+P3×3（DBSessionStore 多实例并存、DBMessageService 订阅窃取等）；配额中断重派一次（首派无盘上工件） |
| C1-E | 其余 16 小包+根（115f/11,158） | C1-025…027；CL-011/012 | P2×1+P3×2 |
| **C1 合计** | 全模块 | C1-001…027、CL-001…012 | **P1×2/P2×16/P3×9 = 27 条 + CL×12** |

### C3 初审（2026-10-19，3 批次串行）

| 批次 | 分区 | 编号 | 结果 |
|------|------|------|------|
| C3-X1 | A=ai-core（101f/14,176） | C3-001…009；CL-013…017 | **P1×3**（callStream 双悬挂/背压放弃）+P2×6；注：首派遇磁盘满中断已落盘 8 条，重派独立复审合并去重（+C3-009） |
| C3-X2 | B+C=security/plan/engine/runtime/model/reliability（246f/32.9k） | C3-010…020；CL-018…023 | **P1×3**（执行槽孤儿化+续期无限续命/工具取消不传播/mailbox 只增不删）+P2×6+P3×2 |
| C3-X3 | D+E（247f/33.5k） | C3-021…027；CL-024…028 | **P1×4**（DBMessageService 单线程 poller 内联/flow orchestrator 池泄漏/BackgroundTaskRegistry 只增不删/replySubscriptions 永久轮询）+P2×2+P3×1 |
| **C3 合计** | 全模块 | C3-001…027、CL-013…028 | **P1×12/P2×10/P3×5 = 27 条 + CL×16** |

- 环境记录：本批次期间宿主磁盘满（ENOSPC，117MB 剩余）——清理可再生缓存（JetBrains IDE 37GB/Homebrew 2GB/pip 0.9GB）后恢复，无数据丢失

### C4 初审（2026-10-19，3 批次串行）

| 批次 | 分区 | 编号 | 结果 |
|------|------|------|------|
| C4-X1 | A=ai-core | C4-001…005；CL-029…031 | P1×2（callStream publisher 不关闭）+P2×3 |
| C4-X2 | B+C | C4-006…009；CL-032…035 | P2×4（call-agent 同池嵌套 join、租约/恢复周期任务 Error 停转、续期单线程 DB 队头阻塞、commonPool 阻塞缺省） |
| C4-X3 | D+E | C4-010…016；CL-036…040 | **P1×3**（flow orchestrator 池泄漏、AgentDispatchExecutor commonPool 阻塞工具体、BackgroundTaskRegistry 零清理+timeout 无执行点）+P2×4 |
| **C4 合计** | 全模块 | C4-001…016、CL-029…040 | **P1×5/P2×11 = 16 条 + CL×12** |

### C5 初审（2026-10-19，3 批次串行）

| 批次 | 分区 | 编号 | 结果 |
|------|------|------|------|
| C5-X1 | A=ai-core | C5-001…007；CL-041…044 | **P1×1**（StreamAggregator 无界 OOM）+P2×3+P3×3 |
| C5-X2 | B+C | C5-008…014；CL-045…049 | 落盘 7 条 + CL×5（checkpoint journal 全量物化族等）；返回消息被配额中断吞没，覆盖声明缺失（深挖批补确认） |
| C5-X3 | D+E | （下接） | |

| C5-X3 | D+E | C5-015…022；CL-050…054 | **P1×1**（session store 缓存 put-only 终态清理缺口族主案）+P2×7 |
| **C5 合计** | 全模块 | C5-001…022、CL-041…054 | **P1×4/P2×13/P3×5 = 22 条 + CL×14**（X2 中断注记在案，覆盖完整性深挖批确认） |

### C7 初审（2026-10-19，3 批次串行，Phase 2 收官）

| 批次 | 分区 | 编号 | 结果 |
|------|------|------|------|
| C7-X1 | A=ai-core | C7-001…011；CL-055…060 | **P1×2**（流式挂死不可见/断连误判成功）+P2×6+P3×3 |
| C7-X2 | B+C | C7-012…021；CL-061…066 | **P1×2**（续期异常无限容忍 fencing 失效/DBCheckpointManager remove 漏修）+P2×7+P3×1 |
| C7-X3 | D+E | C7-022…032；CL-067…074 | **P1×5**（超时取消契约 F3 漏修族×3/毒消息停摆/轮询负载线性增长）+P2×6 |
| **C7 合计** | 全模块 | C7-001…032、CL-055…074 | **P1×9/P2×19/P3×4 = 32 条 + CL×20** |

### Phase 2 汇总（主 agent）

- **初审累计 124 条**：C1×27（P1×2/P2×16/P3×9）、C3×27（P1×12/P2×10/P3×5）、C4×16（P1×5/P2×11）、C5×22（P1×4/P2×13/P3×5）、C7×32（P1×9/P2×19/P3×4）——P0×0/**P1×32**/P2×69/P3×23
- **CL×74**（CL-001…074）
- 格式抽检：5 文件全含触发时序+失效面+file:line
- 继续条件判定：5 维度均含 P1 → 全部必派第 2 轮深挖（合并批）

### Phase 3 深挖（2026-10-19）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C3/C4/C5/C7 | C3 +2（C3-028 **P1** 流式工具调用截传/C3-029 P2）；CL-075/076 | C1/C4/C5/C7 终止；C3 需续 |
| R3（收敛） | C3 | +3（C3-030 **P1** 流式 usage 链失活/C3-031 **P1** Ollama 空串抢占/C3-032 P2）；CL-077 | 终止（矩阵穷举闭合；**偏差记录**：新增含 P1 未启第 4 轮——同族单修复批收敛裁定，理由留档） |

- **深挖后累计 129 条**：C1×27（P1×2/P2×16/P3×9）、C3×32（P1×15/P2×12/P3×5）、C4×16（P1×5/P2×11）、C5×22（P1×4/P2×13/P3×5）、C7×32（P1×9/P2×19/P3×4）→ P0×0/**P1×35**/P2×71/P3×23；CL×77

### Phase 4 独立复核（2026-10-19/20，5 维度串行）

| 维度 | 判定 | 最终严重度 | 现状标注 | 关键裁定 |
|------|------|-----------|---------|---------|
| C1 | 保留 26+降 1 | P1×1/P2×17/P3×9 | 新×27 | C1-005 P1→P2（零生产实例化，激活回升条款）；C1-008 P1 维持（live 触发链） |
| C3 | 保留 32/驳 0 | P1×13/P2×16/P3×3 | 新×32 | 方言流式族矩阵逐格直核；C3-030 Anthropic 校正不改判；跨单元 C3-020/027→WI 2.1 C3-001 平台绝缘层 |
| C4 | 保留 16/驳 0 | P1×4/P2×12 | 新×16 | 指派 9 组指针全核对成立 + 补登记 C4-016↔C3-024↔C7-026 三重登记；globalTimer 修复建议与 WI 2.1 C4-001 冲突留 7.1 |
| C5 | 保留 19+降 1+**驳 1** | P1×4/P2×11/P3×6 | 新×21 | **C5-019 驳回**（初审 grep 失误——releaseSession 4 出口接线在案）；C5-022 降档（缺省 NoOp 装配）；F4 族首 C5-015 |
| C7 | 保留 31+降 1 | P1×8/P2×20/P3×4 | 新×32 | C7-013 降档（与 C5-009/C1-011 收敛 P2）；C7-017 装配面修正（缺省即有状态，发现强化）；C7-012 条件面披露不降档 |

- **复核后最终保留 128 条：P0×0/P1×30/P2×76/P3×22**；新发现×128/已知未修复×0/已修复×0
- 过程记录：C5 复核两次中断（首轮回填未落盘[占位行误读]、续作首派被取消），第三次续作全量重做完成

### Phase 5 C10 测试保护力（2026-10-20）

- 保护力矩阵 128 行：**会红 0 / 假保护 103 / 无测试 25**（P1×30 = 假保护 23 + 无测试 7）；缺保护登记 C10-001…019（全 P2，覆盖 128 条闭合）
- 反向钉死 4 项：R2（gate3 测试字面钉 remove no-op）、R3（邮箱无界默认）、R1（Ollama fixture 掩蔽空串变体）、R4（wakeup 次数断言）
- 独立复核：128/128 维持、19 登记全保留、7 项修正落账（M1 统计/M2 闭合缺口 C3-024/M3 主归属/M4–M7 引用卫生）

### Phase 6 单元内合并裁定（2026-10-20）

- 独立裁定子代理串行派发一次（未参与五维初审/复核通道）；输入 = 五维 `## 最终保留项`/`## 维度复核结论` + round 文件 + C10（含复核 M1–M7 修正）+ cra-index WI 1.1–2.5 台账
- 输出 = merge-adjudication.md：128 → 50 工单（21 缺陷族 MA-01~21 吸收 97 + 29 独立 + watch 2），P0×0/P1×13/P2×29/P3×8；冲突裁定 8 项 + 留 7.1 开放 5 项；CL 77 全落位（46/29/2/0）；MA-07（6 条）移交 WI 2.1 C3-001 平台绝缘层单列
- summary.md 由主 agent 按附录 B 撰写，cra-index 登记（报告行/13 P1 行/CL 小节；P1 累计 104→117）

### Phase 6 后勘误（closure audit B2）

- 勘误：Phase 2 汇总「C3 合计 P1×12/P2×10/P3×5」与「C4-X1 P1×2」系誊写算误——实际 C3 初审 **P1×10/P2×14/P3×3**、C4 初审 **P1×4/P2×12**（见各维度复核节判定表；条数链 124→129→128 与终局分布 P0×0/P1×30/P2×76/P3×22 不受影响）
