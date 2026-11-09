# 2550 CRA Phase 8.56 — R4-16 2.3 WS/网关束 a（G36 前半，12 工单）

> Plan Status: active
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.56；批次定义 = batch-proposal.md §二 :131 R4-16 行；成员发现 = `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`
> Related: plan 2508（R2-05 SSO 异步化+准入池，done）、plan 2509（R2-06 BoundedWindowBuffer，done）、plan 2549（C2-005 双检同型先例）；设计文档 = `ai-dev/design/concurrency/service-ws-gateway-async-chain-a.md`
> **PA**：nop-service-framework 批次表判「无」；**例外**：C3-010 前置原语 CL-025 落 `nop-http-api`（跨模块公共 API）= **plan-first**，授权载体 = 设计文档裁定 E + SPI 回归面。

## Purpose

关闭 R4-16 的 **12 个 P2 工单**：共享可变状态（4：C1-002/004/005/006）、锁内 I/O（2：C2-001←C9-007、C2-003）、异步链终态（3：C3-004 残余/C3-005 残余/C3-009）、WS 错误语义（1：C3-006 残余）、网关取消传播+CL-025 原语（1：C3-010）、Auth 会话刷新失败消费（1：C3-011）。

**基线勘误 E-2550-1**（fresh-session 取证 @ 2026-11-09）：C3-004/C3-005/C3-006/C3-010 为 2508/2509 部分覆盖（残余面见设计 E-2550-1）；其余 8 条原样在位。

## Baseline（live 亲读 @ 2026-11-09）

- `UserContextImpl.roles` 裸 HashSet：addRole :267 synchronized、isUserInRole :322 裸读。
- `GraphQLExecutionContext`：getRequest :127 synchronized / setRequest :132 无锁；dispatchAll :156 synchronized 锁内 dispatch。
- `JsonRpcWebSocketHandler`：validSubscription :403-415 containsKey；sendStreamingMessage :430-435 裸 put；keepAlive :440-448 已绝缘（2509）；onError/session-closed 分支未动。
- `MfaChallenge.verifiedAt` :81 非 volatile。
- `BizObjHelper.loadByProp` :32-42 computeIfAbsent 内 biz invoke。
- `BufferedStreamingPublisher`：2509 重构后 maxAttempts 零命中（重试上限仍委托 retryCallback）。
- `GraphQLSubscriptionPublisher` :52-70 cancel 已转发；context complete 终态待核对。
- `GraphQLServerCallHandler.onCancel` :128-130 空；setOnCancelHandler :250 无人调用。
- `InvokeProcessor`：取消检查零命中；Retry-After 钳制/在途许可池已落（:53/:207-300）。
- `AuthHttpServerFilter` :397 whenComplete 返回值丢弃；flushUserContextAsync thenRun(clearDirty) 无失败分支。
- **基线测试签名**：`./mvnw test -pl nop-service-framework -am` Phase 2 起点实际复跑为准。

### Phase 1 - 设计与授权载体

Status: completed（设计六裁定 A-F + E-2550-1 + CL-025 plan-first 授权；draft review 待审）

- [x] 设计文档（六裁定 + 12 成员唯一形态 + 回归面含 SPI 全实现）
- [x] 成员取证（fresh-session：8 条原样、4 条部分覆盖）
- [ ] 独立 draft review

### Phase 2 - 12 工单实现 + 回归测试

Status: planned
Targets: nop-graphql-core / nop-gateway / nop-biz / nop-auth-core / nop-http-api / nop-service-framework + 测试树

- Item Types: `Fix`（11）、`Fix+Decision`（1：C3-010 含 CL-025 原语 SPI 面）

**实施顺序**
- 组 1（状态原子化）：C1-002 → C1-004 → C1-005 → C1-006
- 组 2（锁内 I/O）：C2-001 → C2-003
- 组 3（异步链）：C3-004 → C3-005 → C3-009
- 组 4（WS 错误）：C3-006
- 组 5（网关取消 + Auth）：CL-025 → C3-010 → C3-011

**工单 × 载体表**（全新建；每建一类后 grep 校验）

| 工单 | 载体（测试类 / 方法 / 断言目标） | 类型 |
|---|---|---|
| C1-002 | `TestUserContextRoles#pin2a`（并发 addRole×isUserInRole 一致视图，无 CME/权限误判） | 行为钉 |
| C1-004 | `TestGraphQLContextRequest#pin4`（setRequest/getRequest 监视器一致——并发读写后读得最新值） | 行为钉 |
| C1-005 | `TestJsonRpcSubscriptionRegister#pin5`（同 id 并发注册不覆盖首订阅——putIfAbsent） | 行为钉 |
| C1-006 | `TestMfaChallengeVisibility#pin6`（verifiedAt volatile——compute 写后 peek 读可见，构造窗断言） | 行为钉 |
| C2-001 | `TestDispatchAllSnapshot#pin1`（dispatchAll 锁外 dispatch：慢 loader 不阻塞并发分支——latch 计时断言；P1-2.6-004 不回退） | 行为钉 |
| C2-003 | `TestBizObjHelperDoubleCheck#pin2`（并发 loadByProp 双检：invoke 计数与结果一致性；bin 锁外构建断言） | 行为钉 |
| C3-004 | `TestStreamResubscribeBound#pin3`（重订阅次数达 maxAttempts 后停止 + 退避间隔生效——注入缝确定断言） | 行为钉 |
| C3-005 | `TestSubscriptionContextComplete#pin7`（onError/onComplete/cancel 三路径 context complete 均触发） | 行为钉 |
| C3-009 | `TestGrpcCancelPropagation#pin9`（onCancel → executeRpcAsync future cancelled——结构钉 + 反射接线断言；模块仅编译，载体落测试树标「模块编译豁免」） | 结构钉 |
| C3-006 | `TestWsErrorSemantics#pin10`（onError 发终止信号帧 + session-closed 取消上游） | 行为钉 |
| C3-010 | `TestClientAbortPropagation#pin11`（CL-025 default 语义 + InvokeProcessor abort 检查点：断连后重试不触发；SPI 实现面编译） | 行为钉 |
| C3-011 | `TestSessionFlushFailure#pin12`（flush 失败 → dirty 不清 + WARN；成功 → clearDirty） | 行为钉 |
| **无静默跳过**（rule 24） | | |

## Exit Criteria

- [ ] 12 工单全部实施
- [ ] 载体表逐条存在且断言目标行为
- [ ] 关键项先红后绿或变异论证（≥3：C2-001 锁外、C2-003 双检、C3-010 abort 检查点）
- [ ] E1 全量编译门禁 BUILD SUCCESS
- [ ] nop-service-framework clean 全量 0F/0E + 触点模块回归
- [ ] **SPI 回归面**：CL-025 全部 http server 实现编译/测试
- [ ] **不回退 2508/2509**：SSO 异步化/准入池/BoundedWindowBuffer 既有钉全绿
- [ ] owner doc 落账：`service-layer.md` + `source-anchors.md`
- [ ] 工具门禁：check-doc-links EXIT=0；scan-hollow `--module nop-service-framework --severity high` EXIT=0
- [ ] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

### C3-010 SPI 断连检测的各 server 深度实现
- Classification: `watch-only residual`
- Why Not Blocking Closure: 本批落 CL-025 default 语义（false/无操作）+ 已有实现的 aborted 检测接线；深度 socket 级检测（半开连接探测）属各 server 设施增强，不阻断「取消传播链路已闭合」。
- Successor Required: `no`。

## Review 记录

（待 draft review）

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）
