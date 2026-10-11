# 2428 WI3 GitHub Actions → JDK 25 + nop-cli lint gate 入 CI

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI3(mission jdk25-sb4-q4,第四个执行项;WI0-WI2 已 done)
> Related: `ai-dev/plans/2425-wi1-jdk25-build-chain-pins.md`(解除 21/22 错配的前置);`docs-for-ai/03-runbooks/nop-cli-and-lint-gate.md`(lint 门禁 runbook,§5 记录 CI 阻塞)

## Purpose

CI 构建面全部迁到 JDK 25(与 WI1 后的 release=25 一致,消除过桥期 CI 必红),并把因 21/22 错配而缺席 CI 的 nop-cli lint 门禁接入 maven.yml。

## Current Baseline

- WI1(commit 508a992038)后全仓 release=25;WI2(d4b92ca041)后全 reactor 无 <25 pin。CI 仍是 21 → release=25 无法在 CI 编译(过桥期必红,plan 2425 已声明)。
- `.github/workflows/maven.yml`:3 处 `java-version: '21'`(:39 build、:84 invariant-gate、:114 e2e)+ 步骤名 "Set up JDK 21" ×3。
- `.github/workflows/compliance.yml`:3 处 `java-version: '21'`(:26、:73、:112)。`sync-docs-to-community.yml` 无 Java setup(WI0 已核)。
- nop-cli lint 门禁现状:`.githooks/pre-commit` 仅对本机生效(core.hooksPath);runbook §5(`nop-cli-and-lint-gate.md:203-205`)明载 "CI 目前无法构建 nop-cli,lint 门禁只在本机 git hook 中生效"。git log 无 maven.yml 曾含 lint gate 的痕迹——WI3 的"re-enable"= **首次接入**(非恢复)。
- lint 执行器:`ai-dev/tools/run-java-lint.sh` 支持无参模式(扫描全部 git-tracked .java),nop-cli 解析顺序 = PATH → `~/.nop/cli/bin` → worktree 构建产物 jar;gate 规则 3 条(no-raw-exception/no-empty-catch/no-log-getmessage),fixture/bench-corpus 过滤内建;退出码 0/1/2。2026-10-10 日志记载本机已验证 65 规则引擎、hook 全绿。
- nop-cli 产物:reactor 构建产出 `nop-runner/nop-cli/target/nop-cli-2.0.0-BETA.1.jar`(WI1 后 release=25;模块 pom 覆盖版本为 2.0.0-BETA.1);maven.yml build job 的构建命令为 **`mvn -B package --file pom.xml`**(runner 系统 Maven 3.9,非 wrapper Maven 4;含测试)——nop-cli 在 reactor 构建路径上(root→nop-runner→nop-cli),CI 上该链(nop-cli ≥22 以来)首次可走通。
- 验证边界:GitHub CI 无法本地执行;本地等价验证 = 用 JDK 25 + worktree jar 实跑 wrapper 全仓扫描 + YAML 结构解析 + java-version 残留 grep。

## Goals

- 两个 workflow 共 6 处 java-version 全部 25,步骤名同步。
- maven.yml build job 新增 nop-cli lint gate 步骤(run-java-lint.sh 无参全仓,失败即红)。
- runbook §5 的"CI 未构建 nop-cli"表述更新为 CI 已接入(本 plan 改变 owner-visible 行为,owner doc 必须同步)。

## Non-Goals

- legacy `.workflow/*` 删除与 docs JDK 表述(WI4)。
- 扩大 lint 规则面至全 65 规则(runbook 载明是 deliberate follow-up,以清偿存量 findings 为前提)。
- invariant-gates/e2e job 的 Node/pnpm 等其他工具链版本。

## Scope

### In Scope

- `.github/workflows/maven.yml`、`.github/workflows/compliance.yml` 的 JDK 版本与 lint gate 步骤。
- `docs-for-ai/03-runbooks/nop-cli-and-lint-gate.md` §5 更新。
- 本地等价验证。

### Out Of Scope

- `.workflow/`、`AGENTS.md`、`project-context.md`(WI4)。
- `sync-docs-to-community.yml`(无 Java 面)。

## Execution Plan

### Phase 1 - workflow 修改

Status: completed
Targets: `.github/workflows/maven.yml`、`.github/workflows/compliance.yml`、`docs-for-ai/03-runbooks/nop-cli-and-lint-gate.md`

- Item Types: `Fix`(WI3 Deliverables)+ `Feature`(lint gate 步骤为新增能力)

- [x] maven.yml:3 处 java-version 21→25,步骤名 "Set up JDK 21"→"Set up JDK 25"
- [x] compliance.yml:3 处 java-version 21→25(步骤名如有同步)
- [x] maven.yml build job 增加步骤:构建后运行 `ai-dev/tools/run-java-lint.sh`(无参全仓,退出码非 0 即 job 失败;步骤名 "nop-cli lint gate (3 gate rules, whole tree)")
- [x] runbook §5:改写为"CI 已接入(maven.yml build job,JDK 25 构建后全仓扫描)",保留本机 hook 说明;INDEX/其他 doc 若有同样表述则同步(grep 核实)
- [x] No new test required:CI 配置与文档;wrapper 行为验证由 Phase 2 本地等价执行承担

