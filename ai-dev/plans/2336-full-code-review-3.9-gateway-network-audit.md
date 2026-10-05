# 2336 full-code-review 3.9 nop-gateway + nop-network 深审

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.9（MA3）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 12/13/14 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-network.md` + `docs-for-ai/03-modules/nop-ai-gateway.md`；审计输入四份——check2/gateway-bizauth.md **18 条**（P0×2 :19 AiAuth 混合大小写 key + :43 AiRateLimit X-Forwarded-For 塌缩 + P1×4 :66/:84/:110/:129 + P2×5 :153/:170/:191/:210/:233 + P3×7 :255/:272/:291/:311/:331/:354/:373，18 个 fix-ai-check 处置块）；check/gateway-bizauth.md **24 条**（P0×1 :23 测试 API Key 硬编码 + P1×5 + P2×9 + P3×9，仅 1 处置块）；check/net-misc.md **24 条**（P0×3 :21 GZip 解压失效/:45 SocketServer key 不一致/:70 MQTT accept + P1×7 + P2×7 + P3×7，仅 3 处置块）；check/net-http-rpc.md 20 条（P1×4 + P2×11 + P3×5，0 处置块）。**NET 族 P0（:21/:45/:70）处置记录在 check/SUMMARY.md :98**（2026-08-22 剩余 32 P0 清零波次「流处理/失效类 9 条全部修复」）。
> 只读审计（P0 走异步注入）；四份 check/check2 报告与 check/SUMMARY.md 为点名例外输入。
> **标定口径**：already-fixed 判定须定位 live 修复点（file:line）并抽样复跑对应回归测试；pre-existing「暂缓/需 owner 裁定」处置须显式承接（维持暂缓/重新裁定/转 R1.0，不得悬空）；标定结论与处置块声明冲突时升级 P0 即时通道评估。
> Draft Review: agent_ad7e3388 首轮 2 Blocker（F1 四份输入构成勘正：实为 86 条〔18+24+24+20〕非 79——check2 P2×5+P3×7、check gateway P3×9、net-misc 24 条〔P2×7+P3×7〕、net-http-rpc P2×11+P3×5、:255 重复列举矛盾消除；F2 vertx 实为 16 main Java 非 0——MQTT 标定对象全部 live、修复已合入 master）+ 2 Major（F3 标定例外清单——15+ 条标定对象在原 In Scope 外；F4 AI 网关拦截器全在 nop-gateway 非 nop-ai-gateway——后者整体归 7.1）+ 4 Minor（基线补 7 子模块计数/-am 偏离显式裁定/先例引用补出处/Filter 命名勘正）——本轮全部修复；agent_8335121f 聚焦复核 8 项全 PASS + 1 Minor 残留（:54/:60 nop-ai-gateway 列举）已随手勘正——无 Blocker，进入执行。

## Purpose

执行 roadmap 工作项 3.9：深审 nop-gateway + nop-network（AiAuthGatewayInterceptor 鉴权失败语义确认、Apache/Jdk HTTP 客户端错误路径、NET 族 P0〔解压、MQTT〕复核），产出报告 `3.9-gateway-network-deep-audit.md`。

## Current Baseline

- nop-service-framework/nop-gateway：53 main Java（GatewayHttpFilter + 拦截器链〔Auth/AiAuth/AiRateLimit/AiFailover〕+ InvokeProcessor；AuthFilterConfig/AuthHttpServerFilter 位于 nop-biz-auth-core，属标定例外）。
- nop-service-framework/nop-gateway 含全部 AI 网关拦截器（AiAuthGatewayInterceptor/AiRateLimitGatewayInterceptor/AiFailoverGatewayInterceptor/InvokeProcessor——**均在 io.nop.gateway.core 下**；check2/check 的全部发现指向 nop-gateway + nop-biz-auth-api/core + nop-biz-file-core，**零指向 nop-ai-gateway**）。nop-ai/nop-ai-gateway（27 main：channel/feishu 连接器 10、failover 族 13〔含易混类 AiGatewayFailoverInterceptor——与 AiFailoverGatewayInterceptor 是两个类〕、login/scan 4）**整体归 7.1 增量审计，本项不审**。
- nop-network 组：http-api 46/http-client-jdk 8/http-client-apache 10/http-client-okhttp 3/netty 31/socket 11/rpc-model 24/codec 6/**vertx 16**（commons 3/mqtt-server 13——**net-misc MQTT 条目的标定对象全部 live 存在，MqttConnection:139 endpoint.accept() 修复已合入 master**）/rpc-api 18/rpc-core 30/rpc-http 3/rpc-simple 3/http-client-oauth 6/http-server-filters 1 main Java。手写最大：JdkHttpClient 605、ApacheHttpClient 545、ContentType 461、OkHttpClientImpl 397、NettyTcpClient 349。
- 测试资产：以 find 实测为准（Phase 1 复核）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS。**其后相关域可能被其他 plan 触及——Phase 1 机械基线须 git log 复核三组在 2026-09-22 后的产品代码变更并登记**（plan 2335〔3.8〕Current Baseline 曾因同类遗漏被对抗审查拦下，见 `ai-dev/plans/2335-full-code-review-3.8-tcc-retry-file-audit.md` Draft Review 行）。预存失败归因兜底见 Phase 3（已知：nop-stream-runtime TestStreamConnectorRegistryDiscovery、nop-sys-dao 测试编译断裂〔3.5 域〕——仅在 -am 链上）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.9-gateway-network-deep-audit.md`：摘要 + 标定台账 + P0–P3 发现（`P<级别>-3.9-<序号>`）+ 驳回台账 + 裁决。
- 网关鉴权与限流语义（维度 13，roadmap 点名第一优先）：AiAuthGatewayInterceptor 鉴权失败语义确认（**warn 后是否拒绝**——fail-open/fail-closed 裁定）+ AiRateLimitGatewayInterceptor（:43 塌缩/:110 无界绕过域）+ AiFailoverGatewayInterceptor（:233 header 转发域）+ BizAuth 面（AuthFilterConfig :129 开放重定向、GatewayHttpFilter、:66 拒绝响应无消费者域、check :23 测试 Key/:86 OAuth cookie/:109 IP 计数 key 域）。
- HTTP 客户端错误路径（维度 12/14，roadmap 点名）：JdkHttpClient（605 行——:24 503 误判域 + :44 SSE 取消泄漏域）、ApacheHttpClient（545 行——:102 背压冻结域）、OkHttpClientImpl（:83 证书校验默认/:136 GET 必抛域）、uploadAsync 三实现 null（:157 域）、RPC 面（MessageRpcClient :65/:211、RpcTaskMonitor :227、RpcMethodReference :261 域）、:278 X-Forwarded-For 最左侧域（与 gateway 限流撞车对照）。
- NET 族 P0 复核（维度 14，roadmap 点名）：net-misc :21 GZip 解压/:45 SocketServer key/:70 MQTT accept 三条 P0 逐条实测标定（SUMMARY :98 声称已修）+ :97/:119/:143/:163/:182/:204/:220 P1 域 + ByteBuf release :241 域。
- 权限横切（维度 13）：gateway 拦截器链的动作权限语义、API Key/token 日志脱敏（:153/:170 域）、LocalEmailCodeStore/LocalMfaChallengeStore 无界（:191 域）、check :283 上传跳过鉴权域（与 3.8 P1-3.8-001 撞车对照）。
- 四份 check/check2 报告 **86 条**标定全覆盖（still-live/already-fixed/false-positive/duplicated，check↔check2 同源合并；gw 面 42 = check2 18 + check 24；net 面 44 = net-misc 24 + net-http-rpc 20）+ 处置块声明冲突升级评估。
- index 登记 + 独立 closure audit + roadmap 3.9 翻 done。

