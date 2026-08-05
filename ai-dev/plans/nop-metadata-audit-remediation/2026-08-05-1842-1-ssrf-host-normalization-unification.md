# SSRF 主机校验统一修复（HostSecurityUtil——JDBC 建连 + webhook 投递双入口 IP 记法归一化）

> Plan Status: completed
> Last Reviewed: 2026-08-05
> Draft Review: 2 轮独立子 agent 对抗性审查 consensus——R1 `ses_02e7846f4ffe2K257F6K4QIvJM`（1 Blocker：testOctalDottedIpv4Blocked 与严格十进制语义矛盾，已处置；3 Major：127.1 已被 fast path 拦截/2130706433 已有测试的基线修正、JDK textToNumericFormatV4 规格欠定义、172.16 族 over-blocking 移除未声明——全部修复）；R2 `ses_02e6f00f9ffek8ncFi0NCu5BbZ`（0 Blocker，1 Major：非 IP 字面量命中内网前缀的 fail-closed 裁定——已按建议方向修复并补测试向量；3 Minor 文本修正）。全部 Blocker/Major 清零，裁定可执行。
> Source: `ai-dev/audits/2026-08-05-0655-multi-audit-nop-metadata-audit-remediation.md`（[P1-01][P1-02]）、`ai-dev/audits/2026-08-05-0655-open-audit-nop-metadata-audit-remediation.md`（[AR-01][AR-02]）
> Related: 执行顺序 `{1}` of 3 — 本计划为最高优先级（2 处实测可绕过的 SSRF）；与 `{2}`、`{3}` 无代码面冲突（不同文件域），可并行但按安全优先级先行
> Mission: nop-metadata-audit-remediation

## Purpose

消除 nop-metadata-service 中两处 `isInternalHost` 实现的 IP 记法归一化盲区，使 JDBC 建连校验与 webhook 主机校验的"内网判定"与 JDK `InetAddress` 实际解析语义严格一致，堵住十进制整数 / 前导零 / 1-4 段短格式 / 八进制错配四类绕过向量，并以共享工具类 + 双侧回归测试一次性消除同根因家族。

## Current Baseline

2026-08-05 live repo 核对（jshell 实测 JDK 21/26 解析语义，审计报告记录）：

