# Dispatch Log — nop-core-framework concurrency audit (plan 2469)

- 主 agent：ZCode 会话 sess_21ba4274（分支 concurrency-audit-remediation，worktree nop-entropy-concurrency-audit）
- 审计对象：nop-core-framework 六子模块（ioc 99f/9,524、config 40f/3,541、plugin 26f/3,732、security 12f/630、log 11f/528、boot 5f/364）+ nop-utils（115f/11,003），合计 308 files/29,322 行手写
- 方法论：ai-dev/skills/concurrency-resource-audit-prompt.md；维度集合 C1/C2/C4/C6 全维度 + C10 最后批次（roadmap 2.2 裁定）；C3/C5/C7/C8/C9 维度集外 overlay 五形态，CL 前缀 `nop-core-framework-CL-0NN`
- 基线：_tmp/cra-baseline-nop-core-framework.txt（14 节，7 目录分别执行）
- 预注锚点：R2 全量实测逐字命中（BeanContainerImpl/SingleThreadConfigExecutor/PluginManagerImpl/DefaultConfigProvider/VirtualThreadTaskExecutor/Jdk21CommonsCoreInitializer 等）；api-core（IConfigRefreshable/AppConfig/FutureHelper）越界仅对照

## 盲审纪律（每份派发 prompt 均含）

> 【盲审纪律】禁止读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下的任何文件（含审计输出目录中其他维度的文件）。只审计当前 live code。例外：深挖轮次可读本维度前序轮次文件（必要输入）。

## 第 1 轮初审（串行 4 批次，2026-10-17）

| 批次 | 产出文件 | 派发 agent |
|------|---------|-----------|
| C1 共享可变状态 | C1-shared-mutable-state.md | （见下结果表） |
| C2 锁与同步 | C2-lock-sync.md | 同上 |
| C4 线程池治理 | C4-thread-pools.md | 同上 |
| C6 资源生命周期 | C6-resource-lifecycle.md | 同上 |

## 第 1 轮初审结果（2026-10-17）

| 批次 | agent | 发现 |
|------|-------|------|
| C1 | agent_f7999321 | 16 条（**P1×3：DefaultConfigProvider.applyChangeToUsed 裸字段发布配置刷新、JdbcConfigSource.close 竞态连接泄漏、VfsPluginDefinition unload/load 锁外交错活容器泄漏**；P2×9、P3×4）+ CL-001~003（CL-001 JdbcConfigSource tryTriggerChange 在 try 外候选 P1）+ 口径备注：nop-ioc 实测 135f/17,072（含测试面，与派单 99f/9,524 src/main 口径差待复核轮统一） |
| C2 | agent_92c4b6cd | 11 条（**P1×2：getBean0 锁序环——InjectTypeValueResolver/factoryBean 依赖边对拓扑图不可见跨线程死锁、ProducedBeanInstance 跨实例 monitor 等待环深化**；P2×6、P3×3）+ CL-004~007 + 44 处锁站点矩阵与全模块锁序图 |
| C4 | agent_9d1f7727 | 7 条（**P1×1 门控：concurrent-start 三复合面——启动 bean 占 globalWorker+框架级无 join 点+启动失败进程带死容器 run-success 存活**；P2×3、P3×3）+ CL-008（ShutdownHook——nop-kernel 辖区移交=WI 2.1 C6-011 同位点）/009（PluginManager syncGet 零立案终判）+ 7 目录执行载体全景 11 行 |
| C6 | agent_4f4f8373 | 13 条（**P1×4：loadPluginFromJar 失败点在 try 外 ClassLoader+JAR 句柄线性泄漏、插件状态机永久卡死 DEACTIVATING/ACTIVATING 无恢复 API、config source 先调度后加载初始失败泄漏永久 globalTimer 任务、ShellRunner 非 timeout 异常不销毁子进程**；P2×8、P3×1）+ CL-010~013（CL-013 CoreInitialization.registerCleanup 移交 nop-kernel）+ 9 项配对成立面 |

### 初审汇总

**47 条发现（P0×0 / P1×10 / P2×26 / P3×11）+ 13 条 CL 线索（CL-001~013）**。跨单元已知重复：CL-008↔WI 2.1 C6-011（ShutdownHook）、CL-013 移交 nop-kernel。全部 4 维度含 P1 → Phase 3 全维度派第 2 轮深挖。

## 第 2~3 轮深挖（串行，2026-10-17）

| 批次 | 产出文件 | 发现 | 终止判定 |
|------|---------|------|---------|
| C1 r2 | C1-round2.md | +4（**P1：C1-017 配置发布协议字段面扩展 defaultValue 第三字段+两步写序撕裂+运行期第二写点**——C1-007 修复建议不完备；P2×2：unregisterNameIndex 三步非原子名字索引孤儿化、reloadPlugin remove→put 窗口双活定义；P3×1）+ CL-014/015 + CL-001 升格建议 | 新增 P1 → 派第 3 轮 |
| C1 r3（收口轮） | C1-round3.md | **C1-021（P1）：CL-001 终裁升格立案**（JdbcConfigSource.tryTriggerChange 在 try 外永久静默杀死 DB 配置热更新全链——修正第 2 轮回调链表述）+ CL-016（PlaceholderConfigReference 未替换占位符返回，api-core 移交）；7 写点×8 读站复核成立、跨定义门控 TOCTOU 随 C1-010 闭合 | 终止（C1-007/017 族闭合；C1 合计 21） |
| C2 r2 | C2-round2.md | +6（P2×3：依赖边不可见族扩 4 条新边（ignore-depends/force-lazy/构造参数 XPL/config 监听器重解析）、AOP 拦截器 bean 边不入拓扑图、LC⇄BIN 跨线程互等环；P3×3）+ CL-017/018；锁内用户代码站点表 20 行、依赖边 12 条全闭合 | 终止（C2 合计 17） |
| C4 r2 | C4-round2.md | +4（P2×3：完成回调族三形态吞错（Error 漏接→图孤儿→死容器半可用）、restart 代际竞态自毁新生代、singleton 销毁序哈希序；P3×1：关闭序缺口不 join 在途图）+ CL-019/020（移交 nop-kernel）+ globalTimer 消费方 7 站点终表 + 跨线程交叉创建死锁假设证伪 | 终止（C4 合计 11） |
| C6 r2 | C6-round2.md | +1（P2 条件性：ShellRunner 进程创建与内层 try 间失败窗口——修复必须置于外层 try finally，对 C6-004 修复方案实质约束）+ CL-021（OsUserHelper 明文口令入进程参数——移交安全线）；10 项失败点矩阵、「先调度后加载」族穷举仅 2 站点、双轨回滚扑空候选否证 | 终止（C6 合计 14） |

### Phase 3 收敛汇总

- 发现总量：C1 21 + C2 17 + C4 11 + C6 14 = **63 条**（初审 47 + 深挖 16；P1 10→11（C1-021 升格立案）；深挖新增 P1×2：C1-017/C1-021）
- 维度集外线索累计 **21 条（CL-001~021）**；跨单元移交：CL-008/013/019/020 移交 nop-kernel、CL-021 移交安全线
- 全部 4 维度显式终止
