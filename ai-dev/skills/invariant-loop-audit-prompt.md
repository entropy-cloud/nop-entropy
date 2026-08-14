# 不变式驱动的持续审计闭环（Invariant-Driven Continuous Audit Loop）

> **项目定制化层（nop-entropy）**：使用本提示前必须先读 `AGENTS.md`、`docs-for-ai/00-start-here/project-context.md`，将本仓库的验证命令（`./mvnw clean install -DskipTests -pl <模块> -am -T 1C` / `./mvnw test -pl <模块> -am -T 1C`）、命名约定（nop-* 模块前缀、`arm-` 审计报告前缀、PascalCase/camelCase/UPPER_SNAKE_CASE）、保护区域（ORM 模型源 `model/*.orm.xml`、API 模型 `*.api.xml`、禁止手编 `_gen/` 生成产物）、roadmap 先例（`audit-remediation-roadmap.md` / `nop-metadata-audit-remediation-roadmap.md` 线性、各模块 `*-invariant-loop-roadmap.md` 闭环）、共性插入机制（PD-n 先例链编号——PD = Precedent Derivation，借鉴 `arm-index` 已有的"沿先例"引证风格，本提示新引入的闭环派生编号，用于新失败族派生 Cycle N+1 时追踪先例链），编号基线（lessons 最高 14、bugs 手动编号）注入上下文。
>
> **授权（本项目）**：AI 默认按 `docs-for-ai/00-start-here/project-context.md` 声明的自主级别；保护区域（`_gen/` 生成产物、`_` 前缀文件）禁止手编；P0/P1 自动修复预授权（mission description 声明）；结构性重构（公共 API、模块边界、ORM/API 模型变更）执行前人工确认。
>
> **定位**：这是一份**过程元提示词**（method selector），不是对象级审计提示词。它编排「诊断 → 选型 → 拟 roadmap+mission → 共识审查 → 执行」的工作流，特别针对**反复复发的有状态子系统**设计持续闭环飞轮。
> **与既有 skill 的关系**：`audit-remediation-roadmap-authoring-prompt.md` 是**线性管道**（M0→MA→MR→MV→MG）的专用提示，其 MG 产出为 lessons 文档；本提示是其**闭环升级版**——MG 产出为**可执行 CI 门禁**（门禁即契约），适用于线性管道反复复发后的根治。两者不互斥：线性管道先行铺面，闭环飞轮后续治根。
> **先例**：nop-chaos-flux 的 roadmap-and-mission-authoring-with-consensus-review.md（docs/skills 下，可移植元提示词，位于独立 worktree 不可在本仓解析，本文件按其「如何移植到其他项目」节移植）；nop-chaos-flux 的 ai-invariant-loop-roadmap.md（docs/backlog 下，首个闭环先例，Cycle 1+2 完成后稳态暂停）。

## 用途

当一个**有状态子系统**（engine / 事务 / undo / 流处理 / 会话管理 / 索引维护）**已被多轮审计但同族缺陷反复复发**时，用它把"修-漏-再修"改造为自驱动飞轮。

典型触发信号：

- 同一个缺陷家族（如"并发守卫缺失"、"资源清理不对称"、"静默吞异常"）在**多轮审计中被反复在新路径上发现**
- 修了报到的方法，但**漏了同类兄弟方法**（"same family, missed method"）
- MG/lessons 沉淀了经验教训，但**未沉淀为可执行门禁**——重构或新增方法时同族缺陷再次成盲区

## 何时**不**用

- 首次审计或覆盖面不足 → 走**线性 roadmap**（`audit-remediation-roadmap-authoring-prompt.md`）
- 单模块窄改动 → 直接 plan + 对象级 skill（`deep-audit-prompts.md` 等）
- 需求仍模糊 → 先走 discussion / deep-interview
- 缺陷分散不收敛（非同族复发） → 常规审计足够，闭环飞轮无收益

---

## 步骤 0 — 诊断优先（禁止跳过）

