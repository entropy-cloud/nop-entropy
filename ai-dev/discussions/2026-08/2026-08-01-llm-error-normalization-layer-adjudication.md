# LLM 错误规范化层的架构归属裁定

> Status: resolved
> Date: 2026-08-01
> Scope: 错误规范化逻辑放在哪一层——ChatServiceImpl / IChatService 装饰器 / ChatResponse 字段；以及相关 roadmap 归属
> 结论沉淀：`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md` §3.4（最终方案）+ §4.0（拒绝方案）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W2e

---

## 背景

起点需求（用户表述）：某个 model 的订阅额度经常运行到一半耗尽，此时需要根据返回码确定**等待多长时间**或**切换其他备用账号**。这需要在底层 chatService 封装，并在 `llm.xdef` 中增加扩展配置，参考 `dialect.xdef`/`dialect.xml` 如何将返回信息**规范化为少数固定情况**。

AI 据此产出设计文档 `nop-ai-llm-error-normalization-design.md` 并经两轮独立子 agent 审查。本讨论记录的是设计落地为 roadmap 时，**错误规范化逻辑的架构归属**经用户三轮纠正、最终定调的过程。

---

## 第 1 轮 — AI 初始方案：在 ChatServiceImpl 内抛 LlmProviderException

### AI 方案

错误规范化逻辑放在底层 `ChatServiceImpl`：非 200 时读取错误体 + 头，按 `<errorMappings>` 规范化，**抛出带分类的 `LlmProviderException`**（携带 `classification` + `retryAfterMs`）。上层 `LlmErrorClassifier` 信任该异常携带的分类，重试策略消费之。

### 待用户确认

1. 错误规范化放 ChatServiceImpl 内部、以异常形式上抛，是否合适？

---

## 第 2 轮 — 用户纠正：通过 IChatService 接口增加，ChatServiceImpl 只保留基础调用

### 用户原始表述

> "通过 IChatService 接口来增加功能，ChatServiceImpl 中应该只有基础调用功能。"

### AI 复述

用户认为不应把错误规范化逻辑塞进 `ChatServiceImpl`——`ChatServiceImpl` 应只保留基础调用。新功能通过 `IChatService` 接口叠加。

### AI 执行

改为**装饰器模式**：新增 `ErrorNormalizingChatService implements IChatService`，包装 `ChatServiceImpl`，捕获其抛出的原始 HTTP 错误 → 按 `<errorMappings>` 规范化 → 重抛 `LlmProviderException`。`ChatServiceImpl` 仅做最小改动（非 200 忠实回传 body/headers 而非丢弃）。理由写成：装饰器可按需叠加、可复用覆盖 `DefaultAiChatService`、对 Agent 层零感知。

### 待澄清问题

1. 是否按此装饰器方案推进？（AI 默认采纳并改写了设计 §3.4）

---

## 第 3 轮 — 用户质疑：有几个 roadmap？该改哪个？

### 用户原始表述

> "nop-ai-agent 相关的有几个 roadmap？你应该改的是哪一个？是都没有完成吗？"

### AI 核查结论

nop-ai-agent 有**两个**特性 roadmap（此前 AI 未知，只盯着一个）：

| roadmap | 位置 | 性质 | 状态 |
|---------|------|------|------|
| main 设计 roadmap | `../../design/nop-ai-agent/nop-ai-agent-roadmap.md` | 架构状态索引 | **全 done**（L0–L5） |
| harness-evolution backlog | `../../backlog/nop-ai-agent-harness-evolution-roadmap.md` | 新增增量工作 | **全 todo**（W1–W6） |

AI 此前把 `todo` 的 L2+ 插进了 main done-roadmap，破坏了"全 done 状态索引"语义。**不是都没完成**：main 全 done，backlog 全 todo。

### AI 执行（纠正）

- **还原** main roadmap 到全 done
- 把错误规范化工作项放入 **backlog harness-evolution roadmap**，新增 **W2e**（LLM 错误规范化与配额感知恢复，W2 前置必须项），插在 W2 前——因为 W2-4 `ProviderFailoverQueue`（跨 provider 故障转移）正需要 W2e 的错误分类信号

### 关键认知

- main 设计 roadmap 是"已完成工作的状态索引"，新工作（todo）不进去；只有 backlog 工作完成 closure audit 后才追加入 main（harness-evolution 的"完成定义"明确写"更新 main roadmap 追加 L6"）
- backlog 才是"插入待办、按必须项排序"的地方

---

## 第 4 轮 — 用户再纠正：错误规范化回 ChatServiceImpl（经 dialect），结果放 ChatResponse

### 用户原始表述

> "错误规范化为什么不在基础的 IChatService 中，这里不是有 dialect 吗？需要 dialect 的都在 ChatServiceImpl 中。它的作用就是规范化输入输出。ChatResponse 上可以增加规范化好后的错误信息。"

### AI 复述

用户指出三件事：
1. 错误规范化**应该在 ChatServiceImpl 里**——因为 dialect 就在那里，"需要 dialect 的都在 ChatServiceImpl 中"。
2. ChatServiceImpl 的作用就是**规范化输入输出**——错误规范化属于输出规范化，是它的本职，不是"额外塞入"。
3. 规范化后的错误信息**放在 ChatResponse 上**（增加字段），不要用装饰器、不要用新异常类型。

