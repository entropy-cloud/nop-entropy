# merge-adjudication.md — nop-kernel-concurrency 跨维度合并裁定（Phase 6）

> 裁定人：跨维度合并裁定子代理（与 8 维初审/复核代理相互独立，以证据为准）。
> 裁定日期：2026-10-17。基线：分支 `concurrency-audit-remediation`，HEAD `8c13908bfd`（八维复核一致确认其后零代码提交）。
> 本文件是 summary.md 归档与 cra-index.md 台账回填的直接输入；不修改 8 维度文件与 C10 文件。

## 〇、输入口径

- **八维复核后保留发现 118 条**：C1×17、C2×22、C3×20、C4×12、C5×8、C6×13、C7×17、C9×9；判级分布 **P1×5（C1-001/C2-001/C3-001/C4-001/C7-007）/ P2×75 / P3×38**（与 C10 §6 逐文件对账一致）。
- **驳回 2 条**（复核轮已裁定，本裁定不变动）：C5-009（EnumDictLoader 值持键证伪）、C9-006（putIfAbsent synchronized 站点证伪）。
- **维度集外线索 CL 编号 36 条**（C1-CL-001~018、C2-CL-004~020、C3-CL-021~022、C4-CL-007/023/024、C5-CL-008~029、C6-CL-011/030/031、C7-CL-012~034、C9-CL-014~016/035/036），处置按各维度复核 + 本裁定去重合并，见 §五。
- **C10-test-protection.md**（缺保护 C10-001~014、测试注记 N1~N5）不参与合并裁定主体，仅作家族归组引用（见 §三末）。
- **跨单元台账**：`ai-dev/audits/cra-index.md`（WI 1.1–1.7 P1 台账 + CL 台账），跨单元映射见 §四（本单元重点交付）。
- **三态判定规则**：同一缺陷（同代码位点 + 同根因 + 同后果 → 合并保留最高严重度，登记主 ID 与被并 ID）；同族相邻（互列关联、绑定同变更集/同批次，不并 ID）；不同缺陷（各自独立）。
- **证据核验方式**：必裁交叉项全部回读两轮原文条目（file:line、触发时序、失效面枚举）逐对裁定；存疑处（C1-009 站点表、C3-002/C7-014 同位点、C4-005/C6-002 同根因、C3-006/C7-010 同位点、C4-009/C3-010 同根因、C7-003/C1-005/C3-017 三登记）已亲核原文坐实。

---

## 一、必裁交叉项裁定表（12 组，逐一三态）

### M1. SequentialTaskExecutor.execute 忽略 offer —— C1-CL-002 / C2-CL-004 / C3-008 / C7-CL-012（四登记，C5-CL-009 为第五登记）

| 判定 | 说明 |
|---|---|
| **同一缺陷** | 同位点 `SequentialTaskExecutor.execute:81`（offer 返回值丢弃且无日志）、同根因（忽略 OverflowBlockingQueue.offer 直通语义的 false）、同后果（满队列静默丢弃任务；全仓零生产调用方，潜伏） |
| **归并** | **主处置项 = C3-008**（唯一立案条目，P2）；CL 别名 = C1-CL-002、C2-CL-004、C5-CL-009、C7-CL-012 五登记全部并入该台账 |
| 计数影响 | 0（被并者均为 CL 线索，本不在 118 内） |

### M2. BlockingSourceConsumeTask —— C1-CL-003 / C2-CL-006 / C3-011 / C7-012（逐条三态）

| 配对 | 判定 | 依据 |
|---|---|---|
| C1-CL-003 ↔ C2-CL-006 | **同一缺陷（CL 别名）** | 同位点同面：drainTo 出队后 `consumer.acceptList` 失败仅 LOG.error，无重试/死信/重排队 → 整批静默丢弃 |
| C3-011 ↔ C7-012 | **同一缺陷** | 同位点 `BlockingSourceConsumeTask.run:48-76` + `AbstractBatchProcessService.start:58-60`；同根因（drainTo 中断路径 `throw NopException.adapt` 退出循环 + catch(Exception) 漏 Error + submit future 丢弃无监督）；同后果（消费线程永久减员且不可感知，唯一子类 AuditServiceImpl 审计写路径连锁挂死） |
| 整批丢弃面 ↔ 线程死亡面 | **同族相邻（同文件不同缺陷面）** | 「消息丢但循环活」（CL 面）与「循环死」（C3-011/C7-012 本体）机制不同；CL 面随同文件修复批一并覆盖 |
| **归并** | **主 = C3-011**（P2，覆盖中断残留 + Error 两机制 + future 丢弃，覆盖面最全）；**被并 = C7-012**（P2）；CL 别名 = C1-CL-003 = C2-CL-006（整批丢弃面台账，随 C3-011/C3-012 同文件批处置）。C7-012 的「无日志」细节修正（ExecutorHelper 包装有 LOG.error）随并入记录 | 计数影响：**−1** |

### M3. OverflowBlockingQueue 容量 0 —— C1-CL-001（已驳回）/ C5-CL-008 / C9-CL-014

| 判定 | 说明 |
|---|---|
| C1-CL-001 | **驳回维持**：`new OverflowBlockingQueue<>(0, DROP_ELDEST)` 委托 `ArrayBlockingQueue(0)` 构造即 IAE fail-fast，「容量 0 无限自旋」前提证伪 |
| C5-CL-008 | **族主登记**（确认）：DROP_NEWEST 黑洞 / BLOCK_WAIT 挂死 / FAIL 良性 + 4-arg 失配构造可达性 |
| C9-CL-014 | **并入 C5-CL-008 作残余子面**：多生产者满载「有界自旋无退避」与族主登记相容，不独立维持 |
| 相邻立案条目 | **C5-007**（P3，容量 0 跨设施语义分裂：TaskQueue(0) IAE / newSingleThreadTimer(0) / ThreadPoolConfig 无校验）= 同族相邻，保留独立；其 **ThreadPoolConfig 无校验面与 C4-003 同位点**，处置并入 C4-003 同 diff（该面去重注记）；C2-011（DROP_ELDEST 两段内锁非原子）同设施另一面，独立保留 |
| 计数影响 | 0（CL 级） |

### M4. FileWatcher —— C5-004 / C7-013 / C7-CL-013

| 判定 | 说明 |
|---|---|
| **编号勘误** | 终表 **C7-013 实为 StandardThreadPoolExecutor 墙钟线程更新延迟（P3），并非 FileWatcher 登记**——任务指令中「C7-013」系 C7-CL-013 之笔误，裁定按 CL-013 对账 |
| C5-004 ↔ C7-CL-013 | **同一缺陷（CL 别名）**：`FileWatcher.queue:24/31` 无界事件队列 + debounce 风暴无限推迟 + 处理任务落 globalTimer（仅 nop-cli 开发态构造）；CL-013 附加 lost-wakeup F3 观察（processChangeEvent processing 标志窗）随 C5-004 修复一并处理 |
| 相邻 | C6-006（文件监关注册表不对称）= 同设施不同机制面，独立保留、同批修复；C7-CL-033（debounce 风暴推迟幂等面）随 debounce 组 |
| **归并** | 主 = C5-004（P3）；别名 = C7-CL-013。计数影响：0 |

### M5. debounce 非原子 —— C1-004 / C2-005 / C3-020 / C7-CL-033

