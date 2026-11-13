# Dispatch Log — CRA WI 3.3 nop-ai-gateway 并发审计

- **Plan**: `ai-dev/plans/2475-cra-wi3.3-nop-ai-gateway.md`（active；R1 agent_b034de53 0 Blocker/0 Major + 3 Minor 采纳 + 锚点清单回填；R2 agent_893f7e4e 0 Blocker/0 Major）
- **基线**: commit f4b36732c0，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-ai-gateway.txt`（18 命令组 × 1 源码根 = 392 命中行；C4b 节心跳/Idle/ping 用**词边界口径** `\bheartbeat\b|\bidle\b|\bping\b`——字面子串有 31 处 saveMapping/TypingIndicator 误报，R2 Minor 消化）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`（含本单元目录）；仅 Phase 4 复核允许读历史审计与 cra-index 台账
- **串行约束**: 运营者要求——全部子代理严格串行派发，无并行批次

## 源码根实测（Phase 1 回填，手写口径不含 `_` 前缀与 target/）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-ai/nop-ai-gateway/src/main/java | 36f/6,035 | 无嵌套子模块；`_gen/` 残留 0；模块根 `_dump/` 在 src 外不入圈 |

## 预注锚点核实（Phase 1，grep 实测——R1 锚点清单）

| 锚点 | 实测 | 结论 |
|------|------|------|
| ChannelConnectorManager 非并发容器 | ArrayList(:42,:44)+LinkedHashMap(:43)，零 synchronized/volatile | C1/C4 重点有效 |
| FailoverStreamFlow Flow.Publisher+ArrayDeque | :51 implements / :153 缓冲 | WI 3.1 MA-01 同族对照成立 |
| FeishuConnector(781L) | volatile 恰 3 字段(:115/116/127)、synchronized start:173/stop:206、rateLimitLock:136+467 | 最大并发面 |
| ChannelMessageServiceImpl(520L) | ownedFanOutExecutor:115 + 双检锁懒建 newFixedThreadPool(:227)+ThreadFactory(:259) + worker 中断交错:391-433 | C4 主站点 |
| 心跳/超时调度器 | 词边界 grep 0 命中；ScheduledExecutor 族 0 命中 | **缺席裁定**：在 nop-integration-feishu/SDK 侧，初审不得越界硬凑 |
| 扫码登录 | ChannelLoginScanProcessor(198L)/ChannelLoginApiBizModel(178L) | 在册 |

## owner doc 漂移登记

- `docs-for-ai/03-modules/nop-ai-gateway.md`：channel/failover（含流式重订阅、并发限流 nopFailoverConcurrencyRegistry）/扫码登录三块在册，类名与 live code 一致——Phase 1 粒度**无漂移**。

## 历史审计对照口径

- `ai-dev/audits/` 下 nop-ai-gateway **并发主题**直接审计 0 命中（按并发/thread/async 主题过滤）。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-20，7 批次串行）

| 批次 | 编号 | 结果 |
|------|------|------|
| C1 | C1-001…008；CL-001/002 | **P1×1**（FailoverStreamFlow 窗口清理竞态）+P2×4+P3×3 |
| C2 | C2-001…008；CL-003/004 | **P1×1**（取消-重订阅并发计数幽灵泄漏）+P2×5+P3×2 |
| C3 | C3-001…006；CL-005…008 | **P1×1**（终态冲刷受 demand 约束静默截断）+P2×3+P3×2；C3-002 与 C1-003 同站点待裁定 |
| C4 | C4-001…006；CL-009/010 | **P1×4**（孤儿池无 destroy/传输线程内联 DB+引擎/停机零调用/FeishuConnector 内联）+P2×2 |
| C6 | C6-001…004；CL-011…013 | **P1×1**（会话映射半个成功孤儿化）+P2×3 |
| C7 | C7-001…004；CL-014…016 | P2×4（终态回调挂死不可检测/墙钟双敏感/fan-out 无界队列） |
| C8 | C8-001…003；CL-017…021 | **P1×2**（越窗缓冲无上界/orTimeout 锚定提交时刻）+P2×1 |
| **Phase 2 合计** | 39 条 + CL×21 | **P0×0/P1×10/P2×22/P3×7** |

