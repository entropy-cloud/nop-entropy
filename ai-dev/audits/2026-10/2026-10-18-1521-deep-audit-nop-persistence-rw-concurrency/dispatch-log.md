# Dispatch Log — nop-persistence-rw concurrency audit (plan 2471)

- 主 agent：ZCode 会话 sess_21ba4274（分支 concurrency-audit-remediation，worktree nop-entropy-concurrency-audit）
- 审计对象：nop-persistence 读写热路径五模块（5 源码根）509 files/84,484 行手写——nop-orm 154f/25,517（IOrmSession/OrmSessionImpl/IOrmTemplate/IOrmSessionEntityCache）、nop-orm-eql 165f/39,510（EQL/SQL 生成，单模块非聚合器 F9 落定）、nop-orm-model 67f/5,729、nop-dao 122f/13,435（IDaoProvider/IEntityDao/AbstractSqlExecutor）、nop-orm-data 1f/293（DaoEntityBlockingSource）
- 方法论：ai-dev/skills/concurrency-resource-audit-prompt.md；维度集合 C1–C6 C9 全维度 + C10 最后批次（roadmap 2.4 裁定）；C7/C8 维度集外 overlay 两形态，CL 前缀 `nop-persistence-rw-CL-0NN`
- 基线：_tmp/cra-baseline-nop-persistence-rw.txt（15 节，5 源码根分别执行）；计数与 plan 2471 对账一致
- 依赖 2.1 口径：nop-commons 并发设施缺陷仅误报校准时引用（WI 2.1 台账），不重复立案；跨单元对照：WI 2.2 C1-009（JdbcConfigSource 连接面）、WI 2.3 C3-002（OrmBatchLoader requireSession）、WI 2.3 C1 维度 P2 C1-010（NopAuthSession 无 version 列——勿与 2.2 同号 P1 混淆）

## 盲审纪律（每份派发 prompt 均含）

> 【盲审纪律】禁止读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下的任何文件（含审计输出目录中其他维度的文件）。只审计当前 live code。例外：深挖轮次可读本维度前序轮次文件（必要输入）。

## 第 1 轮初审（预拆制串行，7 维度，2026-10-18 起）

| 批次 | 产出文件 | 拆分批次 |
|------|---------|---------|
| C1 共享可变状态 | C1-shared-mutable-state.md | ✅ 4 批次完成（见下） |
| C2 锁与同步 | C2-lock-sync.md | ✅ 4 批次完成（见下） |
| C3 异步链路 | C3-async-chains.md | ✅ 合并批完成（见下） |
| C4 线程池治理 | C4-thread-pools.md | ✅ 合并批完成（见下） |
| C5 内存有界性 | C5-memory-bounds.md | ✅ 三维合并批完成（见下） |
| C6 资源生命周期 | C6-resource-lifecycle.md | ✅ 三维合并批完成（见下） |
| C9 请求热路径 | C9-hot-path.md | ✅ 三维合并批完成（见下） |

## C5+C6+C9 三维合并批结果（5 源码根全量，2026-10-18）

C5×4（P2×2：findAll 全量物化+L1 缓存双重驻留 100 万行 250-500MB、cacheRef 结果集单条目物化；P3×2）+ C6×3（**P1×1：DaoEntityBlockingSource.take 无限等待路径连接+事务+行锁持有窗口无上界，K 消费者先于生产者即池耗尽**；P3×2）+ C9×5（P2×1：事务监听器 O(n²) 1 万行 1-10s；P3×4：每查询 8-30μs 分解/单槽缓存命中率趋零/SQL INFO 日志税/命中路径审结）+ CL-600~604 + 配对表 21 站点 + 有界性总账 17 容器。编号至 C5-004/C6-003/C9-005/CL-604。

### Phase 2 初审总汇总（7 维度全量）

**发现 72 条（P0×0 / P1×11 / P2×34 / P3×27）+ CL 线索 21 条（CL-001~002/100/200/300~302/400~415/500~504/600~604）**。分布：C1×30（P1×5）、C2×21（P1×9）、C3×6（P1×3）、C4×3、C5×4、C6×3（P1×1）、C9×5。全部 7 维度含 P1 或 ≥3 条 P2/P3 且无终止信号 → Phase 3 全维度派深挖。

## Phase 3 深挖（合并批，2026-10-18）

| 维度 | 新增 | 要点 |
|------|------|------|
| C1 | 0 | 三项穷举闭合 |
| C2 | +1（C2-021 P3：REQUIRED 加入路径双读伪 ALREADY_STARTED） | 与 C2-016 共用 CAS 状态机修复 |
| C3 | 0 | C3-001 单点修复断言验证成立；C3-005 触发形态落实 |
| C4 | 0 | 否定性验证维持（五根零自建线程） |
| C5 | +1（C5-005 P2：findAll 族 F3 收口 4 新成员——**findAllByQuery 静默丢弃已设 limit** 契约缺口；ISqlExecutor.findAll 单点守卫） | — |
| C6 | 0 | C6-001 vs C3-006 裁定完成 |
| C9 | +1（C9-006 P3：IntArray 家族未覆写 equals——单槽 SQL 缓存命中率恒 0%，根因载体 nop-commons 登记移交）+ CL-700 | C9-003 判定修正 |

### Phase 3 收敛汇总

- 发现总量：72 + 4 = **76 条**（P1 立案 11 不变：C1×5/C2×9/C3×3/C6×1——降级建议无；本轮零新增 P1）
- 跨维度同族裁定（3 组闭合）：**C1-001+C1-023 同一缺陷合并**（ContextAttrs.getOrCreate 原子工具单修复）；静态单例槽 4 条同族相邻（一个「静态槽收紧」切片）；**C1-008(c)+C2-008 同位点双登记合并**（clearCache 内聚 queryPlanCache.clear+代版本校验）；C1-027/C2-004 同族相邻独立工单；C6-001+C3-006 同位点单工单（修复重叠 >80%）；C9-006 根因归 nop-commons（CL-700）
- 全部 7 维度终止，Phase 3 关闭