**不要直接再开一轮审计。** 先诊断为什么反复——这是本流程区别于"再来一次"的关键：

### 0.1 核查历史复发证据

列出历次审计/修复的「已修复」清单，看下一轮是否在**同族兄弟路径**再次击穿。实证而非猜测，带 `文件:行` / audit-finding-ID / commit-hash 证据。

### 0.2 判定根因类别

| 根因 | 表现 | 对策 |
|---|---|---|
| **"修实例不修类别"** | 修了报到的实例，漏了同类兄弟方法 | 强制类别清扫（grep 全类） |
| **反应式测试非穷举** | per-bug 加测试，无"全方法 × 全不变式"穷举契约 | 参数化穷举测试 + 表完备性门禁 |
| **零可执行门禁** | 修复未沉淀为入 CI 的 check/gate | 门禁即契约（入 CI，非文档） |
| **MG 产出为文档非门禁** | lessons 写了"建议手工检查"但未自动化 | 升级为 CI 检查脚本 |

### 0.3 结论分两路

- **线性补查能解决**（缺覆盖面） → 走线性 roadmap（用 `audit-remediation-roadmap-authoring-prompt.md`）
- **反复复发/防回退失效**（缺不变式契约） → 走**持续闭环 roadmap**（本提示步骤 1b）

---

## 步骤 1 — 选型：持续闭环 roadmap

持续闭环 roadmap = 自驱动飞轮，门禁单调棘轮。每 Cycle 固定 7 步：

```
I0 盘点基线 → I1 沉淀不变式(→门禁入CI) → I2 按不变式审计(跑门禁+对抗探查)
  → I3 裁决→工作项 → I4 修复(强制类别清扫+测试) → I5 验证(全绿+门禁零命中)
  → I6 收口(新失败类?→下轮 I1; 否则稳态)
```

### 关键机制（缺一不可）

1. **不变式 = 可执行门禁入 CI**：每条不变式必须落地为 JUnit `@ParameterizedTest`（方法表驱动穷举）或 `ai-dev/tools/*.mjs` 静态扫描器或 ArchUnit 规则或 spotbugs/PMD 自定义规则。**不是文档**。
2. **门禁集合单调棘轮**：已沉淀的不变式只增不减；弱化/删除/豁免需人工确认 + 留痕 + committed 回归测试同步。
3. **表完备性门禁**：参数化测试的方法表 == 从公共接口/类型反查的全部变更型方法集。新增方法不入表即门禁红（防新方法静默成盲区）。
4. **类别清扫强制**：I4 修任一方法时必须 grep 全部同类兄弟一并修。只修报到实例 = closure 拒绝。
5. **Loop Rule 预授权派生**：I2/I3 发现新失败类 → I6 自动派生下轮 I1（附触发证据），不逐次人工重启。
6. **稳态暂停 + 复触发**：一轮零新族且 red list 零 → 标稳态暂停；复触发三选一：CI 门禁变红 / 结构变更（新增/重命名变更型方法 / 类）/ 周期复探（默认每 major release 或季度，取早）。

### nop-entropy 门禁技术栈

| 不变式类型 | 落地形式 | 示例 |
|---|---|---|
| 行为穷举（"每个变更型方法必须做 X"） | JUnit 5 `@ParameterizedTest` + `@MethodSource`（方法表驱动） | "每个 `Collections.synchronizedMap` 字段的迭代点必须在 `synchronized` 块内" |
| 架构约束（"A 不能依赖 B"，**需先引入 ArchUnit 依赖**） | ArchUnit `@ArchTest`（当前 pom.xml 未配置 ArchUnit，首次使用前需在对应模块 pom.xml 添加 `com.tngtech.archunit:archunit-junit5` 依赖） | "service 层不能直接访问 dao 层的非接口类" |
| 静态模式扫描（"不应出现 X 模式"） | `ai-dev/tools/*.mjs` Node 脚本 + ast-grep YAML 规则（`../tools/rules/` 已有 3 条 Java lint 规则；新规则按同格式追加） | "catch 块不能只有 `e.getMessage()` 而不 rethrow" |
| ORM/API 模型完整性 | `ai-dev/tools/check-*.mjs` 自定义检查 | "每个 `<unique-key>` 必须有 `constraint` 属性" |
| 聚合入 CI | `ai-dev/tools/` 下聚合脚本或 Maven enforcer plugin | 所有 invariant check 统一入口 |

