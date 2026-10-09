# 2373 full-code-review R1.8 — 密钥与 codec 安全面修复批（P1-2.4-001 / P1-check2-nosql-67 / P1-check-mcc-172 / P1-3.10-001）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.8 行；四 finding（`2.4-nosql-cdc-deep-audit.md` rl-dim15-01 + check2/nosql-67 复核承接；`3.10-auth-credential-incremental-audit.md` §2.3.2/§2.4 + CRED-01 勘误）
> Related: 与 P1-check-mcc-172 同批互引（P1-check2-nosql-67 codec 修复）；P1-3.10-001 为安全台账链文档修正（非代码）

## Purpose

修复四项安全面 P1：①Redis 限流双 key 无 hash tag（Cluster 下 CROSSSLOT 必炸）；②PrefixTextCodec 类型不对称致 MFA 票据功能性失效（方向 a 最小改=消费点 peek 解析）；③主密钥 passphrase 经异常 param 泄漏（Default+Vault 7 个全 entry 参数点 keyId-only 化）；④CRED-01 安全台账勘误+ledger 补 :172。

## Current Baseline

- **①P1-2.4-001**：`LettuceRateLimiter.tryAcquireAsync`（`nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRateLimiter.java:42-43`）构造 `key+":tokens"`/`key+":timestamp"` 双 key 传 Lua（KEYS[1]/KEYS[2]）——无 hash tag，Redis Cluster 按槽分片必 CROSSSLOT。审计实测：单节点 cluster 必炸+hash tag 修复实测可行。测试盲区：`TestLettuceClusterService`（容器化 cluster，7 用例）零 rateLimiter 用例。
- **②P1-check2-nosql-67**：`PrefixEncodeHelper.encode` Number→裸十进制文本、`decode` 无 `$` 前缀原样返回 String（javadoc 自我声明）；消费点 `RedisMfaChallengeStore.peek`（`nop-auth/nop-auth-service/.../mfa/store/RedisMfaChallengeStore.java:117-122`）`ticket instanceof Number` 恒 false → `setVerifiedAt(null)` → `nop.auth.mfa.store-type=redis` 下 MFA 验证功能性失效（fail-closed 无旁路）。**修复方向 a（审计确认最小改）=消费点 peek 处 String 数值解析**；方向 b（codec 类型保留编码）捆绑回滚毒化评估被否。审计并要求：修复后补**经真实 codec 往返**的回归（FakeNosql 桩绕过真实序列化是缺陷逃逸根因）。
- **③P1-check-mcc-172**：`DefaultCredentialKeyProvider.init`（`nop-credential/nop-credential-service/.../crypto/DefaultCredentialKeyProvider.java:106-140`）5 个 throw 点中 4 个 param 完整 entry（含真实 passphrase——L122-123 pattern 不匹配、L133-135 重复 keyId 为高频运维现实；L114-115 缺冒号形态整串入 param）；`VaultCredentialKeyProvider`（`.../kms/vault/VaultCredentialKeyProvider.java:250-274`）migration 循环 3 个全 entry 点。样板在位：Vault keyId-active 点 :268-269 已是 `param(ARG_KEY_ID, keyId)` keyId-only。`NopException.getMessage` 无条件拼接 params 全量（:383-389）→ 启动失败日志携带可解密 cv1: 密文的主密钥口令。审计修复指令：「Default 侧五点与 Vault 侧三点统一改为 param(ARG_*_ENTRY, keyId 或截断前缀) + 原因码区分；迁移窗口场景同时 param 截断后的 entry 前缀（不含冒号后段）」。
- **④P1-3.10-001**：`ai-dev/audits/security-audit/2026-09-19-credential-audit-CRED-01~04.md` CRED-01「no finding」声明与 live 矛盾（:172 已修复但台账零记录——假阴性声明误导了一轮零处置）；`consolidation-summary.md` ledger 缺 :172 行。纯文档修正（勘误注+补行）。
- sql/集群测试基建：`TestLettuceClusterService` 用 testcontainers Redis Cluster；`TestLettuceRateLimiterValidation` 存在（单节点验证形态）。

## Goals

- 限流双 key hash tag 化：`{key}:tokens`/`{key}:timestamp` 同槽；集群测试补 rateLimiter 用例（真实 CROSSSLOT 防线）。
- MFA peek 数值解析（方向 a）：String 数字 → long verifiedAt；经真实 PrefixTextCodec 往返的回归测试。
- 7 个全 entry 参数点 keyId-only/截断前缀化（含 Vault 迁移窗口点保留截断前缀），passphrase 不再进任何异常 param。
- CRED-01 勘误注 + consolidation ledger 补 :172 行（台账链一致）。

## Non-Goals

- 不改 PrefixEncodeHelper 编码格式（方向 b 被否——存量 Redis 数据回滚毒化）。
- 不做 rateLimiter 生产调用方接线（审计 caveat：全仓零调用方，nop-auth 刻意单键实现——本批只修库缺陷+测试盲区）。
- 不修 consolidation ledger 其余 CRED 行（:355 P3 watch 在台账）。

## Scope

