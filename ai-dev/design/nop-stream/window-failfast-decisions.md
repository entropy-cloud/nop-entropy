# window-failfast-decisions——D9/D10/D11/D12 四个 fail-fast 放行裁定落档

> Status: active
> 裁定日期：2026-10-02（D9-D12 首裁）
> 修订日期：2026-10-08（仅 D10/D11 两节放行改判，D9/D12 不变；承载 plan ai-dev/plans/nop-stream-sql-enhancement/01-wi0a-d10-d11-emit-decisions.md）
> 落档日期：2026-10-02（D10/D11 修订落档 2026-10-08）
> 负责人：仓库 owner（委托链：2026-10-02 执行指令「执行 nop-stream-sql-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」委托 ZCode 代理按 roadmap 建议项与 D1 已裁结论执行；2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」委托 ZCode 代理按 survey 建议作出 D10/D11 放行改判）
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI0d 行、前置裁定表 D9-D12 行）、ai-dev/design/nop-stream/sql-subset-and-semantics.md §1（D1=(a) 对 D9-D12 的输入约束）、`ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI0a 行——2026-10-08 修订依据）
> 承载 plan: ai-dev/plans/nop-stream-sql/05-wi0d-window-failfast-decisions.md（首裁）；ai-dev/plans/nop-stream-sql-enhancement/01-wi0a-d10-d11-emit-decisions.md（修订）

## 1. D9 allowedLateness = 放行

| 项 | 内容 |
|---|---|
| 选项 | 放行 / 保持 fail-fast |
| 结论 | **放行**（实施归 WI10） |
| 理由 | D1 已裁 (a) 终值语义：迟到数据触发窗口更新重发（fire-and-update），下游 last-value-wins 覆盖，无 retract 一致性问题。当前唯一闸门是 build 期双层 fail-fast——strategy 级 AdvancedTransforms.java:204-210 与 window 节点级 :228-233（均抛 ERR_STREAM_WINDOW_ATTR_UNSUPPORTED）。**运行时迟到管线已存在**：接口 WindowedStream 无该方法，但实现类 WindowedStreamImpl.java:52,:154-160 已有 allowedLateness(long) 并经 :238-292 四个算子构造点与 IWindowOperatorFactory.java:27,:46 接线到 WindowOperator（字段 :173）；迟到语义机制齐全（isWindowLate :1340-1342、cleanup timer :1358-1372、PaneInfo EARLY/ON_TIME/LATE :1147-1165、lateDataOutputTag :180、numLateRecordsDropped 指标 :192）。AdvancedTransforms.java:189-194 的「core WindowedStream cannot express」注释相对实现类已陈旧，WI10 实施时一并更正 |
| WI10 义务 | 接口面补方法（或 impl 通路）+ buildWindow 消费 xdef 双层声明值（strategy 级与节点级合并语义在 WI10 plan 定义并测试）+ 迟到语义缺口收口 |
| **关键实现约束（D9×D10 交互）** | emitWindowContents 在 DISCARDING 下每次 fire 后清空窗口内容（WindowOperator.java:1101-1106）。放行 lateness 时**清空点必须从 fire 时推迟到 window end + lateness（cleanup 时点）**，否则迟到更新退化为迟到元素的部分聚合，经下游 last-value-wins 恰好破坏 D1=(a) 的终值承诺 |

## 2. D10 accumulationMode = 放行 ACCUMULATING 声明（2026-10-08 修订，生效前提 WI38 done）

| 项 | 内容 |
|---|---|
| 选项 | 放行 DISCARDING 以外 / 保持 fail-fast |
| 结论（首裁 2026-10-02） | **保持 fail-fast**（仅 DISCARDING）——首裁时行号，现已漂移 |
| 结论（修订 2026-10-08） | **放行 ACCUMULATING 声明**。生效前提 = WI38 done：FU-8 修复（configureBuilder 从不传递 accumulationMode 致 DISCARDING 声明恒为 ACCUMULATING 的接线缺口）完成、声明值真实接线并有判别性测试后，AdvancedTransforms build 期 strategy 级 accumulationMode 门（当前 :241-248 拒绝非 DISCARDING）同步放开；WI38 done 前该门维持原样 |
| 边界（修订不变项） | (i) `ACCUMULATING_AND_RETRACTING` 维持 spec-only fail-fast（WindowOperator.java:590-596 运行时门禁原样保持）；(ii) D9 的 skipPurgeForLateness 交互（WindowOperator.java:1388-1400，event-time + allowedLateness>0 时 DISCARDING 亦推迟清空）不因本修订改变；(iii) ACCUMULATING 下 fire 后不清空窗口内容，迟到更新可继续重发，与 D9 fire-and-update 语义同向 |
| 修订理由 | 首裁自证「ACCUMULATING 运行时已支持」（WindowOperator.java:492 缺省即 ACCUMULATING；:590-596 错误文案明示可用 DISCARDING 或 ACCUMULATING），保持 fail-fast 的唯一理由是 scope 最小化而非语义不相容。现 scope 依据具备：survey B3（kafka-streams/ksql 形态）与本批 roadmap WI0a 立项；且 FU-8 实测暴露两层缺省相反（core WindowingStrategy.java:52/:60 缺省 DISCARDING vs WindowOperator.java:492 缺省 ACCUMULATING），放行 + WI38 接线修复使声明语义可验证。D1=(a) 输入约束 1 只消灭 retract 路线下的放行**必要性**，不禁止无 retract 诉求下的增量放行 |
| 首裁记录（保留） | 理由两层：(i) A&R spec-only，WindowOperator.java（首裁时 :449，现 :590-596）门禁；(ii) ACCUMULATING 运行时已支持，首版收敛为 DISCARDING-only 属 scope 最小化；「后续若放行 ACCUMULATING 须先修订本裁定」——**该前置条款已由本 2026-10-08 修订触发并完成** |
| 受影响 WI | WI38：修复接线（WindowedStreamImpl/factory/configureBuilder 链路传递声明值）+ 放开 build 期 strategy 级 accumulationMode 门（唯一 build 门；window 节点无该属性）+ DISCARDING 与 ACCUMULATING 双档判别性测试 + 本档边界三条不回退 |
| **生效前提达成（WI38，2026-10-08）** | **done**——接线五环闭合（WindowingStrategyModel → AdvancedTransforms.buildWindow → WindowedStreamImpl.accumulationMode → IWindowOperatorFactory 第四代 → WindowOperatorFactoryImpl.configureBuilder → WindowOperatorBuilder → WindowOperator 字段），build 门改判为仅拒 A&R；判别性测试钉于三层（flow 声明消费 TestWindowAccumulationModeDeclaration 含缺省档、core 四 call-site 传输 TestWindowedStreamAccumulationModeWiring、runtime 四方法算子字段 TestWindowOperatorFactoryAccumulationModeWiring）；Java API 无声明路径保持算子缺省 ACCUMULATING 不变。本节放行结论自即日起生效 |

## 3. D11 trigger = 放行 emit 两档（2026-10-08 修订；triggerId fail-fast 保留）

| 项 | 内容 |
|---|---|
| 选项 | 放行 / 保持 fail-fast |
| 结论（首裁 2026-10-02） | **保持 fail-fast**（首版不建 trigger 注册表）——首裁时行号，现已漂移 |
| 结论（修订 2026-10-08） | **放行 emit 两档**：形态锁死 Kafka Streams EmitStrategy 的 ON_WINDOW_UPDATE / ON_WINDOW_CLOSE 两档，`<sql>` 面 emit 属性取值 `update\|close`。实施归 WI37。**trigger 注册表前置作废**：两档是封闭枚举而非自由 trigger 体系，无需注册表，首裁「后续放行须先建 trigger 注册表并修订本裁定」的前置条款随本修订废止。**triggerId fail-fast 保留**：不引入自由 trigger 声明面，`triggerId` 属性与既有双层门（AdvancedTransforms strategy 级 :233-238、window 节点级 :258-263）维持原样，未实现声明仍显式拒绝 |
| 挂点裁定 | emit 挂 `<sql>` 顶层属性（编译产物落 windowingStrategy 声明，WI37 在 stream.xdef strategy 增 emit 属性承载），非窗口/聚合节点属性——`<sql>` 是模型级生成器唯一入口（sql-compiler-contract D8 已裁），声明面随查询整体下发 |
| 对首裁「部分值未经验证」反对的处置 | 首裁反对的是**开放式**自由 trigger 语义（任意 trigger 逻辑的部分发射未经引擎验证）。本裁定的两档是**封闭枚举**：发射语义由引擎管线唯一定义并经 WI37 判别性测试钉死（update 档=逐元素 FIRE + ACCUMULATING 不清空，close 档=现 fire-once 行为）；验证面由封闭档位的钉码测试承担——首裁设「注册表前置」要避免的未验证面在封闭枚举形态下不再成立 |
| 修订理由 | survey kafka-streams 篇 EmitStrategy 两档即范围锁定、ksql 篇 EMIT CHANGES/FINAL 同构；引擎 trigger 机制现状（triggers 包 Trigger/TriggerResult FIRE 等 11 类、WindowedStream.trigger 接口点、WindowOperator per-trigger accumulator）已具备承载，缺的只是逐元素 FIRE 档位与声明面；D1=(a) 终值语义下 update 档逐次发射经下游 last-value-wins 覆盖，无 retract 一致性问题 |
| 首裁记录（保留） | 理由：triggers 需要专用注册表（首裁时 AdvancedTransforms.java:190-192 注释自述）；自定义 trigger 引入部分发射语义，与首版 DISCARDING-only 结果一致性承诺不一致——部分发射经 last-value-wins 会被后续终值覆盖，但部分值本身未经验证；放行推迟至有 retract/触发器语义验证面后另行裁定（首裁时行号，现已漂移）。闸门位置（首裁时 :198-203 与 :234-239，现 :233-238 与 :258-263）原样保留——triggerId 门不废 |
| 受影响 WI | WI37：emit 两档实施（声明面 `<sql>`/xdef + 编译产物 + trigger/accumulationMode 形态 + 判别性测试）；WI0a 括注销号 |
| **WI37 交付达成（2026-10-08）** | done——`<sql emit>`/`<strategy emit>` 声明面（xdef 枚举 StreamEmitMode，缺省 CLOSE）、EmitUpdateTrigger（逐元素 FIRE + 窗口末 FIRE）、buildWindow 消费（UPDATE 强制 ACCUMULATING + 装配 trigger；join 引用 fail-fast）、编译器贯通（SPI 加参；UPDATE 仅窗口聚合路径写产物，plain-TUMBLE/连续聚合/join 三路径显式拒绝）；判别性 E2E：emit=UPDATE 12 发射（逐记录+窗口末）vs close 档单次发射。表面格式修正见下行 |
| **表面格式修正（WI37 执行 F-1，2026-10-08）** | 本节与本档修订记录的 emit 取值表面 `update\|close`（小写）修正为 `UPDATE\|CLOSE`——xdef 枚举字典校验（EnumStdDomainHandler.parseProp）按枚举常量名精确匹配，与 stream.xdef 既有枚举属性（accumulationMode="DISCARDING"）同一表面约定；小写表述系 WI0a 落档时的格式自选细节，非 survey 形态要求（Kafka Streams EmitStrategy 枚举名亦为大写约定）。两档语义、挂点、triggerId fail-fast 保留等裁定实质零变化 |

## 4. D12 窗口级 parallelism = 保持 fail-fast

| 项 | 内容 |
|---|---|
| 选项 | 放行 / 保持 fail-fast |
| 结论 | **保持 fail-fast** |
| 理由 | `<window>` 是虚拟构建点，无自身执行顶点（AdvancedTransforms.java:159-173 注释与实现）：其 window 函数算子归属于后续 `<aggregate>/<reduce>/<process>` 元素，per-transform parallelism（productization item 29）已由宿主元素承载并经 buildKeyBy/applyDeclaredParallelism 应用。窗口级声明 parallelism 无消费者，若强行应用会静默改写上游 keyBy 顶点——fail-fast 是正确行为而非缺口 |
| **不退化确认** | per-transform parallelism 通路（宿主元素声明 → 算子顶点）与 2PC sink 门禁在本裁定下**零改变**；D1=(a) 不触及该通路（输入约束 3）。既有锚点：nop-stream-flow 测试 TestPerTransformParallelismWiring 覆盖宿主元素 parallelism 装配。WI10 实施窗口参数化时不得改动 item 29 通路与 2PC 门禁 |
