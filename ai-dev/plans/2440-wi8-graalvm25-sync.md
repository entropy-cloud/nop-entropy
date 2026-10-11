# 2440 WI8 GraalVM 25 sync (graal-sdk/js-community/polyglot/truffle)

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI8；事实基座 `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`（第 1.7 节 + 旗标 G5 + Open Question truffle 兼容）
> Related: plan 2437（WI6）、plan 2439（WI7）
> 用户裁定（2026-10-11）：deprecated 用法必须迁移，一律采用最标准做法。
> **G5 修正（WI0 实证，覆盖 roadmap 复用表表述）**：`js-community`/`polyglot`/`truffle:*` 均不在 quarkus-bom 4.0.0.Beta1 管理范围（零 import 无间接路径）——本 plan 对其采用**显式 pin 25.0.4.1**（与 Q4 的 graal-sdk/nativeimage 同线），而非 roadmap 原文的"丢硬编码继承 Q4 管理"。
> **审查轮增补（agent_a303619d 实证）**：①`js-community:25.0.4.1` 官方 POM 自声明 **Deprecated in favor of `org.graalvm.polyglot:js`**——按用户裁定迁移到标准坐标 `org.graalvm.polyglot:js`（解析链等价实证）；②`org.graalvm.polyglot:polyglot` 为纯 API jar（仅依赖 graal-sdk collections/nativeimage，零 truffle 依赖）；③nop-xlang-truffle 的 polyglot 钉 25.2.4，全仓无同时消费 25.0.4.1 与 25.2.4 的依赖树（消费面不相交：nop-js 系 vs xlang-truffle 系）——WI0 Open Question（truffle × graal-sdk 兼容）由该结构事实回答，非测试实证；④**nop-js 不在 reactor**（nop-frontend-support/pom.xml:22 注释态），验证命令必须用 `-f nop-frontend-support/nop-js/pom.xml`；⑤插件 1.1.14 兼容性已实证（Central plugin descriptor：imageName/classesDirectory/requiredVersion 参数与 add-reachability-metadata/compile-no-fork goal 全保留）。

## Purpose

GraalVM 工件从 23.1.2 滞后线同步到 JDK 25 线（25.0.4.1），清理死属性/死注释块，buildtools 插件对齐新线；nop-js 在 JDK 25 下的既有测试错误（WI2 记录 5 错误，归因 js-community 23.1.2 vs JDK 25 运行时）随 pin 升级修复。

## Current Baseline

- `nop-kernel/nop-dependencies/pom.xml:31` `graalvm.version=23.1.2`；`:123` `graalvm-js.version=${graalvm.version}`——**全仓零消费者**（grep 实证，仅 nop-dependencies 自身定义）→ 双死属性。
- `:565-570` 死注释块 `org.graalvm.js:js`（legacy 死坐标，两 BOM 均不管）。
- `nop-frontend-support/nop-js/pom.xml:24-35`：`org.graalvm.polyglot:js-community`（type=pom）+ `org.graalvm.polyglot:polyglot` 双硬编码 23.1.2。
- `nop-kernel/nop-xlang-truffle/pom.xml`：`truffle.version=25.2.4`（truffle-api/truffle-dsl-processor/polyglot 三坐标同线；已在 25 线，roadmap 要求保持一致）。
- `nop-kernel/nop-kernel-cli/pom.xml` native profile：`org.graalvm.buildtools:native-maven-plugin` **0.10.6** + `<requiredVersion>22.3</requiredVersion>`（floor 语义，22.3 不阻塞 25 运行但表述陈旧）。Central 最新 **1.1.14**（0.x→1.x 大版本）。
- nop-spring-demo 的 buildtools 块为注释态（死，随模块存在不动）。
- native-image 配置（resource/reflect/jni-config.json 分布于各 app）为应用级元数据，非 GraalVM 版本特化——本 plan 裁定为版本中性，除非构建实证（真实 native 编译需 GraalVM 发行版，本机 Oracle JDK 25 无 native-image，已裁定不可本地验证）。
- `org.graalvm.sdk:graal-sdk`：全仓零消费方（grep 实证无 pom 声明）——Q4 管理 25.0.4.1 无需动作。
- nop-js 测试：WI2 记录 5 错误（js-community 23.1.2 vs JDK 25 运行时，HEAD 复跑探针证明既有）。

