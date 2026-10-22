# 深度审核汇总报告 — nop-sys + nop-dyn 并发·异步·资源专项审计（CRA WI 5.2）

## 基本信息

- **审核单元**: `nop-sys-dyn`（2 模块 245f/21,844 lines src/main 排除 `_gen/` 口径——nop-sys 140f/10,890 [api 57f/6,632 + dao 58f/3,473 + service 24f/747 + app 1f/38] + nop-dyn 105f/10,954 [api 45f/7,232 + dao 35f/1,802 + service 24f/1,882 + app 1f/38]）
- **审核日期**: 2026-10-21（初审/深挖/复核/C10）；合并裁定与汇总收口 2026-10-22
- **执行维度**: C1 共享可变状态、C2 锁与同步、C6 资源生命周期、C8 背压流控（roadmap 5.2 裁剪集四全维度）+ C10 测试保护力；C3/C4/C5/C7/C9 overlay 线索形态
- **目标范围**: `src/main/java` 全部手写代码（`_gen/` 残留 sys dao entity 25f + dyn dao entity 17f 排除）；基线 commit 3807af8361
- **方法论**: 盲审初审 → 深挖迭代（每维度 2 轮）→ 独立复核 → 合并裁定（共享前缀+维度正文）
- **Plan**: `ai-dev/plans/2483-cra-wi5.2-nop-sys-dyn.md`（R1 1 Blocker+5 Major+3 Minor 全修；R2 agent_231d56ab 3 Minor 措辞/口径级就地订正）
- **Protected Area**: `nop-sys-api`（57f/6,632）+ `nop-dyn-api`（45f/7,232）plan-first——审计只读不受限，**0 张工单触碰 api 叶**；I-03 若走唯一索引方案触碰 orm 模型 = plan-first
- **执行说明**: 四维度各单批覆盖 sys+dyn 两模块，全部子代理严格串行派发；主战场=序列号/分布式锁/事件队列/动态模型运行时缓存（roadmap 点名四件套全部命中实质缺陷面）

## 执行统计

| 维度 | 初审 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|------|---------|---------|------|------|
| C1 | 9（P1×2/P2×2/P3×5） | +2（P3×2） | 11 | 1（C1-004 → watch-only） | 0 |
| C2 | 7（P1×1/P2×4/P3×2） | +2（P1×1/P3×1） | 9 | 0 | 0 |
| C6 | 5（P2×3/P3×2） | +2（P2×1/P3×1） | 7 | 0（C6-005 保留即标注 watch-only） | 0 |
| C8 | 4（P2×1/P3×3） | +2（P3×2） | 6 | 0 | 0 |
| C10 | — | — | 16 行矩阵（会红 0/反向钉死 1/时序敏感 2）+ 缺保护登记 15 | 0 | 0 |
| **合计** | **25** | **+8 = 33** | **33** | 2（watch） | 0 |

四维度深挖第 2 轮全部产出追加发现（8 条）后显式收敛；锁序图（JVM 内无环）与 DB 层锁序经第 2 轮反向复查维持。

## 合并裁定

- 合并前 33 条 → **17 张工单**（7 缺陷族 MA-01~07 承载 21 条 = 主登记 7 + 吸收 14；10 独立 I-01~10）+ watch 台账 2 条（C1-004、C6-005）。
- 工单判级：**P0×0 / P1×3 / P2×8 / P3×6**。
- 跨单元家族关联 11 处单列（cra-index P1 台账 159 条零同站点重复）；冲突裁定 6 项（含 1 项推翻维度复核建议——租户缓存三站点归族）；C10 缺保护 15 条单列随本体工单落地。
- 权威表：见同目录 `merge-adjudication.md` §六。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行） |
| P1 | 3 | MA-01 DB 行协调原语 fencing 纪律族（P1 站点×2：过期锁行删除缺 holderId → 互斥无声破坏；事件认领一次性租约不续期 + 完成写无 fencing → 双节点并发处理同一事件）；I-01 已发布 mergedStore 并发结构修改（TreeMap 撕裂读）；I-02 订阅 cancel×subscribe 孤儿化 → 消息置 PROCESSED 静默丢弃 |
| P2 | 8 | MA-02 动态模型生成锁全局串行（读路径+DB I/O+用户钩子同一临界区）、MA-03 租户缓存生命周期三缝隙（evict/init/destroy）、MA-04 sys_lock/sys_change_log 零保留（F4）、MA-05 序列默认回退路径（共享锁 item+锁内 DB+漏清）、MA-06 懒初始化与快照发布卫生、I-03 generateByAI TOCTOU、I-04 动态模型删除零失效面、I-05 消费等待无超时（F5 静默挂死） |
| P3 | 6 | MA-07 轮询管线无界工作族（无预算/无隔离/无退避/清理无 LIMIT）、I-06 scope 跨代残留、I-07 setVar TOCTOU、I-08 无租户上下文旁路缓存、I-09 doStop 不等在途任务、I-10 批量发送半个成功未声明（F2） |

C10 缺保护登记 15 条（P2 单列）：矩阵 16 行**会红=0**（P1 级 4/4 无测试；P2 级无测试 9 + 假保护 2；C8-004 假保护随案）——**会红=0 第 6 例 campaign 同型确认**；反向钉死 1（TestInMemoryCodeCacheContract 修饰符钉死，MA-02 修复须同 diff 改写断言）。

