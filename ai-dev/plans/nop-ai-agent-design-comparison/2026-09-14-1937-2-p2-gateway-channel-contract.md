---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-CHANNEL
group: "2026-09-14-1937"
verify: [test]
---

# P2 gateway channel 契约收口（MFA 分支钉子 + getCapabilities 归宿 + Feishu 凭证路径）

## Current Baseline

- 来源：deep-audit round 2 的 P2 Follow-up Backlog（roadmap `## Follow-up Backlog` 文档顺序第 21/22/23 项；M0–M6 全部 WI 已勾选，这是剩余未勾选集的末三项），全部经 live 复核（HEAD `c00173afae`，2026-09-14）：
  1. **ChannelLoginScanProcessor MFA 分支零测试 + 跨模块裸字符串契约**（`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/login/ChannelLoginScanProcessor.java`）：`MFA_REQUIRED_ERROR_CODE = "nop.err.auth.mfa-required"`（:37）在 :147 与 nop-auth-service 的 `NopAuthErrors.ERR_AUTH_MFA_REQUIRED`（`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/NopAuthErrors.java:97`）靠字符串精确匹配；gateway login 测试面（`TestChannelLoginApi`/`TestChannelLoginAccessCode`）对 MFA 分支零覆盖（`grep -i mfa` 零命中）。nop-auth 侧改名会静默把 MFA 从"挑战应答"降级为"透传失败"（catch 不匹配 → rethrow，客户端收到原始错误）。`nop-ai/nop-ai-gateway/pom.xml:90-114` 已裁定 gateway 不依赖 nop-auth-service（Candidate A 已被证明破坏既有测试套件，连 test scope 也不引入），常量无法跨模块 import——需在"保留字符串匹配 + 双向交叉引用 + 测试钉子"与"错误码定义上移 nop-auth-api（跨模块公共 API）"之间显式裁定。
  2. **IChannelConnector.getCapabilities() 无生产消费者**：`IChannelConnector.getCapabilities()`（`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/IChannelConnector.java:46`）与 105 行 `ChannelCapabilities`（同目录 `ChannelCapabilities.java`）唯一生产实现是 `FeishuConnector.getCapabilities()`（`.../channel/feishu/FeishuConnector.java:139-151`，自实现），生产代码零消费者（`grep getCapabilities` 仅接口/实现/测试 stub 命中）；`ChannelMessageServiceImpl.sendToUser`（:268-304）从不读 `maxMessageLength`/`supportsMarkdown`/`supportsFileUpload`。owner doc `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md:88` 承诺"供权限矩阵和消息格式化参考"、:260 承诺"supportsMarkdown=false 的信道需要转为纯文本"——承诺与 live 行为不一致。
  3. **FeishuConnector 凭证路径与 beans.xml 契约矛盾**：`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml:36-44` 注释称 FeishuCredentials bean 来自 nop-integration-feishu 的 `feishu-defaults.beans.xml`；但 `FeishuConnector` 只有 `@Inject protected FeishuClient`/`IChannelSessionStore`（:94-98），无 FeishuCredentials 注入字段；`resolveCredentials`（:669-691）只读 ChannelConfig options（`feishu.appId`/`feishu.appSecret`/`feishu.credentials`），最后兜底空凭证、延迟到 `FeishuClient.start` 才失败。平台标准 `nop.integration.feishu.credentialId`（`nopFeishuCredentials` bean，`nop-integration-feishu` 的 `feishu-defaults.beans.xml:14-19`，`@InjectValue("@cfg:nop.integration.feishu.*|")`）经连接器不可达；`TestFeishuConnector.channelConfigOptionsCredentialIdIsIgnored`（:339-355）把 options 面忽略 credentialId 固化为断言（该负向语义本身应保留——options 面不引入 credentialId 引用语义）。
- 归属模块：nop-ai-gateway（3 项全部）。
- 现有测试基建：`TestChannelLoginApi`/`TestChannelLoginAccessCode`（login 面）、`TestFeishuConnector`/`TestFeishuConnectorIoC`、`TestChannelMessageService`/`TestChannelMessageServiceIoC`/`TestChannelConnectorManager`/`TestIChannelConnectorWiring`。

## Goals

