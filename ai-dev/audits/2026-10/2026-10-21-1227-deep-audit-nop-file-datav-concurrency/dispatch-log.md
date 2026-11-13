# Dispatch Log — CRA WI 5.3 nop-file-datav 并发审计

- **Plan**: `ai-dev/plans/2484-cra-wi5.3-nop-file-datav.md`（active；R1+R2 合并审查 agent_9642d045 4 Major+5 Minor 全修）
- **基线**: commit 30a60bd33a；基线文件 `_tmp/cra-baseline-nop-file-datav.txt`（12 命令组 × 5 源码根 = 857 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: nop-file-api 3f/492 plan-first——审计只读不受限；P0 即修仅限 impl 叶

## 源码根实测（Phase 1 回填，src/main 手写口径）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-file/nop-file-api | 3f/492 | Protected Area plan-first |
| nop-file/nop-file-core | 10f/687 | DaoResourceFileStore（上传去重主战场 :184-262） |
| nop-datav/nop-datav-api | 0f | pom-only |
| nop-datav/nop-datav-core | — | |
| nop-datav/nop-datav-service | — | DashboardBizModel/ExportTaskBizModel/ReportDeliveryExecutor 等 |
| **合计** | **144f/18,411** | |

## 预注锚点核实（Phase 1，R1+R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| C1 volatile/CHM | 12 命中/5 文件 | |
| C2 synchronized | 8 命中/2 文件（ShareAccessGuard×6/DashboardBizModel×2） | |
| C4 globalWorker/IJobScheduler | 8 命中/5 文件 + 周期任务 3 站点 | 周期停摆族 CL 候选 |
| C5 有界缓存 | DashboardPanelQueryCache MAX_ENTRIES/MAX_ROWS/TTL | C5 界口 |
| C6 DaoResourceFileStore | 11 命中（saveFile/DigestInputStream/LimitedInputStream/失败清理） | 上传去重主战场 |
| orTimeout/ThreadLocal/BlockingQueue/new Thread | 零命中 | 极稀疏 |
| nop-datav owner doc | 不存在 | drift 登记 |

## owner doc 漂移登记

- nop-file.md 281 行/并发关键词 0 命中——按 drift 登记；**nop-datav 无 owner doc**（94% 行数无 owner）。

## 历史审计对照口径

- `ai-dev/audits/` 下 file/datav **并发主题**直接审计 0 命中。

## 维度级派发记录

### WI 5.3 全流程合并执行（2026-10-22 完成，初审+深挖+复核+C10+裁定五阶段单代理完成）

- **实测规模勘误**：src/main 手写口径 5 根实测 nop-file-api 3f/492、nop-file-dao 6f/597、nop-file-service 4f/57、nop-datav-core 2f/276、nop-datav-service 82f/15,509 = **97f/16,931**（预注 144f/18,411 的差异为分组口径：预注 nop-file-core≈dao+service、datav 131f≈core+dao+service 含 datav-dao 48f/1,692 生成实体层，本 WI 审计面按手写 5 根执行）
- **初审**（盲审）：C1 批1（file×15 文件）/批2（datav-service 82 文件，20 核心全文精读+62 实例字段扫描）→ 8 条；C5 → 5 条；C6 → 5 条；合计 18 条
- **深挖**：C1+2（调度器生命周期/注册停摆）、C5+2（快照历史/会话列表）、C6+3（SMTP 半程/删除×在途/双失败）→ 7 条；累计 25
- **复核**：25 保留 / 0 降级 / 0 驳回；现状=新发现 24 + 部分已知 1（CL-003 face a 经 C10 钉死测试豁免）；cra-index 162 条 P1 查重：本单元条目 0 命中，跨单元同族关联 6 组
- **C10**：矩阵 18 行——会红 0 / 假保护 4 / 部分保护 1 / 无测试 13；反向钉死 1（testSlowExecutionRaceWorkerSuccessOverwritesScanFailed）；时序敏感 0；泄漏钩子 0；补测建议 5
- **裁定**：25 条 → 7 修复工单（W-01~07）+ 1 测试工单（W-08）；P0=0 P1=2 P2=13 P3=10；P1：CL-009（dashboard 查询无行上界）、CL-014（文件去重竞态）
- 产出：C1-shared-mutable-state.md / C1-round2.md / C5-memory-bounds.md / C5-round2.md / C6-resource-lifecycle.md / C6-round2.md / C10-test-protection.md / merge-adjudication.md（九节）