- 继续条件判定：6 维度含 P1 + C7（P2×4 ≥3 且无终止信号）→ **7 维度全部必派第 2 轮深挖**（合并批）

### Phase 3 深挖（2026-10-20）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C2/C3/C4/C6/C7/C8 | +9（全 P2×5/P3×4）；CL-022/023 | **7 维度全部显式终止** |

- **深挖后累计 48 条**：C1×10（P1×1/P2×6/P3×3）、C2×9（P1×1/P2×6/P3×2）、C3×8（P1×1/P2×4/P3×3）、C4×7（P1×4/P2×2/P3×1）、C6×5（P1×1/P2×3/P3×1）、C7×5（P2×5/P3×0）、C8×4（P1×2/P2×1/P3×1）→ P0×0/**P1×10**/P2×27/P3×11；CL×23（勘误：原记 P1×8/P2×26/P3×14 系算误——closure audit 阻断 2 订正）

### Phase 4 独立复核（2026-10-20，7 维度 4 派发串行）

| 批次 | 判定 | 最终严重度 | 关键裁定 |
|------|------|-----------|---------|
| C1+C2 | C1 保留 10（降 5）/C2 保留 9（降 4） | C1 P1×1/P2×1/P3×8；C2 P1×1/P2×2/P3×6 | 渠道栈休眠条件化（7 条）；AttemptCancelToken 三登记合并；C1-008 并入 C2-001（一行闭合） |
| C3+C4 | C3 保留 8（降 1）/C4 保留 7（降 6） | C3 P1×1/P2×3/P3×4；C4 P2×4/P3×3（**P1×0 全部条件化挂起**） | C3-007 记账反转子面驳回（触发链混淆）核心维持；C4 降档全部渠道栈休眠 |
| C6+C7 | C6 保留 5（降 4）/C7 保留 5（降 1） | C6 P2×1/P3×4；C7 P2×4/P3×1 | C6-004 链主 P1→P2 条件化（保持链内相对序）；CL-015 影子行面不成立关闭（UK 实证）；C7-005 与 3.1 引擎侧对照反证 gateway 消费面失真为模块特定 |
| C8 | 保留 4（降 2） | P1×1/P2×1/P3×2 | C8-001 P1 维持（opt-in 先例不适用——装配后请求面行为缺陷）；CL-023 与 WI 2.3 C3-003 重复登记关闭 |

- **复核后最终保留 48 条：P0×0/P1×4/P2×16/P3×28**；新发现×48/已知未修复×0/已修复×0
- 主登记建议网：C2-001（计数泄漏+取消传播）、C6-004（会话映射链主）、C1-001/C3-001/C8-001（FailoverStreamFlow 三面 live P1）、C4-002+（fan-out 工单族）

### Phase 5 C10 测试保护力（2026-10-20）

- 保护力矩阵 48 行：**会红 0 / 假保护 2 / 无测试 46**；缺保护登记 C10-001…018（P2×15/P3×3，覆盖闭合）
- 反向钉死：2 强（C4-005/C8-003 端点时序钉死）+ 1 弱（C3-007 修复方案依赖）
- 独立复核：0 对账差异、20/48 回测零误判、18 登记全保留、5 项叙述性勘误落账

### Phase 6 合并裁定（2026-10-20）

- 独立裁定子代理串行派发一次；输入 = 48 条保留发现（七维 `## 最终保留项`/`## 维度复核结论`）+ C10 矩阵 + cra-index WI 1.1–3.2 台账 122 行
- 输出 = merge-adjudication.md：48 → 34 工单（7 族 MA-01~07 覆盖 21 + 27 独立 + watch 0），P0×0/P1×4/P2×14/P3×16（13 张条件化携回升条款）；显式裁定 6 处；跨单元零重复 + 21 处关联（重复关闭 1/移交 2/家族 18）；CL 23 全落位（10/10/1/1/1）；C10 18 全映射
- summary.md 由主 agent 按附录 B 撰写；cra-index 登记（报告行/4 P1 行/CL 3.3 小节；P1 累计 122→126）

### Phase 7 P0 即时通道（2026-10-20）

- P0=0 显式空执行；No owner-doc update required
