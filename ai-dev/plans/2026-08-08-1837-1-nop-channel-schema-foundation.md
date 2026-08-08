# 1 nop-ai 外部信道集成 — Schema 基础与 nop-integration-feishu 模块骨架 (W0)

> Plan Status: completed
> Mission: nop-ai-channel-integration
> Work Item: W0
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (W0) · `ai-dev/design/nop-ai-channel-integration-design.md` (§3.4) · `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` (§6,§10)
> Related: 后续 Plan 2 (W1 传输层)、Plan 3 (W2 业务消息层) 依赖本计划产出

## Purpose

为整个外部信道集成 mission 打下地基：① 给 `NopAuthExtLogin` 增加 `(loginType, extId)` 唯一约束以保证扫码登录 extId→userId 唯一性；② 扩展 `auth/login-type` 字典，为飞书/钉钉/企微/Webhook 分配整数码；③ 新建 `nop-integration-feishu` 模块骨架（不依赖 AI），为 W5 飞书首信道落地提供落点。三者均为后续 W1–W6 的前置依赖。

## Current Baseline

- `NopAuthExtLogin` 实体定义在 `nop-auth/model/nop-auth.orm.xml:179-232`，有 `loginType`(int, `ext:dict="auth/login-type"`)、`extId`(string, comment="第三方系统中对应的用户唯一标识")、`verified`(boolean)、`delFlag`、`lastLoginTime` 列，`useLogicalDelete="true"`。**当前无 `<unique-keys>`**（对照同文件 `NopAuthUser:175` 与 `NopAuthRole:285` 均有 `<unique-keys>`）。
- `auth/login-type` 字典权威源（非 `_dump/` 生成产物）在 `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/dict/auth/login-type.dict.yaml`，当前仅 2 项：`1`=密码登录、`10`=单点登录。`nop-auth-app/_dump/.../login-type.dict.yaml` 是生成产物，**不是权威源**。
- `nop-integration` 父 pom（`nop-integration/pom.xml`）当前注册 9 个子模块（`nop-integration-api`/`-sftp`/`-sms-*`/`-email-*`/`-file-local`/`-oss`/`-zxing`），**无 `nop-integration-feishu`**。
- `nop-integration-api` 已存在，接口（`ISmsSender`/`IEmailSender`/`IQrcodeService`）在 `io.nop.integration.api.{sms,email,qrcode}`，pom 仅依赖 `nop-api-core`。
- `nop-ai-gateway` 已存在（deps: `nop-gateway`/`nop-ai-api`/`nop-ai-core`，无 `nop-ai-agent`/`nop-auth-api`/`nop-integration-api`），有 `ai-gateway-defaults.beans.xml`。
- ORM 变更纪律（AGENTS.md + roadmap W0-1）：编辑 `model/*.orm.xml` 源 → `mvn clean install -DskipTests` 触发增量重新生成 → 迁移 DDL；**禁止手编 `_gen/` 与 `_` 前缀生成产物**（如 `nop-auth-dao/.../_app.orm.xml`）。

## Goals

- `NopAuthExtLogin` 的有效绑定（`verified=1 AND delFlag=0`）在 `(loginType, extId)` 维度上唯一，有 DB 级或应用层+DB 兜底保证
- `auth/login-type` 字典新增 `20`=飞书、`21`=钉钉、`22`=企微、`23`=Webhook 四个整数码及中文 label
- `nop-integration-feishu` 模块骨架可独立编译，注册在父 pom `<modules>`，依赖仅 `nop-integration-api`（不依赖任何 `nop-ai-*`/`nop-auth-*`）

## Non-Goals

- 不实现飞书 SDK 协议（`FeishuClient`/`FeishuPbCodec`/`FeishuBindProvider`）—— 属 W5
- 不实现绑定业务逻辑（`completeBinding` 写表）—— 属 W3
- 不改动 `nop-ai-gateway` 现有依赖（加 `nop-ai-agent` 等依赖属 W1）
- 不修改 `IAuthTokenProvider` / `ILoginSpi` 任何契约
- 不涉及传输层 `IChannelConnector` 接口定义

