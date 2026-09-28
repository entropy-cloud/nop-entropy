# 21 check-*.mjs 迁移切换收口：5 switchover + 2 candidate 行（roadmap item 5）

> Plan Status: active
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 5](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [design 12](../../design/nop-lint/12-check-scripts-migration-manifest.md) · [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [design 09](../../design/nop-lint/09-suppression.md)

## Purpose

完成 design 12 的 5 条 migrated-pending-switchover 行（#7 ibiz-interfaces / #12 vfs-violations / #20 orm-unique-key / #21 sensitive-literal / #22 silent-swallow）与 2 条 candidate 行（#3 bean-naming / #19 orm-icons）的切换收口：逐脚本同语料行为对照（零 diff 或 delta 逐条裁定）→ 调用点改接 nop-lint → 原脚本下线 → design 12 行状态 `switched-over`（门禁词表扩展）。**硬门禁牙齿保持**（Hard constraint 4）：改接后的命令必须与旧脚本同 fail-fast 语义。完成后 roadmap MT1 解锁。

## Current Baseline

- **接线点实测**：
  - #7 ibiz-interfaces：`ai-dev/tools/package.json` `check:ibiz`（纯手工口径，不在聚合 check/CI）；规则 `api/ibiz-missing-annotation` + `api/ibiz-missing-context` 已落地（error 级）。
  - #12 vfs-violations：无接线（手工）；规则 `nop/no-vfs-violation`（warning）已落地但**规则侧无 exemptions 对应**——mjs 默认语料 = nop-code + nop-stream、WHITELIST 4 文件 6 tag 豁免、已裁定两处结构性 delta（AST 面消除注释/字符串误报、深层限定链不匹配）——**对照 delta 必然出现，豁免对应是切换判据组成部分**（design 09 ruleset exemptions 机制已落地，cli-rules-exempt 测试在档）。
  - #20 orm-unique-key（model XML 面）/ #21 sensitive-literal / #22 silent-swallow：`ai-dev/tools/run-nop-metadata-invariants.sh` 步骤 [1/6][2/6][3/6]，经 `.github/workflows/maven.yml:103` invariant-gate job（JDK 21 + pnpm + node_modules 在场）间接 CI 接线。规则：`nop-orm-unique-key`（XNode，error）、`nop/silent-swallow`（error）已落地；`security/no-sensitive-literal` **warning 级**——旧 mjs 任何命中即非零退出，CLI warning 默认 exit 0，**改接必须带 `--max-warnings 0` 或升 severity（二选一裁定）**。
  - #3 bean-naming：`.github/workflows/compliance.yml:13–22`（**node-only job，无 JDK/maven**——头注释自述不要求 Maven 构建）；规则未落地（BEAN-ID + COLLECT-PREFIX 两面归 XNode，REF 跨文件引用面维持 mjs）；compliance.yml 现整步跑 mjs（REF 面 CI 在执法）。
  - #19 orm-icons：无接线；规则未落地（XNode attribute 必填，nop-orm-mandatory-default 先例同形）。
  - CLI `--rules` 白名单存在（CheckMojo 无此参数 → CI 改接走 CLI 通道）；TargetScanner 无 test 排除/内建白名单——**切换命令必须显式钉 targets 与旧脚本语料一致**（mjs 各自 scope：silent-swallow 仅 src/main、ibiz 跳 test、vfs 仅 nop-code+nop-stream）。
- design 12 门禁 `check-lint-migration-manifest.mjs`：状态词表 5 值无 `switched-over`（design 12 行 83 预告新增）；enum-set 双向；缺席判据适配 = plan 18 mapping 门禁同族模式（switched-over 行脚本缺席合法；非 switched-over 行缺席 = hard error）。
- 下线脚本零残留 grep 面除 workflows/package.json/sh 外含 `ai-dev/tools/README.md:28`（check:ibiz 行）。
- 新规则落地涟漪（bean-naming + orm-icons）：规则库 67→69、EXPECTED 57→59、RULE_FACET_CENSUS 71→73、gen 67→69、账本分面表 +2 行。
- 测试/门禁基线全绿（plan 20 后）。

## Goals

- 7 行逐脚本收口：switch-ready 者 switched-over（对照 + 改接 + 下线），不可 faithful 者显式 delta/deferred 裁定（不假切换）。
- 硬门禁牙齿保持：#21 面裁定 `--max-warnings 0` 或升 severity；#3 新规则 error 级；**变异红测**入 Exit Criteria（注入命中 → 改接命令必须非零退出）。
- design 12 词表 += switched-over + 门禁缺席判据 + 7 行回写。
- roadmap item 5 done → MT1 解锁。

## Non-Goals

- maintain-mjs 7 行与 exclude 7 行；`check-import-order.sh` 孪生件；ruleset exemptions 机制变更（仅消费）。

## Scope

### In Scope

- 新规则 2 份（bean-naming XNode error 级 / orm-icons XNode warning）+ suites + 三件套计数联动。
- 对照记录（7 行）+ design 12 词表/行状态/row 42 decommission cell 同步 + `check-lint-migration-manifest.mjs` 缺席判据 + self-test。
- 调用点改接：compliance.yml（含头注释更新）、invariants.sh、package.json（check:ibiz 改接形态裁定——若裁定保留纯 node 性质则记录改为移除 script + README 行）、ai-dev/tools/README.md。
- maven.yml invariant-gate job 的 nop-lint 可用性（构建步骤或 classpath 方案）——spike 裁决后入 scope 执行。
- 下线脚本 git rm + 零残留 grep（workflows/package.json/sh/README/账本行）。
- roadmap item 5 状态 + `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- maintain-mjs/exclude 行；`docs-for-ai/`（No owner-doc update required）。

## Execution Plan

### Phase 1 - spike 与对照（逐脚本）

Status: planned
Targets: 各脚本 + 等价规则

- Item Types: `Proof`（对照）+ `Fix`（缺规则落地）+ `Decision`（spike B CI 形态裁决）

- [ ] spike A：#3/#19 规则未落地确认 → 按 design 12 行规格落地（bean-naming error 级 CI 面；orm-icons warning）+ suites + 计数涟漪（67→69 / 57→59 / 71→73 / gen 67→69 / 账本 +2 行）
- [ ] spike B：invariant-gate job 的 nop-lint 可用性（mvnw 构建步骤 vs 预装 classpath）——结论决定 #20/#21/#22 的 CI 改接形态；**不可行时三行整体阻塞（不做局部切换、不降门禁），plan 记录 blocked 并升级裁定**
- [ ] 逐脚本对照（语料面钉死 = 旧脚本各自 scope，登记进对照记录与改接命令）：
  - #22 silent-swallow：mjs（nop-metadata src/main）vs `nop/silent-swallow`（error）
  - #20 orm-unique-key：mjs（nop-metadata/model/*.orm.xml 源模型面）vs `nop-orm-unique-key`（error）
  - #21 sensitive-literal：mjs（nop-metadata）vs `security/no-sensitive-literal` + `--max-warnings 0`（或 severity 升 error 裁定）
  - #7 ibiz：mjs 全仓 vs 两规则（error）
  - #3 bean-naming：mjs（BEAN-ID/COLLECT-PREFIX/REF 三面拆分）vs 新 XNode 规则（BEAN-ID/COLLECT-PREFIX 面）——REF 面不对照（维持 mjs）
  - #12 vfs-violations：mjs（nop-code+nop-stream、4 文件白名单豁免）vs `nop/no-vfs-violation` + 规则侧豁免对应（design 09 exemptions）——两处结构性 delta 逐条裁定
  - #19 orm-icons：mjs vs 新规则
- [ ] 对照结论登记：7 行 switch-ready / delta / deferred 资格判定

Exit Criteria:

- [ ] 7 行对照记录完整（语料、双方命中数、diff/delta 裁定、豁免对应）；无未裁定 diff
- [ ] 新规则落地 + 计数链同步 + `./mvnw test -pl nop-lint/nop-lint-nop -am` 全绿
- [ ] spike B 结论在档（#20/#21/#22 CI 形态或 blocked 升级）
- [ ] `ai-dev/logs/2026/09-28.md` 阶段条目

### Phase 2 - 切换执行 + 门禁适配

Status: planned
Targets: compliance.yml、maven.yml（若 spike B 要求）、invariants.sh、package.json、README、tools 目录、design 12、migration-manifest 门禁

- Item Types: `Fix`

- [ ] design 12 门禁：STATUS_VOCAB += `switched-over`；缺席判据（switched-over 行脚本缺席合法，其他行缺席 hard error）+ self-test 增补；design 12 row 42 decommission cell 与行 83 下线计划段按实际切换形态回写
- [ ] 调用点改接（switch-ready 行）：invariants.sh 三步 → nop-lint CLI（显式 targets 钉旧语料 + `--max-warnings 0` 若 #21 面维持 warning）；compliance.yml → 双接线（修剪后 REF 面 mjs + nop-lint CLI bean-naming 面；node-only 头注释更新——若 spike A/B 判定 CI 不可行则该行按 Deferred 段裁定）；package.json check:ibiz → 改接或移除（裁定记录）+ README 行同步
- [ ] **变异红测**（每个硬门禁改接面）：临时注入一个命中（如向 nop-metadata 某 java 加 `catch (Exception e) {}`），改接命令必须非零退出；还原后 exit 0——记录进对照文件
- [ ] switch-ready 脚本 git rm；design 12 行 → switched-over + 下线注记；delta/deferred 行回写
- [ ] 全门禁 + mvn test + doc-links + hollow；roadmap item 5 翻转前置检查 + MT1 解锁检查

Exit Criteria:

- [ ] 硬门禁等效运行：invariants.sh 改接后全跑通 exit 0 **且变异红测非零退出**；compliance.yml bean-naming 面等效（本地模拟 runner 命令）
- [ ] 已下线脚本零残留（grep workflows/package.json/sh/README/账本引用面）
- [ ] design 12 门禁主检查 + self-test 绿（缺席判据验证）；六门禁 + doc-links + hollow 全绿
- [ ] `./mvnw test -pl nop-lint/nop-lint-nop -am` 全绿；账本门禁（新规则行 + census 73）绿
- [ ] roadmap item 5 = `done` 前置就绪；MT1 解锁检查在档
- [ ] `ai-dev/logs/2026/09-28.md` 完整条目

## Closure Gates

> 构建验证 = `./mvnw test -pl nop-lint/nop-lint-nop -am` + 门禁联跑。

- [ ] Phase 1–2 全部 Exit Criteria 勾选完毕
- [ ] 7 行全部收口（switched-over / delta / deferred 逐行证据在档），无假切换；硬门禁牙齿经变异红测证明保持
- [ ] 全部门禁绿 + 测试全绿
- [ ] roadmap item 5 = `done`（独立 closure audit 后翻转）；MT1 解锁
- [ ] 独立子 agent closure audit 完成且证据写入 Closure 段
- [ ] Anti-Hollow Check：对照真实双跑 + 变异红测真实非零退出
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/21-mjs-switchover.md --strict` exit 0

## Deferred But Adjudicated

### #3 bean-naming CI 改接（若 compliance.yml node-only 约束不可满足）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Hard constraint 5 禁止无等价替代下线——REF 面 mjs 保留执法（原脚本修剪后仍 live），BEAN-ID/COLLECT-PREFIX 面规则已落地供本地消费；CI 双接线的 JDK 前置属 infra 变更，不阻塞其余 6 行收口。
- Successor Required: `yes`
- Successor Path: 后续 plan（compliance.yml infra 变更 + BEAN-ID 面 CI 接入）
- 分支补注：该分支下 roadmap item 5 不翻转 done（保持 planned + 注记"item 4b…#3 CI 接入 deferred"），MT1 解锁顺延——与 spike B 的 blocked 分支处理对称

## Non-Blocking Follow-ups

- （无——#12 白名单豁免已入切换判据，见 Phase 1）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:
