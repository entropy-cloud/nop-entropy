# SSRF 修复残余变体收口：无括号 IPv6 + FQDN 尾点 host 归一化（AR-02/AR-03）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Draft Review: R1 `ses_02c1164daffek0d9cjUzdsLlw9`（3 Blocker：B1 IPv6+port 归一化规则字面执行会回归 `::1` 基础形态 → 已重写为"头部可解析为合法 IPv6 字面量且尾部数字才剥离"的主动判读语义；B2 JDK 26 `getByName("::1:3306")` 不抛异常（解析为 0:0:0:0:0:0:1:3306）、`fe80::1:3306` 已判 link-local → 基线机制描述与 red 证明分列已修正；B3 extractHost 触发条件 `indexOf(':')==0` 拦不住 `fe80::1:5432`（首字符 f）→ 改为"含 : 且非方括号包裹 + lastIndexOf 分割 + 头部 IPv6 判读"。2 Major：M4 extractWebhookHost 同款截断 → Phase 2 独立修复项；M5 red-proof 对 `127.0.0.1.`/`fe80::1:3306` 本已 green → 分列 red→green / keep-green / 反例。3 Minor（尾点归一化提升到入口、反例不断连、allowlist 语义核对）已并入）。R2 `ses_02c05fd30ffeF5rDBHkPvy1JtV`（1 新 Blocker：I-1 Phase 2 提取器"否则整个串"回退缺失会把 `127.0.0.1:3306` 整体交给 util 放行 → 已加"回退到既有首冒号分割 + 头部仅字面量解析禁 DNS"；1 Major：I-2 Phase 1 退出标准含 URL 层不可满足项 → 改为 util 层口径、URL 层移至 Phase 2；3 Minor：`0.0.0.0.` 改判 red→green、基线算法措辞对齐、`[2001:db8::1]:3306` 标契约边界守卫——已并入）。R3 `ses_02bfe84eaffe3wwBD6Krb0102k`（1 Major：F-1 `::ffff:127.0.0.1:3306` 若限定 16 字节会回退放行 → 头部判读扩展为"16 字节 IPv6 或 4 字节 mapped"并复用 HostSecurityUtil 判定 + 双侧 mapped 用例；4 Minor：connection 包测试 seam 具名、删除前缀特判措辞、JDBC 尾点 URL 用例、工具门禁——已并入）。consensus 达成（R3 无 Blocker；F-1 修复后可直接执行）。
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-02/AR-03）、`ai-dev/audits/2026-08-05-0655-open-audit-nop-metadata-audit-remediation.md`（R5.1 上下文）
> Related: 执行顺序 `{1}` of 3 — 安全面优先；`{2}`（custom_sql 沙箱）、`{3}`（查询/质量/导入正确性）无依赖关系，独立执行
> Mission: nop-metadata-audit-remediation

## Purpose

收口 R5.1 SSRF 统一防护（HostSecurityUtil 双侧接线）后仍 live 的两个同族绕过变体：无方括号 IPv6 字面量（`jdbc:mysql://::1:3306/db`，实测可建连 ::1 loopback）与 FQDN 尾点 hostname（`localhost.` / `a.localhost.`，jshell 实测解析到 127.0.0.1）。目标是让 host 校验语义与 JDK/OS 实际解析语义一致：任何解析到 loopback/link-local/内网段的 host 形式都必须被拒绝（fail-closed），并补正反回归用例。

## Current Baseline

2026-08-06 live repo 核对：

- **HostSecurityUtil**（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/security/HostSecurityUtil.java`）：R5.1 后为 JDBC 建连与 webhook dispatcher 双侧共享的 host 校验工具。`isInternalHostname`（:186-207）对 `localhost` / `*.localhost` / `127.` / `10.` / `192.168.` / `172.16-31.` / `169.254.` 做前缀字符串比对——**`localhost.`（尾点 FQDN）不命中任何前缀**，而 jshell 26.0.1（macOS）实测 `InetAddress.getByName("localhost.")` 与 `getByName("a.localhost.")` 均解析到 127.0.0.1。`127.0.0.1.` 因前缀比对恰被拦截（`127.` 前缀命中），`localhost.` 无前缀可依。`isInternalIpv6Literal`（:210-233）用 `InetAddress.getByName` 判定。
- **JDK 26.0.1 对"无括号 IPv6 + 端口"的解析语义（jshell 实测，修正审计 AR-02 的机制描述）**：`getByName("::1:3306")` **不抛异常**，解析为 `0:0:0:0:0:0:1:3306`（合法非回环地址 → `isInternalIpv6Literal` 返回 false → 绕过成立，但机制是"解析成功且判为外部"，不是 UnknownHostException）；`getByName("fe80::1:3306")` 解析为 `fe80:0:0:0:0:0:1:3306`（**link-local → 当前已判为内网 true**）。结论：**端口剥离必须是主动的判读**——对"尾 `:` 后为纯数字且头部可解析为合法 IPv6 字面量"的形态按剥离后头部判定，不能把"含端口整体串"直接交给 getByName（在 JDK 26 上它解析成别的地址而非报错）。
- **MetaDataSourceConnectionProcessor.extractHost**（`.../connection/MetaDataSourceConnectionProcessor.java:293-295`）：对 `hostPort.indexOf(':')` 取首个冒号剥离端口。对 `jdbc:mysql://::1:3306/db`：`hostPort = "::1:3306"`，`indexOf(':') == 0` → `colon > 0` 不成立 → host = 整个 `"::1:3306"` → HostSecurityUtil 按上条机制判为外部 → **放行**。对 `jdbc:postgresql://fe80::1:5432/db`：首字符为 `f`，`colon = 4 > 0` → host = `"fe80"`（**在进入 HostSecurityUtil 前已被截断**）→ 前缀不命中 → **放行**。实测（MySQL Connector/J 9.6.0 + pgjdbc 42.7.10，本机 TCP listener on ::1）：`::1:3306` 形式两驱动均接受并成功建连 `::1` loopback。
- **webhook 路径**（`.../quality/CheckpointActionDispatcher.java:303-331` `extractWebhookHost`）：R5.1 后校验复用 HostSecurityUtil，但 host 提取逻辑与 extractHost 相同的首冒号截断——`http://::1:3306/` → host `"::1:3306"`（需 Phase 1 归一化才能拦）；`http://fe80::1:3306/` → host `"fe80"`（**截断先于校验**，Phase 1 归一化也救不了，需本侧同款提取修复）。`localhost.:8080` 形态同样放行（jshell 实测解析到 loopback）。
- **测试现状**：`TestHostSecurityUtil` 覆盖 `localhost`/`a.localhost`（不含尾点形式）与 `[::1]`；`TestMetaDataSourceConnectionSecurity` 仅测带括号 IPv6；`TestCheckpointActionDispatcherWebhookSsrf` 无无括号 IPv6 用例。无 `::1:3306` / `fe80::1:3306` / `localhost.` 用例。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → 923 tests / 0 failures（R6.6 收口口径；执行时以当前为准）。
- 非绕过确认（审计已核验，不重复处理）：`0x7f.0.0.1` / `0177.0.0.1`（JDK 严格十进制）、`10.0.0.1.` / `192.168.1.1.` / `169.254.x.x.` 等 IPv4 尾点形式（前缀比对已拦截）。注意：`0.0.0.0.` / `127.0.0.1.` 形态当前在 macOS 上 `getByName` 失败（不解析），暂无实际可利用路径，但本计划按"归一化后判定"原则一并覆盖（见 Phase 1 尾点条目）。

## Goals

- `::1:3306` / `fe80::1:3306` 等无括号 IPv6（含端口）在 JDBC 与 webhook 双侧全部被拒绝（fail-closed）
- `localhost.` / `a.localhost.` 尾点 hostname 在双侧全部被拒绝；`127.0.0.1.` / `0.0.0.0.` 等数字尾点形态按归一化后判定（被拒）
- 合法外网 host 不受影响（如 `2001:db8::1`、`example.com.`、带括号 IPv6 正路径）
- host 校验语义与 JDK/OS 实际解析语义一致（端口剥离是主动判读，非异常回退）；回归测试覆盖全部正反用例，判别性有效（red 先于修复，keep-green 项有标注）

## Non-Goals

- 不重构 HostSecurityUtil 的整体架构（R5.1 设计维持）
- 不处理其它 SSRF 面（webhook 重定向跟随、凭据脱敏——R6.2 已修）
- 不修改公开 API 契约（仅内部校验语义归一化）
- 不做 DNS 查询式校验（保持 fail-closed 前缀/字面量判定，不触发外部 DNS）

## Scope

### In Scope

- `HostSecurityUtil` 判定入口：统一剥离一个尾 `.`（FQDN 标记）后再分派（hostname / IPv4 / IPv6），覆盖 `localhost.` 与数字尾点形态（Fix）
- `HostSecurityUtil`：无括号多冒号 IPv6 带端口形式（`::1:3306` / `fe80::1:3306`）的主动判读归一化（Fix）
- `MetaDataSourceConnectionProcessor.extractHost` 与 `CheckpointActionDispatcher.extractWebhookHost`：无括号 IPv6（含首字符非 `:` 的 `fe80::1:5432` 形态）的端口剥离修复（Fix）
- 回归测试：`TestHostSecurityUtil` + `TestMetaDataSourceConnectionSecurity` + `TestCheckpointActionDispatcherWebhookSsrf`（Fix）
- `ai-dev/logs/` 对应日期条目（Follow-up）