| 判定 | 说明 |
|---|---|
| **同一缺陷** | 三立案同位点 `RateLimitExecutorImpl.debounce:50-67`（+replace:70-72）；同根因（get-cancel-schedule-put 四段非原子 + holder[0] 不安全发布）；同后果（并发同 key 重复执行、CHM.remove(key,null) NPE 被吞、陈旧条目） |
| **归并** | **主 = C1-004**（P2，生产调用方在用：FileWatcher/ZipFileWatcher/CliWatchZipCommand）；**被并 = C2-005（P2）、C3-020（P3，机制修正：NPE 非返回 false）**。修复对齐 throttle 的 compute 原子先例 |
| 相邻 | **C5-005**（P3，cancel 后 schedule 抛出条目滞留；主泄漏已修 commit 22129fa9f7）= 同族相邻，**同 diff 修复**（compute 原子化 + schedule 失败回滚）；C7-CL-033 = throttle-compute 对照注记 |
| 计数影响 | **−2** |

### M6. JsonWhitelist 前缀 bug —— C1 备注 / C5-CL-025 / C9-CL-036

| 判定 | 说明 |
|---|---|
| **同一缺陷（三登记合并）+ 转域单列** | 同位点 `JsonWhitelist.java:36-55`（`whitelistPrefixes.add` 于 :43，`substring(length-1)` 取末字符 "*"，contains 的 startsWith 恒 false；fail-closed）；**功能性缺陷非并发** |
| **归属裁定** | **转功能/安全域单列移交**，不入本单元 118/96 并发台账、不入 Phase 8 并发批次；主登记 = **C5-CL-025**，C1 维度外备注与 C9-CL-036（行号 :21-27→:36-55 修正）为别名；`addResource` 零调用 = 潜伏。移交目标：功能正确性/安全审计域台账 |
| 计数影响 | 0（CL 级） |

### M7. BindScheduledExecutor / 自续链调度器族 —— C3-002/003/004、C7-001、C7-014、C2-CL-005（同设施多缺陷面逐条三态）

| 配对 | 判定 | 依据 |
|---|---|---|
| C3-003 ↔ C7-001 第三段 | **同一缺陷** | 同位点 `BindScheduledExecutor.scheduleAtFixedRate:64-69` 直通委托；同根因（executor.execute 拒绝 REE 逃出 timer 线程 wrapper lambda，JDK 抑制后续全部触发）；同后果（周期链静默永久死亡、返回 Future 无人消费）。C7-001 为复合条目（①重叠 ②积压 ③拒绝静默死亡） |
| **归并 A** | **主 = C7-001**（P2 条件化，三段复合 + JDK 26 实证 + Bind 层 fixedRate 零生产调用方）；**被并 = C3-003**（P2，拒绝杀链面 = 其第三段；CL-005 直系升格谱系与 WI 1.6 C8-001 平台统一修复锚点随主条保留）；CL 别名 = C2-CL-005（前身）。计数影响：**−1** |
| C3-002 ↔ C7-014(a) | **同一缺陷** | 同位点 `BindScheduledExecutor:107`（worker lambda 内重排调用 `timer.schedule` 无 try/catch 覆盖，timer destroy/shutdown 时抛 ERR_SERVICE_NOT_ACTIVE/REE → 链死 + future 永不完成） |
| C6-001 ↔ C7-014(b) | **同一缺陷** | C6-001 收窄口径本体 = ExecutorHelper.submit/schedule wrapper（完成点在任务体内）× DefaultScheduledExecutor/Bind destroy→shutdownNow 丢弃排队任务 → wrapper future 永不终态化；C7-014(b) = 同机制（pending 被 shutdownNow 取消 + **bindCancel 单向** FutureHelper:313-318 无反向传播） |
| **归并 B（拆分吸收）** | **C7-014 整条拆分归并，无独立残留**：facet(a) → 并入 **C3-002**（主，P2）；facet(b) → 并入 **C6-001**（主，P2 终裁口径）。C7-014 的「bindCancel 单向」注记与 C3-004（取消不传播）互列相邻。计数影响：**−1** |
| C3-002 ↔ C3-003 | **不同缺陷（同族相邻）** | 不同入口不同机制：fixedDelay 手工重排链（:88-116）vs fixedRate 直通委托（:64-69）；同批修复 |
| C3-004 | **不同缺陷** | 取消传播缺失（生产暴露 RetryEngineImpl:407）；与 C7-014 遗留注记、C3-016（api-core 半边）互列 |
| C7-003 ↔ C1-005 ↔ C3-017 | **同一缺陷（三登记，C3 复核遗留对账项就此闭合）** | 同位点 `ExecutorHelper.scheduleWithRandomDelay:210-238`；同根因（call() 无入口 isDone 守卫 + `ref.set` 后写覆盖 → 取消读旧 ref 无效）；同后果（ghost-chain：取消后周期链继续运行） |
| **归并 C** | **主 = C7-003**（P2 条件化，复合三段：①成功路径 future 永不完成 ②首次异常链死（C3-001 族自续链站点，互引）③ghost-cancel）；**被并 = C1-005（P2）、C3-017（P2）**。计数影响：**−2** |
| 族级绑定 | C3-001（P1 设施主体）为平台级统一契约锚点；C3-015 为其失效面扩展（站点表 + 「绝缘≠续链」语义修正），**同变更集强制绑定**，不并 ID（站点级缺陷面独立，避免 P1 吸并站点表） |

### M8. globalTimer 族 —— C4-001 / C1-003 / C6-003 / C7-002

| 配对 | 判定 |
|---|---|
| 四条互判 | **不同缺陷（同设施四面），单组互列**：C4-001 = 容量死配置 + 工厂副作用改写 + 直连/转发混用无隔离（P1 设施本体）；C1-003 = destroy 置空 × checkEnabled-then-use TOCTOU（P2，六入口 + stats NPE）；C6-003 = 无平台级 destroy 编排 + destroy→re-init 僵尸窗（P2）；C7-002 = fixedRate 补偿突发 + 共享 timer 挤压（P2） |
| 相邻注记 | C9-009（LocalResourceLockManager 周期维护占 globalTimer）= 使用方因子，互引不并；C4-008 死机件、C4-011 死旋钮与 C4-001 同「死配置族」同批治理 |
| 计数影响 | 0。跨单元对接（WI 1.4 C4 / WI 1.5 MA-04 / WI 1.6 CL-002）见 §四 |

### M9. XLangSemantics 族 —— C5-001 / C2-019 / C9-003 / C9-004

| 配对 | 判定 |
|---|---|
| C5-001 ↔ C9-003「Class 强键永不淘汰」子面 | **同一事实（子面去重）**：C9-003 立案本体 = 生成代码属性访问 2 层 CHM 查找开销（P3，不同缺陷），其强键子面与 C5-001（无界 CHM + 零清理入口，P2）为同一结构事实 → 去重注记归 C5-001 记账，修复同批（有界 LRU + GlobalCacheRegistry 注册；weakKeys 路线受值持键约束无效，与 C5-008 联合设计） |
| C2-019 ↔ C5-001/C9-003 | **不同缺陷**：CLASS_MODELS bin 锁内类加载 + ClassModel 全量构建 = 锁内重活延迟面（C2 复核已裁定无环非死锁族、独立排期） |
| C9-004 ↔ C2-019 | **不同缺陷（同族相邻）**：生成代码静态方法调用每次 Class.forName 未复用 CLASS_MODELS 模式 = 缓存复用缺失面；与 C2-019 修复（复用 CLASS_MODELS）同向，互引同批 |
| 计数影响 | 0 |

