# Dispatch Log — CRA WI 3.2 nop-ai-toolkit-tools-shell 并发审计

- **Plan**: `ai-dev/plans/2474-cra-wi3.2-nop-ai-toolkit-tools-shell.md`（active；R1 agent_9f0563b8 0 Blocker/0 Major + 4 Minor 采纳；R2 agent_e78c2f7d 0 Blocker/0 Major）
- **基线**: commit 9b091c9d4b，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-ai-toolkit-tools-shell.txt`（18 命令组 × 3 源码根 = 528 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`（含本单元目录）；仅 Phase 4 复核允许读历史审计与 cra-index 台账
- **串行约束**: 运营者要求——全部子代理严格串行派发，无并行批次

## 源码根实测（Phase 1 回填，手写口径不含 `_` 前缀与 target/）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-ai/nop-ai-toolkit/src/main/java | 74f/7,330 | 无嵌套子模块；precompile/ 仅 1 个 .xgen 构建模板不入圈；model/_gen 全 `_` 前缀；独立沙箱族 io/nop/ai/toolkit/tools/sandbox/（DockerBashSandbox/HostBashSandbox/IBashSandbox），BashExecutor 在 tools/ 包根（sandbox 消费方） |
| nop-ai/nop-ai-tools/src/main/java | 23f/2,088 | 无嵌套；_dump/ 在模块根 src 外不入圈 |
| nop-ai/nop-ai-shell/src/main/java | 44f/4,508 | 无嵌套 |
| **合计** | **141f/13,926** | `_gen/` 非下划线残留 0（三模块同查） |

## 预注锚点核实（Phase 1，grep 实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| 三模块规模 | 74f/7,330 + 23f/2,088 + 44f/4,508 = 141f/13,926 | 计划一致（R1/R2 双实测） |
| toolkit 独立沙箱族与 agent 侧分属两套 | DockerBashSandbox/HostBashSandbox/IBashSandbox 在 toolkit；DockerSandboxBackend 在 agent（3.1 已审） | WI 3.1 MA-17 对照为同族面非同站点 |
| WI 2.1 CL-021 移交面 | 点名清单 nop-stream/ai-agent/feishu **不含本单元** | Phase 4 仅漂移复查 |
| P1 累计 117 = 104 + 13 | cra-index P1 表实测（124 原始行含 7 行内嵌 CL 台账行，按计数口径 117） | 计划一致 |

## owner doc 漂移登记

- `docs-for-ai/03-modules/nop-ai.md`：覆盖三模块（R2 实测）；Phase 1 粒度**无漂移**；契约细节按各维度审计以 javadoc 校准。

## 历史审计对照口径

- `ai-dev/audits/` 下三模块**并发主题**直接审计 0 命中（按并发/thread/async 主题过滤；质量域 arm 系列在册不计命中）。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-20，4 批次串行）

| 批次 | 范围 | 编号 | 结果 |
|------|------|------|------|
| C1 | 三模块全量（141f/13,926） | C1-001…008；CL-001…004 | **P1×2**（ToolManagerImpl 路径队列锁内 get 悬挂/ShellCommandExecutor 会话状态无同步）+P2×5+P3×1 |
| C4 | 三模块全量 | C4-001…007；CL-005…008 | **P1×2**（executePipeline 拒绝无回滚级联吞线程/pathQueues executeNow.get 挂死[与 C1-001 同站点]）+P2×5 |
| C6 | 三模块全量 | C6-001…008；CL-009…013 | **P1×6**（沙箱收尾不闭环双后端/Docker 清理双失效/管道队列死锁簇）+P2×2 |
| C8 | 三模块全量 | C8-001…010；CL-014…019 | **P1×4**（≥1024 行输出确定性挂死/EOF offer 丢失/Docker CLI 进程泄漏/管道下游失败上游阻塞）+P2×6 |
| **Phase 2 合计** | 全模块 | 33 条 + CL×19 | **P0×0/P1×14/P2×18/P3×1** |

