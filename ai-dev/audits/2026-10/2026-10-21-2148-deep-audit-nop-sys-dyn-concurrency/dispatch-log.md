# Dispatch Log — CRA WI 5.2 nop-sys-dyn 并发审计

- **Plan**: `ai-dev/plans/2483-cra-wi5.2-nop-sys-dyn.md`（active；R1 1 Blocker+5 Major+3 Minor 全修；R2 agent_231d56ab 0 Blocker/0 Major）
- **基线**: commit 3807af8361；基线文件 `_tmp/cra-baseline-nop-sys-dyn.txt`（14 命令组 × 8 源码根 = 514 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: nop-sys-api 57f/6,632 + nop-dyn-api 45f/7,232 plan-first——审计只读不受限；P0 即修仅限 impl 叶

## 源码根实测（Phase 1 回填，R1 实测口径）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-sys/nop-sys-api | 57f/6,632 | Protected Area plan-first |
| nop-sys/nop-sys-dao | 58f/3,473 | SysSequenceGenerator/DefaultCodeRule/SysDaoMessageService/SysDaoResourceLockManager/SysDaoLeaderElector |
| nop-sys/nop-sys-service | 24f/747 | SysCompactExtFieldHelper（volatile CHM :24/:25） |
| nop-sys/nop-sys-app | 1f/38 | |
| nop-dyn/nop-dyn-api | 45f/7,232 | Protected Area plan-first |
| nop-dyn/nop-dyn-dao | 35f/1,802 | |
| nop-dyn/nop-dyn-service | 24f/1,882 | InMemoryCodeCache（CHM :66-73+volatile :78-79+synchronized×13）/DynCodeGen（AtomicReference×5+synchronized×13） |
| nop-dyn/nop-dyn-app | 1f/38 | |
| **合计** | **245f/21,844** | `_gen/` 残留 sys dao entity 25f + dyn dao entity 17f——排除 |

## 预注锚点核实（Phase 1，R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| C1 原语 | 6 文件 27 命中 | C1 重点 |
| C2 synchronized | 4 文件 32 命中 | C2 重点 |
| C4 周期任务 | SysDaoMessageService :217/:221/:228 + SysDaoNamingService :77 | 周期停摆族 CL 候选 |
| C4 orTimeout/ThreadLocal/BlockingQueue | 零命中 | 极稀疏 |
| C8 backpressure | SysDaoMessageService :234 注释 | C8 重点 |
| nop-dyn.md 并发关键词 | 0 命中 | drift 登记 |

## owner doc 漂移登记

- nop-sys.md 141 行/并发关键词 23 命中（有实质并发内容）；nop-dyn.md 65 行/**并发关键词 0 命中**——按 drift 登记。

## 历史审计对照口径

- `ai-dev/audits/` 下 sys/dyn **并发主题**直接审计 0 命中。

## 维度级派发记录

（Phase 2 起逐条追加）
