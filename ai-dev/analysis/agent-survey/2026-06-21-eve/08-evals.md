# 08 · Eval 系统

> 来源：`docs/evals/*` + `packages/eve/src/evals/*` + `e2e/`
> 这是 eve 把"测试 agent"工程化的精华，与传统 agent 框架的"手动验证"形成鲜明对比。

## 核心命题

> 「An eval is a scored check that runs your agent against real sessions and grades the result.」
> —— `docs/evals/overview.mdx:6`

**关键**：eval 走**与真实用户相同的 HTTP 表面**——runner 启动（或指向）一个真实 agent server，通过 TypeScript client 协议驱动 session，再对结果打分。

**通过 = agent 启动 + 接受请求 + 产出断言的结果**。这是真正的端到端。

## 目录与发现

- **位置**：app 根目录下的 `evals/`（与 `agent/` 平级，**不在** `agent/` 内）
- **文件**：`.eval.ts`，每个文件默认一个 eval；可 default-export 数组做数据集扇出
- **身份**：**文件路径**就是 id（如 `evals/weather/brooklyn-forecast.eval.ts` → `weather/brooklyn-forecast`），**无需写 `id`/`name`**
- **必需**：每个 `evals/` 根目录一个 `evals.config.ts`（`defineEvalConfig`，声明 `judge` / `reporters` / `maxConcurrency` / `timeoutMs` 默认值）

## `defineEval` 定义

```ts
// packages/eve/src/evals/define-eval.ts:21-28
import { defineEval } from "eve/evals";

export default defineEval({
  description: "Text-reply smoke for the eve eval CLI.",
  async test(t) {
    await t.send('Reply with exactly the text "smoke ping" and nothing else.');
    t.completed();
    t.didNotFail();
    t.messageIncludes("smoke ping");
    t.usedNoTools();
  },
});
```

要点：
- **每个 `.eval.ts` 文件 = 一个测试用例**
- **没有 `input`/`run`/`checks`/`scores`/`expected` 字段**（legacy key 黑名单，`define-eval.ts:43-81`）
- **`test(t)` 是命令式的**：先驱动 agent（`t.send` / `t.respond` / `t.sendFile`），再断言

## `t` 上下文：既是驱动器也是断言器

`t` **没有分开的 input/run/checks/scores 字段**，写普通控制流即可。这是 eve eval 设计的精髓——**测试代码即测试规格**。

### 驱动 API

| 方法 | 用途 |
|---|---|
| `t.send(input)` | 发消息；input 可为 string、`{ message, clientContext, outputSchema }` |
| `t.sendFile(text, filePath, mediaType)` | 发附件 |
| `t.respond(...responses)` / `t.respondAll(optionId)` | HITL 响应 |
| `t.sleep(ms?)` | 暂停 |
| `t.newSession()` | 起新 session |
| `t.target.fetch(path, init)` | 直接打 channel 路由（custom channel） |
| `t.target.attachSession(id)` | 附加到既有 session |
| `t.target.dispatchSchedule(id)` | 触发 cron schedule（dev-only） |
| `t.target.capabilities.devRoutes` | 检测是否部署模式 |

读取：`t.reply`（最后一条 assistant 消息）、`t.sessionId`、`t.events`。

## 三种断言面

| 面 | 示例 | 默认严重度 |
|---|---|---|
| **run-level 方法**（观察整个 run，无值参） | `t.completed()`、`t.calledTool("get_weather")`、`t.usedNoTools()`、`t.toolOrder([...])` | **gate**（失败 = eval 失败） |
| **`t.check(value, assertion)`**（用 `eve/evals/expect` 的确定性 builder） | `t.check(t.reply, includes("Sunny"))`、`equals`、`matches` | gate（`similarity` 例外，soft） |
| **`t.judge.autoevals.*`**（LLM-as-judge，复用 Braintrust autoevals 家族） | `factuality(expected)`、`summarizes(expected)`、`closedQA(criteria)`、`sql(expected)` | **soft**（默认仅记录，不 fail） |

### run-level 方法清单（gate 默认）

```ts
t.completed()                  // turn 正常完成
t.didNotFail()                 // session 没失败
t.waiting()                    // session 处于 waiting 状态
t.messageIncludes(token | RegExp)
t.calledTool(name, opts?)      // opts: { isError, times, output 谓词 }
t.notCalledTool(name)
t.usedNoTools()
t.maxToolCalls(max)
t.toolOrder(names)
t.calledSubagent(name, opts?)
t.loadedSkill(skill, opts?)
t.noFailedActions()
t.event(predicate, label)      // 自定义事件流谓词
t.outputEquals(value)
t.outputMatches(schema)
```