Exit Criteria:

- [x] `grep -n "java-version" .github/workflows/*.yml` 全部为 '25',零 '21' 残留
- [x] YAML 解析通过(python yaml.safe_load 或等价)
- [x] runbook §5 与 maven.yml 实际步骤一致(步骤名/脚本路径)
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 本地等价验证

Status: completed
Targets: wrapper 全仓执行

- Item Types: `Proof`

- [x] JDK 25 环境确认(`_tmp/wi3-toolchain.txt`)
- [x] 前置核对:工作树存在并发会话 WIP java 差异(nop-orm-eql/nop-autotest 等,已记录)——扫描 exit 0 无 violation,归因问题不发生;若出现 violation 须按 HEAD 探针归因(未触发)
- [x] nop-cli jar 存在且为 release 25 产物(必要时 `-pl nop-runner/nop-cli -am` 重建;major 抽查)
- [x] `ai-dev/tools/run-java-lint.sh` 无参全仓实跑,退出码 0(与 CI 步骤同命令同环境)
- [x] YAML 结构校验两文件均通过

Exit Criteria:

- [x] wrapper 全仓退出码 0 且日志落档 `_tmp/wi3-lint-gate.log`
- [x] 工具链证据在档
- [x] `ai-dev/logs/` 条目已更新

验证边界:GitHub runner 上的实际行为(Ubuntu 环境差异、缓存)无法本地复现,由 push 后的 CI 运行兜底;本 plan 承诺命令级等价。

No owner-doc update required beyond Phase 1 的 runbook 更新(已含)。

### Phase 3 - 收口

Status: completed
Targets: roadmap、本 plan、日志

- Item Types: `Proof` / `Decision`(roadmap 回写)

- [x] 独立子代理 closure audit(fresh session),证据写入 Closure 节
- [x] roadmap WI3 `todo` → `done`(仅此一处)
- [x] `check-plan-checklist.mjs --strict`、`check-doc-links.mjs --strict` 退出码 0
- [x] 严格 pathspec 提交
- [x] `scan-hollow-implementations.mjs`:N/A(CI 配置+文档)

Exit Criteria:

- [x] audit 证据入 plan
- [x] roadmap WI3 = done 且其余未动
- [x] 两工具退出码 0
- [x] 日志收口条目在档

## Closure Gates

- [x] 6 处 java-version 全 25、零 21 残留、YAML 解析通过
- [x] lint gate 步骤在档且命令与本地验证一致
- [x] 本地等价验证:wrapper 全仓 exit 0(JDK 25 + release-25 jar)
- [x] runbook §5 已同步
- [x] 独立子代理 closure audit PASS 且证据入 plan
- [x] roadmap WI3 回写 done 且其余未动
- [x] 两 --strict 工具退出码 0

## Deferred But Adjudicated

无 deferred 项。

## Non-Blocking Follow-ups

- 扩大 lint 规则面(runbook 既有 deliberate follow-up,非本 plan 引入)。
- CI 首次真实运行的结果追踪(push 后观察一次 run,记录到日志)。

## Closure

Status Note: 6 处 java-version 全 25(零残留)、lint gate 步骤入 maven.yml build job、runbook §5 同步;本地等价验证(JDK 25 + release-25 uber-jar)wrapper 全仓 exit 0;closure audit PASS(2b 日志截断以 artifact 直检替代,advisory 已记录)。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_67ae69fe-250b-4592-9eeb-f39a5805cfc0(fresh session,只读)
- Evidence:
  - Phase 1:6 处 java-version 全 '25' 零 21;步骤名 JDK 25 ×3;lint gate 步骤(:44-45)无 continue-on-error;compliance 裸 uses 形态 ×3;runbook §5 断言逐项核实(GATE_RULES 逐字、脚本在档、hook 同源);禁用短语族 docs-for-ai 零残留;YAML safe_load 双文件通过
  - Phase 2:`_tmp/wi3-toolchain.txt`=25.0.1;jar 在档(59MB)+ EmptyMain major=69(auditor unzip+xxd 独立复核;advisory A:`_tmp/wi3-cli-build.log` 为截断捕获,以 artifact 直检替代);lint 全仓 15848 文件 40 批次 error=0 + 统计行在档,无规则静默降级
  - diff 圈定恰 5 文件无夹带;roadmap 未提前翻转;文本一致性自洽
  - 工具:check-plan-checklist --strict exit 0、check-doc-links --strict exit 0(收口时复跑);scan-hollow 不适用(本 plan 零 Java 代码变更,无实现面)
- Follow-up:
  - advisory(B):`docs-for-ai/02-core-guides/invariant-guards.md:72` 的工具链措辞(Node 20/JDK 21)留待 WI4 一并核对
  - CI 首跑观察(push 后记录一次真实 run 结果到日志)
