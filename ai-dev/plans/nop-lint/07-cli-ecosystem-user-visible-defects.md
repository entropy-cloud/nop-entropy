# 07 CLI/生态用户可见缺陷修复（cache 双读/Mojo 开关与乱码/checkFile cap/扩展名归一化）

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings C3/C4/C5/C6/C7 + CLI null 消息）
> Related: ai-dev/plans/nop-lint/2026-09-25-0030-1-ci-cache-tuning.md（--cache 交付）、2026-09-24-1500-1-maven-plugin.md、2026-09-24-1530-1-graphql.md、2026-09-24-2330-1-editor-lsp.md
> Review: R1 对抗审查（2026-09-25）：baseline 引用核对全对、现有测试无破坏；2 Major（Phase 1 测试缝可行性、checkFile 第三处扩展名缺陷）+ 9 Minor 已全部修订进本文本，进入执行。

## Purpose

修复审计确认的 6 个用户可感知缺陷：`--cache` 双读与哈希错位、CheckMojo 基线开关静默丢弃、CheckMojo 日志桥非 ASCII 乱码、`Lint__checkFile` 大小上限不设防、大写扩展名解析失败（LSP/MatchCommand/checkFile 三处同族）、CLI 顶层错误输出 `error: null`。全部是既有契约的正确实现，不引入新功能面。

> 对抗审查记录（2026-09-25，R1）：2 Major（Phase 1 测试缝可行性、checkFile 第三处扩展名缺陷点）修订后执行；Minor 9 的编号映射已在分析报告工作区版本对齐。

## Current Baseline

- 全部 6 项缺陷已在 2026-09-25 工作区逐条读码确认（分析报告引用 文件:行号），6 模块 1020 测试全绿。
- `--cache` 主循环（cli/CheckRunner.java:185-208）：cache miss 路径 read#1（:187）→ `lintFile` 内 read#2（:323）→ `cache.put(path, bytes(read#1), diagnostics(来自 read#2))`（:196）。`lintWithTrace` 已接收 `byte[] source`，`lintFile` 为 private、调用点 2 处（:194 cache 路径、:201 非 cache 路径）。测试缝现状：`RuleResultCache` 为 final + 私有构造（仅经 `load` 创建）、`CheckRunner.run` 内部自建 cache 无注入点——Phase 1 的同源性验证需按计划明示的 package-private 测试缝进行。
- CheckMojo（nop-lint-maven-plugin）：buildArgs 组装 `if (baselineApply && baselineFile != null)`（:240-251），开关缺 file 时静默丢弃；`bridgeToLog` 字节桥 `LogOutputStream.write(int b)` 逐字节 `(char) b`（:304-344）；`ConsoleReporter.render(CheckOutcome, Writer)` 重载已存在（ConsoleReporter.java:79-89；注意 PrintStream 面 :49-66 的内联匿名 Writer 与 cli/NopLintCli.java:168-185 `writerOf` 逐行等价，ConsoleReporter 构造器对 PrintStream 参数有 requireNonNull——仅用 Writer 重载时仍需传非 null PrintStream 如 nullOutputStream）；`render(outcome, Writer)` 声明 `throws IOException`。
- NopLintBizModel.checkFile（nop-lint-graphql）：`readControlled(path)`（整读）在 :120、`checkSourceCap(source.length())` 在 :121；VFS 分支 `resource.readText(...)`（:209）无预检；磁盘分支走 `toRealPath` + workdir confinement。**第三处扩展名缺陷点（审查 R1 Major 2 确认）**：:125-127 `name.substring(dot + 1)` 未 lowercase 直传 `languageIdForExtension`——`/x/FILE.JAVA` → 表 miss → `rt.select(null,...)` 空规则 → `resolve(null)` 抛"must not be blank"。现行 cap 口径为**字符数**（source.length()），`Files.size`/`IResource.length()` 为**字节数**；`IResource.length()` 对长度未知资源返回 **-1**。
- LSP `resolveLanguage`（lsp/NopLintLanguageServer.java:190-197）与 MatchCommand（cli/MatchCommand.java:75-76）：`name.substring(dot + 1)` 未 lowercase；`TargetScanner.languageIdForExtension` 契约要求已小写（TargetScanner.java:70-74）；`LanguageRegistry.resolve(null)` 抛 "lint language id must not be blank"。失败链实测：`FILE.JAVA` → 表 miss → resolve(null) 抛异常 → didOpen 失败。
- NopLintCli 顶层 catch（cli/NopLintCli.java:143）：`err.println("nop-lint: error: " + e.getMessage())`，message 为 null 时输出 "nop-lint: error: null"（随后有堆栈）；LSP 侧 NopLintLanguageServer.onMessage（:122）既有形态为 `e.getMessage() == null ? e.toString() : ...`——本 plan 对齐为类名（更可诊断，两形态都满足"不输出 null"的 Goal，取类名）。
- 现有测试防线：core `TestNopLintCli*`/`TestCheckRunner*` 家族、maven-plugin `CheckMojoTest`（8 测试，CapturingLog 只做 contains 断言、fixture 全 ASCII——日志桥改 Writer 后现有断言全部成立）、graphql 模块 17 测试（TestNopLintGraphQL 4 + TestNopLintBizModel 13；checkFile cap 消息断言含 `nop.lint.graphql.max-source-size`，新消息保留配置名即不破坏）、LSP 9 测试全用小写扩展名。

