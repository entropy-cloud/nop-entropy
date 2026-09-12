# nop-ai-agent vs deepseek-harness vs pi：全维度三方对照总报告（WI28 交付物）

> Status: resolved
> Date: 2026-09-12
> Scope: nop-ai-agent 与 deepseek-harness（dsh）、pi 在 agent 内在设计（D1-D10 十个维度）的全部对比结论汇总：全维度三方对照总表、结构性差异（范式级）清单、逐维裁定汇总与证据索引、可吸收增量建议汇总
> Conclusion: 20 个对比方向（10 维 × 2 对手）的裁定分布——nop 领先 4（dsh-D5、pi-D5/D7/D10）、对方领先 6（dsh-D1/D3/D6 + pi-D1/D3/D6）、等价 10。三个结构性总结：①无一方全面领先——nop 强在治理纵深（治理终态/预算护栏/故障转移/恢复守护/安全纵深/修复链），dsh 强在执行工程（流式/事件溯源/重试持久化/统一扩展原语），pi 强在接口工程（缓存四旋钮/类型化条目/三层扩展位/双队列注入）；②路线分岔集中在五个范式轴（扩展组织/停止哲学/真相模型/自动化程度/辅助调用缓存策略）；③roadmap 三项初步假设两项修正一项成立（上限假设不成立、failover 假设成立、缓存假设成立且幅度最大）。
> 基线: nop=800baf32da、dsh=c291e7961a、pi=c49906ec7；裁定与证据以各维度报告为准（本文档为汇总索引）

## ① 结论摘要

- 总表：20 个裁定单元格中 nop 领先 4、对方领先 6、等价 10——三方各有不可替代的强项域，无全面领先者。
- nop 的领先全部集中在"自主运行的治理与安全"：唯一内置故障转移（D5 双向）、唯一安全纵深+修复链（对 pi D7）、唯一编排全栈（对 pi D10）。
- 对方的领先集中在"交互执行的工程成熟"：dsh 的流式整流持久化（D1/D3）、构造性缓存（D6）；pi 的缓存四旋钮（D6）与事件类型化（D3）。
- 五个范式轴（③ 节）解释了大部分裁定分歧：同一能力在三种架构下的实现不可互相直译——吸收建议必须按 ⑤ 的归组整体评估而非单点搬运。
- 可吸收优先级提示（按 nop 改造成本/收益比排序）：D6 缓存观测消费（资产已就位）> D8 溢出压缩重试环 > D1 流式路径接线 > D3 事件类型化 > D9 增量写路径（详见 ⑤）。

## ② 全维度三方对照总表

裁定值来自各维度报告 ⑥ 节（dsh-Dx 报告对比 dsh、pi-Dx 报告对比 pi）：

| 维度 | vs dsh 裁定 | vs pi 裁定 | 一句话依据（报告索引） |
|---|---|---|---|
| D1 内部 agent loop | 对方领先 | 对方领先 | dsh 流式整流+持久化注入（dsh-D1 D1-4/5）；pi 双队列+QueueMode（pi-D1 D1-5）；nop 治理终态+预算护栏独有（两报告 D1-2/3） |
| D2 扩展点 | 等价 | 等价 | 三种组织哲学能力上限相当（dsh-D2/pi-D2 D2-1；04 census 38/25/53 面） |
| D3 事件类型与触发 | 对方领先 | 对方领先 | dsh 49→56 类可扩展持久事件+五 mode；pi 判别联合+双消费+三模式 UI（dsh-D3/pi-D3 D3-1/3/4）；nop 全瞬时单一扇出 |
| D4 容错性 | 等价 | 等价 | dsh 重试持久化（dsh-D4 D4-2）vs pi 溢出独立分类+一次性门闩（pi-D4 D4-1/5）vs nop 恢复纵深（两报告 D4-5）——三方各执一端 |
| D5 自动切换 | nop 领先 | nop 领先 | nop 唯一内置多通道切换+熔断+分级路由（dsh-D5/pi-D5 全子机制）；dsh 留白 waterfall、pi 零内置——自动化谱系 nop>dsh>pi |
| D6 前缀缓存利用 | 对方领先 | 对方领先 | dsh 构造性稳定+压缩复用（dsh-D6）；pi 四旋钮+禁写隔离+观测闭环（pi-D6）；nop 四项不存在/空置（两报告 ②）——本维 nop 差距最大 |
| D7 工具系统 | 等价 | nop 领先 | vs dsh：修复链+安全纵深 vs 投影分离+并发调度互补（dsh-D7）；vs pi：pi 修复链与安全纵深全缺（pi-D7 D7-4/5） |
| D8 上下文工程与压缩 | 等价 | 等价 | 三方各有独有机制：nop 分层+引用式、dsh 溢出环+影子价、pi 双预算+树级压缩（三报告 D8-3/4/5） |
| D9 会话持久化与恢复 | 等价 | 等价 | 范式三分（事件溯源/快照+journal/类型化条目）：dsh 数据模型领先、pi 类型化+迁移链领先、nop 恢复纵深双向领先（三报告 D9-4 同结论） |
| D10 多代理与子代理 | 等价 | nop 领先 | vs dsh：编排全栈 vs 传输谱系正交（dsh-D10）；vs pi：pi 零内置（pi-D10 全子机制） |
| **合计** | **nop 1 : 对方 3 : 等价 6** | **nop 3 : 对方 3 : 等价 4** | 合计 nop 4 : 对方 6 : 等价 10 |

