---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND3-TESTS
group: "2026-09-15-0534"
verify: [test]
---

# P2 round-3 测试反模式与弱断言硬化（8 项：TestAiFileTool / ShellCommandExecutorTest / TestThoughtStorage / TestAuditEvent / 残余 P-1 镜像断言 / TestPipelineCompactor）

## Current Baseline

- 来源：deep-audit round 3 登记 P2/P3 Follow-up Backlog 中 8 项未勾选的测试反模式/弱断言项（roadmap `## Follow-up Backlog` 文档顺序），全部经 live repo 复核（HEAD `51544255b8`，2026-09-15）：
  1. **TestAiFileTool 沙箱包含性缺"同前缀兄弟目录 + 符号链接"负例**（`nop-ai/nop-ai-mcp-server/src/test/java/io/nop/ai/mcp/server/TestAiFileTool.java`）：现有 15 例逃逸形态单一（绝对路径/`../`，:90-153），`AiFileTool.isInsideBaseDir`（`AiFileTool.java:200-204`）用 `startsWith(basePath + File.separator)`；若退化为 `startsWith(basePath)`（同前缀兄弟目录穿透）或引入 symlink 逃逸，现有全绿回归不报警。
  2. **TestAiFileTool merge 正例无法区分 merge 分支与普通覆写分支**（TestAiFileTool.java:68-77）：`testSaveNopFileMergeSupportedWritesFile` 传入内容与预置文件完全相同（`"{\"a\":1}"`），`assertTrue(exists && length>0)` 几乎恒真；merge 静默退化为覆写（plan 350 曾修复的条件反转）此处不报警。
  3. **ShellCommandExecutorTest.testInputRedirectFromFile 只断言退出码不验证重定向内容**（`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java:208-221`）：`echo < test_input.txt` 若输入重定向实现损坏仍返回 0，测试全绿。
  4. **TestThoughtStorage 两处路径解析断言近似同义反复**（`nop-ai/nop-ai-tools/src/test/java/io/nop/ai/tools/sequential_thinking/service/TestThoughtStorage.java:72-95`）：`new File(workDir, "_tmp/...")` 的 `startsWith` 恒真、`new ThoughtStorage(null)` 的 `assertNotNull` 恒真、`~/.mcp_sequential_thinking` 的 `startsWith` 恒真——未锚定 storageDir 实际解析值。
  5. **TestAuditEvent 全类为 P-1 构造器/Getter 镜像测试**（`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestAuditEvent.java:12-107`）：8 个测试方法 7 个是 set/get 往返（`testConstructionWithAllFields`/`testDenyEvent`/`testWithPathVariable`/`testEquality`/`testInequality`/`testToString` 等），`testImmutability` 与 `testConstructionWithAllFields` 重复——无法捕获任何真实业务 bug。
  6. **清理后仍残余 P-1 枚举/Bean 镜像断言约 20 处**（2026-09-14 清理只覆盖 9 处）：`TestChannelKind.valuesMatchDesignSpec`（valueOf 往返）、`TestSkillModel` 字段往返、`TestPathAccessDecision.enumHasAllowAndDenyValues`、`TestTeamSpec` getter 镜像、`TestContributionAndPayload` 计数、`TestUsageRecord.nullableFieldsDefaultToNull` 残留、`TestPermission` equals/hashCode/toString 镜像（多文件，均已定位：`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/` 下 security/skill/team/contribution/usage 包）。
  7. **TestPipelineCompactor 缺策略异常/空结果/不缓解结果与 isRelieved `<=` 边界测试**（`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/compact/TestPipelineCompactor.java` + `PipelineCompactor.java:94-118,144-147`）：`catch → continue` 降级契约（策略抛异常 agent 继续）无负例守护；`layerResult == null` 跳过、不缓解结果（tokensAfter ≥ currentTokens）继续升级、`isRelieved` 的 `<=`（恰好等于阈值）边界均无测试（现有 `emptyStrategiesReturnsExplicitNoOpResult` 与 `emptyMessageListHandledExplicitly` 只覆盖空列表输入）。
  8. **ShellCommandExecutorTest.testGroupExprEnvironmentRestore 空壳**（ShellCommandExecutorTest.java:378-390）：命令 `echo inside_group` 不含 group 表达式、未比较执行前后 `getExportedEnv`——任何 group 解析/还原逻辑损坏都不影响它。
