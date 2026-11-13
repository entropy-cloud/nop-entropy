# C7 深挖第 2 轮 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C7 维度深挖第 2 轮盲审员（盲审纪律同第 1 轮；编号续接 C7-005 起）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118 + L266–287（C7 正文）+ L351–377（附录 A）

## 深挖范围与方法

按派单深挖面逐一执行，跨模块证据面全文核对 `ThresholdBreaker.java`（nop-ai-core，331L——本模块熔断消费的原语语义）：

1. **熔断状态机长程卡死（HALF_OPEN 悬置已留痕——找未登记面）**：以 ThresholdBreaker 实际语义逐条核对编排消费方式，发现 C7-005（选择路径绕过 allowCall 的两个未登记后果）。第 1 轮已留痕面复核：HALF_OPEN 探活挂死有 probeTimeoutMs（30s）逃逸（ThresholdBreaker L200-211），悬置不永久——第 1 轮"熔断器状态机本体越界"留痕维持；**多候选探活多槽位发放**（probeBrokenCandidates 对每个冷却已满的 OPEN 候选各发一个 probe 槽）复核为可自愈——未选中的 HALF_OPEN 候选在健康视图中非 OPEN、仍可被后续请求选中，真实调用落地后经 recordSuccess/recordFailure 收敛，另有 probeTimeout 逃逸兜底，**负验证**。
2. **候选健康表长期演化**：ThresholdBreaker entries 收缩（pristine CLOSED 条目即时移除，L172-180/L247/311-313）+ ConcurrencyRegistry 归零收缩复核——两表均无单调增长；`FailoverMetricsImpl` 标签基数依赖 model 取值——**定向验证 model 的来源闭环**：metrics 仅在接管路径（`hasRoutingGroup()==true`，即 model 命中已配置模型类）后触发，model 值 = 候选解析产物 = 配置界，**无用户可控标签基数爆炸，负验证**（第 1 轮"配置界"结论以代码链证实）。
3. **登录过期后的重试行为**：AUTH_INVALID → switchable → 即时切换 ×retryBudget → fail-loud。无退避为第 1 轮已核的声明语义；"同一模型类内多账号失败记账跨账号累计"为 ModelClassRouter javadoc L39-43 明文声明（recordFailure 按 modelKey=provider:model 共享）——单账号密钥过期可使共享 breaker OPEN、门控全部账号，属**已声明语义的固有代价**，负验证留痕不立案。
4. **长会话累积行为面**：`NopAiChannelSession` 表 F4 面 = 第 1 轮 C6-004 + 单调增长清单第 7 项已登记；引擎侧会话存储在 nop-ai-agent（越界）。`updateLastActive` 高频写（每条命中消息一次 DB 写）为热路径成本非累积，归 C9 族，负验证。
5. 补充核对：重试收敛第 1 轮"无发现"结论复核维持（三处饱和重选递归均受 attempted 集合约束）；挂死点清单第 2/3/6 项留痕维持。

## 新增发现

### [C7-005] 选择路径健康视图系统性绕过 allowCall：OPEN 且冷却已期满的候选在非全池饱和期永久出局（容量单调流失不可自愈）+ HALF_OPEN 候选被健康视图视为完全可用（单探针恢复契约在编排消费面失真）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/DefaultSelectionStrategy.java:36-48`（isAvailable 门控）；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/CandidateHealth.java:49-51`（isCircuitOpen = state==OPEN）；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java:403-450`（探活唯一触发点）；原语语义证据 `nop-ai/nop-ai-core/.../ThresholdBreaker.java:165-219`（状态迁移仅在 allowCall 内惰性发生）
- **证据片段**:
  ```java
  // DefaultSelectionStrategy——唯一门控是快照状态，从不调 allowCall：
  for (ModelClassCandidate candidate : candidates) {
      if (attempted != null && attempted.contains(candidate)) { continue; }
      if (!health.healthOf(candidate).isAvailable()) { continue; }   // L40 —— OPEN 即跳过
      return candidate;
  }
  // CandidateHealth.isCircuitOpen——读的是惰性状态字段：
  public boolean isCircuitOpen() { return circuitState == CircuitState.OPEN; }   // L50
  // ThresholdBreaker——OPEN→HALF_OPEN 只在 allowCall 内发生（无后台 timer）：
  case OPEN:
      if (now - entry.openedAt >= cooldownMs) {                       // L185 —— 冷却期满，但……
          entry.state = CircuitState.HALF_OPEN; ...                   //      只有 allowCall 的调用者能看到
  // allowCall 全仓调用点（grep 举证）：仅两个 probeBrokenCandidates（全池饱和探活路径）
  ```
