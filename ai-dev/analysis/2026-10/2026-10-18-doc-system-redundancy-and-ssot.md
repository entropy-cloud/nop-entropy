# nop-entropy 文档体系冗余审计与单一出处（SSOT）方案

> Status: resolved
> Date: 2026-10-18
> Scope: `AGENTS.md`、`docs-for-ai/`、`ai-dev/`、子系统 README/owner docs/design/roadmap
> Conclusion: 冗余的根因是"按目录划分文档"而不是"按内容类型定义唯一 owner"。已在 `truth-and-precedence.md` 落 SSOT 归属矩阵与硬规则，并建立 `doc-system-ssot-roadmap.md` 做分阶段清理；不进行一次性全仓重写。

## Context

本次 nop-stream 状态编码设计/roadmap 任务中，同一个事实（v2/v3 分派口径、物理 stateName、性能裁定、模块清单）在 3–5 份文档里各写一份；改一处、漏两处，导致多轮独立审计反复发现不一致。用户要求分析文档体系冗余，并让权威文档尽量只有一个出处。

本分析回答两个问题：

1. 当前文档体系有哪些结构性冗余？
2. 如何用最小治理成本收敛到"一事实一出处"，同时不打断现有文档消费路径？

## Analysis

### 1. 目录划分 ≠ 内容划分

当前设计把文档分成 `docs-for-ai/`（平台使用）与 `ai-dev/`（开发过程），但两者都能装下四类内容：使用契约、架构决策、交付状态、执行历史。于是同一事实可以合法地出现在多处：

- `docs-for-ai/01-repo-map/module-groups.md` 既有模块清单，也有子模块内部实现细节、plan/roadmap 编号、benchmark 说明、已知限制（`:23` 的 nop-stream 行是极端例子，单行约 2000 字符）；
- `docs-for-ai/03-modules/nop-stream.md` 是 owner doc，但同时复制模块路由表、指标名表、REST 契约、路线图状态；
- `ai-dev/design/nop-stream/01-architecture-baseline.md` 是设计文档，但复制了模块表、roadmap 状态与验收进展；
- `nop-stream/README.md` 又复制模块表与能力限制。

一条事实因此有 3–5 个"看起来都权威"的副本。

### 1b. 规范性与时效性未分离

第二层结构问题是：即使内容不重复，同一份文档也常同时承载“当前规范”与“随时间变化的事实”。规范文档里出现 `Created/Updated`、`Stage N`、`plan NNN`、`WI N`、审计轮次、变更记录，会把“当前结论”与“历史证据”混在一起：

- 读者无法判断哪段是当前契约、哪段只是历史解释；
- 每次演进都要改规范文档，而且容易残留旧结论（出现“文档说 A，代码已 B”）；
- 审计必须读 changelog 才能判断当前状态，成本高且易分歧。

正确模型：**规范性文档（`AGENTS.md`、`docs-for-ai/**`、`ai-dev/design/**`、guide、owner doc）只写当前结论与情境化理由（“当 A 时选 B，因为 C”），不写时间演化；时效性文档（analysis/logs/plans/audits/bugs/testing/backlog/discussions）带日期/Status，记录历史与证据，可过时。** 变更历史由 git 和时效性文档承担，design 不承担 changelog。

### 2. 已确认的重复事实（抽样）

| 事实 | 出现位置（抽样） | 副本数 | 风险 |
|---|---|---|---|
| nop-stream 模块清单/职责 | `docs-for-ai/01-repo-map/module-groups.md:23`、`nop-stream/README.md` 模块表、`docs-for-ai/03-modules/nop-stream.md` 模块路由、`ai-dev/design/nop-stream/README.md` §1.2、`01-architecture-baseline.md` §二 | 5 | 新增/删除子模块时必漏改，审计反复报"模块数 11 vs 13" |
| CEP×RocksDB 限制 | `module-groups.md:23`、`01-architecture-baseline.md`、`state-encoding-and-restore-design.md`、`cep-design.md`、`nop-stream/README.md` | 5 | 能力状态变化时无法一次收敛；本次设计曾把它写成永久限制 |
| "13 个模块"计数 | `module-groups.md`、`docs-for-ai/INDEX.md`、`01-architecture-baseline.md` | 3 | 同一计数三处维护 |
| checkpoint 配置默认值 | owner doc `nop-stream.md`、user guide、`checkpoint-design.md` | 3 | 默认值调整后三处不一致 |
| roadmap/WI 状态 | 多份 design 文档与模块总表引用 `WIxx done/plan xxxx` | 多处 | 状态漂移；审核时无法判定哪份是当前值 |
| v2/v3 分派口径 | `state-encoding-and-restore-design.md` §一/§3.8、`checkpoint-design.md` §2.6、roadmap WI2/WI5/WI8 | 3 | 本次第二/三轮审计重复发现同一冲突 |