### M10. ResourceCacheEntry 族 —— C2-001 / C2-002 / C9-001 / C7-007（+C2-017、C9-010）

| 配对 | 判定 |
|---|---|
| 四条互判 | **不同缺陷（同载体四面）**：C2-001 = entry 监视器内加载跨 entry 嵌套死锁（P1，锁序面）；C2-002 = 快路径无锁读 × clear 锁内销毁 use-after-destroy（P2，生命周期竞态面）；C9-001 = 命中路径全依赖图 walk + 裸 stat 税（P2，性能面）；C7-007 = check-changed 双漏检窗（同 tick 双写 + load/取 mtime 竞态，P1，正确性面） |
| C2-017 ↔ C2-001 | **同族相邻（同一根因族两个节点集，同一张锁序图 E1/E4/E5）**：不同位点（LazyCompiledFunction vs ResourceCacheEntry）不并 ID，**同一变更集强制绑定**（统一不变式：监视器内只做同 key 去重+占位）——C2 复核终裁维持 |
| C9-010 ↔ C9-001/C7-007 | **不同缺陷（同族相邻）**：直接使用方硬编码 checkChanged=true 绕过 refreshMinInterval 门（6 使用方，F3 补全）= 门绕过面，与 C9-001 门设计面、C7-007 正确性面互列；**单点修复（entry 层内建门）三者同变更集收敛** |
| 计数影响 | 0。C10 绑定：C10-002（死锁测试）/ C10-005（失效链测试，N3 @Disabled 恢复） |

### M11. destroy 语义族 —— C4-002 / C6-001 / C6-009 / C6-002 / C1-015（逐对判定）

| 配对 | 判定 | 依据 |
|---|---|---|
| C4-002 ↔ C6-009 | **同一缺陷** | 同旋钮（waitForTasksToCompleteOnShutdown / awaitTerminationSeconds）、同根因（全链路零消费接线）、同后果（shutdown-型池停机无排空等待）。C6-009 复核已将其升格为「shutdown-型池排空唯一在册手段未接线」 |
| **归并 A** | **主 = C4-002**（P2 复合条目：死配置 + destroy 不等待 + 两类池 destroy 互斥 + submittedCount 泄漏）；**被并 = C6-009**；修复与 C6-001 残余面同 diff（继承 C6 复核配对约束）。计数影响：**−1** |
| C4-002 ↔ C1-002 | **同一缺陷（子面吸收）** | C4-002 第四面「shutdown 后提交泄漏 submittedCount」与 C1-002（force 抛 REE 无回冲，复核收窄为将亡实例监控口径）同位点（StandardThreadPoolExecutor/TaskQueue）同根因同后果 |
| **归并 B** | **C1-002（P3）并入 C4-002 作子面**（方向订正：虚高致过扩容，随条记录）。计数影响：**−1** |
| C6-001 ↔ C7-014 | 见 M7 归并 B：**同一缺陷（终态化缺口），C7-014(b) 并入 C6-001** |
| C6-002 ↔ C4-005 ↔ C1-015 | **同一缺陷（三镜头重复立案复合条目）** | 同位点 GlobalExecutors（register/unregister/g_executors 借出）；同根因（生命周期所有权未定义：替换即 destroy 不排水、借出即可被 destroy 且不可恢复、unregister 移除不销毁零调用）；同后果簇（死实例被 computeIfAbsent 返回 / 借出引用 use-after-destroy / unregister 泄漏）。C6-002 复核 F3③ 自认与 C4 借出面「同根因不同入口」 |
| **归并 C** | **主 = C4-005**（P2，复合最全：不可恢复链 + unregister 死 API + register 顶替不 drain + metrics 不解绑）；**被并 = C6-002（P2，覆盖即销毁 + allExecutors 活引用两子面）、C1-015（P3，put 后锁外 destroy 竞态窗子面）**；CL 别名 = C1-CL-018（unregister 不销毁）。metrics 不解绑子面以 **C6-004 为主口径**去重（条目注记）；C4-012 的「re-init 顶替销毁借出引用」面并入本组注记。修复回归面：插件 deactivate 与 lint mojo 循环（C6 复核）。计数影响：**−2** |
| C1-003 | **不同缺陷**（相对本族其余成员）：destroy 置空 TOCTOU 属 C1-003 本体；其与 C2-006 的重复登记见 §二 S0（归并主条仍为 C1-003） |
| C6-003 / C6-010 / C6-011 | **不同缺陷**：编排缺失 / initializer Cancellable 复用 / ShutdownHook CAS 反转，各自独立（C6-011 已吸收 C5-CL-029） |
| **补登（扫描发现，归本族）** | **C1-014 ↔ C2-021 的 zip-closed 面 = 同一缺陷**：同位点 DefaultVirtualFileSystem destroy/refresh × 共享 ZipFile 在飞读者；同根因（旧 store 关闭无退役代际）；同后果（zip file closed）。**主 = C1-014（P2）← 并 C2-021（P3）**；C2-021 的「监视器内配置 I/O + zip 构建」面并入合并条目作为第二缺陷面（不独立计数）。C10 N2 修复阻断预警（testRefreshClosesOldZipOnlyAfterRebuild）适用于合并条目。计数影响：**−1** |

### M12. api-core 边界面与移交包 —— C2-016 与 CL-019

裁定：**设立「api-core 移交包」**（明细见 §四·B）。C2-016（P2）维持立案条目并计入 118/96，标注修复通道移交 api-core 治理 plan（`io.nop.api.core.*` 属 plan-first 保护区）；CL-019 维持移交 C1 台账 → api-core 通道；C3-016 的 bindCancel 守卫根因半边、C3-007 的 executeWithThrottling 幻影释放半边、C3-005 的 completeAfterTask catch 宽度半边，三半边随包移交（各条本体在本单元修复，双层修复的 api-core 层归包）。**协同改造点**：`FutureHelper.bindCancel`（api-core）单点同时服务 C3-016（守卫判别）、C6-001（反向传播终态化，M7 归并 B）、C3-004（传播语义）三个条目。

---

## 二、自主扫描补充组（6 组 + 1 项归 M11；宁缺毋滥）

> 以下均为必裁清单之外、经亲核证据新发现的跨维度重复登记。**S0（C1-003 ← C2-006）为最高置信重复**，因属 destroy 语义紧邻面，随 M11 一并宣布（上文已计）。

