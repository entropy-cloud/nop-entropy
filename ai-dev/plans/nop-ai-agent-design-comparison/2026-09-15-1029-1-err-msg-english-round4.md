---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-ERROR-MSGS
group: "2026-09-15-1029"
verify: [test]
---

# P2 round-4 错误消息英文化（NopAiErrors / AiCoderErrors 中文描述 → 英文）

## Current Baseline

- 来源：deep-audit round 4 登记、roadmap `## Follow-up Backlog` 未勾选项（`source: deep-audit round 4`），全部经 live repo 复核（HEAD `9cbf684d88`，2026-09-15）：
  1. **`NopAiErrors.ERR_AI_SESSION_ID_REQUIRED` 描述为中文**：`nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/NopAiErrors.java:12` `define("nop.err.ai.session-id-required", "会话ID不能为空", ARG_SESSION_ID)`——经 `NopAiChatResponseBizModel.java:64`（GraphQL `summarizeByModel` BizQuery）抛到用户可见路径。
  2. **`AiCoderErrors.ERR_AI_CODER_UNKNOWN_SQL_TYPE` / `ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH` 描述为中文**：`nop-ai/nop-ai-coder/src/main/java/io/nop/ai/coder/AiCoderErrors.java:20-24` `"未知的SQL类型: {sqlType}"` / `"表头和数据的列数不匹配:headers={headers},data={data}"`——分别经 `AiOrmSqlType.java:83`（`getStdSqlType` 未知类型抛错）与 `AiCoderHelper.java:70`（表头/数据列数不匹配抛错）到达用户路径。
  3. **AiCoderErrors javadoc 自相矛盾**：`ERR_AI_CODER_UNSUPPORTED_CONVERSION` 上方 javadoc 声称 "English descriptions preserve the historical message semantics verbatim (AGENTS.md English error-message convention for newly added codes)"，同文件另两个码却是中文——读者会误以为该文件全部英文。
- 前置先例：`McpServerErrors.ERR_MCP_FILE_NOT_FOUND` 已在 round 2（plan 1638-3 Phase 5）完成英文化（ID 与参数契约不变）；本次对齐该先例处理同源问题。
- 归属模块：nop-ai-service（1）+ nop-ai-coder（2/3）。
- 验证面：描述文本变更（ErrorCode ID / 参数契约 / 抛错站点不变），涉及 `./mvnw test -pl nop-ai/nop-ai-service,nop-ai/nop-ai-coder -am`；无 owner doc 声称这些描述文本（属错误码定义面，`error-handling.md` 只规定两档策略与英文消息约定）。

## Goals