## C2 批次 1/4 结果（nop-orm + nop-orm-data，2026-10-18）

11 条（**P1×5：C2-001 session 全生命周期零互斥 flush 重入守卫为静默跳过、C2-002 runInNewSessionAsync 完成线程执行 flush/恢复/close 无同步、C2-003 OrmSessionRegistry 单值模拟栈非原子会话身份交换、C2-004 全局实体缓存 read-repair 迟到 put 覆盖 evict、C2-005 DaoEntityBlockingSource CAS 抢占退化无条件 UPDATE**；P2×4、P3×2）+ CL-400~408（9 条：CL-402 executeWithContext 并发仅 warn 为平台级根因、CL-401 syncGet 隐藏重入通道等）。**核心结论：nop-orm JVM 级锁为 0，并发正确性全押 session 单线程契约，平台语义多处破口**。编号至 C2-011/CL-408。

## C2 批次 2/4 结果（nop-orm-eql）

1 条（P3：SqlExprMetaCache bin 锁内实体元数据构造——CL-100 锁面完整化）+ CL-410~413（CL-410 最高价值：LoadedOrmModel.compileSql + TenantCachedQueryPlan 整实例覆盖 put——多租户并发首编永不收敛，修正批次 1 C2-008「自愈」判定为仅正确性自愈）。编号至 C2-012/CL-413。

## C2 批次 3+4/4 结果（nop-orm-model + nop-dao 合并批）

8 条（**P1×4：C2-013 事务注册协议无原子性（误报 ERR_TXN_NOT_REGISTERED 半个成功）、C2-014 AbstractTransaction.listeners 惰性初始化丢监听器（CL-404 必答题已裁：COW 快照迭代面成立/注册原子性面不成立）、C2-015 subTransactions 裸 LinkedHashMap 组提交丢写无感知、C2-017 JdbcTransaction 连接生命周期无互斥双取泄漏+僵尸连接**；P2×2、P3×2）+ CL-414/415。**C2 核心结论：dao 事务设施整体零 JVM 锁，并发纪律依赖未声明的「同 context 单线程」假设，平台上下文传播语义下不成立，4 条 P1 可具体化竞态；补锁三约束已写入锁序图**。编号至 C2-020/CL-415。

## C3+C4 合并批结果（5 源码根全量，2026-10-18）

C3×6（**P1×3：C3-001 runInNewSessionAsync 上游永不完成 session 永不关闭、C3-005 flush 先清 dirty 再执行 SQL 失败后半成功残留、C3-006 drainTo REQUIRES_NEW 横跨等待窗口池死锁**；P2×3）+ C4×3（P2×3：载体全景**五源码根无任何自建池——全部 Async API 为调用方线程伪异步**、完成线程执行模型无载体隔离、连接获取上下文放大）；CL-500~504。编号至 C3-006/C4-003/CL-504。

### C2 维度汇总（4 批次）

**21 条（P1×9：C2-001~005/013/014/015/017）+ 16 条 CL（CL-400~415）**。
| C3 异步链路 | C3-async-chains.md | 待派发 |
| C4 线程池治理 | C4-thread-pools.md | 待派发 |
| C5 内存有界性 | C5-memory-bounds.md | 待派发 |
| C6 资源生命周期 | C6-resource-lifecycle.md | 待派发 |
| C9 请求热路径 | C9-hot-path.md | 待派发 |

## C1 初审结果（预拆 4 批次，2026-10-18）

| 批次 | 范围 | 发现 |
|------|------|------|
| 1/4 | nop-orm + nop-orm-data | 11 条（**P1×2：OrmSessionRegistry.instance() check-then-act 单槽会话注册竞态→僵尸会话/双一级缓存、DefaultOrmModelProvider.ormModel 非 volatile reload 后旧模型可见+非同步 setOrmInterceptor**；P2×6、P3×3）+ CL-001/002 |
| 2/4 | nop-orm-eql | 3 条（P2×2 理论风险：EqlParseTreeParser 有状态单例暴露（XLangParseTreeParser 热路径共享移交 xlang）、CompiledSql 全可变 POJO 靠约定维持；P3×1）+ CL-100 |
| 3/4 | nop-orm-model | 8 条（**P1×1：OrmComponentModel 双字段先发布引用后填充热路径竞态**；P2×5：冻结守卫族 20 处绕过、putEntityModel 半注册、anyEntityUseTenant 派生标志过期等；P3×2）+ CL-200 |
| 4/4 | nop-dao | 8 条（**P1×2：TransactionRegistry.instance() 上下文属性 check-then-act 事务表覆盖、JdbcTransaction 惰性取连接竞态连接泄漏+事务原子性破裂**；P2×3：DaoProvider 单例槽/AbstractTransaction 状态族/查询缓存回填竞态；P3×3）+ CL-300~302 |

### C1 汇总

**30 条（P0×0/P1×5/P2×16/P3×9）+ 7 条 CL（CL-001~002/100/200/300~302）**。跨批次同族收敛点 3 组（供 Phase 6）：IContext get-or-create 竞态（C1-001+C1-023）、静态单例槽发布（C1-006+C1-024）、缓存 invalidation-vs-repopulate（C1-008+C1-027）。会话生命周期判定基线：session 为 IContext 绑定（OrmSessionRegistry→IContext.attributes），executeBlocking(ordered=false) javadoc 允许同上下文并发。剩余 6 维度按预拆制继续。
