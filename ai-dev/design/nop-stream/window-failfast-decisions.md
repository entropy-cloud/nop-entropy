# window-failfast-decisions——D9/D10/D11/D12 四个 fail-fast 放行裁定落档

> Status: active
> 裁定日期：2026-10-02
> 落档日期：2026-10-02
> 负责人：仓库 owner（委托链：2026-10-02 执行指令「执行 nop-stream-sql-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」委托 ZCode 代理按 roadmap 建议项与 D1 已裁结论执行）
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI0d 行、前置裁定表 D9-D12 行）、ai-dev/design/nop-stream/sql-subset-and-semantics.md §1（D1=(a) 对 D9-D12 的输入约束）
> 承载 plan: ai-dev/plans/nop-stream-sql/05-wi0d-window-failfast-decisions.md

## 1. D9 allowedLateness = 放行

| 项 | 内容 |
|---|---|
| 选项 | 放行 / 保持 fail-fast |
| 结论 | **放行**（实施归 WI10） |
| 理由 | D1 已裁 (a) 终值语义：迟到数据触发窗口更新重发（fire-and-update），下游 last-value-wins 覆盖，无 retract 一致性问题。当前唯一闸门是 build 期双层 fail-fast——strategy 级 AdvancedTransforms.java:204-210 与 window 节点级 :228-233（均抛 ERR_STREAM_WINDOW_ATTR_UNSUPPORTED）。**运行时迟到管线已存在**：接口 WindowedStream 无该方法，但实现类 WindowedStreamImpl.java:52,:154-160 已有 allowedLateness(long) 并经 :238-292 四个算子构造点与 IWindowOperatorFactory.java:27,:46 接线到 WindowOperator（字段 :173）；迟到语义机制齐全（isWindowLate :1340-1342、cleanup timer :1358-1372、PaneInfo EARLY/ON_TIME/LATE :1147-1165、lateDataOutputTag :180、numLateRecordsDropped 指标 :192）。AdvancedTransforms.java:189-194 的「core WindowedStream cannot express」注释相对实现类已陈旧，WI10 实施时一并更正 |
| WI10 义务 | 接口面补方法（或 impl 通路）+ buildWindow 消费 xdef 双层声明值（strategy 级与节点级合并语义在 WI10 plan 定义并测试）+ 迟到语义缺口收口 |
| **关键实现约束（D9×D10 交互）** | emitWindowContents 在 DISCARDING 下每次 fire 后清空窗口内容（WindowOperator.java:1101-1106）。放行 lateness 时**清空点必须从 fire 时推迟到 window end + lateness（cleanup 时点）**，否则迟到更新退化为迟到元素的部分聚合，经下游 last-value-wins 恰好破坏 D1=(a) 的终值承诺 |

## 2. D10 accumulationMode = 保持 fail-fast（首版 scope 收敛）

| 项 | 内容 |
|---|---|
| 选项 | 放行 DISCARDING 以外 / 保持 fail-fast |
| 结论 | **保持 fail-fast**（仅 DISCARDING） |
| 理由（两层） | (i) ACCUMULATING_AND_RETRACTING：spec-only，WindowOperator.java:449 运行时门禁原样保持（输入约束 2）。(ii) ACCUMULATING：**运行时已支持**（:449 错误文案明示可用 DISCARDING 或 ACCUMULATING；:1101 仅 DISCARDING 在 fire 后清空内容；算子构造缺省即 ACCUMULATING :360），与终值语义兼容——首版收敛为 DISCARDING-only 的理由是 **scope 最小化**，非语义不相容；放行属后续增量能力而非 retract 必需（输入约束 1 只消灭了 retract 路线下的放行必要性）。真正闸门是 build 期 AdvancedTransforms.java:211-218 |

## 3. D11 triggerId = 保持 fail-fast

| 项 | 内容 |
|---|---|
| 选项 | 放行 / 保持 fail-fast |
| 结论 | **保持 fail-fast**（首版不建 trigger 注册表） |
| 理由 | triggers 需要专用注册表（AdvancedTransforms.java:190-192 注释自述）；自定义 trigger 引入部分发射语义，与首版 DISCARDING-only（窗口关闭一次性 emit）的结果一致性承诺不一致——部分发射经 last-value-wins 会被后续终值覆盖，但部分值本身未经验证。放行推迟至有 retract/触发器语义验证面后另行裁定。闸门位置：strategy 级 :198-203 与节点级 :234-239 |

## 4. D12 窗口级 parallelism = 保持 fail-fast

| 项 | 内容 |
|---|---|
| 选项 | 放行 / 保持 fail-fast |
| 结论 | **保持 fail-fast** |
| 理由 | `<window>` 是虚拟构建点，无自身执行顶点（AdvancedTransforms.java:159-173 注释与实现）：其 window 函数算子归属于后续 `<aggregate>/<reduce>/<process>` 元素，per-transform parallelism（productization item 29）已由宿主元素承载并经 buildKeyBy/applyDeclaredParallelism 应用。窗口级声明 parallelism 无消费者，若强行应用会静默改写上游 keyBy 顶点——fail-fast 是正确行为而非缺口 |
| **不退化确认** | per-transform parallelism 通路（宿主元素声明 → 算子顶点）与 2PC sink 门禁在本裁定下**零改变**；D1=(a) 不触及该通路（输入约束 3）。既有锚点：nop-stream-flow 测试 TestPerTransformParallelismWiring 覆盖宿主元素 parallelism 装配。WI10 实施窗口参数化时不得改动 item 29 通路与 2PC 门禁 |