---

## 步骤 2 — 拟 roadmap

按 `ai-dev/backlog/` 既有 roadmap 结构：Header / Purpose / Work Item Status（唯一动态区）/ Loop Design / Phase Details / Dependency Graph / Cross-Cutting / Loop Rule。

强制纪律：

- **work item 粒度 = 一个 plan 能完成**
- **基线节必须核对 live code**（不可凭记忆写"X 未修"——违背 = Blocker）。历史复发证据用过去时（"曾漏"、"历经 N 轮才捕获"），不得用现在时暗示仍开放。
- **无悬空引用**：声称"A 指向 B"必须双向核对。
- **计数/编号核对 live**：lessons 最高编号、bugs 最高编号、audit 文件数——全部 live `ls`/`grep` 实测。
- **不变式首条必须来自历史复发族**：I1 首批不变式必须覆盖 I0 盘点出的已知失败族，不可用猜测的新不变式。

---

## 步骤 3 — 拟 mission.json

每个模块的 invariant-loop roadmap 应有专属 mission（`missions/<module>-invariant-loop.json`）。字段对齐本仓先例：

```json
{
  "name": "<module>-invariant-loop",
  "description": "<模块>不变式驱动的持续审计闭环。范围：<模块>下的<子模块>。授权：P0/P1 自动修复预授权（同 audit-remediation 先例），结构性重构（公共 API/模块边界/ORM 模型）执行前人工确认。Loop Rule 预授权下轮 I1 自动派生（PD-n 先例链，附触发证据），不逐次人工重启。",
  "roadmapPath": "ai-dev/backlog/<module>-invariant-loop-roadmap.md",
  "plansDir": "ai-dev/plans",
  "planGuide": "ai-dev/plans/00-plan-authoring-and-execution-guide.md",
  "auditsDir": "ai-dev/audits",
  "contextDir": "docs-for-ai/00-start-here",
  "moduleDir": "<module>",
  "commands": {
    "test": "./mvnw test -pl <module> -am -T 1C",
    "build": "./mvnw clean install -DskipTests -pl <module> -am -T 1C",
    "lint": "./mvnw checkstyle:check -pl <module> -am -q 2>/dev/null || echo 'lint not configured'",
    "typecheck": "./mvnw compile -pl <module> -am -q 2>/dev/null || echo 'typecheck not configured'"
  },
  "prompts": {
    "multiAudit": "ai-dev/skills/deep-audit-prompts.md",
    "openAudit": "ai-dev/skills/open-ended-adversarial-review-prompt.md",
    "invariantLoop": "ai-dev/skills/invariant-loop-audit-prompt.md"
  },
  "commitFormat": "fix(<module>): <description>"
}
```

---

## 步骤 4 — 共识审查（常设纪律，不可豁免）

> **每次修改 roadmap 或 mission 都必须经独立子 agent 反复审查至共识。** 拟制者不得自审自批。

- **独立 fresh session**：每轮审查由独立子 agent（fresh session，review-only 禁改文件）执行。
- **审查输入**：三件套（roadmap + mission + 相关基准文件：先例 roadmap/mission、live code 抽查点、历史审计证据）。
- **审查维度**：① 合规（结构/粒度/状态机）；② 内部一致性（表↔Phase Details↔依赖图↔Rule↔mission，零悬空/零矛盾）；③ 事实基础（基线节核对 live code，计数/编号 live 实测）；④ mission 对齐；⑤ 门禁/Loop 纪律（closure 独立 session、棘轮、类别清扫可执行）；⑥ 遗漏与风险。
- **输出**：逐维度 PASS/FAIL/MINOR + 问题清单（**Blocker**必修 / **Major**建议 / **Minor**）+ verdict（`approved` = 零 Blocker，或 `revised`）。
- **迭代**：`revised` → executor 修正 → 新 fresh session 复审 → 直至 `approved`。

