# 2445 WI11 angus-mail migration

> Plan Status: active
> Last Reviewed: 2026-10-12
> Source: ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md WI11；plan 2444 实测教训（无版本占位条目禁令）
> Related: 2444（WI10 BOM-first 治理，政策先例）

## Purpose

邮件栈从 javax 命名空间的 `com.sun.mail:jakarta.mail` 1.6.8（两 BOM 均不管理的重发布件）迁移到 BOM 管理的 `org.eclipse.angus:angus-mail` 2.0.5（impl）+ `jakarta.mail:jakarta.mail-api` 2.1.5（API），代码导入 javax.mail.* → jakarta.mail.*，达成"邮件跑在 BOM 管理的实现 + 命名空间一致"。

## Current Baseline

- nop-dependencies：depMgmt 有 `com.sun.mail:jakarta.mail` @${jakarta.mail.version}=1.6.8 条目（WI10 后带 WI11 指引注释）。quarkus-bom 管理 `org.eclipse.angus:angus-mail` @2.0.5、`jakarta.mail:jakarta.mail-api` @2.1.5（本地 BOM pom 实测；jakarta.mail-api 2.1.5 已在本地 repo，angus-mail 2.0.5 需网络下载）。
- 直接消费方 2 个：`nop-integration/nop-integration-email-java/pom.xml`（compile 依赖 com.sun.mail:jakarta.mail）、`nop-quarkus/nop-quarkus-file/pom.xml`（quarkus-resteasy-multipart 带 `org.eclipse.angus:angus-mail` exclusion + 独立声明 com.sun.mail:jakarta.mail）。
- javax.mail 导入面 4 个 Java 文件：`JavaEmailSender.java`（main：`javax.mail.*`、`javax.mail.internet.InternetAddress/MimeMessage`）+ 3 个测试（nop-integration-email-java 两个 TestJavaEmailSender* + nop-auth-service 的 TestJavaEmailSenderMfaE2E；Message/Session/Transport/MessagingException）。jakarta.mail 导入现为零。
- impl 内部类引用 1 处：`nop-quarkus-file` 的 `QuarkusFix.java` import `com.sun.mail.util.LineInputStream`（native-image reachability 用途的桩类，唯一消费点是 new LineInputStream(...)）。angus-mail 2.x 将 impl 包名 com.sun.mail.* → org.eclipse.angus.mail.*，对应类 org.eclipse.angus.mail.util.LineInputStream（angus 是同一代码库的 Eclipse 演进，执行时以 jar 实测为准）。
- 无 provider 类名/配置字符串依赖（SMTPTransport、mail.smtp 等全仓零命中，QuarkusFix 除外）。
- nop-auth-service 对 email-java 是 test-scope 依赖（W16-impl-ext 注释在案），无运行时边。

## Goals

- nop-dependencies 删除 com.sun.mail 条目 + jakarta.mail.version 属性；**不新增任何条目**（angus-mail/jakarta.mail-api 均为 BOM 管理，经 quarkus-bom import 链直接生效；遵循 2444 实测教训——无版本占位条目会压制 imported BOM 版本）。
- 两个消费方 pom：com.sun.mail:jakarta.mail → org.eclipse.angus:angus-mail（无版本）；nop-quarkus-file 的 angus-mail exclusion 解除。
- 4 个 Java 文件 javax.mail.* → jakarta.mail.*；QuarkusFix 的 com.sun.mail.util.LineInputStream → org.eclipse.angus.mail.util.LineInputStream。
- 全仓 javax.mail / com.sun.mail 活引用归零。

## Non-Goals

- 不升级任何其他依赖；不动 nop-integration 其他模块（sms-tencent/feishu 等无邮件依赖）。
- 不改 QuarkusFix 类的存在性与职责（仅迁移 import；类的去留归 native-image 治理另案）。
- 不处理邮件语义行为变化（angus 是同库演进，API 面等价；行为验证交给 Gate B 既有测试）。

## Scope

### In Scope

- `nop-kernel/nop-dependencies/pom.xml`、`nop-integration/nop-integration-email-java/pom.xml`、`nop-quarkus/nop-quarkus-file/pom.xml`
- Java：`JavaEmailSender.java`、`TestJavaEmailSenderCredential.java`、`TestJavaEmailSenderErrorPropagation.java`、`TestJavaEmailSenderMfaE2E.java`、`QuarkusFix.java`
- plan、log、roadmap WI11 翻转