## ③ 结构性差异（范式级）清单

1. **扩展组织三分**（D2，04 全篇）：nop=分层枚举点+结果对象白名单合同（会话 12 点/尝试 4 点显式分 scope）；dsh=统一 waterfall 事件原语（五 mode，类型即合同）；pi=三层槽位-事件-注册面（配置单槽/多播/注册）。影响：跨方移植扩展必须重写注册与分发层。
2. **循环停止哲学**（D1，03 §④）：nop=计数预算+判定器（maxIterations+ISustainer+ICompletionJudge）；dsh/pi=队列数据驱动（inbox/双队列排空即停）。影响：nop 适合不可信自主场景，dsh/pi 适合交互场景；nop 是唯一有硬上限+延长机制的。
3. **真相模型三范式**（D9，02 T15）：dsh=事件溯源（log 即真相，消息是派生投影）；nop=快照+journal（聚合状态即真相，分录做恢复校验）；pi=类型化条目文档（树形，条目判别联合+版本迁移）。影响：恢复/审计/多投影能力的天花板由范式决定。
4. **切换自动化程度谱系**（D5）：nop 内置全自动（账号链→provider 链→tier+熔断）> dsh 表达机制留白（waterfall 改写+payload 决策输入）> pi 手动（model_select+registerProvider）。影响：开箱高可用 nop 独有；定制灵活度反向。
5. **流式持久化梯度**（D1-4/D3，03 ④-8）：dsh 整流记录持久化（attempt/message 内嵌 stream，重放保真）> pi delta 转发不持久化（落盘完整消息）> nop 无流式路径（REASONING_CHUNK 死点）。影响：中断内容保真与流式 UX 的能力差。
6. **缓存策略正交**（D6）：dsh 构造性稳定+辅助调用复用主缓存（字节级前缀重放）；pi 断点显式管理+辅助调用禁写隔离（retention+sessionId）；nop 无主动机制（断点通道空置）。dsh/pi 是两条正交策略路线，非同一策略的优劣。
7. **参数可变谱**（D7，06 ④.1）：dsh 禁改（"参数已落账"不变式）< nop 改后检查（repairer 在安全链前）< pi 改不检查（tool_call 就地 mutate 无再校验）。影响：审批所见与执行所用的一致性保证强度相反。
8. **安全面架构位置**（D7-5）：nop=引擎内（7-checkpoint 每工具评估+沙箱 fail-closed）；dsh=工具管线（pre-execute 审批瀑布+confine 方言签名）；pi=生态（核心零权限层，扩展 veto 承担）。影响：nop 服务端部署开箱合规，pi 需自建全套。
9. **错误传播不对称的轴向**（D4/D2-4）：nop 层内不对称（PRE fail-fast/POST 容错、执行级 PRE/POST try 内外）；pi 层间不对称（配置级抛=run 终止 vs 扩展级抛=包含降级）；dsh 无不对称（统一结构化收口 turn error）。影响：扩展作者的心智模型与测试面。
10. **多代理能力谱**（D10）：nop 编排全栈（fan-out/归约/DAG/调度）×dsh 传输谱系（6 provider+continuation）×pi 零内置（example 进程隔离）。三者正交：编排原语/传输抽象/隔离极端各有独占。
11. **恢复重入位置三分**（D1/D4/D8 交叉，06 ③）：nop=引擎内出口咨询（sustainer）；dsh=step 内 while 重入（重走 buildRequest）；pi=Session 层 run 后 continue（摘消息重生成）。影响：压缩/重试/切换的生效时机与可见性。

## ④ 逐维裁定汇总与关键证据索引

各维代表证据（完整证据链见各维度报告 ④ 表）：

