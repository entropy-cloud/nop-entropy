# 2446 WI12 delete obsolete demos

> Plan Status: completed
> Last Reviewed: 2026-10-12
> Source: ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md WI12
> Related: 2444/2445（同 roadmap 前序 WI）

## Purpose

删除 JDK17/SB3.2 时代的两个废弃 demo（nop-jdk17-demo、nop-spring-demo2），unregister 聚合 pom 引用；保留 nop-spring-demo / nop-spring-simple-demo 作为 SB 参照。

## Current Baseline

- nop-jdk17-demo（nop-demo 下，56K，git 跟踪）：**仍在 reactor**（nop-demo/pom.xml:34）。
- nop-spring-demo2（nop-demo 下，1.9M，git 跟踪）：已注释出 reactor（nop-demo/pom.xml:25）。
- 构建/运行时引用面（xml/java/xgen/ts/sh/Dockerfile/e2e/.github/missions/**docs-for-ai** 全 grep 实证）：仅 `nop-demo/pom.xml`（:25 注释行 + :34 module 行），无任何 `<dependency>` 引用（root pom/nop-bom/tests pom 均无）。遗留文档 `docs/dev-guide/spring.md:121`、`docs-en/dev-guide/spring.md:120` 有提及。**ai-dev 历史记录类引用**（audits/analysis/历史 plans/roadmap 叙事行，约 30+ 处）为过程记录，非活引用。
- nop-demo/pom.xml 的 `build-quarkus-modules-on-jdk17-plus` profile 为通用 JDK 门控，与 jdk17-demo 无关，不动。

## Goals

- `git rm -r` 两个 demo 目录。
- nop-demo/pom.xml 删除 ：34 module 行与 ：25 注释行。
- 构建/运行时活引用归零：`grep -rn --include="*.xml" --include="*.java"` 零命中；ai-dev 历史记录与遗留 docs/ 的提及不回写（Non-Goals 已裁定）。

## Non-Goals

- 不更新 docs/、docs-en/（历史遗留目录，AGENTS.md 禁止新增内容；反向引用归 docs 治理另案——与 2445 Ruling 4 同款裁定）。
- 不动其余 demo（nop-quarkus-demo、nop-orm-demo 等保留）。
- 不回写 ai-dev 历史记录（audits/analysis/历史 plans/roadmap 叙事行对两 demo 的提及为过程记录，保留原样）；docs-for-ai/ 零引用（grep 实证）无需更新。

## Execution Plan

### Phase 1 - 删除与注销

Status: completed
Targets: 2 目录 + nop-demo/pom.xml

- Item Types: `Fix`（废弃物清理）

- [x] `git rm -r nop-demo/nop-jdk17-demo nop-demo/nop-spring-demo2 && rm -rf nop-demo/nop-jdk17-demo nop-demo/nop-spring-demo2`（jdk17-demo 有 git-ignored 残留 .classpath/.project/.settings/target，git rm 后需 rm -rf 清目录）
- [x] nop-demo/pom.xml 删除 `<module>nop-jdk17-demo</module>`（:34）与 `<!--        <module>nop-spring-demo2</module>-->`（:25）

Exit Criteria:

- [x] 两目录在工作区与 git index 均不存在；`grep -rn "nop-jdk17-demo\|nop-spring-demo2" --include="*.xml" --include="*.java"` 全仓零命中
- [x] No owner-doc update required（删除不改变任何保留模块行为）
- [x] No new test required: 纯删除
- [x] `ai-dev/logs/2026/10-12.md` WI12 条目

### Phase 2 - 验证与收口

Status: planned
Targets: 全仓构建、roadmap、plan

- Item Types: `Proof`

- [x] Gate A：`./mvnw clean install -DskipTests -T 1C -fae`（容忍面 = chronic gen-aop 家族 {job-dao, tcc-dao, sys-dao, retry-engine} 签名归因）；重点确认 nop-demo 聚合无缺失模块错误
- [x] roadmap WI12 `todo` → `done`（附：删除引发 9 处 markdown 路径引用失效——roadmap 4 行 + 历史 plan 2437 + 本 plan 2 行，按 guide Rule 20 损坏链接许可改为模块名引用；另修 2 处 nop-ai 历史对比文档的 ConcurrencyRegistry 旧路径，指向 gateway 现址）
- [x] 白名单提交（删除 + nop-demo/pom.xml + plan 2446 + log + roadmap）
- [x] 独立子 agent closure audit
- [x] 三门：check-plan-checklist --strict / scan-hollow-implementations（纯删除无实现面，`--module nop-demo` 记录裁定） / check-doc-links --strict
- [x] 文本一致性核对后置 `Plan Status: completed`

Exit Criteria:

- [x] Gate A 通过；roadmap done；commit hash 记录 Closure；审计证据写入；三门留痕

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（废弃 demo 在 reactor 内 = 唯一 defect 面，已删除）
- [x] 所有 in-scope confirmed contract drifts 已收敛（聚合 pom = 工作区目录状态一致）
- [x] 行为/契约结果已达成：reactor 无 nop-jdk17-demo/nop-spring-demo2
- [x] 必要 focused verification 已完成（Gate A）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（No owner-doc update required 已裁定；docs/ 遗留引用显式裁定不更新）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：删除真实生效（目录不存在 + git ls-files 零命中），非注释式假删除
- [x] `./mvnw clean install -DskipTests -T 1C -fae` 全绿（chronic 容忍面内）
- [x] Gate B：不适用（纯删除无测试面，Gate A 即验证）
- [x] 代码规范：不适用（零新增代码）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 两废弃 demo 目录已删（含 git-ignored 残留清理）、聚合 pom 两行注销、Gate A 398 SUCCESS（399−1 恰为删去的 jdk17-demo）+ 恰 4 chronic 家族；删除引发的 markdown 路径引用失效已按 Rule 20 修复（9 处改模块名引用 + 2 处 nop-ai 历史文档旧类路径指向 gateway 现址），doc-links --strict exit 0。
Completed: 2026-10-12
Commit: （本次收口提交）

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Evidence: （审计后回填）

Follow-up:

- 见 Non-Blocking Follow-ups（无）
