# 10 N2.4 自动重建触发(triggerRebuildFromCommit)

> Plan Status: active
> R2(agent_aacc8bf0):PASS——A1 规范路径归一/A2 指纹 no-op statusMessage/A3 reset 可见性(setDebounceMillis public setter)/A4 子目录用例 已纳入执行
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N2.4;`graph-discovery-and-export-design.md` §3.4(契约+未决项);R1 对抗审查(agent_aacc8bf0,1 Blocker + 4 Major + 1 Medium + 8 Low)——manifest 机制证伪后按审查建议方案重写
> Related: N1.2/N1.3/N1.4(失效与自愈语义)、N3.1(增量传播后续)

## Purpose

提供 commit 驱动的自动重建入口:GraphQL mutation `NopCodeIndex__triggerRebuildFromCommit(indexId, projectPath, baselineCommitish, targetCommitish)`。外部适配器(post-commit hook/VCS webhook receiver/nop-job)只调 mutation;nop-code 侧以 `git diff` 做前置校验与短路判断,实际增量由既有 fingerprint 管线对工作树现状执行。

## Current Baseline

- **未决项裁定(R1 证伪后重裁)**:设计 §3.4 的"变更集写入 manifest 复用 triggerIncrementalIndex"**不可行**——live 核实 `manifestPath` 是死参数(`CodeIndexService.triggerIncrementalIndex` L743-839 方法体零引用;真实增量输入=DB 指纹(`OrmFingerprintStore.loadFingerprints`)+ 工作树全量扫描(`collectResourcesFromVfs`)+ `IncrementalDetector` 对比;既有测试传 `"none"`)。**新裁定**:git diff 仅承担(a)HEAD 一致性校验、(b)变更集空短路、(c)报告;实际增量 = `triggerIncrementalIndex(indexId, projectPath, null)`(第 3 参注明废弃)对工作树现状执行——工作树必须已 checkout 至 targetCommitish(见前置条件)。
- **前置条件(钉死)**:调用方保证 projectPath 工作区 HEAD == targetCommitish;mutation 执行 `git rev-parse HEAD` 与 `git rev-parse target` 比对,不一致抛错(防止"名为重建至 target 实际索引任意树状态")。projectPath 必须为 git 仓库根(`git rev-parse --show-toplevel` 等于 projectPath,保证 diff 相对路径与 pathMapper 口径一致)。
- **失效语义已对齐**(N1.2-N1.4):实际变更才失效缓存与物化行;no-op 保留;幂等由 fingerprint 保证。
- git 调用先例:`ChangeAnalyzer.parseGitDiff`(ProcessBuilder + waitFor 30s 超时 + destroyForcibly);ref 校验先例 `GIT_REF_PATTERN`(validateGitRef,拒绝 `=` 等注入字符)。
- BizModel admin mutation 先例:`triggerFullIndex` `@BizMutation @Auth(roles="admin")` + `IncrementalStatus` 注册;`@InjectValue` 先例(`"@cfg:key|default"` 语法 + setter,LoginServiceImpl)。
- invariant 四表:`IDEMPOTENCE_TABLE`/`KNOWN_NON_IDEMPOTENT`/`DELETE_METHODS`/`QUERY_METHODS`(无 MUTATE 表);新 mutation 写方法入 IDEMPOTENCE_TABLE + verify 分支(先例 triggerIncrementalIndex)。
- allowedLocalRoot 现状(R1 核实):`file:` URI 形式路径会跳过 allowedLocalRoot 校验(仅 `..` 检查生效);本 mutation 沿用该现状,根约束收紧归 N6.5 多租户。

## Goals

- `RebuildFromCommitResult` DTO(@DataBean:changedCount/debounced/skippedNoChanges/statusMessage;changedCount=triggerIncrementalIndex 返回的实际入库变更数)。
- `CodeIndexService.triggerRebuildFromCommit(indexId, projectPath, baselineCommitish, targetCommitish)`:
  1. 去抖(拒绝式):per-indexId 内存时间戳,窗口 `@InjectValue("@cfg:nop.code.rebuild.debounce-millis|30000")` setter 注入;窗口内重入返回 debounced=true(statusMessage 含窗口毫秒);**调用成功即刷新窗口**;
  2. 校验:validateGitRef(baseline/target)→ projectPath 归一(剥 file: 前缀)→ `git rev-parse --show-toplevel` 与 projectPath 规范路径归一后比较(getCanonicalFile,macOS /var 符号链接——R2 A1) → `git rev-parse HEAD` == `git rev-parse target`(不一致抛 NopException,新 ErrorCode);
  3. `git diff baseline..target --name-only`(30s 超时+destroyForcibly):空输出 → skippedNoChanges=true, changedCount=0;
  4. 调 `triggerIncrementalIndex(indexId, projectPath, null)`(manifestPath 废弃参数,传 null 并注释)→ changedCount=返回值;
