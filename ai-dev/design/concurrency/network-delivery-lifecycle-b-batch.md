# WI 4.3 交付与生命周期束 b（R4-27，plan 2561）设计裁定

> Status: active
> Last Reviewed: 2026-11-11
> Source: `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/`（10 原样 + 2 部分覆盖 + 2 已修 fresh-session 取证 @ 2026-11-11）

## 〇、工单状态核正（重复立项防护）

取证判级（14 逐单，R2-M1R 勘正）：**已修×2**（C1-018=plan 2523 c321d40407[确认钉+消号]；C6-012=plan 2498 98cdde0e0e[注记消号]）+ **部分覆盖×2**（C6-010 余量=client 登记表+destroy 逆序关闭[2523 世代令牌面已闭]；C3-033 余量=终态隔离三路径[2505 demand-timeout 面已闭]）+ **原样×10**（C1-014/C1-016/C1-019/C3-009/C3-023/C3-025/C3-026/C6-003/C6-013/C6-027）。批外注记：MA-12 已由 plan 2499 修复、2560 相邻批验证型回归钉承载——**不在 2561 判级名册**（束 a 成果）。改码 Fix = 9 原样 + 2 部分余量 = **11**；声明面 1（C6-013 javadoc）；消号 1（C6-012）+确认钉 1（C1-018）。C6-003 修复**同步消 3.9 前案台账行**（并轨）。C6-013 与 C6-001(P0)/C3-005(P1) 的下载链同批约束：本批取**javadoc 残留语义声明**形态（不引入临时名重构——避让后续 P0/P1 修复），typed 声明登记。

## 一、设计结论（十四条裁定 A-N）

1. **裁定 A（C1-014 SSL 惰性缓存）**：`NettySslEngineFactory` client/server 双路径 check-then-set → synchronized 惰性块 + `SslConfig` 两字段 volatile。
2. **裁定 B（C1-016 headerBuf 读守卫；R1-M2 钉死）**：`SocketClient` headerBuf（:47 实例共享）→ **AtomicBoolean readerActive 守卫（call 与 recv-loop 双入口互斥——降局部方案不提供互斥，否决**；BinaryCommand.readPacketFromStream 中 headerBuf 实为单次 scratch，降局部行为等价但 call（调用线程）与 recv-loop（executor 线程）同 InputStream 交错读帧缺陷本体依旧）+「单读线程」契约 javadoc 声明（SocketClient 类级）。
3. **裁定 C（C1-018 确认性钉）**：不碰码——补 `TestMqttConnection` 注册序/幂等 complete 确认断言已有（2523 落地）+ `nop-network.md`/cra-index 映射注记（2561 消号）。
4. **裁定 D（C1-019 accept 世代）**：`SocketServer` doStart 新标志对象（AtomicLong acceptGeneration）或等价——run 循环退出判定不再复用共享单比特 `stopped`；restart 场景旧 accept 循环不误杀新循环。测试缝 TestSocketServerRestart（ThreadHelperSleep 注入）。
5. **裁定 E（C3-009 onFailure 对齐）**：`SimpleRpcClient`（:140-145）send 失败 catch 改调 `channelState.onFailure(id, e)`（对齐 MessageRpcClient 先例；基于 2560 现行 prepareSend failed-future 包络语义——raw future 仍在 waitFutures，onFailure 完成之）。
6. **裁定 F（C3-023 codec fail-loud；R1-M4 形态勘正）**：encode 侧——**删除 resetWriterIndex+WARN 吞噬分支**（encode 路径无 size 上限判定可寻——maxFrameLength 仅 decode 强制），encode 异常直接上抛（MessageToByteEncoder 据此 fail write promise；不自动关 channel，由调用方/exceptionCaught 观察）；decode 侧**现状已具备 WARN+计数**（:100-109）——真实增量 = 结构化丢帧计数器暴露（长计数器 getter），zero-diff 部分显式确认。PacketCodecHandler 本体零测试→本批建测试类。
7. **裁定 G（C3-025 email 类型化；R1-B1 模块勘正）**：`TencentEmailSender`（**nop-integration-email-tencent** 叶，非 nop-network；catch 实锚 :190-199）未知错误分支 → `throw NopException.adapt(e)`（对齐 JavaEmailSender/TencentSmsSender 重抛范式；仅保留 EmailAddressIsNULL 静默 return 与 FrequencyLimit warn+return 两个显式分支）；实施时顺手确认 MFA 调用点（MfaCodeSender:197/UserMfaSelfService:1422）对上抛的容错。
8. **裁定 H（C3-026 重连终态处理器）**：`SocketClient` retryExecute 链（:218-227）加终态 exceptionally 处理器（LOG+onChannelClose+closed 置位）；send os==null 静默丢弃面 typed 化；doRecv handler.accept 异常与 IO 异常分流。
9. **裁定 I（C3-033 SSE 终态隔离余量）**：①onComplete（:84-91）cleanup ②dispatchEvent（:139）completed 守卫（防 completed 后继续派发）③`[DONE]` 取消底层请求 ④jdk `ServerEventPublisher`（:100-105）CancellationException 分流（取消不再派假 onError）。**勿回退 2505 ERR_HTTP_SSE_DEMAND_TIMEOUT 行为**。
10. **裁定 J（C6-003 Range 头移除+3.9 并轨消号）**：`JdkHttpClient`（:391-393）/`OkHttpClientImpl`（:169-171）offset=0 时 removeHeader range（对齐 Apache :256-262 正确对照）；jdk/okhttp TestFileTransfer 各补 416 恢复用例（模板=Apache :264）；**同步消 3.9 前案台账行**。
11. **裁定 K（C6-010 余量 client 登记表；R1-M5 收口细节）**：`SimpleRpcClientFactory` client 登记表——**登记点 = newInstance()（:178-189 newClient/connect/executeRecv 处）；登记物 = SimpleRpcClient（AutoCloseable）**；destroy 逆序关闭兜底；**幂等策略**：SocketClient.close() 重入会再触发 onChannelClose（cleanup 无 closed 守卫）——destroy 关闭路径容忍重复回调（计数断言按「至少一次」形态），不做 closed 幂等守卫（超范围）。测试缝 TestSimpleRpcClientFactoryIdempotentInit_C1_001。
12. **裁定 L（C6-012 映射注记消号）**：不碰码——cra-index/`nop-network.md` 消号（2498 已落地 deadline watchdog；2560 MA-15 防御面零冲突）。
13. **裁定 M（C6-013 下载残留语义声明；载体落点=nop-http-api/.../FileDownloadSink.java）**：`FileDownloadSink` 直写路径零回滚面——本批取 **javadoc/契约声明形态**（残留语义显式声明，临时名重构避让后续 C6-001(P0)/C3-005(P1) 下载链修复批）；typed 声明登记。
14. **裁定 N（C6-027 connect 回调守卫）**：`SocketClient.connect`（:121-139）回调段+heartbeat 段 try/catch → safeCloseObject+cleanup 后重抛（异常后重试入口不再锁死）。

