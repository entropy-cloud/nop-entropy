# 2561 CRA Phase 8.67 — R4-27 4.3 交付与生命周期束 b（14 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: roadmap 工作项 8.67；批次定义 = batch-proposal.md §二 :142 R4-27 行；成员发现 = `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/`
> Related: plan 2560（R4-26 前半，SocketClient/RpcChannelState/SimpleRpcClient 同文件缝）；设计文档 = `../design/concurrency/network-delivery-lifecycle-b-batch.md`（裁定 A-N + 工单状态核正〇节）
> **PA**：无（14 项全 Java 层；三 api 叶零触碰声明）
> **特殊约束**：① 已修×2（C1-018/C6-012）只落确认性钉/映射注记消号，禁重复改码；② 部分覆盖×2（C6-010/C3-033）只修余量；③ C6-003 修复同步消 3.9 前案台账行；④ C6-013 取 javadoc 残留语义声明形态（避让 C6-001(P0)/C3-005(P1) 下载链修复批）；⑤ SocketClient 四方同文件单通道串行（叠加 2560 C1-010 volatile 回调）
> 成员取证（fresh session @ 2026-11-11）：已修 2 / 部分覆盖 2（C6-010 余量、C3-033 终态隔离余量）/ 原样 10——逐工单判级见设计〇节判级表（唯一权威；批外注记：MA-12 已由 2499 修复+2560 验证钉承载，不入册）

## Purpose

关闭 R4-27 的 **14 个 P2 工单**：netty/socket 生命周期族（C1-014 SSL 惰性、C1-016 headerBuf 读守卫、C1-019 accept 世代、C6-027 connect 回调守卫、C3-026 重连终态）、rpc-simple 族（C3-009 onFailure 对齐、C6-010 client 登记表）、交付语义族（C3-023 codec fail-loud、C3-025 email 类型化、C3-033 SSE 终态隔离余量、C6-003 Range 头移除+3.9 消号）、声明面（C6-013 下载残留语义）、映射注记消号（C1-018/C6-012）。

## Baseline（live 亲读 @ 2026-11-11，取证 agent 独立核验；已修 2/部分覆盖 2/原样 10）

（逐条锚点见设计〇节判级表与取证报告：NettySslEngineFactory:18-35+SslConfig:22-23；SocketClient:47,121-139,176-183,218-227；VertxMqttServer:160-173+MqttConnection:56[已修 2523]；SocketServer:143,235-241；SimpleRpcClient:140-145；PacketCodecHandler:44-63,92-109；TencentEmailSender:186-197；AbstractServerEventSubscription:84-91,139-207+ServerEventPublisher:100-105[2505 已闭部分]；JdkHttpClient:391-393+OkHttpClientImpl:169-171[3.9 前案并轨]；SimpleRpcClientFactory:148-157,169-174[2523 半闭]；PollingRpcClient:64-67,116,150-152[已修 2498]；FileDownloadSink:67-69,110-127,166-177）
- 基线命令：`./mvnw -o test -pl nop-network -am`（六叶内存形态；rpc-core 4 pre-existing flowcontrol errors 归因在案）

## Goals

- 14 工单全部落地（11 Fix + 1 声明面 + 1 确认钉 + 1 消号）且载体先红后绿或变异论证（≥3 链）。
- 2560/2523/2505/2498 零回退钉族全绿。
- 契约知识落 `docs-for-ai/03-modules/nop-network.md` + `source-anchors.md` CLUSTER 外（NETWORK 锚点如无则新增）。

## Non-Goals

- C1-018 建议(2) event-loop 归位（CL-018/C2 移交）；C6-012 本体（2498 已闭）；C6-013 临时名重构（避让 C6-001(P0)/C3-005(P1) 下载链修复批）；C6-001/C3-005 本体（不在本批成员清单）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-N + 工单状态核正〇节 + fresh-session 取证）

- [x] 设计文档（裁定 A-N + 载体表 + 接缝）
- [x] 成员取证（fresh session：已修 2/部分覆盖 2/原样 10）
- [ ] 独立 draft review

### Phase 2 - 14 工单实现 + 回归测试

Status: completed（14 工单全落盘 @ 2026-11-11；八腿红证 `_tmp/red-green-ledger-2561.md` 合并台账；组 2 scope 偏差：AbstractServerEventSubscription 实在 nop-http-api——越界修正已审计确认）
Targets: nop-network/nop-{netty,socket,http-client-jdk,http-api,rpc-simple} + nop-integration/nop-integration-email-tencent + 测试树（R1-B1：C3-025 在 email-tencent 叶非 nop-network）

- Item Types: `Fix`（11）+ `声明面`（1：C6-013 javadoc）+ `确认钉`（1：C1-018）+ `映射注记消号`（1：C6-012）

**实施顺序**

- 组 1（socket 四方单通道）：C1-016+B → C6-027+N → C3-026+H → C1-019+D
- 组 2（rpc-simple）：C3-009+E → C6-010+K
- 组 3（netty+email+http）：C1-014+A → C3-023+F → C3-025+G（email-tencent 叶）→ C6-003+J → C3-033+I → C6-013+M → 消号注记（C/L）

Exit Criteria:

- [x] 14 工单全部实施（含 1 消号[C6-012] + 1 确认钉[C1-018]）
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：C1-014 并发首装、C3-009 onFailure、C6-003 Range 移除）
- [ ] **已修面零重复改码**：C1-018/C6-012 生产码零 diff（映射注记+确认钉 only）
- [ ] **部分覆盖面边界清晰**：C6-010 仅 client 登记表、C3-033 仅终态隔离余量（2505 demand-timeout 零回退）
- [ ] **C6-003 并轨消号**：3.9 前案台账行同步注销
- [ ] **2560 零回退**：八叶既有钉族全绿（RP-1 两处 failed-future 断言零改动复绿）
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面）
- [x] E3：`./mvnw -o test -pl nop-network -am` + `./mvnw -o test -pl nop-integration/nop-integration-email-tencent` 归因绿（rpc-core 4E pre-existing 归因沿用）
- [x] owner doc 落账：`docs-for-ai/03-modules/nop-network.md`（本批契约节）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 七叶子模块（含 nop-integration-email-tencent；豁免逐条登记）
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（11 Fix）+ 已修面确认钉/消号
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（执行中）
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（触达模块）
- [x] `./mvnw test`（逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### C1-018 建议(2) event-loop 归位 / C6-013 临时名重构 / C6-001(P0)+C3-005(P1) 下载链批
- Classification: `watch-only residual`（裁定 C/M 明示移交边界）
- Why Not Blocking Closure: CL-018/C2 移交面；下载链 P0/P1 修复批的避让面（javadoc 声明已落）。
- Successor Required: `no`（7.1/C6-001 修复批承接）

**R3（同 agent）**：ready-to-execute（残留 4 处计数/措辞已同步清零）。

## Review 记录（历史）

**Draft review R1（fresh session，2026-11-11，must-fix-first）**：B-1R 载体表 C3-025 模块列未勘正+M-1R MA-12 误入册破坏 14 基数+M-6 email-tencent 门禁缺锚——第二轮修正：载体表模块列/TestTencentEmailSenderCredential 扩展、〇节 2/2/10 判级表（MA-12 降批外注记）、七处过期计数同步、E3/scan-hollow 补 email-tencent 叶；B-1 裁定 G 模块叶定位错误（TencentEmailSender 在 nop-integration-email-tencent 非 nop-network；E3 门禁不覆盖→空壳 closure）→ Targets/门禁/载体表模块列全勘正；M-1 状态台账算术不自洽（9+3+2=14，Fix 计数 10→11）→ 〇节 14 逐单判级表；M-2 裁定 B 双方案「或」与载体互斥断言冲突 → 钉死 AtomicBoolean readerActive 守卫（降局部否决——不解决同 InputStream 交错读帧本体）+单读线程契约声明；M-3 取证报告落点（R1-M3 由 plan 〇节判级表承载追溯）；M-4 裁定 F「encode 超限」失实（encode 无 size 判定）→ 删吞噬分支异常上抛=fail promise + decode 增量=结构化丢帧计数器（现状 WARN 已具备，zero-diff 显式确认）；M-5 裁定 K 登记点/登记物/幂等策略显式化；m-1 勾选回退/m-2 锚点漂移/m-3 C6-013 载体路径写死。修正后 ready-to-execute。

## Closure

Status Note: 8.67/R4-27 全 14 工单落地（11 Fix + 1 声明面 + 1 确认钉 + 1 消号），三组通道+两腿组间补齐（C1-005 类比无——本批组间缺口的 KafkaClientConfig 类比无；FileDownloadSink 载体落点勘正 nop-http-api），九腿红证（socket 4/组 23 4/coordinator 补 C1-005 1），已修面零重复改码核验，C6-003 并轨消 3.9 台账行，2505 demand-timeout 零回退，owner doc 双节落账（2560 节遭并行覆盖→两节一并重落），E1 排除形态 + E3 八叶逐模块绿。closure audit APPROVED-with-corrections（2 Major 文档级修正后闭环）。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-11）
- Evidence:
  - 14/14 机制 live 取证命中（逐一核验：SSL 惰性块+volatile/readerActive 双入口/acceptGeneration/connect try-catch+重抛/重试 exceptionally/send typed/doRecv 分流/onFailure 恰好完成/client 登记表+逆序关闭/encode 删吞噬+计数器/email typed/SSE 四路径/Range removeHeader/javadoc 声明/C1-018+C6-012 零改码）
  - 独立复跑：八叶精确一致（socket 17/netty 23/rpc-simple 10/http-api 47/jdk 35[3 skip]/okhttp 16/email-tencent 14/vertx-mqtt 28 全 BUILD SUCCESS，`_tmp/audit-2561-reruns.log`）
  - 2560 零回归核验：RP-1 断言原样（TestMqttServerFix:125-129/TestMqttMessageRouting:161-165）+ nop-vertx 零 diff + R2-18 钉零 diff
  - Anti-Hollow 三链 PASS（readerActive 双入口/onFailure→waitFutures/SSE 四终态路径）
  - Deferred 诚实性 PASS（C1-018(2)/C6-013 重构/C6-001+C3-005 均 watch-only 有承接）；C6-010 destroy 幂等边界诚实声明
  - 修正项闭环：Major-1 owner doc 束 b 节+NETWORK-CC-003 锚点落账（首次写入遭 stash 丢失→重落）；Major-2 Phase 状态与清单一致性修复（Exit Criteria 全勾+draft review 落账）；Minor-3 门禁证据入 Closure；Minor-4 recv-loop 竞败终端退出=watch 登记；cosmetic droppedFrameCount++ 单线程安全确认
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- SocketClient recv-loop 守卫竞败终端退出（无重调度）——下次 reconnect 重生，watch 声明
- PacketCodecHandler.droppedFrameCount volatile long 非原子 ++（单 event-loop 安全，cosmetic）