- BizModel `@BizMutation @Auth(roles="admin")` `triggerRebuildFromCommit` + `IncrementalStatus`(mode="rebuild")注册。
- invariant:`triggerRebuildFromCommit` 入 IDEMPOTENCE_TABLE + verify 分支(先重置去抖状态——package-private reset 方法供测试,再重放断言 0)。
- 集成测试 `TestRebuildFromCommit`(真实 git 仓库夹具,`git init`+`-c user.name/-c user.email` commit;git 不可用 `assumeTrue` 跳过;变更后 sleep 50ms 规避 mtime 粒度)。
- owner docs:query-api-design §4.1 增行、graph-discovery §3.4 状态+未决项裁定回写、缺口矩阵 N2.4 行。

## Non-Goals

- watch daemon/webhook 入口(§3.4);远端 clone/fetch(Deferred:外部适配器职责);repo→indexId 服务端注册表(§3.4 允许调用方提供,roadmap 偏差在此登记)。
- 不改 triggerIncrementalIndex 签名;不收紧 allowedLocalRoot(N6.5)。

## Scope

### In Scope

- nop-code-api:`RebuildFromCommitResult`
- nop-code-service:CodeIndexService 触发逻辑+去抖+git 辅助、`NopCodeErrors` 新 ErrorCode、BizModel action、invariant 测试同步
- 测试:`TestRebuildFromCommit`(git 夹具)
- owner docs ×3

### Out Of Scope

- watch/webhook/远端同步/nop-job 适配器;allowedLocalRoot 收紧(N6.5)。

## Execution Plan

### Phase 1 - mutation、去抖与 git 校验

Status: planned
Targets: `nop-code-api/dto/RebuildFromCommitResult`、`ICodeIndexService`、`CodeIndexService`、`NopCodeErrors`、`NopCodeIndexBizModel`、invariant 测试

- Item Types: `Fix`

- [x] DTO(4 字段)
- [x] ErrorCode 登记(NopCodeErrors:git 校验失败/HEAD 不一致/非仓库根)
- [x] `triggerRebuildFromCommit`:去抖(拒绝式,`@InjectValue("@cfg:nop.code.rebuild.debounce-millis|30000")` setter 注入,public setDebounceMillis(0) 测试归零(R2 A3:跨包可见性))→ validateGitRef ×2 → projectPath 归一+仓库根校验 → HEAD==target 校验 → git diff --name-only(30s 超时)→ 短路或 triggerIncrementalIndex(指纹 no-op 时 statusMessage 注明"工作树与索引已同步"——R2 A2)→ IncrementalStatus(mode="rebuild")
- [x] BizModel mutation(admin)
- [x] invariant:IDEMPOTENCE_TABLE + verify 分支(setDebounceMillis(0) 后重放断言 0 变更)
- [x] 集成测试 `TestRebuildFromCommit`:git 夹具(assumeTrue git 可用;commit 用 -c user.name/-c user.email;变更后 sleep 50)——①commit2 后触发 changedCount>0 且物化行失效;②重放同参数 resetDebounce 后 0 变更;③连发第二次 debounced=true;④无变更仓库 skippedNoChanges;⑤HEAD!=target 抛错;⑥子目录 projectPath(非仓库根)抛错

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 集成测试全绿(含去抖/幂等/HEAD 校验)
- [x] invariant 门禁全绿(新方法已分类)
- [x] **无静默跳过**:git 失败/HEAD 不一致显式抛错;去抖为拒绝式显式返回
- [x] Owner-doc(Phase 1 内完成):query-api-design §4.1 增行、graph-discovery §3.4 状态+未决项裁定回写(manifest 复用证伪→新裁定;去抖=拒绝式;签名/源码来源语义——R2 A4)、缺口矩阵 N2.4 行
- [x] `check-doc-links --strict` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] mutation 与去抖/幂等/HEAD 校验落地且有测试钉住
- [x] 必要 focused verification 完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**:closure audit 验证(a)GraphQL→mutation→增量管线端到端,(b)去抖真实生效,(c)git 失败/HEAD 不一致快速抛错
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

- **远端 clone/fetch 与 repo→indexId 注册表**:projectPath 须为服务端已有工作区;远端同步与注册表归外部适配器(§3.4 触发源表中的 webhook receiver/nop-job 均为外部组件;§3.4 允许"调用方提供 indexId")。roadmap Deliverable 的"repo→indexId 注册表"与"e2e"偏差在此登记——e2e 以 localDb 集成测试承载(先例 plan 07-09)。
- Classification: `moved to explicit successor ownership`
- Successor Required: `yes`
- Successor Path: 平台集成层适配器(非 nop-code 索引服务职责)

## Non-Blocking Follow-ups

- 去抖为 per-node 内存状态(重启清零/多节点不共享)——§四否决 watch daemon 的同一无状态张力,集群语义归 N6.3/N6.4。

## Closure

Status Note: (待 closure audit 后填写)
Completed: (待填)

Closure Audit Evidence:

- Reviewer / Agent: (待独立子 agent closure audit 后填写)
- Evidence: (待填)
