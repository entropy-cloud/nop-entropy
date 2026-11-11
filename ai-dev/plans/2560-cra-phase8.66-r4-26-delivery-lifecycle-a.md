# 2560 CRA Phase 8.66 — R4-26 4.3 交付与生命周期束 a（14 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: roadmap 工作项 8.66；批次定义 = batch-proposal.md §二 :141 R4-26 行；成员发现 = `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/`
> Related: plan 2499（MA-12 已修）、plan 2522/2523（R2-19/R2-20 兄弟批零缺陷面重叠）；设计文档 = `../design/concurrency/network-delivery-lifecycle-a-batch.md`（裁定 A-N）
> **PA**：无（14 项全 Java 层；三 api 叶零触碰声明）
> **特殊约束**：① MA-15 携 RP-1 断言级反向钉死 2 处先改测试（4.2 先例）；② 六处同文件接缝单 diff 或顺序约束（裁定 N）；③ MA-14+C6-028 同提交收敛 + VertxMqttServer.setVertx 注入缝 Phase 0；④ 先行失败测试基数核减 16→13（3 处过期登记）；⑤ vertx-mqtt 休眠面注记（修复照常立项，不要求生产激活证明）
> 成员取证（fresh session @ 2026-11-11）：13 原样 + MA-12 已修（验证型）

## Purpose

关闭 R4-26 的 **14 个 P2 工单**：token 注入族（MA-08 锁内 IO+四入口覆盖）、注册配对族（MA-09 CAS、MA-10 stop 世代、MA-11 ticket 原子消费）、monitor/通道族（MA-13 RMW、C1-003 容量原子化、MA-15 同步抛统一包络）、生命周期族（MA-14+C6-028 VertxMqttServer 启停、MA-16+C6-025 stop close 收敛、C1-005 回调槽位、C1-010/C1-011 feishu 停机快照+重启）、验证型（MA-12 回归钉）。

## Baseline（live 亲读 @ 2026-11-11，取证 agent 独立核验；13 原样 + MA-12 已修）

（逐条锚点见取证报告：AddAccessTokenHttpClientEnhancer:55,91-96,115-123；MqttServerMessageService:93-113；NettyTcpClient:63,206,214,231；FeishuBindProvider:172-192；ApiContextInterceptor:27-39[已修 44e633da72]；RpcTaskMonitor:41,92,115-117；VertxMqttServer:41,67-75,179-192；RpcChannelState:80-105；MqttConnection:233-239；NettyTcpServer:287-290；JdkHttpClient:83,155-161 + JdkStreamTransport:46,198-209 + JdkFeishuHttpApi:28 + FeishuClient:104-111,204-226,229-253；SimpleRpcClient:45-46,50-64；RP-1：TestMqttMessageRouting:159-160 + TestMqttServerFix:122-126）
- 基线命令：`./mvnw -o test -pl nop-network -am`（六叶内存形态）

## Goals

- 14 工单全部落地（13 Fix + 1 验证型）且载体先红后绿或变异论证（≥3 链）。
- RP-1 两处先改测试；先行失败测试按双口径验收（单元台账 13 记账 + 本批 in-scope 枚举清单为验收物）。
- R2-19/R2-20 零回退钉族全绿；owner doc `docs-for-ai/03-modules/nop-network.md` 落账。

## Non-Goals

- vertx-mqtt 生产激活证明（休眠面注记，激活回升条款 7.1）；C6-009 ThreadLocal 滞留（CL-025 平台轮）；MA-01（2523 已修）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-N + fresh-session 取证 13 原样/1 已修 + 六接缝/RP-1/13 先行失败落位）

- [x] 设计文档（裁定 A-N + 载体表 + 接缝）
- [x] 成员取证（fresh session：13 原样/MA-12 已修；先行失败基数核减 16→13）
- [x] 独立 draft review

### Phase 2 - 14 工单实现 + 回归测试

Status: completed（14 工单全落盘 @ 2026-11-11；三组通道+coordinator 补 C1-005；红证 `_tmp/red-green-ledger-2560.md` 合并台账）
Targets: nop-network/nop-{http-client-oauth,http-client-jdk,vertx-mqtt-server,netty,rpc-api,rpc-core,rpc-simple} + nop-integration/nop-integration-feishu + 测试树

- Item Types: `Fix`（13）+ `Proof`（1：MA-12 回归钉）

**实施顺序**

- 组 0（前置）：RP-1 两处测试改写（先红）→ VertxMqttServer.setVertx 注入缝 + MqttSessionManager.clear()（Phase 0）
- 组 1（oauth+jdk）：MA-08+A → MA-15 H 站点 → MA-16+C6-025+I
- 组 2（vertx+netty）：MA-14+C6-028+G（注入缝后）→ MA-09+B → MA-15 H 站点（mqtt 面）→ MA-10+C
- 组 3（rpc+feishu）：MA-12+E 回归钉 → MA-13+F → C1-003+J → MA-15 H 站点（rpc 面）→ C1-005+K → C1-010/C1-011/MA-16 feishu 分片（L/M）

Exit Criteria:

- [ ] 14 工单全部实施（13 Fix + MA-12 验证型回归钉）
- [ ] 载体表逐条存在且断言目标行为
- [ ] 关键项先红后绿或变异论证（≥3：MA-08 四入口、MA-15 包络、MA-14 doStart 抛出）
- [ ] **RP-1 两处先改测试**落盘（failed-future 断言形态）
- [ ] **先行失败测试双口径**：单元台账 13 处记账（批外项归对应工单）+ **本批 in-scope 枚举清单**（C10-009②/C10-006②/C10-007①/C10-013③/C10-005②+restart×N/RP-1×2/C10-014②③/C10-004①④/MA-12 回归钉+新造即红面）逐条登记为验收物
- [ ] **2503/2522/2523 零回退**：既有钉族全绿
- [ ] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面）
- [ ] E3：`./mvnw -o test -pl nop-network -am` + feishu 归因绿
- [ ] owner doc 落账：`docs-for-ai/03-modules/nop-network.md`
- [ ] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 六叶子模块（豁免逐条登记）
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（13 Fix + MA-12 验证）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（同步抛契约统一）
- [ ] 行为/契约结果已达成
- [ ] 必要 focused verification 已完成
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [ ] `./mvnw compile`（全触达模块）
- [ ] `./mvnw test`（逐模块）
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### vertx-mqtt 激活回升条款
- Classification: `watch-only residual`（§三.5#9 休眠面注记）
- Why Not Blocking Closure: 全模块生产零装配（唯一激活路径不存在）；激活证据与回升条件移交 7.1 统一裁定；本批代码修复照常落地。
- Successor Required: `no`

### C6-009 ThreadLocal 滞留子面
- Classification: `watch-only residual`
- Why Not Blocking Closure: 移交 CL-025 平台轮（MA-12 裁定文）。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-11，must-fix-first）**：F1 先行失败 13 处为单元级基数非本批验收基数（9+ 处批外项写了永久红挂门禁）→ 双口径区分（单元台账+本批 in-scope 逐条枚举）；F2 裁定 H 站点遗漏两处（MqttServerMessageService.sendAsync:76/NettyTcpServer.getFirstChannel）+SSE Flow.Publisher 面无 CompletionStage → 站点补全+onError 语义裁定；F3 裁定 A 前提错误（DelegateHttpClient.onRequestBegin 统一钩子已存在）→ override 改一行+分 provider 锁（否决 fetch-outside-then-put 防并发击穿）；F4 裁定 D remove-first 删除已文档化重试语义 → 显式取舍选项 (b)（remove→exchange→失败回插 putIfAbsent 防 ABA）；F5 裁定 G 注入缝形状钉死（bean 级 setVertx 非 NopVertx.setVertx——registerInstance 已存在）+doStop await 带超时+MqttSessionManager.clear() 新增面；F6-F9 精度（MA-10 方案可行/三字段非四/ownsScheduler=false fail-fast/JdkFeishuHttpApi 无 close 需 IFeishuHttpApi seam 新增）。修正后 ready-to-execute。

## Closure

Status Note: 8.66/R4-26 全 14 工单落地（13 Fix + MA-12 验证型回归钉），三组通道+coordinator 补 C1-005，RP-1 两处先改 RED 验证→修复 GREEN，先行失败双口径验收，八叶逐模块绿，owner doc 落账（首次落账遭并行 agent 提交覆盖→重新落账并经 doc-links 核验——并行写覆盖教训登记），E1 排除形态 BUILD SUCCESS。closure audit APPROVED-with-corrections（1 Major=owner doc 未落账[实为并行覆盖]→重新落账闭环；2 Minor=ledger 计数勘正/rpc-core pre-existing 观察项）。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-11）
- Evidence:
  - 14/14 机制 live 取证命中（onRequestBegin 四入口+分 provider 锁/CAS 注册+两参移除/世代守卫四检查点/remove→exchange→回插/MA-12 零 diff+回归钉/AtomicInteger+volatile/setVertx 注入缝+doStart await+doStop 超时+sessionManager.clear()/failed-future 全站点/JDK close 三处+IFeishuHttpApi.close seam/prev 回调合并链/三字段 volatile+scheduler 重建/WS 快照）
  - 独立复跑：oauth 5/jdk 30/vertx-mqtt 28/netty 17/rpc-api 9/rpc-core 53（4E=TestDefaultFlowControlRunner pre-existing，零 diff 归因 auditor 独立确认）/rpc-simple 6（ledger 勘正 4→6）/feishu 61 全核验
  - RP-1 核验：git show 07affd6d95 两处 in-place 改写非删除
  - Anti-Hollow 三链 PASS（四入口注入链/世代四检查点链/remove→exchange→回插链）
  - Deferred 诚实性 PASS（vertx-mqtt 休眠面/C6-009→CL-025）；scan-hollow 豁免（rpc-core pre-existing 零 diff）
  - 修正项：Major-1 owner doc 重新落账（首次写入遭并行 agent 提交覆盖——并行写覆盖教训登记于 ledger）；Minor-2 ledger rpc-simple 计数勘正
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- rpc-core TestDefaultFlowControlRunner 4 errors（pre-existing，NPE bucketFor:60，a7e348069d 引入）——归后续修复轨
- 编辑脚本边界纪律：plan 全文重复（2559）与 owner doc 并行覆盖（2560）两起同型事故——并行 agent 写文件需路径互斥约定
