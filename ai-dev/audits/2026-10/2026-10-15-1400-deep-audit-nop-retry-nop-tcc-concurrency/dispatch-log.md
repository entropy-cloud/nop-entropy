# Dispatch Log — nop-retry-nop-tcc concurrency audit (plan 2466)

- 主 agent：ZCode 会话 sess_21ba4274（分支 concurrency-audit-remediation，worktree nop-entropy-concurrency-audit）
- 审计对象：nop-retry 聚合器（8 jar 子模块，40f/4,100 行手写）+ nop-tcc 聚合器（9 jar 子模块，42f/3,872 行手写），合计 82 files/7,972 lines（真手写口径，不含 `_` 前缀与 target/；含 `_gen` 全量口径为 90/15,211 已勘误）
- 方法论：ai-dev/skills/concurrency-resource-audit-prompt.md；维度集合 C1/C3/C6/C7/C8 全维度 + C10（roadmap 1.6 裁定；维度集外线索 overlay 为 C2/C4/C5/C9 四形态、CL 前缀 nop-retry-nop-tcc-CL-0NN，沿 plan 2464 裁定节口径）
- 基线：_tmp/cra-baseline-nop-retry-nop-tcc.txt（附录 C 全部命令 + C7/C8 补充正则 + C2/C4/C5/C9 维度集外节，按两目录分别执行，--exclude-dir=target）

## 盲审纪律（每份派发 prompt 均含）

> 【盲审纪律】禁止读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下的任何文件（含审计输出目录中其他维度的文件）。只审计当前 live code。例外：深挖轮次可读本维度前序轮次文件（必要输入）。

## 第 1 轮初审（串行 5 批次，2026-10-15）

| 批次 | 产出文件 | 派发 agent |
|------|---------|-----------|
| C1 共享可变状态 | C1-shared-mutable-state.md | （执行中） |
| C3 异步链路 | C3-async-chains.md | 待派发 |
| C6 资源生命周期 | C6-resource-lifecycle.md | 待派发 |
| C7 长时运行 | C7-long-running.md | 待派发 |
| C8 背压流控 | C8-backpressure.md | 待派发 |

## 第 1 轮初审结果（2026-10-15）

| 批次 | agent | 发现 |
|------|-------|------|
| C1 | agent_7ab325f3 | 6 条（P1×3：tcc CAS 失败不短路双向补偿、OVERWRITE 无 UK 兜底、pause/resume TOCTOU 回调丢异常；P2×1、P3×2） |
| C3 | agent_70509002 | 11 条（P1×6：CAS 写失败不短路、内存聚合误报、重试不续租双执行、join 无超时停摆、OVERWRITE 幽灵链、簿记异常覆盖业务结果；P2×4、P3×1） |
| C6 | agent_7eeaa53c | 13 条（P1×3：回调同步异常 future 永不完成、RUNNING 残留 UK 碰撞砖化、OVERWRITE 半个成功；P2×6、P3×4）+ CL-001（C5/P1 完成写失败副作用重复） |
| C7 | agent_dc01a770 | 12 条（P1×5：三表零保留、扫描停摆无界、僵尸 ERROR 放大、tcc 清理零驱动+零索引、namespace 硬过滤断链；P2×6、P3×1）+ CL-002/003 |
| C8 | agent_78e8bbd3 | 11 条（P1×1：扫描循环被拒/Error 即静默永久停摆；P2×8、P3×2）+ CL-004（C5/P1 过期扫描/清理零生产装配） |

维度集外线索累计 4 条（CL-001~004），待 Phase 4 复核确认/驳回。

## 第 2 轮深挖（串行，2026-10-15/16）

| 批次 | 产出文件 | 发现 | 终止判定 |
|------|---------|------|---------|
| C1 r2 | C1-round2.md | +3（P2×2：全局取消后分支可创建+跨终态分支失恢复入口、发布安全；P3×1） | 终止 |
| C3 r2 | C3-round2.md | +4 | — |
| C3 r3 | C3-round3.md | +2 | — |
| C3 r4 | C3-round4.md | 验证轮，0 新增 | 终止（聚合矩阵 9 单元关闭，C3-016 确认 P1） |
| C6 r2 | C6-round2.md | +3 | 终止 |
| C7 r2 | C7-round2.md | +3（全部 P2：C7-013 分支守卫读陈旧内存副本+恢复链孤儿、C7-014 惰性流单点异常整批跳过、C7-015 doScan 排水式循环心跳线性退化+**C7-002 触发条件修订：三种内置 HTTP client 默认 30s readTimeout，永久挂死收窄为自定义 invoker/显式禁用超时/慢滴三配置，建议 P1→条件化 P2**）+ CL-005（removeCompletedRecords 无 limit 全量物化，零装配死代码） | 终止（指令①日志站点 19 处全枚举、②清理面反向核查无降级证据 C7-001/005 维持 P1、③fencing 三边界正向、④零指标暴露、⑤超时复核闭合） |

## 第 3 轮深挖（串行，2026-10-16）

| 批次 | 产出文件 | 发现 | 终止判定 |
|------|---------|------|---------|
| C8 r2 | C8-round2.md | +4（**P1×1：C8-012 executeImmediateRetry 返回 future ≠ 回调挂接 future，同步 DB 异常下 allOf.join() 永久冻结、零日志**；P2×1：C8-013 batch/interval 参数零护栏；P3×2：C8-014 tcc 分支级 maxRetryTimes 死护栏、C8-015 PartitionResolver 空列表 fail-open）+ CL-006（完成回调内同步 REQUIRES_NEW DB 事务，线程模型待核） | 不终止（新 P1+新停摆家族）→ 停摆面统一表 S1~S16 产出 |
| C8 r3 | C8-round3.md | +2（P2×1：C8-017 executeTask 双后置条件契约缺口、6 组同步抛注入点；P3×1：C8-016 policy 级 retryingTimeoutMs 零读方死配置）+ CL-007（retryFromDeadLetter 死信重放零生产装配，同 CL-004 形态） | 终止（「返回 future ≠ 回调挂接 future」家族全扫 25 站点闭合，确认断裂仅 C8-012 一处；22 站点核验通过） |

### Phase 3 收敛汇总

- 发现总量：C1 9 + C3 17 + C6 16 + C7 15 + C8 17 = **74 条**（P0×0 / P1 / P2 / P3 分布以各文件为准，Phase 4 复核统一裁定）
- 维度集外线索累计 7 条（CL-001~007），待 Phase 4 复核确认/驳回
- 跨维度待裁定交叉项：C7-002 触发条件修订（P1→条件化 P2 建议，与 C7-015 合并裁定）、C8-001/C7-002/C8-012 停摆面三形态（异常逃逸/挂死/永不完成）合并裁定、C8-014/C8-016 死配置同族
- 全部维度终止信号成立，Phase 3 关闭
