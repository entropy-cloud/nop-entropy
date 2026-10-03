# 2304 WI22 Retry-After 双源解析 + overloaded 长退避 + 正则归类器（refactor roadmap M6，含 ⑤-E-3）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI22（Deliverable: 代码变更 + 测试；Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`；来源: unreal 比对 §9 + `dsh-D4-fault-tolerance.md` ⑥-建议3）
> 划界: 溢出恢复环归 autonomous WI4/WI5——本 plan 不新增 OVERFLOW 分类值，context-length 文案仅归既有 NON_TRANSIENT
> Protected Areas: ErrorClassification（nop-ai-api 公共枚举）additive 值 → plan-first + owner doc + 迁移说明（全部消费者 == 比较无 switch-exhaustive，审计清单见 Goals）
> Adversarial Review: round 1 子代理 agent_94ee164c——NOT APPROVED（2 Blocker：shipped claude.llm.xml 529 拦截未入 scope/归类器输入契约不含 errorType；4 Major：链末落点事实错误/gateway 行为变更未裁定/验证门缺下游模块/QUOTA 误判后果）+4 Minor 全部吸收；round 2 子代理 agent_3a50cfaf——APPROVED（10/10 PASS，含 gateway 必要性实码推演），N1-N4 一行级修正已吸收

## Purpose

Retry-After 仅有响应头/body 字段两源（unreal §9：错误 message 正文 "try again in N s" 第三源缺失）；overloaded/slow_down 无独立长退避曲线（现在落 5xx TRANSIENT 纯 jitter）；provider 非结构化文案无正则归类器（dsh-D4 建议3：归入规范码提高跨 provider 兼容率）。本 plan 补齐三项。

## Current Baseline

（执行时 HEAD `2a8084eee8`，2026-10-03 实测）

- `AbstractLlmDialect.resolveRetryAfterMs:165-189`：三源优先级 retry-after-ms 头 → retry-after 头（秒/HTTP-date）→ body 字段（matched.retryAfterPath > errorResponse.retryAfterPath）；全缺返回 null。**无 message 正文解析**。
- `StandardRetryPolicy.shouldRetry:112-154`：RATE_LIMITED 且 retryAfterMs 非空 → floor+jitter（`computeRateLimitedDelay`）；**TRANSIENT 的 retryAfterMs 被忽略**（纯 full-jitter）；QUOTA/AUTH → FALLBACK；其余 STOP。retryable 集合 = {TRANSIENT, RATE_LIMITED}。
- `ErrorClassification`（nop-ai-api :33-38）6 值：TRANSIENT/NON_TRANSIENT/RATE_LIMITED/QUOTA_EXCEEDED/AUTH_INVALID/CACHE_STATE_LOST。**消费者清单（== 比较为主，无 switch-exhaustive）**：StandardRetryPolicy、LlmErrorClassifier、AbstractLlmDialect.defaultHeuristic、EmbeddingServiceImpl、LlmCallCoordinator（TRANSIENT/NON_TRANSIENT 构造 + isSwitchable 集 QUOTA/AUTH）、gateway failover 三类（CACHE_STATE_LOST 特判 + isSwitchable）。
- `AbstractLlmDialect.defaultHeuristic:113-124`：429→RATE_LIMITED、5xx→TRANSIENT、其余→NON_TRANSIENT。**529（Anthropic overloaded）现归 TRANSIENT 纯 jitter**；无 message 正则归类。
- `LlmErrorClassifier.classify:73-`：cause 链解包（timeout/status）——**message 文本归类缺失**（dsh-D4 建议3 靶点）；**链走完落 TRANSIENT**（:114-115 "Unknown failure on a network call → assume transient"；仅 error==null 时 NON_TRANSIENT :74-76）——接线② fail-open 的未知文案维持该落点。
- `matchesMapping:130-154`：config errorMappings 的 messagePattern 复刻 dialect.xdef 规则（下划线替空白、DOTALL、CASE_INSENSITIVE、**matches() 全匹配**）。
- **shipped config 已有 529 拦截（round 1 B1）**：`claude.llm.xml:58-59` `anthropic-transient` mapping 含 `httpStatus="500,504,529"` + `errorTypes="api_error,timeout_error,overloaded_error"` → 529 现经 config 层即判 TRANSIENT（config first-match-wins 先于启发式与 message 归类）；`default.llm.xml` 有 5 条 errorMappings（quota/rate-limited/auth/non-transient/server-error）但 **server-error 仅 500,502,503,504×errorTypes=server_error——无 529 特配**（round 2 N1 修正：default 确有 errorMappings 段）；azure/gemini/ollama/bailian/lm-studio/volcengine/free/gw 测试配置亦无 529 特配。**Anthropic 端到端 OVERLOADED 必须拆分该 mapping**。
- **Anthropic 529 payload 形态（round 1 B2）**：`{"error":{"type":"overloaded_error","message":"Overloaded"}}`——`overloaded_error` 是 errorType 词元而非 message 文案；归类器输入须含 errorType/errorCode。
- **errorMessage 前置条件（round 1 m8）**：errorMessage 非空依赖 `errorResponse.errorMessagePath` 配置（shipped claude/default/deepseek 已配）；未配置 provider 的第四源与接线①空转——登记 Known Residual。
- 契约：错误规范化数据驱动（`<errorMappings>`）+ 默认启发式零回归红线（TestLlmDialectErrorResponse 固化）；`StandardRetryPolicy` retryAfterMs 可超 maxDelayMs（服务器要求优先）。

