# 2365 full-code-review R1.2 — nop-file-app/nop-credential-app 半拆命运裁决（P1-1.8-001）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.2 行；finding `P1-1.8-001`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.8-integration-periphery-audit.md`）
> Related: —（无 design doc 需求：本计划为裁决 + 文档声明 + 注记，不引入新架构）

## Purpose

对 nop-file-app / nop-credential-app「可启动、无 HTTP 端点」半拆（P1-1.8-001）做出唯一命运裁决并落地：审计预授权的两条路（恢复 start()+web-orm-starter / 正式休眠+文档声明+降 P2）中裁定其一，消除「公开面指向可部署 HTTP 服务而依赖树无 web 扩展」的误导，并清理坏模板传播面（credential-app 对已坏 file-app 主类的复制）。

## Current Baseline

- 两 app 主类 `nop-file/nop-file-app/.../NopFileApplication.java` 与 `nop-credential/nop-credential-app/.../NopCredentialApplication.java` 的 `start()` 中 `QuarkusIntegration.start()` 均为裸注释（c7ea7b6836 2025-12-05 大提交顺手注释且无说明；credential-app 主类系 1d12af9dcb 对已坏 file-app 主类的直接复制——坏模板传播已兑现）。
- `nop-file/nop-file-app/pom.xml` 中 `nop-quarkus-web-orm-starter` 依赖被注释；`nop-credential/nop-credential-app/pom.xml` 无该依赖（连注释都没有）。依赖树两 app 均无任何 quarkus web/REST 扩展。
- 后果（1.8 审计实证）：app 不崩溃但 CDI 桥父容器缺省、quarkus.profile→nop.profile 传播丢失、MeterRegistry 静默回落、无 HTTP 端点；application.yaml（auth/jwt/orm/graphql 配置）与 `nop-credential.md`「standalone nop-credential-app」段落的部署语义讨论构成误导公开面。
- 审计预授权降级路径原文出自 1.8 审计报告 P1-1.8-001 行：「降 P2 触发条件已附（正式裁决休眠并文档声明）」；roadmap R1.2 行原文「恢复 start()+补 web-orm-starter，或正式休眠+文档声明+降 P2；含坏模板 C093 传播面清理」，修复方向列（审计报告）另要求「修复需含 web-orm-starter 注释裁决」。
- 复活路径坐标真实存在：`nop-quarkus/nop-quarkus-web-orm-starter/` 模块在仓；`QuarkusIntegration` 实为 `io.nop.quarkus.core.QuarkusIntegration`（nop-quarkus-core-starter），两主类当前 import 已被移除（姊妹 app 如 nop-datav-app 均带此 import）——复活须先恢复 import 再取消注释。
- 两 app pom `:16` 均有「该模块为独立服务包…」注释（审计点名的误导公开面三面之一：pom「独立服务包」+ yaml + nop-credential.md:231）。
- 两 app 无测试目录（装配壳模块）；行为验证依赖其服务的模块自身测试。

## Goals

- 裁决落地：**正式休眠（Option B）**——两 app 不是可部署 HTTP 服务；恢复为明确文档化的休眠态（非默默损坏）。
- 裸注释全部消除：代码注释、pom 注释均带裁决语义（为何休眠 + 复活三步路径）。
- owner doc 同步：module-groups.md 声明 + nop-credential.md standalone 段勘注。
- P1-1.8-001 以休眠裁决方式收口（审计预授权路径）。

## Non-Goals

- 不恢复 QuarkusIntegration.start()，不补 web-orm-starter 依赖（复活属独立特性工作，须重新验证 quarkus-web 全栈）。
- 不删除两 app 模块（roadmap 选项集为「恢复/休眠」，删除超范围）。
- 不改 application.yaml 配置面（休眠态下配置惰性，由文档声明覆盖；删配不是本裁决必需）。
- 不处理 quarkus-web-starter 聚合面（P2-1.8-001 独立条目，属 P2 台账通道）。

## Scope

### In Scope

- `NopFileApplication.java` / `NopCredentialApplication.java`：裸注释 → 裁决注记（休眠原因 + 复活三步：取消注释 start()、恢复 web-orm-starter 依赖、重验证 quarkus-web 栈）。
- `nop-file-app/pom.xml`：被注释的 web-orm-starter 依赖块补裁决注记。
- `docs-for-ai/01-repo-map/module-groups.md`：nop-file / nop-credential 两处的 -app 休眠声明（可复用业务模块节表行注记）。
- `docs-for-ai/03-modules/nop-credential.md`：standalone 部署语义段补休眠勘注；`docs-for-ai/03-modules/nop-file.md` 如有同类 standalone 语义则同批勘注。

