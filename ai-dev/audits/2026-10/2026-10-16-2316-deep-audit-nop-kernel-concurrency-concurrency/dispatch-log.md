# Dispatch Log — nop-kernel-concurrency audit (plan 2468)

- 主 agent：ZCode 会话 sess_21ba4274（分支 concurrency-audit-remediation，worktree nop-entropy-concurrency-audit）
- 审计对象：A 面 = nop-commons concurrent/cache/pool 三包（85 files/7,550 行，平台并发设施自身实现）；B 面 = nop-core/nop-xlang 全局模型缓存与注册表单例（三层口径并集候选 46 files/5,945 行——机械口径实测，Phase 1 按②层语义判据过滤后回填）
- 方法论：ai-dev/skills/concurrency-resource-audit-prompt.md；维度集合 C1–C7 + C9 全维度（8 初审批次）+ C10 最后批次（roadmap 2.1 裁定）；C8 不在维度集——CL overlay 单形态，CL 前缀 `nop-kernel-concurrency-CL-0NN`
- 基线：_tmp/cra-baseline-nop-kernel-concurrency.txt（18 节：A 面附录 C 命令 + C7/C9 补充 + C8 维度集外节 + B 面三层枚举节 + 行数对账）
- 计数核实：A 面 85/7,550 与 plan 2468 一致；B 面候选 46/5,945（R2 实测口径 ≈38/~6,900 的机械并集上界）
- Special：本单元 WI 1.1–1.7 五条预注面（BindScheduledExecutor 重排语义/globalWorker 30+10000/globalTimer core4-max5/DefaultThreadPoolExecutor 队列语义/SyncThreadPoolExecutor destroy no-op）从设施本体镜头重审，使用方结论不预设；FutureHelper/ContextProvider 温床注记位于 nop-api-core（越界）仅对照移交

## 盲审纪律（每份派发 prompt 均含）

> 【盲审纪律】禁止读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下的任何文件（含审计输出目录中其他维度的文件）。只审计当前 live code。例外：深挖轮次可读本维度前序轮次文件（必要输入）。

## 第 1 轮初审（串行 8 批次，2026-10-16/17）

| 批次 | 产出文件 | 派发 agent |
|------|---------|-----------|
| C1 共享可变状态 | C1-shared-mutable-state.md | （见下结果表） |
| C2 锁与同步 | C2-lock-sync.md | 同上 |
| C3 异步链路 | C3-async-chains.md | 同上 |
| C4 线程池治理 | C4-thread-pools.md | 同上 |
| C5 内存有界性 | C5-memory-bounds.md | 同上 |
| C6 资源生命周期 | C6-resource-lifecycle.md | 同上 |
| C7 长时运行 | C7-long-running.md | 同上 |
| C9 请求热路径 | C9-hot-path.md | 同上 |

## 第 1 轮初审结果（2026-10-16/17）

| 批次 | agent | 发现 |
|------|-------|------|
| C1 | agent_f2f09c73 | 13 条（**P1×1：RoundRobinSupplier.resize 锁外关闭旧数组尾段 use-after-close**；P2×9、P3×3）+ CL-001~003 + 维度外备注 1（JsonWhitelist 前缀 substring bug 转交安全/功能域） |
| C2 | agent_6aac35c0 | 15 条（**P1×1：ResourceCacheEntry.getObject 持 entry 监视器加载模型→跨 entry 嵌套监视器循环依赖死锁**；P2×10、P3×4）+ CL-004~006（CL-004/006 与 C1 轮 CL-002/003 同位点，留裁定）+ 4 条条目外观察 |
| C3 | agent_59761aa4 | 14 条（**P1×1：周期任务设施无统一异常绝缘层，26 处调用点**；P2×12、P3×1）+ 0 CL（**CL-005 升格立案**为 C3-002/003；C3-008 与 C1-CL-002/C2-CL-004 同位点） |
| C4 | agent_8e19fcd7 | 10 条（**P1×1：globalTimer 死配置（newScheduledExecutor 副作用改写 maxPoolSize=0 丢弃）+4 线程共享阻塞放大**；P2×5、P3×4）+ CL-007（**纠偏：TaskQueue 实为 Tomcat 式弹性扩容，实现正确**——真问题是过载信号被深队列吸收）+ B 面零线程载体 |
| C5 | agent_ac4dc94b | 7 条（**P1×1：XLangSemantics 三静态 CHM 无界强引用 Class 钉死 classloader**；P2×3、P3×3）+ CL-008~010 + B 面缓存三列判定表（25 项：无界 3/有界 16/断链 3/度量错配 1 等） |
| C6 | agent_bcb97880 | 8 条（**P1×2：destroy 仅 shutdown→ExecutorHelper.submit wrapper future 永不终态、GlobalExecutors.register 覆盖即毁借出引用+unregister 零调用方**；P2×5、P3×1）+ CL-011 + 6 项配对良好正面样本 |
| C7 | agent_6c26386e | 13 条（**P1×2：BindScheduledExecutor fixedRate 任务超间隔→并发重叠+积压单调增长+队列满静默死亡、模型缓存 check-changed 双漏检窗口+ResourceTimestampCache 死代码**；P2×10、P3×1）+ CL-012/013 + 单调增长源 24 项/挂死点 14 项/时钟站点 9 项清单 |
| C9 | agent_e201d9a9 | 9 条（P2×2：模型缓存命中路径每次全依赖图遍历+裸 stat（NFS 场景 P1 升级条件）、生成代码静态调用每次 Class.forName；P3×7）+ CL-014~016 + 11 站点串行化点清单 |

