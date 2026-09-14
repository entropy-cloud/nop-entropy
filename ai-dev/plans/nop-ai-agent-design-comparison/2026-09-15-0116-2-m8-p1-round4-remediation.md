---
status: active
mission: nop-ai-agent-design-comparison
work-item: M8-P1
group: "2026-09-15-0116"
verify: [test]
---

# M8-P1 DockerBashSandbox 退出码误判与错误信息丢失修复 + bash sandbox 接线

## Current Baseline

- 来源：deep-audit round 4 登记 1×P1（roadmap `## Work Item Status` 的 M8 块，未勾选）+ 同域 Follow-up Backlog [P2] `ai-tools:bash` 无 sandbox 接线（roadmap `## Follow-up Backlog` 末段，未勾选）。以下全部经 live repo 复核（HEAD `a993ea9f43`，2026-09-15）：
  1. **`DockerBashSandbox.classifyFailure` 把一切非零命令退出码误判为 `CONTAINER_START_FAILED`**（`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:174-198`）：`docker run` 原样透传容器内命令退出码（`dockerCmd` 追加 `request.getCommand()`，无包裹 `sh -c`），普通命令失败（`ls /nonexistent` exit 2、`grep` 无命中 exit 1、`cd` 失败 exit 1 等）不匹配 BACKEND_UNAVAILABLE/CONTAINER_START_FAILED 的字符串特征、也不是 137/124，落入末尾 `return CONTAINER_START_FAILED`——全部被归类为"容器启动失败"。
  2. **`BashExecutor.doExecute` 丢弃唯一输出副本**（`BashExecutor.java:123-127`）：`DockerBashSandbox.execute` 对 reason != null 抛 `BashSandboxException(reason, message)`（message 含 exitCode 与截断输出），`BashExecutor` catch 后返回 `"Sandbox refused execution [" + e.getReason() + "]"`——真实退出码与 stdout/stderr 全部丢失，agent 无法区分命令失败与基础设施失败，bash 主工具错误契约失效（`bash.tool.xml` description 承诺"失败时（exitCode ≠ 0）将 stderr 放入 error，status 设为 failure，并提供具体退出码"）。
  3. **`BashSandboxTest:203-204` 把该误判固化为断言**：`dockerBackendClassifiesFailuresFailClosed` 断言 `classifyFailure(42, "something else")` 必须返回 `CONTAINER_START_FAILED`（"an unclassified non-zero exit must be conservatively fail-closed, never null"）——修复该缺陷必须同步改此断言。
  4. **`ai-tools:bash` bean 无任何 sandbox 接线**（`nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/beans/ai-toolkit-defaults.beans.xml:18`）：`<bean id="ai-tools:bash" class="io.nop.ai.toolkit.tools.BashExecutor"/>` 无 sandbox 构造器参数/property；`BashExecutor`（:62）`sandbox == null` 时恒 fail-closed（"BashExecutor has no IBashSandbox backend wired (fail-closed): the bash tool refuses to ..."），全仓无 HostBashSandbox/DockerBashSandbox 生产接线示例——主工具开箱即不可用且无 opt-in 指引。
- 归属模块：nop-ai-toolkit（1/2/3/4）。
- 现有测试基建：`BashSandboxTest`（`nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/sandbox/BashSandboxTest.java`，docker 不可用 CI 时以 wiring 测试为回退）、`BashExecutor` 相关工具测试。

## Goals

- `classifyFailure` 只把基础设施/容器启动/资源/超时类失败判为 sandbox 失败；普通非零命令退出码按命令失败处理，`BashExecutor` 结果携带真实退出码与输出（符合 `bash.tool.xml` 错误契约）；`BashSandboxTest:203-204` 断言与修正后分类一致，并补回归测试。
- bash 工具 sandbox 接线提供 opt-in 装配途径与文档指引：默认 fail-closed 安全姿态不变，接线后工具可用且有接线验证。

## Non-Goals

- 不处理 nop-ai-shell 模块的语义缺陷簇（cd/group/background，另开计划）、jline-reader 依赖清理、shell 弱断言测试强化。
- 不改 `DockerBashSandbox` 的 docker 命令构建/沙箱隔离语义（容器名、网络、资源限制、工作目录挂载等行为不变）。
- 不引入新的沙箱实现（HostBashSandbox/DockerBashSandbox 之外不新增后端）。
- 不运行 mvn 全量构建。

## Phase 1 — classifyFailure 分类修正 + BashExecutor 保留真实退出码与输出

