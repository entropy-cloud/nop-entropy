# 2392 full-code-review R1.26 — nop-integration 消息通道修复批（P1-6.8-002/004）

> Plan Status: active
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.26；findings `P1-6.8-002`（Yunpian null areaCode）+ `P1-6.8-004`（Feishu DATA 帧零 ACK），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.8-check-p1-batch-d.md`
> Related: 6.4 #8（Tencent 腿已修——Yunpian 为同根残腿，parity 修复）；R1.24（同批次 d 面已收口项）
> Owner Doc: `docs-for-ai/03-modules/nop-network.md`

## Purpose

收口消息通道两条 P1：①YunpianSmsSender null areaCode 拼接出 `"null138…"`（6.4 #8 同根残腿——6.4 仅修 Tencent 腿）；②FeishuClient DATA 帧处理后零 ACK 回发（wire 协议确认语义缺失→重复投递/反复断连）且 toInbound 丢 headers 使下游无从回执。

## Current Baseline

- **P1-6.8-002**：`YunpianSmsSender.sendMessage:141`——`param.put(MOBILE, message.getAreaCode() + message.getMobile())` null 拼接原样。生产链 MfaCodeSender:214-218 不设 areaCode（国内短信常态——6.4 #8 审计亲证）。**parity 基准**：TencentSmsSender:155-158 修复形态 = null/empty→DEFAULT_AREA_CODE="86"、strip "+"。**拼接语义裁定（自查）**：Yunpian mobile 参数为字符串拼接（非 Tencent 的分离 nationCode 参数），国内约定 = 无区号裸号——null/empty 时仅用 mobile（不引入 "86" 前缀拼接改变既有国内行为）；非空 strip "+" 后拼接（Tencent strip+ 裁定对齐）。
- **P1-6.8-004**：`FeishuClient` streamListener.onBinary:334-338——DATA 帧 onMessage 后无回发路径、ACK 帧仅 debug 日志；`toInbound:363-374` 不透传 frame.getHeaders()（下游拿不到 message_id/log_id 等回执头）。FeishuStreamFrame 具 headers/requestId 字段；FeishuFrameType.ACK=2。
- **测试基建**：Yunpian 有 TestYunpianSmsSenderCredential（TestableSender createClient seam + mockito stub client/smsApi + 捕获 param）；Feishu 有 TestFeishuClient（FakeStreamTransport 驱动 IStreamListener + sentFrames 记录——可直接喂 DATA 帧断言回发）。

## Goals

- Yunpian：areaCode null/empty → 仅用 mobile；非空 → strip "+" 后拼接；两腿 parity 回归测试。
- Feishu：DATA 帧处理后回 ACK 帧（headers 复制原帧 + requestId 沿用）；FeishuInboundMessage 增 headers 透传；wire 层契约测试。
- 判别测试双腿 stash 实证；两模块测试全绿。

## Non-Goals

- Tencent 腿（已修）；飞书重连/心跳语义；Yunpian international SMS 模板语义；ACK 超时重发机制（协议级确认仅到回发帧）。

## Scope

### In Scope

- `YunpianSmsSender.sendMessage` areaCode 守卫
- `FeishuClient` DATA→ACK 回发 + `FeishuInboundMessage.headers` 透传
- 测试：Yunpian areaCode parity 判别（捕获 param）；Feishu DATA→ACK wire 契约判别
- owner doc：nop-network.md 注记

### Out Of Scope

- Non-Goals 全部；其他 SMS 实现；P2 台账行

## Execution Plan

### Phase 1 - 双修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] YunpianSmsSender.sendMessage：areaCode null/empty → 仅 mobile；非空 strip "+" 后拼接；javadoc 记录拼接语义与 Tencent parity 裁定
- [x] FeishuClient：DATA 帧处理后构造 ACK 帧（FeishuFrameType.ACK.getMethod() + frame.getHeaders() 原样 + requestId 沿用）经 transport.send(FeishuPbCodec.encode) 回发；toInbound 增 headers 透传（FeishuInboundMessage 增字段）
- [x] 测试：①Yunpian——TestableSender seam 捕获 param：null areaCode → MOBILE 无 "null" 前缀（纯 mobile）；"+86" → "86138…"（strip+拼接）；空串同 null。②Feishu——FakeStreamTransport 喂 DATA 帧（headers 携带 message_id）→ sentFrames 断言新增 ACK 帧（method=ACK、headers 复制、requestId 沿用）+ handler 收到消息且 headers 透传
- [x] **判别验证（双腿 stash）**：①stash Yunpian 守卫→null 场景测试 FAIL（param 值 "null138…"）；②stash Feishu ACK→ACK 断言 FAIL（sentFrames 无新帧）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 双修全落地（areaCode 守卫/DATA→ACK）
- [x] 判别测试双腿绿 + 既有测试零回归
- [x] nop-integration-sms-yunpian/nop-integration-feishu 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-network.md`

- Item Types: `Fix`

- [x] nop-network.md：SMS areaCode 语义注记（null→裸号国内约定；非空 strip+ 拼接）+ Feishu ACK 契约注记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.26 翻 done；index P1-6.8-002/004 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-6.8-002/004）
- [x] 行为/契约结果已达成（判别双腿 + 零回归）
- [x] 必要 focused verification 已完成（两模块测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `./mvnw test -pl nop-integration/nop-integration-sms-yunpian,nop-integration/nop-integration-feishu` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- Feishu ACK 超时重发机制（协议级确认仅到回发帧；超时重投递属订阅语义域）
- Yunpian 国际号码格式校验（strip+ 后拼接的格式边界）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-
