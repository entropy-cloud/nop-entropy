# 2453 WI16 孤儿模块处置裁定（shell / maven / dsl-orm）

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI16；三模块普查 = 2026-10-13 live HEAD（plan 2452 后）
> Draft Review: 2026-10-13 独立子代理对抗审查 round 1（agent_484a1ac5）——2 Major（10 文件拆分 6+4 非 7+3——VfsMavenUsageExampleRunner 在 test 源根 / nop-bom 无 maven 条目系幽灵消费者）+ 4 Minor，全部修入正文后转 active；方向性裁定（三 keep/move 定位、菜单一致性、A2 退役否决理由）live 核查成立
> Related: `ai-dev/backlog/nop-ai-shell-bash-cst-frontend-roadmap.md`（active，shell 处置的外部约束）；WI25（agent-refactor roadmap，completed——shell `&` 已接 BackgroundRegistryJobSink）；nop-dyn-service（dsl-orm 唯一平台消费者）

## Purpose

完成 roadmap WI16 三模块处置裁定：shell（维持现状——CST roadmap 约束下不得单方面处置）、maven（并入 toolkit fs 域——二选一菜单的非退役分支）、dsl-orm（维持现状登记重启条件——直接退役被 roadmap 排除）。

## Current Baseline

> 2026-10-13 live HEAD 实测。

- **shell**：`nop-ai-shell` 模块；唯一生产引用 = `nop-ai-agent` pom 的 **test scope** 依赖（pom :28-36 依赖块（注释 :29-33）自证 "no production consumer (orphan module, plan 2301 Known Residual)"，且注明 WI25 的 BackgroundRegistryJobSink 桥接 shell `&` 任务到 background registry——**该桥接使 shell 不再是启动即失联**）。shell-cst roadmap（`nop-ai-shell-bash-cst-frontend-roadmap.md`，active）仍有在途投入；roadmap 明文"裁定必须与 CST roadmap 负责人/用户确认，不得单方面处置"。消费者面：nop-ai/pom.xml（模块表）、nop-bom、nop-ai-agent（test scope）。
- **maven**：`nop-ai-maven` 模块，10 文件（main 6：DeltaVirtualFileSystem/DeltaWorkspaceReader/DeltaWorkspaceReaderConfigurator/ArtifactInfo/VfsMavenCli/NopAiMavenErrors + test 4：TestVfsMavenCli/VfsMavenUsageExampleRunner〔main()-only 手动示例〕/DeltaVirtualFileSystemTest/DeltaWorkspaceReaderTest）；**`io.nop.ai.maven` 包外 java 消费者 = 0**（round 1 复核 + 本日复测一致）。pom 消费者：仅 nop-ai/pom.xml:37（模块表）——**nop-bom 无此条目**（bom 仅含 dsl-orm :1379 / shell :1437）。error-handling.md 英文模块族清单含 `NopAiMavenErrors`。
- **dsl-orm**：`nop-ai-dsl-orm` 模块，6 java 文件（另有 `_vfs/nop/schema/gpt/orm.xdef` + 1 test resource，维持现状下不迁移；GptOrmConstants/GptOrmModelParser/GptOrmErrors/GptOrmSqlType + test 2）。**唯一消费者 = `nop-dyn/nop-dyn-service`**（pom :86 compile 依赖 + `GptCodeGen.java:11` import `GptOrmModelParser`）。与 coder 存在重复面（GptOrmSqlType vs AiOrmSqlType 等）。roadmap 明文"直接退役会编译打破 nop-dyn 构建，排除"；三选项菜单含"维持现状登记重启条件"。
- **roadmap Rules 约束**：纯裁定项允许"维持现状"清单 = shell/dao/gateway/app——**maven 与 dsl-orm 不在清单**：maven 必须二选一（并入 toolkit fs 域 / 退役）；dsl-orm 三选项含维持现状。

## Goals