| 组 | 主 ← 被并 | 三态依据（位点/根因/后果） | 计数 |
|---|---|---|---|
| **S0** DefaultScheduledExecutor destroy TOCTOU | **C1-003（P2）← C2-006（P2）** | 同位点 DefaultScheduledExecutor destroy 置 null × 六入口 + stats() checkEnabled-then-use；同根因；同后果（NPE 替代类型化错误，globalTimer 全平台共享）。C2-006 = C1-003 的 ①-⑦ 失效面的 C2 镜头重复登记 | −1 |
| **S1** 非 volatile 静态状态重复登记 | **C1-009（P2）← C2-009（P2）**；**C2-014（P3）← C1-008（P3）** | C2-009（DictProvider/ModuleManager registerInstance 槽位）经亲核为 C1-009 十三站点表之 ①③ 的子集登记；C1-008（两 Set 惰性初始化缺 volatile）与 C2-014（isSupportTenant 无同步惰性初始化）同位点同根因同后果（重复初始化良性、仅可见性/性能欠账） | −2 |
| **S2** ReactiveObjectMap 失败毒化三登记 | **C1-006（P2）← C2-015（P2）、C7-017（P3）** | 同类（ReactiveObjectMap）；毒化面三镜头重复：C1-006 第二面 = C2-015 本体 = C7-017 本体（失败条目留存→未收集依赖 getDepends()=null→isChanged 恒 false→永久毒化）。C1-006 另持首调 NPE 面（remove(key,null)），全组零生产调用（死代码，删除优先） | −2 |
| **S3** throttleExecute 不可中断双登记 | **C7-010（P2）← C3-006（P2）** | 同位点 ExecutorHelper.throttleExecute（:240-260/:243）；同根因 acquireUninterruptibly；同后果（信号量耗尽调用线程永久挂起、停机中断无效；nop-web 3 生产站点）。**C7-015（IRateLimiter，Guava sleepMicrosUninterruptibly）= 同族相邻不同设施**，同批修（可中断变体），不并 | −1 |
| **S4** updateThreadPool 收缩 IAE 双登记 | **C4-006（P2）← C1-016（P3）** | 同位点 updateThreadPool；同根因（收缩「当前 core>新 max」时 setMaximumPoolSize 抛裸 IAE，缩容不可用；C1-016 为 JDK 26 实测时序修正版 + config POJO 交叉写残余）；同后果。实证可达面 = IoC bean 管理池（C4-006 复核）。历史关联：check 批次暂缓项「newScheduledExecutor config 突变」随同设计 | −1 |
| **S5** LocalCache executor 接线族 | **C3-010（P2）← C4-009（P3）、C3-019（P3）** | 同位点 LocalCache.buildCache（从未调 builder.executor(...)）；同根因（异步加载/刷新 + sync refreshAfterWrite 刷新链固化 ForkJoinPool.commonPool、不可配置）；同后果（脱离平台池治理；C3-019 另持异常仅达 Caffeine JUL 子面随并）。C4-009 复核条件化（无生产 async() 消费者）随主条标注。**C7-009 保留独立**（清理迟滞面为本体；其 commonPool 加载面去重注记归 C3-010） | −2 |
| **S6** DedupBlockingQueue send 一致性 | **C2-010（P2）← C7-006（P2）** | 同位点 DedupBlockingQueue.send（map.put→queue.send 两步无统一补偿；对照 offer 有回滚 ✅）；同根因（复合不变式「map 条目 = 在途条目」无共同锁、失败路径不回滚）；同后果（死键/墓碑 + 条目滞留只增不删）。C2-010 覆盖异常分支（ERROR 策略抛出/BLOCK_WAIT 中断），C7-006 覆盖 DROP 策略静默丢弃分支——同一缺陷的两个触发分支。均零生产调用方（C10-011 处置护栏）。相邻 CL：C9-CL-016（takeMulti m×timeout）随本组同批 | −1 |

**不并只注记的观察（防重复计数提示，均维持原条目独立）**：① CycleSynchronizer「持 this 锁跑回调」子面四处重复（C1-010/C2-004/C7-011/C9-005）——主处置归 C2-004，其余三条该子面去重注记，各条本体面（代际清位/状态机死区/热路径开销）独立；② `catch(Exception)` 宽度模式族（C3-005/C3-011/C6-001 及 WI 1.7 C6-001）——同模式不同设施，不并，修复批统一 catch(Throwable) 校准；③ 模型缓存销毁链修复杂族：C6-005 + C6-012 + C6-013 + C5-003（含租户容器不对称面，与 C6-012 互列同位点注记）+ C7-008——按 C6 复核优先序 ④ 合并实施，不并 ID；④ 死配置族：C4-001/C4-002（含并入面）/C4-008/C4-011 + C1-CL-017——同批系统性治理；⑤ C2-022 + C5-CL-028 同位点（DefaultTaskExecutionQueue 递归 compute 面）——CL 别名归 C2-022。

**家族归组 ↔ C10 缺保护包引用**：退役代际族（C1-001 主 + C1-003/C1-014←C2-021/C4-005 组）→ C10-001/C10-004 + N2 阻断预警 + N5；ResourceCacheEntry 族 → C10-002/C10-005 + N3；调度器族（C3-001/C3-002/C7-001/C7-002/C7-003/C6-001/C3-016）→ C10-003；debounce 组 → C10-006；锁设施族（C2-007/C2-008/C7-010 组/C3-007/C1-011）→ C10-007；模型缓存销毁链 → C10-008；api-core → C10-009；池工厂（C4-003/C4-006 组/C4-011/C4-012/C5-007）→ C10-010；零调用潜伏族 → C10-011 + N1（复核降级为注释清理）；缓存容量治理 → C10-012；提交-取消面 → C10-013；BlockingSource 组 → C10-014；C1-009 组 → N4。

---

## 三、合并后台账（118 条逐条归属；★=被并入他条；主条括注吸收成员）

### C1 共享可变状态（17 → 12）

| ID | 终判级 | 处置 |
|---|---|---|
| C1-001 | P1 | 保留主条（退役代际族族首；C10-001/N5） |
| C1-002 | P3 | ★ 并入 **C4-002**（submittedCount 泄漏子面，M11·B） |
| C1-003 | P2 | 保留主条（←并 C2-006，S0；globalTimer 族互列；退役代际族） |
| C1-004 | P2 | 保留主条（←并 C2-005、C3-020，M5；C10-006） |
| C1-005 | P2 | ★ 并入 **C7-003**（M7·归并 C） |
| C1-006 | P2 | 保留主条（←并 C2-015、C7-017，S2；删除优先，C10-011/N4 关联 ErrorMessageManager 属 C1-009 组） |
| C1-007 | P2 | 保留（deferred；C10-011） |
| C1-008 | P3 | ★ 并入 **C2-014**（S1；volatile 化随 C1-009 同 diff） |
| C1-009 | P2 | 保留主条（←并 C2-009，S1；13 站点 + VFS 错误序 + this-escape；C10 缺保护随 C10-011/N4） |
| C1-010 | P2 | 保留（锁内回调子面去重注记 → C2-004；C10-011） |
| C1-011 | P2 | 保留（deferred；C10-007） |
| C1-012 | P3 | 保留（deferred） |
| C1-013 | P3 | 保留（最小修 javadoc 契约） |
| C1-014 | P2 | 保留主条（←并 C2-021，M11 补登；退役代际族；N2 修复阻断预警） |
| C1-015 | P3 | ★ 并入 **C4-005**（M11·归并 C） |
| C1-016 | P3 | ★ 并入 **C4-006**（S4） |
| C1-017 | P3 | 保留（volatile 化随 C1-009 同 diff） |

### C2 锁与同步（22 → 17）

