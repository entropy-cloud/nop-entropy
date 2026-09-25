# 13 NodeTscBridge 会话隔离与资源治理

> Plan Status: draft
> Last Reviewed: 2026-09-25
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings C8、D9-TscBridge/TscBridgeConfig/TscProtocol）
> Related: ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md §5.3、11-performance-profiles.md §3
> R1 对抗审查（agent_006ff517）：无 Blocker；执行前裁定——(a) 代际隔离首选**每代独立队列**（spawn 换 queue 字段引用+reader 线程局部捕获 queue/Process，poll 全在 synchronized 内字段替换安全；帧打 generation 侵入更大）；(b) onExit 等待：spawn/close 两调用点全等（短超时硬编码常量，对齐现有超时常量风格），handleProcessFailure 持锁路径同样等待但记录代价；(c) 有界容量裁定为"reader put() 阻塞背压"（request/response 在 synchronized 下串行、实际深度 ~1-2、零风险），EOF 注入路径须满足同一上界语义——注意现 finally 用 offer()（满时静默丢 EOF→超时路径），改 put()；(d) defaultEnvironment 惰性化后 TestL2DemoSuite/TestTscBridgeReal 的 skip 判定路径随之迁移（"报 skipped 不报绿"契约保持）；(e) 本机 node v25.3.0 + typescript 存在，TestTscBridgeReal 三例可真实运行。

## Purpose

修复 NodeTscBridge 进程重启路径的代际污染缺陷（旧 reader 线程的迟到 EOF 被新会话握手消费 → 误判 EXHAUSTED 烧掉整个桥），并收敛该子系统的资源治理散点（无界队列、吞异常、writer 不关、非 lazy 的默认环境解析）。

## Current Baseline

（行号引用经 draft review 重核；审计报告 C8/D9 为初版依据。）

- **C8（重启代际污染）**：`spawn()` 先 `destroyProcess()`（`destroyForcibly()` 异步、不 `waitFor`、不 join 旧 reader）再 `inbound.clear()`；旧 reader 线程在旧进程流 EOF 后于 `finally` 向共享 `inbound` 队列 `offer(EOF)`——迟到 EOF 被新 spawn 的握手 `pollWithDeadline` 消费 →"peer exited before the ready frame"误判 → `EXHAUSTED` 终态；旧进程残留数据行也可能被当作新请求应答（id 校验兜底但表现为烧掉 restart 预算）。
- **资源散点**：`inbound` 无界队列（失控 peer 可无限堆积）；reader 线程吞 IOException 无日志；`peerInput` 从不 close/flush 即置 null；`TscTypeResolver` 仅测试构造、无生产接线（接线时引擎关闭路径必须调 close，否则 resident node 进程孤儿化——现状记录）。
- **TscBridgeConfig.defaultEnvironment()**：构造期走文件系统解析 helper 路径且解析两次（与"进程到第一条查询才存在"的 lazy 自述相悖），两次解析间 FS 变化可拿到不一致 command/helperPath；找不到时构造期抛 `TscBridgeUnavailableException`。
- **TscProtocol**：`resultOf` 的 `id` 参数未使用；`NodeTscBridge` 借 `request(0,"",…)` 造参数 map，wire 帧携带多余 `id`/`op` 字段。
- 测试防线：nop-lint-js 61 测试绿；TestTscBridgeReal 3 例真实 Node 非跳过（环境契约：缺 Node/typescript ⇒ 报 skipped 不报绿）。

## Goals

- 重启路径代际隔离：旧会话的 EOF/残留数据不可能进入新会话的队列（按代际隔离队列或等价机制），迟到 EOF 不再烧桥；`destroyProcess` 后等待旧进程退出（短超时）再清理。
- 资源治理：`inbound` 有界（失控 peer 背压/丢弃策略显式）；reader 异常留日志；close 路径先关 writer 再销毁进程。
- `defaultEnvironment()` 惰性化（构造不碰 FS、单次解析、失败延迟到 `isEnvironmentUsable()`）；`TscProtocol` 死参数删除、参数 map 构造专用化。
- 行为语义面（ready 握手、查询应答、程序缓存失效、降级阶梯 L2 gate）零变化。

## Non-Goals