- shell：维持现状裁定 + 登记依据（CST active + WI25 桥接 + test scope 隔离已正确）与重启条件。
- maven：10 文件迁入 `nop-ai-toolkit`（fs 域新子包），`nop-ai-maven` 模块解散（nop-ai/pom + nop-bom 条目移除）；零行为变化。
- dsl-orm：维持现状裁定 + 登记依据与重启条件（合并 successor 联动 nop-dyn-service 依赖迁移）。
- 模块计数登记：nop-ai 子模块 -1（maven 解散）。

## Non-Goals

- 不退役任何已发布 artifact（maven 退役分支被否决，见 A2）。
- 不动 shell 代码与 CST roadmap 在途项。
- 不做 dsl-orm 与 coder 的去重合并（重启条件 successor）。
- 不改三模块的既有错误码 ID。

## Adjudications

### A1 shell 维持现状（roadmap 授权的必确认项——本轮裁定 = 登记现状为裁定结论）

- 依据：①CST roadmap active（不得单方面处置的明文约束）；②WI25 已为 shell `&` 语义接通 BackgroundRegistryJobSink 闭环（启动即失联缺陷已修，shell 是活跃演进面而非死代码）；③隔离形态已正确（agent test scope，不泄漏生产依赖图）。
- 重启条件：CST roadmap 收口（bash parser 前端交付完毕）后，按其最终形态复评"纳入正依赖图 / 降级 / 退役"三选一——该复评须 CST roadmap 负责人/用户确认（roadmap 明文约束；本 plan 仅登记现状为裁定结论，非处置）。

### A2 maven 并入 toolkit fs 域（二选一菜单：否决退役分支）

- 依据：零 java 消费者 + roadmap 二选一菜单；**退役分支否决理由**：`nop-ai-maven` 是已发布 artifact（io.github.entropy-cloud，Maven Central），删除 artifact 属跨版本用户可见破坏，超出本 roadmap 边界；并入 toolkit 保留能力、消除孤儿模块、消灭"文档记载但孤儿"的 misleading 状态。
- 落点：`nop-ai-toolkit` 新子包 `io.nop.ai.toolkit.mavenvfs`（fs 域 = roadmap 指定域；VfsMavenCli/DeltaVirtualFileSystem 为 VFS 工具面）。`NopAiMavenErrors` 随迁（错误码 ID `nop.err.ai.maven.*` 不变，error-handling.md 归属行更新）。
- 重启条件：无（能力保留，模块孤儿状态消除）。

### A3 dsl-orm 维持现状（三选项菜单第三项）

- 依据：唯一消费者 nop-dyn-service compile 依赖在位（删除被 roadmap 排除）；合并入 coder 须联动 nop-dyn-service 依赖迁移论证（平台模块依赖 coder 的合理性）——属独立 successor 而非本 WI 顺手可做。
- 重启条件：nop-dyn-service 的 GptCodeGen 退役或迁移时，dsl-orm 一并评估并入 coder。

## Execution Plan

### Phase 1 - maven 并入 toolkit fs 域

Status: completed
Targets: nop-ai-toolkit（新 mavenvfs 包）、nop-ai/pom.xml、nop-bom/pom.xml、docs（module-groups/nop-ai.md/error-handling.md）

- Item Types: `Fix`

- [x] `git mv` 10 文件（main 6 + test 4，test 文件落 test 源根——VfsMavenUsageExampleRunner 为 main()-only 手动示例不得入生产 jar）→ `nop-ai-toolkit/src/{main,test}/java/io/nop/ai/toolkit/mavenvfs/{vfs,config,cli,examples}/`（物理子目录与原包结构一致，closure audit round 1 纠偏扁平错位）；包声明 `io.nop.ai.maven.{vfs,config,cli,examples}` → `io.nop.ai.toolkit.mavenvfs.*`；全部 import 更新
- [x] `nop-ai-maven` 模块解散：nop-ai/pom.xml:37 模块表移除 + 目录删除（nop-bom 无此条目，无需改动）
- [x] 编译 + toolkit 测试：`./mvnw test -pl nop-ai/nop-ai-toolkit -am` 全绿（含随迁 mavenvfs 测试）
- [x] owner docs：module-groups（:17 叙述 + :110 模块表行更新）、error-handling.md（:120 NopAiMavenErrors 归属改 toolkit）、nop-ai.md 模块表**新增** maven 解散 dissolution 行（仿 :61 WI15 空名行先例——现表无 maven 行）

