# 2350 WI4 检查门禁接线与存量基线梯度

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI4 行，M1 门禁）
> Related: plan 2348（WI2 L1 规则）、plan 2349（WI3 L2 lint）

## Purpose

把两层检查器接入门禁：L1（nop-lint CLI）入 compliance.yml 作业 + 只减不增 baseline；L2（StreamModelLinter）以仓扫 JUnit 门禁测试接入既有 mvn test 通道。存量违规全量登记（负控夹具白名单化），建立 warn→error 两阶段的消号台账。

## Current Baseline（2026-10-09 live 核对）

- CI：`.github/workflows/compliance.yml` 已有 nop-lint CLI 作业先例（nop-bean-naming：编译 nop-lint 模块 + build-classpath + `NopLintCli check $FILES --rules ... --max-warnings 0`）；`.github/workflows/maven.yml` 跑 `mvn package`（含全部测试）+ `check-nop-stream-invariants.mjs`。
- nop-lint CLI 原生支持 baseline 三态（`--baseline` 抑制 / `--baseline-check` 只减不增 / `--write-baseline` 生成，design 09 §5）。
- L2：`StreamModelLinter.lint(resource, resolver, connect)`（plan 2349）；存量试扫已证 R5=1、R10=21 站点、R12=2、R7=R8=R9=0（38 份语料，WI2 Phase 3 构件级扫描）；flow test resources 含 by-design 负控夹具（conf-validate fail-fast 测试用，lint 会折叠 FAIL）。
- user-guide 矩阵段已登记 L1 存量基线数字（WI2 落地时）。

## Goals

- compliance.yml 新增 `xdsl-lint` 作业：L1 六规则扫全部 `.stream.xml`（target/_tmp 除外）+ 提交式 baseline（`ai-dev/tools/nop-lint-xdsl-baseline.txt`）+ `--baseline-check` 只减不增。
- L2 门禁测试 `TestXdslCorpusLintGate`（nop-stream-flow）：仓内 `.stream.xml` 全量经 StreamModelLinter，白名单表登记存量 WARNING/负控 FAIL，零新增违规。
- 消号台账：user-guide 矩阵段的存量基线段扩展为台账（L1 baseline 文件 + L2 白名单表指针 + 消号义务：WI5–WI16 放行时删对应 face 的规则分支并同步缩减 baseline/白名单）。

## Non-Goals

- 不升 error（规则 severity 维持 warning；baseline 只减不增即是两阶段的「梯度」机制——新违规即阻塞，存量消号后收紧）。
- 不改 lint/规则行为；不把 L2 接入 compliance.yml 的 CLI 作业（L2 需 Java 解析链，走 mvn test 通道即 CI 覆盖——maven.yml 的 mvn package 已跑全部模块测试）。

## Scope

### In Scope

- `.github/workflows/compliance.yml`：新增 xdsl-lint 作业。
- `ai-dev/tools/nop-lint-xdsl-baseline.txt`：L1 存量基线（--write-baseline 生成后提交）。
- `nop-stream-flow` 测试：`TestXdslCorpusLintGate`（白名单表 + 仓扫）。
- `docs-for-ai/03-modules/nop-stream-user-guide.md`：矩阵段台账扩展。

### Out Of Scope

- error 升级；规则变更；L2 CLI 化；其他 CI 工作流。

## Execution Plan

### Phase 1 - L1 CI 作业与 baseline

Status: completed
Targets: `.github/workflows/compliance.yml`、`ai-dev/tools/nop-lint-xdsl-baseline.txt`

- Item Types: `Feature` + `Proof`

- [x] 本地生成 baseline（**裁定**：语料口径 = nop-stream 子树 38 份 `.stream.xml`——**含 `_vfs/_delta` 层**（真实生效模型层，1 条 R10 存量在账）；**排除 `nop-lint/`**（规则套件 valid/invalid fixtures 14 份是规则自测夹具，已有 TestNopRuleSuites 自门禁，不入仓基线——排除后恰与 WI2 台账口径一致）；在 repo root 用 bash -c（zsh 不做 $FILES 词分割）+ xargs 形态跑 `NopLintCli check <files> --rules <6 规则逗号清单> --write-baseline ai-dev/tools/nop-lint-xdsl-baseline.txt`；build-classpath 的 outputFile 用 `<root>/_tmp/` 绝对路径（AGENTS.md 禁系统 /tmp；相对路径按模块 basedir 解析）
- [x] compliance.yml 新增 xdsl-lint 作业（镜像 bean-naming 作业结构：node/jdk setup + compile nop-lint 三模块 + build-classpath（绝对路径 outputFile）+ find（同口径：nop-stream 子树、排除 target）+ `check ... --rules <6 规则> --baseline-check ai-dev/tools/nop-lint-xdsl-baseline.txt --max-warnings 0`——**--max-warnings 0 是 warning 级规则只增门禁的必要条件**（baseline-check 先抑制存量，max-warnings 拦新增 warning），勿删）
- [x] 本地以 `--baseline-check` 复跑验证 exit 0；篡改一处（新增违规）验证 exit 非 0（正控自测，探针后还原）

Exit Criteria:

