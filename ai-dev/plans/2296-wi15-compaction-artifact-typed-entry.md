# 2296 WI15 压缩产物条目化（refactor roadmap M4）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI15（Deliverable: 裁定记录 + 代码变更 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI15 本 roadmap 吸收；与 autonomous WI16/WI20 不同层）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`
> Cross-Cutting: 涉及 `nop-ai-api` 公共 API（`ChatMessage` 子类型注册）——plan-first + owner doc + 迁移说明（本 plan 即 plan-first 载体）

## Purpose

压缩产物（summary + 计量元数据 + 快照引用）目前以**无类型形态**散落：summary 是带 `SUMMARY_MARKER` 魔法串的伪 `ChatUserMessage`，计量元数据只在 COMPACTION checkpoint 的自由文本与瞬态事件 payload 里。本项把压缩产物以**类型化条目**落 session 历史，并与既有 COMPACTION checkpoint / snapshot archive 裁定职责边界（无双真相源）。同时内嵌对 pi-D9 建议2 的吸收裁定：不做通用"条目判别联合"改造，只落压缩这一个高价值条目类型（见 Ruling）。

## Ruling — 职责边界裁定（Deliverable ①）

**结论：新增 `ChatCompactionMessage` 类型化历史条目，作为"压缩发生过 + 效果计量 + 快照指针"的 in-history 唯一权威记录；checkpoint/archive/event 三既有面职责不变，互不重复。**

四方职责边界（避免双真相源）：

| 面 | 职责 | 保持/变更 |
|---|---|---|
| 类型化历史条目（新增） | session 历史内压缩事实的权威记录：summary 全文 + tokensBefore/After + originalSize/compactedSize + shadowedTokenCount + snapshotId + 层名 + 时间 | 新增；替换 `ChatUserMessage(SUMMARY_MARKER...)` 伪消息 |
| COMPACTION checkpoint（reliability） | 恢复/审计面的压缩水位记录（短计量串），随 checkpoint journal | 保持不变——不承载 summary 全文（今日亦不承载），无双真相源 |
| snapshot archive（§8.3 Decision B） | 压缩**前原文**的会话内存档（snapshotId 寻址） | 保持不变——条目持有 snapshotId 指针，原文只存 archive 一处 |
| COMPACTION 事件（WI9 payload） | 瞬态订阅者通知 | 保持不变——历史事实以条目为准，事件只是通知 |

**pi-D9 建议2 吸收裁定**：建议原义是"Jackson role 多态标签升级为显式条目判别联合（对位 pi SessionEntry），使 compaction/标签等元数据条目化"。本项**部分吸收**：只落 compaction 一个条目类型（当前唯一有真实消费者缺口的——`findPreviousSummary` 靠魔法串探测、计量元数据无处可查）；不做通用条目联合改造（ChatMessage 既有 6 子类型 + 全 dialect 消费面稳定，通用改造收益不成立且波及 nop-ai-api 全消费方）。"标签等元数据条目化"无当前需求，登记为不吸收。

**形态裁定：`ChatCompactionMessage extends ChatUserMessage`**（而非独立 ChatMessage 平级子类型）——压缩 summary 的本质是"模型应看到的上下文内容"：作为 user 子类，全部既有消费方（dialect 请求构建、`instanceof ChatUserMessage` 分支、`findPreviousSummary` 探测）零改动兼容，wire 格式不变（dialect 只读 content，扩展字段不外发）；`getRole()` 覆写为 `"compaction"` 使类型判别、ORM role=30 语义（owner doc §17.3 既有映射）与日志可读。拒绝独立平级子类型：所有 dialect 需为未知 role 增加转换分支，波及面大且无语义收益。

## Current Baseline

（执行时 HEAD `511b609cf2`，2026-10-03）