Exit Criteria:

- [x] grep `io.nop.ai.maven` 在 src 零残留；`nop-ai-maven` artifact 零引用（pom/bom/docs 活性引用）
- [x] toolkit `-am` 全绿
- [x] No new test required：纯迁移；4 个随迁测试类为行为基线（guide Rule #25 注记）
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - shell/dsl-orm 裁定登记与收口

Status: completed
Targets: roadmap、log、plan closure

- Item Types: `Decision` + `Proof`

- [x] A1/A3 裁定登记（roadmap WI16 状态行括注 + 本 plan Closure）；模块计数登记（nop-ai 子模块 -1）
- [x] roadmap `## Work Item Status` WI16 → `done`；状态块解析核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2453-wi16-orphan-module-adjudication-shell-maven-dslorm.md --strict` 0
- [x] pathspec 限定单一原子提交（Phase 1+2 同 commit）

Exit Criteria:

- [x] roadmap 更新后解析核对通过（21 行 WI 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：三裁定摘要、验证、A1-A3、commit hash、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] maven 并入 toolkit 落地（10 文件新坐标、模块解散、pom/bom 零残留）
- [x] shell/dsl-orm 维持现状裁定登记（依据 + 重启条件）
- [x] 行为零变化：toolkit `-am` 全绿
- [x] 模块计数登记（nop-ai 子模块 -1）
- [x] 不存在被静默降级的 in-scope live defect（A1-A3 为显式裁定）
- [x] owner docs 已同步：module-groups / nop-ai.md / error-handling.md
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2453-wi16-orphan-module-adjudication-shell-maven-dslorm.md --strict` 退出码 0

## Deferred But Adjudicated

### dsl-orm 并入 coder（A3 重启条件）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 唯一消费者在位（删除被排除）；合并需联动 nop-dyn-service 依赖论证，独立 successor 更安全
- Successor Required: `no`（GptCodeGen 退役/迁移时触发）

### shell 三选一复评（CST roadmap 收口后）

- Classification: `watch-only residual`
- Why Not Blocking Closure: CST active + WI25 桥接修复后 shell 为活跃演进面；本轮登记现状为裁定结论
- Successor Required: `no`（CST roadmap 收口触发复评）

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: 三裁定落地：maven 并入 toolkit mavenvfs（10 文件 6+4，物理子目录与包声明一致，模块解散、退役分支因已发布 artifact 否决）；shell 维持现状登记（CST active + WI25 桥接后为活跃演进面，复评须 CST owner/用户确认）；dsl-orm 维持现状登记（唯一消费者在位，合并 successor 联动依赖迁移）；nop-ai 子模块 -1；行为零变化 34/0 mavenvfs 绿 + toolkit -am 302 绿。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（agent_f034bea7，fresh session，2026-10-13）
- Evidence:
  - round 1：6/8 PASS——REJECT：P1（mavenvfs 物理扁平与 package 子包声明错配，git mv 入扁平目录所致）+ P2（doc-links 1 error：本 plan 删 README 致 build-scheduling.md:112 断链）+ P3/P4 Minor
  - 解除动作：文件移入 mavenvfs/{vfs,config,cli,examples}/ 物理子目录（34/0 复测绿）；build-scheduling.md 引用改指 toolkit mavenvfs；NopAiMavenErrors javadoc 措辞更新；nop-ai-maven 目录壳删除
  - 复核：check-doc-links --strict 0 error；focused 34/0 绿；全仓 `io.nop.ai.maven` 零残留
  - 裁定：**APPROVE closure**（P1/P2 修复 + 双门禁复核）
- `node ai-dev/tools/check-plan-checklist.mjs 2453-wi16-orphan-module-adjudication-shell-maven-dslorm.md --strict` 退出码 0（closure 前末次确认）
