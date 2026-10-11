# 2429 WI4 删除 legacy `.workflow/*` + docs JDK 表述同步

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI4(mission jdk25-sb4-q4,第五个执行项;WI0-WI3 已 done)
> Related: `ai-dev/plans/2428-wi3-github-actions-jdk25-lint-gate.md`(CI 已 consolidated 到 `.github/workflows/`,本 WI 是其删除侧收尾)
> Draft Review: round 1 对抗审查(独立子代理 agent_59d7a6b0,2026-10-11)2 Major(bootstrap-new-application.md:15 表格分隔符形态的第 5 锚点、invariant-guards.md:72 须连 Node 20 一并同步)+ 3 Minor,全部已修,裁定可执行。

## Purpose

CI 入口收敛到 GitHub Actions:删除 3 个 legacy `.workflow/*` pipeline(JDK 11/8/8,已无存在意义),并把 `docs-for-ai`/`AGENTS.md` 的 JDK 表述同步到 25 基线。

## Current Baseline

- WI3(eb89ec8b14)后 `.github/workflows/` 6 处 java-version 全 25;`.workflow/` 仍存 3 个文件:`master-pipeline.yml`(jdkVersion 11 / Maven 3.6.3)、`pr-pipeline.yml`(jdkVersion 8 / 3.3.9)、`branch-pipeline.yml`(jdkVersion 8 / 3.3.9)——live 核实。
- `.workflow/` 引用面:全仓仅本 mission 自身文档(roadmap/WI0 分析/plan 2422/2428)以"删除目标"语义提及,无功能性引用(live grep 核实)。
- JDK 表述待同步点(live 核实):
  - `docs-for-ai/00-start-here/project-context.md:12`:"构建用 JDK 17+,编译目标 `maven.compiler.release=17`"
  - `AGENTS.md:7`:"**Tech Stack**: Java 21, ..."
  - `AGENTS.md:216`:"Install JDK 21 first."(Windows setup)
  - `docs-for-ai/02-core-guides/invariant-guards.md:72`:"limit 测试依赖 JDK 21"(WI3 audit advisory B;作为下限语义在 25 下仍真,但应同步为当前基线表述)
- 保留不动:`docs-for-ai/01-repo-map/module-groups.md` 等对 nop-rg/nop-find "JDK 22+ 门控模块"的描述(java22-modules reactor 纳入门控是有意设计,WI2 已裁定保留);`module-groups.md:33-34` 属此类。

## Goals

- `.workflow/` 目录整体删除(3 文件)。
- 5 处 docs JDK 表述同步到 25 基线(docs-for-ai/ 与 AGENTS.md 范围内;README 类仓外文档见 Non-Goals)。

## Non-Goals

- java22-modules 门控 profile 及其文档描述(有意设计,保留)。
- GitHub workflows 内容(已由 WI3 完成)。
- 历史 plan/日志/分析中的旧表述(过程记录,不回写)。
- 根 `README.md`(:138/:146 "JDK 17+")、`nop-entropy-e2e/README.md:30`("Java 21")、`nop-kernel/README.md:70`、`nop-stream/quickstart/README.md:21` 等 docs-for-ai 之外的 README 表述——不在 roadmap WI4 Deliverables 内,登记 Non-Blocking Follow-ups。
- `project-context.md:17` 的 Active Work 旧计划列表(非 JDK 表述,非本 WI 职责)。

## Scope

### In Scope

- 删除 `.workflow/master-pipeline.yml`、`pr-pipeline.yml`、`branch-pipeline.yml`。
- 4 处 docs 表述更新(上列锚点)。

### Out Of Scope

- 其他 docs 的 JDK 提法(如模块 JDK 门控描述);代码/pom(WI1/WI2 已完成)。

## Execution Plan

### Phase 1 - 删除与表述同步

Status: completed
Targets: `.workflow/`(3 文件)、`project-context.md:12`、`AGENTS.md:7,216`、`invariant-guards.md:72`

- Item Types: `Fix`

- [x] `git rm .workflow/master-pipeline.yml .workflow/pr-pipeline.yml .workflow/branch-pipeline.yml`
- [x] project-context.md:12:JDK 17+/release=17 → JDK 25/release=25
- [x] AGENTS.md:7:Java 21 → Java 25
- [x] AGENTS.md:216:Install JDK 21 → Install JDK 25
- [x] invariant-guards.md:72:limit 测试 "JDK 21" → 25,且同句 "Node 20" → Node 22(live maven.yml node-version=22;完成 WI3 advisory B 完整移交)
- [x] `docs-for-ai/03-runbooks/bootstrap-new-application.md:15`:表格行 "JDK | 17+(推荐 21,与 CI 一致)" → "JDK 25+(与 CI 一致)"——表格竖线分隔形态,escape 了平面 grep,审查抓获的第 5 锚点
- [x] No new test required:删除 + 文档表述,无行为面