- 不改 wire 协议格式（ready/查询帧 schema 不变——多余字段删除是发送面收敛，peer 侧解析兼容）。
- 不做 TscTypeResolver 生产接线（现状显式记录，接线属后续功能 plan）。
- 不改 L2 降级 gate 语义与 design 11 §3 lazy 契约本身（只是让实现回到契约）。

## Scope

### In Scope

- `nop-lint-js/src/main/java/io/nop/lint/js/tsc/`：NodeTscBridge、TscBridgeConfig、TscProtocol（死参数/参数构造）。
- 焦点测试：代际隔离（旧 EOF 不污染新会话）、有界队列、惰性环境。

### Out Of Scope

- tsc helper 脚本（JavaScript 侧）；nop-lint-core 其余。

## Execution Plan

### Phase 1 - 代际隔离与资源治理（Fix）

Status: planned
Targets: `nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/NodeTscBridge.java`

- Item Types: `Fix`

- [ ] 代际隔离：**每代进程独立队列（R1 裁定首选）**——spawn 换 queue 字段引用、reader 线程局部捕获本代 queue 与 Process（现读共享 `process` 字段 :233 有竞态）；`destroyProcess()` 后 `onExit()` 短超时等待（spawn/close 两调用点全等，硬编码常量；handleProcessFailure 同等但注明持锁代价）
- [ ] `inbound` 有界 + **reader `put()` 阻塞背压（R1 裁定）**——finally 的 EOF 注入从 offer() 改 put()（满时静默丢 EOF 会使握手走超时路径）；reader 的 IOException 记 logger；`close()` 先 close/flush `peerInput` 再销毁进程
- [ ] 焦点测试：spawn→杀旧进程→旧 reader 迟到 EOF 注入→重新 spawn 握手成功（修复前必现 EXHAUSTED 或可构造等价断言）；队列满时行为符合裁定；close 后无孤儿 writer
- [ ] TestTscBridgeReal 真实 Node 三例零回归（环境缺失时仍 skipped 不假绿）

Exit Criteria:

- [ ] 代际隔离测试红转绿；重启路径不再有跨代会话污染面（代码可观察）
- [ ] nop-lint-js 全量测试零回归（61+）
- [ ] `No owner-doc update required`（design 06 §5.3 行为契约未变，属缺陷修复）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 惰性环境与协议面收敛（Fix）

Status: planned
Targets: `nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/TscBridgeConfig.java`、`TscProtocol.java`

- Item Types: `Fix`

- [ ] `defaultEnvironment()` 惰性化：构造不碰文件系统、helper 路径单次解析存字段、解析失败延迟到 `isEnvironmentUsable()` 返回 false；**spawnCommand 内嵌 helper 路径字符串→spawnCommand() 访问时解析**；TestL2DemoSuite/TestTscBridgeReal 的 skip 判定路径随失败面迁移（"报 skipped 不报绿"契约保持）
- [ ] `TscProtocol.resultOf` 未用 `id` 参数删除；`NodeTscBridge` 参数 map 构造专用化（不再借 `request(0,"",…)` 塞多余 `id`/`op` 字段——发送面收敛，peer 解析兼容性以真实 Node 测试背书）
- [ ] 焦点测试：构造 config 不触发 FS 访问（可观测：不存在的根路径下构造成功）；isTypeAssignableTo 帧不再携带多余字段（真实 Node roundtrip 仍正确）

Exit Criteria:

- [ ] 惰性化测试落地（构造期零 FS、失败面移到 probe）；真实 Node 测试零回归
- [ ] nop-lint-js 全量测试零回归
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] C8 修复落地且有红转绿测试；资源散点（无界队列/吞异常/writer）全部收敛
- [ ] wire 协议与 L2 gate 语义零漂移（真实 Node 测试背书）
- [ ] owner docs：`No owner-doc update required`
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：代际隔离机制被 spawn/restart 路径真实消费（代码追踪）；无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-lint/nop-lint-js -am` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-js --severity high` 退出 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出 0

## Non-Blocking Follow-ups

- TscTypeResolver 生产接线时的 close 生命周期归属（引擎关闭路径调用）——接线 plan 的必要项，当前无生产消费方故不阻塞。
- 环境契约增补：`TscTypeResolver` 无 shutdown hook 兜底的裁定记录。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