### Out Of Scope

- 其余依赖（WI10 已收口）；GraalVM native 反射配置（如 ojdbc 类似面出现归 native 治理另案）

## Rulings

1. **不设 depMgmt 条目**：angus-mail 2.0.5 / jakarta.mail-api 2.1.5 均由 quarkus-bom 先 import 管理（2444 实测 Q4 字面量）；按 2444 Ruling 4 同款处理——本地任何条目（含无版本占位）都会压制 imported 版本。WI10 预留的 com.sun.mail 条目注释（"angus-mail 迁移归 roadmap WI11"）随条目一起删除。
2. **QuarkusFix 保持桩类职责**，仅迁移 import 到 angus 命名空间；若 angus-mail 2.0.5 中无 org.eclipse.angus.mail.util.LineInputStream（预期存在，jar 实测裁定），备选=该类的实际新位置，仍不做超范围重构。
3. **exclusion 解除后 angus-mail 的到达路径**：quarkus-resteasy-multipart 经二跳传递（resteasy-multipart-provider 7.0.5.Final）带来 angus-mail，与本模块显式声明同一坐标（BOM 统一 2.0.5），无冲突；显式声明保留（编译期需要 impl 内部类 LineInputStream，jar 实测 org.eclipse.angus.mail.util.LineInputStream 存在且构造器兼容）。
4. **遗留文档矛盾显式裁定**：docs/dev-guide/quarkus.md:83-99 与 docs-en/dev-guide/quarkus.md:82-98 指导用户引入 com.sun.mail + exclusion angus-mail，与本 WI 后基线方向相反。按 AGENTS.md，docs/ 为历史遗留目录不接收新内容——裁定**不更新**，归 docs 治理另案（docs-for-ai 无对应内容，grep 实证）。
5. **jakarta.mail-api 消费方式**：经 angus-mail 传递供给（compile scope，jar pom 实测），消费方不显式声明；Gate C 以 dependency:tree 可见 2.1.5 为证据。

## Execution Plan

### Phase 1 - 依赖与代码迁移

Status: completed
Targets: 3 个 pom + 5 个 Java 文件

- Item Types: `Fix`（坐标/命名空间迁移）

- [x] nop-dependencies：删除 com.sun.mail:jakarta.mail 条目 + jakarta.mail.version 属性（WI11 指引注释随之清理）
- [x] nop-integration-email-java pom：com.sun.mail:jakarta.mail → org.eclipse.angus:angus-mail（无版本）
- [x] nop-quarkus-file pom：解除 quarkus-resteasy-multipart 的 angus-mail exclusion；com.sun.mail:jakarta.mail → org.eclipse.angus:angus-mail（无版本）
- [x] 4 个 javax.mail 文件 → jakarta.mail（import 面见 Baseline）
- [x] QuarkusFix.java：com.sun.mail.util.LineInputStream → org.eclipse.angus.mail.util.LineInputStream

Exit Criteria:

- [x] 全仓 `import javax.mail` / `com.sun.mail` 活引用为零（grep，注释/文档除外）；pom 层 `com.sun.mail` 零命中
- [x] `org.eclipse.angus:angus-mail` 在 email-java 与 quarkus-file 各出现一次（无版本）；nop-quarkus-file exclusion 块已删
- [x] No owner-doc update required（无 API/行为契约变化；模块级依赖面变更由本 plan 与 log 记录）
- [x] No new test required: 坐标/命名空间迁移，既有测试即行为验证面（Gate B）
- [x] `ai-dev/logs/2026/10-12.md` WI11 条目（可与 Phase 2 合并）

### Phase 2 - 验证门

Status: completed
Targets: 全仓构建 + 邮件消费面 focused tests

- Item Types: `Proof`