- **`MetaDataSourceConnectionProcessor.isInternalHost`**（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:343-375`）：纯字符串前缀比对（`localhost`/`127.`/`10.`/`192.168.`/`172.`/`169.254.`/`::1`），**无任何记法归一化**——`2130706433`（→127.0.0.1）、`010.0.0.1`（→10.0.0.1）、`0169.254.169.254`（→169.254.169.254）、`0.1`/`0.256`（→0.0.0.0/8）全部放行；`extractHost`（:269-298）已含 MA7.2-01 修复（userinfo 剥离 + `[::ffff:x.x.x.x]` IPv4-mapped），但产出 host 直接进入无归一化的 `isInternalHost`
- **`CheckpointActionDispatcher.isInternalHost`**（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/CheckpointActionDispatcher.java:308-378`）：MA7.6-04 修复已覆盖 4 段点分/十进制整数/IPv4-mapped/IPv6 字面量，但（a）`looksLikeDottedIpv4`（:406-417）要求恰好 3 个点 → 1-2 段短格式 `0.1`/`0.256` 漏判放行（JDK 解析：`0.1→0.0.0.1`、`0.256→0.0.1.0`，均属 0.0.0.0/8）；(b) `parseDottedIpv4`（:420-449）对前导零段按 **radix=8（inet_aton 语义）**，与 JDK 严格十进制错配——`0127.0.0.1` 被判 87.0.0.1（放行）而 JDK 解析为 127.0.0.1（loopback）；`0169.254.169.254` 同理放行
- **已拦截（非缺口，基线修正）**：`127.1` 两入口均已被 fast path `startsWith("127.")` 拦截（非放行向量）；dispatcher 侧 `2130706433`（`testDecimalIntegerIpv4Blocked` :182-185）与 `0.0.0.0`（`testZeroZeroZeroZeroBlocked` :175-178）已有回归测试
- **既有测试冲突点（必须处置）**：`TestCheckpointActionDispatcherWebhookSsrf.testOctalDottedIpv4Blocked`（:188-192）按 inet_aton 语义断言 `0177.0.0.1`（注释"= 127.0.0.1"）必须被拒——JDK 严格十进制下 `0177.0.0.1 → 177.0.0.1`（外部），该测试断言与修正后语义**不可同时成立**，Phase 3 必须显式改写（见 Phase 3）
- **行为反向变更（over-blocking 移除，需显式声明）**：两入口当前 fast path 拦截 `172.16`/`172.17`…`172.31` 二段形式（`172.` 前缀 + parts[1]∈16-31），JDK 解析 `172.16 → 172.0.0.16`（第二段 0，非 RFC1918）——统一到 JDK 语义后该族变放行；`0177.0.0.1` 同理从拦截变放行（其既有测试 `testOctalDottedIpv4Blocked` 断言方向相反，Phase 3 改写）。均为"向 JDK 语义收敛"的有意变更；`172.16` 族无既有测试覆盖（核实），`0177.0.0.1` 有既有测试但将按新语义改写
- **注释漂移**：`CheckpointActionDispatcher.java:301` 注释声称"与 MetaDataSourceConnectionProcessor 一致"，实际两实现不一致（git 验证 MA7.6-04 提交 `9b769490e` 只修了 dispatcher）
- **既有回归测试**：`TestMetaDataSourceConnectionSecurity.java`（22 个 @Test，MA7.2-01 分组 4 个）、`TestCheckpointActionDispatcherWebhookSsrf.java`（16 个 @Test；`TestCheckpointActionDispatcher*` 三文件合计 24 个）——均无 `0.1`/`0.256`/`0127.*`/`010.0.0.1` 类向量
- **JDK 21+ 勘误口径**：`0177.0.0.1`/`0x7f.*` 不可利用（严格十进制解析，`0177.*`→177.0.0.1 外部；`0x*` 段/整数非 JDK 数值形式，`textToNumericFormatV4` 纯十进制），不列为正例
- 绿色基线：`./mvnw test -pl nop-metadata -T 1C` 867 tests / 0 failures（R4.3 收口，860→867 口径）

## Goals

- 实现共享主机安全判定工具（决策落点：`nop-metadata-service` 内共享类），内网判定语义与 JDK `textToNumericFormatV4` 严格十进制 + 1-4 段位移解析一致，覆盖：localhost hostname / IPv4（全部记法变体）/ 0.0.0.0/8 / IPv6 loopback + link-local + IPv4-mapped；纯确定性解析，不触发 DNS
- 两个调用方（JDBC 建连校验 + webhook 投递校验）统一调用共享工具，删除各自重复实现，消除注释漂移
- 双侧回归测试补齐 jshell 验证过的正负向量（每个入口各 4+ 正例 + 2+ 负例）
- 明确 fail-closed 语义：无法判定为外部主机时不因解析歧义放行内网地址

## Non-Goals

- 不引入 DNS 解析/反向解析（保持现有"不触发 DNS"设计约束）
- 不改动 `MetaDataSourceConnectionProcessor` 的协议白名单 / 危险参数黑名单 / 驱动白名单 / loginTimeout / 数据源 allowlist 机制（均已核验生效，仅内网判定这一道防线在 scope 内）
- 不改动 `CheckpointActionDispatcher` 的 userinfo 剥离 / fail-closed 默认禁内网 / method 白名单 / HTTP 客户端重定向行为（P2-11 归 backlog）
- 不处理 rawJdbcUrl 凭据进异常参数问题（P2-12 归 backlog）
- 不触碰 `nop-core`/`nop-xlang` 等平台模块（框架核心 Protected Area 不在 scope）

## Scope

### In Scope