- [x] baseline 文件提交且 --baseline-check 本地 exit 0
- [x] 正控自测双方向：①人为新增违规 → baseline-check 非 0；②修掉一处违规但不再生 baseline → exit 1（stale entries，只减不增的「减侧收紧」）。两探针记录在 daily log，均还原
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - L2 仓扫门禁测试

Status: completed
Targets: `nop-stream/nop-stream-flow/src/test/java/io/nop/stream/flow/validate/TestXdslCorpusLintGate.java`

- Item Types: `Feature` + `Proof`

- [x] **白名单形态裁定（A 形）**：WARNING 面逐站点登记（键 = 路径后缀 + severity + errorCode + target——**不按消息文本匹配**（FAIL 消息含 bean 名/元素 id，fixture 改名即红；errorCode 已被测试钉为稳定键））；FAIL 面文件级登记（路径后缀 → 允许 FAIL + 预期原因，含全部 bean-not-found 语料文件与 by-design 负控——resolver=null 折叠全局容器解析失败是已知形态）；R6/R13/R14 的 SKIP **显式过滤 + 注释声明**（休眠注记不入门禁，激活由 WI15/WI21 负责）。不做 skipConfValidate 开关（WI3 severity contract 不动）
- [x] 测试逻辑：定位仓库根（复用 `nop-stream-core` 的 `InvariantTableCompleteness.findInventoryFile()` 向上找 marker 先例，fail-loud + -D 覆盖）→ 递归收集 `nop-stream/**` 下全部 `.stream.xml`（排除 target/；**含 _delta**）→ **语料加载裁定**：路径含 `_vfs/` 祖先的经 VFS 逻辑路径加载（剥离到 `_vfs/` 后的路径，与生产解析/delta 层叠一致），其余 FileResource（首个任务先验证 delta 文件解析行为并记录）→ 逐文件 lint → WARNING 站点 ⊆ 白名单、FAIL 文件 ⊆ 文件级白名单、SKIP 过滤 → 违规输出 diff 形态（文件+errorCode+target）
- [x] 白名单表（测试内常量表）：WARNING 10 键（R4×7 + R5×1 + R12×2——L2 无 R10 命名面，与 L1 口径设计性不同；每项带消号注释）+ FAIL 文件级登记（test 资源树整体 + 5 个 main 资源模型的 bean 依赖显式登记）
- [x] 断言失败消息输出 diff 形态（哪个文件出现白名单外 finding），便于消号

Exit Criteria:

- [x] 门禁测试绿（`./mvnw test -pl nop-stream/nop-stream-flow -am -Dtest=TestXdslCorpusLintGate`）
- [x] 正控自测：临时向某语料文件加一个坏 id → 测试红（探针后还原）
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 台账登记与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream-user-guide.md`

- Item Types: `Proof`

- [x] 矩阵段存量基线句扩展为台账：L1 基线文件路径 + baseline-check 门禁语义（内容寻址指纹、行号漂移不失效、只减不增）+ L2 门禁测试名 + 白名单表指针 + **消号操作语义**（消号 = 修违规 + 同 PR `--write-baseline` 再生，一体完成——修违规不改再生则 CI 红（stale entries）；新增第 7 条规则时须再生 baseline）；warn→error 升级条件 = 对应存量消号完毕
- [x] flow + lint 模块回归
- [x] 独立 closure audit

Exit Criteria:

- [x] 台账段落落地且与实际工件一致
- [x] 回归全绿
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] L1 CI 作业 + baseline + 只减不增验证（含正控探针）
- [x] L2 门禁测试 + 白名单（含负控 FAIL 登记 + 正控探针）
- [x] 消号台账落 user-guide 矩阵段
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2350-wi4-lint-gates-and-baseline.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI4 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 两层门禁 + 双台账落地：L1 compliance.yml xdsl-lint 作业（baseline 24 entries 只减不增）+ L2 TestXdslCorpusLintGate 仓扫白名单门禁（双向防漂移）+ user-guide 消号台账。审计实测三态探针（新增违规红/消号未再生红/还原绿）与 231/231 回归全过；两阶段机制（warning 不阻存量只阻新增）实证成立。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_82978a3d-e6cc-4bdd-b5cb-cc10a6eb278e（fresh session，未参与实现）
- Audit Session: agent_82978a3d-e6cc-4bdd-b5cb-cc10a6eb278e
- Evidence:
  - Phase 1：baseline 24 entries 恰合台账口径（R10=21/R12=2/R5=1）；CI 命令独立复现 exit 0（diagnostics total=0 + baseline-suppressed 24）；双向探针实测（植入违规 exit 1、修存量不再生 exit 1 + stale entries、还原 exit 0）
  - Phase 2：门禁源码逐点核验（10 键白名单/FAIL 白名单/SKIP 过滤/STALE 双向/仓库根定位/VFS 加载）全 PASS；测试绿 + 模块全量 231/231（audit 独立复跑）
  - Phase 3：台账段与工件零漂移；check-doc-links --strict exit 0；check-plan-checklist exit 0
  - Rule 4 两阶段合规：六规则 severity 仍 warning，存量抑制/新增即阻——实证成立
  - Minor 4 项均为收尾记账/措辞（roadmap checkbox、Closure 段回填、plan 措辞订正、closure commit）——已随本收口完成
  - 探针产物完全还原（audit 核对 git diff 为空）

Follow-up:

- no remaining plan-owned work
