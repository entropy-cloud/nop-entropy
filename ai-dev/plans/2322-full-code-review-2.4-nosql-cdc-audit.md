# 2322 full-code-review 2.4 nop-nosql + nop-cdc 深审

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.4（MA2）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 09/14/15 + 共享前缀）
> Related: plan 2314（1.7 已裁定 cdc 双叶零依赖空壳——本项只标定现状）；owner doc `ai-dev/design/nop-nosql/`（README + architecture.md）；既有深审目录 `ai-dev/audits/2026-05-20-deep-audit-nop-nosql-post-plan23` 为历史资产不入输入
> Skill 适配注记：两模块为 Redis 客户端封装与 CDC 空壳——维度 09 → 错误处理（Lua 脚本错误路径/异常转换/静默吞）；维度 14 → 异步与并发（异步 API 生命周期/PubSub 分发/连接生命周期/限流器状态机）；维度 15 → 类型安全（codec 对称性/泛型契约/强转）。check2 点名复核：P0-1（:19 RESP3 下 LettuceRateLimiter.tryAcquire 必抛 UnsupportedOperationException——**P0 即时通道候选，须实测**）+ P1-2（:44 PubSub 不按 channel 过滤串投）+ P1-3（:67 PrefixTextCodec 值类型不对称致 MFA 票据失效——roadmap「MFA 票据 P1 复核」）。check2 余量 P2×10/P3×12 留 R1.0 裁决或 watch-only。
> 只读审计（P0 走异步注入）；check2/nosql-cdc.md 为 roadmap 点名的唯一例外输入。
> Draft Review: agent_acb52850 首轮 3 Major（check2 处置状态遗漏——**P0 已裁非问题且有 raw-socket 服务端实测证据、修复已合入当前分支**；RESP3「逐原语验证」被静默窄化——补五族原语输出通道矩阵交付物；计数 P2×10/P3×12→P2×9/P3×13）+ 5 Minor（-pl 补 cdc、协商配置点改写、实测备选路径锚定、hollow 豁免声明等）全修。Lettuce 6.6.0.RELEASE 实证；MFA 消费链 RedisMfaChallengeStore:98/:169/:118 定位。

## Purpose

执行 roadmap 工作项 2.4：深审 nop-nosql（Redis 业务语义封装）+ nop-cdc（空壳现状标定），产出报告 `2.4-nosql-cdc-deep-audit.md`；复核 check2 点名 P0×1+P1×2 的 live 状态。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-persistence/nop-nosql/nop-nosql-core`：19 main/1 test。业务模式层接口（INosqlQueue/Lock/RateLimiter/Ranking/Counter/SessionStore/Cache）+ NosqlCache 203。
- `nop-persistence/nop-nosql/nop-nosql-lettuce`：18 main/4 test。核心：LettuceMessageService 385、LettuceRedisConnectionProvider 286、LettuceHashOperations 253、LettucePubSubService 206、LettuceRanking 150；Lua 脚本 + PrefixTextCodec。
- `nop-persistence/nop-cdc`：nop-cdc-core 1 main/0 test、nop-cdc-debezium 0/0——双叶零依赖空壳（1.7 已裁定），本项只标定现状与归属建议。
- check2（`ai-dev/audits/check2/nosql-cdc.md`）25 条：P0×1（:19）+ P1×2（:44/:67）+ P2×9 + P3×13，**每条带 fix-ai-check 处置块且修复已合入当前分支**（Lettuce 6.6.0.RELEASE 经 BOM 解析）：P0 已在文件内裁定「复查非问题」（真实 Redis raw-socket HELLO 3 实测——Lua boolean 恒扁平化为整数/nil 与协议无关；lettuce RESP3 MULTI 返回正常；docker 测试 60/60 绿）；P1-1（PubSub 串投）已修复（LettucePubSubService:128 过滤+测试 :705）；P1-3（codec 类型不对称）**暂缓+过渡 javadoc 已落地**（markVerified 仍写 Long——本项 still-live 复核点）；P2×9/P3×13 大多已修复（RedisConfig.verifyPeer、parseTokenCount、Guard 空参校验、4 个新 Lua 脚本、死脚本已删）。
- 点名复核口径（修正版）：P0 复裁定并留存证据（不预设即时通道——check2 已裁非问题）；P1-2 验证修复在位；P1-3 逐帧标定 still-live（预期仍 live——MFA markVerified/peek 消费链已定位 RedisMfaChallengeStore:98/:169/:118）；check2 真正暂缓项= P1-3 与 P3 `$d:` 信任边界两条，本项撞见时标定重复关系。历史深审目录（2026-05-20）不入输入。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；owner doc architecture.md（2026-05-20）为设计基线——文档-代码漂移属维度 09/15 域外（5.1/5.2 通道），本项只审实现行为。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.4-nosql-cdc-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-2.4-<序号>`）+ 驳回台账（正文）+ 裁决。
- RESP3/协议面（维度 09+15）：**RESP3 兼容逐原语矩阵交付物**（roadmap Details 忠实覆盖：Lock 的 BOOLEAN 输出/Counter 的 INTEGER/Ranking 的 double score/SessionStore·KeyValue 的 getex/setGet 输出通道 × RESP3 协商——以 check2 处置块的 raw-socket/lettuce 实测证据为基，逐原语核对 ScriptOutputType/命令输出类型并落盘矩阵；docker 可用时复跑 testRateLimiter_* 作加强项，不可用时引用处置块实测证据+源码推演，口径 repo-observable）；限流器 Lua/命令路径全链（P0 复裁定）；限流器数值解析（check2 :242 线索域撞见按新发现）；Lua 脚本错误路径与原子性（hash CAS vs 两步操作一致性域）。
- PubSub 面（维度 14）：分发过滤语义（**check2 P1-2 复核**——串投是否仍在）；订阅生命周期（cancel/resume/stale entry 域）；sendAsync 静默吞消息域；Options 参数消费。
- codec 与类型面（维度 15）：PrefixTextCodec 对称性（**check2 P1-3 复核**——Number/Boolean 写读往返；MFA 票据 markVerified/peek 链路追踪）+ `$d:` 反序列化信任边界域。
- 连接与缓存面（维度 14+09）：连接提供者生命周期（ClientResources 泄漏域）、SSL 配置、NosqlCache 契约（ICache 平台契约对齐）、集合参数空值防护。
- cdc 空壳现状标定 + 归属建议（1.7 裁定落实现状）。
- index 登记 + 独立 closure audit + roadmap 2.4 翻 done。