- 共享主机安全判定工具实现（Decision：包位置/类名/公开方法签名）+ 与 JDK 语义对齐的记法归一化（严格十进制、1-4 段 24/16/8 位移位、0.0.0.0/8、前导零、IPv4-mapped、IPv6 字面量）
- `MetaDataSourceConnectionProcessor.isInternalHost` 改接共享工具 + 行为回归测试（正负向量）
- `CheckpointActionDispatcher.isInternalHost` 改接共享工具（废弃 inet_aton 八进制分支 + 短格式补全）+ 行为回归测试（正负向量）
- 注释漂移修正（:301 一致性注释）
- arm-index / roadmap 对应行终态更新（本计划引用）

### Out Of Scope

- 数据源 allowlist（`resolveAllowedInternalHosts`）语义变更——保持"原始 host 字符串比对"行为不回归（执行时核实）
- webhook URL 解析（`CheckpointActionDispatcher` 的 host 提取段）本身
- 其他模块/平台代码

## Execution Plan

### Phase 1 - 共享工具实现 + 单测（JDK 语义对齐）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/`（新共享类，落点 Decision）+ 新建单测文件

- Item Types: `Decision | Fix | Proof`

- [x] **落点裁定（Decision）**：共享类落点（如 `service/security/HostSecurityUtil.java` 或既有包内）——以两调用方均可达、无循环依赖为约束；公开方法覆盖 hostname（localhost/*.localhost）、IPv4 记法变体、IPv6 字面量判定；**host 输入先 `trim()`**（与 dispatcher 既有行为一致，processor 由 extractHost 产出，工具层统一 trim 防 `"localhost "` 类绕过）
- [x] **归一化语义实现（Fix，JDK `textToNumericFormatV4` 规格）**：点分形式按 1-4 段严格十进制解析——`v=0`；对前 n-1 段：每段须 ≤255（字节位移），`v = (v << 8) | seg`；末段：须 ≤ `2^(8*(5-n))-1`（n=1: 2^32-1、n=2: 2^24-1、n=3: 2^16-1、n=4: 255），`v = (v << 8) | seg`；得到 32 位 IPv4 值 → 拆 4 octets（例：`0.1`→0.0.0.1、`0.256`→0.0.1.0、`127.1`→127.0.0.1、`2130706433`→127.0.0.1）；**前导零段按严格十进制（废弃 inet_aton 八进制语义）**；`0.0.0.0/8`（首 octet=0）判内网；**非 IP 字面量（含非十进制字符段，如 `0x7f`、`127.abc`、`10.0.0.1.nip.io`）→ 走 hostname 路径：先做既有字符串前缀比对（localhost/127./10./192.168./172.16-31/169.254.，fail-closed 维持拦截，与既有 fast path 行为一致），未命中前缀才判外部**（不触发 DNS）；**工具输入契约**：接收不带方括号的 host（两调用方已在调用前剥离），工具层统一 `trim()` 后再判定
- [x] **0x 十六进制裁定（Decision）**：JDK `textToNumericFormatV4` 不支持 0x（纯十进制，jshell 实测 `0x7f000001`/`0x7f.0.0.1` 抛 UnknownHostException）——裁定：**保留 0x 整数形式（`0x7f000001`）拦截为 fail-closed 超集**（点分段含 0x 段则按上条走 hostname 路径）；偏离 JDK 的理由显式记录（JDK 视为非法 hostname、DNS 不可解析，放行亦无害，但保守拦截成本为零）
- [x] **行为反向变更声明（Decision）**：`172.16`~`172.31` 二段形式（JDK→172.0.0.16 等，第二段 0 非 RFC1918）与 `0177.0.0.1`（JDK→177.0.0.1 外部）由"拦截"变"放行"——向 JDK 语义收敛的有意变更，记录于本 plan + arm-index（closure 时防误判为回归）；**非 IP 字面量命中内网前缀（如 `127.abc`/`10.0.0.1.nip.io`）维持拦截（fail-closed，行为不回归）**；十进制整数形式（`2130706433`）与 0x 形式（保留拦截）归一化；IPv4-mapped IPv6 `::ffff:x.x.x.x` 提取后复用 IPv4 判定；IPv6 字面量（`::1`、`fe80::/10`、`0:0:0:0:0:0:0:1`）字面量解析判定
- [x] **单测（Fix，Test-Mandated Feature Rule）**：新工具类专项单测覆盖全部分支——正例（判内网）：`0`/`0.1`/`0.256`/`127.1`/`2130706433`/`010.0.0.1`/`0127.0.0.1`/`0169.254.169.254`/`127.0.0.1`/`10.1.2.3`/`192.168.1.1`/`172.16.0.1`/`169.254.169.254`/`::1`/`::ffff:127.0.0.1`（不带括号，输入契约）/`localhost`/`a.localhost`/`127.abc`（命中前缀 fail-closed）/`10.0.0.1.nip.io`（命中前缀 fail-closed）；负例（判外部）：`0177.0.0.1`（JDK→177.0.0.1）/`172.16`（JDK→172.0.0.16，第二段 0 非 RFC1918）/`8.8.8.8`/`example.com`/`172.32.0.1`/`1.2.3.4.5`（5 段非法）/空串/`  localhost  `（trim 后判内网）；**区分性断言**（拒绝向量必须为内部判定、放行向量必须为外部判定，禁止断言"不抛异常"）；jshell 交叉验证记录（`textToNumericFormatV4` 语义逐向量核对）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 共享工具类存在且两调用方均可达（编译通过），公开语义与 JDK 解析一致（jshell 交叉验证记录）
- [x] 工具单测全绿且为区分性断言（正例判内网 / 负例判外网），覆盖 1-4 段短格式 + 前导零 + 十进制整数 + 0.0.0.0/8 + IPv4-mapped + IPv6 字面量
- [x] **无静默跳过**：非法/歧义输入走明确分支（非内网判定即放行仅限确定性外部地址；解析失败按 fail-closed 裁定，见 Phase 1 裁定），无 catch-and-continue 空分支
- [x] `No owner-doc update required`（docs-for-ai 无内网判定细节章节，已核实；工具类行为变更——0177/172.16 族由拦截变放行——为向 JDK 语义收敛的有意变更，已声明于本 plan Phase 1 + arm-index 终态）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MetaDataSourceConnectionProcessor 接线 + 回归测试

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java` + `TestMetaDataSourceConnectionSecurity.java`

- Item Types: `Fix | Proof`

- [x] `isInternalHost`（:343-375）改为委托共享工具（删除重复实现），保留 allowlist 比对语义（allowlist 比对用原始 host 字符串，与现有行为一致——执行时核实 `resolveAllowedInternalHosts` 调用点不回归）
- [x] **回归测试（Fix）**：`TestMetaDataSourceConnectionSecurity` 补齐 jshell 验证向量——正例（拒绝建连）：`jdbc:mysql://2130706433:3306/db`、`jdbc:mysql://0.1:3306/db`、`jdbc:mysql://0.256:3306/db`、`jdbc:mysql://010.0.0.1:3306/db`、`jdbc:mysql://0169.254.169.254:3306/db`（`127.1` 已被既有 fast path 拦截，作为回归向量保留）；负例（放行）：`jdbc:mysql://0177.0.0.1:3306/db`（JDK→177.0.0.1 外部）、`jdbc:mysql://172.16:3306/db`（JDK→172.0.0.16，第二段 0 非 RFC1918）、`jdbc:mysql://8.8.8.8:3306/db`、`jdbc:mysql://example.com:3306/db`（外部 hostname）；断言使用真实错误码 `ERR_DATASOURCE_JDBC_URL_BLOCKED`；**负例断言方式**：沿既有模式（`assertDoesNotThrow` + testConnect 会真实建连，loginTimeout=5s；CI 禁网时连接异常不抛 BLOCKED 码，快速失败可容忍）或捕获异常断言错误码非 BLOCKED——执行时按既有 `testUserinfoWithExternalHostPassesHostCheck`（:179-183）模式裁定，避免网络依赖误判
- [x] 接线验证：断言测试确实经 `extractHost` → 共享工具完整链路（不允许测试绕过处理器直接调工具类）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：`extractHost` → 共享工具 → `ERR_DATASOURCE_JDBC_URL_BLOCKED` 抛错链路对全部正例向量成立（拒绝），负例向量全部放行
- [x] **接线验证**：处理器 `isInternalHost` 运行时确实调用共享工具（测试经公开入口断言，非直接测工具类）
- [x] **无静默跳过**：无 catch-and-continue / 空方法体残留
- [x] 既有 4 个 MA7.2-01 用例不回归
- [x] `./mvnw test -pl nop-metadata -T 1C` 相关测试类全绿
- [x] `No owner-doc update required`（行为方向为安全加固收敛，docs-for-ai 未描述内网判定细节）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CheckpointActionDispatcher 接线 + 回归测试

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/CheckpointActionDispatcher.java` + `TestCheckpointActionDispatcherWebhookSsrf.java`

- Item Types: `Fix | Proof`

- [x] `isInternalHost`（:308-378）改为委托共享工具：删除 `looksLikeDottedIpv4`/`parseDottedIpv4`/`isInternalIpv4`/`isAllDigits`/`isHexInteger`/`parseIpv4Integer` 中与共享工具重复的部分（`isInternalIpv6Literal` :475 复用的 `isInternalIpv4` 一并按工具能力收敛），**废弃 inet_aton 八进制语义**；保留 hostname fast path 行为（localhost / *.localhost 及非 IP 输入不触发 DNS）
- [x] 修正 :301 注释（"与 MetaDataSourceConnectionProcessor 一致"→ 实际经共享工具统一，注释与实现不再漂移）
- [x] **回归测试（Fix）**：`TestCheckpointActionDispatcherWebhookSsrf` 补齐——正例（拒绝）：`http://0.1/`、`http://0.256/`、`http://0127.0.0.1/`、`http://0169.254.169.254/`（`127.1`/`2130706433`/`0.0.0.0` 已有用例覆盖，作为回归保留）；负例（放行）：`http://0177.0.0.1/`（JDK→177.0.0.1 外部）、`http://example.com/`（外部 hostname，fail-closed 语义下放行）；断言经真实 dispatcher 分派路径（非直接调工具）
- [x] **既有冲突测试处置（Fix，Blocker 项）**：改写 `testOctalDottedIpv4Blocked`（:188-192）——其断言"`0177.0.0.1`（= 127.0.0.1）必须被拒"基于 inet_aton 八进制假设（错误），JDK 严格十进制解析为 177.0.0.1（外部）；改写为**放行断言**（附理由注释：JDK 21+ 严格十进制），并入负例组；AGENTS.md 测试规则下属"按 JDK 语义修正错误断言"，非削弱保护
- [x] 全量 webhook 相关测试类回归（`TestCheckpointActionDispatcher*` 三文件 24 个既有用例按修正后语义复核：除 `testOctalDottedIpv4Blocked` 改写外其余不回归）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：webhook URL 从分派入口 → host 提取 → 共享工具 → 拒绝/放行的完整路径对全部正负向量成立
- [x] **接线验证**：dispatcher 运行时确实调用共享工具（测试经真实分派路径断言）
- [x] **无静默跳过**：无空 catch / continue / 返回默认值兜底残余
- [x] 既有 24 个用例按修正后语义复核（`testOctalDottedIpv4Blocked` 按严格十进制改写为放行断言，其余 23 个不回归——尤其 userinfo 剥离、`[::1]`、`[::ffff:127.0.0.1]`、十进制整数、0.0.0.0 已拦截用例）
- [x] `./mvnw test -pl nop-metadata -T 1C` 相关测试类全绿
- [x] `No owner-doc update required`（docs-for-ai 无 webhook 内网判定细节章节，已核实；行为变更声明见 Phase 1 + arm-index 终态）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（arm-index 终态 + closure audit）

Status: completed
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [x] arm-index-nop-metadata.md 本 plan 对应 P1 行（P1-01/P1-02/AR-01/AR-02，来源 multi-audit 2026-08-05-0655 + open-audit 2026-08-05-0655）终态（fixed + 本 plan 引用 + 修复摘要 + 测试证据）——**首轮登记由 plan {3} Phase 3 完成（planned），本 Phase 仅更新状态终态；若 {3} 未先行，则登记+终态一次完成**（与 plan {2}/{3} 收口段串行化，git 提交顺序保证同文件不并发编辑）
- [x] roadmap 对应工作项行更新（如适用；登记段由 plan {3} Phase 3 建立）
- [x] 独立子 agent closure audit（fresh session）逐项核对 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] arm-index + roadmap 终态一致可追溯（SSRF 归一化缺口两处全部 fixed）
- [x] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [x] `./mvnw test -pl nop-metadata -T 1C` 全绿（0 failures）
- [x] 无静默降级：两处 SSRF 缺口为 fixed，无 live defect 被降级
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] JDBC 建连 + webhook 投递两处内网判定已统一到共享工具且与 JDK 解析语义一致（十进制整数 / 前导零 / 1-4 段短格式 / 0.0.0.0/8 / IPv4-mapped / IPv6 字面量全覆盖）
- [x] 两入口各 4+ 正例（拒绝）+ 2+ 负例（放行）回归测试落地，jshell 向量全部可复现
- [x] 注释漂移修正（dispatcher :301 与实现一致）
- [x] 必要 focused verification 已完成（双侧端到端 + 既有用例不回归）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）处理器/dispatcher 运行时确实调用共享工具（调用链连通，非仅 import），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -T 1C`
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）

## Deferred But Adjudicated

### `0177.0.0.1` / `172.16`~`172.31` 二段形式（JDK 严格十进制下为外部地址）

- Classification: `watch-only residual`
- Why Not Blocking Closure: JDK 21+ `textToNumericFormatV4` 严格十进制解析——`0177.0.0.1` → 177.0.0.1（外部）、`172.16` → 172.0.0.16（第二段 0，非 RFC1918）；jshell 实测确认不可利用；本 plan 按 JDK 语义实现后该族自动一致，并由**改写后的 `testOctalDottedIpv4Blocked`（放行断言）+ 负例 `172.16` 测试钉死**防回归；既有"拦截"行为属 inet_aton 语义误判（over-blocking），移除为有意变更（Phase 1 裁定声明）
- Successor Required: `no`
- Successor Path: —

### 0x 十六进制整数形式（`0x7f000001`）

- Classification: `watch-only residual`
- Why Not Blocking Closure: JDK 不支持 0x（纯十进制，`UnknownHostException`），放行亦无害；本 plan 裁定保留拦截为 fail-closed 超集（Phase 1 裁定，偏离 JDK 语义但方向保守、成本为零），无需后续处理
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- `0x7f.0.0.1` 点分段十六进制形式的"外部判定"依赖 JDK 硬拒绝（歧义→UHE 不触发 OS getaddrinfo）；若未来 JDK 放宽歧义拒绝或非 JVM 网络栈接入，需复核（当前 JDK 21/26 实测不可利用，plan Deferred 段已裁定 watch-only）
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）

## Closure

Status Note: 两处实测可绕过的 SSRF 缺口（JDBC 建连 P1-01/AR-01 + webhook 投递 P1-02/AR-02）全部 fixed：共享 `HostSecurityUtil` 以与 JDK 实际解析一致（严格十进制 + 1-4 段位移 + mod 2^32 截断 + 0x fail-closed 超集）统一 processor/dispatcher 双侧入口，回归测试双侧补齐（889/0 全绿），独立 closure audit PASS。
Completed: 2026-08-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，read-only，task `ses_02e50d66fffeqToS5l4mpgpzds`，未复用实现 session）
- Evidence:
  - Phase 1 五条 Exit Criteria 全 PASS：`HostSecurityUtil.java:31-243` 全分支与 JDK 实测一致（audit 独立 jshell 复跑 13 个向量全部匹配，含 172.16→172.0.0.16、0177.0.0.1→177.0.0.1、4294967297→0.0.0.1）；`TestHostSecurityUtil` 16/16 区分性断言；无空分支/静默跳过；`No owner-doc update required` 核实（docs-for-ai grep 无内网判定章节）；log 已更新
  - Phase 2 七条全 PASS：`validateJdbcUrl → extractHost → isInternalHost → HostSecurityUtil` 链路（`MetaDataSourceConnectionProcessor.java:240-241,345-347`）；正例 5+1（2130706433/0.1/0.256/010.0.0.1/0169.254.169.254/127.1 → `ERR_DATASOURCE_JDBC_URL_BLOCKED`，`TestMetaDataSourceConnectionSecurity.java:189-216`）+ 负例 4（0177.0.0.1/172.16/8.8.8.8/example.com 放行 :220-232）；MA7.2-01 4 用例不回归
  - Phase 3 七条全 PASS：`validateWebhookUrl → extractWebhookHost → isInternalHost → HostSecurityUtil` 真实分派路径（`CheckpointActionDispatcher.java:252-259,307-309`）；`testOctalDottedIpv4Blocked` 改写为 `testOctalDottedIpv4Allowed` 放行断言（:208-215，附 JDK 严格十进制理由）；新正例 0.1/0.256/0127.0.0.1/0169.254.169.254 + 负例 172.16；三文件 19+6+2 全绿，24 既有用例仅 1 个按语义改写
  - Phase 4 产物 PASS：arm-index `P1-01/P1-02/AR-01/AR-02` 四行 fixed（含本 plan 引用 + 修复摘要 + 测试证据）；roadmap MR5 R5.1 done（889/0 全绿）
  - Closure Gates 12/12 PASS：`./mvnw test -pl nop-metadata -T 1C` **889/0/0/0**（audit 独立复跑确认）；`scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0（0 findings）；`check-plan-checklist.mjs --strict` exit 0
  - Anti-Hollow：两链路（JDBC/webhook）运行时调用连通性经代码追踪 + 端到端测试断言（fetchCallCount==0 拒绝 / ==1 放行）双重实证；无空方法体/no-op
  - Deferred 分类检查：0177.0.0.1 + 172.16~31 二段形式由拦截变放行 = 向 JDK 语义收敛的有意变更（watch-only residual，Successor: no）+ 0x 整数形式保留拦截（fail-closed 超集）——均有钉死测试（testInetAtonOctalLegacyExternal/testStrictDecimalExternalHostsPass/testOctalDottedIpv4Allowed/testTwoSegment172ExternalAllowed），无 in-scope live defect 被降级
  - Minor 3 条全非阻塞：①工作树未提交（交由 mission/用户流程）；②0x7f.0.0.1 dotted-hex 平台依赖（JDK 本机 UHE 无连接风险，已裁定）；③R5.1 测试计数为净增量口径（16→19 = +3 净）

Follow-up:

- no remaining plan-owned work（0x7f.0.0.1 dotted-hex 平台差异已裁定为 watch-only，见 Deferred But Adjudicated 与 Non-Blocking Follow-ups）

## Optional Sections

- `## Risks And Rollback`：共享工具语义若与 JDK 解析产生偏差（如新 JDK 版本解析规则变化），双侧判定同时受影响——用"与 JDK 源码语义对齐 + jshell 交叉验证"约束；回滚 = 两调用方恢复原实现 + 测试回退（均在一个 commit 内可逆）
- `## Outdated Note`：若执行期间发现平台层（nop-core 等）提供等效主机安全工具，优先复用并登记本 plan 工具为弃用