### Out Of Scope

- 行为变更、依赖变更、配置变更（零行为差异是本裁决的验证基线）。
- application.yaml 收敛、quarkus-web-starter 聚合面、CLI 版本硬编码等其他 1.8 发现。

## Execution Plan

### Phase 1 - 休眠裁决注记（代码 + pom）

Status: completed
Targets: 两个 app 主类、两个 app pom（`nop-file/nop-file-app/pom.xml`、`nop-credential/nop-credential-app/pom.xml`）

- Item Types: `Fix`（误导公开面勘注——已确认 live defect 的修复动作）+ `Decision`（命运裁决本体）

- [x] 两主类裸注释 `// QuarkusIntegration.start();` 替换为裁决注记：休眠裁定 + 日期 + 修复工作项号（R1.2/plan 2365）+ 复活路径（①恢复 `io.nop.quarkus.core.QuarkusIntegration` import；②取消注释 start() 调用；③恢复 `nop-quarkus-web-orm-starter` 依赖并重验证 quarkus-web 栈）。注记与被注释调用同行/同块（验证允许 `git grep -B2 -A2` 上下文形式）
- [x] 两 app pom `:16`「该模块为独立服务包…」注释勘注：休眠态下不构成独立 HTTP 服务包（点名自 P1-1.8-001 误导公开面）
- [x] `nop-file-app/pom.xml` 被注释的 `nop-quarkus-web-orm-starter` 依赖块（:23-26）补同源裁决注记

Exit Criteria:

- [x] 两主类与两 pom 中不再存在无说明的裸注释：`git grep -n "QuarkusIntegration.start" nop-file nop-credential` 与 `git grep -n "独立服务包" nop-file nop-credential` 的命中处均带裁决说明（同行或紧邻上下文块——两 pom :16 原注释行经 :17-18 勘注块紧邻覆盖）
- [x] 编译无影响：`./mvnw compile -pl nop-file/nop-file-app,nop-credential/nop-credential-app -am` 成功（纯注释变更，编译为回归确认；Maven 4.0.0-rc-5 逗号路径写法已由 plan 审查实测可行）
- [x] **无静默跳过**（Rule #24）：不适用——零行为变更，无新增方法/分支
- [x] No owner-doc update required: 不适用——owner doc 更新是 Phase 2 的交付物本体
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 休眠声明

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/03-modules/nop-file.md`（如适用）

- Item Types: `Fix`（误导公开面勘注）+ `Decision`（裁决的文档承载）

- [x] `module-groups.md` §2.5「可复用业务模块」表（9 行表，`nop-file/` 在 :62）：`nop-file/` 表行补「-app 休眠」声明；**新增 `nop-credential/` 表行**（该模块组当前在本文档零提及）——用途一句话 + 「-app 休眠态：不是可部署 HTTP 服务」声明；两处声明口径一致并指向模块内复活注记
- [x] `nop-credential.md` 「Web 授权编辑」段 standalone 部署语义处（:231「共部署语义」句）补勘注：nop-credential-app 当前为休眠态，页面/编辑器语义面向嵌入宿主或复活后的 app
- [x] `nop-file.md` 检索 standalone/独立部署语义：**无同类表述**（standalone/独立/file-app 三关键词零命中，命中「无」分支，无需勘注）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] 三份 owner doc 的休眠声明可在 live 文档中直接读到且彼此口径一致（module-groups 两行 + credential.md 勘注；nop-file.md 记录无同类表述）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口

Status: completed
Targets: roadmap R1.2 行、轮次 index.md、本 plan

- Item Types: `Proof`

- [x] P1-1.8-001 处置结论回填：index.md P1 汇总行 → `fixed`（沿用词表；摘要注明「audit 预授权『降 P2 触发条件』已由休眠裁决行使且无残余误导公开面」，附 plan 号）+ index MR 表 R1.2 行登记
- [x] roadmap R1.2 行 Status → done（含裁决结论一句话）
- [x] 文本一致性核对：plan Status / Phase Status / Exit Criteria / Closure Gates / daily log 五处一致

Exit Criteria:

- [x] roadmap 与 index 两处回填彼此一致且与 plan 结论一致
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 命运裁决唯一且落地：正式休眠（Option B），裸注释全部变为裁决注记
- [x] 行为零差异：两 app 主类仅注释变更（git diff 证实无代码语义变化）
- [x] owner docs 已同步：module-groups.md（nop-file 行勘注 + nop-credential 新行）+ nop-credential.md 勘注 + nop-file.md「无同类表述」记录；三处休眠声明口径一致
- [x] focused verification：两模块编译通过；doc-links --strict 0 errors
- [x] No new test required: 纯注释与文档变更，行为零差异，无可测新行为；hollow scan 不适用（两 app 为合法装配壳，零方法变更）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（P1 以审计预授权的休眠路径收口——「降 P2 触发条件已附（正式裁决休眠并文档声明）」行使且误导公开面三面全部勘注，非降级掩盖）
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] `./mvnw compile -pl nop-file/nop-file-app,nop-credential/nop-credential-app -am` 成功
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### 复活两 app 为 HTTP 服务

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 休眠是审计预授权的合法终态（非缺陷遗留）；复活属特性工作且需全栈重验证，无当前需求信号
- Successor Required: `no`（复活路径已在代码注记 + owner doc 中文档化，无既存 successor plan）

## Non-Blocking Follow-ups

- application.yaml 配置面收敛（休眠态下惰性；如未来复活时一并重审）
- P2-1.8-001 quarkus-web-starter 聚合面裁决（按 P2 通道另行裁决；现无既存台账行，非本计划交付物）

## Closure

Status Note: P1-1.8-001 命运裁决落地为正式休眠（audit 预授权路径）：两主类裁决注记（含复活三步）+ 两 pom 误导注释勘注 + owner doc 三处声明（module-groups §2.5 nop-file 行勘注/nop-credential 新增行/:16 枚举同步、credential.md standalone 勘注、nop-file.md 无同类表述记录）；行为零差异（diff 全为注释行）；误导公开面三面全部处置，无残余 live defect。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_c17cd54d-3cb4-4cdd-b5f1-7cda9d1d529c
- Evidence:
  - **裁决落地**：PASS——两主类 :23-26 裁决注记（休眠裁定+日期+R1.2/plan 2365+复活三步①import ②start() ③web-orm-starter），QuarkusIntegration 保持注释态、import 未恢复；复活坐标 io.nop.quarkus.core.QuarkusIntegration 实存 nop-quarkus-core-starter。
  - **行为零差异**：PASS——git diff 亲读：全部新增行均为注释行（主类 +3/文件、file-app pom +5、credential-app pom +2），零非注释行变更。
  - **误导公开面三面**：PASS——pom「独立服务包」两处勘注（:17-18 紧邻覆盖 :16）+ file-app web-orm-starter 块勘注（:25-26）+ credential.md :231 勘注；yaml 由 plan Non-Goals 显式裁定（惰性配置+文档声明覆盖）。
  - **owner doc 一致**：PASS——module-groups :62/:63 两行口径一致 + :16 枚举同步（auditor advisory 顺带修复）；credential.md 勘注在位；nop-file.md standalone/独立/file-app 零命中（「无同类表述」成立）。
  - **focused verification**：PASS——auditor 独立复跑 compile（exit 0）+ check-doc-links --strict（exit 0，0 errors）。
  - **状态回填一致**：PASS——roadmap R1.2 done 行 / index P1-1.8-001 fixed 行（沿用词表+预授权行使注明+plan 号）/ index MR 表 R1.2 行 / daily log R1.2 条目，四处与 plan 结论一致。
  - **坏模板传播面**：PASS——全仓 grep QuarkusIntegration.start：仅两目标 app 注释态，其余 20+ 姊妹 app 均活跃调用——传播面恰为两 app 且均已勘注。
  - **Deferred 分类**：PASS——复活=out-of-scope improvement 诚实（休眠为预授权合法终态）；无 in-scope live defect 被降级。
  - Advisory 处置：① Closure 回写（本段）② 前向引用由本审计 PASS 批准（先例同 plan 2364）③ module-groups :16 枚举滞后（已修）④ plan 内相对链接 2 条 warning（记录性，不改）⑤ yaml 无文件内休眠指针（Non-Goals 裁定覆盖，复活时重审）。

Follow-up:

- 复活两 app 为 HTTP 服务：out-of-scope improvement（路径已文档化，无 successor plan）
- application.yaml 配置面收敛：复活时一并重审；P2-1.8-001 聚合面按 P2 通道另行裁决
