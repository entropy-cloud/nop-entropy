# Adversarial Review: nop-job Round 10 — Summary

**Date**: 2026-06-19
**Module**: nop-job
**Scope**: 独立第 10 轮。切入点为 R9（2026-06-04）之后合入的全新功能代码（Plan 211~215：本地配置调度、资源限制、dispatchMode 路由、任务优先级、Best-Fit 派发），这些代码此前从未被任何审计覆盖。

## Overall Verdict: **issues**

17 new findings: 0×P0, 5×P1, 8×P2, 4×P3。完整发现见 `01-open-findings.md`。

## Top 3 Actionable Directions

1. **资源限制特性（Plan 212）worker 侧核心缺陷（AR-83, AR-84）** — `RESERVED_TASK_STATUSES` 含 WAITING 导致 worker 把自身归因任务 cost 双重计算，使 cost > capacity/2 的任务永不被认领；同时 null task cost（所有 Plan 212 前存量 schedule）在 fit-check 自动拆箱触发 NPE，终止整批 worker 扫描。资源限制路径从未在"声明 capacity + 存量 schedule"组合下跑通。

2. **批处理隔离缺失是跨 scanner 的系统模式（AR-86 ×2 + AR-84 落点）** — dispatcher 与 worker 的 scanOnce 都用单 try 包整个循环，dispatcher 还先"预锁存入"（全部 fire 翻 DISPATCHING）再循环；一个 fire 失败令其余卡死并被超时判失败（fail 而非 defer）。是 R9 AR-73（已修）同类问题在新 scanner 的重现。

3. **名实不符与静默失败集群（AR-87/89/90/93）** — `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED` 死代码静默兜底、`SingleBestFitStrategy` 实为 worst-fit、容量 `0` 在 config/metadata 两处语义相反、饥饿无日志。单个不致命，合起来让对系统行为的直觉全部失真。

## New Findings

| ID | Sev | Description | Confidence |
|----|-----|-------------|------------|
| AR-83 | **P1** | Worker reserved 重复计入自身 WAITING 任务 → cost>capacity/2 永不认领 | 确定 |
| AR-84 | **P1** | Null task cost → worker fit-check NPE → 终止整批扫描（影响存量 schedule） | 确定 |
| AR-85 | **P1** | Worker 忽略 CLAIMED→RUNNING 的 updateTask 返回值 → 失去所有权仍执行（重复执行） | 确定 |
| AR-86 | **P1** | Dispatcher 单 try 批处理：no-fitting-worker/loadSchedule 抛异常 → fire 卡 DISPATCHING 5min 后失败 | 确定 |
| AR-87 | **P1** | ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED 死代码，未知 dispatchMode 静默 fallback | 确定 |
| AR-88 | P2 | 无 WAITING-task 恢复路径（超时器只处理 RUNNING）→ 放大 AR-83 | 确定 |
| AR-89 | P2 | SingleBestFitStrategy 实为 worst-fit/spread，名实不符 | 确定 |
| AR-90 | P2 | config "0"=无限 vs metadata "0"=零容量黑洞；负数无校验 | 确定 |
| AR-91 | P2 | enforceAttribution=true 使 single-dispatch 任务在非同地部署饥饿 | 确定 |
| AR-92 | P2 | 优先级排序无索引（filesort）+ NULL 排序 MySQL/Oracle/PG 不一致 | 确定 |
| AR-93 | P2 | overfetch fit 过滤饿死窗口外可行任务，且无升级日志 | 确定 |
| AR-94 | P2 | reserved 读与插入跨事务 → 多 coordinator 超额派发 | 很可能 |
| AR-95 | P2 | dispatcher 二次覆盖 cost/priority（冗余 + 重新引入 NULL，AR-84 根因） | 确定 |
| AR-96 | P3 | per-fire 查服务发现 + GROUP-BY 聚合，无 per-scan 缓存 | 确定 |
| AR-97 | P3 | ResourceVector.add 静默 int 溢出；SQL SUM→Integer 溢出风险 | 确定 |
| AR-98 | P3 | PartitionTaskBuilder shortRange()[0,32766] off-by-one，partition 32767 无覆盖 | 很可能 |
| AR-99 | P3 | AdaptiveJobTaskBuilder 每 fire 硬编码 1 task；serviceName (String) 强转 CCE | 确定 |

## 误报校准

剔除 explore 子代理的 P0 候选"`AdaptiveJobTaskBuilder.scheduleStore` 未注入"：NopIoC `@Inject` 按类型自动注入（同文件 `JobDispatcherScannerImpl` 纯 @Inject setter 可工作即反证），故注入正常，P0 不成立。

## 严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 5 | 资源双重计算、null NPE、所有权丢失重复执行、批处理 fail-vs-defer、死错误码兜底 |
| P2 | 8 | WAITING 无回收、best-fit 名实不符、容量 0 黑洞、attribution 饥饿、优先级索引/NULL、overfetch 饥饿、跨事务超额、cost 二次覆盖 |
| P3 | 4 | 查询风暴、int 溢出、分片边界、强转/单任务限制 |