---

## 步骤 5 — 执行（按 closed loop）

- AI 读 Work Item Status → 取第一个 `todo` → 起草 plan。
- plan 经独立草案审查 → `todo`→`planned` → 执行 → **closure audit 由独立 fresh session** → `planned`→`done`。
- 持续闭环：收口步骤若发现新族 → 按 Loop Rule（预授权）追加下轮 work item（附触发证据）→ 该追加不触发步骤 4 审查（已预授权），但下轮 roadmap 主体修订仍需审查。

---

## 规则

1. **诊断优先**：领域反复出问题时，先诊断根因再选 roadmap 形态；禁止"直接再开一轮审计"。
2. **共识审查不可豁免**：roadmap/mission 的每次修订都走步骤 4；executor 不自审；每轮 fresh session。
3. **基线核对 live**：基线节的事实声明必须 live 实测，违背 = Blocker。
4. **无悬空引用**：交叉引用双向核对；范围独立就明说独立。
5. **门禁即契约**：不变式必须是可执行门禁（入 CI），不是文档；门禁集合单调棘轮。
6. **类别清扫强制**：修任一实例必 grep 全类兄弟；只修实例 = closure 拒绝。
7. **MG 升级**：闭环 roadmap 的收口（I6）产出必须是 CI 门禁（可执行），不是 lessons 文档。lessons 是副产品，门禁是主产出。

## 反模式（禁止）

- 不诊断直接再开审计轮（治标不治本，复发保证）
- executor 自审自批 roadmap/mission（违反共识纪律）
- 基线节凭记忆写"X 未修"（高频 Blocker）
- 伪交叉引用（A 声称指向 B 但 B 无对应行）
- 持续闭环的不变式只写文档不入 CI（门禁不防回退 = 飞轮不转）
- 修实例不修类别（正是要治的病）
- MG 产出为 lessons 文档而非 CI 门禁（线性管道与闭环的关键分水岭）
- 线性管道冒充闭环（无棘轮/无表完备性/无稳态判定 = 非闭环）

---

## nop-entropy 各模块的适用判定

| 模块 | 审计轮次 | 同族复发证据 | 裁定 | 理由 |
|---|---|---|---|---|
| **nop-stream** | 21 轮（5 deep + 16 adversarial r1–r16）+ 23 阶段独立审计 | WindowAggregationOperator 被击穿 7+ 次（R8/R13/R14/R15/R16）；TwoPhaseCommitSinkFunction 并发族 2 级联 | **P1 上闭环** | 活跃建设 + 最清晰同族复发 + R16 summary 自列 15 条未修兄弟 |
| **nop-metadata** | 5 轮 multi+open + ARM MA1–MA7 + MR1–MR8 | 先例链交叉引用（R6.2→R8.2 脱敏；AR-09→AR-23④ limit）；R3.14 AR-06 虚假关闭（diff 只有版权头） | **P1 上闭环** | 先例链已显式存在 + 36 unique-key 缺 constraint 系统性遗漏 |
| **nop-ai** | 6 deep + ARM MA1–MA7 + MR1–MR4/MV（50/50 done） | Lesson 05 记录 3 次虚假关闭；Default* 缺 secure-default 6 兄弟；ToolExecutor 安全族 | **P2 回归防护型** | mission 已关闭，防重构回退 |
| **nop-code** | 2 baseline + 13 轮（含同日 5 sub-round） | AR-168→AR-177 OOM 同族交叉引用；增量索引去同步族 | **P3 待激活** | 无活跃 mission，审计发现 AR-94→AR-178 大量悬空；待重新激活时直接上闭环 |