### `t.check` 的确定性 builder

来自 `eve/evals/expect`：
- `includes(token)` —— 包含子串
- `equals(value)` —— 深相等
- `matches(regex)` —— 正则匹配
- `similarity(text)` —— 相似度（默认 soft）

## gate vs soft

每个断言返回**链式句柄**：

| 方法 | 效果 |
|---|---|
| `.gate(threshold?)` | 提升为硬门（失败 = eval 失败） |
| `.soft(threshold?)` | 降级为仅记录 |
| `.atLeast(threshold)` | 带阈值的 soft |

默认规则：
- **gate**：`includes` / `equals` / `matches`、所有 run-level 方法
- **soft**：`similarity`、所有 `t.judge.*`
- 无阈值的 soft **永不 fail**，只进报表
- CI 用 `eve eval --strict` 让 soft 阈值不达也失败

## Verdict 计算（三态）

`packages/eve/src/evals/runner/verdict.ts:11-25`：

```ts
export function computeEvalVerdict(input) {
  if (input.error !== undefined) return "failed";
  let demoted = false;
  for (const assertion of input.assertions) {
    if (assertion.passed) continue;
    if (assertion.severity === "gate") return "failed";
    demoted = true;
  }
  return demoted ? "scored" : "passed";
}
```

| verdict | 含义 |
|---|---|
| `passed` | 所有 gate 通过，无 soft 失败 |
| `scored` | 所有 gate 通过，但有 soft 失败（`--strict` 提升为 fail） |
| `failed` | 有 gate 失败 |

## LLM-as-judge

- **judge 模型永远不是被测模型**（避免自己评自己）
- 三层 innermost-wins：per-call > per-eval > `evals.config.ts`
- 字符串 model id 走 Vercel AI Gateway（需 `AI_GATEWAY_API_KEY` 或 `VERCEL_OIDC_TOKEN`）
- AI SDK `LanguageModel` 实例直连
- 配了 model 但缺凭证 → **可见地 skip**，不报错

judge 家族（`t.judge.autoevals.*`，复用 Braintrust autoevals）：
- `factuality(expected)` —— 事实性
- `summarizes(expected)` —— 摘要质量
- `closedQA(criteria)` —— 封闭问答
- `sql(expected)` —— SQL 正确性

## 典型判定模式（实例归纳）

| 模式 | 例子 |
|---|---|
| **verbatim echo**（避免 judge 不稳定） | `text-reply.eval.ts:16`：要求模型逐字回 "smoke ping"，再 `messageIncludes` |
| **跨轮 session 连续性** | `multi-turn-session.eval.ts:13-23`：第一轮塞"marigold"，第二轮问；断言 `t.sessionId` 没变 |
| **schema 验证** | `output-schema-turn.eval.ts:24-37`：发 `outputSchema` turn，用 `matches(z.object(...))` |
| **附件** | `image-attachment.eval.ts:18-28`：`t.sendFile` 发 PNG，断言回复含 "cat" |
| **client context** | `client-context.eval.ts:16-24`：发 `clientContext` 数组，断言回复含约定 token |
| **工具调用断言** | `structured-echo-narrowing.eval.ts:17-24`：`calledTool("structured-echo", { isError: false, output: ... })` 函数谓词 |
| **工具错误恢复** | `throw-recover.eval.ts:7-34`：调 always-throws → 断言 `isError: true` → 再发一轮确认 session 还活着 |
| **HITL park/resume** | `approve-then-no-regate.eval.ts:13-40`：`expectInputRequests` → `respondAll("approve")` → 第二轮不再 park |
| **HITL ask_question** | `ask-question-select.eval.ts:20-37`：断言 `display === "select"` + option ids，再 respond |
| **LLM-as-judge** | `deny-then-regate.eval.ts:23-30`：`t.judge.autoevals.closedQA(criteria, { on: denied.message }).atLeast(0.5)` |
| **子 agent delegation** | `local-delegation.eval.ts:14-21`：`calledSubagent("echo-marker", { output: /.../ })` + 消息含 SUBAGENT_TOKEN |
| **跨 channel handoff** | `cross-channel-receive.eval.ts:16-43`：`postChannel` POST `/webhook` → `attachSession` → 检查 `message.completed` 事件 |
| **schedule dispatch** | `schedule-dispatch.eval.ts:22-67`：`dispatchSchedule("heartbeat")` → `attachSession` → 在事件流找 `record-heartbeat` |
| **OpenAPI + HITL** | `tfl-swagger-approval.eval.ts:12-60`：`expectInputRequests({ display: "confirmation", toolName })` → `respondAll("approve")` |
| **sandbox 网络策略** | `network-policy.eval.ts:23-37`：用正则区分「网络被拒」vs「curl 不存在」 |