## Non-Goals

- nop-network meta/web/codegen/app 生成面（抽查即可）；nop-vertx 不做全面深审（但 net-misc 点名 MQTT/Vert.x 条目须对 live 文件逐条标定）；nop-ai 组全部（含 nop-ai-gateway，7.1 域）；2.7 已扫声明面；nop-rpc 非 model 子模块不做全面深审（仅对标定所引条目只读实证——rpc-core 30 main 实测存在）

## Scope

### In Scope

- `nop-service-framework/nop-gateway/**`、`nop-network/nop-http/**`、`nop-network/nop-netty/**`、`nop-network/nop-socket/**`、`nop-network/nop-rpc/nop-rpc-model/**`、`nop-network/nop-codec/**`
- **标定例外（只读验证，不深审）**：标定所引 live 文件位于下列模块时，仅做条目标定实证，不展开全面深审——`nop-network/nop-rpc/nop-rpc-core|nop-rpc-api|nop-rpc-http|nop-rpc-simple`、`nop-network/nop-http/nop-http-client-oauth`、`nop-network/nop-http/nop-http-server-filters`、`nop-network/nop-vertx/nop-vertx-mqtt-server`、`nop-auth/nop-biz-auth/nop-biz-auth-core`、`nop-file/nop-biz-file-core`（以实际路径为准）
- 只读输入：check2/gateway-bizauth.md、check/gateway-bizauth.md、check/net-misc.md、check/net-http-rpc.md、check/SUMMARY.md（:98 NET 族处置）、两个 owner doc

### Out Of Scope

