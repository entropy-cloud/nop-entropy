# 深度审核汇总报告 — nop-persistence 读写热路径并发·异步·资源专项审计（CRA WI 2.4）

## 基本信息

- **审核单元**: `nop-persistence-rw`（`nop-persistence/` 聚合器下五模块 509 files/84,484 lines：nop-orm 154f/25,517、nop-orm-eql 165f/39,510（单模块非聚合器）、nop-orm-model 67f/5,729、nop-dao 122f/13,435、nop-orm-data 1f/293（DaoEntityBlockingSource）；手写口径不含 `_` 前缀与 target/；本 campaign 最大行数单元）
- **审核日期**: 2026-10-18
- **执行维度**: C1/C2/C3/C4/C5/C6/C9 全维度（7 批次预拆制，实际 9 派发）+ C10 测试保护力（最后批次）；C7/C8 维度集外 overlay 两形态（CL 前缀 `nop-persistence-rw-CL-0NN`）
- **目标范围**: `src/main/java` 手写代码；基线 commit abccc80dde（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2471-cra-wi2.4-persistence-rw.md`（两轮对抗性审查通过后执行：R1 1 Blocker（基线配方漏 C9 节+2470 残留）+ 5 Major + 3 Minor 全修；R2 0 Blocker/Major + 3 Minor 全修）
- **执行说明**: 全部批次串行派发（运营者要求；预拆制 9 派发——C1×4/C2×3/C3+C4 合并批/C5+C6+C9 合并批）；判级口径「ORM 层被全平台数据访问调用；session 为 IContext 绑定（OrmSessionRegistry→IContext.attributes）；executeBlocking(ordered=false) 允许同上下文并发（C2 批次 1 核实）」；依赖 2.1 口径执行（设施缺陷仅误报校准引用，零重复立案）。

## 执行统计

| 维度 | 深挖 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|------|---------|---------|---------|------|------|
| C1 | 1 轮（合并批） | 30（4 批次，P1×5） | +0（三项穷举闭合） | 30 | 0 | 0 |
| C2 | 1 轮（合并批） | 20（3 批次，P1×8） | +1（C2-021 P3） | 20 | 1（**C2-014 P1→P2**：默认装配 ormDefaultTransactionListener 必然注入使惰性 init 竞态不可达） | 0 |
| C3 | 1 轮（合并批） | 6（P1×3） | +0（单点修复断言验证成立） | 6 | 0 | 0 |
| C4 | 1 轮（合并批，无新增） | 3 | +0（五根零自建池否定性验证维持） | 3 | 0 | 0 |
| C5 | 1 轮（合并批） | 4 | +1（C5-005 P2：findAll 族 F3 收口——**findAllByQuery 静默丢弃已设 limit**） | 5 | 0 | 0 |
| C6 | 1 轮（合并批） | 3 | +0 | 3 | 0 | 0 |
| C9 | 1 轮（合并批） | 5 | +1（C9-006 P3：IntArray 未覆写 equals——单槽 SQL 缓存命中率恒 0%，根因归 nop-commons）+CL-700 | 6 | 0 | 0 |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 17（缺保护登记） | 0 | 0 |
| **合计** | — | **71（P0×0/P1×17/P2×32/P3×22）** | **+3 = 74** | **74 全保留（含 1 降级）+ 17 缺保护** | 1 | 0 |

（计数勘误：dispatch-log 曾记「初审 72/+4/P1 立案 11」——复核按文件实态勘误为 71/+3/74/P1×18→降级后 17。）

**结构性发现**：nop-orm 与 nop-dao 事务设施 **JVM 级锁为 0**——并发正确性全押「session/事务同 context 单线程」契约，而平台自身语义（executeBlocking(ordered=false) 仅 warn、异步完成线程收尾、ContextTaskQueue.syncGet 隐藏重入通道）已使契约存在多条可具体化破口（C2-001/002/003 同根 F10、CL-402 平台级根因）。

## 合并裁定（详见 merge-adjudication.md）

- 台账 74 条不动（逐一归属）；**维度内并入 7 → 有效发现 67 = P1×13 / P2×31 / P3×23**；跨单元并入 0。
- 合并组：复核确认 3（**C1-001←C1-023**（IContext get-or-create 逐字节同构，ContextAttrs.getOrCreate 单修复）、**C1-008(c)←C2-008**（reloadModel 失效面同位点）、**C6-001←C3-006**（drainTo 单工单，修复重叠 >80%，K≥8 阈值亲验））+ 本裁定新裁 4（**C1-028←C2-020**（方言记忆化计数去重）、**C1-001←C2-003**（单槽互踩同位点同根因同后果，C1/C2 初审互盲致漏并）、**C3-005←C2-006**（flush dirty 先复位同位点，C3-005 为 P1 超集）、**C2-017←C1-026**（连接双取泄漏面同一缺陷，C2-017 为超集；C1-026「可见性面」误标勘正为原子性面））。
- **P1 变动**：台账 17 → 有效工单 13（并入 4 条 P1：C1-023/C3-006 确认项 + C2-003/C1-026 新并项；无升降级；C2-014 降级为复核既成）。13 条有效 P1 清单见下。
- 必裁 12 项逐一裁定：其余为同族相邻/不同缺陷——C1-004↔C1-001（refCount 链两侧修复互不替代）、C1-003/C1-021↔C2-007（可见性 vs 协议面三分立案）、C2-013↔C1-023（注册协议层 vs 实例创建层勿混淆）、C2-005 独立（CAS 谓词退化；事务边界内移须保持抢占原子性入工单验收）、C1-016（全单元唯一有钉，验证通过）、C5-001/002↔C9-002（不同缺陷）、C9-006↔CL-700（移交登记）。
- CL 台账终态：33 条全部确认——承接 8 / 收敛 5 / 独立 10 / watch 6 / 移交 4（CL-700→nop-commons IntArray equals 6 点回归面；CL-401↔WI 2.2 CL-018 同站点；CL-409 空号勘误）。
- **跨单元映射表 7 行**：X1 本单元 C1-010↔WI 2.3 C1-010 同号异单元（NopAuthSession 无 version 列丢更新，兜底语义互补）；X2 连接生命周期家族↔WI 2.2 C1-009；X3 C9-006+CL-700→nop-commons；X4 CL-401↔WI 2.2 CL-018 同站点（ContextTaskQueue.syncGet 重入通道，C2-009/C2-002 修复依赖该语义裁定）；X5 C3-001↔WI 2.3 CL-025 断连观测原语（排程关联非先决）；X6↔WI 2.3 C3-002 完成线程会话契约同族；X7 对 WI 2.1 零重复立案（仅误报校准引用）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 13 | **C1-001**（←C1-023/C2-003）IContext 会话/事务注册表 get-or-create 竞态（僵尸会话/事务表覆盖/双一级缓存；ContextAttrs.getOrCreate 单修复）、**C1-003** ormModel 非 volatile reload 后旧模型可见、**C1-015** OrmComponentModel 先发布引用后填充热路径竞态、**C2-001** session 全生命周期零互斥（flush 重入守卫为静默跳过）、**C2-002** runInNewSessionAsync 完成线程 flush/恢复/close 无同步、**C2-004** 全局实体缓存 read-repair 迟到 put 覆盖 evict（实体级 opt-in 启用上可达）、**C2-005** DaoEntityBlockingSource CAS 抢占退化无条件 UPDATE、**C2-013** 事务注册协议无原子性（半个成功 ERR_TXN_NOT_REGISTERED）、**C2-015** subTransactions 裸 LinkedHashMap 组提交丢写、**C2-017**（←C1-026）JdbcTransaction 连接生命周期无互斥双取泄漏+僵尸连接、**C3-001** runInNewSessionAsync 上游永不完成 session 永不关闭、**C3-005**（←C2-006）flush 先清 dirty 再执行 SQL 半成功残留、**C6-001**（←C3-006）drainTo 事务横跨等待窗口池耗尽（K≥8 阈值亲验） |
| P2 | 31 | 事务设施族（C2-014 降级后、C2-016 复核降无保护对应缺陷本体、C2-018 锁内动态实体加载、C1-002 session 写路径缺守卫、C1-004 refCount 竞态、C1-005/011/017/018/021、C2-006（已并）、C2-009/010/011/012）、EQL 族（C1-012/013/014）、缓存族（C1-008、C1-027）、载体族（C4-001 伪异步全景/C4-002/C4-003）、内存族（C5-001/002/005）、观测族（C9-001）等 |
| P3 | 23 | C1-006/009/010/019/022/028/029/030、C2-020/021、C3-002/003/004、C5-003/004、C6-002/003、C9-002/003/004/005/006 等 |

C10 另有缺保护登记 17 条（全 P2）：保护力矩阵 **有钉 1 / 部分 23 / 无保护 49 / N-A 1**；**P1×13 = 有钉 0 + 部分 8 + 无保护 5**——无一条 P1 拥有并发后果回归钉。唯一有钉：C1-016（TestLazyLoadOrmModel 真并发，修复与回归钉同 commit 99abdf8f28）。测试资产 249 测试类/1578 @Test，真并发测试仅 1 个（0.4%）。测试套件自身缺陷注记 N1~N6（复核全部成立——N1 钉住 INFO 日志行为、N3 钉错失败相位、N4 全 mock 屏蔽结构性盲区）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 74 条保留发现全部「新发现·未修复」（cra-index WI 1.1–2.3 台账不含本单元；git log 核实无审计后修复提交）。
- 与 WI 2.3 C1-010 同号异单元互注（NopAuthSession 无 version 列丢更新与本单元乐观锁语义互补）；对 WI 2.1 零重复立案。
- 维度集外线索 33 条全部确认：承接 8/收敛 5/独立 10/watch 6/移交 4（CL-700→nop-commons IntArray equals 6 点回归面）。

## 关键发现摘要（P1 全清单 13 条有效工单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-001（←C1-023/C2-003） | IContext 会话/事务注册表 get-or-create 竞态：僵尸会话/事务表覆盖/双一级缓存（ContextAttrs.getOrCreate 单修复） | nop-orm × nop-dao 注册协议 |
| 2 | C1-003 | ormModel 非 volatile：reload 后旧模型无限期可见+非同步 setOrmInterceptor | nop-orm 模型提供器 |
| 3 | C1-015 | OrmComponentModel 先发布引用后填充：热路径竞态无需 JMM 假设 | nop-orm-model 元模型 |
| 4 | C2-001 | session 全生命周期零互斥：flush 重入守卫为静默跳过（丢刷写/双刷写） | nop-orm 会话 |
| 5 | C2-002 | runInNewSessionAsync 完成线程执行 flush/registry 恢复/close 无同步 | nop-orm 异步入口 |
| 6 | C2-004 | 全局实体缓存 read-repair：迟到 put 覆盖并发 evict，陈旧值无界驻留 | nop-orm 二级缓存 |
| 7 | C2-005 | DaoEntityBlockingSource CAS 抢占退化无条件 UPDATE：任务重复投递 | nop-orm-data 抢占 |
| 8 | C2-013 | 事务注册协议无原子性：半个成功 ERR_TXN_NOT_REGISTERED | nop-dao 事务协议 |
| 9 | C2-015 | subTransactions 裸 LinkedHashMap：组提交丢写无感知 | nop-dao 事务组 |
| 10 | C2-017（←C1-026） | JdbcTransaction 连接生命周期无互斥：双取泄漏+僵尸连接丢写 | nop-dao 连接 |
| 11 | C3-001 | runInNewSessionAsync 上游永不完成：session 永不关闭 | nop-orm 异步入口 |
| 12 | C3-005（←C2-006） | flush 先清 dirty 再执行 SQL：非事务路径半个成功残留 | nop-orm 刷写 |
| 13 | C6-001（←C3-006） | drainTo 事务横跨等待窗口：K≥8 消费者池耗尽 | nop-orm-data 抢占消费 |

## 盲区自评

- `_gen/` 与 target/ 按纪律排除；`model/*.orm.xml` Protected Area 只读不受限。
- C7/C8 为维度集外 overlay 两形态（CL 线索登记）——若 7.1 需要完整口径需补审计。
- 17 条台账 P1 全部四要素复核通过（行级亲验+具体时序+判级口径+F3 闭合）；未做并发复现实验（时序推演到行级 + executeBlocking 语义核实）；C2-014 降级有装配面实测支撑。
- EQL 语言语义/SQL 生成正确性属质量域未审；`model/*.orm.xml` 模型文件内容未审（Protected Area）。
- 修复耦合：ContextAttrs.getOrCreate 单修复（C1-001 族案）、事务设施三切片（注册协议原子化/CAS 状态机/单锁连接生命周期）+ 三约束硬性执行、C6-001+C3-006 单工单、findAll 物化防线单工单（C5-005 汇聚 ISqlExecutor.findAll）、测试 N1/N3/N4 须随对应修复改写、CL-025（WI 2.3）平台原语前置非本单元先决。

## 与其他 roadmap 的重叠

- 与 WI 1.1–2.3 台账对账**零跨单元重复立项**（对 WI 2.1 零重复；WI 2.3 C1-010 同号异单元互补互注；连接生命周期家族与 WI 2.2 C1-009 两面关系单列）。
- full-code-review roadmap（2026-09）：无本域同位点。
- CL-700→nop-commons（IntArray equals 值语义，6 点回归面）、CL-401/CL-018→nop-kernel ContextTaskQueue 语义裁定——Phase 8 平台级批次输入。
