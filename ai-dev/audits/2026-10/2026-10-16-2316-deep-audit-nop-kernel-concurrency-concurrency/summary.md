# 深度审核汇总报告 — nop-kernel 并发内核本体并发·异步·资源专项审计（CRA WI 2.1）

## 基本信息

- **审核单元**: `nop-kernel-concurrency`（A 面 = nop-commons concurrent/cache/pool 三包 85 files/7,550 lines——平台并发设施自身实现；B 面 = nop-core/nop-xlang 全局模型缓存与注册表单例三层口径并集 46 候选 files；nop-xdefs 0 Java 自然空集）
- **审核日期**: 2026-10-16 ~ 2026-10-17
- **执行维度**: C1–C7 + C9 全维度（8 初审批次，本 campaign 最大维度集）+ C10 测试保护力（最后批次）；C8 不在维度集——CL overlay 单形态（CL 前缀 `nop-kernel-concurrency-CL-0NN`）
- **目标范围**: `src/main/java` 手写代码；基线 commit 8c13908bfd（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2468-cra-wi2.1-nop-kernel-concurrency.md`（两轮对抗性审查通过后执行：R1 2 Major + 5 Minor 全修、R2 4 Major（维度计数矛盾/C4 幻影类/api-core 越界锚点/B 面数量低估）+ 5 Minor 全修）
- **执行说明**: 全部批次串行派发（运营者要求）；**本单元是 campaign 唯一审计平台并发设施自身实现的工作项**——WI 1.1–1.7 五条预注面（BindScheduledExecutor 重排语义/globalWorker 30+10000/globalTimer core4-max5/DefaultThreadPoolExecutor 队列语义/SyncThreadPoolExecutor destroy no-op）从设施本体镜头重审；判级口径「设施被全平台并发调用，当前无生产调用方站点条件化降档」；Protected Area 处置：审计只读不受限，B 面（nop-core/nop-xlang）P0 修复的 plan-first 授权=本 plan 置 active（A 面 nop-commons 不在 Protected Area 列表，P0 即修）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 13（P1×1/P2×9/P3×3） | +4（P2×1/P3×3）+CL-017/018 | 17 | 2（C1-002/008→P3） | 0 |
| C2 | 2 | 15（P1×1/P2×10/P3×4） | +7（P2×3/P3×4）+CL-019/020 | 22 | 0 | 0 |
| C3 | 2 | 14（P1×1/P2×12/P3×1） | +6（P2×4/P3×2） | 20 | 0 | 0 |
| C4 | 2 | 10（P1×1/P2×5/P3×4） | +2（P2×2）+CL-023/024 | 12 | 0 | 0 |
| C5 | 2 | 7（P1×1/P2×3/P3×3） | +2（P2×1/P3×1）+CL-025~029 | 8 | 2（**C5-001 P1→P2** 热更新触发链生产接线不可达；C5-004→P3） | 1（C5-009 值持键前提证伪） |
| C6 | 2 | 8（P1×2/P2×5/P3×1） | +5（P2×4/P3×1）+CL-030/031 | 13 | 2（**C6-001/002 P1→P2**——C6-001 的 JDK shutdown() 语义主张经实测程序证伪；C6-002 可达性条件化） | 0 |
| C7 | 2 | 13（P1×2/P2×10/P3×1） | +4（P2×1/P3×3）+CL-032~034 | 17 | 1（**C7-001 P1→P2**——Bind 层 fixedRate 零生产调用方；JDK 并发重叠/静默死亡经实测证实） | 0 |
| C9 | 2 | 9（P2×2/P3×7） | +1（**C9-010**：4→6 处直接使用方绕过 stat 门）+CL-035/036 | 9 | 0 | 1（C9-006 putIfAbsent 原生覆写证伪） |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 14（缺保护登记，全 P2） | 0 | 0 |
| **合计** | — | **89（P0×0/P1×9/P2×56/P3×24）** | **+31 = 120** | **118（含 7 降级）+ 14 缺保护** | 5 | 2 |

全部 8 维度各 2 轮深挖后显式终止（无第 3 轮）；P1×9 全部出自初审，深挖零新增 P1；复核降 4 条 P1（9→5）。C10 复核 13 行抽验全维持。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 118 条保留缺陷 → 合并后 **96 条 = P1×5 / P2×63 / P3×28**（并入 22 条：必裁 12 组 + 扫描新增 7 项；最大并组 C3-008←4 CL 登记、C1-004←C2-005/C3-020、C4-005←C6-002/C1-015（GlobalExecutors 三镜头）、C7-003←C1-005/C3-017（ghost-cancel 三登记）、C7-014 拆分吸收进 C3-002/C6-001）；逐条 118 归属账经亲核。
- **P1 5 条零合并进出全部维持**：C1-001（RoundRobinSupplier resize use-after-close）、C2-001（ResourceCacheEntry 锁内加载嵌套死锁——复核补强三条嵌套路径+单线程循环 StackOverflow 新面）、C3-001（周期任务无统一异常绝缘层，29 处生产站点）、C4-001（globalTimer 死配置 setMaxPoolSize(0) 副作用丢弃+4 线程共享阻塞放大）、C7-007（check-changed 双漏检窗口+ResourceTimestampCache 死代码）。
- 关键复核事实修正：C6-001 初审「shutdown() 丢弃排队任务」被实测程序证伪（JDK 契约为排空执行；真凶是 shutdownNow 型池 + TaskQueue.force REE 泄漏）；C5-001 热更新链第一环（JavaCompileTool/JaninoClassLoader）生产调用方为 0；C7-001 JDK 非重叠契约不覆盖 Bind 层异步 command（实测 maxConcurrent=2）。
- CL 台账终态：编号 36 = 确认 35（含 11 条别名归并、4 条移交 nop-graph/nop-stream/rpc/api-core）+ 驳回 1（C1-CL-001 容量 0 自旋证伪——ArrayBlockingQueue 构造期 IAE fail-fast）。
- **api-core 移交包（5 项）**：C2-016（ContextTaskQueue.syncGet 丢失唤醒，计入 96 但通道移交）、C2-CL-019、C3-016 的 bindCancel 守卫根因半边、C3-007 的 executeWithThrottling 幻影释放半边、C3-005 的 completeAfterTask catch 宽度半边；协同改造点 `FutureHelper.bindCancel` 单点服务 C3-016/C6-001/C3-004。
- **跨单元映射表（15 行，Phase 8 批次对齐直接输入）**：WI 1.6 C8-001↔C3-001（P1 同批，设施契约先行）、WI 1.6 C8-001/WI 1.4 C4-003↔C7-001+C3-002、WI 1.5 MA-04/WI 1.6 CL-002/WI 1.2 C4-004↔C4-001/C4-005（globalTimer 隔离统一）、WI 1.7 C8-003↔C4-004、**WI 1.7 C4-003↔本单元 C4-003 同设施同面跨单元双登记（处置归本单元，WI 1.7 侧 cross-ref 关闭）**、WI 1.6 C8-012↔C3-016（bindCancel 单点改造）等；勘误：WI 1.6 C7-001 与本单元 C7-001 同名不同物零关联。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 5 | **C1-001** RoundRobinSupplier resize 锁外关闭旧数组尾段 use-after-close（Lettuce 生产链+IoC 刷新派发链可达）、**C2-001**（←C2-017 同变更集绑定）ResourceCacheEntry 持 entry 监视器加载模型→跨 entry 嵌套监视器死锁（循环依赖可配、双线程反序即死锁、单线程循环 StackOverflow）、**C3-001** 周期任务设施无统一异常绝缘层（29 处生产站点，30 点绝缘状态表产出）、**C4-001** globalTimer 死配置（newScheduledExecutor 副作用改写 maxPoolSize=0）+4 线程共享定时器阻塞放大（28 消费方分类终表）、**C7-007** 模型缓存 check-changed 双漏检窗口（collectDepends 时序固化旧内容+新 mtime；同 tick 双写盲；ResourceTimestampCache 死代码；与 C9-001/C9-010 同链三面绑定单点修复） |
| P2 | 63 | destroy/生命周期族（C4-002←C6-009/C1-002、C4-005←C6-002/C1-015、C6-003/004/005/010/011/012）、锁内重活族（C2-003/017/019、C2-001 关联）、参数护栏族（C4-003/004/006/011/012）、策略原子性族（C1-004←C2-005/C3-020、C1-003←C2-006、C7-015/016）、翻页/淘汰族（C5-002/003/008、C7-006←C2-010、C7-008/009）、热路径族（C9-001/004/010、C3-005）、单例槽位族（C1-009←C2-009、C1-017）、api-core 移交（C2-016）等 |
| P3 | 28 | C1-002/005/006←C2-015/C7-017/008/012/013/014/016/017、C2-011~014/018/020~022、C3-007/013/014/019/020、C4-007~010、C5-004~007、C6-008/013、C9-002/003/005~009 等 |

C10 另有缺保护登记 14 条（全 P2，C10-001~014）：保护力矩阵 **有钉 0 / 部分 7 / 无保护 111**（结构性：三模块 260 测试文件/1474 @Test，真并发断言仅 7 处且全部为历史修复轮相邻面护栏，与 118 条缺陷本体零重叠）；**P1 5/5 全部无保护**（C7-007 唯一集成测试 TestXplTask 类级 @Disabled）。测试套件自身缺陷注记 N1~N5（复核修正：N1 降格注释级误导，修复阻断预警仅 N2 一处成立——TestDefaultVirtualFileSystemRefresh 断言旧 zip 立即关闭与退役代际修复冲突）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 118 条保留发现：设施本体口径「新发现」为主，多处复核标注「已知未修复」（git 核实 HEAD 8c13908bfd 后设施文件零修复提交）。
- 已修复：0 条（本域）；CL-007 相关面（AsyncFetch exception 二次检查）系 WI 1.7 期间确认的历史修复 7defae0e69。
- 维度集外线索 36 条终态：确认 35（含 11 别名归并、4 移交 nop-graph/nop-stream/rpc/api-core）+ 驳回 1。

## 关键发现摘要（P1 全清单 5 条，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-001 | RoundRobinSupplier.resize 收缩路径锁外关闭旧数组尾段，并发 get 取得已关资源 | nop-commons pool 供给策略 |
| 2 | C2-001（C2-017 同变更集） | ResourceCacheEntry 持 entry 监视器执行模型加载，跨 entry 嵌套监视器循环依赖死锁 | nop-core 资源缓存 × xlang 编译链 |
| 3 | C3-001 | 周期任务设施无统一异常绝缘层：一次 Throwable 静默永久停摆（29 处生产站点） | nop-commons 调度设施 |
| 4 | C4-001 | globalTimer 死配置（副作用改写 maxPoolSize=0）+4 线程全平台共享定时器阻塞放大 | nop-commons 全局池 |
| 5 | C7-007 | 模型缓存 check-changed 双漏检窗口（旧内容+新 mtime 固化；ResourceTimestampCache 死代码） | nop-core 资源失效链 |

## 盲区自评

- `_gen/` 与 target/ 按纪律排除；B 面 46 候选中按「模型/元数据缓存」语义判据圈定（机械并集上界 46/5,945，各维度按子集审计）。
- C8 为维度集外（CL overlay 单形态 36 条确认 35）——若 7.1 需要 C8 完整口径需补审计（队列/背压形态集中在 OverflowBlockingQueue/DedupBlockingQueue/HighWatermarkSemaphore 族）。
- nop-api-core 为 Protected Area 另行裁定：本单元移交包 5 项（C2-016 等）不立案修复，留专项；FutureHelper/ContextProvider 本体未审。
- 5 条 P1 均经双向独立复核（其中 C6-001/C7-001 的降级与 C7-007 的维持均以实测程序/JDK 语义验证支撑）；未做长时并发复现实验（时序推演到行级+语义验证）。
- B 面 Registry 类 8+2、@GlobalInstance 单例、static 编译缓存全覆盖；xlang 编译链（LazyCompiledFunction/CompiledTag deps）经 C2 r2 补审。
- 修复耦合：C2-001+C2-017 同变更集（监视器内只做去重+占位不变式）；C5-001+C5-008 联合设计（weakKeys 路线无效）；C6-001 残余面+C6-009 同批（有界等待排空）；C7-007 checker 层修复同步收敛 C9-001/C9-010 性能面；FutureHelper.bindCancel 单点改造服务 C3-016/C6-001/C3-004；测试 N2 须与 C1-014 退役代际修复同变更集改写。

## 与其他 roadmap 的重叠

- 与 WI 1.1–1.7 台账逐条对账：**1 处同设施同面跨单元双登记**（WI 1.7 C4-003↔本单元 C4-003——处置归本单元，WI 1.7 侧 cross-ref 关闭）；其余 15 行映射表为「使用方↔设施本体」两面关系（非重复立项），Phase 8 批次对齐。
- security-audit-roadmap：JsonWhitelist 前缀 bug（C5-CL-025 主登记，转功能/安全域）已登记移交。
- full-code-review-roadmap：plan 2448（EhcacheCache putIfAbsent）与本单元 C9-006 驳回相关（审计前批次已修）。