## Goals

- `--cache` miss 路径单次读盘，缓存条目的哈希与诊断必然来自同一份字节。
- CheckMojo：基线开关缺 `baselineFile` 时 execute 快速失败（MojoExecutionException 点名缺失参数）；日志输出经 `render(outcome, Writer)` 直达 Maven Log，任何 charset 下非 ASCII 消息不乱码。
- `Lint__checkFile`：磁盘与 VFS 分支都在读取内容前以长度预检拒绝超限文件（预检按**字节**口径、方向只会更严；read 后的字符 cap 检查保留作兜底——`IResource.length()` 返回 -1（长度未知）时落到 read 后检查，不产生 fail-open 洞）；同文件的大写扩展名解析缺陷（审查 R1 Major 2）一并修复。
- LSP/MatchCommand：大写扩展名（`FILE.JAVA`、`Demo.TS`）正确解析语言。
- CLI 顶层错误对无消息异常输出异常类名而非 "null"。

## Non-Goals

- 不改 `--cache` 指纹构成、命中语义、互斥矩阵（item 43 裁定不动）。
- 不改 checkFile 路径三分法、cap 默认值与配置项名（item 38 裁定不动）。
- 不重构 CheckRunner 编排结构 / PrintStreamWriter 去重 / RunSummary 集合策略（plan 14 的范围）。
- 不处理 NodeTscBridge 会话隔离（plan 13）、DefUseChain/TemplateFix（plan 11）。
- 不动 ConsoleReporter 输出字节（golden 约束）。

## Scope

### In Scope

- `nop-lint-core`：cli/CheckRunner（cache 路径单读）、cli/NopLintCli（null 消息）、cli/MatchCommand（扩展名归一化）、lsp/NopLintLanguageServer（扩展名归一化）。
- `nop-lint-maven-plugin`：CheckMojo（基线开关校验 + Writer 直连日志）。
- `nop-lint-graphql`：NopLintBizModel（cap 前移：磁盘 `Files.size` / VFS `resource.length()`）。
- 各缺陷的可证伪回归测试（先红后绿或断言新行为）。

### Out Of Scope

- 规则 YAML、引擎内核、语义分析器、bench 脚本。
- 任何 nop-xlang/nop-core/nop-xdef 平台文件。

## Execution Plan

### Phase 1 - CheckRunner cache 单读贯穿（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/CheckRunner.java`

- Item Types: `Fix`

- [x] cache miss 路径改为单次 `readSource`：read#1 的 bytes 传入 lintFile（`lintWithTrace` 已接收 byte[]，lintFile 增加 bytes 传参形态），`cache.put` 与 lint 消费同一份数组
- [x] 非缓存路径行为不变（lintFile 既有 2 个调用点语义保持；run 的 3 个委托重载 + 2 个委托构造器形态不动）
- [x] **测试缝授权（审查 R1 Major 1）**：允许为验证同源性添加 package-private 缝——实现为 `SourceReader` 函数式接口注入（package-private run 重载，生产路径传 `this::readSource`），lintFile/fixFile 消费注入的快照
- [x] 新增回归测试：cache miss 后 `cache.put` 收到的字节与 lint 使用的字节同源（经上述测试缝断言同一数组引用）；cache 命中重放路径不受影响（现有 cache 测试保持绿）

Exit Criteria:

- [x] CheckRunner 中 cache 路径对同一文件只发生一次 `readSource` 调用（代码可观察：read#1 的 bytes 流入 lint 与 cache.put 两处）；单读成立后同数组引用为结构必然（同一 `bytes` 变量流入两处，closure audit R2 确认语义等价）
- [x] 新增同源性测试落地（`cacheMissReadsEachFileOnceAndReplaysFromTheSameBytes`：计数 reader 证明每 miss 文件恰一次读取 + warm 重放 2 hits）；现有 `TestNopLintCli*`/cache 相关测试全绿
- [x] `No owner-doc update required`（--cache 契约面不变，仅消除错位缺陷；design 11 §4 语义未被修改）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - LSP/MatchCommand 扩展名归一化 + CLI null 消息（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/lsp/NopLintLanguageServer.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/MatchCommand.java`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/NopLintCli.java`