## Goals

- **Retry-After message 正文第四源**：`resolveRetryAfterMs` 优先级追加第 4 位（三源全缺后）——errorMessage 正则解析 "try again in N s/ms/minutes"（unreal retry.go 形态：`(\\d+(?:\\.\\d+)?)\\s*(ms|seconds?|secs?|s|minutes?|mins?|m)`；单位缺省=秒）。从 `parseErrorResponse` 传入 errorMessage；解析失败容错返回 null（不抛）。
- **ErrorClassification.OVERLOADED（additive 枚举值）**：Anthropic `529 overloaded_error` / openrouter `slow_down` 语义。消费者迁移审计（== 比较安全，逐一登记）：
  - StandardRetryPolicy：retryable 集 + 独立长退避曲线 `min(10s × 2^attempt, 60s)` ±20% 抖动（unreal 形态：unattended 场景长退避）；server retryAfterMs 仍为 floor（`max(curve, retryAfterMs)`）。**曲线不受 maxDelayMs（默认 30s cap）约束——对既有延迟包络 Javadoc 契约的显式例外**（round 1 m9），常量硬编码不可配（owner doc 登记契约）。
  - TRANSIENT floor 补强：TRANSIENT 且 retryAfterMs 非空 → `max(jitterBackoff, retryAfterMs)`（服务器告知等待即遵守——与 RATE_LIMITED floor 同一理由；行为改进，owner doc 登记）。
  - LlmCallCoordinator.isSwitchable 集**不含** OVERLOADED（账号链是同 provider 换 key——provider 过载下无意义；round 1 审计确认 OVERLOADED 经 policy RETRY 路径不入 FALLBACK 分支）——显式裁定登记。
  - **gateway failover isSwitchable 集**含**入** OVERLOADED（round 1 M4 裁定：gateway 候选是跨 provider/跨 model-class——provider 过载恰是跨 provider 切换的标准场景，与 coordinator 裁定相反的理由各自成立；否则 529 的 failover 能力被静默移除）。nop-ai-gateway 行为随归类变化：需 gateway 测试项。
  - 消费者完整清单（round 1 m10 + round 2 N4 补齐——AttemptContext/RetryContext 均纯字段持有无分支，安全）：StandardRetryPolicy、LlmErrorClassifier、AbstractLlmDialect、EmbeddingServiceImpl、LlmCallCoordinator、AttemptContext、RetryContext、gateway failover 三类。
  - **既有测试改写点（round 1 m10）**：`TestStandardRetryPolicy:259-263` 断言 "TRANSIENT 仍用纯 full-jitter，retryAfterMs 不影响它"——TRANSIENT floor 补强落地后该断言必须改写为新契约（本 plan 登记的行为改进，非回归）。
- **正则归类器（dsh-D4 建议3 全量）**：新增 `ErrorMessageClassifier`（nop-ai-core reliability 包，fail-open 黑名单式——只强匹配已知词元，未知返回 null 不归类；**find/substring 语义而非 matches() 全匹配**（round 2 N3：裸词元 `overloaded`/`try again` 必须命中 "Rate limit exceeded: try again later" 类真实文案——与 config messagePattern 的 matches() 惯用法刻意不同）：
  - **输入契约（round 1 B2）**：接线① 输入 = errorType + errorCode + errorMessage 拼合文本（errorType 词元如 `overloaded_error` 才可匹配）；接线② 输入 = 异常 `getMessage()`。
  - **规范表（→ 既有 6 值 + OVERLOADED，不新增 OVERFLOW——划界 autonomous WI4/WI5）**：`server_is_overloaded|overloaded_error|slow_down|overloaded`→OVERLOADED；`try again|rate limit|too many requests`→RATE_LIMITED；`quota|billing|insufficient`→QUOTA_EXCEEDED（**仅 httpStatus<500 生效**——round 1 M6：5xx 文案含 "insufficient capacity" 误判 QUOTA 会触发账号链漂移，5xx 时 QUOTA/AUTH 行不参与）；`invalid api key|unauthorized|authentication`→AUTH_INVALID（同上仅 4xx）；`context length|maximum context|too many tokens|prompt is too long`→NON_TRANSIENT（溢出语义但归 NON_TRANSIENT——划界裁定，owner doc 登记）。接线① 无状态码门（parseErrorResponse 本就以 httpStatus 入参——按其值执行 4xx/5xx 门槛）；接线② 无 status 信号，**视为满足 <500 门**（round 2 N2 裁定：异常通道的 QUOTA/AUTH 词元生效——传输异常文案 "quota exceeded" 归 QUOTA 走账号链是合理语义）。
  - 接线点①：`parseErrorResponse` config mapping 未命中后、defaultHeuristic 前——文案归类优先于纯状态码（非结构化 provider 的唯一信号是文案；4xx/5xx 门槛见规范表）。
  - 接线点②：`LlmErrorClassifier.classify` cause 链解包末尾（无 timeout/status 信号时，用原始 error 的 message）正则归类——**补强而非替代** cause 链（dsh 建议3 原义）；fail-open 落点维持既有 TRANSIENT。