## 二、载体表

| 工单 | 载体 | 模块叶 |
|---|---|---|
| C1-014 | `TestNettySslEngineFactory`（新：并发首装单实例+volatile 结构钉） | nop-netty |
| C1-016 | `TestSocketClientReadGuard`（call/recv 并发互斥） | nop-socket |
| C1-018 | 确认性钉（TestMqttConnection 既有断言核验+映射注记） | nop-vertx-mqtt-server |
| C1-019 | `TestSocketServerRestart` 扩展（旧 accept 循环不误杀新循环） | nop-socket |
| C3-009 | `TestSimpleRpcClientCallbacks` 扩展（send 失败→onFailure 完成 waitFutures future） | nop-rpc-simple |
| C3-023 | `TestPacketCodecHandler`（新：encode 超限 promise fail + decode 丢帧 WARN 计数） | nop-netty |
| C3-025 | `TestTencentEmailSenderCredential` 扩展（未知错误 typed 上抛——该模块现无 TestTencentEmailSender 文件） | nop-integration-email-tencent |
| C3-026 | `TestSocketClientRetryTerminal`（终态处理器：LOG+closed 置位+onChannelClose） | nop-socket |
| C3-033 | `TestSseTerminalIsolation`（onComplete cleanup/completed 守卫/[DONE] 取消/CancellationException 分流） | nop-http-client-jdk（+端到端链） |
| C6-003 | `TestFileTransfer` 扩展（jdk/okhttp 各 1 条 416 恢复用例） | nop-http-client-jdk + okhttp 叶 |
| C6-010 | `TestSimpleRpcClientFactoryDestroy`（client 登记表+destroy 逆序关闭） | nop-rpc-simple |
| C6-012 | 映射注记消号（不碰码） | — |
| C6-013 | javadoc 残留语义声明（结构钉） | nop-http 相关叶 |
| C6-027 | `TestSocketClientConnectGuard`（回调段异常→cleanup+重抛+重试入口可用） | nop-socket |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-network -am`（六叶；rpc-core TestDefaultFlowControlRunner 4E=pre-existing HEAD-stash 归因[2560 已登记]）。
- 硬接缝：① SocketClient 四方同文件（C1-016/C3-026/C6-027 × 2560 C1-010 volatile 回调）单通道串行；② C3-009 基于 2560 现行 prepareSend 语义；③ C3-033 勿回退 2505 demand-timeout 行为；④ C1-018/C6-012 只落注记消号禁重复改码。