## P0 记录

本单元 **P0 = 0**（显式空执行记录；nop-sys-api/nop-dyn-api Protected Area plan-first 未被触发——无 P0 修复需求）。

## 现状标注摘要

33 条发现全部为**新发现**（cra-index 报告清单 22 单元无 nop-sys/nop-dyn 条目；历史审计 sys/dyn 并发主题 0 命中）。特殊现状标注：

- C1-001/C1-002：站点带前一轮（campaign 前局部审计）修复痕迹，本条为其修复的**残余并发面**（修复本身正确，未闭合全部交错）。
- C2-001：**已知未修复（F3 同族遗留站点）**——同文件 releaseLock/tryResetLease 已修且注释言明根因，唯 tryLockWithLease 过期删除分支漏补；现有 takeover 测试全部顺序化，无双竞争者交错覆盖。
- C2-002：**潜伏缺陷**（全仓唯一调用方为测试单次续约场景）——附激活回升条款：上游引入 watchdog 周期续约即升 P1。
- C1-008：站点注释自认「显式去重」为已知问题的应用层弥补，弥补手段自身有竞态（并发缺口为新发现）。
- C2-006：互斥为类注释声明设计——按代价登记立案（P3），未按缺陷报。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | MA-01 | DB 行协调原语 fencing 纪律族：C2-001 过期锁行删除缺 holderId——竞争者可删掉新持有者的新锁行，双节点各自认为持锁（同文件两处同族已修，教科书 F3 遗留）；C2-102 事件认领一次性租约 10s 不续期 + 完成写无版本/属主 fencing——慢消费（>10s，普通场景）下双节点并发处理同一事件、终态互覆 | nop-sys-dao（SysDaoResourceLockManager / SysDaoLeaderElector / NonBroadcastEventProcessor） |
| 2 | I-01 | InMemoryCodeCache 已发布 mergedStore 被 genBizObjFiles merge/addToMergedStore 原地结构修改（底层普通 TreeMap 无同步）——违反本类自声明只读发布契约，动态模型资源热路径撕裂读（丢节点/遍历异常） | nop-dyn-service（InMemoryCodeCache + 内核 ResourceTreeNode） |
| 3 | I-02 | DurableSubscription.cancel 与 subscribe 复合序列交错——新订阅者被整个从 map 摘除（孤儿化），此后事件 warn 后置 PROCESSED 静默丢弃，直至该订阅者再次 cancel/resubscribe | nop-sys-dao（SysDaoMessageService） |

结构性发现：①fencing/条件写缺失集中于 nop-sys-dao 协调原语（五站点同一修法：条件 UPDATE + 状态回写，无一处需 JVM 锁）；②「会红=0」第 6 例——唯一反向钉死恰是「结构断言钉死实现修饰符」形态，把实现钉死在正确的一半；③动态模型服务路径「生成锁=全局限速器」——管理端一次编辑触发平台级动态模型排队；④租户缓存生命周期三缝隙，默认容器上限 100 即可触发周期性整租户重初始化风暴。

## 盲区自评

- C3/C5/C7/C9 为 overlay 线索形态，未做完整口径审计（处置：C5/C3 零命中确认关闭、C7 并入 I-05、C4 落入 I-09/MA-07、C9 零立案；见 merge-adjudication.md §八）。
- C6-005（广播游标语义）依赖内核 findNext 对已删游标行的语义，**未证实**——watch 台账并登记触发条件（§五#4）。
- C1-102 可达性依赖部署形态（useTenant=true 且存在无租户头管理调用的组合）；C2-002 影响面取决于未来 watchdog 调用方——两条均附激活/确认条款留 7.1。
- 变异推演仅覆盖 P0/P1/P2（15 条缺保护登记），P3 缺陷无保护力登记（C10 口径一致）。
- nacos/sentinel/DB SDK 黑盒、`_gen/` 残留、业务语义正确性为 Non-Goals；api 叶 Protected Area 全程只读未触碰。
- owner doc drift 登记：`nop-dyn.md` 并发关键词 0 命中（nop-sys.md 23 命中正常）。

## 与其他 roadmap 的重叠

- 与 WI 1.1–5.1 台账（cra-index P1 累计 159 条）**零同站点重复**；跨单元家族关联 11 处单列：fencing 纪律族（↔1.6/4.2 MA-01/2.5 MA-05/1.5 C7-001）、订阅生命周期族（↔4.1 MA-05、2.5 C6-004）、F4 零保留族（↔5.1 MA-01、1.6 C7-001、1.7 C7-003）、F5 静默挂死族（↔3.1 C7-001、1.6 C8-001）、输家归一族（↔5.1 MA-05）、停止协议族（↔2.5 MA-04）、半个成功族（↔1.6 C6-003、2.4 C2-013、5.1 I-02）、扫描无界族（↔1.5 C5-001）、单飞治理（↔2.5 MA-05）、懒初始化族（↔5.1 I-04）、锁内重 I/O 同型异层（↔2.1 C2-001）。
- security-audit roadmap：无重叠面（本单元主战场为并发/资源面）。
- 全仓 grep 证据：预注周期任务族 CL 候选确认——本单元三周期任务 try/catch 全包，WI 1.6/4.5/2.1 的 Error 逃逸停摆形态在本单元不可达（见 merge-adjudication.md §八）。