## Non-Goals

- Redis/Lettuce 库内部；RESP3 协议规范本身（只审客户端协商行为）。
- check2 P2×10/P3×12 的逐条复核（深挖独立撞见按新发现处理并注明重复关系）。
- cdc 空壳的功能建设（功能开发归各自 roadmap）；owner doc 更新（5.1/5.2 通道）。
- nop-auth MFA 票据的域逻辑（只追 codec 消费链证据，MFA 域归 3.10 安全审计）。

## Scope

### In Scope

- `nop-persistence/nop-nosql/**`
- `nop-persistence/nop-cdc/**`
- 只读输入：`ai-dev/audits/check2/nosql-cdc.md`（P0/P1 点名 + 标题撞车对照）；`ai-dev/design/nop-nosql/architecture.md`（语义基线）；nop-auth 的 codec 消费链（仅 markVerified/peek 调用点定位）

### Out Of Scope

- nop-persistence 其余兄弟模块；Redis 服务器行为；nop-auth 域逻辑深审（3.10）。

## Execution Plan

### Phase 1 - 深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: nop-nosql + nop-cdc

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：两模块 main/test 清单、公开接口（INosql* 族）方法表、Lua 脚本清单、**确认模块层无协议覆盖配置（RedisConfig 零命中 protocol/RESP）+ 客户端默认 newestSupported()=RESP3（6.6.0.RELEASE 实证）**、@Disabled 清单（docker-gated 测试标注）
- [ ] 限流与脚本子 agent（维度 09+15）：LettuceRateLimiter 全路径（**check2 P0-1 实测**——RESP3 场景或源码级推演+运行时验证）+ Lua 脚本原子性/错误路径 + Counter/Ranking 数值解析
- [ ] PubSub 与连接子 agent（维度 14）：LettucePubSubService 分发/生命周期（**check2 P1-2 复核**）+ ConnectionProvider 生命周期/SSL + SessionStore
- [ ] codec 与缓存子 agent（维度 15+09）：PrefixTextCodec 对称性与信任边界（**check2 P1-3 复核**——MFA markVerified/peek 链路：RedisMfaChallengeStore:98/:169/:118 定位，codec 侧标定即可，MFA 域归 3.10）+ NosqlCache ICache 契约 + KeyValue/Hash/List/Set/ZSet 原语对称性 + **RESP3 矩阵的原语输出通道核对（Lock/Counter/Ranking/SessionStore/KeyValue 五族）**
- [ ] cdc 空壳现状标定（并入任一子 agent）
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-2.4-{ratelimit,pubsub,codec}.md`

Exit Criteria:

- [ ] 三份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] check2 点名条目（P0×1+P1×2）逐条标定结论落盘（still-live/already-fixed/false-positive/duplicated + 实测证据）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；与 3.10（nop-auth 安全域）/6.x 边界协调；check2 重复发现归属
- [ ] 限流器与 PubSub 正结论收敛（RESP3 协商/分发语义各一条总评）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-2.4-{ratelimit,pubsub,codec}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `2.4-nosql-cdc-deep-audit.md`；index 登记 2.4 行
- [ ] 如有 P0（含 check2 P0 复核若 still-live）：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-persistence/nop-nosql/nop-nosql-core,nop-persistence/nop-nosql/nop-nosql-lettuce,nop-persistence/nop-cdc/nop-cdc-core,nop-persistence/nop-cdc/nop-cdc-debezium -am`（无条件执行；cdc 双叶零测试以构建通过为准；如遇失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 2.4 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因为预存失败并在报告/log 登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证与 scan-hollow-implementations 扫描（无新增实现，扫描对象为空）；以定向 test 运行为验证锚点。

- [ ] 报告落盘且三面全覆盖（限流脚本/PubSub 连接/codec 缓存）+ cdc 现状标定
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 2.4 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定。check2 P2×10/P3×12 留 R1.0 裁决通道或 watch-only 归档处理。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
