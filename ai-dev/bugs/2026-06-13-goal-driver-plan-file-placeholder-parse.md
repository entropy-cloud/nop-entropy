# 2026-06-13 Goal Driver PLAN_FILE Placeholder Parse Bug

## Problem

- goal-driver 的 `PLAN_DRAFT` 步骤反复报 `ERROR: PLAN_FILE="/path/to/plan.md" does not exist — AI returned placeholder`，连续 retry 3 次都失败。
- 发生在 `_tmp/2026-06-13-223030-goal-driver/nop-ai-agent.log` 这次运行（nop-ai-agent 模块，第 6/7/8 步 PLAN_DRAFT visit #1/#2/#3）。
- 表面症状：看似 AI 一直在返回字面量占位符 `/path/to/plan.md`，怎么 retry 都不改。

## Diagnostic Method

- **诊断难点**：错误信息把锅指向 AI（“AI returned placeholder”），让人以为是模型没遵循指令，方向被带偏。
- **先查了什么**：直接读第一次 PLAN_DRAFT 的完整 log（`_tmp/2026-06-13-223030-goal-driver/oc-PLAN_DRAFT-1781363088281-0tw337.log`），而不是只看 engine 的 ERROR 行。
- **被排除的假设**：
  - “AI 没创建文件” —— 排除：log 末尾 `Write ai-dev/plans/160-nop-ai-agent-talent.md` + `ls -la` 确认磁盘上文件真实存在（18454 bytes，23:11 创建）。
  - “AI 返回了占位符” —— 排除：log 第 304–306 行 AI 真实输出是 `<PLAN_FILE>/Users/abc/.../160-nop-ai-agent-talent.md</PLAN_FILE>`，是正确绝对路径。
- **确认根因的直接证据**：`grep -n "FLOW_VARS\|PLAN_FILE\|AI_STEP_RESULT"` 该 log 文件，发现 `<FLOW_VARS>` 出现两次：
  - 第 45–47 行：**prompt 里的示例文本**（`/path/to/plan.md` 占位符），来自 `ai-dev/tools/opencode-goal-driver/prompts/plan-draft.md:45-47`。
  - 第 304–306 行：AI 真实输出（正确路径）。
  - engine 只取了**第一个**匹配 → 永远拿到占位符。
- **辅助铁证**：同一文件里 `extractTag`（marker 提取）取的是**最后一个**匹配，所以 marker `created` 提取正确；两个提取函数行为不一致本身就是 bug 信号。

## Root Cause

- **`engine.js:91` `_extractFlowVars` 用非贪婪正则只取第一个匹配**：
  ```js
  const m = text.match(/<FLOW_VARS>([\s\S]*?)<\/FLOW_VARS>/);  // 非贪婪 → 命中最先出现的块
  ```
  与之对比，`engine.js:18-23` 的 `extractTag`（marker 提取）用 `matchAll` 取**最后一个**，所以 marker 永远是对的，掩盖了 FLOW_VARS 的解析问题。
- **`executor.js:45` 把完整 prompt 写进 log header**：
  ```js
  const header = [`# cmd: ${cmd} ${args.join(" ")}`, ...];  // prompt 作为 args 末尾被原样写入
  ```
  `ai-dev/tools/opencode-goal-driver/prompts/plan-draft.md` 为了教 AI 输出格式，自身含一段 `<FLOW_VARS><PLAN_FILE>/path/to/plan.md</PLAN_FILE></FLOW_VARS>` 示例。该示例随 prompt 被写进 log 文件头部，排在 AI 真实输出之前，于是成为“第一个”匹配。
- **retry 无效且误导**：每次 retry 重新跑，prompt 头永远在 log 最前面，第一个匹配永远是占位符。更糟的是 `engine.js:637` 追加的 feedback “the file you specified does not exist” 指责 AI，但 AI 指定的文件明明存在 —— feedback 本身是错的。

## Fix

> **状态：已修复**（`engine.js` + `executor.js` 已改，5 个回归测试通过）。

- **Fix 1（根因，已应用）**：`engine.js` `_extractFlowVars` 改为取**最后一个** `<FLOW_VARS>` 匹配，与 `extractTag`（marker 提取）行为对齐。用 `matchAll` + `matches[matches.length - 1]`。即使 prompt 不慎泄漏进 text，也优先采信最后的 AI 输出。
- **Fix 2（源头治理，已应用）**：`executor.js` log header 不再写完整 prompt —— 新增 `summarizeArg` 把超长（>80 字符）或含换行的 arg 截断为 `preview...(<N> chars)`。opencode run 的 prompt（含 `/path/to/plan.md` 示例）不再原样进入 log header，从源头消除占位符 token 污染。
- **Fix 3（未做，可选后续）**：校验失败时的 feedback（`engine.js:637`）区分“值是已知占位符字面量”与“值是真实路径但文件不存在”，给出更准确提示。Fix 1+2 已彻底闭合本 bug，Fix 3 属体验改进，暂不做。

## Tests

已新增 5 个回归测试，全部通过（`node --test test/core.test.js`）：

- `ai-dev/tools/opencode-goal-driver/test/core.test.js`
  - `FlowEngine — flowVars extraction takes LAST block`：
    - “prompt-example 块在 AI 输出之前时，返回真实值” —— 直接复现本 bug 场景（text 含两个 FLOW_VARS 块，第一个是 `/path/to/plan.md` 占位符），断言取最后一个。
    - “无 FLOW_VARS 块时返回空对象”。
  - `summarizeArg — keeps prompt out of log header`：
    - 短 arg 原样返回。
    - 长/含换行 arg 被截断、单行、且不含 `/path/to/plan.md` 占位符 token。
    - 超 80 字符的无换行 arg 也被截断。

## Affected Files

- `ai-dev/tools/opencode-goal-driver/src/engine.js`（`_extractFlowVars` line 90-100；feedback line 637）
- `ai-dev/tools/opencode-goal-driver/src/executor.js`（header line 44-50）
- `ai-dev/tools/opencode-goal-driver/test/core.test.js`（新增 5 个回归测试）
- 相关证据 log：`_tmp/2026-06-13-223030-goal-driver/oc-PLAN_DRAFT-1781363088281-0tw337.log`
- 受影响的 prompt（无需改，但需理解其示例文本会进 log）：`ai-dev/tools/opencode-goal-driver/prompts/plan-draft.md`

## Notes For Future Refactors

- **解析函数必须统一“取第一个还是最后一个”的语义**：任何新增的 XML-tag 提取辅助函数都要和 `extractTag` / `_extractFlowVars` 保持一致，否则一旦 prompt/历史输出泄漏进待解析 text，就会重演本 bug。建议抽取一个统一的 `extractLastXmlBlock(text, tag)` 工具函数复用。
- **不要把 prompt 原文写进会被下游解析的文本流**：log/缓存文件若同时含“指令”和“响应”，解析时必须能区分两者（用明确的分隔 marker，或物理分离文件）。仅靠非贪婪正则在“指令里的示例”和“响应”长得一样时是不可靠的。
- **错误信息要指对方向**：本 bug 的 `AI returned placeholder` 措辞误导排查长达数轮。校验失败时应同时打印“实际收到的值”和“它在 text 中第几次出现 / 出现次数”，便于一眼看出是解析取错块还是真值就有问题。
