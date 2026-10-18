# Source of Truth and Precedence

当多个信息来源对同一事实有不同描述时，按此优先级解决冲突。

## Precedence Rules

### 代码行为相关

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | **实际代码行为**（运行时） | 最终真相。如果文档和代码不一致，以代码为准，但必须修复文档 |
| 2 | **ORM 模型**（`model/*.orm.xml`） | 数据结构的权威来源 |
| 3 | **xmeta**（`meta/*.xmeta.yaml`） | API schema 的权威来源 |
| 4 | **`docs-for-ai/`** | 平台使用规范。当 `docs-for-ai/` 与代码冲突，视为 docs bug 优先修复 |
| 5 | **`ai-dev/`** | 开发过程记录。不作为代码行为的权威来源 |

### 开发指导相关

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | **`AGENTS.md`** | AI 行为的最高约束 |
| 2 | **`docs-for-ai/00-start-here/`** | 全局默认和项目状态 |
| 3 | **`docs-for-ai/02-core-guides/`** | 领域规范（service-layer、architecture-principles 等） |
| 4 | **`docs-for-ai/03-runbooks/`** | 具体操作步骤 |
| 5 | **`ai-dev/` 中的 00-*-guide.md** | 文档写作规范 |

## Document Classes: Normative vs Time-Sensitive

文档必须二选一，不允许混合：

| 类别 | 目录/文档 | 时间属性 | 内容约束 | 过时处理 |
|---|---|---|---|---|
| **规范性（Normative）** | `AGENTS.md`、`docs-for-ai/**`、`ai-dev/design/**`、`ai-dev/*/00-*-guide.md`、各 subsystem README/owner doc | **无时间演化**；不写创建/更新日期、不写变更历史/Stage/plan/WI/审计轮次 | 只描述**当前有效**的结论、契约、约束与“在什么情况下选什么、为什么”；理由以条件/取舍表达，不以时间顺序表达 | 内容变化时**原地更新为最新**；旧结论不保留在 normative 文档中 |
| **时效性（Time-Sensitive）** | AI 开发记录区的 analysis/logs/plans/audits/bugs/testing/backlog/discussions | **必须带日期/时间戳**（analysis 还要 Status；roadmap 带 Last updated） | 记录“发生了什么、当时为什么、证据是什么”；可以包含演进叙事与旧结论 | 可以过时；不作为当前行为/契约的权威来源 |

**硬规则**：

1. **Normative 不写历史**：不得出现“Updated/更新于/历史版本/Stage N/plan NNN/WI N/第 N 轮审计”等时间演化叙述；变更历史由 git 与时效性文档承担。
2. **Normative 的理由是情境化的**：写“当输入可重放且 sink 支持 2PC 时选 STRICT_EXACTLY_ONCE，因为……”，不写“最初是 AT_LEAST_ONCE，后来改成 STRICT”。
3. **时效性文档必须可判龄**：没有日期/Status 的过程记录视为不规范；结论一旦确认，必须收敛进 normative owner，时效性文档保留为证据（可标 superseded/stale）。
4. **引用方向**：normative 可以链接时效性文档作为证据，但不得复制其时间性内容；时效性文档可以引用 normative，但不得声称替代它。
5. **冲突裁决**：行为/契约以 normative 为准；历史事实以时效性文档为准；两者冲突时按上文 Precedence Rules 与 Content Ownership Matrix 修复，而不是折中。

## Content Ownership Matrix (SSOT)

**每个事实只有一个 canonical owner。** 下表是内容类型到 owner 的权威映射；其他文档只能链接 owner 或写不超过 3 行的摘要，并在摘要后标注「以 `<owner>` 为准」。