| ID | 终判级 | 处置 |
|---|---|---|
| C2-001 | P1 | 保留主条（与 C2-017 同一变更集强制绑定；C10-002） |
| C2-002 | P2 | 保留 |
| C2-003 | P2 | 保留（锁内重活纪律族同校准） |
| C2-004 | P2 | 保留主条（CycleSynchronizer 锁内回调面主处置；C1-010/C7-011/C9-005 子面去重注记；C10-011） |
| C2-005 | P2 | ★ 并入 **C1-004**（M5） |
| C2-006 | P2 | ★ 并入 **C1-003**（S0） |
| C2-007 | P2 | 保留（C10-007） |
| C2-008 | P2 | 保留（C10-007） |
| C2-009 | P2 | ★ 并入 **C1-009**（S1） |
| C2-010 | P2 | 保留主条（←并 C7-006，S6；C9-CL-016 同批；C10-011） |
| C2-011 | P3 | 保留（容量 0 族相邻注记 → M3） |
| C2-012 | P3 | 保留 |
| C2-013 | P3 | 保留 |
| C2-014 | P3 | 保留主条（←并 C1-008，S1） |
| C2-015 | P2 | ★ 并入 **C1-006**（S2） |
| C2-016 | P2 | 保留（计入 96；api-core 移交通道，M12；C10-009） |
| C2-017 | P2 | 保留（与 C2-001 同变更集强制绑定） |
| C2-018 | P3 | 保留 |
| C2-019 | P2 | 保留（XLang 族互列；与 C9-004 同向同批） |
| C2-020 | P3 | 保留（锁内解析低频族） |
| C2-021 | P3 | ★ 并入 **C1-014**（M11 补登；监视器内 I/O 面随条第二面） |
| C2-022 | P3 | 保留主条（←C5-CL-028 alias；C10-013） |

### C3 异步链（20 → 15）

| ID | 终判级 | 处置 |
|---|---|---|
| C3-001 | P1 | 保留主条（平台级契约锚点；C3-015 站点表绑定；C10-003） |
| C3-002 | P2 | 保留主条（←并 C7-014(a)，M7·B；C10-003） |
| C3-003 | P2 | ★ 并入 **C7-001**（M7·归并 A；WI 1.6 C8-001 锚点随主条） |
| C3-004 | P2 | 保留（取消不传播；RetryEngineImpl:407；与 C7-014 注记/C3-016 半边互列；C10-003） |
| C3-005 | P2 | 保留（api-core 半边移交，M12） |
| C3-006 | P2 | ★ 并入 **C7-010**（S3；C10-007） |
| C3-007 | P2 | 保留（api-core 半边移交，M12；C10-007） |
| C3-008 | P2 | 保留主条（五 CL 别名，M1；C10-013） |
| C3-009 | P2 | 保留（与 C3-010 修复同批注记） |
| C3-010 | P2 | 保留主条（←并 C4-009、C3-019，S5） |
| C3-011 | P2 | 保留主条（←并 C7-012，M2；CL 别名 C1-CL-003=C2-CL-006；C10-014） |
| C3-012 | P2 | 保留（C10-014） |
| C3-013 | P2 | 保留（CL-022 清单随批；与 CL-019 无冲突） |
| C3-014 | P3 | 保留（C10-014） |
| C3-015 | P2 | 保留（C3-001 失效面扩展，同变更集绑定，不并） |
| C3-016 | P2 | 保留（api-core 半边移交，M12；C10-003） |
| C3-017 | P2 | ★ 并入 **C7-003**（M7·归并 C） |
| C3-018 | P2 | 保留（C10-013） |
| C3-019 | P3 | ★ 并入 **C3-010**（S5） |
| C3-020 | P3 | ★ 并入 **C1-004**（M5） |

### C4 线程池（12 → 11）

| ID | 终判级 | 处置 |
|---|---|---|
| C4-001 | P1 | 保留主条（globalTimer 族互列；跨单元 WI 1.5 MA-04/WI 1.6 CL-002，§四；checkTimeout 终表表述强制修正随修） |
| C4-002 | P2 | 保留主条（←并 C6-009、C1-002，M11·A/B；与 C6-001 残余面同 diff；C10-004/C10-010） |
| C4-003 | P2 | 保留主条（WI 1.7 C4-003 跨单元同面归此，§四；C5-007 ThreadPoolConfig 面与 C6-CL-011 同 diff；C10-010） |
| C4-004 | P2 | 保留主条（WI 1.7 C8-003 映射，§四；主可达面=batch 分片） |
| C4-005 | P2 | 保留主条（←并 C6-002、C1-015，M11·归并 C；ownExecutor 正确范式；C10-004） |
| C4-006 | P2 | 保留主条（←并 C1-016，S4；C10-010） |
| C4-007 | P3 | 保留 |
| C4-008 | P3 | 保留（死配置族同批） |
| C4-009 | P3 | ★ 并入 **C3-010**（S5） |
| C4-010 | P3 | 保留 |
| C4-011 | P2 | 保留（死配置族同批；prestart 机制支撑 C4-CL-024） |
| C4-012 | P2 | 保留（re-init 顶替面去重注记 → C4-005 组；按名通道=ModelBasedBatchTaskBuilderFactory） |

### C5 内存上界（8 → 8）

| ID | 终判级 | 处置 |
|---|---|---|
| C5-001 | P2 | 保留（XLang 族互列；与 C5-008 联合设计；运行期编译后端落地则回升 P1；C10-012） |
| C5-002 | P2 | 保留（C10-012） |
| C5-003 | P2 | 保留（销毁链修复杂族 ④；租户容器面与 C6-012 互列同位点注记；C10-008） |
| C5-004 | P3 | 保留主条（←C7-CL-013 alias，M4；C6-006 同批；服务端嵌入方消费则回升） |
| C5-005 | P3 | 保留（debounce 族相邻，与 C1-004 组同 diff） |
| C5-006 | P3 | 保留 |
| C5-007 | P3 | 保留（ThreadPoolConfig 无校验面 → C4-003 同 diff；其余两面独立；C10-010） |
| C5-008 | P2 | 保留（与 C5-001 联合设计；C10-012） |

### C6 资源生命周期（13 → 11）

| ID | 终判级 | 处置 |
|---|---|---|
| C6-001 | P2 | 保留主条（←并 C7-014(b)，M7·B；修复=C6-009 接线 + 终态化补偿同 diff） |
| C6-002 | P2 | ★ 并入 **C4-005**（M11·归并 C） |
| C6-003 | P2 | 保留（g_executors 统筹批 ②；回归面含插件/lint 循环） |
| C6-004 | P2 | 保留（metrics 对称主口径；C4-005 metrics 子面去重注记归此） |
| C6-005 | P2 | 保留（销毁链族 ④） |
| C6-006 | P2 | 保留（C7-CL-032 独立保留同批；C5-004 同批） |
| C6-007 | P2 | 保留（awaitTermination 面随 C6-009 接线批） |
| C6-008 | P3 | 保留（三选一落档） |
| C6-009 | P2 | ★ 并入 **C4-002**（M11·归并 A） |
| C6-010 | P2 | 保留（休眠；抽象基类一次收敛 8 处） |
| C6-011 | P2 | 保留（←C5-CL-029 已并；休眠） |
| C6-012 | P2 | 保留（销毁链族 ④；与 C5-003 互列注记） |
| C6-013 | P3 | 保留（销毁链族 ④） |

### C7 长运行（17 → 13）