- 继续条件判定：4 维度均含 P1 → 全部必派第 2 轮深挖（合并批）
- 跨维同站点指针：C1-001=C4-005（ToolManagerImpl pathQueues 挂死）；C4-003↔C6-004/005↔C8-001/003（shell 管道死锁族）；CL-001=C4-CL-005=C8-004（pathQueues 无界）

### Phase 3 深挖（2026-10-20）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C4/C6/C8 | +6（全 P2×5/P3×1）：C1-009/010、C4-008、C6-009、C8-011/012 + 扩展 C1-005；CL-020 | **4 维度全部显式终止** |

- **深挖后累计 39 条**：C1×10（P1×2/P2×6/P3×2）、C4×8（P1×2/P2×6）、C6×9（P1×6/P2×3）、C8×12（P1×4/P2×8）→ P0×0/**P1×14**/P2×23/P3×2；CL×20

### Phase 4 独立复核（2026-10-20，4 维度串行）

| 维度 | 判定 | 最终严重度 | 关键裁定 |
|------|------|-----------|---------|
| C1 | 保留 10（降 1） | P1×1/P2×7/P3×2 | C1-002 P1→P2（shell 零生产实例化 + job sink 零接线）；C1-001 P1 维持（三分类可达面） |
| C4 | 保留 8（升 1） | P1×3/P2×5 | C4-001 并案校准升 P1（每次永久泄漏一线程一 fd）；JDK 实测证伪「首个调用 loud-fail」——排队分支全部静默悬挂 |
| C6 | 保留 7（降 2） | P1×4/P2×5 | C6-004/005 条件化降档（零生产接线）；C6-004 主面时序修正（纯本面=worker 钉死，调用方挂死需组合面）；C8-009 归属细化收编 |
| C8 | 保留 12（降 3） | P1×1/P2×11 | C8-001/002/003 随族条件化 P2；C8-006 主登记 pathQueues 三登记；C8-009 关闭为 C6-002 成员 |

- **复核后最终保留 39 条：P0×0/P1×9/P2×28/P3×2**；新发现×39/已知未修复×0/已修复×0
- 主登记建议网：C1-001（pathQueues 挂死族）、C6-001（reader 泄漏族）、C6-002（docker 清理族）、C6-004（管道死锁族案）、C8-006（pathQueues F4）

### Phase 5 C10 测试保护力（2026-10-20）

- 保护力矩阵 39 行：**会红 0 / 假保护 21 / 无测试 18**（9/9 P1 全部无测试）；缺保护登记 C10-001…023（全 P2，覆盖闭合；9 条 P1 收敛在 5 条登记）
- 反向钉死：ShellIOTest:294-300 条件命中（close 改 put(eof) 修复变体打红）；TestToolPathQueue:203-228 watch
- 独立复核：0 对账差异、判定修正 0、证据修正 3、23 登记全保留

### 勘误（closure audit 阻断 A）

- Phase 3 汇总行算误修正：C1 深挖后实际 P1×2/P2×6/P3×2（原记 P2×7）；总计 P2×23（原记 24）——条数链 33→39 与终局分布 P0×0/P1×9/P2×28/P3×2 不受影响

### Phase 6 合并裁定（2026-10-20）

- 独立裁定子代理串行派发一次；输入 = 39 条保留发现（四维 `## 最终保留项`/`## 维度复核结论`）+ C10 矩阵 + cra-index WI 1.1–3.1 台账 117 行
- 输出 = merge-adjudication.md：39 → 25 工单（8 缺陷族 MA-01~08 吸收 22 + 17 独立 + watch 0），P0×0/P1×5/P2×19/P3×1（MA-06 判级分歧暂计 P1）；冲突裁定 6 项；2 组判级分歧移交 7.1（沙箱族跨单元、shell 族 P1/P2）
- summary.md 由主 agent 按附录 B 撰写；cra-index 登记（报告行/5 P1 行/CL 3.2 小节；P1 累计 117→122）