- `ERR_AI_SESSION_ID_REQUIRED`、`ERR_AI_CODER_UNKNOWN_SQL_TYPE`、`ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH` 三个 ErrorCode 描述英文化（ID `nop.err.ai.*` 与参数契约不变）。
- AiCoderErrors 内 javadoc 与实现一致（不再有"英文约定"声明与中文描述并存的矛盾）。
- 两个文件复核确认无其他中文描述残留（同文件其余码全英文）。
- 回归测试守护：错误码 ID/参数契约不变 + 描述为英文（无 CJK 字符）——防止再次漂移为中文。
- `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`ai-dev/logs/` 收口条目。

## Non-Goals

- 不改 3 个 ErrorCode 的 ID、参数名、参数契约与抛错站点；不改任何日志/前端消费逻辑。
- 不处置本轮 roadmap 其余未勾选项（nop-ai-agent / gateway / shell 相关 P2/P3 项，另开计划）。
- 不运行 mvn 全量构建（仅受影响的 nop-ai-service / nop-ai-coder 及依赖）。

## Phase 1 — 三个 ErrorCode 描述英文化 + javadoc 一致性 + 回归守护

Status: completed

Targets: `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/NopAiErrors.java`、`nop-ai/nop-ai-coder/src/main/java/io/nop/ai/coder/AiCoderErrors.java`、`nop-ai/nop-ai-service/src/test/`、`nop-ai/nop-ai-coder/src/test/`

- Item Types: `Fix | Proof`

- [x] `Fix` `NopAiErrors.java:12` 描述改英文（"Session ID must not be empty"），ID `nop.err.ai.session-id-required`、参数 `{sessionId}` 不变。
- [x] `Fix` `AiCoderErrors.java:20-24` 两个描述改英文（"Unknown SQL type: {sqlType}" / "Headers and data column count mismatch: headers={headers}, data={data}"），ID 与参数契约不变。
- [x] `Fix` AiCoderErrors 内 javadoc 与实现一致：删除或改写"English descriptions preserve the historical message semantics verbatim"中与现状矛盾的表述（同文件全部码英文后再无例外语义）。
- [x] `Proof` 复核：`NopAiErrors.java` 与 `AiCoderErrors.java` 全文 grep 确认零中文残留（`[\u4e00-\u9fff]` 扫描）；顺带复核同目录/同模块其余错误码容器文件无同类问题（如 nop-ai-service 其余 Errors 类），发现即一并处置或登记。
- [x] `Fix` 回归测试：新增/扩展测试断言 3 个 ErrorCode 描述为英文（无 CJK 字符）且 `getErrorCode()`/参数契约不变（复用 `TestNopAiChatResponseSummarizeByModel` 既有 `ERR_AI_SESSION_ID_REQUIRED` 断言位 + 新增描述守卫；coder 侧对应 `AiCoderErrors` 描述守卫测试）。

Exit Criteria:

- [x] 3 个 ErrorCode 描述为英文，ID `nop.err.ai.*` 与参数契约不变（diff 可核查）。
- [x] AiCoderErrors javadoc 与实现一致（无"英文约定"声明与中文描述并存）。
- [x] 两文件全仓 grep `[\u4e00-\u9fff]` 零命中（错误码定义面）。
- [x] 回归测试断言描述无 CJK + `getErrorCode()` 契约不变（断言具体行为，非仅"不报错"）。
- [x] **端到端验证**：`NopAiChatResponseBizModel.summarizeByModel` 空 sessionId → 抛 `ERR_AI_SESSION_ID_REQUIRED` 的既有测试路径仍通过（错误码一致），描述文本为英文。
- [x] **接线验证**：抛错站点（NopAiChatResponseBizModel.java:64 / AiOrmSqlType.java:83 / AiCoderHelper.java:70）与 ErrorCode 定义仍指向同一 ID。
- [x] **无静默跳过**：无描述被留空或吞掉；所有处置显式落地。
- [x] owner doc 更新：错误码定义面无契约文档声称这些描述文本——显式写 `No owner-doc update required`（`error-handling.md` 英文约定已成立，本次是向其收敛）。
- [x] `./mvnw test -pl nop-ai/nop-ai-service,nop-ai/nop-ai-coder -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：3 个 ErrorCode 描述英文化收口 + javadoc 一致性 + 零中文残留 + 回归测试守护（ID/参数契约不变 + 描述无 CJK）后，由独立子 agent closure-audit 完成并写入 `## Closure`；本 section 不保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-1029-1-err-msg-english-round4-1-fadc3ad7 to opencode-pid-32617
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-1029-1-err-msg-english-round4-1-fadc3ad7

## Verification

- pass test 2026-09-15-0333 exit=0

## Closure

- dispatch audit #audit-20260915-0333-2026-09-15-1029-1-err-msg-english-round4-r4-4bd3f5ef to opencode-pid-43881 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-0333-2026-09-15-1029-1-err-msg-english-round4-r4-4bd3f5ef：独立 closure audit 复核通过——三码描述英文 + ID/参数契约不变（git diff 可核查）、AiCoderErrors javadoc 一致、两文件 CJK 零命中（rg 退出码 1）、抛错站点仍指向同一 ID（NopAiChatResponseBizModel.java:64 / AiOrmSqlType.java:83 / AiCoderHelper.java:70）、`./mvnw test -pl nop-ai/nop-ai-service,nop-ai/nop-ai-coder -am` 全绿（新守卫测试 +1/+4 全部执行 0 失败）、`check-doc-links.mjs --strict` 0 errors；15/15 勾选 + pass 行 + 收口记录齐备，roadmap 与 09-15 日志同步。