| ID | 终判级 | 处置 |
|---|---|---|
| C7-001 | P2 | 保留主条（←并 C3-003，M7·A；三段劣化 + JDK 实证；WI 1.6 C8-001 平台统一排期；C10-003） |
| C7-002 | P2 | 保留（Default 层 fixedRate 补偿突发；与 C7-001 同批；C10-003） |
| C7-003 | P2 | 保留主条（←并 C1-005、C3-017，M7·归并 C；①② 段自有面，② 段归 C3-001 族站点注记；C10-011） |
| C7-004 | P2 | 保留（C7-CL-034 随批；部分保护） |
| C7-005 | P2 | 保留（与 C7-016 同点位修复；N1 降级为注释清理；C10-011） |
| C7-006 | P2 | ★ 并入 **C2-010**（S6） |
| C7-007 | P1 | 保留主条（checker 层修复同步收敛 C9-010 正确性面；C10-005/N3） |
| C7-008 | P2 | 保留（销毁链族 ④；C10-012） |
| C7-009 | P2 | 保留（清理迟滞面本体；commonPool 加载面注记 → C3-010；C10-012） |
| C7-010 | P2 | 保留主条（←并 C3-006，S3；nop-web 3 站点；C10-007） |
| C7-011 | P2 | 保留（锁内回调子面注记 → C2-004；C10-011） |
| C7-012 | P2 | ★ 并入 **C3-011**（M2） |
| C7-013 | P3 | 保留（**勘误：非 FileWatcher 登记**，任务指令编号混淆已裁定，见 M4） |
| C7-014 | P3 | ★ 拆分并入 **C3-002(a) + C6-001(b)**（M7·B；无独立残留） |
| C7-015 | P2 | 保留（与 C7-010 组/S3 同批修；WI 1.6 C3-004 映射，§四） |
| C7-016 | P3 | 保留（与 C7-005 同点位；C10-011） |
| C7-017 | P3 | ★ 并入 **C1-006**（S2） |

### C9 热路径（9 → 9；另 C9-006 驳回不计）

| ID | 终判级 | 处置 |
|---|---|---|
| C9-001 | P2 | 保留（与 C7-007 同链两面判级自洽；C10-005） |
| C9-002 | P3 | 保留（putAll 子项已剔除） |
| C9-003 | P3 | 保留（强键子面去重注记 → C5-001；C10-012） |
| C9-004 | P2 | 保留（与 C2-019 同向同批） |
| C9-005 | P3 | 保留（锁内回调子面注记 → C2-004） |
| ~~C9-006~~ | — | 驳回（复核轮裁定，不计入） |
| C9-007 | P3 | 保留 |
| C9-008 | P3 | 保留 |
| C9-009 | P3 | 保留（globalTimer 占用因子互引 C4-001） |
| C9-010 | P2 | 保留（6 使用方；entry 层内建门单点修复覆盖 C9-001/C7-007；C10-005） |

---

## 四、跨单元关联（重点交付）

### A. 使用方发现 ↔ 设施本体发现 映射表

| # | 使用方条目（WI 单元） | 设施本体条目（本单元合并后） | 关系裁定 | Phase 8 批次对齐建议 |
|---|---|---|---|---|
| 1 | WI 1.6 C8-001（retry/tcc 扫描循环被拒/Error（近）静默永久停摆，P1，←C7-004/C6-014，「修复须平台级统一」） | **C3-001**（周期任务设施无统一异常绝缘层，P1，29 站点） | 同根因两面：使用方停摆面 / 设施契约面（kill-chain 无续链无告警） | **同批 P1**：设施契约（续链 or kill+alarm）先行定稿，使用方站点按 C3-015 站点表逐点收敛；C10-003 测试包覆盖 |
| 2 | WI 1.6 C8-001 与 WI 1.4 C4-003（nop-job rpcPoll Bind 链式再调度停摆，P1） | **C7-001**（←并 C3-003；Bind fixedRate 拒绝静默死亡）＋**C3-002**（fixedDelay 重排链死） | 停摆的设施转发面/重排面 | 并入批次 1 同变更集（Bind 层补绝缘 + completeExceptionally） |
| 3 | WI 1.6 C8-001（扫描循环 REE 死亡形态） | **C7-002**（Default fixedRate 补偿突发 + 共享挤压） | 调度载体 Default 层对应面 | 同批 P2（fixedRate 补偿语义与 C7-001 同审） |
| 4 | WI 1.6 C3-004（join 冻结四合一 ←C6-008/C7-002/C8-002，条件化 P2） | **C3-003（已并 C7-001）＋C7-015**（IRateLimiter 不可中断） | throttle/join 越界面：使用方 join 永冻的设施侧根因（调度链死 × 不可中断限流等待） | 同批 P2：C7-015 可中断变体 + C7-001 拒绝终态化共同消除 join 永冻面 |
| 5 | WI 1.6 C7-002（已并入其 C3-004；HTTP client 30s 慢滴封顶） | **C7-015** | 同上（有界慢滴 vs 设施不可中断语义错配） | 同上批 |
| 6 | WI 1.5 C4-001（MA-04：wf/task retry/delay 业务体 + saveState 簿记恒跑 globalTimer，P1，成员 C4-008/C3-013） | **C4-001**（globalTimer 死配置 + 副作用改写 + 无隔离，P1） | 同设施使用方占用面 / 设施本体面，互补不重复（C4-001 复核终裁 (d) 已裁定） | **同批 P1**：一个 globalTimer 隔离/迁移方案两面对接；使用方事实交叉印证设施 P1 占用严重度 |
| 7 | WI 1.5 MA-04（=WI 1.5 C4-001，同条异号） | C4-001 | 同上 | 同上 |
| 8 | WI 1.6 CL-002（tcc doScan 体实占 globalWorker，P2，关联 C3-004） | **C4-001**（隔离面）＋**C4-005**（globalWorker 注册表面） | 占用转嫁面（C 类 executeOn 自救把占用从 timer 转嫁 worker） | 并入批次 6 同批；重活强制 executeOn 化实施时同步复核 worker 侧容量 |
| 9 | WI 1.4 C4（globalTimer/globalWorker 池占用、C4-008 fallback 挂池） | C4-001 / C4-005 | 占用面 | 同批 |
| 10 | WI 1.2 C4-004（MA-06：Debezium 无限阻塞任务常驻 globalWorker，P1） | C4-001（隔离面） | 共享池无隔离的使用方形态 | 平台池隔离方案（批次 6）统一覆盖该使用方形态 |
| 11 | WI 1.7 C8-003（batch concurrency 无上界 + xdef 声明与实现相反；executor 兜底 `GlobalExecutors.cachedThreadPool()`，P1） | **C4-004**（nop-cached-thread-pool 按名共享无上界无配额，P2） | 使用方并发无上界 × 池本体无上界（其自身台账已挂「平台池占用族」= WI 1.5 C4-001/WI 1.6 CL-002） | 同批：配额/熔断在设施层（C4-004）统一实现；batch 侧 concurrency 护栏独立保留（P1 面不因设施修复关闭） |
| 12 | WI 1.7 C4-003（batch 侧 newExecutor 零校验登记） | **C4-003**（newExecutor/ThreadPoolConfig 工厂零校验，P2） | **同设施同面跨单元双登记** | 处置归本单元 C4-003（设施本体）；WI 1.7 条目 Phase 8 对账时 cross-ref 关闭不重复修复；C5-007 ThreadPoolConfig 面 + C6-CL-011 同 diff |
| 13 | WI 1.6 C8-012（executeImmediateRetry 返回 future ≠ 回调挂接 future，P1） | **C3-016**（submit/schedule 成功路径误 cancel；api-core bindCancel 半边） | FutureHelper.bindCancel 语义同族根因 | api-core 移交包（§B）单点改造 bindCancel 同时服务两条；retry/tcc 使用方列入回归面 |
| 14 | WI 1.7 C7-001（batch 任务行永久 RUNNING 无 fencing）＋WI 1.5 C7-001（WfTaskScanner 无 fencing） | （本单元无同位点设施条目） | 三方 fencing 语义统一关联（非设施缺陷） | 批次对齐注记：fencing 平台级对齐讨论并入批次 1/6 的停摆族方案，不产生本单元条目 |
| 15 | WI 1.6 CL-006（完成回调占用共享 RPC 完成线程池，留 rpc 单元）＋C4-CL-024（prestart×死超时） | C4-004（共享执行资源无隔离同族形态） | 形态同族 | 共享资源隔离策略与批次 6/11 合并考量；CL-006 终裁留 rpc 单元 |

