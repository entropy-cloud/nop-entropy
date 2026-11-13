# Dispatch Log — CRA WI 4.5 nop-search-runner-autotest 并发审计

- **Plan**: `ai-dev/plans/2481-cra-wi4.5-nop-search-runner-autotest.md`（active；R1 agent_fdefed75 2 Major[dbtool 零主源码/autotest 并行执行预注失真]+4 Minor 全修；R2 agent_8035e4d0 0 Blocker/0 Major）
- **基线**: commit d3b05606de，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-search-runner-autotest.txt`（14 命令组 × 8 源码根 = 572 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发

## 源码根实测（Phase 1 回填，src/main 手写口径）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-search/nop-search-api/src/main/java | 8f/652 | 搜索 SPI 契约 |
| nop-search/nop-search-core/src/main/java | 4f/324 | |
| nop-search/nop-search-lucene/src/main/java | 5f/1,586 | LuceneSearchEngine（三 CHM :134-136 indexDirs/indexWriters/searcherManagers） |
| nop-runner/nop-cli-core/src/main/java | 29f/2,447 | SyncThreadPoolExecutor.INSTANCE:75（usage-site only，本体归 WI 2.1） |
| nop-runner/nop-cli/src/main/java | 1f/11 | |
| nop-runner/nop-tool/src/main/java | 11f/795 | CliRunCommand globalState CHM:70/CliWatchCommand state CHM:108 |
| nop-autotest/nop-autotest-core/src/main/java | 47f/4,822 | MockScheduledExecutor（volatile:17/synchronized×3）/AutoTestVars（ThreadLocal:34）/AutoTestOrmHook（双层 CHM:21-22/54）/TestClock |
| nop-autotest/nop-autotest-junit/src/main/java | 7f/479 | |
| **合计** | **112f/11,116**（dbtool 零主源码显式登记） | `_gen/` 残留 0 |

## 预注锚点核实（Phase 1，R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| dbtool 主源码 | 0f/0L | WI 2.5 已审不重复 |
| autotest ExecutorService/真 Thread | 0 命中 | Mock 替身为并发面主体 |
| LuceneSearchEngine 三 CHM | :134-136 | C6 索引生命周期重点 |
| SyncThreadPoolExecutor | :75 usage-site only | 本体归 WI 2.1 |
| MockScheduledExecutor 原语 | volatile:17/synchronized×3 | C1/C4 重点 |
| owner doc | nop-search 零提及/runner+autotest 4 处一句话 | drift 登记 |

## owner doc 漂移登记

- module-groups.md 对 nop-search 零提及；runner/autotest 仅 4 处一句话——按 drift 登记。

## 历史审计对照口径

- `ai-dev/audits/` 下三模块 **并发主题**直接审计 0 命中。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-21，C1+C4+C6 合并批单次派发）

| 维度 | 编号 | 结果 |
|------|------|------|
| C1 | C1-001…007；CL-001…004 | P2×4（Lucene 三 CHM 竞态/Mock 替身三重破坏/globalTimer debounce 竞态）+P3×3 |
| C4 | C4-001…005；CL-005…007 | **P1×1**（CliRunCommand scheduleWithFixedDelay Future 丢弃——异常静默停摆）+P2×2+P3×2 |
| C6 | C6-001…006；CL-008…010 | P2×4（CliWatchZipCommand init 非幂等/Lucene 三资源只增不逐出）+P3×2 |
| **Phase 2 合计** | 18 条 + CL×10 | **P0×0/P1×1/P2×10/P3×7** |

- 继续条件判定：C4 含 P1 → 3 维度全派第 2 轮（合并批）


| 维度 | 编号 | 结果 |
|------|------|------|

- 继续条件判定：C4 含 P1 → 3 维度全派第 2 轮（合并批）

### Phase 3+4 深挖与复核（2026-10-21，深挖+复核合并任务单次派发）

| 阶段 | 范围 | 结果 |
|------|------|------|
| R2 深挖 | C1/C4/C6 | +6（P2×4/P3×2）：C1-008/009、C4-006/007、C6-007/008；CL-011/012 | 3 维度全部显式终止 |
| 复核 | 24 条 | 24/24 全保留零驳回 | P1×1/P2×14/P3×9；新发现×24 |

- **复核后最终保留 24 条：P0×0/P1×1/P2×14/P3×9**
- 主登记建议网：C4-001（CliRunCommand 周期任务异常静默停摆——唯一 P1）、C6-004/C4-006（Mock 替身族）、C6-001+C6-002+C6-003（Lucene 三资源族）


| 阶段 | 范围 | 结果 |
|------|------|------|
| R2 深挖 | C1/C4/C6 | +6（P2×4/P3×2）：C1-008/009、C4-006/007、C6-007/008；CL-011/012 | 3 维度全部显式终止 |
| 复核 | 24 条 | 24/24 全保留零驳回 | P1×1/P2×14/P3×9；新发现×24 |

- **复核后最终保留 24 条：P0×0/P1×1/P2×14/P3×9**
- 主登记建议网：C4-001（CliRunCommand 周期任务异常静默停摆——唯一 P1）、C6-004/C4-006（Mock 替身族）、C6-001+C6-002+C6-003（Lucene 三资源族）

### Phase 5 C10 + Phase 6 合并裁定（2026-10-21，C10+裁定合并任务单次派发）

- C10：矩阵 24 行 = 会红 0/假保护 12/无测试 12（**全域会红=0 第四次同型确认**）；缺保护登记 C10-001~009 全 P2；反向钉死 0 硬钉 + 1 正向修复约束
- 合并裁定：24 → 15 工单 + watch 1（5 族 MA-01~05 吸收 14 + 9 独立）；P0×0 显式空执行/P1×1/P2×6/P3×7；跨单元 11 处零重复；冲突裁定 3 留 7.1
