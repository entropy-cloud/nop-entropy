# Codex Goal 驱动模块完善提示词

> **定位**: 本文件是 codex `/goal` 机制的完整执行指令。codex 通过 `create_goal()` 创建目标后，会自动注入 continuation prompt 循环工作。本文件的内容应作为 codex 的初始 prompt 传入。
> **用法**: 由 `ai-dev/tools/codex-module-driver.sh` 读取并拼接到 codex 命令中。

---

## 目标模板

完善 `{MODULE}` 模块，彻底实现设计目标。不预设静态阶段清单，而是循环执行"审计→规划→执行→验证"直到所有设计目标达成。

## 工作方法：审计-规划-执行循环

每次循环严格按照以下阶段顺序执行：**A → B → C → D → 回到 A**。

---

### 阶段 A：多维度深度审计（独立子 agent）

**目标**：发现模块中所有值得修复的问题，将审计结果写入 `ai-dev/audits/`。

**第 1 步：基础健康检查**

1. 运行 `./mvnw clean install -pl {MODULE} -am -T 1C` 检查构建
2. 运行 `./mvnw test -pl {MODULE} -am -T 1C` 检查测试
3. 如果构建或测试失败，这就是当前最高优先级的工作项，先修复再继续

**第 2 步：模块设计文档对照**

阅读 `ai-dev/design/{MODULE}/` 下的设计文档（如有），对照设计目标检查实现完成度。记录通过/不通过的项。

**第 3 步：派发独立子 agent 执行多维度深度审计**

1. 选择与模块相关的维度（不需要全部 20 个维度）。对模块类型的选择指引：

   | 模块类型 | 重点维度 |
   |---------|---------|
   | 流处理引擎（如 nop-stream） | 01 依赖图、02 模块职责、09 错误处理、15 类型安全、16 测试覆盖、17 代码风格 |
   | 业务模块（如 nop-auth、nop-job） | 01-11 全部、16-20 全部 |
   | 基础设施模块（如 nop-core、nop-xlang） | 01 依赖图、02 模块职责、09 错误处理、15 类型安全、16 测试覆盖、19 命名一致性 |

2. 如果模块有专属审计提示词（如 `ai-dev/skills/nop-code/audit-prompt.md`），也纳入本轮执行。

3. **派发独立子 agent**（使用 `ai-dev/skills/deep-audit-prompts.md` 作为提示词）执行审计：
   - 子 agent 为每个选定维度独立执行审计
   - 子 agent **必须将审计结果写入 `ai-dev/audits/` 目录**，命名格式：`YYYY-MM-DD-deep-audit-{module}/`（含多份报告时用目录）或 `YYYY-MM-DD-deep-audit-{module}.md`（单文件时）
   - 审计记录必须包含 `summary.md`（或摘要段落），每条发现标注严重程度（P0/P1/P2/P3）

4. 主 agent 读取审计结果，判断是否有 P0/P1 问题：
   - 如果有：直接进入**阶段 C**（基于审计结果拟制 plan），**跳过阶段 B**
   - 如果没有：继续进入**阶段 B**

---

### 阶段 B：开放式对抗性审查（独立子 agent，与阶段 A 分开执行）

**前置条件**：阶段 A 的多维度深度审计已完成且无 P0/P1 问题（或所有 P0/P1 已通过上一轮 plan 修复）。

**目标**：用非固定维度的开放式视角发现深层问题，将审查结果写入 `ai-dev/audits/`。

1. **派发独立子 agent**（使用 `ai-dev/skills/open-ended-adversarial-review-prompt.md` 作为提示词）执行对抗性审查：
   - 子 agent 不受固定维度约束，从代码异常信号、模式、矛盾、缺失物或跨边界连锁效应出发
   - 子 agent **必须将审查结果写入 `ai-dev/audits/` 目录**，命名格式：`YYYY-MM-DD-adversarial-review-{module}/` 或 `YYYY-MM-DD-adversarial-review-{module}.md`
   - 审查记录必须包含摘要，每条发现标注严重程度

2. 主 agent 读取审查结果，判断是否有需要修复的问题：
   - 如果有：进入**阶段 C**
   - 如果没有：说明当前模块质量已收敛，进入**阶段 D**检查目标是否达成

---

### 阶段 C：规划、迭代审核与执行

**前置条件**：阶段 A 或 B 的审计/审查已产出 `ai-dev/audits/` 下的结果文档，其中有需要修复的问题。

#### C-1：问题分组

将审计发现的问题按依赖关系和影响范围分组。分组原则：

- 同一子系统/同一文件的相关问题放入同一 plan
- 有依赖关系的修复放入同一 plan（先修 A 才能修 B → 同一个 plan）
- 每轮审计产生的 plan 数量控制在 **1-3 个**
- 不要强行把所有问题塞进一个 plan——如果一个 plan 的 Goals 超过 5 条，应该拆分
- 不要为每个小问题单独建 plan——相关的小修复应合并