### In Scope

- `LettuceRateLimiter`：key 构造加 `{}` hash tag。
- `TestLettuceClusterService`：新增 rateLimiter 用例（容器内真实 cluster 执行 tryAcquire）。
- `RedisMfaChallengeStore.peek`：String 数值解析（`Long.parseLong` 于数字形态 String，非数字保持 null 语义——fail-closed 不变）。
- 新增经真实 codec 的回归测试（PrefixTextCodec encode→decode→peek 判定全链，无桩）。
- `DefaultCredentialKeyProvider` 5 点 + `VaultCredentialKeyProvider` 3 点：param 改 keyId/截断前缀（解析前点用「冒号前截断」+ 明确不输出冒号后内容）。
- 安全台账文档：CRED-01 勘误注 + consolidation-summary 补 :172 行。

### Out Of Scope

- PrefixEncodeHelper 编码变更；rateLimiter 生产接线；CRED-02~04 复审。

## Execution Plan

### Phase 1 - 三代码修复 + 回归

Status: completed
Targets: `LettuceRateLimiter.java`、`RedisMfaChallengeStore.java`、`DefaultCredentialKeyProvider.java`、`VaultCredentialKeyProvider.java`、对应测试

- Item Types: `Fix`

- [x] `LettuceRateLimiter`：`"{" + key + "}:tokens"` / `"{" + key + "}:timestamp"`（**两处构造点全改**：tryAcquireAsync :42-43 写侧 + getAvailableTokensAsync :77 读侧——审查 B1 实证读侧不改则读空返回 capacity 单节点即错；或抽共享 key 派生 helper）
- [x] `TestLettuceClusterService` 新增 rateLimiter 用例：注入 rateLimiter service 后 tryAcquire 在真实 cluster 上执行成功（修复前 CROSSSLOT）
- [x] `RedisMfaChallengeStore.peek`：`ticket instanceof Number` 之外补 String 数值解析（`Long.parseLong` try/catch NumberFormatException→null 语义保持 fail-closed）
- [x] 新增真实序列化往返回归（auth-service 侧）：`PrefixEncodeHelper`（PrefixTextCodec 的实际编解码实现，nop-core 已在类路径——审查 B4：PrefixTextCodec 类本身不在 auth-service 类路径）Long→encode→decode 往返喂 `FakeNosqlService`（仅传输桩、序列化真实）→ peek 断言 verifiedAt 非 null；先例 `TestMfaChallengeJsonCompat:141-145`；并点名运行 `OperationMfaCheckerImpl`/`requireChannelProof` 两条消费链既有测试（audit §2.4 要求）
- [x] `DefaultCredentialKeyProvider`：4 个全 entry param 点改 keyId（解析后点）；**缺冒号点（L114-115）可执行规则**——`colonIdx > 0` → param 取 `entry.substring(0, colonIdx)`（尾冒号形态即真实 keyId）；`colonIdx <= 0`（缺冒号/冒号首位，整串可能是裸 passphrase）→ param 用静态标记 + `entry.length()`（**不输出任何 entry 派生字符**——审查 B3：字符前缀也是材料泄漏）
- [x] `VaultCredentialKeyProvider`：3 个全 entry param 点同改（blank 点 String.valueOf(null-entry) 加 null 守卫；迁移窗口点按审计建议保留截断前缀 param 辅助运维定位）
- [x] 原因码区分（审计「+原因码区分」指令落地）：Default 四形态细分——缺冒号/pattern 不匹配/空白/重复各具名 param（reason 字段或细分错误码），日志可分辨（param keyId-only 化后共用同一错误码不可分辨——审查 advisory）
- [x] 泄漏回归测试：构造 pattern 不匹配/重复 keyId 配置 → 断言抛出的 NopException `getMessage()` 不含 passphrase 内容（含 keyId）
- [x] stash 判别：三修复分别 stash 跑对应新测试，预期 FAIL（CROSSSLOT/verifiedAt null/message 含 passphrase），留输出入 daily log（**hash tag 的 CROSSSLOT 判别须 docker-enabled 运行** `-Dnop.test.docker.enabled=true`——审查核实的双门控形态）

Exit Criteria:

- [x] 新回归全绿；stash 判别记录在案
- [x] `./mvnw test -pl nop-persistence/nop-nosql-lettuce -am` 全绿（集群测试若依赖 docker 环境，记录其运行形态与结果）
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 全绿
- [x] `./mvnw test -pl nop-credential/nop-credential-service,nop-credential/nop-credential-kms-vault -am` 全绿（审查 B2：kms-vault 主依赖仅 api，credential-service 是其 test scope——service 命令构建不到 vault 侧）
- [x] Phase 1 owner-doc 裁定：`ai-dev/design/nop-nosql/` 未记录 `:tokens` 键布局、keyId-only 化不改配置契约 → No owner-doc update required（审查核实）；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 台账文档修正与收口

Status: completed
Targets: `ai-dev/audits/security-audit/2026-09-19-credential-audit-CRED-01~04.md`、`2026-09-19-consolidation-summary.md`、roadmap、轮次 index、本 plan