### 3. 根因

1. **没有"内容类型 → canonical owner"映射**：只有目录边界，没有事实归属。
2. **owner doc 被当作"什么都能装"**：实现能力、设计理由、路线图状态、执行历史混在一起。
3. **design 文档复制交付状态**：design 本应是"目标架构 + 决策 + 拒绝方案"，却经常写 `WIx done`、`plan xxx`。
4. **模块总表被当作百科**：`module-groups.md` 承担了模块清单 + 内部职责 + 技术债 + benchmark + 限制，成为最高频冲突点。
5. **没有"link, don't copy"的硬规则与门禁**：只有 `check-doc-links` 检查链接有效性，不检查"同一事实多处副本"。
6. **审计产出没有去重机制**：审计发现只增不减，长期累积成 parallel docs。

### 4. 优化方案（已落地的治理层）

- 在 `docs-for-ai/00-start-here/truth-and-precedence.md` 新增：
  - **Document Classes**：规范性 vs 时效性二分及其硬规则（规范性不写日期/历史/Stage/plan/WI/审计轮次；时效性必须带日期与 Status，可过时）；
  - **Content Ownership Matrix**：代码行为 / 平台使用 / 模块清单 / 单模块能力 / 架构决策 / 交付状态 / 执行证据 / 流程规则 / 导航，各只有一个 owner；
  - **Single-Source Rules**：link don't copy、状态只住状态块、禁止复制清单（模块表/能力表/配置默认值/指标名/错误码/测试计数/状态）、reconcile-on-touch、派生视图标记、新增先查重。
- 在 `AGENTS.md` 新增 `Document Authority (SSOT)` 与 `Large Design / Review Tasks`：把规则变成 AI 行为硬约束，并约束大任务先建证据底账、审查预算 ≤2 轮。
- 在 `ai-dev/lessons/19-doc-ssot-and-evidence-first.md` 记录本次返工教训与执行细则。
- 建立 `ai-dev/backlog/doc-system-ssot-roadmap.md` 做分阶段清理（模块总表瘦身、owner/design 边界、checker 与 CI 门禁）。

### 5. 不采用的方案

| 方案 | 拒绝理由 |
|---|---|
| 一次性全仓重写/合并文档 | 4000+ 文档、消费路径多，风险与工作量不可控；且会在迁移期制造更多临时副本 |
| 取消 `ai-dev/`，全部并入 `docs-for-ai/` | 破坏"使用知识 vs 开发过程"的边界；agent 需要不同阅读面 |
| 只靠 doc-link checker | 链接检查无法发现"两份都有效但内容不同"的语义副本 |
| 把全部状态放进代码注释/自动生成 | 状态需要人可读的 roadmap/plan 视图；生成器可以作为后续优化，但不作为唯一方案 |
| 所有人肉记住 SSOT 规则 | 规则必须进入 AGENTS.md + 检查器，不能依赖注意力 |

## Conclusion

- 冗余不是"文档写多了"，而是**同一事实没有唯一 owner**；另一个根因是**规范性与时效性未分离**，导致规范文档被迫承载 changelog，历史与当前结论混写。
- 治理层（归属矩阵 + 硬规则 + lesson + 清理 roadmap）已落地；全仓清理按模块/内容类分片执行，不做一次性重写。
- 后续每新增一份文档，必须先回答：它属于矩阵中的哪一类？owner 是谁？其他位置是否需要改为链接？
- 被否决方案：全仓一次性重写、取消 `ai-dev/`、只靠 doc-link checker。
- 后续工作：`ai-dev/backlog/doc-system-ssot-roadmap.md`；规则执行面：`docs-for-ai/00-start-here/truth-and-precedence.md` 与 `AGENTS.md`。

## Open Questions

- [ ] `module-groups.md` 是否最终拆为"模块清单（稳定）+ 各模块 owner doc 链接（能力/限制）"两层？由 doc-system roadmap WI4 裁定。
- [ ] `check-doc-ssot.mjs` 的机械判据（哪些重复模式可自动检测）需要基线扫描后确定，避免产生大量误报。
- [ ] 导航类文档（INDEX/README）能否改为由 front-matter 或脚本生成？先以"派生视图"标记过渡。
- [ ] 历史遗留的超长模块行（如 nop-stream）迁移时是否需要保留历史细节？倾向迁移到对应 owner doc/design 后再删减。

## References

- `docs-for-ai/00-start-here/truth-and-precedence.md`
- `AGENTS.md`
- `ai-dev/lessons/19-doc-ssot-and-evidence-first.md`
- `ai-dev/backlog/doc-system-ssot-roadmap.md`
- `docs-for-ai/01-repo-map/module-groups.md`
- `docs-for-ai/03-modules/nop-stream.md`
- `ai-dev/design/nop-stream/01-architecture-baseline.md`
- `nop-stream/README.md`
