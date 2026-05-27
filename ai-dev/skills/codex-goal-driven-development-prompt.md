# Codex Goal 驱动模块完善提示词

> **定位**: 本文件是 codex `/goal` 机制的完整执行指令。codex 通过 `create_goal()` 创建目标后，会自动注入 continuation prompt 循环工作。本文件的内容应作为 codex 的初始 prompt 传入。
> **用法**: 由 `ai-dev/tools/codex-module-driver.sh` 读取并拼接到 codex 命令中。

---

## 目标模板

完善 `{MODULE}` 模块，彻底实现设计目标。不预设静态阶段清单，而是循环执行"审计→规划→执行→验证"直到所有设计目标达成。

## 工作方法：审计-规划-执行循环

### 阶段 A：多维度深度审计

每次循环从审计开始。按照以下顺序执行：

**第 1 步：基础健康检查**

1. 运行 `./mvnw clean install -pl {MODULE} -am -T 1C` 检查构建
2. 运行 `./mvnw test -pl {MODULE} -am -T 1C` 检查测试
3. 如果构建或测试失败，这就是当前最高优先级的工作项

**第 2 步：模块专属审计**

阅读 `ai-dev/design/{MODULE}/` 下的设计文档（如有），对照设计目标检查实现完成度。

用以下检查清单逐项验证，记录通过/不通过的项：

```
对每个组件，检查：
- 接口契约是否完整（方法不抛 UnsupportedOperationException、返回值不为 null/空）
- 组件间接线是否真正接通（不能只看类存在，要验证运行时调用链）
- 状态管理是否正确（快照/恢复、隔离性）
- 错误处理是否合规（NopException + ErrorCode，不吞异常）
- 测试是否覆盖端到端路径（从用户入口到最终输出）
```

**第 3 步：多维度深度审计（使用 `ai-dev/skills/deep-audit-prompts.md`）**

当基础检查通过后，执行 `deep-audit-prompts.md` 中的维度审计。

选择与模块相关的维度（不需要全部 20 个维度）。对模块类型的选择指引：

| 模块类型 | 重点维度 |
|---------|---------|
| 流处理引擎（如 nop-stream） | 01 依赖图、02 模块职责、09 错误处理、15 类型安全、16 测试覆盖、17 代码风格 |
| 业务模块（如 nop-auth、nop-job） | 01-11 全部、16-20 全部 |
| 基础设施模块（如 nop-core、nop-xlang） | 01 依赖图、02 模块职责、09 错误处理、15 类型安全、16 测试覆盖、19 命名一致性 |

如果模块有专属审计提示词（如 `ai-dev/skills/nop-code/audit-prompt.md`），也在本轮执行。

**第 4 步：重复审计直到收敛**

每次审计发现的问题修复后，重新运行基础健康检查，确认修复没有引入新问题。
如果审计发现了新问题，继续修复。重复直到一轮审计中**没有发现新的 P0/P1 问题**。

### 阶段 B：开放式对抗性审查（使用 `ai-dev/skills/open-ended-adversarial-review-prompt.md`）

当多维度审计收敛后（不再发现新的 P0/P1 问题），切换到开放式对抗性审查：

1. 阅读 `ai-dev/skills/open-ended-adversarial-review-prompt.md`
2. 按照其中的方法做一轮完整的对抗性审查
3. 发现的问题同样需要修复并验证
4. 重复对抗性审查直到不再发现新问题

### 阶段 C：规划、审核与执行

审计完成后，将发现的问题转化为 plan 并执行。

**C-1：问题分组**

将审计发现的问题按依赖关系和影响范围分组。分组原则：

- 同一子系统/同一文件的相关问题放入同一 plan
- 有依赖关系的修复放入同一 plan（先修 A 才能修 B → 同一个 plan）
- 每轮审计产生的 plan 数量控制在 **1-3 个**
- 不要强行把所有问题塞进一个 plan——如果一个 plan 的 Goals 超过 5 条，应该拆分
- 不要为每个小问题单独建 plan——相关的小修复应合并

**C-2：拟定 plan**

对每个分组创建一个 plan：