## Scope

### In Scope

- `nop-auth/model/nop-auth.orm.xml` — `NopAuthExtLogin` 增加 `(loginType, extId)` 唯一键
- `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/dict/auth/login-type.dict.yaml` — 新增 4 个字典项
- 迁移 DDL（针对新增唯一约束）
- `nop-integration/nop-integration-feishu/` — 新模块骨架（pom + 目录结构 + 占位）
- `nop-integration/pom.xml` — 注册新子模块

### Out Of Scope

- 飞书 SDK 协议实现（W5）
- 绑定/登录业务逻辑（W3/W4）
- 传输层接口（W1）
- 业务消息层接口（W2）
- 跨模块依赖边新增（各后续 plan 各自处理）

## Execution Plan

### Phase 1 — NopAuthExtLogin 唯一约束 + login-type 字典扩展

Status: completed
Targets: `nop-auth/model/nop-auth.orm.xml` · `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/dict/auth/login-type.dict.yaml` · DDL 迁移

- Item Types: `Decision | Fix`

- [x] **Decision：唯一约束实现方式**。`NopAuthExtLogin` 用逻辑删除（`useLogicalDelete="true"`），软删行保留 `delFlag=1`。需在以下方案中裁定一种以满足"有效绑定（`verified=1 AND delFlag=0`）的 `(loginType, extId)` 唯一"：
  - 方案 A：条件唯一索引（partial unique index，DB 相关，MySQL 无原生 partial index 需变通）
  - 方案 B：ORM `<unique-keys>` 声明 `(loginType, extId)` + 应用层在 `completeBinding`（W3）做 `verified/delFlag` 过滤校验 + 重绑定时先物理清理/复用旧软删行
  - 裁定须记录在 `ai-dev/design/nop-ai-channel-integration-design.md` Open Questions 或对应 daily log，说明对重绑定（unbind 后重绑同 extId）的影响
  - **裁定（W0 已收口）**：采用**方案 B**（普通唯一约束 + 应用层兜底）。理由：① MySQL 无原生 partial unique index，三种 DB 无法用统一 DDL 表达条件索引；② 与既有 `NopAuthUser.userName`（普通唯一键 + `useLogicalDelete`）模式一致，DB 无关；③ 有效绑定条件过滤在 `completeBinding`（W3）应用层完成，重绑定时物理清理/复用旧软删行。裁定记录在 `ai-dev/design/nop-ai-channel-integration-design.md` §3.4（"约束实现方式裁定"段落）。