## CLI 运行方式

```bash
# 跑所有发现的 eval（默认对本地 dev server）
eve eval

# 单个或一组（evals/weather/）
eve eval weather

# 指向已有服务器/部署
eve eval --url https://<app>

# 严格模式（soft 阈值不达也 fail）
eve eval --strict

# 列出所有 eval
eve eval --list
```

退出码 `0` = 全部 gate 通过。

选项：`--url`、`--tag`、`--strict`、`--list`、`--timeout`、`--max-concurrency`、`--json`、`--junit`、`--skip-report`、`--verbose`。

## Reporters

`evals.config.ts` 里可挂：
- `Braintrust({ projectName })` —— 上传 Braintrust
- JUnit XML —— CI 集成
- config 级 reporter 观察所有 eval

## e2e 测试组织（fixture-owned）

`e2e/` 目录的 10 个 fixture，每个是独立 eve app：

| fixture | 覆盖 |
|---|---|
| `agent-basic-runtime` | 基础 runtime：多轮、附件、output schema |
| `agent-channels` | 自定义 channel + 跨 channel handoff |
| `agent-openapi-swagger` | OpenAPI/Swagger 连接（含 HITL） |
| `agent-schedules` | 定时调度（cron markdown） |
| `agent-skills` | skill auto-invocation + dynamic skills |
| `agent-subagents` | 本地子 agent delegation |
| `agent-subagents-hitl` | 子 agent 工具 HITL 代理 |
| `agent-tools` | 工具调用、动态工具、结果 narrowing、错误恢复 |
| `agent-tools-hitl` | HITL 流程（once/ask_question） |
| `agent-tools-sandbox` | Vercel Sandbox 代码执行 |

每个 fixture 的 `package.json`：`"test:e2e": "eve eval --strict"`。

运行单个 fixture（`e2e/README.md:8-13`）：
```sh
cd e2e/fixtures/agent-basic-runtime
pnpm exec eve eval --strict
```

根目录便利命令：
```sh
pnpm test:e2e  # 跑所有有 test:e2e script 的 fixture
```

## E2E 的设计哲学（`e2e/README.md:1-30`）

- "End-to-end coverage is **fixture-owned** `eve eval` runs"
- "The suite only runs fixture eval files from the fixture directory"
- "Every retained e2e eval is **deterministic and self-contained**"
- "Coverage that needs external services, injected env, or provider credentials is intentionally **not** part of this suite"（但模型凭证除外——agent 和 judge 都跑真实 `openai/gpt-5.5`）

## Vercel e2e 模式

`e2e/README.md:44-56`：用 `vc deploy --prebuilt` 拿不可变 preview URL，再 `eve eval --strict --url "$DEPLOYMENT_URL"`：

```sh
vc link --yes --project "$VERCEL_PROJECT_ID"
vc env pull --yes --environment=preview
VERCEL=1 VERCEL_ENV=preview VERCEL_TARGET_ENV=preview \
  VERCEL_PROJECT_ID="$VERCEL_PROJECT_ID" \
  VERCEL_DEPLOYMENT_ID="dpl_eve_e2e_manual" \
  pnpm exec eve build
DEPLOYMENT_URL="$(vc deploy --prebuilt --yes --target=preview | tail -n 1)"
npx eve eval --strict --url "$DEPLOYMENT_URL"
```

> 不设 `VERCEL_TEAM_ID`：sandbox template keys 必须在 build 和 runtime 一致派生，而 Vercel 运行时无 team 变量。

## 设计启示

eve 的 eval 系统有几个值得借鉴的设计：

1. **测试代码即测试规格**：用 `t` 把驱动和断言合二为一，消灭"配置式测试"的冗余
2. **gate vs soft 二分**：让 LLM-judge 这类不确定断言可以"仅记录不 fail"，CI 用 `--strict` 提升门槛
3. **走真实 HTTP 表面**：不 mock，不 stub，端到端验证 agent 真能启动+接受请求+产出结果
4. **fixture-owned**：每个 fixture 是独立 app，自己的 eval 套件，解耦又可组合
5. **identity 从路径派生**：与框架其他 slot 一致，eval 也不写 `id`/`name`
6. **deterministic by contract**：e2e 必须确定且自包含，外部服务依赖挡在门外

这是把"agent 测不准"问题工程化的范例。