Exit Criteria:

- [x] `.workflow/` 目录不存在(`ls .workflow` 报错)
- [x] 双判据:①`grep -rn "release=17\|JDK 17\|Java 21\|JDK 21" docs-for-ai/ AGENTS.md` **零命中**(module-groups 的门控描述用 "JDK 22+\|release=22\|java21-modules" 形态,天然不匹配该 pattern);②弹性分隔符宽扫(如 `grep -rniE "jdk[ |:]*1[78]|java[ |:]*21|推荐 21" docs-for-ai/ AGENTS.md`)人工逐条核对仅剩合法保留(第三方语义/历史记录)
- [x] `ai-dev/logs/` 条目已更新
- [x] **执行期追加(审查外发现)**:`docs-for-ai/01-repo-map/module-groups.md:33-34` 的 "nop-rg-core/nop-find-core(`maven.compiler.release=22`)" 为 WI2 删 pin 后的 doc 漂移——修正为"编译级别随根 pom release=25(原 release=22 pin 已由 plan 2427 收编),reactor 纳入门控保留";`java21-modules`/`JDK 22+ 门控` 表述保留(门控仍 [22,))

### Phase 2 - 收口

Status: completed
Targets: roadmap、本 plan、日志

- Item Types: `Proof` / `Decision`(roadmap 回写)

- [x] 独立子代理 closure audit(fresh session),证据写入 Closure 节
- [x] roadmap WI4 `todo` → `done`(仅此一处)
- [x] `check-plan-checklist.mjs --strict`、`check-doc-links.mjs --strict` 退出码 0
- [x] 严格 pathspec 提交
- [x] `scan-hollow-implementations.mjs`:N/A(删除+文档)

Exit Criteria:

- [x] audit 证据入 plan
- [x] roadmap WI4 = done 且其余未动
- [x] 两工具退出码 0
- [x] 日志收口条目在档

No owner-doc update required beyond Phase 1(表述同步即 owner doc 更新本体)。

## Closure Gates

- [x] `.workflow/` 删除且无功能性引用残留
- [x] docs JDK 表述同步 4 处完成、grep 判据通过
- [x] 独立子代理 closure audit PASS 且证据入 plan
- [x] roadmap WI4 回写 done 且其余未动
- [x] 两 --strict 工具退出码 0

## Deferred But Adjudicated

无 deferred 项。

## Non-Blocking Follow-ups

- docs-for-ai 之外的 README JDK 表述同步(根 README.md、nop-entropy-e2e/README.md、nop-kernel/README.md、nop-stream/quickstart/README.md)——不在 roadmap WI4 范围,登记待后续 WI 或 docs 清理批处理。

## Closure

Status Note: `.workflow/` 三 pipeline 删除(CI 入口唯一收敛 .github/workflows/);6 文件 8 位点 JDK/Node 表述同步 25(含审查抓获的第 5 锚点与 WI2 遗留的 module-groups release=22 漂移);双 grep 判据绿;closure audit round 1 FAIL(Phase 1 状态头未翻转,单项)→ 即修 → 按审计裁定其余各条放行。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_1d36c39c-4956-4bb5-bb3e-a37e11422e10(fresh session,只读)
- Evidence:
  - 删除:目录不存在、3 文件 staged D、全仓零功能性引用(同名异 concept 误报已排除)
  - 表述:6 锚点 diff 逐一核对通过,与 live pom 交叉验证(java22-modules [22,) 门控在档)
  - 双 grep:严格 pattern 零命中;弹性宽扫仅剩 java21-modules profile 名(合法保留,live 实证)
  - diff 圈定无夹带;README 类登记于 Non-Goals/Follow-ups(非静默降级)
  - audit round 1 单项 FAIL(Phase 1 状态头)已修复,审计明示其余放行
  - 工具:check-plan-checklist --strict exit 0、check-doc-links --strict exit 0(收口复跑);scan-hollow 不适用(删除+文档)
- Follow-up:
  - docs-for-ai 之外 README JDK 表述(根 README/e2e/nop-kernel/quickstart)已登记 Non-Blocking Follow-ups
  - WI5 按序(Quarkus 4.0.0.Beta1 unification)
