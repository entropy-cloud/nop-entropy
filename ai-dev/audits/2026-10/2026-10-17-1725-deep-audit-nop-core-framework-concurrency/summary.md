# 深度审核汇总报告 — nop-core-framework + nop-utils 并发·异步·资源专项审计（CRA WI 2.2）

## 基本信息

- **审核单元**: `nop-core-framework`（六子模块 193 files/18,319 lines：ioc 99f/9,524、config 40f/3,541、plugin 26f/3,732、security 12f/630、log 11f/528、boot 5f/364）+ `nop-utils`（115 files/11,003 lines），合计 308 files/29,322 lines（手写口径不含 `_` 前缀与 target/）
- **审核日期**: 2026-10-17
- **执行维度**: C1/C2/C4/C6 全维度（4 初审批次）+ C10 测试保护力（最后批次）；C3/C5/C7/C8/C9 维度集外 overlay 五形态（CL 前缀 `nop-core-framework-CL-0NN`）
- **目标范围**: `src/main/java` 手写代码；基线 commit 6dceb20dc3（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2469-cra-wi2.2-nop-core-framework.md`（两轮对抗性审查通过后执行：R1 1 Blocker（三处幻影锚点——Security 会话缓存/Log 异步 appender/IConfigRefreshable api-core 越界均以实测锚点重写）+ 4 Major + 4 Minor 全修；R2 3 Major + 3 Minor 全修）
- **执行说明**: 全部批次串行派发（运营者要求）；判级口径「框架层被全平台并发使用，仅启动期单线程触达站点条件化降档」；R1-M3 接线的 Phase 1 锚点核实回填门（R2 全量实测逐字命中）执行完毕；nop-core-framework 不在 AGENTS.md Protected Areas 表——P0 即修口径。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 3（r3 收口） | 16（P1×3/P2×9/P3×4） | +5（**C1-017 P1**、C1-018/019 P2、C1-020 P3、**C1-021 P1**（CL-001 终裁升格））+CL-014~016 | 19 | 0 | 2（C1-003 handler 快速通道不可达、C1-013 ++ 实际锁保护） |
| C2 | 2 | 11（P1×2/P2×6/P3×3） | +6（P2×3/P3×3）+CL-017/018 | 15 | 0 | 2（C2-014 LC⇄BIN 环第二腿不存在、C2-015 与 C1-003 同口径） |
| C4 | 2 | 7（P1×1 门控/P2×3/P3×3） | +4（P2×3/P3×1）+CL-019/020 | 11 | 0 | 0 |
| C6 | 2 | 13（P1×4/P2×8/P3×1） | +1（P2 条件性）+CL-021 | 14 | 0 | 0 |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 18（缺保护登记：P2×17+P3×1） | 0 | 0 |
| **合计** | — | **47（P0×0/P1×10/P2×26/P3×11）** | **+16 = 63（P1 升格 +1 → 11）** | **59 + 18 缺保护** | 0 | 4 |

C1 派 3 轮（r2 新增 C1-017 P1 → 按 campaign 继续规则必派 r3 收口轮；C1-021 为 CL-001 终裁升格非新探索面，族闭合终止）；C2/C4/C6 各 2 轮终止。C10 复核 15 行抽验 0 推翻。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 59 条保留缺陷 → 合并后 **45 条 = P0×0 / P1×11 / P2×21 / P3×13**（并入 14：复核已裁 6 组台账登记 + 本裁定新裁 8 组经 live code 亲核——**C1-009←C4-002**（JdbcConfigSource 同位点同交错）、**C1-010←C2-004**（check-then-act 窗口镜像交错）、**C2-002←C1-002**（跨维度同缺陷 wait/notify 环）、**C2-001←C2-012**（collectDepends 不可见边同根因）、**C4-003←C4-008**（whenComplete 汇聚点三形态）、**C6-004←C6-014**（同根因同修复 diff）等）；逐条 59 归属账。
- **P1×11 零判级变动**：C1-007（族案含 C1-017）、C1-009（新并 C4-002）、C1-010（含 011/012 并案，新并 C2-004）、C1-021、C2-001（新并 C2-012，allow-cycle 默认 true 附注）、C2-002（新并 C1-002）、C4-003（门控终裁维持——docs/performance 明文推荐开启+三重文档债）、C6-001/002/003/004（C6-004 新并 C6-014）。
- 产出 **7 个同族相邻修复批次协同表**（插件生命周期/IoC 启动图/配置刷新链/JdbcConfigSource/ShellRunner/VTE 治理/销毁链）——Phase 8 批次对齐直接输入。
- CL 台账终态：编号 21 = 升格 1（CL-001→C1-021）+ 落格 2（CL-002→C2-005、CL-018→C4-010）+ 同位点并入设施 2（CL-008/020）+ 零立案归档 1（CL-009 PluginManager syncGet 零线程创建终判）+ 关联保留 8 + 纯移交 7（nop-kernel 2、安全线 1、api-core 1、功能线 1、C5/C7 各归 1）。
- **跨单元映射表 11 行**：①C1-007 族案↔WI 2.1 C1-001（刷新链上下游，回归共享不双改）；②C1-021↔WI 2.1 C3-001（设施绝缘层吸收 try-catch 语义）；③C4-003 族案+C4-009/011+CL-019 同 PR 批次（join/失败传导/超时/代际令牌一体）；④⑤CL-008/CL-020=WI 2.1 C6-011/C6-003 同位点；⑥⑦C6-010 三重登记+Jdk21CommonsCoreInitializer 注册链为 WI 2.1 C6-002 可达性终审输入；⑧C4-001↔WI 2.1 C4-001 同设施两面；⑨C1-015↔WI 2.1 CL-018；⑩CL-013 为 WI 2.1 未登记新面；⑪C1-007 回归面跨单元必测清单（OrmMigrationWriteGate 等）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 11 | **C1-007**（族案含 C1-017）配置发布协议三字段裸写裸读+两步写序撕裂（api-core 单一不可变 holder 修复，7 写点×8 读站）、**C1-009**（←C4-002）JdbcConfigSource close 竞态连接泄漏、**C1-010**（含 011/012 并案，←C2-004）插件 unload×reconcile 交错活容器泄漏（lifecycleLock 全迁移统一修复）、**C1-021** JdbcConfigSource.tryTriggerChange 在 try 外永久静默杀死 DB 配置热更新（兄弟类保护不对称）、**C2-001**（←C2-012）getBean0 锁序环依赖边不可见（allow-cycle 默认 true 附注；历史 check 审计 P2-6 同源回访）、**C2-002**（←C1-002）ProducedBeanInstance 跨实例 monitor 等待环 wait 无超时、**C4-003**（←C4-008，门控）concurrent-start 三复合面：启动 bean 占 globalWorker+框架无 join 点+死容器 exit-0 伪装成功/quarkus 三绿假活、**C6-001** loadPluginFromJar 失败点 try 外 ClassLoader+JAR 句柄线性泄漏、**C6-002** 插件状态机永久卡死 DEACTIVATING/ACTIVATING 无恢复 API、**C6-003** config source 先调度后加载初始失败泄漏永久 globalTimer 任务（启动期降档不适用论证成立）、**C6-004**（←C6-014）ShellRunner 非 timeout 异常不销毁子进程+stderr 线程 pin（修复须置于外层 try finally） |
| P2 | 21 | 插件注册表族（C1-018/019、C2-013 AOP 拦截器边不入图）、配置面（C1-008 代际撕裂、C2-007/008、C4-007 单线程搁浅）、容器生命周期（C1-001←C2-003、C1-004/005/012/014、C2-009 Error 滞留、C4-009 restart 代际、C4-010←C6-006 销毁序哈希序、C6-007/008/009/011/012）、载体治理（C4-001 globalTimer 直连、C4-002（已并）、C4-010 VTE 治理 P3 相邻、C6-010 三重登记、C6-014 条件性）等 |
| P3 | 13 | C1-006←C2-011、C1-015/016/020、C2-006/015（驳回残余并入 C2-017 契约面）、C2-016/017、C4-004/005/006/011、C6-013 等 |

C10 另有缺保护登记 18 条（P2×17+P3×1）：保护力矩阵 **有钉 0 / 部分 20 / 无保护 38**（58 行）；**P1×11 = 有钉 0 + 部分 4 + 无保护 7**（C1-009/C1-021/C2-001/C6-001/002/003/004 零保护）。测试资产 152 测试文件/394 @Test，审计面真多线程测试仅 3 个；nop-commons-java21 整模块无 src/test。测试套件自身缺陷注记 N1~N7（复核 7/7 属实——N1 tearDown 丢弃 future 吸收卡死症状、N2 钉死 unload 失败不关 classLoader 与 C6-001 修复设计冲突、N5 testAsyncStartOrder 造成并发启动已覆盖错觉）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 59 条保留发现全部「新发现」为主（C2-001 为「已知未修复」——历史 check 审计 P2-6 暂缓项同源回访，判级张力留合并阶段已裁定 P1）；git log 核实无修复提交。
- 已修复：0 条（本域）。
- 维度集外线索 21 条终态：升格 1 + 落格 2 + 同位点并入 2 + 零立案 1 + 关联保留 8 + 纯移交 7。

## 关键发现摘要（P1 全清单 11 条，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-007（族案含 C1-017） | 配置发布协议三字段裸写裸读+两步写序撕裂，刷新值可见性无界 | nop-config × api-core |
| 2 | C1-009（←C4-002） | JdbcConfigSource.close 竞态：停机后在途刷新重建永不关闭的连接 | nop-config 调度源 |
| 3 | C1-010（含 011/012，←C2-004） | 插件 unload×reconcile 交错：state=UNLOADED 但子容器已 start，活容器永久泄漏 | nop-plugin 状态机 |
| 4 | C1-021 | tryTriggerChange 在 try 外：回调异常永久静默杀死 DB 配置热更新全链 | nop-config 刷新调度 |
| 5 | C2-001（←C2-012） | getBean0 锁序环：依赖边对拓扑图不可见，跨线程永久死锁 | nop-ioc 容器 |
| 6 | C2-002（←C1-002） | ProducedBeanInstance 跨实例 monitor 等待环：init 交叉获取+wait 无超时 | nop-ioc bean 创建 |
| 7 | C4-003（←C4-008，门控） | concurrent-start 三复合面：无 join 点+失败吞入+死容器 exit-0 伪装/quarkus 三绿假活 | nop-ioc 启动图 |
| 8 | C6-001 | loadPluginFromJar 失败点 try 外：ClassLoader+JAR 句柄线性泄漏 | nop-plugin 类加载 |
| 9 | C6-002 | 插件状态机永久卡死 DEACTIVATING/ACTIVATING，无恢复 API | nop-plugin 生命周期 |
| 10 | C6-003 | config source 先调度后加载：初始失败泄漏永久 globalTimer 刷新任务（jdbc 变体滞留连接） | nop-config 生命周期 |
| 11 | C6-004（←C6-014） | ShellRunner 非 timeout 异常不销毁子进程+stderr 线程 pin（修复须外层 try finally） | nop-utils 进程管理 |

## 盲区自评

- `_gen/` 与 target/ 按纪律排除；C1 轮口径备注（nop-ioc 含测试面 135f vs src/main 99f）留档。
- C3/C5/C7/C8/C9 为维度集外 overlay 五形态（确认线索见 CL 台账）——若 7.1 需要 C3（异步链）/C5（内存有界）完整口径需补审计（IoC 异步启动链的 C3 面受 C4-003 族案牵连最大）。
- 11 条 P1 均经双向独立复核（C4-003 的 P1/P2 张力已在复核结论双口径完整呈现，7.1 协调方可裁）；C2-001 与历史 check 审计 P2-6 的判级张力已在本轮裁定 P1 并留档。
- api-core 移交面（IConfigRefreshable/AppConfig/FutureHelper/PlaceholderConfigReference CL-016）不立案，留专项；nop-ioc DSL 语义正确性属质量域未审。
- 修复耦合：C1-007 族案修复在 api-core（单一不可变 holder）一次覆盖 7 写点×8 读站；插件生命周期统一批次（C1-010 并案+lifecycleLock 全迁移+reconcileLock 序列化）；IoC 启动图批次（C4-003 族案+C4-009/011+CL-019 join/失败传导/超时/代际令牌一体）；测试 N2 须与 C6-001 修复设计冲突消解。

## 与其他 roadmap 的重叠

- 与 WI 1.1–2.1 台账对账：**零跨单元重复立项**（CL-008/CL-020 为同位点主处置在 WI 2.1；C6-010 三重登记设施归 WI 2.1）；跨单元映射表 11 行为两面关系与批次对齐。
- full-code-review roadmap：C2-001 与历史 check 审计 P2-6 同源（本轮升 P1 裁定留档）。
- 安全线：CL-021（OsUserHelper 明文口令入进程参数）移交。