- 在 `ai-dev/plans/` 下创建 plan，**严格按照** `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 中的 Template 格式
- plan **必须**包含以下所有段落（缺一不可）：
  - `Plan Status` / `Last Reviewed` / `Source` 头部元数据
  - `Purpose`
  - `Current Baseline`
  - `Goals`
  - `Non-Goals`
  - `Scope`（含 In Scope / Out Of Scope）
  - `Execution Plan`（含 Phase 或 Workstream，每个 Phase 必须有 Status / Targets / Item Types / 执行项 checkbox / Exit Criteria）
  - `Closure Gates`（含 `独立子 agent closure-audit 已完成并记录证据` 条目）
  - `Deferred But Adjudicated`（如有）
  - `Non-Blocking Follow-ups`（如有）
  - `Closure`（含 Status Note / Closure Audit Evidence / Follow-up）
- plan 的 scope 必须收敛——Goals 是可验证的具体交付物，不是模糊的方向

**C-3：独立审核 plan（强制）**

plan 拟定完成后、开始执行之前，**必须**用独立子 agent 审核计划本身：

1. 使用 `ai-dev/skills/plan-reviewer-prompt.md` 的审核模板
2. 派发独立子 agent（不同 task_id）审核每个 plan
3. 审核重点：
   - Goals 是否具体可验证（不是"改进 XXX"而是"修复 XXX 使 YYY 通过"）
   - Exit Criteria 是否覆盖了所有 Goals
   - Scope 是否合理（不过宽也不过窄）
   - Non-Goals 是否明确了边界
   - Current Baseline 是否准确反映 live code 状态
   - 工作项之间是否存在遗漏的依赖
   - **必填段落是否齐全**（对照 C-2 的段落清单逐项核对）
4. 如果审核发现问题：
   - 修改 plan 解决审核意见
   - 重新提交审核
   - 直到审核通过
5. **只有审核通过的 plan 才能开始执行**

**C-4：执行 plan**

逐个执行审核通过的 plan（按依赖关系排序）：

- 编码、测试、验证
- **每完成一个工作项必须立即将 plan 文件中对应的 `- [ ]` 改为 `- [x]`**（这是关闭 plan 的前置条件，不是可选的附带动作）
- **禁止在所有 checklist 未勾选的情况下将 Plan Status 改为 completed**
- 每个 Phase 完成后，将 Phase Status 改为 `completed`，并将 Exit Criteria 全部勾选
- 每次改动后运行 `./mvnw test -pl {MODULE}/<submodule> -am`

**C-4.1：Phase 完成前检查（强制）**

在将任何 Phase Status 标记为 `completed` 之前，**必须**运行：

```bash
node ai-dev/tools/check-plan-checklist.mjs <plan-file>
```

如果该 Phase 还有未勾选的 Exit Criteria，**不能**将该 Phase 标记为 `completed`。必须先完成或移出所有未勾选项。

**C-4.2：Plan 完成前检查（强制，在 closure audit 之前）**

在执行 C-5 的 closure audit 之前，**必须**运行：

```bash
node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict
```

此命令必须退出码为 0（所有 checklist 已勾选、Closure Evidence 已写入）。如果退出码非 0，**不能进入 closure audit，也不能将 Plan Status 改为 completed**。必须先修复所有问题。

**C-5：plan 完成后做 closure audit（强制，独立子 agent）**

- **必须**用 `ai-dev/skills/plan-closure-audit-prompt.md` 做独立 closure audit
- **必须**由独立子 agent 执行（为 closure audit 单独启动的 fresh session，不能复用实现阶段的同一 agent session）
- Closure audit **必须**检查：
  1. plan 文件中所有 `- [ ]` 是否已改为 `- [x]`（`grep -c '\- \[ \]' <plan-file>` 必须返回 0）
  2. 每个 Phase 的 Exit Criteria 是否全部 `[x]`
  3. Closure Gates 是否全部 `[x]`
  4. 实际代码是否与 plan 描述一致（抽查关键代码路径）
  5. deferred / follow-up 项中没有 in-scope live defect
- **必须**在 plan 的 `Closure` 段落写入：
  - `Reviewer / Agent`: 独立子 agent 的 session ID 或 task ID
  - `Evidence`: 审计发现摘要，包括每条 exit criterion 的验证结果
- 更新每日日志 `ai-dev/logs/{year}/{month}-{day}.md`

**C-6：每轮执行完毕后提交代码**

每个 plan 执行完毕并通过 closure audit 后，**必须**按以下顺序操作：

1. **提交前强制检查**：运行 `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`，确认退出码为 0。如果非 0，说明有未完成的 checklist 项或缺失的 Closure Evidence，**不能提交**，必须先修复
2. **使用 `nop-git-master` skill 执行 git 提交**：
   - 提交范围：本轮 plan 涉及的所有代码、测试和文档变更
   - 提交信息格式：`<plan编号>: <plan标题> — <一句话摘要>`
   - 如果本轮有多个 plan，每个 plan 完成后分别提交
   - 不要跨 plan 积攒未提交的变更

全部 plan 执行完毕后，回到阶段 A 重新审计

### 阶段 D：目标达成

当以下所有条件满足时，调用 `update_goal(status="complete")`：

1. 构建通过：`./mvnw clean install -pl {MODULE} -am -T 1C`
2. 全量测试通过：`./mvnw test -pl {MODULE} -am -T 1C`
3. 多维度审计收敛（连续一轮无新 P0/P1 问题）
4. 开放式对抗性审查收敛（连续一轮无新发现）
5. 所有 plan 已 completed（**每个 plan 的所有 checklist 已勾选**）或显式 deferred
6. 设计文档中的所有设计目标都有对应的已验证实现

## 硬性约束

1. 禁止 java.sql.Connection — 用 IJdbcTemplate + IDialect
2. 禁止空方法体/continue跳过/吞异常 — 未实现功能抛 UnsupportedOperationException
3. 禁止修改 _gen 目录
4. 每个新增功能必须有测试，端到端覆盖从入口到出口
5. 每次改动后 `./mvnw test -pl {MODULE}/<submodule> -am` 必须通过
6. 每个 plan 完成前必须做独立 closure audit，**必须在 plan 文件中记录 evidence**
7. 遵循 `AGENTS.md` 中的 Code Conventions 和 Verification Checklist
8. **禁止在 plan 文件存在未勾选 checklist 的情况下将 Plan Status 标记为 completed**

## 审计技能文件索引

| 文件 | 用途 | 何时使用 |
|------|------|---------|
| `ai-dev/skills/deep-audit-prompts.md` | 多维度深度审计（20 个维度） | 阶段 A 第 3 步 |
| `ai-dev/skills/open-ended-adversarial-review-prompt.md` | 开放式对抗性审查 | 阶段 B |
| `ai-dev/skills/plan-closure-audit-prompt.md` | Plan closure 独立审计 | 阶段 C 第 C-5 步（关闭前强制，必须独立子 agent + evidence） |
| `ai-dev/skills/plan-reviewer-prompt.md` | Plan 创建后独立审核 | 阶段 C 第 C-3 步（执行前强制） |
| 模块专属审计提示词（如有） | 模块特有维度的补充审计 | 阶段 A 第 3 步 |
