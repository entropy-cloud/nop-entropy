# 深度审核汇总报告 — nop-message 全族并发·异步·资源专项审计（CRA WI 4.1）

## 基本信息

- **审核单元**: `nop-message`（4 有源子模块：core 7f/723 + kafka 7f/1,955 + pulsar 7f/1,513 + debezium 10f/1,949，合计 31 files/6,140 lines；codec 零 java 源登记零审计面；手写口径不含 `_` 前缀与 target/，`_gen/` 残留 0）
- **审核日期**: 2026-10-20
- **执行维度**: C1 共享可变状态、C3 异步链路、C5 内存有界、C6 资源生命周期、C8 背压流控（roadmap 4.1 裁剪集）+ C10 测试保护力（C2/C4/C7/C9 不在维度集，overlay 线索 CL×27）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 1407fc426e（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2477-cra-wi4.1-nop-message.md`（两轮对抗性审查通过后执行；R1 1 Major + 3 Minor 全修——**CL-022 落点清单错列 debezium→实测仅 kafka/pulsar 2 站点**、orTimeout 计数口径注、pulsar 对抗审查目录预登记、clearConnector 跨模块清除路径预注；R2 0 Major 置 active）
- **执行说明**: 全部批次串行派发（运营者要求）——5 初审批次（各单批覆盖 4 子模块）+ 深挖 1 轮 + 复核 2（合并批）+ C10 + 裁定；**本单元为多个前序单元移交落点**（WI 2.3 CL-022 orTimeout、WI 2.5 REGISTRY 消费面双轨、WI 1.2 debezium 同族、WI 3.1 MA-11 同契约异实现）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 11（P1×1/P2×6/P3×4） | +1（P2） | 12 | 1（C1-001 P1→P2 与 WI 1.2 C3-006 同案双轨对齐——FAILURE_LISTENERS 使用面/本体面互补） | 0 |
| C3 | 2 | 8（P1×1/P2×5/P3×2） | +2（P2/P3） | 10 | 0 | 0 |
| C5 | 2 | 4（P2×2/P3×2） | +2（P3×2） | 6 | 0 | 0 |
| C6 | 2 | 6（P1×1/P2×4/P3×1） | +1（P3） | 7 | 0 | 0 |
| C8 | 2 | 8（P1×3/P2×4/P3×1） | +1（P2） | 9 | 1（C8-001 P1→P2 并入 C3-001 主登记——getResult 无上界同缺陷） | 0 |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 44 行矩阵全维持 + 缺保护 12（P2×9/P3×3） | 0 | 0 |
| **合计** | — | **37** | **+7 = 44** | **44** | 2 | 0 |

R2 深挖五维度全部显式终止（+7 全 P2×3/P3×4 且收敛证据齐备——Helper/Probe/Bridge/Registrar 四面 examined-empty、配置构建期/schema history 句柄面 examined-empty、4 项 grep 口径复现）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 44 条保留缺陷 → 合并后 **28 张工单 = 10 缺陷族（MA-01…10，吸收 26 条）+ 18 独立立案**；watch-only 0（显式空——44 条全部具备可行动修复点）。逐条落位无遗漏（脚本回测落位表零遗漏）。
- 工单判级：**P0×0 / P1×3 / P2×15 / P3×10**；判级分歧 4 项 + 设计裁决 4 项移交 7.1。
- 显式裁定 8 处（复核登记确认 5 + 新增归并 3：C3-007 残留②→C8-008、CL-021→CL-017 并线、C5-002 与 C1-001 同表成族 MA-10）。
- 跨单元对账：与 cra-index WI 1.1–3.4 台账（130 条 P1）**零同站点重复**；跨单元关联 8 处（MA-01↔WI 3.1 MA-11 同契约异实现、MA-03+C1-002↔WI 2.5 消费面双轨、MA-09↔WI 1.2 MA-07、MA-10↔WI 1.2 C3-006 双轨、C3-009↔WI 2.1 C3-001 平台绝缘层、MA-04↔WI 2.1 C4-001 globalTimer 约束、C3-007↔WI 2.3 CL-022 闭环、CL-003/006/010/018↔WI 1.2 MA-06）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行） |
| P1 | 3（2 族 + 1 独立） | MA-02 Debezium 屏障两极性（C3-002 回调无异常屏障 CDC 静默断流 + C8-004 dispatchEvent 吞异常推进 offset——同 diff 配对，生产主路径结构性免疫、直接嵌入方受损面）；MA-05 DebeziumMessageSource subscribe 站点（C6-002 先 put 后启动幽灵订阅——长生命周期嵌入方双投递）；C8-002 Local 总线消费失败静默丢弃——三层失败架构断链（Local 为生产路径 SysDao/nop_sys_event，无已声明边界可援引） |
| P2 | 15 | MA-01 getMessage 无上界等待族（C3-001 主，C8-001 并入；60s 有界 + null 取消令牌断裂——与 WI 3.1 MA-11 契约对齐）、MA-03 REGISTRY 族（C1-002/C5-001/C6-004 三面 + stop-clear vs TTL 一次裁决）、MA-04 globalTimer 约束族、MA-06 destroy 窗口守卫族（五条）、MA-07/08/09/10 + C3-004/C3-005 等 |
| P3 | 10 | C3-007 CL-022 落点残留、C1-012 配置活 map（反向钉死阻断不可变修复）、C5-002/005/006、C6-005/007、C8-008 过载可观测缺口等 |

C10 另有缺保护登记 12 条（P2×9/P3×3，覆盖 44/44 闭合[含 3 行挂靠处置]，全部映射随本体工单）：保护力矩阵 44 行**会红 0/假保护 23/无测试 21**——P1×4 全部无保护；反向钉死 5 处（4 硬 + 1 软——双后端分叉被成对测试合法化/活 map setup 阻断不可变修复等，修复 diff 必须先改测试）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 44 条保留发现全部为**新发现**（cra-index WI 1.1–3.4 台账零同站点命中；WI 1.2 C3-006 为跨单元同案双轨非本单元前案；R5-CON-02/03 为已交付机制、本批是新失效面）；已知未修复 0、已修复 0。
- 生产主路径结构性免疫标注：C3-002/C6-002/C8-004 的受损面为直接嵌入 DebeziumMessageSource 的部署（仓内 main 仅 fraud-example 示例），nop-stream 主路径自愈（capture-and-rethrow + checkpoint 重放）——判级分歧显式登记 7.1。

## 关键发现摘要（P1 全清单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C3-002 + C8-004（MA-02，同 diff 配对） | Debezium 事件回调无异常屏障：畸形事件（非对象 JSON/null key NPE/数值宽度 CCE）逃逸进 engine.run() 使 CDC 流终态死亡（isStarted 永久 true 静默断流）+ dispatchEvent 吞订阅者异常推进 offset（事件永久丢失，无重放）——屏障存活/投递两极性 | nop-message-debezium |
| 2 | C6-002（MA-05 主） | DebeziumMessageSource.subscribe 先 put 后启动引擎：启动失败幽灵订阅残留 + 调用方拿不到 ICancellable 永不可注销——后续成功启动后事件持续投递给「被告知失败」的消费者 | nop-message-debezium |
| 3 | C8-002（独立） | LocalMessageService 消费失败静默丢弃且从不派发 onConsumeFailure——三层失败架构在 Local 整体断链（Local 为生产路径 nop_sys_event 通道） | nop-message-core |

## 盲区自评

- C2/C4/C7/C9 为 roadmap 4.1 裁剪维度：仅 overlay 线索登记（CL×27：并入工单 18/独立线索 4/移交 5/驳回 0），未做完整口径审计；若 7.1 需完整面需补审。
- 外部客户端内部行为（kafka-clients/pulsar-client SDK 内部队列、FeishuClient 等）为已声明边界——C8 面按本模块配置与调用面审计，SDK 内部黑盒。
- Debezium 判级三案（C3-002/C6-002/C8-004）的生产接线权重分歧显式登记 7.1（fraud-example vs 直接嵌入部署的暴露面权衡）。
- R2 深挖五维度全终止（+7 全 P2/P3），无口径偏差。
- C10「会红」口径文件内重定义为缺陷回退方向（与技能正文修复方向变异相反）——自洽且以反向钉死节补偿正方向，复核认可登记为口径注记。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：无新增重叠面。
- 与 WI 1.1–3.4 台账（130 条 P1）零同站点重复；跨单元关联 8 处单列（详见合并裁定 §七）。
- security-audit roadmap：无重叠面。