Status: planned

Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java`、`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java`、`BashSandboxTest.java`、`BashExecutorTest.java`

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 命令失败表达方式裁定：(A) `classifyFailure` 对不匹配基础设施/容器启动/137/124 特征的非零退出码返回 null → `execute()` 返回 `BashSandboxResult(exitCode, stdout, "", false)` → `BashExecutor.toResult` 走既有 exitCode != 0 → failure + error body 路径（保留真实退出码与输出，复用现有契约）；或 (B) 新增 `COMMAND_FAILED` 枚举值并在 `execute()` 对该 reason 返回结果而非抛异常。记录理由与备选；推荐 (A)——枚举保持"沙箱/基础设施失败"语义，命令失败经既有 result 路径自然表达，无新增枚举扩散。
- [x] `Fix` 按裁定落地 `classifyFailure`：普通非零退出码（无 BACKEND_UNAVAILABLE/CONTAINER_START_FAILED 特征、非 137/124）不再返回 `CONTAINER_START_FAILED`；基础设施/容器启动特征判断保持 fail-closed。
- [x] `Fix` `BashExecutor` 错误面：命令失败经 `toResult` 携带真实 `exitCode` 与输出（error body）；`BashSandboxException` catch 仅用于真正的 sandbox/基础设施失败（保持 `"Sandbox refused execution [...]"` 语义）；复核 `toResult` 对 exitCode != 0 的空输出兜底文本仍成立。
- [x] `Fix` 回归测试：修正 `BashSandboxTest:203-204` 固化断言；新增分类回归（`ls /nonexistent` 等价 exit 2 非 137/124 → 命令失败语义；`Cannot connect to the Docker daemon` exit 1 → BACKEND_UNAVAILABLE 保持；137 → RESOURCE_LIMIT_EXCEEDED、124 → TIMEOUT 保持）；新增 `BashExecutor` 级测试断言命令失败结果含真实 exitCode 与输出（不依赖 Docker 的 wiring 级构造，复用既有测试基建形态）。
- [x] `Proof` 复核 `classifyFailure` 全部调用点与 `BashSandboxResult` 消费链：`execute()` 唯一 throw/return 路径与 `BashExecutor.toResult` 分支一致；`bash.tool.xml` 错误契约描述与修复后行为一致。

Exit Criteria:

- [x] 普通非零命令退出码不再被归类为 `CONTAINER_START_FAILED`（分类回归测试为证）；基础设施/容器启动失败仍 fail-closed。
- [x] `BashExecutor` 命令失败结果含真实退出码与输出（回归测试断言 exitCode 与 error body 内容，非仅"没报错"）。
- [x] `BashSandboxTest:203-204` 断言与修正后分类一致（不再固化误判）。
- [x] **端到端验证**：从 `BashExecutor` 入口经 `DockerBashSandbox.execute` → `classifyFailure` → `toResult` 到错误结果输出完整路径覆盖——真实 docker 条件测试（`assumeTrue(isDockerAvailable())` 形态，命令 `sh -c 'exit 2'` 或 `ls /nonexistent` 类非零退出）经 `execute()` 走完整链并断言 result 含真实 exitCode 与输出；CI 无 docker 时以「`BashExecutor` + 返回非零 `BashSandboxResult` 的 stub sandbox 验证 `toResult` 保真」+「`classifyFailure` 修正分支单测」组合为判定依据（复用既有测试基建形态）。
- [x] **接线验证**：`classifyFailure` 修正分支在 `execute()` 运行时被消费——docker 条件测试经 `execute()` 触发非零退出码走 result 路径（非直调静态方法）；CI 无 docker 时以「调用点 Proof：`execute()` 唯一消费 `classifyFailure` 返回值（DockerBashSandbox.java:123-129）」+ 修正分支单测为判定依据。
- [x] **无静默跳过**：命令失败不吞 exitCode/输出；无新的空方法体或 catch 吞错。
- [x] owner doc 更新或 `No owner-doc update required`：若 `bash.tool.xml` description 或 `nop-ai-toolkit` 相关设计文档需要与修复后行为对齐则同步；否则显式写明。
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — ai-tools:bash sandbox opt-in 接线与文档化

Status: planned

Targets: `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/beans/ai-toolkit-defaults.beans.xml`、`BashExecutor` 接线测试、`docs-for-ai/03-modules/nop-ai.md`（`## 工具配置（nop-ai-tools）` 段，首选）或 `ai-dev/design/nop-ai-agent/` 相关设计文档（如接线说明更属内部设计，则登记于 design 并注明）

- Item Types: `Fix | Decision | Proof`