| 内容类型 | Canonical owner | 允许的派生形式 |
|---|---|---|
| 代码实际行为 | 运行时代码（最终真相） | 文档只描述使用契约，行为冲突时修文档 |
| 平台使用规范（API/约定/runbook） | `docs-for-ai/` 对应 owner doc（`02-core-guides/`、`03-runbooks/`、`03-modules/`） | 其他 guide 链接，不复制规则正文 |
| 模块清单/目录结构/子模块职责 | `docs-for-ai/01-repo-map/module-groups.md` | README/owner doc 只写链接或 1 行摘要；不得复制完整模块表 |
| 单模块已实现能力与运维契约 | `docs-for-ai/03-modules/<module>.md`（owner doc） | design/README 链接；不得复制完整能力/配置/指标表 |
| 架构决策/目标架构/拒绝方案 | `ai-dev/design/<subsystem>/`（README + 专题设计） | 其他文档链接；design 不复制实现状态与执行历史 |
| 交付状态/进度（done/planned/todo、里程碑） | `ai-dev/backlog/*roadmap.md` 的 `## Work Item Status`；执行期以 `ai-dev/plans/*` 为准 | 其他文档只写“当前状态见 X”，不复制状态 |
| 执行证据/历史（测试结果、审计发现、修复记录） | AI 开发记录区（`ai-dev`）的 plans/logs/audits/bugs/testing 子目录 | roadmap/design 只引用路径，不转抄结论 |
| 流程/写作规则（含 AI 行为约束） | `AGENTS.md` + `ai-dev/*/00-*-guide.md` | 其他文档链接；不复制整套规则 |
| 导航/索引 | `docs-for-ai/INDEX.md`、各 subsystem `README.md` | 必须标注“派生视图”，且可重建；不得引入新事实 |

## Single-Source Rules

1. **Link, don't copy**：同一事实只允许一个 full form；其他位置只能写链接或 ≤3 行摘要（摘要不引入新事实，并以 owner 为准）。
2. **状态隔离**：`done/planned/todo` 等状态 token 只允许出现在 roadmap/plan 的状态块；README、owner doc、design、module-groups 不得复制状态。
3. **禁止复制的内容类**：完整模块表/子模块清单、完整能力或限制矩阵、配置默认值表、指标名表、错误码总表、测试计数、roadmap 状态/里程碑。
4. **Reconcile-on-touch**：任务中一旦修改某事实，必须同步更新 canonical owner，并把其他位置的副本改为链接/摘要；同一任务内完成，不留给后续审计。
5. **派生文档必须可辨**：INDEX、模块总表、subsystem README 顶部应标注「派生视图，以 X 为准」；若无法自动生成，至少标注 owner。
6. **新增内容先查重**：先 grep 现有 owner；已有 owner 时只补链接，不新建平行文档；结论未定时放 AI 开发记录区（`ai-dev`）的 analysis 子目录（`Status: open`），确认后收敛进 owner。

> 规则执行细节与清理计划见 AI 开发记录区（`ai-dev`）的 backlog/doc-system-ssot-roadmap.md；过程教训见同区 lessons/19-doc-ssot-and-evidence-first.md。

## Conflict Resolution Protocol

当发现冲突时：

1. **代码 vs `docs-for-ai/`**：以代码为准。在同一任务中修复 `docs-for-ai/`（视为 docs bug）。如果修复会改变用户可见行为，则需 `ask-first`。
2. **`docs-for-ai/` vs `ai-dev/`**：以 `docs-for-ai/` 为准。`ai-dev/` 是开发过程记录，不作为规范性引用。
3. **生成文件（`_gen/`、`_*.xml`）vs 源模型**：以源模型（`model/*.orm.xml`）为准。生成文件是派生物，重新生成后覆盖。
4. **`docs-for-ai/` 内部冲突**：以更具体的 guide 为准（如 `service-layer.md` 优于 `INDEX.md` 的默认规则摘要）。

## Stale Documentation

| 状态 | 含义 | AI 行为约束 |
|------|------|------------|
| `fresh` | 文档与代码一致 | 正常工作 |
| `partially stale` | 部分文档过时 | 仅在已验证新鲜的切片上工作，其余视为 `plan-first` |
| `stale` | 文档明显过时 | `research-only` 直到基线重建 |
| `unknown` | 未验证 | 等同 `stale` |

AI 可从实际代码证据更新过时文档，但不可自行将 `stale` 标记为 `fresh`。

## Related

- `./project-context.md` — 文档新鲜度标记位置
- `../02-core-guides/architecture-principles.md` — "模型是源头"原则
- `../../AGENTS.md` — "Generated Files And Docs" 与 "Document Authority (SSOT)" 节
- AI 开发记录区（`ai-dev`）的 lessons/19-doc-ssot-and-evidence-first.md — 单一出处与证据底账的过程教训
- AI 开发记录区（`ai-dev`）的 backlog/doc-system-ssot-roadmap.md — 全仓文档去重与门禁清理计划