- **严重程度**: P2
- **并发场景**: 长时运行多候选部署；触发无需并发竞态——状态演化 + 选择门控的组合即可。
- **触发时序**: T1 候选 A（模型类内第二候选）连续 3 次可切换失败 → breaker(modelKey) OPEN，冷却 60s 启动 → T2 冷却期满后任意时刻：请求到达且模型类内第一候选健康可用 → `DefaultSelectionStrategy` 对 A `healthOf(A).isCircuitOpen()==true`（状态仍是 OPEN——惰性迁移只发生在 allowCall，而 allowCall 只在**全池饱和**探活时被调）→ A 被跳过 → 请求走第一候选成功 → 无饱和事件 → **探活永不触发** → A 永久出局。交叠后果①：每发生一次瞬时抖动（3 连败即 OPEN），候选池容量单调流失，数周运行后有效池 = 从未抖过的候选；后果②：反转面——某候选进入 HALF_OPEN 后（饱和探活放行过一次），`isCircuitOpen()` 为 false → 健康视图视为完全可用 → 选择路径对恢复期候选**无并发限制**（ThresholdBreaker javadoc L31-36 声明的"exactly one probe call is admitted"单探针契约在真实调用面不被执行，任意多请求并发打到恢复中候选）。
- **现状**: 探活触发条件（"全池饱和时对 OPEN 候选 allowCall 探活"，javadoc L63/L400 与 owner doc）是**声明设计**——本条立案面是该设计的两个未声明后果：①健康视图无法感知冷却期满（状态惰性），使恢复依赖"其他候选恰好同时全部饱和"这一小概率事件，声明中"冷却期满 → HALF_OPEN 放行"的恢复路径在常态下不可达；②HALF_OPEN 的可用性语义在 CandidateHealth 上与 CLOSED 无差别，单探针契约仅存在于原语内、编排消费面未对齐。
- **风险**: 多账号/多 provider 模型类的容量在长时运行中单向缩水（fail-loud 饱和误报前置化：有效池缩小使全池饱和更易发生——而饱和恰是唯一恢复触发器，形成"越缩越容易触发恢复"的病态耦合）；恢复期候选被并发击穿使探活结论（recordSuccess/recordFailure）被噪声主导，熔断恢复判据失真。
- **建议**: 选择门控补 allowCall 维度——`DefaultSelectionStrategy`/健康投影在 `isCircuitOpen()==true` 时追加 `breaker.allowCall(modelKey)` 冷却复查（期满即经原语惰性迁移 HALF_OPEN，恢复常态可达；原语已内建 probeTimeout 逃逸与并发探活互斥，直接复用即可，不新建判定——对齐 CandidateHealthProvider"只做读取投影"的复用优先不变式需同步调整为一处 allowCall 代理）；HALF_OPEN 候选若维持单探针语义则在健康视图标注 `probeInFlight` 并令选择期跳过（或显式声明恢复期放开并发的取舍）。
- **失效面枚举（F3）**: 健康视图/门控消费点全量清单——①`DefaultSelectionStrategy.select` L40（**缺陷面**）；②`RuleBasedSelectionStrategy.buildInputs` L201-211（health 快照喂规则——同受惰性 OPEN 污染，且规则侧同样不调 allowCall，**同病未修**）；③`FailoverProbeSupport.isConcurrencySaturated`/`ChatServiceFailoverAdapter.isConcurrencySaturated`（纯并发计数读取，无熔断面，豁免）；④两处 `probeBrokenCandidates`（唯一 allowCall 消费点，本条的修复落点）；⑤`CandidateHealth.isAvailable`（L63-64，使能面，与①②同批修）。
- **信心水平**: 确定（状态惰性、allowCall 调用点唯一性、选择门控逻辑均为代码静态可证；"容量单调流失"在多候选 + 候选间流量不均的常态负载下必然逐步发生）
- **误报排除**: 非"已声明边界"——声明的探活**触发条件**不等于声明其常态不可达的恢复后果与 HALF_OPEN 并发语义；非 C3-005 重复（attempted 集合污染是单请求内不可回收，本条是跨请求长程容量流失）；非原语内部缺陷（ThresholdBreaker 自身语义自洽，立案面是本模块的消费方式——选择门控不消费 allowCall）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。四子项逐一复核：(1) 原语语义——OPEN→HALF_OPEN 惰性迁移仅发生在 allowCall 内（ThresholdBreaker L166-218 compute 内 switch，无后台 timer）实证；(2) 选择门控——DefaultSelectionStrategy L36-48 仅读 healthOf().isAvailable()，CandidateHealth.isCircuitOpen() = state==OPEN 快照（L50），CandidateHealthProvider.healthOf 读 breaker.getState() 无 allowCall（L48-54）实证；(3) allowCall 消费点唯一性——nop-ai-gateway 内仅 CircuitObservation.allowCall 经两处 probeBrokenCandidates（adapter L444 / FailoverProbeSupport L78）grep 证实；反证面：nop-ai-agent LlmCallCoordinator L240/890/912 在引擎侧正确消费 allowCall——gateway 选择面消费失真为模块特定缺陷，立案面锁定编排消费方式而非原语（初审定性准确）；(4) F3 兄弟——RuleBasedSelectionStrategy.buildInputs L201-211 同读快照无 allowCall，同病并入修复面。后果①（冷却期满候选常态不可达恢复 → 容量单调流失 + "越缩越易触发恢复"病态耦合）与后果②（HALF_OPEN 视同完全可用 → 单探针契约在真实调用面不执行）均结构成立；非已声明边界定性准确（声明的是探活触发条件，未声明常态不可达后果）。live 面（failover 请求主链）维持 P2；与 C3-005（单请求 attempted 面）非同案维持