### AI 分析（推翻第 2 轮装饰器方案）

经核查，用户的论点完全成立，且代码事实支撑：

1. **dialect 在 ChatServiceImpl 手里**：`parseResponse` 已持有 `LlmModel config`（含 `<errorMappings>`/`<errorResponse>`）。错误规范化需要的 provider 配置与成功解析同源，拆到装饰器反而要重新注入 dialect/config，凭空多一层。

2. **职责内聚**：`ChatServiceImpl` 既是"provider 差异屏蔽层"（`nop-ai-agent-llm-layer.md` §二定位），错误差异屏蔽正是其本职。

3. **ChatResponse 已有错误模式**：已有 `error`/`errorCode`/`isSuccess()`/`ChatResponse.error(...)` 工厂；`OpenAiDialect.parseResponse` 已对空 body 返回 `ChatResponse.error("NULL_RESPONSE",...)`；`<response errorPath>` 已能从 200 body 抽 error。**错误即输出**，走同一通道最自然。

4. **关键发现**：`LlmCallCoordinator` 今天有**两条分裂的错误路径**——抛异常走重试（`:122-174`）、`!isSuccess()` 走终止不重试（`:179-187`）。错误进 ChatResponse 后两条统一：`!isSuccess()` 也读 `errorClassification` 进重试决策，不再因"是返回值"放弃重试。

### 最终架构裁定

```
ChatServiceImpl（职责 = 经 dialect 规范化 I/O）
  ├─ 成功：dialect.parseResponse(body)        → ChatResponse(message, usage...)
  └─ 错误：dialect.parseErrorResponse(body,status,headers,config)
                                            → ChatResponse(error, errorCode, errorClassification, retryAfterMs)
```

- **不用装饰器、不用新异常类型**——错误即输出，走同一通道
- **响应级错误**（拿到 HTTP 响应，非 200 或 200-with-error）→ ChatResponse（带 errorClassification）
- **传输级错误**（连不上、超时——无 HTTP 响应无法构造 ChatResponse）→ 仍抛异常，由 `LlmErrorClassifier` 启发式处理
- ChatResponse 新增字段：`errorClassification`（ErrorClassification）、`retryAfterMs`（Long）、`httpStatus`（Integer）；沿用既有 `error`/`errorCode`

---

## 纠正

> 针对第 2 轮 AI 关于"ChatServiceImpl 应只有基础调用、用装饰器"的方案，用户在第 4 轮纠正如下：
> 错误规范化回 ChatServiceImpl（因 dialect 在此、其职责即规范化 I/O），结果放 ChatResponse 字段。装饰器方案与"新异常类型"方案均被推翻。
>
> 针对第 3 轮之前的 roadmap 改动，用户纠正：改错了文件（main 是 done 状态索引），应改 backlog harness-evolution roadmap。

---

## 总结

### 最终需求

LLM 错误规范化逻辑经 `ILlmDialect` 在 `ChatServiceImpl` 内完成（与成功响应 `parseResponse` 同构），结果放 `ChatResponse` 新字段（`errorClassification`/`retryAfterMs`/`httpStatus`）。响应级错误 → ChatResponse；传输级错误 → 异常。上层重试循环读 `ChatResponse.errorClassification` 驱动"等待 vs 切账号"恢复决策。

### 关键决策（含三轮纠正的演进）

| 轮次 | 方案 | 裁定 |
|------|------|------|
| 第 1 轮 | ChatServiceImpl 内抛 `LlmProviderException` | 被第 2 轮纠正（用户：ChatServiceImpl 只基础调用） |
| 第 2 轮 | `IChatService` 装饰器 `ErrorNormalizingChatService` | 被第 4 轮推翻（用户：dialect 在 ChatServiceImpl，职责即规范化 I/O） |
| 第 4 轮（**最终**） | ChatServiceImpl 经 dialect 规范化 → ChatResponse 字段 | **采纳** |

并行的 roadmap 归属裁定（第 3 轮）：

| 轮次 | 改动 | 裁定 |
|------|------|------|
| 第 2 轮末 | 插 L2+ 进 main 设计 roadmap | 被第 3 轮纠正（main 是 done 状态索引） |
| 第 3 轮（**最终**） | 还原 main；放 backlog harness-evolution 的 W2e（W2 前置必须项） | **采纳** |

### 待定事项

- W2e 的 6 个工作项（W2e-0~5）尚无 execution plan，待起草
- 流式路径 `Retry-After` 头依赖 `nop-http` `ServerEventPublisher` 挂响应头到异常（W2e-0 前置），未完成则流式仅支持 body 级 Retry-After

### 后续行动

- 设计文档 `nop-ai-llm-error-normalization-design.md` §3.4/§3.5/§3.8/§一(2) 已改为最终方案；§4.0 记录被拒绝的装饰器/异常方案及理由 ✅
- backlog `nop-ai-agent-harness-evolution-roadmap.md` W2e 已按最终架构更新（W2e-1 ChatResponse 加字段 / W2e-2 dialect 加 parseErrorResponse / W2e-3 重试循环 !isSuccess() 升级）✅
- main 设计 roadmap 已还原全 done，仅 Platform Reuse 表留指针指向 backlog W2e ✅
- 后续：起草 W2e 的 execution plan