- **D1**：dsh 流式 `agent.ts:386,421-447,474-483`（c291e7961a 结构迁移后）vs nop 死点勘误（03 ④-8）；pi 双队列 `agent.ts:125-159`。nop 治理 `ReActAgentExecutor.java:452-532`。
- **D2**：04 census 与能力矩阵全表；三方扩展注册形态（05 ②）。
- **D3**：dsh 56 类事件+ignorable（`known-event-types.ts:22-79`）；pi AgentEvent 10 变体+AgentSessionEvent（`types.ts:428-443`、`agent-session.ts:143-186`）；nop 21 值封闭枚举（`AgentEventType.java`）。
- **D4**：dsh 重试先持久化（`llm-retry:188-190`）；pi 溢出门闩（`agent-session.ts:2090-2118`）；nop 发散检测（`AgentSessionLifecycle.java:527-567`）。
- **D5**：nop 三级通道（`LlmCallCoordinator.java:276-328`）；dsh/pi 无内置（grep 证据见两报告 ③）。
- **D6**：pi 四旋钮（`anthropic-messages.ts:50-71,1295-1361`）；dsh 构造性稳定（`system-prompt:53-75`）；nop 五项核查（dsh-D6 ②）。
- **D7**：nop ChainRepairer+7-checkpoint（`ChainRepairer.java:42-80`、`AgentSecurityConsultation.java:276-284`）；pi deferred tools+权限留白（`deferred-tools.ts:8-39`）；dsh output.render（`schema.ts:483-545`）。
- **D8**：nop Layer1→2→3（`compact/`）；dsh 溢出环（`compaction-basic:179-223`）；pi 门闩+迭代摘要（`compaction.ts:461-498`）。
- **D9**：dsh 事件溯源（`session/src/index.ts:446`）；pi 迁移链（`session-manager.ts:283-288`）；nop 发散检测+接管锁（`DbSessionTakeoverLock.java:197-261`）。
- **D10**：nop 编排全栈（`team.flow/`）；dsh 6 provider（`subagent/src/index.ts:171`）；pi 零内置（`examples/extensions/subagent/index.ts`）。

## ⑤ 可吸收增量建议汇总（按主题归组，仅建议不实施）

各维度报告 ⑥ 节建议的主题归组（来源报告标注；优先级按 nop 改造成本/收益比排序，非排期）：

**A. 缓存利用（D6，改造成本最低——解析资产已就位）**
- 修两个前缀不稳定源：工具定义显式排序+记忆移出 system 区域（dsh-D6 建议 1 / pi-D6 建议 4）
- ChatUsage cacheHitTokens 消费+归因（dsh-D6 建议 2 / pi-D6 建议 3）
- 辅助调用禁写隔离（pi-D6 建议 2）或前缀复用（dsh-D6 建议 3）——需先裁定策略路线
- AnthropicDialect cache_control 放置位置纠错（dsh-D6 建议 5，前置纠错）

**B. 溢出与重试环（D8/D4 交叉）**
- 溢出→压缩→重试恢复环（dsh-D8 建议 1 / pi 既有机制参照）
- maxContextTokens 随路由切换动态刷新（dsh-D8 建议 2，D5×D8 缺口）
- 溢出独立错误分类（pi-D4 建议 1）+重试前摘除失败响应可选模式（pi-D4 建议 2）
- 重试计划持久化到 journal（dsh-D4 建议 1）

**C. 流式与事件（D1/D3，改造成本最高）**
- 接通流式路径+REASONING_CHUNK 接线+chunk/attempt 持久化评估（dsh-D1 建议 1）
- AgentEvent 类型化 payload+可跳过兼容（dsh-D3 建议 1 + pi-D3 建议 1 组合）
- 订阅者失败上报通道（pi-D2 建议 1）

**D. 注入与 steering（D1-5）**
- steering 持久化+三语义（followup/steer/inject）+队列事件（dsh-D1 建议 2 / pi-D1 建议 1 / pi-D3 建议 2 合并）

**E. 容错与治理（D4，nop 已领先，选择性吸收）**
- 崩溃孤儿工具合成收尾（dsh-D4 建议 2）
- 取消 cause 分型（dsh-D4 建议 4）
- 正则归类器补充（dsh-D4 建议 3）

**F. 扩展面治理（D2）**
- veto 死配置收敛（实现消费或类型收窄）（dsh-D2 建议 1）
- 修复前后差异审计（dsh-D2 建议 3 / dsh-D7 建议 3）
- hook 白名单类型化（pi-D2 建议 2）

**G. 会话存储（D9）**
- 增量 append 写路径+版本化字段（dsh-D9 建议 1 / pi-D9 建议 1-2）
- 压缩产物条目化（pi-D9 建议 2）