- [x] `Decision` 接线形态裁定：(A) 在 `ai-toolkit-defaults.beans.xml` 提供注释掉的 HostBashSandbox/DockerBashSandbox 装配示例（保持默认 fail-closed）+ 文档化 opt-in 指引；或 (B) 提供可选装配 beans 文件/条件注册（如 `ioc:condition` 按配置启用）。记录理由与备选；默认 fail-closed 安全姿态是硬约束，任何接线不得改变"未显式 opt-in 时拒绝调用"的语义。
- [x] `Fix` 按裁定落地接线示例/装配：bash bean 在显式注入 sandbox 后可用；未接线时 fail-closed 行为与错误消息保持。
- [x] `Fix` 接线验证测试：`setSandbox`/构造器注入 HostBashSandbox/DockerBashSandbox 桩后调用成功路径可用；未接线时 fail-closed（拒绝 + 明确错误消息）——两条路径各一断言。
- [x] `Fix` 文档化：在 owner doc（`docs-for-ai/` 下 nop-ai-toolkit/bash 相关文档或 `ai-dev/design/nop-ai-agent/`）登记 bash 工具 sandbox 的 opt-in 装配指引与默认 fail-closed 说明，消除"开箱不可用且无指引"状态。
- [x] `Proof` 复核：全仓生产装配面无"默认装配即静默启用无沙箱 bash"路径；opt-in 装配示例与实际 bean id/类名一致。

Exit Criteria:

- [x] bash 工具提供显式 opt-in 接线途径（示例/装配）且有测试为证；默认 fail-closed 语义不变。
- [x] **端到端验证**：从 bean 装配（注入 sandbox）→ `BashExecutor.execute` → 命令执行 → 结果返回完整路径覆盖（wiring 级测试）。
- [x] **接线验证**：sandbox 注入字段在 `execute` 运行时被消费（接线测试断言经 setter/构造器注入的 sandbox 被调用）。
- [x] **无静默跳过**：未接线时 fail-closed 明确报错，不静默返回成功或空结果。
- [x] owner doc 更新：bash sandbox opt-in 指引与默认 fail-closed 已登记。
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-0116-2-m8-p1-round4-remediation-1-7f3a9c2e to opencode-pid-93033
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-0116-2-m8-p1-round4-remediation-1-7f3a9c2e

## Verification

- pass test 2026-09-15-0442-closure-audit exit=0
- pass test 20260915062053 exit=0

## Closure

- dispatch audit #audit-2026-09-14-110620-2026-09-15-0116-2-m8-p1-round4-remediation-4-fb649bfc to closer-session-2026-09-15-0442 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-2026-09-15-0116-2-m8-p1-round4-remediation-4-fb649bfc：独立 closure audit 通过——Phase 1-2 全部落地并经 live repo 复核（classifyFailure 非基础设施非零退出码返 null 走 result 路径保真、execute() 唯一消费点复核、BashSandboxTest 固化断言改为 null + 137/124/BACKEND_UNAVAILABLE/CONTAINER_START_FAILED 保持、stub toResult 保真 + 真实 Host 全链 + docker 条件 e2e 三形态回归；beans.xml 注释 opt-in 装配示例 + TestBashToolDefaultWiring 生产文件守卫 + BashExecutorTest setter 接线运行时消费断言 + 未接线 fail-closed 逐字断言 + docs-for-ai/03-modules/nop-ai.md opt-in 指引），本 visit 独立验证 ./mvnw test -pl nop-ai/nop-ai-toolkit -am 全绿 227 测试 0 失败（BashExecutorTest 17/17、TestBashToolDefaultWiring 2/2、BashSandboxTest 12 中 2 docker 条件跳过按 CI 回退语义）、doc-links --strict 退出码 0（本 visit pass test）、plan-check 26/26 全勾选、anti-hollow 扫描 0 发现，无 in-scope defect 降级
- dispatch audit #audit-2026-09-14-110620-2026-09-15-0116-2-m8-p1-round4-remediation-5-ac9736f0 to closer-session-2026-09-15-0620 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-2026-09-15-0116-2-m8-p1-round4-remediation-5-ac9736f0：round-5 复核通过——CLOSURE_SCRIPT_CHECK FAIL（missing-pass:test）根因为 `## Verification` pass 行带前导空格被 ledger parser 按 prose 忽略（与 ai-dev/logs/2026/09-14.md 记录同类根因），本 visit 修复为列 0 `- pass test ...`；重跑 plan-check 26/26 勾选 exit=0；本 visit 实跑 `./mvnw test -pl nop-ai/nop-ai-toolkit -am` 全绿（227 tests 0 失败，BashExecutorTest 17/17、TestBashToolDefaultWiring 2/2、BashSandboxTest 12 中 2 docker 条件跳过按 CI 回退语义）+ `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors，11 warnings 全部为其他历史计划/兄弟计划存量）；语义复核 Phase 1-2 全过——classifyFailure 非基础设施非零退出码返 null（DockerBashSandbox.java:196-202）、BashExecutor.toResult 保真 exitCode 与输出、beans.xml 注释 opt-in 装配示例 + fail-closed 默认、docs-for-ai/03-modules/nop-ai.md:137-146 opt-in 指引在位、roadmap M8 已勾选；无 in-scope defect 被降级