- 归属模块：nop-ai-mcp-server（1/2）、nop-ai-shell（3/8）、nop-ai-tools（4）、nop-ai-agent（5/6/7）。
- 验证面：全部为测试代码强化（新增/改写断言），零生产代码变更；涉及模块 `./mvnw test -pl <module> -am` 复核。

## Goals

- 消除"逃逸形态单一 + 包含性退化不报警"盲区：TestAiFileTool 补同前缀兄弟目录 + 符号链接负例，merge 正例改为可区分 merge 分支与覆写分支的内容/断言。
- 弱断言升级为内容断言：ShellCommandExecutorTest 输入重定向断言重定向内容、group 表达式测试断言环境还原语义。
- TestThoughtStorage 断言锚定真实解析值；TestAuditEvent 残余镜像断言替换为真实行为断言（或按裁定处置）；约 20 处残余 P-1 镜像断言逐处处置（删除/替换，保留有效断言）。
- TestPipelineCompactor 补齐策略异常/空结果/不缓解/`<=` 边界四类负例。
- 涉及模块 `./mvnw test -pl <module> -am` 通过；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不修改任何生产代码（`src/main` 零改动）；若某测试暴露真实缺陷，仅登记并在测试中显式标注（缺陷修复另开计划），不得为让测试通过而改生产代码。
- 不处置本轮 roadmap 其余未勾选项（round-3 P2 docs 漂移、round-4 P2/P3 项，另开计划）。
- 不重写未列出的既有通过测试（保持既有覆盖，只强化列出的弱断言）。
- 不运行 mvn 全量构建。

## Phase 1 — TestAiFileTool 沙箱包含性负例 + merge 分支可区分正例（nop-ai-mcp-server）

Status: planned

Targets: `nop-ai/nop-ai-mcp-server/src/test/java/io/nop/ai/mcp/server/TestAiFileTool.java`

- Item Types: `Fix | Proof`

- [ ] `Fix` 补同前缀兄弟目录负例：baseDir 下建 `sibling-dir`（与 baseDir 同前缀，如 baseDir 为 target/test-ai-file-tool 时建同前缀兄弟目录 target/test-ai-file-tool-evil），断言经 MCP 工具入口写入/读取兄弟目录路径被 `ERR_MCP_PATH_ESCAPE` 拒绝（防 `startsWith(basePath)` 退化）。
- [ ] `Fix` 补符号链接负例：baseDir 内建指向 baseDir 外目录的 symlink（`Files.createSymbolicLink`），断言经 `getResource`/`saveNopFile` 访问 symlink 目标被拒绝或按 canonical 校验 fail-closed（`ensureWithinBaseDir` 已 canonical 化——测试应断言 canonical 后越界被拒，且 symlink 存在时不静默放行）。
- [ ] `Fix` merge 正例改写：`testSaveNopFileMergeSupportedWritesFile` 预置文件与传入内容改为**不同**的合法 dict.xml 片段，断言结果同时覆盖 merge 分支（目标文件存在 + 内容为 merge 后形态，非原样覆写）与普通覆写分支（无 merge 语义的覆写路径）——可区分两分支；`testSaveNopFileMergeToNewFileWritesFile` 保持新建分支。
- [ ] `Proof` 复核：新增/改写测试在 `./mvnw test -pl nop-ai/nop-ai-mcp-server -am` 全绿；断言 merge 分支确实走 merge 逻辑（如内容含合并标记），非 `exists && length>0` 恒真式。

Exit Criteria:

- [ ] 同前缀兄弟目录 + 符号链接两类负例存在且断言 fail-closed（`ERR_MCP_PATH_ESCAPE` 或 canonical 拒绝）。
- [ ] merge 正例内容与预置不同，断言能区分 merge 分支与普通覆写分支。
- [ ] **端到端验证**：负例/正例均经 MCP 工具公开入口（`loadNopFile`/`saveNopFile`）触发，非直调私有方法。
- [ ] **接线验证**：负例断言命中 `ensureWithinBaseDir`/`ensureNewFileWithinBaseDir` 运行时路径（经 `getResource`/`saveNopFile` 调用链）。
- [ ] **无静默跳过**：无新空壳断言；被拒路径断言错误码而非仅"不抛异常"。
- [ ] No owner-doc update required：测试强化不改变契约面。
- [ ] `./mvnw test -pl nop-ai/nop-ai-mcp-server -am` 通过。
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — ShellCommandExecutorTest 两处弱断言硬化（nop-ai-shell）

Status: planned

Targets: `nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java`

- Item Types: `Fix | Proof`

- [ ] `Fix` `testInputRedirectFromFile`：断言 stdout 内容含 `"hello from file"`（输入重定向实现损坏时失败），并保留退出码断言。
- [ ] `Fix` `testGroupExprEnvironmentRestore`：命令改为真实 group 表达式（如 `{ export GROUP_VAR=1; echo in_group; }`），断言执行前 `getExportedEnv` 无 `GROUP_VAR`、执行后仍无（环境还原语义），必要时补 group 内导出在 group 外不可见断言；若 group 表达式语法解析需按模块语法（`BashSyntaxParser`），以模块内既有 group 测试形态为准。
- [ ] `Proof` 复核：改写后两测试能捕获对应缺陷（如用损坏实现验证测试会红——纯逻辑复核或注释说明），模块测试全绿。

Exit Criteria:

- [ ] 输入重定向测试断言重定向内容（非仅退出码）。
- [ ] group 表达式测试断言环境还原（执行前后 `getExportedEnv` 对比），不再是无 group 表达式的空壳。
- [ ] **端到端验证**：经 `executor.execute(...)` 入口到结果断言完整路径。
- [ ] **接线验证**（不适用）：无新组件协作。
- [ ] **无静默跳过**：无空壳断言残留（原空壳测试被真实断言替换）。
- [ ] No owner-doc update required：测试强化不改变契约面。
- [ ] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过。
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 3 — TestThoughtStorage 断言锚定 + TestAuditEvent 镜像处置（nop-ai-tools / nop-ai-agent）

Status: planned