- Item Types: `Fix`（台账文档）、`Proof`

- [x] CRED-01 审计文件勘误：按 3.10 审计建议①**收窄「no finding」声明适用范围**（非仅加注）——声明与 live 矛盾点（msg-cluster-cred:172 已修复未记录）引 plan 2373
- [x] consolidation-summary ledger 补 :172 行（ID 沿源缩写惯例 `F-MCC-1`；同步更新 `### HIGH (1)` 计数标题——审查 advisory）
- [x] roadmap R1.8 行 → done；index P1-2.4-001 / P1-check2-nosql-67 / P1-check-mcc-172 / P1-3.10-001 四行 → fixed + MR 表 R1.8 行
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 台账文档修正与 index/roadmap 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P1-2.4-001 修复落地：hash tag + 集群用例（或记录容器环境不可用的替代验证形态）
- [x] P1-check2-nosql-67 修复落地：peek 数值解析 + 真实 codec 往返回归
- [x] P1-check-mcc-172 修复落地：7 参数点 keyId-only 化 + 泄漏回归锁定
- [x] P1-3.10-001 修复落地：CRED-01 勘误注 + ledger 补行
- [x] focused verification：nosql-lettuce/auth-service/credential-service 三模块绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：三修复 stash 判别真实 FAIL→PASS
- [x] 受影响模块测试全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### rateLimiter 生产调用方接线

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审计 caveat 在案（全仓生产配置零 cluster-nodes、rateLimiter 生产调用方为零、nop-auth 刻意单键实现）；本批修复库缺陷+测试盲区即收口 finding 本体
- Successor Required: `no`

## Non-Blocking Follow-ups

- CRED-02~04 ledger 复审（P3 watch 通道）

## Closure

Status Note: 安全四批修复全部落地并经独立审计双 stash 判别复现（CROSSSLOT docker 实测逐字吻合审计探针/verifiedAt null/泄漏回归）；closure audit 发现的 hash tag 下游断裂（TestLettuceNosqlService 预置键名）已修正，lettuce 全模块 docker-on 77/77 零跳过全绿；R1.6c 下游回归（nop-orm TestOrmTemplate 4 期望）透明修正并留档；CRED-01 勘误+ledger 补行完成。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_915c5809-aeba-4e84-bd1e-6da224bda5d8
- Evidence:
  - **Anti-Hollow 两项 stash 判别独立重做**：PASS——(a) hash tag：auditor docker 实测 stash 后 `CROSSSLOT Keys in request don't hash to the same slot`（与审计探针逐字同款）→ pop 后 8/8 绿；(b) peek：stash 后 `expected: not <null>` FAIL（对照用例绿）→ pop 后 2/2 绿。
  - **P1-2.4-001**：PASS——两 helper `{key}` 包裹（:44-50），tryAcquireAsync(:54-55)/getAvailableTokensAsync(:89) 两构造点全改；集群用例断言 allowed/remaining/超容拒。
  - **P1-check2-nosql-67**：PASS——peek 三分支（Number/String 解析/else）+parseTimestampText fail-closed 保持；真实 PrefixEncodeHelper 往返 2 用例。
  - **P1-check-mcc-172**：PASS——Default entryInvalid reason 五值、param 仅 keyId/长度标记；Vault migrationKeyInvalid + 迁移窗口截断前缀；7 点全覆盖；泄漏回归断言真实（两侧 secret 不在 + keyId 保留）。
  - **P1-3.10-001**：PASS——CRED-01 勘误为声明收窄（点名 5+3 throw 点、引台账号）；consolidation F-MCC-1 行 fixed + 表头 HIGH (2)。
  - **状态回填四处一致**：PASS——roadmap :139/index 四行/MR :264/daily log。
  - **Blocking-1（closure audit 发现，已修）**：hash tag 改键名破坏预存 docker-gated 测试 TestLettuceNosqlService:878 testRateLimiter_FractionalTokens 预置键名——已同步 `{test:rl:frac}:tokens`，**lettuce 全模块 docker-on 77/77 零跳过全绿**（closure 后复跑）。
  - **R1.6c 下游回归透明记录**：nop-orm TestOrmTemplate 4 用例期望更新为 DML 租户注入后契约（SimsClass useTenant；注入即审计裁定的跨租户写面闭合）——plan 2371 验证面缺口（只覆盖 nop-orm-eql）在此留档。
  - **验证环境注记**：nop-auth-service 全链需 install（nop-sys-dao 测试编译断裂=R1.17 在案预存，`-Dmaven.test.skip=true` 打通）；nop-stream-runtime TestStreamConnectorRegistryDiscovery FAIL=预存 P1-7.2-001（R1.23 承载），与本批无关。
  - Advisory 处置：A1 随本 closure 提交（已做）；A2 consolidation :80 计数段勘注（已补）；A3 no-colon 分支更保守（字面偏差留档）；A4 @Order(5) 重复（无害留档）。

Follow-up:

- rateLimiter 生产调用方接线（Deferred But Adjudicated，out-of-scope improvement）
- CRED-02~04 ledger 复审（P3 watch 通道）