#### C-2：拟定 plan

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
- plan 的 `Source` 应引用阶段 A/B 产出的审计文档路径

#### C-3：独立子 agent 迭代审核 plan（强制，反复迭代直到无 blocking findings）

plan 拟定完成后、开始执行之前，**必须**用独立子 agent 反复审核计划本身。这不是一轮审核，而是迭代过程：

1. 派发独立子 agent（使用 `ai-dev/skills/plan-reviewer-prompt.md` 作为提示词，不同 task_id）审核每个 plan
2. 审核重点：
   - Goals 是否具体可验证（不是"改进 XXX"而是"修复 XXX 使 YYY 通过"）
   - Exit Criteria 是否覆盖了所有 Goals
   - Scope 是否合理（不过宽也不过窄）
   - Non-Goals 是否明确了边界
   - Current Baseline 是否准确反映 live code 状态
   - 工作项之间是否存在遗漏的依赖
   - **必填段落是否齐全**（对照 C-2 的段落清单逐项核对）
3. **如果审核发现 blocking findings**：
   - 修改 plan 解决审核意见
   - **重新派发独立子 agent 审核修改后的 plan**（新的 task_id，全新 session）
   - 重复此过程直到审核**无 blocking findings**
4. **如果审核只发现 advisory findings 且无 blocking findings**：
   - 可以选择采纳 advisory 建议改进 plan，但不需要再次审核
   - 进入执行阶段
5. **只有无 blocking findings 的 plan 才能开始执行**

#### C-4：执行 plan

逐个执行审核通过的 plan（按依赖关系排序）：

- 编码、测试、验证
- **每完成一个工作项必须立即将 plan 文件中对应的 `- [ ]` 改为 `- [x]`**（这是关闭 plan 的前置条件，不是可选的附带动作）
- **禁止在所有 checklist 未勾选的情况下将 Plan Status 改为 completed**
- 每个 Phase 完成后，将 Phase Status 改为 `completed`，并将 Exit Criteria 全部勾选
- 每次改动后运行 `./mvnw test -pl {MODULE}/<submodule> -am`

#### C-5：Plan 执行完毕后强制检查脚本

plan 的所有工作项执行完毕后，**必须**运行检查脚本：

```bash
node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict
```

此命令必须退出码为 0（所有 checklist 已勾选、Closure Evidence 已写入）。如果退出码非 0：
- 根据输出修复所有未勾选项或缺失的 Closure Evidence
- **重新运行检查脚本**，直到退出码为 0
- **在检查脚本通过之前，不能进入 closure audit，也不能将 Plan Status 改为 completed**

#### C-6：独立子 agent 迭代审查 closure 条件（强制，反复迭代直到通过）

检查脚本通过后，**必须**用独立子 agent 反复审查关闭条件：

1. 派发独立子 agent（使用 `ai-dev/skills/plan-closure-audit-prompt.md` 作为提示词，不同 task_id）执行 closure audit
2. Closure audit **必须**检查：
   - plan 文件中所有 `- [ ]` 是否已改为 `- [x]`（`grep -c '\- \[ \]' <plan-file>` 必须返回 0）
   - 每个 Phase 的 Exit Criteria 是否全部 `[x]`
   - Closure Gates 是否全部 `[x]`
   - 实际代码是否与 plan 描述一致（抽查关键代码路径）
   - deferred / follow-up 项中没有 in-scope live defect
3. **如果 closure audit 发现 blocking findings**：
   - 根据审计意见修复问题
   - 修复后**重新运行检查脚本** `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`
   - 检查脚本通过后，**重新派发独立子 agent 审查 closure 条件**（新的 task_id）
   - 重复此过程直到 closure audit **无 blocking findings**
4. **如果 closure audit 通过**（无 blocking findings）：
   - **必须**在 plan 的 `Closure` 段落写入：
     - `Reviewer / Agent`: 独立子 agent 的 session ID 或 task ID
     - `Evidence`: 审计发现摘要，包括每条 exit criterion 的验证结果
   - 更新每日日志 `ai-dev/logs/{year}/{month}-{day}.md`

#### C-7：每轮执行完毕后提交代码

每个 plan 通过 closure audit 后，**必须**按以下顺序操作：

1. **提交前最终确认**：再次运行 `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`，确认退出码为 0
2. **使用 `nop-git-master` skill 执行 git 提交**：
   - 提交范围：本轮 plan 涉及的所有代码、测试和文档变更
   - 提交信息格式：`<plan编号>: <plan标题> — <一句话摘要>`
   - 如果本轮有多个 plan，每个 plan 完成后分别提交
   - 不要跨 plan 积攒未提交的变更