- **测试**：message 退避解析（秒/ms/minutes/复数/容错）、OVERLOADED 分类（529/文案/config mapping 可达）、长退避曲线边界（10s 起/60s 封顶/±20% 抖动范围/retryAfter floor）、TRANSIENT floor 补强、正则归类器全表 + fail-open、零回归（既有 429/5xx 启发式与 retryAfter floor 行为不变）。

## Non-Goals

- 溢出恢复环（OVERFLOW 分类值/压缩重算）——autonomous WI4/WI5（context-length 文案归 NON_TRANSIENT 是划界内的诚实映射）。
- 错误分类的配置化正则表（llm.xdef `<errorMappings>` 已支持 messagePattern——静态规范表仅服务无配置 provider 的兜底）。
- 空响应（EMPTY_RESPONSE）分类——dsh 可重试集含它，nop 现无该形态登记，watch。

## Scope

### In Scope

- nop-ai-api：ErrorClassification.OVERLOADED additive 枚举值。
- nop-ai-core：AbstractLlmDialect（message 第四源 + 归类器接线①）、ErrorMessageClassifier（新）、StandardRetryPolicy（OVERLOADED 曲线 + TRANSIENT floor）、LlmErrorClassifier（接线②）、测试。
- **`claude.llm.xml`（round 1 B1）**：拆分 `anthropic-transient`——移除 529 + `overloaded_error`，新增独立 `anthropic-overloaded` mapping（classification=OVERLOADED，httpStatus=529 或 errorTypes=overloaded_error）；并审计其余 shipped 配置（default/azure/gemini/ollama/bailian/lm-studio/volcengine/free + 6 个 gw 测试配置）无同类 529 拦截（round 1+2 审计：仅 claude 有）
- nop-ai-gateway：OVERLOADED 入 isSwitchable 集（round 1 M4）+ 测试。
- owner doc（reliability）+ roadmap 锚点 + log。

### Out Of Scope

- nop-ai-agent 行为变更（消费者审计确认 == 比较天然兼容 + OVERLOADED 不入 coordinator isSwitchable 裁定，无需改动）。

## Execution Plan

### Phase 1 - 双源解析 + OVERLOADED + 正则归类器

Status: completed
Targets: `AbstractLlmDialect.java`、`ErrorClassification.java`、`ErrorMessageClassifier.java`（新）、`StandardRetryPolicy.java`、`LlmErrorClassifier.java`、测试

- Item Types: `Feature`

- [x] resolveRetryAfterMs 追加 message 正文第四源（parseRetryAfterText——"try again in N s/ms/minutes"，容错）
- [x] ErrorClassification.OVERLOADED additive + 消费者审计登记（完整清单见 Goals——含 round 2 N4 补的 RetryContext）
- [x] ErrorMessageClassifier（fail-open find 语义规范表——round 2 N3；QUOTA/AUTH <500 门）+ 接线①（parseErrorResponse config mapping miss 后）+ 接线②（LlmErrorClassifier cause 链末尾，firstMessage 捕获——实现修正：循环变量退出后为 null）
- [x] StandardRetryPolicy：OVERLOADED 长退避曲线（10s→60s ±20%、retryAfter floor）+ TRANSIENT floor 补强
- [x] 单测：TestRetryAfterMessageSource 7 + TestErrorMessageClassifier 6 + TestStandardRetryPolicy 新增 3 + TRANSIENT 断言改写（round 1 m10，plan 登记的行为改进）+ TestLlmErrorClassifier 安全网测试精化（rate-limited 文案→RATE_LIMITED，意图"可重试不 fail-fast"保留）+ claude 拆分 mapping 测试（TestLlmDialectErrorResponse）+ gateway TestOverloadedSwitchable 2

Exit Criteria:

- [x] 单测全绿；`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api,nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway -am` 通过（agent 3535/gateway 45/core 470+ 全绿，2026-10-03）
- [x] **接线验证**：529 → OVERLOADED（claude mapping + 无配置 message 分类双路径，TestLlmDialectErrorResponse + TestRetryAfterMessageSource）→ computeOverloadedDelay 策略级断言；"try again in 30s" → retryAfterMs=30000
- [x] **无静默跳过**：正则归类器 fail-open 显式（未知文案返回 null 走原启发式，不静默改判——TestErrorMessageClassifier.unknownTextIsFailOpen）
- [x] `ai-dev/logs/` 条目更新（owner doc 归 Phase 2）

### Phase 2 - owner doc 与收口

Status: completed
Targets: `nop-ai-agent-reliability.md`、roadmap、log

- Item Types: `Proof`

- [x] reliability owner doc：新增"缓存与过载的重试细节"节（四源优先级表、OVERLOADED 分类与长退避曲线契约含 maxDelayMs 例外、TRANSIENT floor 行为改进登记、正则归类器规范表与 fail-open 语义、gateway 含入/coordinator 不含的双切换裁定）
- [x] refactor roadmap WI22 勾选 + Current Baseline 锚点（closure audit agent_4d231762 APPROVED 后落位）

Exit Criteria:

- [x] owner doc 在位且与 live 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2026-10-03 实测）

## Closure Gates

- [x] 三项增量端到端连通（文案→retryAfterMs；529/文案→OVERLOADED→长曲线；无结构信号文案→规范分类——audit Anti-Hollow 链逐段验证）
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_4d231762，APPROVED：0 Blocker / 0 Major / 4 Minor 非阻塞）
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-api,nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway -am` 通过（audit 实测 exit 0）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2304-wi22-retry-after-classifier.md --strict` 退出码 0
- [x] checkstyle：commit 钩子承担（No separate checkstyle gate required）
- [x] 受影响 owner docs 已同步
- [x] git commit 完成

## Known Residuals

### errorMessage 未配置 provider 的第四源/归类①空转
- Classification: watch-only residual
- Why Not Blocking Closure: errorMessage 依赖 errorResponse.errorMessagePath 配置（shipped claude/default/deepseek 已配）；未配置 provider 属自建接入面，配置一行即激活
- Successor Required: no

### EMPTY_RESPONSE 分类缺失（dsh 可重试集含它）
- Classification: watch-only residual
- Why Not Blocking Closure: nop 现无该形态登记（parse 层空响应已按错误处理）；需求出现时入正则规范表
- Successor Required: no

### 正则规范表为静态硬编码（非配置面）
- Classification: watch-only residual
- Why Not Blocking Closure: provider 无结构文案的兜底归类（fail-open）；provider 特定精度由既有 `<errorMappings>` 配置面承载（可覆盖）
- Successor Required: no

## Closure

Status Note: 三项增量全落地——Retry-After 第四源（message 正文，最低优先级容错解析）、OVERLOADED 分类（claude mapping 拆分 + 文案归类双路径；独立 10s→60s±20% 长曲线不受 maxDelayMs 约束、retryAfterMs floor；gateway 含入/coordinator 不含的双切换裁定）、ErrorMessageClassifier（dsh-D4 建议3 全量：fail-open find 语义 + QUOTA/AUTH <500 状态门 + 双接线）；TRANSIENT floor 补强（两处 legacy 测试按 plan 登记的行为改进改写）。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理 agent_4d231762-5ef1-47b0-b688-1dbc774fdc48（fresh session）
- Evidence:
  - A-I 逐条 PASS（live 锚点：ErrorClassification:42；parseRetryAfterText :221-258 + 第四源 :203-210 + 接线① :100-110；ErrorMessageClassifier find 语义 :74/:80 + 规范表 :43-55；StandardRetryPolicy 曲线 :182-198 不引用 maxDelayMs + TRANSIENT floor :162-164；LlmErrorClassifier 接线② :126 + firstMessage :88-92；claude.llm.xml :58-63；gateway :452-463）
  - Anti-Hollow：ChatServiceImpl → parseErrorResponse → 分类+第四源 → ChatResponse.error → coordinator/policy（OVERLOADED 长曲线、TRANSIENT floor）+ gateway 双通道切换——全链连通，无 stub
  - 实测：四模块全量 exit 0；doc-links 0；hollow 0；checklist 0
  - Minor 4 条（非阻塞）：javadoc 示例漂移（已顺手修正）、plan 规范表未回改 rate[- ]limit 精化（log+owner doc 已登记）、一测试名与路径略不符（注释已澄清）、双空行
  - 两处 legacy 测试改写均为 plan 预登记行为改进（owner doc + 测试固化），非降级

Follow-up:

- 见 Known Residuals