Targets: `nop-ai/nop-ai-tools/src/test/java/io/nop/ai/tools/sequential_thinking/service/TestThoughtStorage.java`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestAuditEvent.java`

- Item Types: `Decision | Fix | Proof`

- [ ] `Fix` TestThoughtStorage：`testDefaultPathResolution` 改为断言 `new ThoughtStorage(relativePath)` 解析出的实际目录（经公开 API 如 `getStorageDir`/实际落盘文件路径）与文档化语义一致（`./` 相对 JVM 工作目录解析），删除 `startsWith` 恒真断言；`testEmptyPathFallsBackToUserHome` 断言 `new ThoughtStorage(null)` 实际使用 `~/.mcp_sequential_thinking`（写文件后断言路径在 home 下），删除裸 `assertNotNull`。
- [ ] `Decision` TestAuditEvent 处置裁定：记录 (A) 删除整个镜像测试类（8 方法全为 P-1 镜像，无业务断言）或 (B) 保留 1-2 个有效断言（如 `testEquality` 保留）并删除其余镜像。理由与备选记录于计划（推荐按 2026-09-14 清理先例：无行为价值的镜像测试删除，保留真实语义断言）。
- [ ] `Fix` 按裁定落地 TestAuditEvent：删除/合并镜像方法；若保留，则保留的断言必须可捕获真实业务 bug（如 toString 含关键字段），逐条注明保留理由。
- [ ] `Proof` 复核：改动后两类测试全绿；TestAuditEvent 无空壳镜像残留（每保留方法有保留理由注记）。

Exit Criteria:

- [ ] TestThoughtStorage 路径断言锚定真实解析值（storageDir/落盘路径），无恒真断言。
- [ ] TestAuditEvent 处置完成（按裁定），无 P-1 镜像断言残留或残留均有理由。
- [ ] **端到端验证**（不适用）：测试改写本身无运行时路径；以断言真实值取代恒真式为判定。
- [ ] **接线验证**（不适用）：无新组件协作。
- [ ] **无静默跳过**：删除镜像测试不隐藏真实行为（保留类有效性断言经 `./mvnw test` 证明）。
- [ ] No owner-doc update required：测试强化不改变契约面。
- [ ] `./mvnw test -pl nop-ai/nop-ai-tools,nop-ai/nop-ai-agent -am` 通过。
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 4 — 残余 P-1 镜像断言批量处置 + TestPipelineCompactor 降级契约边界（nop-ai-agent）

Status: planned

Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/` 下 security/skill/team/contribution/usage 包测试、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/compact/TestPipelineCompactor.java`

- Item Types: `Decision | Fix | Proof`

- [ ] `Fix` 残余约 20 处 P-1 镜像断言逐处处置（以 live grep 口径为准——`TestChannelKind.valuesMatchDesignSpec`、`TestSkillModel` 字段往返、`TestPathAccessDecision.enumHasAllowAndDenyValues`、`TestTeamSpec` getter 镜像、`TestContributionAndPayload` 计数、`TestUsageRecord.nullableFieldsDefaultToNull`、`TestPermission` equals/hashCode/toString 等）：删除编译期/常量断言，或替换为真实行为断言；每类保留至少一条有效断言，无空壳测试类；处置理由记录于 Verification 段。
- [ ] `Fix` TestPipelineCompactor 补四类负例：(a) 策略抛异常 → `PipelineCompactor.compact` 不失败、跳过该层继续（catch→continue 契约）；(b) 策略返回 null → 跳过；(c) 策略返回不缓解结果（tokensAfter ≥ currentTokens）→ 继续升级/最终不缓解结果（`currentTokens < tokensBefore` 分支不触发）；(d) `isRelieved` 边界——`currentTokens == tokenThreshold && messageCount == messageThreshold` 时返回 true（`<=` 语义），任一门限超一即 false。
- [ ] `Proof` 复核：`grep -rn "valuesMatchDesignSpec\|assertEquals(a, b)\|assertNotNull(event)"` 残余镜像断言零遗留（按 live 口径）；TestPipelineCompactor 新增负例断言具体语义（跳过层数/结果形态），非仅"不抛异常"。

Exit Criteria:

- [ ] 残余 P-1 镜像断言逐处处置完毕（删除或替换为行为断言），无空壳测试类残留。
- [ ] TestPipelineCompactor 四类负例（异常/空结果/不缓解/`<=` 边界）存在且断言语义正确。
- [ ] **端到端验证**：PipelineCompactor 负例经 `compact()` 入口断言降级行为（策略异常被吞但 agent 继续），非直调私有方法。
- [ ] **接线验证**：`isRelieved` 边界测试直调静态方法可接受（纯函数）；策略异常负例经 `compact()` 循环触发 catch 分支。
- [ ] **无静默跳过**：新增断言验证"跳过层"语义（如 invoked 计数），不允许仅断言不抛异常。
- [ ] No owner-doc update required：测试强化不改变契约面。
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-0534-2-p2-round3-test-hardening-1-2a8cedff to opencode-pid-51408
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-0534-2-p2-round3-test-hardening-1-2a8cedff

## Verification

（空，由 BUILD_VERIFY 填写）

## Closure

（空，由 CLOSURE_AUDIT 填写）