- [x] 按裁定结果编辑 `nop-auth/model/nop-auth.orm.xml` 的 `NopAuthExtLogin` 实体（参照同文件 `NopAuthUser:175-177` 的 `<unique-keys>` 写法），声明 `(loginType, extId)` 唯一键
- [x] 扩展 `nop-biz-auth-core/.../_vfs/dict/auth/login-type.dict.yaml`：新增 `20`=飞书、`21`=钉钉、`22`=企微、`23`=Webhook（label 用中文，避开已有 `1`/`10`）
- [x] `mvn clean install -DskipTests -pl nop-auth -am`（或 `-pl nop-auth/nop-auth-dao -am`）触发增量重新生成，确认 `_gen/` 与 `_` 前缀生成产物由源码驱动更新，**未被手编**
- [x] 生成/更新迁移 DDL（目标路径：`nop-auth/deploy/sql/{mysql,postgresql,oracle}/_create_nop-auth.sql` 更新或新建 `_add_ext_login_unique.sql`，针对新增唯一约束），DDL 可执行

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-auth/model/nop-auth.orm.xml` 中 `NopAuthExtLogin` 实体包含 `(loginType, extId)` 唯一键声明
- [x] 重新生成后的 `_app.orm.xml`（`nop-auth-dao` 下）同步包含该唯一键，且未被手编（`git diff` 仅显示源 + 生成产物联动）
- [x] `login-type.dict.yaml` 含 6 项（原 2 + 新 4），新码 `20/21/22/23` 不与已有冲突
- [x] DDL 迁移针对唯一约束可执行（不要求本 plan 真实跑 DB 迁移，但 DDL 文本须存在且语法正确）
- [x] **无静默跳过**：唯一约束方案裁定有明确记录，不留 "TODO 待定" 作为完成态
- [x] **No new test required**: 纯 ORM schema 变更与字典数据扩展，无新行为需测试（DDL 可执行性在 Phase 2 编译时间接验证）
- [x] 若裁定改变 live baseline：`ai-dev/design/nop-ai-channel-integration-design.md` 对应 Open Question 已收口或 daily log 已记录；否则明确写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — nop-integration-feishu 模块骨架

Status: completed
Targets: `nop-integration/nop-integration-feishu/` (新) · `nop-integration/pom.xml`

- Item Types: `Fix`

- [x] 创建 `nop-integration/nop-integration-feishu/pom.xml`：`<parent>` = `nop-integration`；依赖仅 `nop-integration-api`（**不含任何 `nop-ai-*` / `nop-auth-*`**）；参照同级模块如 `nop-integration-sms-tencent/pom.xml` 的结构
- [x] 在 `nop-integration/pom.xml` 的 `<modules>` 增加 `<module>nop-integration-feishu</module>`（当前 9 模块无 feishu，不加则 `-pl nop-integration -am` 不构建本模块）
- [x] 创建包目录结构 `src/main/java/io/nop/integration/feishu/{client,codec,bind}/`（仅目录 + `package-info` 或留空，本 plan 不放实现类）
- [x] **接线验证（预置）**：`./mvnw compile -pl nop-integration-feishu -am` 成功
- [x] **依赖纯净度证明**：`rg "nop-ai|nop-auth" nop-integration-feishu/pom.xml` 输出为空

Exit Criteria:

- [x] `nop-integration-feishu/pom.xml` 存在且 `<parent>` 指向 `nop-integration`
- [x] `nop-integration/pom.xml` `<modules>` 含 `nop-integration-feishu`
- [x] `./mvnw compile -pl nop-integration-feishu -am` 成功（BUILD SUCCESS）
- [x] `rg "nop-ai|nop-auth" nop-integration/nop-integration-feishu/pom.xml` 为空（依赖纯净，厂商协议可被非 AI 场景复用）
- [x] **无静默跳过**：无空 pom / 无缺失 `<module>` 注册导致构建跳过
- [x] No owner-doc update required（新模块骨架，尚无可更新的 owner doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `NopAuthExtLogin` 有效绑定 `(loginType, extId)` 唯一性有 DB 级或应用层+DB 兜底保证，且 ORM 源是唯一编辑点（`_gen/` 未手编）
- [x] `auth/login-type` 字典含飞书/钉钉/企微/Webhook 整数码，权威源已更新
- [x] `nop-integration-feishu` 模块可独立编译且依赖纯净（不依赖 AI/Auth）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：模块骨架真实注册进父 pom 且可编译（非空壳目录）
- [x] `./mvnw compile -pl nop-auth,nop-integration -am`（覆盖两个变更域）— BUILD SUCCESS（扩展验证 `-pl nop-auth,nop-integration,nop-ai -am` 亦 BUILD SUCCESS）
- [x] `./mvnw test -pl nop-auth,nop-integration -am`（或 `-pl` 指定受影响子模块）— **环境性前置失败，非回归**：详见 Closure Audit Evidence（H2 "database is empty" 在 baseline 同样复现）
- [x] checkstyle / 代码规范检查通过 — `mvn checkstyle:check -pl nop-integration-feishu` = 0 violations（项目 checkstyle 绑定在根 pom 已注释禁用，此处用直接 goal 验证新模块）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 飞书 SDK 协议实现（`FeishuClient`/`FeishuPbCodec`/`FeishuBindProvider`）属 W5，本 plan 仅建模块骨架
- `completeBinding` 应用层唯一性校验的最终接线属 W3，本 plan 仅确定约束方案

## Closure

Status Note: W0 三项地基全部落地——① NopAuthExtLogin 增 `(loginType,extId)` 唯一约束（方案 B：普通唯一约束 + 应用层兜底，DB 无关，与既有 NopAuthUser.userName 模式一致）；② `auth/login-type` 字典扩展飞书/钉钉/企微/Webhook 整数码；③ `nop-integration-feishu` 模块骨架可独立编译、注册进父 pom、依赖纯净（不依赖 AI/Auth）。所有变更纯增量、编译全绿；唯一约束方案裁定已记录在设计 §3.4。后续 W1–W6 可在此基础上展开。
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_01e8f998fffeTKWctRKK02Y3Hp`，general 类型，read-only 验证）
- Audit Session: ses_01e8f998fffeTKWctRKK02Y3Hp
- Evidence:
  - **Phase 1 Exit Criteria**：逐条 PASS
    - `nop-auth/model/nop-auth.orm.xml:232-235` 含 `<unique-keys columns="loginType,extId" constraint="UK_NOP_AUTH_EXT_LOGIN_TYPE_EXTID">` — PASS
    - 生成产物 `nop-auth-dao/.../_app.orm.xml:244` 同步含该唯一键（source→generated 联动，非手编）— PASS
    - 三方言 `_create_nop-auth.sql:192` 均含 `unique` 约束（mysql/oracle 大写、pg 小写）— PASS
    - 新建迁移 `_add_ext_login_unique.sql`（mysql/pg/oracle 三份）`alter table ... add constraint ... unique (...)` 语法正确 — PASS
    - `login-type.dict.yaml` 含 6 项（1/10/20/21/22/23），无码冲突 — PASS
    - 唯一约束裁定（方案 B）记录在 `design §3.4` "约束实现方式裁定" 段落 — PASS（无静默跳过）
    - 设计 Open Question（字典码）已收口标记 `[x]` — PASS
  - **Phase 2 Exit Criteria**：逐条 PASS
    - `nop-integration-feishu/pom.xml` `<parent>`=nop-integration，唯一依赖 nop-integration-api — PASS
    - `nop-integration/pom.xml:26` `<module>nop-integration-feishu</module>` — PASS
    - 三子包 `client/codec/bind` 含 package-info.java（纯文档，无实现类，符合 Non-Goals）— PASS
    - 依赖纯净：`rg "nop-ai|nop-auth" nop-integration-feishu/pom.xml` exit=1（无匹配）— PASS
  - **Closure Gates**：
    - `./mvnw compile -pl nop-auth,nop-integration -am` = BUILD SUCCESS（扩展 `-pl nop-auth,nop-integration,nop-ai -am` 亦 SUCCESS）— PASS
    - `./mvnw test`：**环境性前置失败，非回归** — baseline 验证（`git stash` 暂存本 plan 全部 tracked 改动后）同样复现 `Table "NOP_AUTH_SITE" not found (this database is empty)` / `Table "NOP_SYS_SEQUENCE" not found`，H2 初始化问题，与本 plan 改动无关（本 plan 仅增量加唯一约束 + 字典 + 新模块，编译全绿）。此为 checkout 级环境问题，非 in-scope live defect。
    - `mvn checkstyle:check -pl nop-integration-feishu` = 0 violations（项目 checkstyle 绑定在根 pom 已注释禁用）— PASS
  - **Anti-Hollow Check**：模块真实注册进父 pom + 真实 pom.xml（非空壳目录）；package-info 仅为文档，无 no-op/stub 伪装实现 — PASS
  - **Deferred 分类检查**：无 in-scope live defect 被降级；Non-Blocking Follow-ups 仅含 W5（飞书 SDK 协议）与 W3（completeBinding 接线），均显式标注归属，非本 plan 范围
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` = all 34 items checked

Follow-up:

- no remaining plan-owned work（W0 in-scope 全部落地）
- 环境性 H2 测试初始化失败（baseline 复现）超出本 plan 范围，若后续需要绿色 `./mvnw test` 全量需单独排查 H2 schema 初始化机制