## 维度集外线索（overlay）

### [nop-ai-gateway-CL-022] FailoverMetricsImpl.maskAccount 将备用账号 apiKey 的前 4 字符 + 总长度写入 micrometer 标签（C9 观测面 / 安全面相邻）

`FailoverMetricsImpl.maskAccount`（failover/FailoverMetricsImpl.java:131-137）：`accountKey.substring(0, 4) + "***(" + length + ")"` 作为 `account` 标签随全部 12 个指标导出到观测面。对照同仓掩码惯例：`ConcurrencyRegistry.maskAccountKey`（routing/ConcurrencyRegistry.java:125-127）对异常消息面采用**全掩码**（`***`/`null`），`ModelClassCandidate.toString` 同为全掩码——本实现是全仓唯一保留明文前缀的掩码姿态；短 apiKey（≤4 字符）退化为全掩码，但 ≥5 字符的 apiKey 泄露前缀 + 精确长度（长度本身缩小爆破空间）。javadoc L126-130 自述"掩码保留可区分性"为有意取舍，但与密钥处理惯例的一致性未对照。归 C9/安全维度评估：建议统一为 `***` + 长度分桶（如 `len<32`），或复用 ConcurrencyRegistry 姿态。

—— 本维度无其他集外线索。

深挖面负验证留痕（不立案）：①HALF_OPEN 悬置有 30s probeTimeout 逃逸，第 1 轮留痕维持；②多槽位探活可自愈（HALF_OPEN 候选仍可被选中，真实调用收敛 + 逃逸兜底）；③metrics 标签 model 值经路由门控闭环为配置界；④登录过期重试即时切换 + 跨账号记账累计均为已声明语义；⑤NopAiChannelSession F4 面维持第 1 轮登记。

## 继续判定

新增 1 条（P2×1，无 P0/P1）。判定：**终止信号**——①第 1 轮 4×P2 的站点（终态回调挂死/双墙钟/限流窗/fan-out 队列）第 2 轮定向复核未再扩展，唯一新发现虽为长程性但失效面封闭（健康视图消费方式，修复落点集中）；②四个定向深挖面（熔断卡死/健康表演化/登录过期/长会话）均以立案或负验证闭合；③单调增长源清单与挂死点清单复核后无新增条目。建议 C7 维度不再派第 3 轮，转入 Phase 4 复核合并。

---
*审计员：C7 深挖第 2 轮盲审。发现计数：P0=0，P1=0，P2=1，P3=0；CL 追加=1（CL-022）。*

## 复核结论（WI 3.3 独立复核，合并批 C6+C7）

- **C7-005**：保留 P2——四子项复核全部回码证实（OPEN→HALF_OPEN 惰性迁移仅在 allowCall；DefaultSelectionStrategy/CandidateHealthProvider 均只读 getState 快照；模块内 allowCall 消费点仅两处 probeBrokenCandidates，nop-ai-agent LlmCallCoordinator 反证引擎侧消费正确；RuleBasedSelectionStrategy 同病并入修复面）。后果①容量单调流失与后果②HALF_OPEN 无单探针约束均结构成立；非已声明边界定性准确。live 面 P2 维持。逐条状态行见上方条目；维度判定表与最终保留项见 C7-long-running.md「维度复核结论」。
- 第 2 轮终止信号判定维持（四个定向深挖面以立案或负验证闭合，与本复核回码结论一致）。
- 深挖面负验证抽验：③"跨账号记账累计为已声明语义"维持——ModelClassRouter javadoc 明文声明"同一模型类内多账号连续失败记账跨账号累计由编排实现"（Phase 4 裁定节）；②多槽位探活可自愈维持（probeTimeout 逃逸 + HALF_OPEN 候选仍可被选中）。
- C7 维度合计判定：保留×4（C7-002/005 P2 独立；C7-001 P2 并入 C3-004；C7-004 P2 条件化并入 C4-002）、降级×1（C7-003 P2→P3 条件化）、驳回×0。