- summary 落史形态：`Layer3FullSummaryStrategy.summarize`（nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/Layer3FullSummaryStrategy.java，summarize 内 `new ChatUserMessage(SUMMARY_MARKER + "\n" + summaryContent)`，约 :189；`findPreviousSummary` 约 :212-220，`instanceof ChatUserMessage` + content 前缀探测；行号随编辑漂移，以方法名+语义锚定）
- 计量元数据现状：`AgentCompactionCoordinator.performCompaction`（engine/AgentCompactionCoordinator.java performCompaction，约 :73-219）——tokensBefore/After、originalSize/compactedSize、shadowedTokenCount、snapshotId 只进 checkpoint compactSummary 自由文本（约 :154-176）与 COMPACTION 事件 payload（约 :185-191）；session 历史内无处可查。
- snapshot archive：`snapshotArchive.put(ctx.getMessages())`（约 :94-96）返回 snapshotId（InSessionCompactionSnapshotArchive，会话内存档）；条目化后指针落史。
- `ChatMessage` 多态：`@JsonTypeInfo(property="type")` + `@JsonSubTypes` 6 子类型（nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatMessage.java:25-33）；`getType()` 恒定输出（:54-56）；`ChatUserMessage` 无参构造（约 :45）+ `@DataBean`（:19-28）——子类化形态可行。
- WI14 增量写路径已落地（plan 2295）：新消息类型经 `JsonTool.stringify` 多态序列化自动兼容 FileBacked/DB 两 store，无需 store 改动。
- ORM 层 `nop_ai_session_message` role=30 compaction 映射（owner doc §17.3）是 Gateway/ORM 面的另一条消费链，不在本项范围（store 层 ChatMessage 面优先；ORM writer 若后续接入按 role 30 落库与条目 role 名一致）。
- `MicroCompressionCompactor`/`Layer2TurnPruningStrategy`/`ReferenceCompactionStrategy` 均不产 summary 消息（copy/裁剪/替换语义），summary 条目仅 Layer3 产——但生产装配中 Layer3 必经 `PipelineCompactor`（IContextCompactor），其 compact 在"single final construction point"（PipelineCompactor.java:121-129）用 current 列表**重新构造** CompactionResult、内层字段不透传——producedEntry 必须在此第三点透传，否则生产路径富化永不触发（round 2 Blocker）。
- `new CompactionResult` 全仓 40 处（main 18 + test 22）；适配机制 = 新增最深参构造器 + 既有构造器链式委托 producedEntry=null（类内既有委托先例），仅 Layer3（六参构造处，约 :208）与 PipelineCompactor（:127）两处调用点变更，其余 38 处零改动自然 null。

## Goals

- **`nop-ai-api`**：新增 `ChatCompactionMessage extends ChatUserMessage`（`@JsonSubTypes` 注册名 `"compaction"`；字段 tokensBefore/tokensAfter/originalSize/compactedSize/shadowedTokenCount/snapshotId 均 Long 型可空、compactionLayer 为 String（strategy NAME，如 "layer3-full-summary"）、compactedAt 为 Long epoch ms；**覆写 `copy()`** 返回完整副本——不覆写则任何 copy 路径静默降级为伪 user 消息）+ `@JsonSubTypes` 注册 + Javadoc 迁移说明（additive，新读旧零破坏；**旧构建读新条目 InvalidTypeIdException——回滚需接受 load 失败或清理 session**）。
- **`nop-ai-agent`**：`Layer3FullSummaryStrategy` 产 `ChatCompactionMessage`（SUMMARY_MARKER 前缀保留；tokensBefore/After 与 CompactionResult 同源同值；**originalSize/compactedSize 取 summarize 真实的 `messages.size()`/`result.size()`**）并在 `CompactionResult` 新增 `producedEntry` 字段（最深构造器携带，既有构造器委托置 null）；`PipelineCompactor` 末次构造透传 **最后一个生效层的 producedEntry**（循环内记录，current 列表保条目引用、身份语义成立）；`AgentCompactionCoordinator` 富化 `snapshotId` + `shadowedTokenCount` **只对 `result.getProducedEntry()` 身份引用**（杜绝扫描式富化错挂历史旧条目；富化发生在 coordinator 末尾 re-sync save 之前的同 save 周期，WI14 判据不触发）。
- **兼容**：`findPreviousSummary` 对新旧两种形态都识别（新条目 IS-A ChatUserMessage 且仍带 marker 前缀）；旧 session 的 marker user 消息照常工作（迁移说明：无需迁移，读侧双形态兼容）。
- 测试：条目 round-trip（FileBackedSessionStore 持久化 + 多态还原）；Layer3 产出类型与字段；coordinator 富化；findPreviousSummary 双形态；既有 compaction/store 测试零回归。

## Non-Goals

- 通用"条目判别联合"改造（pi-D9 建议2 的完整原义）——裁定不吸收（见 Ruling）。
- checkpoint journal / snapshot archive / COMPACTION 事件任何改动。
- ORM 层 session-message writer 的 role 30 接入（另一消费链，范围外）。
- dsh/pi 比对报告其他建议。

## Scope

### In Scope

- `nop-ai-api`：ChatCompactionMessage 新类 + ChatMessage @JsonSubTypes 注册。
- `nop-ai-agent`：Layer3FullSummaryStrategy 产出替换；AgentCompactionCoordinator 富化。
- 测试：新增条目 round-trip 与产出/富化断言；owner doc + log。

### Out Of Scope

- 其他 ChatMessage 消费方（dialects/gateway）改动；checkpoint/archive/事件面。

## Execution Plan

### Phase 1 - 类型化条目 + 产出/富化接线

Status: completed
Targets: `nop-ai/nop-ai-api/.../messages/ChatCompactionMessage.java`（新增）、`ChatMessage.java`（注册）、`nop-ai/nop-ai-agent/.../compact/Layer3FullSummaryStrategy.java`、`engine/AgentCompactionCoordinator.java`

- Item Types: `Feature`