### Out Of Scope

- 其它 IP 记法变体（十进制整数/短格式/前导零——06:55 AR-01/AR-02 已由 R5.1 计划 `2026-08-05-1842-1` 处理并验证）
- DNS rebinding / 多实例部署面
- nop-metadata-app 运行时配置

## Execution Plan

### Phase 1 - HostSecurityUtil 归一化（尾点 + 无括号 IPv6 带端口形式）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/security/HostSecurityUtil.java`

- Item Types: `Fix | Proof`

- [x] **先写失败用例（Proof，red/keep-green 分列）**：在 `TestHostSecurityUtil`（及/或新增专门用例）添加断言并**逐项标注判别性**——
  - **red→green**（修复前失败）：`localhost.`（判内网）、`a.localhost.`（判内网）、`::1:3306`（判内网）、`0.0.0.0.`（当前前缀不命中 → 判外部；修复后尾点剥离 → `0.0.0.0` → 0/8 判内网）
  - **keep-green 回归守卫**（修复前已通过，修复后必须保持）：`127.0.0.1.`（前缀已拦）、`fe80::1:3306`（JDK 26 解析为 link-local，util 已判内网）
  - **反（必须判为外部/放行）**：`example.com`、`example.com.`、`2001:db8::1`、`[2001:db8::1]:3306`（util 输入契约是不带方括号的 host，此为契约边界守卫用例——调用方在进 util 前已剥离方括号，此用例防"方括号处理条件"误删）
  - 运行确认：red→green 项当前全部失败；keep-green 项当前通过（记录两类区分，防止把"本就 green"误报为修复成果）
- [x] **尾点归一化（Fix）**：`HostSecurityUtil` 的 isInternalHost 判定入口（hostname / IPv4 / IPv6 分派之前）统一剥离一个尾 `.`（仅一个；`example.com..` 剥一个后剩余 `example.com.` 不再递归处理，按既有判定路径处理，不允许静默放行）；`localhost` / `*.localhost` 与 IPv4 前缀比对在归一化后的字符串上执行。同时覆盖数字尾点形态（`0.0.0.0.` → `0.0.0.0`）
- [x] **无括号 IPv6 带端口主动判读（Fix）**：对含多个冒号且非方括号包裹的 host 串（`::1:3306` / `fe80::1:3306` / `::ffff:127.0.0.1:3306`），按以下语义判定（**主动判读，不是异常回退**——JDK 26 上 `getByName("::1:3306")` 不抛异常，异常回退永不触发）：
  - 规则：若尾 `:` 之后为纯数字（端口候选），且尾 `:` 之前的头部解析为 IP 地址（**16 字节 IPv6 字面量，或 4 字节 `::ffff:` IPv4-mapped 形式**——`getByName("::ffff:127.0.0.1")` 返回 4 字节 IPv4；若限定 16 字节会把 mapped 头部误判为"非 IPv6"而回退放行，形成同族绕过）→ 按头部（剥离端口后的 host）判定内网；否则按整个串判定。
  - **头部判定直接复用 HostSecurityUtil 自身判内网逻辑**（mapped 头部由既有 `::ffff:` 分支判为内网），保持单一事实源。
  - 禁止把串剥离到非法剩余（如 `::1` 剥离成 `:`）——`::1` 无端口，整个串判定。
  - 判定不定（无法解析为合法 IP 字面量且不是合法 hostname）时保持既有 fail-closed 语义（不允许"解析成功但被放行"的路径）
