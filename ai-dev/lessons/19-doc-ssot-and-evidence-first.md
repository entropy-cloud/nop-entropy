# 19: 大任务先建证据底账，文档只写链接不写副本

**日期**：2026-10-18
**触发**：nop-stream「状态编码设计 + 36 WI 收口 roadmap」任务。任务最终通过四轮独立审查，但过程出现大量返工与重复审查（3 个 background reviewer 白跑、同一设计整篇重写、27→36 WI 重排、同一分派口径在 3 份文档里反复漏改）。

## 教训 A：大设计任务的第一件事不是写文档，而是建"证据/裁定底账"

本次首版设计在未核实以下事实前就写完了 27 个 WI，导致第二轮审查全部推翻重写：

- CEP `Lockable` 的引用计数是 live `lockNode` 的**多重集**语义（同节点同 dewey 可多次加锁），不是集合；
- `eventQueuesStateName` 也是 durable state，漏了会丢缓冲事件；
- 已有物理 stateName 不能重命名（会静默恢复为空）；
- 性能项已有 360/2279/plan 01 裁定，不能重新当 open；
- 分布式 enumerator/backend 缺口已有 DIV/AR 登记。

**防御**：100 行以上的设计/roadmap，先写 ≤1 页「证据底账」：10–15 条载荷事实 + 已有裁定，每条带 `file:line`；底账未闭合前不写细节。底账完成后先交骨架（目录 + 核心决策 + 风险），只让 1 个 reviewer 打架构，再补全。

## 教训 B：审查要有预算与停止判据

本次共 4 轮、11 个子 agent：第一轮 3 个 background 结果未回收，又用 foreground 重开 3 个；随后两轮仍出现"同一口径多处不一致"。

**防御**：

1. 设计/roadmap 最多 2 轮独立审查；每轮只收 `[Blocker|Major]` + 证据 + 修复，输出 ≤100 行。
2. 只剩 Minor 即停：登记 watch-only，不再润色。
3. 同一问题不要同时开 background 与 foreground reviewer；用 background 就必须先设计回收方式（`job_output` / `send_message` / 等待通知），未回收前不开第二个。
4. 审查前先自检：对每个 canonical claim（公式/分派/名称/状态）跑一次全文档 grep，确认只有一种表述。
5. 禁止 sleep 轮询；等待期间做独立工作。

## 教训 C：文档体系必须"一事实一出处"，其余文档只链接

本次 36 WI roadmap 与设计文档出现多处"改了一处、另外两处仍旧"：

- v2/v3 分派口径同时存在于 design §一/§3.8、roadmap WI2/WI5/WI8、checkpoint-design §2.6；
- 物理 stateName 同时存在于 design、cep-design、README、roadmap；
- 性能裁定同时存在于 analysis、plan、roadmap 台账。

**根因**：仓库按目录划分（docs-for-ai vs ai-dev），但没有按"内容类型"定义权威 owner；同一事实可以在 owner doc、design、README、roadmap 各写一份。

**防御**（已落档）：

1. 权威归属矩阵与 SSOT 规则：`docs-for-ai/00-start-here/truth-and-precedence.md`；
2. AI 行为硬规则：`AGENTS.md` 的 **Document Authority (SSOT)** 与 **Large Design / Review Tasks** 节；
3. 清理路线图：`ai-dev/backlog/doc-system-ssot-roadmap.md`（含模块表瘦身、owner/design 边界、`check-doc-ssot.mjs` 门禁）；
4. 具体执行：改一个事实时，同时更新 canonical owner，并把其他位置的副本改为链接或"派生摘要，以 X 为准"；禁止复制完整模块表、能力/限制表、状态块、配置默认值表、指标名表。

## 教训 D：规范性与时效性必须分开

规范文档（AGENTS/docs-for-ai/design/guide/owner doc）一旦承载 `Created/Updated`、Stage/plan/WI/审计轮次、变更记录或“最初…后来…”的演进叙事，就会出现两个问题：读者分不清当前契约与历史证据；每次演进都要改规范文档且容易残留旧结论。本次 nop-stream design README 的巨型 `Updated:` 行、design 文档里的 Stage/WI 考古注释都是实例。

**防御**：

- 规范性文档只写当前结论与情境化理由（“当 A 时选 B，因为 C”），变化时**原地更新为最新**；不写日期/历史/changelog。
- 时效性文档（analysis/logs/plans/audits/bugs/testing/backlog/discussions）必须带日期/Status，记录历史与证据，可过时。
- 变更历史由 git 与时效性文档承担；被取代的设计文档直接删除，不保留 superseded 副本。
- 规则已写入 `docs-for-ai/00-start-here/truth-and-precedence.md` 的 **Document Classes**、`AGENTS.md` 与 `ai-dev/design/00-design-writing-guide.md`；清理归 `doc-system-ssot-roadmap.md` WI9。

## 适用范围

所有跨 2 个以上文档的设计、roadmap、审计回复任务；所有 `docs-for-ai/` 与 `ai-dev/` 的文档变更。

## 关联

- `docs-for-ai/00-start-here/truth-and-precedence.md`
- `ai-dev/backlog/doc-system-ssot-roadmap.md`
- `ai-dev/analysis/2026-10/2026-10-18-doc-system-redundancy-and-ssot.md`