- Item Types: `Fix`

- [x] NopLintLanguageServer.resolveLanguage 与 MatchCommand：扩展名 `toLowerCase(Locale.ROOT)` 后再查表；无扩展名文件的既有失败行为保持（fail-closed，不新增 NO_EXTENSION 查表旁路）
- [x] NopLintCli 顶层 catch：`getMessage() == null` 时输出 `e.getClass().getName()`（LSP onMessage 既有形态是 toString，本处取类名，Goal 均满足）
- [x] 新增测试：LSP didOpen 大写扩展名 URI 成功解析语言（didOpenResolvesUppercaseUriExtensionWhenClientSendsNoLanguageId）+ MatchCommand 对 `SAMPLE.JAVA` 正常 lint（matchResolvesUppercaseFileExtension）；CLI 对无消息异常输出类名（topLevelErrorWithoutMessageNamesTheExceptionClass，经无消息 NPE stub 语言触发）

Exit Criteria:

- [x] `FILE.JAVA` 经 LSP didOpen 或 MatchCommand 可正常 lint（两处焦点测试均落地，失败链不再触发 `resolve(null)`）
- [x] 无扩展名路径的现有测试行为不变；CLI null 消息测试落地
- [x] `No owner-doc update required`（TargetScanner 契约本来如此，属旁路缺陷修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CheckMojo 基线开关校验 + 日志桥乱码修复（Fix）

Status: completed
Targets: `nop-lint/nop-lint-maven-plugin/src/main/java/io/nop/lint/maven/CheckMojo.java`

- Item Types: `Fix`

- [x] baselineApply/baselineCheck/writeBaseline 任一为 true 且 `baselineFile == null` 时抛 `MojoExecutionException`（消息点名缺失的 `noplint.baselineFile`；不受 failOnError 支配——治理配置错误而非诊断结果，execute 早期校验先于任何管线工作）
- [x] 日志输出改走 `ConsoleReporter.render(outcome, Writer)`：字符粒度行缓冲 Writer 直连 `log.info`（与原字节桥的行语义一致：跳过 \r、空行不渲染），删除 `LogOutputStream` 字节桥；machine 格式输出通道不变
- [x] CheckMojoTest 新增：baselineSwitchWithoutFileFailsFastNamingTheParameter（三开关逐一断言参数名 + 无渲染发生）；nonAsciiDiagnosticMessagesReachTheLogIntact（fixture 规则 CJK 消息"禁止 System.exit"逐字断言——字节桥下必红、Writer 桥下绿）

Exit Criteria:

- [x] 开关/file 配对校验测试落地；乱码修复以 CJK 字符串逐字断言证明（CheckMojoTest 10/10）
- [x] 既有 CheckMojoTest 全绿（8 例原有行为不变）
- [x] design 03 §2.1 增注：基线开关与 baselineFile 必须配对 + 日志桥字符粒度 Writer（plan 07 修订段）
- [x] `ai-dev/logs/` 对应日期条目已更新（随 plan 收口统一补记）

### Phase 4 - NopLintBizModel checkFile cap 前移（Fix）

Status: completed
Targets: `nop-lint/nop-lint-graphql/src/main/java/io/nop/lint/graphql/NopLintBizModel.java`

- Item Types: `Fix`

- [x] 磁盘分支：`Files.size(toRealPath 后的真实路径)` 在读取前对照 cap 拒绝（字节口径，方向只会更严）；VFS 分支：`resource.length()` > cap 时预检拒绝，`length()` 返回 -1（长度未知）时落回 read 后的 `checkSourceCap` 兜底（read 后检查在本路径**保留**，不删）；`checkSource`（内存 source 入参）的既有 cap 检查保持
- [x] checkFile 的扩展名解析补 `toLowerCase(Locale.ROOT)`（审查 R1 Major 2 的第三处缺陷点，与 Phase 2 同族修复收口）
- [x] 超限错误消息保留 `nop.lint.graphql.max-source-size` 配置名与路径（现有断言不破坏），可注明字节口径
- [x] 新增测试：磁盘超限文件读取前拒绝；VFS 分支超限拒绝（**测试缝授权**：允许使用平台配置覆盖 API 设小 cap、允许测试用 VFS 写入或现有资源构造超限场景）；`FILE.JAVA` 形态路径经 checkFile 正常 lint
- [x] design 03 §2.3 增注写明：预检为字节口径、方向更严；长度未知资源保留 read 后字符 cap 兜底；扩展名大小写归一化

Exit Criteria:

- [x] 超限拒绝发生在读取内容之前（磁盘/VFS 分支代码路径可观察：size/length 检查先于 readText/readControlled；length()==-1 时保留 read 后兜底）
- [x] graphql 模块 20 测试全绿（TestNopLintBizModel 13→16 含 3 新增[VFS 超限测试为 audit R2 补齐]；TestNopLintGraphQL 4）
- [x] design 03 §2.3 增注完成
- [x] `ai-dev/logs/` 对应日期条目已更新（随 plan 收口统一补记）

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 6 个 in-scope 缺陷全部以可证伪测试证明修复（C3/C4/C5/C6[磁盘+VFS 两分支]/C7 三处/null 消息——audit R1 核对 5.5/6，R2 补 VFS 超限测试后收口）
- [x] 无 in-scope live defect 被降级到 deferred/follow-up（audit R1 确认）
- [x] golden 输出字节不变（audit R1：ConsoleReporter.java 零字节改动，git diff 为空；CheckMojo 仅换消费通道）
- [x] 受影响 owner docs（design 03 §2.1/§2.3）已同步；其余明确 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据（R1 REJECTED→R2 缺口修复→本 Closure 段证据）
- [x] Anti-Hollow Check：四条调用链经审计独立追踪连通（execute→requirePairedBaselineFile→CheckRunner.run；runChecked→MatchCommand/CheckRunner；didOpen→resolveLanguage；checkFile→readControlled→checkPreReadCap→lint）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-maven-plugin,nop-lint/nop-lint-graphql -am` 全绿（audit R1 独立重跑 exit 0；R2 后 790+1=791 测试）
- [x] `./mvnw compile -pl nop-lint/nop-lint-core,nop-lint/nop-lint-maven-plugin,nop-lint/nop-lint-graphql` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出 0（completed 态下复核）

## Deferred But Adjudicated

（无——本 plan 所有项均为 in-scope Fix，不设 deferred。）

## Non-Blocking Follow-ups

- PrintStreamWriter 提取与 CheckRunner 编排拆分（plan 14 收纳，属可读性而非缺陷）。

## Closure

Status Note: 6 项用户可见缺陷（cache 双读错位、Mojo 基线开关静默丢弃、Mojo 日志桥乱码、checkFile cap 不设防、大写扩展名三处、CLI null 消息）全部修复并有可证伪测试背书；golden 输出零改动。closure audit R1 REJECTED（VFS 超限测试缺失 + 勾选不齐），R2 补齐 vfsBranchOversizedResourceIsRejectedBeforeRead 后全部 Closure Gates PASS。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_723dcbb3（fresh session，未参与实现）
- Evidence:
  - Phase 1：CheckRunner.java:212 单次 sourceReader.read → :221 lintFile → :223 cache.put 同一数组；SourceReader package-private（public 面未放宽、RuleResultCache 未动）；测试 cacheMissReadsEachFileOnceAndReplaysFromTheSameBytes 断言读取计数 + warm 2 hits。PASS
  - Phase 2：NopLintLanguageServer.java:196 / MatchCommand.java:76 / NopLintBizModel.java:127-128 三处 toLowerCase；NopLintCli.java:143-144 类名替换；测试 ×3 全绿。PASS
  - Phase 3：requirePairedBaselineFile（CheckMojo.java:311-318）先于 resolveTargets 且在 try 外（结构性不受 failOnError 支配）；CJK 逐字断言经字节桥必红机制成立；CheckMojoTest 10/0/0。PASS
  - Phase 4：磁盘 :228 Files.size 先于 :229 readString；VFS :215-217 length() 预检先于 :218 readText；-1 落回 :122 checkSourceCap 兜底；R2 补 vfsBranchOversizedResourceIsRejectedBeforeRead（小 cap 10B + 真实 VFS 资源）。PASS
  - Closure Gates：audit R1 独立重跑三模块测试 exit 0（当时 790）；anti-hollow 四链追踪；doc-links 0 errors；hollow 扫描 exit 0；golden git diff 取证。R2 复核 VFS 测试落地后 graphql 模块 20 测试（TestNopLintBizModel 16 + TestNopLintGraphQL 4）全绿。
  - `node ai-dev/tools/check-plan-checklist.mjs 07-cli-ecosystem-user-visible-defects.md --strict` completed 态退出 0
  - Deferred 项分类检查：Deferred 区为空；follow-up 仅可读性项（归 plan 14），无 in-scope defect 降级
- Audit Session: agent_723dcbb3-b490-480a-a55d-e248e945b2ca

Follow-up:

- PrintStreamWriter 提取与 CheckRunner 深度拆分归 plan 14（可读性，非缺陷）
- C4 failOnError 独立性的 failOnError=false 对照测试（audit R1 cosmetic 项，结构性证明已存在，测试补入 plan 14 一并处理）