- nop-ai 组非网关面；nop-rpc 非 model 子模块深审；2.7 已扫声明面；demo/app。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: completed
Targets: nop-gateway + nop-network（nop-ai-gateway exclude→7.1）

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：**组内全部子模块逐一计数**（含标定例外模块，深审范围外标注 exclude 理由）、四份 check/check2 报告逐条清点（构成勘正）、处置块状态盘点、**三组 2026-09-22 后 git log 产品代码变更登记**（已知线索：93d47833f0 触及 OkHttp trust-all〔net-http-rpc :83 域〕、d149b645df 增 gateway/auth-core 测试）、测试资产 find 实测
- [x] 网关面子 agent（维度 13+12）：nop-gateway + nop-ai-gateway——AiAuth 鉴权失败语义（warn 后拒绝？深标定）+ AiRateLimit/AiFailover + BizAuth AuthFilter/GatewayHttpFilter/拦截器链 + check2 18 条与 check 24 条撞车合并标定（42 条）
- [x] 网络面子 agent（维度 12+14）：nop-network——NET 族 P0 三条复核（:21/:45/:70 实测）+ HTTP 三客户端错误路径 + SSE 双实现 + RPC model 面 + net-misc 24 条与 net-http-rpc 20 条标定（44 条）
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-3.9-{gw,net}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] 四份报告 86 条标定结论落盘（18+24+24+20）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 标定独立验证（29 候选：A 11 保留+1 降级〔12-03 不可达证伪〕、B 17 全保留带 2 修正；标定抽验零虚假申报）
- [x] 主 agent 汇总：判级统一、与 3.8（file 上传跳过鉴权 :283 撞车）/3.10（nop-auth 增量安全审计边界）/7.1（nop-ai 增量）划界、安全类判级按 2.6 总裁据（check :49/:86 P1 确认维持登记 R1.0）
- [x] 两面正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.9-{gw,net}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.9-gateway-network-deep-audit.md`；index 登记 3.9 行 + P1-check-gw-49/86 汇总行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记（本项零 P0——check2 :19/:43 与 NET 族 :21/:45/:70 五条 P0 全部 already-fixed 实测确认，未触发即时通道）
- [x] 定向验证锚点：`./mvnw test -pl <8 审计域模块>` BUILD SUCCESS（gateway 107/http-api 38/jdk 9/apache 9/okhttp 9/netty 12/socket 9/codec 9 全绿）
- [x] 独立子 agent closure audit；通过后 roadmap 3.9 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且网关鉴权限流/HTTP 客户端错误路径/NET 族 P0 复核/权限横切面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.9 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 只读审计计划收口。报告 `3.9-gateway-network-deep-audit.md` 落盘：通过无 P0/P1 新发现附 P2×9/P3×20（净 29 条；A 面 11 保留+1 降级〔12-03 不可达证伪〕、B 面 17 全保留带 2 修正〔14-09 范围 3→2、14-13 论据〕）。**86 条标定全覆盖零虚假申报**（50 AF + 9 PF 台账行 + 1 adjudicated + 26 still-live + 0 FP）。**AiAuth 鉴权失败语义终裁 fail-closed**（warn 脱敏后 throw 401 三层证据）。**五条 P0 全部 already-fixed 实测**（check2 :19/:43 + check :23 + NET 族 :21/:45/:70，测试亲跑绿）。check :49/:86 两条 P1 确认维持登记 R1.0（P1-check-gw-49/86）。定向验证 8 模块全绿。closure audit 一次 PASS（处方为 log/plan 补记类机械项，已落实）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_26868bb3-e2fb-4c12-85f9-c220ec4f4840（fresh session，只读 closure auditor；一次 PASS 附机械处方）
- Evidence:
  - 计数勾稽：净 P0=0/P1=0/P2=9/P3=20=29 ↔ 报告实点（机械 grep 9+20 标题）↔ index 3.9 行（0/0/9/20）↔ 映射表 29 行 ↔ 复核净账（A 12 + B 17）
  - P1 台账：P1-check-gw-49/86 两行在案且「非转正——check 原文即 P1」性质记载明确；「P0 发现追踪」无 3.9 新增正确
  - 标定台账 86 条四节机械点数吻合；重点亲读五条 P0 修复点 + AiAuth fail-closed 三层证据 + :83 adjudicated 与 plan 2283 三方交叉一致
  - 锚点抽验 6/6 亲读命中（AuthHttpServerFilter:344-370 + flushTasks 捕获/JwtHelper ARG_TOKEN 13 处实测/416 Range JDK 无 else-remove + Apache 对照/OkHttp download catch :223-225 范围修正准确/UOE 分支 :61-64 + RouteExecutor 分流先于 proceedInvoke/parseEnumRest 丢弃 + ProtoApiModelLoader 生产委托）
  - 格式：抽查 6 条 11 要素完整
  - 12-03 降级与 14-09 范围修正六处如实记载
  - 定向验证：8 模块 192 例全绿（auditor 核对记录）；`check-doc-links.mjs --strict` exit 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以定向 test 全绿 + closure auditor 亲读 live code 锚点抽验为锚
  - Deferred 项分类检查：无 deferred；P2×9 进 R1.0 输入池；still-live 26 条归属 R1.0 输入池/watch-only；check :49/:86 P1 登记 R1.0，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P2×9 修复归属 R1.0 分族；P3×20 watch-only/R1.0 低优先池；still-live 26 条按报告裁决段归属；check :49/:86 已登记 P1-check-gw-49/86 进 R1.0）