- [x] Gate A：`./mvnw clean install -DskipTests -T 1C -fae`（容忍面 = chronic gen-aop 家族 {job-dao, tcc-dao, sys-dao, retry-engine} 签名归因 + 2444 已裁定的 nop-cli-core pre-existing red 不在 install 范围）。angus-mail 2.0.5 首次解析需网络
- [x] Gate B（执行记录：`_tmp/wi11-gateb.log`；一处环境阻塞裁定——nop-auth-service 的 TestJavaEmailSenderMfaE2E 无法执行：其上游 sys-dao 属 chronic gen-aop 家族（WI6 起 Gate A 全量构建即 SKIPPED），standalone test-compile 亦死于 gen-aop-proxy-fail（`_tmp/wi11-authsvc-testcompile.log`，从未到达 javac），非本迁移引入（HEAD 基线同签名；该测试的 javax→jakarta 改写与 email-java 两个已绿测试文件机械同型）。email-java（邮件命名空间迁移的属主模块）20/20 全绿即本 WI 行为验证面；MFA E2E 归 chronic gen-aop 缺陷修复后的可运行面）
- [x] Gate B（原项）：`./mvnw test -pl nop-integration/nop-integration-email-java,nop-auth/nop-auth-service -Dtest='TestJavaEmailSender*,TestJavaEmailSenderMfaE2E'`（或模块全测若更快）——验证口径（如实）：API 命名空间迁移 + 消息构造/saveChanges/头处理/getContent（经 activation）+ Transport stub 出口的发送链行为不变；**SMTP wire 层与 provider lookup 无既有测试覆盖，属接受风险**（Baseline 已证零 provider 字符串依赖，angus jar 自带 META-INF/services/jakarta.mail.Provider 注册）。nop-quarkus-file 由 Gate A 编译覆盖（QuarkusFix 编译面）。注意：勿把 -pl 扩到无匹配测试的模块（surefire failIfNoSpecifiedTests 默认 true 会误红）
- [x] Gate C 解析证据：`dependency:tree -pl nop-integration/nop-integration-email-java,nop-quarkus/nop-quarkus-file -Dincludes=org.eclipse.angus*,jakarta.mail*,com.sun.mail*` 记录 angus-mail 2.0.5 / jakarta.mail-api 2.1.5 / com.sun.mail 零命中（quarkus-file 必含——exclusion 解除后双到达路径交汇的唯一模块）
- [x] `ai-dev/logs/2026/10-12.md` WI11 条目（含裁决与证据）

Exit Criteria:

- [x] Gate A/B/C 通过且证据可追溯；失败处置规则同 2444（非 chronic → 修复或归因）
- [x] jakarta 命名空间一致性：email-java 编译产物无 javax.mail 残留（javap 或 grep class 常量池抽查可选，grep 源码已足够）

### Phase 3 - 收口

Status: planned
Targets: roadmap、plan、log

- Item Types: `Proof`（审计与门禁）、`Follow-up`（无——本 Phase 无遗留项）

- [ ] roadmap WI11 `todo` → `done`
- [ ] 白名单提交（3 pom + 5 java + plan 2445 + log 10-12 + roadmap；严禁混入 plan 2380 EQL 文件集与并行 session 未提交内容）
- [ ] 独立子 agent closure audit
- [ ] 三门全过：check-plan-checklist --strict / scan-hollow-implementations --module nop-integration/nop-integration-email-java --severity high / check-doc-links --strict
- [ ] 文本一致性核对后置 `Plan Status: completed`

Exit Criteria:

- [ ] roadmap WI11 行 done；commit hash 记录 Closure 段；审计证据写入；三门留痕

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（javax/com.sun.mail 命名空间漂移 = 本 WI 的 contract drift）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（代码 namespace = 依赖 artifact namespace = BOM 管理版本三者一致）
- [ ] 行为/契约结果已达成：API 命名空间迁移 + stub 出口发送链行为不变（Gate B 既有测试绿）；SMTP wire 层无既有覆盖属接受风险（已如实声明）
- [ ] 必要 focused verification 已完成（Gate B）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步（No owner-doc update required 已裁定）或已更新
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：迁移真实生效（grep 源码零 javax.mail / dependency:tree 真实解析 angus），非注释式假迁移
- [ ] `./mvnw clean install -DskipTests -T 1C -fae` 全绿（chronic 家族容忍面内）
- [ ] Gate B focused tests 全绿
- [ ] 代码规范：无新增注释违例；import 分组符合仓内惯例

## Deferred But Adjudicated

（无——WI11 交付面内无 deferred 项）

## Non-Blocking Follow-ups

- QuarkusFix 桩类去留（native-image reachability 治理）：out-of-scope improvement，与本迁移无关。

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）

Follow-up:

- 见 Non-Blocking Follow-ups