**零重复确认**（抽查核定，维持各维复核结论）：C1 复核 §5 使用方条目与本单元零重复；C7-007 在 cra-index 无同位点（平台设施本体首立案）；WI 1.6 C7-001（tcc 账本零保留）与本单元 C7-001（Bind fixedRate）**同名不同物、零关联**，台账引用时须带单元前缀防混。

### B. api-core 移交包清单

| # | 条目 | 类型/计数 | 内容 | 修复通道 |
|---|---|---|---|---|
| 1 | **C2-016** | 全条（P2，计入 96） | ContextTaskQueue.syncGet drain→await 丢失唤醒窗：enqueue 谓词 syncing>0 过窄 + flush beginProcess 失败静默放弃（flush 面 endProcessIfIdle 已修 commit 5ee798e02e，本条为 syncGet 面遗留） | api-core 治理 plan（`io.nop.api.core.*` plan-first 保护区）；测试手法先例 = 同文件 flush 面红验证（C10-009） |
| 2 | **C2-CL-019** | CL（移交 C1 台账 → api-core 通道） | ExecutionContextImpl.onBeforeComplete 完成状态机窗回调静默丢失（L83-93/L109-128）+ ServiceContextImpl 写锁读无锁（L121-132） | 同上 |
| 3 | **C3-016 根因半边** | 半边（C3-016 本体 P2 计入 96；ExecutorHelper 侧双层修复） | `FutureHelper.bindCancel` 守卫 `!future.isDone()` 在 complete-inside-task 写法下恒真 → 必然误 cancel 底层 Future + 注入中断 | 同上；**协同改造点**：bindCancel 单点同时服务 C6-001（反向传播终态化）与 C3-004（传播语义） |
| 4 | **C3-007 移交半边** | 半边 | api-core executeWithThrottling 幻影 release（L548-552，与 LimitedExecutor 同构） | 同上（release 移入 acquire 成功后作用域） |
| 5 | **C3-005 移交半边** | 半边 | completeAfterTask catch Exception 宽度（漏 Error） | 同上（双层改 catch(Throwable)） |

---

## 五、CL 台账终表（36 条编号；确认 35 / 驳回 1）

| CL | 维度 | 终态 | 归属/说明 |
|---|---|---|---|
| C1-CL-001 | C1 | **驳回（维持）** | 容量 0 自旋前提证伪（构造 IAE fail-fast）；族见 M3 |
| C1-CL-002 | C1 | 确认 → **别名并入 C3-008** | M1 |
| C1-CL-003 | C1 | 确认 → **并入 BlockingSource 组** | 整批丢弃面台账，随 C3-011/C3-012 同文件批（M2） |
| C1-CL-017 | C1 | 确认 | setEnabled 运行期无效 = 死旋钮族，随 C4-002（含 C6-009）死配置批处置 |
| C1-CL-018 | C1 | 确认 → **别名并入 C4-005 组** | unregister 不销毁 = M11·归并 C 之 unregister 面 |
| C2-CL-004 | C2 | 确认 → **别名并入 C3-008** | M1 |
| C2-CL-005 | C2 | 确认 → **C3-003 CL 前身，随并 C7-001** | M7·A |
| C2-CL-006 | C2 | 确认 → **与 C1-CL-003 同面并入 BlockingSource 组** | M2 |
| C2-CL-019 | C2 | 确认 → **api-core 移交包**（移交 C1 台账） | §四·B |
| C2-CL-020 | C2 | 确认 | IEvalScope.computeIfAbsent Javadoc 契约补注，随 C2 批 |
| C3-CL-021 | C3 | 确认 | 载体碎片化清单，随 C3-001 平台契约批复核 raw 载体 |
| C3-CL-022 | C3 | 确认 | detach 卫生具名清单，随 C3-013 修复批 |
| C4-CL-007 | C4 | 确认（纠偏成立） | 维持不立案，移交 C8 面（TaskQueue 弹性扩容描述纠偏） |
| C4-CL-023 | C4 | 确认 | nop-stream 模块面（26~27 处裸建池），移交 WI 1.1 池治理批核对 |
| C4-CL-024 | C4 | 确认 | prestart×allowCoreThreadTimeOut 死值联动 C4-011；移交 rpc 单元（与 WI 1.6 CL-006 同批） |
| C5-CL-008 | C5 | 确认 → **容量 0/溢出策略族主登记** | 并入 C9-CL-014 残余面；族见 M3 |
| C5-CL-009 | C5 | 确认 → **别名并入 C3-008** | M1（第五登记） |
| C5-CL-010 | C5 | 确认 | localeToMessages LRU 装载链，C8 面留台账 |
| C5-CL-025 | C5 | 确认 → **JsonWhitelist 主登记，转功能/安全域** | M6；C1 备注、C9-CL-036 为别名 |
| C5-CL-026 | C5 | 确认 | 非 volatile 静态容器新站点，随 C1-009 批处置 |
| C5-CL-027 | C5 | 确认 | 缓存管理语义（remove 忽略 key/stats null），随 C5 批留台账 |
| C5-CL-028 | C5 | 确认 → **别名并入 C2-022** | 同位点 CallerRuns 递归 compute 面 |
| C5-CL-029 | C5 | 确认 → **已并入 C6-011**（维持关闭） | C6 复核维持 |
| C6-CL-011 | C6 | 确认 | 与 C4-003 同位点（ImportDbTool/ExportDbTool 无界队列），同 diff 处置；daemon 面归 C6-007 |
| C6-CL-030 | C6 | 确认 | registerInstance 非原子 + registerMetrics 无 null 判；启动期单线程，留台账 |
| C6-CL-031 | C6 | 确认 | **移交 nop-graph 轮次**（LeidenDetector 非 daemon + 无 awaitTermination） |
| C7-CL-012 | C7 | 确认 → **别名并入 C3-008** | M1 |
| C7-CL-013 | C7 | 确认 → **别名并入 C5-004** | M4；lost-wakeup F3 随修 |
| C7-CL-032 | C7 | 确认 → **独立保留** | 与 C6-006 同文件不同机制面（OVERFLOW 事件丢失），同批修复 |
| C7-CL-033 | C7 | 确认 | throttle-compute 对照注记，随 C1-004 合并批 |
| C7-CL-034 | C7 | 确认 | 墙钟预警清单，随 C7-004 整改统一 nanoTime |
| C9-CL-014 | C9 | 确认 → **并入 C5-CL-008 作残余子面** | 有界自旋无退避；族见 M3 |
| C9-CL-015 | C9 | 确认 | LRLM await/signal 吞吐形态面（与 C2 正确性排除不同 facet），C8 面留台账 |
| C9-CL-016 | C9 | 确认 | Dedup takeMulti m×timeout 契约违约，随 C2-010 组同批 |
| C9-CL-035 | C9 | 确认（首立案） | ClassModel.java:48 非 volatile DCL；归 C2 台账（与 C1-009 静态槽不同族），随 C2 可见性小修批 |
| C9-CL-036 | C9 | 确认 → **JsonWhitelist 别名**（行号 :36-55） | M6 |