- MFA 分支有回归测试守护（挑战参数转发 + 非 MFA 异常透传 + null context 失败路径），跨模块错误码契约的裁定与守护方式落到代码注释/owner doc 并记录理由。
- getCapabilities 的归宿显式裁定：要么被生产路径消费（跨信道降级语义落地 + 测试），要么在 owner doc/javadoc 登记为 reserved 并消除过度承诺；不允许"宣称消费但零消费者"。
- FeishuConnector 凭证解析与 beans.xml 契约一致：平台标准 FeishuCredentials bean（`nop.integration.feishu.*`）经连接器可达；ChannelConfig options 面字面语义保持不变。
- 涉及模块 `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过；check-doc-links 0 error。

## Non-Goals

- 不给 nop-ai-gateway 引入 nop-auth-service 依赖（生产或 test scope 均不引入，维持 pom.xml:90-114 既有裁定）。
- 不实施错误码定义上移（若裁定为 nop-auth-api 上移，登记为 successor 计划，不在本计划 scope 内）；不改 nop-auth-service 的 `NopAuthErrors` 错误码 ID/参数契约。
- 不改 Feishu 协议层（nop-integration-feishu 的 `FeishuClient`/`FeishuCredentials` 契约不变）。
- 不处理 roadmap P2 第 19-20 项（文件工具安全，由同批 `2026-09-14-1937-1` 覆盖）。
- 不运行 mvn 全量构建。

## Phase 1 — ChannelLoginScanProcessor MFA 分支回归测试与错误码契约裁定

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/login/ChannelLoginScanProcessor.java`、`nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/login/`、`ai-dev/design/nop-auth/01-architecture-baseline.md`（MFA 跨模块契约 owner doc）、`ai-dev/design/nop-ai-channel-integration-design.md`（如需登记）

- Item Types: `Decision | Fix | Proof`

- [ ] `Decision` 错误码契约裁定：(A) 保留字符串匹配（gateway 不依赖 nop-auth-service 的既有裁定）并在两侧补交叉引用注释/文档 + 测试钉子，或 (B) 定义上移 nop-auth-api（跨模块公共 API，仅登记 successor，不在本计划实施）。记录选择理由与备选方案；A 为本计划可闭合路径。
- [ ] `Fix` MFA 分支回归测试：mock `ISessionBootstrap.createSessionForUserAsync` 同步抛带 `challengeToken`/`mfaType`/`loginType` 参数的 `NopException("nop.err.auth.mfa-required")` → 断言 `ScanLoginResult.mfaRequired=true` 且三参数转发；非 MFA `NopException` 原样 rethrow（不翻译）；返回 null context → `ERR_CHANNEL_LOGIN_SESSION_FAILED`。
- [ ] `Fix` 按裁定补跨模块契约守护（方案 A）：在 `ChannelLoginScanProcessor` 常量注释与 nop-auth-service `NopAuthErrors.ERR_AUTH_MFA_REQUIRED` javadoc 两侧补交叉引用（改名须同步消费者）；owner doc（`ai-dev/design/nop-auth/01-architecture-baseline.md` 为主，必要时 `ai-dev/design/nop-ai-channel-integration-design.md` §3.4）登记该契约；测试内嵌对照字面量作为漂移哨兵。
- [ ] `Proof` 复核 MFA 捕获分支的异常形态（同步 throw vs rejected future）与测试 mock 形态一致；复核 `MFA_REQUIRED_ERROR_CODE` 全部消费点仅 :147 一处。

Exit Criteria:

- [ ] login 分支有 ≥3 个新回归测试（MFA 挑战参数转发/非 MFA 异常透传/null context 失败），断言 `getErrorCode()` 与参数（非仅异常类型）。
- [ ] 错误码契约裁定落地：交叉引用/owner doc 与 live 代码一致，无"裸字符串无守护"状态。
- [ ] **端到端验证**：从 `execute(callback, provider)` 入口经 provider→binding→session 到 MFA/成功分支的完整路径覆盖。
- [ ] **接线验证**：MFA 捕获路径经 `execute` 运行时触发（测试经公开入口，非直调私有方法）。
- [ ] **无静默跳过**：非 MFA 异常不被吞，原样传播；MFA 分支参数缺失时不伪造默认值。
- [ ] owner doc 更新或 `No owner-doc update required`。
- [ ] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过。
- [ ] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 2 — getCapabilities 归宿裁定（消费或 reserved 登记）

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/IChannelConnector.java`、`ChannelMessageServiceImpl.java`、`ChannelCapabilities.java`、`ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md`、对应测试

- Item Types: `Decision | Fix | Proof`

- [ ] `Decision` 裁定：(A) 消费——在 `ChannelMessageServiceImpl.sendToUser` 的出站边界读 `connector.getCapabilities()` 并做通用降级（`!supportsMarkdown` 时 markdown→text；文本超 `maxMessageLength` 截断并附提示；`!supportsFileUpload` 时附件降级为链接/提示），或 (B) reserved——在 owner doc + `IChannelConnector`/`ChannelCapabilities` javadoc 明确标注"当前无生产消费者、为后续权限矩阵/格式化保留"，删除"used by message formatting"类承诺。记录理由，并明确与 FeishuConnector 自身降级（`deliverSegmented` 切片、附件链接化）的边界，避免双重转换。
- [ ] `Fix` 按裁定落地：方案 A 在 `sendToUser` 消费且保持 `SendResult` 语义不变，新增测试覆盖 markdown/超长/附件三种降级；方案 B 仅文档/javadoc 修订 + `No new test required` 说明（Minimum Rules #25）。
- [ ] `Proof` 复核方案 A 下同一能力只在一个边界生效（无双重转换），或方案 B 下 owner doc 与 javadoc 不再宣称消费。

Exit Criteria:

- [ ] getCapabilities 有明确归宿（被消费且测试为证，或 reserved 且文档一致）；不存在"承诺消费但零消费者"。
- [ ] 方案 A：三种降级行为有回归测试；方案 B：文档/javadoc 与 live 一致。
- [ ] **端到端验证**（方案 A）：从 `sendToUser` 入口经 resolver→connector 查找→capability 适配→`sendOutbound` 的完整路径覆盖；（方案 B 不适用）。
- [ ] **接线验证**（方案 A）：capability 读取在 `sendToUser` 运行时被消费（非仅新增方法）。
- [ ] **无静默跳过**：降级行为有明确语义与可观测结果，不静默丢消息/附件。
- [ ] owner doc 更新或 `No owner-doc update required`。
- [ ] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过。
- [ ] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 3 — FeishuConnector 凭证路径收口（注入 FeishuCredentials bean）

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java`、`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml`、`TestFeishuConnector.java`