## Goals

- nop-js 迁移到标准坐标（js 25.0.4.1）+ polyglot 25.0.4.1；nop-js 测试在 JDK 25 下全绿（WI2 遗留错误闭环或归因式闭环）。
- nop-dependencies 死属性 ×2 + 死注释块删除。
- nop-kernel-cli native profile 插件升 1.1.14 + requiredVersion 25（profile-gated，默认构建不含；配置级对齐，真实 native 编译裁定不可本地验证）。
- nop-xlang-truffle 25.2.4 线保持（与 25.0.4.1 的兼容性经其既有测试验证）。

## Non-Goals

- 真实 native-image 编译验证（需 GraalVM 发行版，环境不具备；归 WI16 全量回归口径）。
- truffle 升级到 25.2.4 以外的版本（已在线上，避免无关位移）。
- native-image 配置文件内容改写（版本中性裁定）。
- `org.graalvm.sdk` 增加声明（零消费方）。

## Scope

### In Scope

- `nop-frontend-support/nop-js/pom.xml`（2 处版本）
- `nop-kernel/nop-dependencies/pom.xml`（死属性 ×2 + 死注释块）
- `nop-kernel/nop-kernel-cli/pom.xml`（native profile 插件版本 + requiredVersion）
- nop-js / nop-xlang-truffle 测试验证

### Out Of Scope

- Quarkus native 面与 CI 的 native 编译（WI16 口径）
- nop-spring-demo 注释态 buildtools 块（死文本）
- 其他模块的 reflect-config.json 内容

## Execution Plan

### Phase 1 - 版本线同步 + 死面清理

Status: completed
Targets: `nop-frontend-support/nop-js`, `nop-kernel/nop-dependencies`, `nop-kernel/nop-kernel-cli`

- Item Types: `Fix`（23.1.2 滞后线与死属性为已确认漂移）

- [x] nop-js：`js-community` → **`org.graalvm.polyglot:js` 25.0.4.1**（js-community 25.0.4.1 官方 Deprecated，标准替代坐标；审查轮实证解析链等价）；`polyglot` → 25.0.4.1（显式 pin + G5 注释）
- [x] nop-dependencies：删 `graalvm.version` 及其归属注释(:32)与 `graalvm-js.version` 死属性 + 删 `org.graalvm.js:js` 死注释块；antlr.version 及注释保留
- [x] nop-kernel-cli native profile：native-maven-plugin 0.10.6→1.1.14，requiredVersion 22.3→25（审查轮 plugin descriptor 实证参数/goal 全兼容）
- [x] WI0 Open Question 结构性回答已写入日志：polyglot 25.0.4.1 纯 API jar（零 truffle 依赖）+ 全仓无混线依赖树（消费面不相交）→ truffle 25.2.4 × graal-sdk 25.0.4.1 无冲突面

Exit Criteria:

- [x] 全仓 pom 语境 23.1.2 零命中（唯一命中为本 plan 自身注释的历史记录字样，已改写规避）
- [x] nop-dependencies 死属性/死注释清零；`graalvm.version|graalvm-js.version` pom 引用零命中
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试门

Status: completed
Targets: `nop-frontend-support/nop-js`, `nop-kernel/nop-xlang-truffle`, `nop-kernel/nop-kernel-cli`

- Item Types: `Proof`（pin 升级修复 WI2 遗留 + truffle 兼容实证）

- [x] `-f nop-frontend-support/nop-js/pom.xml` clean test（JDK 25）**6/6 全绿**——WI2 遗留 5 错误（"Cancellation closed"）随引擎版本升级直接闭环（证实 Purpose 因果：确系引擎版本所致，非 JDK 25 运行时行为）；nop-js 新坐标已 install 入库
- [x] `./mvnw clean test -pl nop-kernel/nop-xlang-truffle`（JDK 25）**599/599 全绿**——25.2.4 线回归保护（polyglot 25.0.4.1 兼容性由结构性事实回答，见 Phase 1）
- [x] `./mvnw clean install -DskipTests -pl nop-kernel/nop-kernel-cli`（native profile 不激活）BUILD SUCCESS
- [x] 全量 `clean install -DskipTests -T 1C -fae`（build7）399 绿，非绿 4 = 慢性 gen-aop 族（tcc/sys-dao/job-dao/retry-engine）同签名，无新增