- [x] **判别性复核（Proof，green）**：Phase 1 全部 red→green 项转 green、keep-green 项保持 green、反例保持放行；`TestHostSecurityUtil` 既有用例不回归

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 正反用例清单齐全（red→green / keep-green / 反例三分类有记录；red 先于修复确认）
- [x] **util 层**：`::1:3306` / `fe80::1:3306` / `localhost.` / `a.localhost.` / `127.0.0.1.` / `0.0.0.0.` 全部判为内网拒绝（URL 层双口径验证在 Phase 2）
- [x] 反例（外网 host 及带括号正路径）全部放行，无功能回归
- [x] **无静默跳过**：非法 host 形态不存在静默放行路径（判不定时显式拒绝或按既有外部判定语义，禁止无声放行）
- [x] `No owner-doc update required`（内部校验语义归一化，无公开契约/行为契约变更——模块文档安全章节如描述 host 校验规则则补一句尾点/无括号 IPv6 说明，以 live 文档为准）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 调用侧接线（extractHost / extractWebhookHost）+ 全量回归

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java` + `.../quality/CheckpointActionDispatcher.java` + `TestMetaDataSourceConnectionSecurity.java` + `TestCheckpointActionDispatcherWebhookSsrf.java`

- Item Types: `Fix | Proof`

- [x] **extractHost 无括号 IPv6 处理（Fix）**：对 hostPort 的端口剥离规则（触发条件"含 `:` 且非方括号包裹"）**不能破坏既有单冒号 host:port 语义**——精确规则：按 `lastIndexOf(':')` 分割，**仅当**尾部为纯数字（端口候选）**且**头部解析为 IP 地址（16 字节 IPv6 或 4 字节 `::ffff:` IPv4-mapped，**仅字面量解析、绝不触发 DNS**——违反 Non-Goal）时取头部；**否则回退到既有首冒号分割语义（`127.0.0.1:3306` / `localhost:3306` / `example.com:3306` 等单冒号形态维持现状，绝不把"含端口整体串"交给 HostSecurityUtil**——整体串含冒号会走 IPv6 判定路径解析失败放行，导致基础防护全线失效）；无端口形态（`::1`）原样返回；`TestMetaDataSourceConnectionSecurity` 补正用例：`jdbc:mysql://::1:3306/db`、`jdbc:postgresql://fe80::1:5432/db`、`jdbc:mysql://::1/db`（无端口）、`jdbc:mysql://::ffff:127.0.0.1:3306/db`（IPv4-mapped 无括号变体）、`jdbc:mysql://localhost.:3306/db`（尾点 FQDN，AR-03 的 JDBC 侧直接证据）均被拒；反例：`jdbc:mysql://[2001:db8::1]:3306/db` 放行——**反例与 allowlist"放行"断言在 connection 包内包级可见 seam 上做（如新增 `io.nop.metadata.service.connection` 包测试类直接调包私有 `validateJdbcUrl`/extract 方法，`validateJdbcUrl` :214 为包级可见），不断开真实连接，避免对 TEST-NET 地址发起慢速真实建连**；既有 `127.0.0.1:3306` / `localhost:3306` / RFC1918 host:port 拒绝用例全部保持 green
- [x] **extractWebhookHost 同步修复（Fix）**：`CheckpointActionDispatcher:303-331` 与 extractHost 相同首冒号截断——`http://fe80::1:3306/` 同样在进入 HostSecurityUtil 前被截断为 `"fe80"`，"仅依赖 Phase 1 归一化"**不成立**，本侧需同款提取修复（**触发条件与分割规则同上，唯一判据是通用头部 IP 解析规则，不按字符串前缀特判**——`FE80::1:3306` 大写形态同样命中 link-local 判定）；`TestCheckpointActionDispatcherWebhookSsrf` 补 `http://localhost.:8080/`（尾点）与 `http://::1:3306/` / `http://fe80::1:3306/` / `http://::ffff:127.0.0.1:3306/`（无括号 IPv6 / mapped 变体）拒绝用例；既有 `http://127.0.0.1:8080/` 等拒绝用例保持 green
- [x] **allowlist 语义核对（Proof）**：`allowed-hosts` 精确匹配对 `::1:3306` 形态（host 校验层已归一化后传入的 host）的语义核对——归一化后的 host（`::1`）与配置项一致时放行、不一致时拒绝，记录核对结论（如配置文档语义需同步则补一句，以 live 为准）
- [x] **全量回归（Proof）**：`./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）；`TestHostSecurityUtil` / `TestMetaDataSourceConnectionSecurity` / `TestCheckpointActionDispatcherWebhookSsrf` 全部通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] JDBC 路径：`::1:3306` / `fe80::1:5432` / `::1` URL 建连被拒（测试断言经真实入口；需要时 jshell/驱动实测记录——R5.1 及审计已有驱动实测先例，本计划以 host 校验层断言为主、建连实测为辅）
- [x] webhook 路径：`localhost.` 尾点 URL 与 `::1:3306` / `fe80::1:3306` 无括号 IPv6 URL 均被拒（测试断言）
- [x] **接线验证**：extractHost / extractWebhookHost → HostSecurityUtil 调用链在修复后实际生效（测试走 URL → 提取 → 校验的真实入口，而非直接调工具方法）
- [x] 全量模块测试全绿
- [x] `No owner-doc update required`（Phase 1 裁定项复核 + allowlist 语义核对结论；如 Phase 1 已更新则此处不再重复）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-02（无括号 IPv6 绕过）已修复：`::1:3306` 等形态在 JDBC 与 webhook 双侧均被拒
- [x] AR-03（FQDN 尾点绕过）已修复：`localhost.` / `a.localhost.` 在双侧均被拒
- [x] 全部正反回归用例已落地且判别性有效（red 先于修复）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 无 owner-doc drift 残留（如模块文档描述 host 校验规则则已同步）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）extractHost/dispatcher → HostSecurityUtil 运行时调用链连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"——根 pom 的 checkstyle 插件仅存在于 `-Pqa` profile）

## Deferred But Adjudicated

（无——本计划两项 finding 均为 confirmed live security defect，全部 in-scope 修复。）

## Non-Blocking Follow-ups

- AR-11~AR-23（2026-08-05-2157 审计 P2 批）已登记 `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md` `## Follow-up Backlog`，不属本计划范围

