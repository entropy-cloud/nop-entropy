# concurrency 设计文档（平台并发设施）

> Status: active
> Created: 2026-10-24

本目录承载 nop 平台**并发基础设施**（`io.nop.commons.concurrent` 及其 executor 家族）的架构决策与使用契约。本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 层级

### Vision 层

- 无独立 vision 文档：平台并发设施的定位与不可违反约束由源码包结构 + 各分项设计的「设计结论」节承载；当本目录文档数超过 3 篇时再拆分独立 Vision 层。

### Architecture Baseline 层

- `periodic-task-insulation.md` — 周期任务异常绝缘层（重臂 vs kill+alarm 选型、返回 future 终态 × isDone 守卫 × 重臂三相容、失败治理四件套[计数/退避/日志抑制/升级]、拒绝路径、API source-compat 形态与 C3-016 边界、MA-06 看门狗前置裁定、30 点表载体覆盖矩阵）
- `pool-facility-hardening.md` — 平台池设施加固（调度槽/执行体契约与 globalTimer 28 消费方文件全处置矩阵（终表 27 + 终表外新增 D5）、globalTimer 容量语义与工厂副作用移除、池工厂 fail-fast 校验契约、cached 共享池上界与熔断、收缩语义与热更面处置、与绝缘层共享代码位边界）
- `bindcancel-context-unification.md` — api-core 取消通道与上下文传播统一（bindCancel 完成原因判别与调用方兼容矩阵、C3-004 接入顺序约束与 R1-01/R1-02 边界衔接、出口泵 thenOnContext/入口 newRpcContext 内置 binder/平台根因 isClosed 守卫、syncGet 端窗修复选型、SPI 零签名变更与 ×11 回归面全表、ICancelToken 契约与 toolkit 取消链接线）
- `cache-invalidation-single-point.md` — 模型缓存失效链单点修复（check-changed 判据统一与唯一载体落点[DefaultResourceChangeChecker：mtime 相等 + 宽限期承诺]、从属编辑点枚举表与 closure audit 机械判定、窗口二 load 原子化/窗口一宽限裁决、ResourceTimestampCache 移除终裁、TTL 兜底双路径自愈覆盖矩阵、ResourceCacheEntry 内建门[verdict 记忆化 + maxAge + deps-空豁免]覆盖 6 直连使用方零改码、TenantAware/WithLoader 委托穿透断言、R2-01/R4-32/5.5-W-03 三边界）
- `ledger-retention-policy.md` — R01 F4 无界账本族级保留策略（统一参数集七项一处定稿[保留期/触发周期/批删上限/行数上限裁定/fail-fast 包络/配置键命名/清理谓词时间锚列]、触发形态四态菜单判据[机器速率写入面必须自动触发]、R01 六成员逐单传导表、C7-101 第七面三选一裁定与 trusted-device 边界注记、W-01 生命周期配对与 G2 retention×reJudge 两条差异条款、sys_change_log 留存语义、家族 SSOT 与参数修订纪律）

## 阅读顺序

1. `periodic-task-insulation.md`（周期任务绝缘语义；新增分项设计按编号或主题追加于此表）
2. `pool-facility-hardening.md`（池设施容量/校验/卸载契约；与 1 共享 executor 代码位，边界见其 §3.6）
3. `bindcancel-context-unification.md`（bindCancel 原语契约与 context 传播统一口径；消费 1/2 的共享代码位边界，见其 §3.2/§五）
4. `cache-invalidation-single-point.md`（check-changed 失效判据与模型缓存门语义；与 1/2/3 零共享代码位，边界归 R2-01/R4-32/R3-05 successor，见其裁定⑥）
5. `ledger-retention-policy.md`（R01 族保留策略参数与逐单传导；形态 A 周期清理链消费 1/2 的设施契约，见其 §3.6）
6. 实现锚：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`（`IScheduledExecutor` javadoc 为周期调度成文契约的代码侧锚点）+ `nop-kernel/nop-core/src/main/java/io/nop/core/resource/`（失效链设施与判据载体代码侧锚点）

## 职责边界

- 本目录：并发设施的**决策与契约**（为什么这样设计、语义是什么、拒绝了什么）
- `docs-for-ai/`：并发设施的**使用知识**（怎么用）；两者冲突时按 `ai-dev/design/README.md` 的 Precedence Model 处理
- 过程性审计证据（30 点表原始判定、单元审计详情）留在 `ai-dev/audits/`，本目录只保留最终裁定