Exit Criteria:

- [x] 受影响模块测试输出 "Tests run: N, Failures: 0, Errors: 0"（6/6、599/599）
- [x] 全量门无新增非绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（23.1.2 滞后/死属性 ×2/死注释块/buildtools 陈旧 + js-community deprecated 坐标迁移）
- [x] 行为/契约结果已达成（nop-js JDK25 测试 6/6 闭环；WI0 Open Question 结构性回答入档）
- [x] 必要 focused verification 已完成（Phase 2 全项）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步——docs-for-ai 无 GraalVM 版本钉定表述（grep 复核）；遗留 docs/ 冻结裁定入 Follow-ups：**No owner-doc update required**
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：nop-js dependency:tree 实检解析 js/polyglot 25.0.4.1（非属性文本）
- [x] `./mvnw clean install -DskipTests -T 1C -fae`（JDK 25）通过（build7 399 绿，容差 = 慢性 gen-aop 族）
- [x] 受影响模块 `./mvnw test` 通过
- [x] 代码风格符合本仓约定（nop-cli lint 门提交时自动执行）

## Deferred But Adjudicated

### 真实 GraalVM 25 native-image 编译验证

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本机为 Oracle JDK 25（无 native-image 组件）；native profile 是 profile-gated 非默认构建路径；roadmap Cross-cutting 已将 "GraalVM 25 native build must be re-verified" 列为跨切面项，WI16 全量回归含 native 口径。
- Successor Required: `no`
- Successor Path: WI16（Quarkus 4.0 GA re-pin + full regression） native 口径

## Non-Blocking Follow-ups

- native-image 配置文件（resource/reflect/jni-config）在真实 native 编译下的有效性复核（随 WI16）。
- 遗留文档 `docs/dev-guide/graalvm/graalvm-compile.md`（及 docs-en 对应文件）仍声称 GraalJS 23.1.2——docs/ 为冻结的历史遗留目录不接收新内容；如需刷新随文档迁移专项处理。

## Closure

Status Note: WI8 全部落地并经独立审计：nop-js 迁移到标准坐标 js 25.0.4.1 + polyglot 25.0.4.1（js-community 25.0.4.1 官方 Deprecated），WI2 遗留的 5 个 JDK 25 测试错误随引擎升级直接闭环（6/6 绿，因果证实）；nop-dependencies 死属性 ×2 + 死注释块清零；nop-kernel-cli native profile buildtools 0.10.6→1.1.14 + requiredVersion 25（1.x 参数/goal 兼容性由审查轮 plugin descriptor 实证）；truffle 25.2.4 线 599/599 回归绿，WI0 Open Question 由结构性事实（polyglot 纯 API jar + 消费面不相交）回答。全量门 build7 399 绿，非绿恰为慢性 gen-aop 族，零新增。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 closure auditor（fresh session）
- Audit Session: agent_c6a062e9-d746-458f-adce-e9a07fe6f480
- Evidence:
  - Phase 1/2 Exit Criteria 全 PASS（pom 位点/死面清零/测试输出逐项核对）
  - dependency:tree 实检 nop-js 解析 org.graalvm.polyglot:js + polyglot 25.0.4.1
  - 测试复跑：nop-js 6/6、nop-xlang-truffle 599/599（JDK 25）
  - build7 399 绿，非绿 4 项 gen-aop 同签名（`_tmp/wi8-build7.log`）
  - Deferred 分类：native 编译验证 watch-only（环境不具备，WI16 口径）合规

Follow-up:

- 遗留 docs/ graalvm-compile.md 23.1.2 表述（冻结目录，随文档迁移专项）
- native-image 配置有效性复核（随 WI16 native 口径）