- [x] `ChatCompactionMessage extends ChatUserMessage`（DataBean、无参构造、getter/setter、`copy()` 覆写、`getRole()="compaction"`、Javadoc 含迁移说明与回滚限制）；`ChatMessage` `@JsonSubTypes` 注册 `"compaction"`
- [x] `CompactionResult` 增 `producedEntry` 字段：新增最深参构造器携带之，既有构造器链式委托置 null（仅 Layer3 与 PipelineCompactor 两处调用点传值，其余 38 处零改动）
- [x] `Layer3FullSummaryStrategy`：summary 消息改产 `ChatCompactionMessage`（SUMMARY_MARKER 前缀保留；tokensBefore/After/真实 sizes/compactionLayer=NAME/compactedAt），构造 CompactionResult 时传 producedEntry
- [x] `PipelineCompactor`：末次构造透传最后一个生效层的 producedEntry（round 2 Blocker 修复——生产装配 Layer3 必经此层，不透传则富化永不触发）
- [x] `AgentCompactionCoordinator`：compaction 生效分支对 `result.getProducedEntry()`（非 null 时）富化 snapshotId + shadowedTokenCount——**身份引用富化，绝不扫描历史**（round 1 Blocker 修复；未见效分支消息未替换、无本轮条目，不富化）
- [x] 测试：条目经 FileBackedSessionStore round-trip（类型与全字段还原，含 `copy()` 副本断言）；Layer3 产出断言（mock chatService；producedEntry 引用 == 列表中的条目）；**coordinator 富化端到端断言必须经 PipelineCompactor+Layer3 真实装配**（禁止用假 IContextCompactor 直塞 producedEntry 的测试形态——round 2 指出的 hollow 风险）；**历史旧条目不被改写**（预置已富化条目跑 Micro 生效轮验证 snapshotId 不变）；`findPreviousSummary` 双形态（新条目 + legacy marker user 消息）；既有 compaction 相关测试零回归（语义漂移登记：LlmCompletionJudge/Layer3 prompt 中条目标签 [user]→[compaction]，非断言面）

Exit Criteria:

- [x] 上述测试全绿；`./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（覆盖 nop-ai-api 重编译，TestCompactionTypedEntry 6/6）
- [x] **接线验证**：coordinator 富化真实运行时走到（端到端断言经 PipelineCompactor+Layer3 真实装配）（富化字段断言即证明），条目经增量写路径持久化并还原（round-trip 断言即证明）
- [x] **无静默跳过**：coordinator 富化分支对 producedEntry==null 显式跳过（文档化——Layer2/Micro/Reference 路径无 summary 条目）；无空方法体；copy() 遵循先例语义
- [x] 本 Phase 改变 live baseline：owner doc 更新归 Phase 2（显式归属声明）；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`、log、roadmap

- Item Types: `Proof`

- [x] owner doc 新增"压缩产物条目化"节（四方职责边界表、形态裁定、pi-D9 建议2 部分吸收裁定、兼容语义）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] refactor roadmap WI15 checkbox 勾选 + Current Baseline 锚点
- [x] 独立 closure audit + 工具门 + git commit

Exit Criteria:

- [x] owner doc 在位且与 live 一致；全部验证门通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI15 checkbox → git commit → 本 plan 标 completed。

- [x] 压缩产物类型化条目端到端连通（Layer3 产出→coordinator 富化→store 持久化→round-trip 还原→findPreviousSummary 消费）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（3509 全绿，3503→3509）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2296-wi15-compaction-artifact-typed-entry.md --strict` 退出码 0
- [x] checkstyle：由 commit 钩子 ast-grep 规则承担（No separate checkstyle gate required）
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件；无关杂散 .rels 改动已排除）

## Non-Blocking Follow-ups

- "标签等元数据条目化"（pi-D9 建议2 其余部分，无当前需求，out-of-scope improvement）
- ORM session-message writer 的 role 30 接入（另一消费链，optimization candidate）

## Closure

Status Note: ChatCompactionMessage 类型化条目（extends ChatUserMessage + copy() 覆写 + 迁移说明）经 producedEntry 身份引用链（Layer3→PipelineCompactor 透传→coordinator 富化）端到端落地，四方职责边界无双真相源；两轮对抗审查的三个 Blocker（扫描式富化错挂/漏 copy()/Pipeline 重包丢引用）全部修复并有直接回归测试；独立 closure audit 复跑 compaction 族 53 测试零回归 + 全量 3509 全绿 + 三工具 exit 0——WI15 交付全部落地。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_cd071180-394a-4599-951a-d1979a9894ba（fresh session）
- Evidence:
  - 7 验证点全 PASS（代码落地 12 项锚点 / 测试 6 个逐一核对断言真实性 / 命令自跑 BUILD SUCCESS + 53 compaction 族测试零回归 / owner doc+roadmap+log 在位 / nop-ai-api 变更 plan-first+owner doc+迁移说明三项齐备 / Anti-Hollow 富化链逐环双证据 / 文本一致性）
  - hollow exit 0；doc-links exit 0；plan-checklist exit 0
  - `./mvnw test -pl nop-ai/nop-ai-agent -am` 全量 3509 全绿
  - Minor 提醒已处理：commit 排除无关 .rels 杂散改动

Follow-up:

- 标签等元数据条目化 / ORM session-message writer role 30 接入（见 Non-Blocking Follow-ups）