---

## 六、统计节

### 6.1 合并汇总

| 指标 | 值 |
|---|---|
| 输入保留发现 | 118（P1×5 / P2×75 / P3×38）+ 驳回 2（C5-009、C9-006，复核轮已裁，维持） |
| 合并组数 | 必裁 12 组 + 扫描 7 项（S0 归 M11 宣布）产出 **17 个主条目吸收 22 条被并条目**（每条被并 = 一项同缺陷判定；C7-014 拆分并入 C3-002/C6-001 两个主条）；另 **2 组仅归并 CL 别名**（M1→C3-008、M4→C5-004）、**1 组转域**（M6 JsonWhitelist → 功能/安全域） |
| 被并入立案条目 | **22 条（P2×12 / P3×10）** |
| CL 别名归并 | 11 条（C1-CL-002/C2-CL-004/C5-CL-009/C7-CL-012→C3-008；C2-CL-005→C7-001；C1-CL-003=C2-CL-006→BlockingSource 组；C1-CL-018→C4-005 组；C7-CL-013→C5-004；C5-CL-028→C2-022；C5-CL-029→C6-011） |
| **合并后有效发现** | **96 = 118 − 22** |
| **合并后严重度分布** | **P1×5 / P2×63 / P3×28**（P2 = 75−12；P3 = 38−10；P1 无合并进出） |
| 转域移交 | JsonWhitelist 三登记 → 功能/安全域单列（不入 96） |

### 6.2 合并组清单（主 ID ← 被并 ID；含 CL 别名）

| # | 主条（判级） | 被并立案条目 | CL 别名 | 判定 |
|---|---|---|---|---|
| 1 | C3-008（P2） | — | C1-CL-002、C2-CL-004、C5-CL-009、C7-CL-012 | 同一缺陷（M1） |
| 2 | C3-011（P2） | C7-012 | C1-CL-003=C2-CL-006 | 同一缺陷（M2） |
| 3 | C1-004（P2） | C2-005、C3-020 | C7-CL-033 注记；C5-005 相邻同 diff | 同一缺陷（M5） |
| 4 | C7-001（P2 条件化） | C3-003 | C2-CL-005 | 同一缺陷（M7·A） |
| 5 | C3-002（P2）＋C6-001（P2） | C7-014（拆分吸收 a/b） | — | 同一缺陷两主（M7·B） |
| 6 | C7-003（P2 条件化） | C1-005、C3-017 | — | 同一缺陷（M7·C） |
| 7 | C4-002（P2） | C6-009、C1-002 | C1-CL-017 随批 | 同一缺陷（M11·A/B） |
| 8 | C4-005（P2） | C6-002、C1-015 | C1-CL-018 | 同一缺陷（M11·C） |
| 9 | C1-014（P2） | C2-021 | — | 同一缺陷（M11 补登） |
| 10 | C1-003（P2） | C2-006 | — | 同一缺陷（S0） |
| 11 | C1-009（P2）；C2-014（P3） | C2-009；C1-008 | — | 同一缺陷 ×2（S1） |
| 12 | C1-006（P2） | C2-015、C7-017 | — | 同一缺陷（S2） |
| 13 | C7-010（P2） | C3-006 | — | 同一缺陷（S3） |
| 14 | C4-006（P2） | C1-016 | — | 同一缺陷（S4） |
| 15 | C3-010（P2） | C4-009、C3-019 | — | 同一缺陷（S5） |
| 16 | C2-010（P2） | C7-006 | C9-CL-016 随批 | 同一缺陷（S6） |
| 17 | C5-004（P3） | — | C7-CL-013 | 同一缺陷（M4） |

### 6.3 P1 合并变动逐条说明（5 条，全部维持原判级、零合并进出）

1. **C1-001（P1 维持）**：无 ID 级合并。其「退役代际」修复族成员变动——吸收 C2-021（P3，经 C1-014）并互列 C1-003/C4-005 组；族修复方案（延迟销毁/退役代际）与 N2 测试阻断预警随族生效。判级不变。
2. **C2-001（P1 维持）**：无合并。同根因族节点 C2-017 维持「同一变更集强制绑定」（不同位点不并 ID，锁序图 E1/E4/E5 完整性不受影响）。
3. **C3-001（P1 维持）**：同根因族内 **C3-003 并出至 C7-001**（固定率转发面归复合条目），C3-015 维持站点表绑定不并；设施主体（无统一异常绝缘层，29 站点）不受影响。与 WI 1.6 C8-001 的平台级统一修复锚点关系经 C7-001 传递保留。
4. **C4-001（P1 维持）**：globalTimer 族四条互列不并（死配置/TOCTOU/编排/fixedRate 补偿为四个独立缺陷面）；跨单元 WI 1.5 MA-04、WI 1.6 CL-002、WI 1.4 C4、WI 1.2 C4-004 映射关系见 §四·A 行 6-10；checkTimeout「DB 扫描」表述强制修正随修。
5. **C7-007（P1 维持）**：同链 C9-001（P2 性能面）/C9-010（P2 绕门面）维持三面互列、单点修复（entry 层内建门）同变更集；判级按正确性面口径不变。

### 6.4 各维合并后有效条目数

| 维度 | 复核保留 | 被并出 | 合并后 |
|---|---|---|---|
| C1 | 17 | 5（C1-002/005/008/015/016） | 12 |
| C2 | 22 | 5（C2-005/006/009/015/021） | 17 |
| C3 | 20 | 5（C3-003/006/017/019/020） | 15 |
| C4 | 12 | 1（C4-009） | 11 |
| C5 | 8 | 0 | 8 |
| C6 | 13 | 2（C6-002/009） | 11 |
| C7 | 17 | 4（C7-006/012/014/017） | 13 |
| C9 | 9 | 0 | 9 |
| **合计** | **118** | **22** | **96** |

### 6.5 对 summary.md 与 cra-index 的回填指令（供协调方执行）

1. summary.md 归档口径：**96 条有效发现（P1×5 / P2×63 / P3×28）+ 驳回 2 + CL 36 条终态（§五）+ api-core 移交包 5 项（§四·B）**；96 条全清单以本文件 §三 为准。
2. cra-index 报告清单行（本单元新增行）按 96 条登记；P1 汇总表追加 5 行（C1-001/C2-001/C3-001/C4-001/C7-007，Finding ID 前缀 `nop-kernel-concurrency-`），并在描述列标注跨单元映射行号（§四·A）。
3. WI 1.7 C4-003 与本单元 C4-003 的同面双登记：按 §四·A 行 12 在两处台账互登 cross-ref，修复归本单元条目。
4. C10 文件维持不变；修复批次验收引用 C10-001~014 时按 §二末尾的家族映射对号。

---

*裁定完毕。裁定基础全部来自八维两轮原文条目 + 各维复核结论 + C10 + cra-index 的亲读核对；所有「同一缺陷」判定均满足同位点 + 同根因 + 同后果三要素，存疑配对（模型缓存销毁链租户面、DedupBlockingQueue 早期方案）一律从保守按同族相邻处置并注记。*