**全部 plan 执行完毕后，回到阶段 A 重新审计。**

---

### 阶段 D：目标达成

当以下所有条件满足时，调用 `update_goal(status="complete")`：

1. 构建通过：`./mvnw clean install -pl {MODULE} -am -T 1C`
2. 全量测试通过：`./mvnw test -pl {MODULE} -am -T 1C`
3. 多维度深度审计收敛（连续一轮无新 P0/P1 问题）
4. 开放式对抗性审查收敛（连续一轮无新发现）
5. 所有 plan 已 completed（**每个 plan 的所有 checklist 已勾选**）或显式 deferred
6. 设计文档中的所有设计目标都有对应的已验证实现
7. `ai-dev/audits/` 中有完整的审计记录作为收敛证据

---

## 硬性约束

1. 禁止 java.sql.Connection — 用 IJdbcTemplate + IDialect
2. 禁止空方法体/continue跳过/吞异常 — 未实现功能抛 UnsupportedOperationException
3. 禁止修改 _gen 目录
4. 每个新增功能必须有测试，端到端覆盖从入口到出口
5. 每次改动后 `./mvnw test -pl {MODULE}/<submodule> -am` 必须通过
6. 每个 plan 完成前必须做独立 closure audit，**必须在 plan 文件中记录 evidence**
7. 遵循 `AGENTS.md` 中的 Code Conventions 和 Verification Checklist
8. **禁止在 plan 文件存在未勾选 checklist 的情况下将 Plan Status 标记为 completed**
9. **阶段 A 和阶段 B 必须分开执行，各自使用独立子 agent，各自产出 `ai-dev/audits/` 下的独立审计文档**
10. **plan 审核和 closure audit 都是迭代过程，不是一轮就结束**——只要有 blocking findings 就必须修复后重新审核

---

## 审计技能文件索引

| 文件 | 用途 | 何时使用 |
|------|------|---------|
| `ai-dev/skills/deep-audit-prompts.md` | 多维度深度审计（20 个维度） | 阶段 A 第 3 步（独立子 agent，结果写入 `ai-dev/audits/`） |
| `ai-dev/skills/open-ended-adversarial-review-prompt.md` | 开放式对抗性审查 | 阶段 B（独立子 agent，结果写入 `ai-dev/audits/`） |
| `ai-dev/skills/plan-reviewer-prompt.md` | Plan 创建后独立审核 | 阶段 C-3（执行前强制，**迭代审核直到无 blocking findings**） |
| `ai-dev/skills/plan-closure-audit-prompt.md` | Plan closure 独立审计 | 阶段 C-6（检查脚本通过后，**迭代审查直到无 blocking findings**） |
| 模块专属审计提示词（如有） | 模块特有维度的补充审计 | 阶段 A 第 3 步 |

---

## 流程总览

```
┌─────────────────────────────────────────────────────────────┐
│                    外层循环（直到目标达成）                      │
│                                                             │
│  ┌─── 阶段 A：多维度深度审计 ──────────────────────────────┐   │
│  │  1. 基础健康检查（构建 + 测试）                          │   │
│  │  2. 设计文档对照                                        │   │
│  │  3. 独立子 agent → deep-audit → ai-dev/audits/          │   │
│  │  有 P0/P1 → 跳到阶段 C                                  │   │
│  │  无 P0/P1 → 进入阶段 B                                  │   │
│  └───────────────────────────────────────────────────────┘   │
│                          ↓                                   │
│  ┌─── 阶段 B：开放式对抗性审查 ────────────────────────────┐   │
│  │  独立子 agent → adversarial-review → ai-dev/audits/      │   │
│  │  有问题 → 进入阶段 C                                    │   │
│  │  无问题 → 进入阶段 D（检查目标达成）                      │   │
│  └───────────────────────────────────────────────────────┘   │
│                          ↓                                   │
│  ┌─── 阶段 C：规划与执行 ─────────────────────────────────┐   │
│  │  C-1: 问题分组                                         │   │
│  │  C-2: 拟定 plan                                        │   │
│  │  C-3: 独立子 agent 迭代审核 plan（反复直到无 blocking）   │   │
│  │  C-4: 执行 plan                                        │   │
│  │  C-5: 运行检查脚本（反复直到退出码 0）                    │   │
│  │  C-6: 独立子 agent 迭代 closure audit（反复直到无 blocking）│  │
│  │  C-7: 提交代码                                         │   │
│  │  全部 plan 完成 → 回到阶段 A                            │   │
│  └───────────────────────────────────────────────────────┘   │
│                          ↓                                   │
│  ┌─── 阶段 D：目标达成判定 ────────────────────────────────┐   │
│  │  全部条件满足 → update_goal(status="complete")           │   │
│  └───────────────────────────────────────────────────────────┘
└─────────────────────────────────────────────────────────────┘
```