- Item Types: `Fix | Proof`

- [ ] `Fix` 为 `FeishuConnector` 增加 `FeishuCredentials` 注入面（`@Inject protected` 字段 + 测试用 setter，NopIoC 禁 private 注入），`resolveCredentials` 解析优先级明确：ChannelConfig options 面（`feishu.appId`+`feishu.appSecret` 成对；`feishu.credentials` 对象，既有行为保留）→ 注入的 `nopFeishuCredentials` bean（`nop.integration.feishu.*` 配置）→ 空凭证兜底；options 面 `feishu.credentialId` 忽略语义保持不变；同步修订 `resolveCredentials` 内联注释（消除"注入 bean 已是 primary source"与 live 的偏差）。
- [ ] `Fix` `ai-gateway-defaults.beans.xml` 的 Feishu 段注释与 live 注入面对齐（说明 FeishuCredentials bean 的注入与优先级），消除"注释称注入但代码未注入"的矛盾。
- [ ] `Fix` 回归测试：无 options 凭证时注入 bean 被使用（appId/appSecret/credentialId 透传）；options 字面值仍优先且 credentialId 仍被忽略（既有用例保持）；两者都缺时兜底空凭证由 `FeishuClient.start` fail-fast（语义不变）。
- [ ] `Proof` 复核 `resolveCredentials` 全部调用点与 beans.xml 引用链（`nopFeishuCredentials` → 连接器），确认平台标准凭证面可达。

Exit Criteria:

- [ ] 注入的 FeishuCredentials bean 经连接器可达且被使用（测试为证）；options 面字面语义与 credentialId 忽略负向语义不变。
- [ ] beans.xml 注释与 live 注入面一致；无契约矛盾残留。
- [ ] **端到端验证**：从 `start(context)` 入口经 `resolveCredentials` 到 `FeishuClient.start(credentials, handler)` 的完整路径覆盖（注入 bean 路径与 options 路径各一）。
- [ ] **接线验证**：注入字段在 `start` 运行时被 `resolveCredentials` 消费（非仅新增字段）。
- [ ] **无静默跳过**：凭证缺失不静默（维持 `FeishuClient.start` fail-fast 或连接器内显式报错）。
- [ ] owner doc 更新或 `No owner-doc update required`。
- [ ] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过。
- [ ] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。

- [ ] 3 项 in-scope confirmed contract drift / live defect（MFA 零测试 + 跨模块裸字符串契约、getCapabilities 零消费者与过度承诺、Feishu 凭证契约矛盾）全部收口（修复或按裁定登记）
- [ ] 各 Phase Exit Criteria 全数达成（含端到端验证、接线验证、无静默跳过）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs（`ai-dev/design/nop-auth/01-architecture-baseline.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` 等）已同步到 live baseline，或显式写明 `No owner-doc update required`
- [ ] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow 检查）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [ ] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-14-1937-2-p2-gateway-channel-contract-1-3827688d to opencode-pid-25063
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-14-1937-2-p2-gateway-channel-contract-1-3827688d

## Verification

（空，由 BUILD_VERIFY 填写）

## Closure

（空，由 CLOSURE_AUDIT 填写）
