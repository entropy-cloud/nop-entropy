# 2509 时间等待违规守卫脚本（roadmap WI-3：分类结果入 ai-dev/tools，存量基线只减不增）

> Plan Status: completed
> Last Reviewed: 2026-11-07
> Source: `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-3；盘点脚本 `_tmp/classify_test_sleeps.py`（临时，git 忽略）晋升为正式守卫
> Related: `ai-dev/plans/2508-shared-deterministic-time-test-tools.md`（completed，共享工具 owner）；`docs-for-ai/02-core-guides/testing.md`「禁止依赖敏感时间等待」
> 编号说明：沿用仓库当前 4 位序列（最近为 2508）。注：plan guide 规则 21 写两位 `NN-` 编号，与仓库实际 4 位惯例（2314/2350/2508 等）不符——属 guide 漂移，按仓库惯例执行；guide 修订留 follow-up（不在本 plan scope）。
> Review: R1 对抗审查（独立子 agent，现场复跑盘点脚本核对基线数）2 Major + 7 Minor，本稿逐条修订（F1 基线数修正 521 带分类明细；F2 探针 (b) 改为实测 stale 分支 + (b2) 增向；F3 窗口算术逐条复刻要求；F4 --update 确定性序列化 + CRLF 拆行；F5 _gen 对账口径；F6 分类翻转双红语义；F7 follow-up 措辞；F8 编号 guide 漂移注记）。R2 复审全部确认解决，新增 2 Minor（探针计数措辞、follow-up 登记）已修订，判定 READY 可执行（同一独立子 agent 两轮）。
> 跨平台约束（用户 2026-11-07 指令）：脚本与基线必须 Windows/Linux/macOS 可复现——零依赖 Node ESM、`fs` 遍历（不依赖 rg/shell）、`fileURLToPath(import.meta.url)` 定位根、基线键一律 repo 相对正斜杠路径、UTF-8 读取、无外部进程调用。

## Purpose

把「测试不得依赖敏感时间等待」从规范条目升级为机械守卫：时间等待分类结果以脚本 + 提交基线入 `ai-dev/tools/`，新增违规即红（exit 非 0），存量基线只减不增（消号 = 修复 + 同批重新生成基线），为 WI-4 ~ WI-8 的逐模块清欠提供可审计的递减账本。

## Current Baseline

- testing.md 已入册四类禁止写法与允许形态（plan 2507 Phase 1）；roadmap Evidence Ledger 记录测试源 `Thread.sleep` / `TimeUnit.*.sleep` 共 530 处（2026-11-06 盘点，启发式四分类：SLEEP_THEN_ASSERT / POLL_LOOP / SIMULATED_WORK / OTHER）。
- 盘点脚本 `_tmp/classify_test_sleeps.py` 为临时启发式实现（按 sleep 出现处上下文分类；不含墙钟 deadline 与真实定时器——roadmap Sources 行明示后者逐模块人工核对）。
- 仓库已有「存量基线只减不增」硬门先例：`xdsl-lint` CI job（`.github/workflows/compliance.yml`）以 `--baseline-check ai-dev/tools/nop-lint-xdsl-baseline.txt --max-warnings 0` 强制——新增违规 exit 1、已修复未重新生成基线（stale entry）也 exit 1；消号 = 修复 + 同批 regenerate。
- 仓库已有「正向控制自测先于扫描」先例：`check-nop-stream-structure.mjs self-test`（扫描器必须先证明自己能变红，防止静默失效空转放行）、`check-silent-wrong-result.mjs --fixture`；`ai-dev/tools/package.json` 已有 `check:ai-invariants`（`--self-test` 两连跑）模式。
- 实测（本 plan 起草日，评审子 agent 现场复跑 `python3 -I _tmp/classify_test_sleeps.py .`）：测试源 sleep 命中 521 处 / 46 模块，按分类 OTHER 186 / POLL_LOOP 210 / SLEEP_THEN_ASSERT 108 / SIMULATED_WORK 17（与 roadmap Evidence Ledger 的 2026-11-06 盘点 530 相比差 -9，为 2507 收口后残余漂移）；集中模块 nop-stream-runtime 144、nop-ai-agent 66、nop-stream-core 48、connector-debezium 24、fraud-example 22、nop-auth-service 16、nop-ai-core 15、lettuce/kafka/shell 各 12；`src/test` 下 `_gen/` 无命中；WI-1/2507 收口后调试子系统残留均为允许形态。
- `ai-dev/tools/` 是仓库工具依赖唯一 pnpm 根；新工具注册面 = `package.json` scripts + `README.md` Tool Index；check-doc-links 覆盖 `ai-dev/` markdown。
- 无 Java/模块面变更预期：本 plan 全部产出为 `ai-dev/tools/` 脚本 + 基线 + README + package.json + `.github/workflows/compliance.yml` job。

## Goals

- `ai-dev/tools/check-test-time-waits.mjs`：零依赖守卫脚本——扫描全仓 `src/test/**.java` 的 sleep 时间等待、按 roadmap 四分类启发式归类、与提交基线对账；新增违规 exit 1、stale entry exit 1、`--update` 同批消号、`self-test` 正向控制。
- `ai-dev/tools/test-time-waits-baseline.json`：提交基线（per-file × category 计数，稳定序列化，diff 可审）。
- 守卫接入 CI（compliance.yml 新 job：self-test 先行 + 全仓检查）与 `pnpm` 别名 + README 注册。
- 与盘点脚本数字对账：JS 守卫与 `_tmp/classify_test_sleeps.py` 在同一树上的 per-file × category 分类结果一致（启发式平移不失真）。

## Non-Goals

- 不在本 plan 内清欠任何存量违规（WI-4 ~ WI-8 逐模块承接；基线初值 = 现状盘点）。
- 不覆盖墙钟 deadline / 真实定时器 / 耗时阈值断言三类语义违规（roadmap Sources 行明示启发式不覆盖、逐模块人工核对；守卫只机械看守 sleep 面）。
- 不改任何 Java 生产/测试代码（`_tmp/` 之外的源码零触碰）。
- 不做语义级「允许形态」裁定自动化——基线条目即为裁定载体，理由随各 WI plan/log 留痕。

## Scope

### In Scope

- `ai-dev/tools/check-test-time-waits.mjs`（扫描 + 分类 + 对账 + self-test + `--update` + `--module` + `--json`）。
- `ai-dev/tools/test-time-waits-baseline.json`（初始基线）。
- `ai-dev/tools/package.json`、`ai-dev/tools/README.md` 注册。
- `.github/workflows/compliance.yml` 新增 `test-time-waits` job。
- `_tmp/` 内对账/探针脚本（不入库）。

### Out Of Scope

- 各模块测试改造与基线消号（WI-4 ~ WI-8）。
- nop-lint 规则化（本 plan 用独立脚本对账；是否并入 nop-lint 由后续 roadmap 裁定——当前 `docs/` 遗留目录不收新物，nop-lint 规则面扩容需独立立项）。
- 墙钟 / 定时器语义守卫。

## Execution Plan

### Phase 1 - 守卫语义裁定

Status: completed
Targets: 本 plan（决策记录）

- Item Types: `Decision`

- [x] Decision：检测面 = 全仓 `src/test/` 下 `.java`（排除 `target/`、`node_modules/`、`.git/`、`_tmp/`、`_gen/`；`_gen/` 无命中实测，排除以免生成物入账）。
- [x] Decision：分类口径 = roadmap 四分类启发式（SLEEP_THEN_ASSERT / POLL_LOOP / SIMULATED_WORK / OTHER），逐处计数入基线；未知/复杂上下文落入 OTHER（不报错不崩溃——OTHER 是真实分类，不是静默跳过；self-test 用畸形输入固定该行为）。
- [x] Decision：对账语义 = per-file × category 计数——current > baseline（含新文件/新类别）exit 1（增）；baseline > current 或文件消失 exit 1（stale，需 `--update` 同批消号）；相等 exit 0。「只减不增」由二者共同保证。已知噪声语义：纯重构导致的分类翻转（如 sleep 自 while 移入 lambda → POLL_LOOP 变 stale 且 SIMULATED_WORK 变增）会双向变红并要求 `--update` 同批重生成——保守且安全，属预期行为而非守卫缺陷（写入 README 语义节）。
- [x] Decision：CI 接入 = compliance.yml 新 job（node-only，self-test 先行 + 全仓检查），沿用 xdsl-lint 的 baseline 门与 nop-stream-structure 的正向控制先例。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 四项 Decision 记录于本 plan 且 `ai-dev/logs/2026/11-07.md` 记录裁定。
- [x] No new test required：纯决策，无行为面。

### Phase 2 - 守卫脚本与正向控制

Status: completed
Targets: `ai-dev/tools/check-test-time-waits.mjs`

- Item Types: `Feature`

- [x] 扫描 + 四分类启发式（与 `_tmp/classify_test_sleeps.py` 同口径：向上找最近包含块头——精确复刻脚本窗口算术 `range(idx-1, max(-1, idx-40), -1)` 即 39 行上溯、跳过空行、缩进更小且以 `{` 结尾——循环头 POLL_LOOP、lambda/run/call 头 SIMULATED_WORK；向下 7 行（idx+1..idx+8 开区间）SYNC 先于 ASSERT 判 OTHER、ASSERT 先于 SYNC 判 SLEEP_THEN_ASSERT、否则 OTHER；JS 逐条复刻正则与窗口，不得凭「等价语义」重写）。
- [x] `self-test` 正向控制：内置正/负 fixture（四分类各至少 1 正例 + 边界：无 sleep 文件全零、畸形未闭合代码不崩溃且稳定落入 OTHER、注释中的 sleep 不计、字符串字面量中的 sleep 不计），断言分类结果与计数，扫描器「证明自己能变红」。注释/字符串剥离用字符状态机（block comment / line comment / string / char literal），只可能少计不可能多计。
- [x] 对账引擎：`--update` 重生成基线（确定性序列化：文件键 posix 排序、类别固定顺序、2 空格缩进、结尾换行——重生成 diff 只含真实增减）；check 模式按 Phase 1 语义判定增/stale/相等并给出可操作报错行（文件 + 类别 + 当前/基线计数）。CRLF：读文件后按 `splitlines()` 等价语义拆行（`/\r?\n/`），与 Python `splitlines()` 一致，Windows checkout 不改变分类。
- [x] `--module <prefix>` 子集扫描（check 只对子集内报错）、`--json` 机器可读报告、`--help`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `node ai-dev/tools/check-test-time-waits.mjs self-test` exit 0。
- [x] 无静默跳过（Minimum Rules #24）：畸形/未知输入有确定行为（OTHER 归类或显式报错，不崩溃不吞异常）；对账判定三态（增/stale/相等）覆盖完整，self-test 含三态正反例。
- [x] No owner-doc update required：README 注册在 Phase 3 与 CI 同批（此刻脚本尚未定稿）。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 3 - 基线、对账验证与 CI 接入

Status: completed
Targets: `ai-dev/tools/test-time-waits-baseline.json`、`ai-dev/tools/package.json`、`ai-dev/tools/README.md`、`.github/workflows/compliance.yml`

- Item Types: `Feature`, `Proof`

- [x] `--update` 生成初始基线并提交（总量、分模块分布写入 logs；与 roadmap Evidence Ledger 的 2026-11-06 盘点数量级一致）。
- [x] 对账（启发式平移不失真）：`_tmp/` 内一次性对账脚本比对 JS 守卫与 `_tmp/classify_test_sleeps.py` 在同一树上的 per-file × category 计数——JS 侧多做的注释/字符串剥离与 `_gen/` 排除在 Python 侧同口径适配后再比对，要求完全相等；不等则修 JS 至相等（证据存 `_tmp/`）。
- [x] 变异探针（对账语义三态实测）：(a) 临时新增含 `Thread.sleep` + 断言的测试文件 → check exit 1（增：current > baseline）；(b) 临时移走一个真实 sleep 命中（如临时删改一个含 sleep 的行）不动基线 → check exit 1（stale：baseline > current，「修复未消号」分支——守卫的核心语义必须被实测变红）；(b2) 人为删除基线一个条目 → check exit 1（另一向：current > baseline 的新增面）；(c) `--update` 消号 + 恢复现场 → exit 0；(d) `--module` 子集模式对无关文件增置不误报。探针全部记录于 logs。
- [x] CI 接入：compliance.yml 新增 `test-time-waits` job（checkout + setup-node 22 → `self-test` → 全仓 check，均 exit 0 required；不装 JDK——纯 node 面）。
- [x] 注册：`package.json` 加 `check:test-time-waits` / `check:test-time-waits:selftest` 别名；README Tool Index 加行 + 详述节（用法、基线语义「只减不增」、消号流程 = 修复 + `--update` 同批）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 基线已生成：`node ai-dev/tools/check-test-time-waits.mjs` exit 0（全仓对账绿）。
- [x] JS/Python 对账 100% 相等（证据写入 logs）。
- [x] 变异探针 5 项（a / b / b2 / c / d）全部按预期变红/变绿（logs 记录）。
- [x] README/别名/compliance.yml 就位；`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 4 - 收口

Status: completed
Targets: roadmap、logs

- Item Types: `Proof`

- [x] roadmap WI-3 → done（Plan 列填本 plan 编号）；`ai-dev/logs/2026/11-07.md` 收口条目（含「后续 WI 消号流程」一句话：WI-4 ~ WI-8 每 plan 收口时对其模块面跑 `--update` 同批减基线）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap 状态块已更新。
- [x] `ai-dev/logs/2026/11-07.md` 收口条目已写。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 守卫脚本 + 基线 + 注册 + CI job 全部就位且 self-test exit 0
- [x] 全仓 check exit 0（基线与现状对账绿）
- [x] JS/Python 启发式对账 100% 相等
- [x] 变异探针（增 / stale 双向 / 消号 / 子集过滤）证据齐全
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：self-test 正向控制证明扫描器对新增违规确实变红（防静默失效）；对账三态语义被探针实测
- [x] **mvn 验证裁定**：本 plan 零 Java/模块面变更（纯 `ai-dev/tools/` + CI + README），`./mvnw compile` / `./mvnw test` 不适用——以脚本级验证（self-test / 全仓 check / 对账 / 探针）替代，此裁定经独立 closure audit 复核
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2509-test-time-waits-guard.md --strict` 退出 0
- [x] **跨平台核查**：脚本零外部依赖（无 rg / shell / 外部进程）、`fs` 遍历、`fileURLToPath` 定位、基线键正斜杠、CRLF 无关读取；Windows/Linux/macOS 可复现结论记录于 logs

## Deferred But Adjudicated

（本 plan 无 deferred 项。）

## Non-Blocking Follow-ups

- WI-4 ~ WI-8 各模块清欠与基线消号（roadmap 承接）。
- 墙钟 / 定时器语义面的机械化：out-of-scope improvement（本 plan Non-Goals 明示）；successor = 后续 roadmap 独立裁定是否 nop-lint 规则化。
- plan guide 规则 21 编号位数修订（guide 漂移，out-of-scope improvement；successor = 独立治理项）。

## Closure

Status Note: 时间等待守卫落地为 `ai-dev/tools/check-test-time-waits.mjs` + 提交基线（521 处 / 255 文件 / 46 模块）+ compliance.yml `test-time-waits` job（self-test 先行 + 全仓对账）+ pnpm 别名与 README 注册；「存量基线只减不增」双向硬门（增与 stale 均 exit 1）经 5 项变异探针实测；启发式与盘点脚本经对账 100% 相等。独立 closure audit 复核全部 Exit Criteria 与 Closure Gate 后 APPROVED（2 Minor：updatedAt 跨日日期戳差异、日志文件数 4767→4709 已修正）。

Completed: 2026-11-07

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现；同日 plan draft R1/R2 审查者为另一独立子 agent）
- Evidence:
  - Phase 1：四项 Decision 记录于 plan（:64-67）与 logs；PASS。
  - Phase 2：`self-test` 审计现场复跑 14/14 exit 0；五个正则与 `_tmp/classify_test_sleeps.py` 逐字节一致；上溯窗口 `j>=max(-1,idx-40)+1` 与下溯 7 行窗口逐行核对一致；掩码为 5 态字符状态机（行结构保持）；`--update` 序列化确定性——审计者以自己 dump 经同一算法重建基线与在盘文件逐字节一致；`--module` inScope 过滤同时覆盖增/stale 双循环（以 nop-stream-runtime 144 条目与 nop-auth-service 实测无误报）；退出码 0/1/2 全部实测。
  - Phase 3：基线 totals OTHER 186 / POLL_LOOP 210 / SIMULATED_WORK 17 / SLEEP_THEN_ASSERT 108 = 521、255 文件、46 模块；审计现场复跑全仓 check exit 0（4709 文件）；对账复跑 `js=py=255, 0 mismatch, 0 only-*` exit 0；探针 (a) 审计者独立复现（含 `--module` 下红），其余 4 项由 logs 记录 + self-test compare 用例覆盖；README Tool Index 行 + 详述节、package.json 别名、compliance.yml job 均就位且 YAML/JSON 有效；`check-doc-links.mjs --strict` 复跑 0 errors。
  - Phase 4：roadmap WI-3 → done + plan 编号；logs 收口条目含 WI-4~8 消号流程。
  - Anti-Hollow：正向控制实证——审计者制造的临时违规确实使扫描器变红（exit 1 + INCREASE 指名文件）；三态对账语义探针实测；`scan-hollow-implementations.mjs --module ai-dev/tools --severity high` exit 0。
  - mvn 验证裁定复核：plan 触碰面恰为 8 个文件（脚本/基线/plan/compliance.yml/package.json/README/roadmap/log），零 Java 零 pom；脚本级验证（self-test/全仓 check/对账/探针）已执行并被审计复跑。
  - 文本一致性（Guide #19）：Plan Status=completed、4 Phase 全 completed、Closure Gates 全勾、`check-plan-checklist.mjs --strict` 复跑 exit 0、logs 与 plan 一致（521/255/46 与两处执行期 bug 修复记录一致）。
  - 跨平台核查：仅 import `node:fs/path/url`；无 child_process / rg；`fs` 遍历 + `fileURLToPath`；基线键正斜杠；`/\r?\n/` 拆行（4709 个测试 java 文件无孤立 CR）；CI node-only ubuntu。
  - 审计判定：**APPROVED**（2 Minor：updatedAt 为日期戳、跨日重生成有一行 diff——同日重生成已实证逐字节一致；日志文件数已修正 4709）。

Follow-up:

- WI-4 ~ WI-8 各模块清欠与基线消号（roadmap 承接，每 plan 收口 `--update` 同批减基线）。
- 墙钟 / 定时器语义面机械化：out-of-scope improvement；successor = 后续 roadmap 独立裁定是否 nop-lint 规则化。
- plan guide 规则 21 编号位数修订（guide 漂移，out-of-scope improvement；successor = 独立治理项）。
- 无剩余 plan-owned work。