**H. 工具与多代理（D7/D10）**
- 并发安全标记+按路径串行队列（dsh-D7 建议 1 / pi-D7 建议 2）
- deferred 工具引入机制（pi-D7 建议 1）
- continuable 子代理形态+传输抽象（dsh-D10 建议 1/2 / pi-D10 建议 1）

**反向记录（供 dsh/pi 生态参考，非 nop 行动项）**：dsh 可参考 nop 账号链/熔断（dsh-D5 建议 1）、team.flow 编排（dsh-D10 建议 3）、ChainRepairer（dsh-D7 建议 4）；pi 可参考 7-checkpoint 安全面（pi-D7 建议 4）、治理 pause/resume（pi-D4 建议 4）。

## ⑥ 总裁定

- **整体裁定：三方各有所长，无全面领先者**（nop 领先 4 / 对方领先 6 / 等价 10）。nop 的差异化价值在"自主治理纵深"（切换/安全/恢复/编排），dsh 在"执行工程成熟度"（流式/事件/缓存构造），pi 在"接口工程精度"（缓存旋钮/类型化/分层扩展）。
- 对 nop 最有价值的三个洞察：①缓存观测是半成品（改造成本最低收益直接）；②溢出处理从硬停升级为恢复环（自主长任务可靠性）；③流式是最大的能力缺口但也是最大的改造面（需独立立项评估）。
- 本报告为 WI28 交付物；交叉一致性校对结论见附录（WI29）。

## Open Questions

- [x] WI29 交叉一致性校对已完成，结论见下方附录（0 结论矛盾、13 处修正、模板 0 缺失）。

## 附录：交叉一致性校对结论（WI29）

> 校对执行：独立子代理 agent_03a1a909-d87b-4b2e-a909-adfdad57d55a（fresh session，2026-09-12）；修正落地：独立子代理 agent_1e74165d-c476-4051-ae6e-dd3c2faf90a1；校对对象=本目录全部 28 个产物（00-06 七份 + 20 份维度报告 + 99 总报告）。

**类 1 结论矛盾：0 处未收敛。** 20 个裁定单元格（维度报告 ⑥ vs 本报告 ②）逐一比对一致；四维交叉主题（循环层级/重试单元/自动化谱系/真相模型）跨报告无矛盾；4 处专项转述抽查 3 处忠实、1 处为锚点冲突（转入类 2）。**修正 1 处簿记错误**：daily log M3 裁定分布计数（"对方领先 4 维/等价 3 维"实为 3/4，M2 tally 同步纠为 1:3:6）。

**类 2 锚点失效：7 项修正 + 2 项基线元数据 + 2 项低优先，全部落地。** ①02 T2 失效指针（runtime-types.ts:261-278）改为 :377-381；②02 T5 失效指针 :119-143 删除（契约已在 :215-241）；③02/dsh-D9 fork 行：seedLength 头字段已在 c291e7961a 移除（index.ts:97-98 显式拒绝），改引 SessionStore.fork（index.ts:1203）+end-seed（types.ts:400）+firstLiveSeq（index.ts:497,584）；④03 §2.2 P5-P6/P7/P9-P10 行号按 c291e7961a 重钉（preStep:240-248/240-259、step 起点组 :302,375,531,541,571-580,597）；⑤dsh-D6 EpochHeader :201-228→:232-261；⑥02/03 头部 dsh 基线元数据 141eb6fef8→c291e7961a（保留祖先注记）；⑦02 T12 五项待核查回写核查结论并勾销 Open Question（WI13 兑现）；⑧02 T1 :442-444→:441-444；⑨"四项"计数 slip→五项。**确认有效**：dsh/pi/nop 三方抽查锚点（dsh 20+、pi 7、nop 3）全部在各自 HEAD 实测命中。

**类 3 模板缺失：0。** 20 份维度报告 6 节结构 120 节全命中；专项 03-06 与 00 矩阵 S1-S4 契约逐条对应；99 总报告登记内容无缺失。

**残留 watch-only（不阻塞收口）**：日志中 WI1/WI2/WI3 小节的 141eb6fef8/"四项待核查"为当日史实记录按日志规范不追溯改写；99 总表若需三方 HEAD 全列（含未被分析方），由后续使用者按 01-code-map 基线表补注。

## References

- `ai-dev/analysis/compare-agent-design/`（00-06 七份基线/专项 + dsh-D1..D10 + pi-D1..D10 共 27 份前序产物）
- `ai-dev/analysis/agent-survey/agentscope-harness-vs-nop-ai-agent-comparison.md`（格式先例）
- `ai-dev/backlog/nop-ai-agent-design-comparison-roadmap.md`（编排）
- `~/ai/deepseek-harness`（c291e7961a）、`~/ai/pi`（c49906ec7）