### 初审汇总

**89 条发现（P0×0 / P1×9 / P2×56 / P3×24）+ 16 条 CL 线索（CL-001~016）**。跨维度已知重复位点：SequentialTaskExecutor.execute 忽略 offer（C1-CL-002/C2-CL-004/C3-008/C7-CL-012 四登记）、BlockingSourceConsumeTask（C1-CL-003/C2-CL-006/C3-011/C7-012 四登记）、OverflowBlockingQueue 容量 0（C1-CL-001/C5-CL-008/C9-CL-014 三登记）、FileWatcher（C5-004/C7-013）——Phase 6 合并裁定消解。全部 8 维度含 P1 或 ≥3 条 P2/P3 且无终止信号 → Phase 3 全维度派第 2 轮深挖。

## 第 2 轮深挖（串行，2026-10-17）

| 批次 | 产出文件 | 发现 | 终止判定 |
|------|---------|------|---------|
| C1 r2 | C1-round2.md | +4（P2：DefaultVirtualFileSystem.refresh 在飞读者 zip-closed；P3×3：register 锁外 destroy、updateThreadPool 五步非原子、实例级可替换协作者槽位族）+ CL-017/018；家族矩阵 16 行、单例写点全量定性（14 处）、check-then-act 12 类清零 | 终止（C1 合计 17） |
| C2 r2 | C2-round2.md | +7（P2×3：ContextTaskQueue syncGet 丢失唤醒（api-core 边界面）、LazyCompiledFunction 锁内完整编译+E4/E5 新环、XLangSemantics CLASS_MODELS bin 锁内类加载；P3×4）+ CL-019/020；锁内重活站点表、锁序环修正为 E1~E5 | 终止（C2 合计 22） |
| C3 r2 | C3-round2.md | +6（P2×4：**30 处周期调用点绝缘状态表**+语义澄清「平台不存在续链周期设施」、ExecutorHelper.submit 正常完成路径必然误 cancel、scheduleWithRandomDelay 取消窗口、DefaultTaskExecutionQueue bindCancel 参数反接；P3×2）+ CL-021/022 | 终止（C3 合计 20） |
| C4 r2 | C4-round2.md | +2（P2×2：ThreadPoolConfig 死旋钮族 8 项合计、nop-virtual-thread 假参数实体+误导开关）+ CL-023/024；globalTimer 28 消费方分类终表（纠偏 CLI 族 5 站点实为直连）、19 创建点参数纪律矩阵、core>max 唯一可达路径 | 终止（C4 合计 12） |
| C5 r2 | C5-round2.md | +2（P2：**ReflectionManager 弱键族失效**——值持键 weakKeys 永不触发、rawTypeCache 事实无界（第 1 轮判定表 row2 修正）；P3：EnumDictLoader 弱键失效）+ CL-025~029；补判定表 #26~#46、LocalCache 配置终表 17 生产站点 | 终止（C5 合计 9；C5-008 与 C5-001 修复须联合设计） |
| C6 r2 | C6-round2.md | +5（P2×4：池配置 waitFor/awaitTermination 旋钮结构性死亡、ICoreInitializer 复用构造期 Cancellable「注册即撤销」、ShutdownHook CAS 反转、租户模型缓存清理双断；P3×1）+ CL-030/031（CL-029 并入 C6-011）；S1~S12 destroy 挂死站点表 | 终止（C6 合计 13） |
| C7 r2 | C7-round2.md | +4（P2：IRateLimiter 等待不可中断（H11 判定修正）；P3×3：shutdown 窗口 future 永不完成、HighWatermarkSemaphore 超卖窗口、ReactiveObjectMap 失败粘滞）+ CL-032~034；时钟终表 24 站点、M1~M24 抽验 8/8 一致、双调度器双负载对照表 | 终止（C7 合计 17） |
| C9 r2 | C9-round2.md | +1（**P2：4 处生产直接使用方绕过 refreshMinInterval 门**——GatewayHttpFilter 硬编码 true 每 HTTP 请求 stat、DefaultDataAuthChecker 每 EQL 查询、XplLibTagCompiler 默认 reloadable、DynOrmModelProvider）+ CL-035/036（JsonWhitelist 前缀 bug 与 C1 轮备注重复登记）；E1~E10 入口穷举、热路径常驻开销终表 17 行 | 终止（C9 合计 10） |

### Phase 3 收敛汇总

- 发现总量：C1 17 + C2 22 + C3 20 + C4 12 + C5 9 + C6 13 + C7 17 + C9 10 = **120 条**（初审 89 + 深挖 31；P1×9 全部初审产出；深挖无新增 P1）
- 维度集外线索累计 **36 条（CL-001~036）**，待 Phase 4 复核确认/驳回；跨维度重复位点在初审 4 组基础上新增：JsonWhitelist 前缀 bug（C1 备注/C5-CL-025/C9-CL-036 三登记）、debounce 非原子（C1-004/C2-005/C3-020/C7-CL-033 四登记）、ShutdownHook（C5-CL-029/C6-011 并入）
- 全部 8 维度显式终止（各 2 轮），无第 3 轮需求
