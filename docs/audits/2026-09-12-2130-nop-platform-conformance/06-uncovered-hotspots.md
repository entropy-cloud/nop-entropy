# 06 · 未深审高信号区（后续审计建议）

> 本轮深审覆盖：nop-ai、nop-stream、nop-metadata + 参照模块（nop-auth/nop-wf/nop-job/nop-task/nop-sys）。
> 以下区域仅经过全仓扫描（02）发现高信号，未做人工定性，按风险排序建议纳入下一轮。

## H1 · nop-datav-service（最高优先）

扫描信号密度接近深审模块，但完全未定性：

| 信号 | 证据索引 |
|------|---------|
| 裸时间 API **16 文件**，且形态与已证实有害模式一致（写库时间戳）：`export/NopDatavExportTaskRecovery.java:107/121/143`、`report/NopDatavReportDeliveryRecovery.java:103/118/142` 均为 `new Timestamp(System.currentTimeMillis())` | evidence/S03 |
| `IDaoProvider/IOrmTemplate` **25 文件**（全仓业务模块第一） | evidence/S06 |
| `dao()` 模板用法含 datav（见 S16 分布） | evidence/S16 |
| `Thread.sleep` 2 文件 | evidence/S20 |
| 裸异常 3 文件 | evidence/S09 |

定性未知：25 个 DAO 直用文件是 store/infra 边界还是业务 BizModel 旁路（对照 nop-metadata P1 模式），需人工逐文件判定。

## H2 · nop-auth MFA store 族（参照模块内的已知偏差，建议立项修复）

`nop-auth-service/.../mfa/store/DbMfaChallengeStore.java:68/92/114/134/142/156/191`、`RedisMfaChallengeStore`、`DbSmsCodeStore/DbEmailCodeStore/RedisSmsCodeStore/RedisEmailCodeStore`、`DaoUserContextCache.java:96`——共 9 文件约 26 处 `System.currentTimeMillis()/LocalDateTime.now()`。挑战码/验证码的过期判定恰好是 TestClock 敏感路径。另有 `WfModelHelper:28`、`TaskStepReturn:186` 等 11 处裸 IAE（详见 01 §3）。

## H3 · BizModel 内 BeanContainer（3 处，需逐处定性）

`nop-job-service/.../entity/NopJobFireBizModel.java`、`NopJobTaskLogBizModel.java`、`nop-metadata-service/.../entity/NopMetaQualityCheckpointBizModel.java`——BizModel 内 service-locator 式 `BeanContainer.` 取 bean，应改构造/setter 注入。属 P3 级，但位于参照系模块内，易被后续代码当模板复制。

## H4 · nop-ai-agent 引擎之外的自建能力扩散面

本轮已证实 nop-ai 7 项自建重复能力（03 §C）。其中**自造调度守护（3 处）与自造 DB 选主**的形态在 nop-ai-agent 之外是否被复制（如 nop-stream ops 面、nop-datav recovery 扫描线程），值得用 `ScheduledExecutorService|scheduleWithFixedDelay` 全仓模式扫描复查（本轮 S20 只覆盖了 Thread.sleep）。

## H5 · nop-sys-dao / nop-credential-service / nop-code-service

- nop-sys-dao：裸时间 7 文件 + IDaoProvider 9 文件 + S16 命中（`NopSysUserVariableBizModel`）——nop-sys 属基线模块，但其 dao 层裸时间值得核对是否为 02 §F-A 同模式。
- nop-credential-service：裸时间 5 文件 + IDaoProvider 5 文件 + S16 3 文件（`NopCredentialBizModel` 等）。
- nop-code-service：IDaoProvider 6 文件。

## 建议的下一轮动作

1. 对 H1（nop-datav）执行与本轮同模板的深审（预计半天）。
2. 立项修复全仓裸时间 API（02 §F-A，202 文件）：机械替换 `CoreMetrics.*`，TestClock 敏感路径（过期判定/租约/超时）优先。
3. 立项收敛裸异常（02 §F-B）：优先已有模块 Errors 体系的模块（stream/metadata/ai），补 ErrorCode 后删除字符串嗅探（`StreamOpsHttpServer:338`）。
4. 将本审计的基线校准结论（01 §2）同步进 `docs-for-ai/02-core-guides/domain-logic-and-ddd.md`（如尚未写明"参照模块无业务 Processor 层"的事实，补一句避免'每方法一 Processor'误读）。
