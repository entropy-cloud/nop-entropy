# 审计-修复闭环核验提示（fix-status / zero-test / 凭证跨层）

在审计-修复闭环（MA → MR → MV → MG）中，对**修复声明的可追溯性**做核验时使用此提示。它把 nop-ai 闭环（MR1-MR4/MV）中反复出现且值得固化的三类检查项固化为可执行步骤：fix-status 与 live repo 的追溯核验、zero-test 模块扫描、凭证字段跨层暴露核查。

```text
阅读 `AGENTS.md`、修复追踪表（如 `ai-dev/audits/arm-index.md`）、相关审计报告、修复 plan 与 live 代码。

你的任务：核验修复声明（fixed/done 行）在 live repo 中是否可追溯。核验按以下三类检查项执行，每项都有明确的执行步骤与输出格式。

---

## 检查项 1：fix-status 与 live repo 追溯核验

背景教训：MR2 声称「MA4.3 P1 已展开进 arm-index」但 live 文件无对应行；MR1/MR3 声称的修复在 `git log` 中从未触及目标文件。**"声称已修复"不是证据，"能找到修复产物"才是。**

执行步骤：
1. 对追踪表中每个标 `fixed` 的 finding，定位其声明的修复路径（plan/commit/测试文件）。
2. 用 `git log --oneline -- <file>` 核验声明的 commit 是否实际触及目标文件；若声明的是"注释/文档已加"，检查该文件是否为生成物（`_` 前缀、xgen 产物）——生成物上的手改会在下次构建被覆盖。
3. 若声明的是代码修复，打开对应代码路径，确认修复逻辑存在且**在运行时调用链上被接线**（不是只存在未调用）。
4. 若声明的是测试，确认测试文件存在且为行为断言（assertThrows/assertEquals，非空断言/catch-and-pass）。

输出格式（每个 finding 一行）：
- `VERIFIED <finding-id> <证据: 文件:行号 / 测试方法名 / commit hash>`
- `OVERCLAIM <finding-id> <缺失的具体证据>`（声明无法在 live repo 定位任何产物）
- `PARTIAL <finding-id> <已存在部分> <缺失部分>`

任何 OVERCLAIM 必须立即标记，不得静默接受声明。

---

## 检查项 2：zero-test 模块扫描

背景教训：nop-ai 7 个模块 0 测试，历次构建全部通过——`mvn test` 的通过条件不是"覆盖达标"，零测试模块在 CI 中不可见。

执行步骤：
1. 枚举目标模块组的所有子模块（`ls <模块组>/`）。
2. 对每个子模块统计 `src/main/java` 文件数与 `src/test/java` 文件数（main 有文件而 test 为 0 = zero-test 模块）。
3. 对 zero-test 模块检查是否被上游模块测试间接覆盖（风险较低但需记录）；基础设施模块（api/dao/toolkit 类，被大量下游消费）的零测试按 P1 记录。
4. 抽查现有测试文件的断言质量：纯 assertTrue/assertFalse 或空 catch 的测试不算有效覆盖。

输出格式：
- `ZERO-TEST <module> main=<n> test=<0> <risk: P1/P2> <建议测试文件位置>`
- `OK <module> main=<n> test=<m>`

---

## 检查项 3：凭证字段跨层暴露核查

背景教训：`NopAiModel.apiKey` 在 ORM 源模型 → 生成 xmeta → Delta xmeta → GraphQL DTO 多层暴露，MR1/MR2/MR3 各自局部修复才在 MR4 闭合。收敛位置必须是 ORM 源模型，生成物上的限制会被 codegen 覆盖。

执行步骤：
1. 列出审计范围内所有含凭证语义的字段（apiKey/secret/password/token 等）。
2. 对每个凭证字段沿完整链核查：
   a. ORM 源模型（`model/*.orm.xml`）：列上是否有 `tagSet="enc,not-query,not-sort,not-pub"` 类限制 + `ui:show="X"`；
   b. 生成 xmeta（`_*.xmeta`，codegen 产物）：`queryable/sortable/published/internal` 是否与源模型一致（源模型是唯一事实源）；
   c. 运行时合并 xmeta（Delta）：`insertable/updatable` 是否限制；
   d. GraphQL DTO / OutputBean：序列化字段集合中是否无凭证字段。
3. **以运行时最终可见性为准**：GraphQL 响应/API DTO 实际输出的字段集合，而非某层模型的属性标注。有条件时用 schema introspection 或 DTO 序列化测试验证。

输出格式：
- `LEAK <field> <层级: orm/xmeta/dto> <暴露属性>`
- `CONVERGED <field> <五层证据: orm tagSet / 生成 xmeta / 合并 xmeta / DTO 无字段 / 测试文件>`
- `FRAGILE <field> <限制只在生成物/Delta，未下沉 ORM 源模型>`

---

## 汇总输出

最终输出必须包含：
1. 三类检查项的逐条结果（按上述格式）。
2. 发现的 OVERCLAIM / ZERO-TEST(P1) / LEAK 清单——这些都是 live defect 或 contract drift，必须进入修复通道，不得降级为 deferred。
3. 对每个 OVERCLAIM 建议的纠正动作（补修复 or 在追踪表记录裁定）。
4. 若全部核验通过，明确声明 `ALL VERIFIED` 并给出核验范围（模块组 + finding 数量 + 抽查比例）。
```

## 使用场景

- MR 修复计划执行后的 fix-status 核验
- MV 全量验证阶段的 P0/P1 可追溯性矩阵生成
- 计划 closure audit 的证据复现（独立子 agent 执行）
- 任何含"修复状态表"的审计-修复闭环

## 与本目录其他 prompt 的关系

- `closure-audit-prompt.md`：plan 级关闭审计；本 prompt 是其证据核验的补充（修复声明可追溯性）。
- `deep-audit-prompts.md` 维度 16（测试覆盖）/13（安全与权限）：本 prompt 的三类检查项是其"修复后核验"形态。
- `plan-closure-audit-prompt.md`：面向修复计划文本；本 prompt 面向追踪表行。