## Closure

Status Note: **allowlist 语义核对结论**——`allowed-hosts` 精确匹配发生在 host 校验层归一化**之后**：`jdbc:mysql://::1:3306/db` 提取归一化出 `::1`，配置 `::1` 时放行、配置 `::1:3306` 整体串时不匹配仍拒绝（TestMetaDataSourceConnectionProcessorExtract 两测钉死）；与 R5.1 行为一致（对比点 extractHost 之后），docs-for-ai 无该配置语义描述 → 无配置文档同步。**closure audit 第 1 轮 NOT_READY 发现并修复**：`isIpLiteral` 字符集守卫初版仅接受小写十六进制，`FE80::1:3306` 大写形态在提取层被判"非字面量"回退首冒号截断为 `FE80` 放行（计划 :97 声明的"大写形态同样命中 link-local 判定"未落地）→ 修复为 `[0-9a-fA-F:.]` + 双侧补大写回归测试（webhook/JDBC/keep-green 三处），第 2 轮复审 READY_TO_CLOSE。已知 Minor（审计认可非阻塞）：无端口无括号 `2001:db8::1` 在 URL 层被首冒号回退截断为 `2001` → 0.0.0.0/8 误判内网拒绝（fail-closed 方向，外网被误拒、内网绝不放行；util 层 `2001:db8::1` 判外部正确；超出本计划范围——URL 层无端口形态仅承诺 `::1`）。
Completed: 2026-08-06（Phase 1-2 全部完成，Plan Status → completed；`./mvnw test -pl nop-metadata -am -T 1C` **942 tests / 0 failures**；check-plan-checklist --strict exit 0 + check-doc-links --strict 0 errors + scan-hollow-implementations --severity high exit 0；roadmap R7.1 → done；独立 closure audit 两轮（第 1 轮 NOT_READY 发现大写变体 → 修复 + 回归 → 第 2 轮 READY_TO_CLOSE））

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_02be7f812ffeUYh62OrDWqR4Ss`，两轮）
  - **第 1 轮（修复前）**：8 项核查 6 PASS / 2 FAIL——FAIL-1：`FE80::1:3306` 大写形态绕过（isIpLiteral 小写限定 → 回退首冒号截断 `FE80` → 放行，JDBC/webhook 双侧，实测编译追踪）；FAIL-2：计划未收口（Status active + 12 gates 未勾，为执行收尾期状态）。PASS：AR-02/AR-03 util 归一化、extractHost、接线 anti-hollow、测试判别性、无静默降级
  - **修复 + 回归**：isIpLiteral 两处 `[0-9a-fA-F:.]` + 双侧大写测试（TestCheckpointActionDispatcherWebhookSsrf.testUnbracketedIpv6UppercaseLinkLocalWithPortBlocked / TestMetaDataSourceConnectionSecurity.testUnbracketedIpv6UppercaseLinkLocalWithPortRejected / TestHostSecurityUtil.testKeepGreenRegressionGuards 补 `FE80::1:3306`）→ 聚焦 84/0 + 全量 942/0
  - **第 2 轮（修复后）**：**READY_TO_CLOSE**——8 项全 PASS（大写变体编译追踪 `FE80::1:3306` → host `FE80::1` → link-local 拦截实证；surefire 942/0 独立复核；Minor 无端口 `2001:db8::1` 过拒 = fail-closed + 计划外，认可非阻塞）；check 7 机械收尾项（Status/gates/证据）本 closure 段已补齐

Follow-up:

- no remaining plan-owned work（关闭时确认）——AR-11~23 归 roadmap Follow-up Backlog，本计划无遗留
