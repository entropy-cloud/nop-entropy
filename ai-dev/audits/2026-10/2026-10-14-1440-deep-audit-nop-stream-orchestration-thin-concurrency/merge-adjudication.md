# 合并裁定 — nop-stream-orchestration-thin 并发审计（7.1 跨维度合并裁定）

- 裁定子代理：独立合并裁定 subagent（独立于 4 个维度初审/复核代理；对全部保留发现按「根因 + 失效层」三态口径裁定，可推翻复核提议）
- 日期：2026-10-14；仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，HEAD 8dcfa4cd0c，与全部维度基线一致）
- 输入：本审计目录 5 份文件（C1 / C3 / C6 / C2-C9 抽查 / C10）的最终保留项节 + 各维度复核结论 + 条目正文；对账基准 `ai-dev/audits/cra-index.md`（WI 1.1 十条 P1 + WI 1.2 十条 P1 台账）
- 输出约束：本文件为 summary.md 数字的裁定依据——每个保留条目恰好落入一个缺陷族或独立立案，无遗漏、无重复计数

---

## 1. 裁定方法与输入清单

### 1.1 输入清单（各维度最终保留条目 × 严重度）

| 来源文件 | 保留/降级条目 | P1 | P2 | P3 | 计入裁定数 | 备注 |
|---|---|---|---|---|---|---|
| C1-shared-mutable-state.md | 保留 7 | 1（C1-001） | 4（C1-002~005） | 2（C1-006/007） | 7 | 无降级/驳回 |
| C3-async-chains.md | 保留 8 + 降级 1 | 2（C3-008/009） | 5（C3-001 降级、C3-002~005，含理论 2） | 2（C3-006/007） | 9 | C3-001 P1→P2 |
| C6-resource-lifecycle.md | 保留 5 + 降级 1 | 1（C6-007） | 5（C6-001 降级、C6-002~005） | 0 | 6 | C6-001 P1→P2；C6-006 已驳回、不入裁定 |
| C2-C9-spot-checks.md | 保留 7 | 0 | 3（C2-001/002、C7-001） | 4（C9-001~004） | 7 | C4/C5/C8 清洁 |
| **缺陷条目合计** | | **4** | **17** | **8** | **29** | |
| C10-test-protection.md | 缺保护登记 8 | — | 8（P2 缺保护力，与缺陷本体分开计数） | — | 8（单列） | 全部经复核保留 |

### 1.2 裁定方法

1. **口径**：同一根因同一失效层 = **同一缺陷**（取一主条目，其余交叉引用）；同一根因不同失效层 = **同族相邻**（合并为单一缺陷族/单一修复工单，修复验证须同时覆盖全部成员失效面）；根因不同 = **不同**（各自立案，可 cross-reference）。
2. **主条目选择**：证据链最完整、判级最准确者；**族内最终严重度取最高**（复核保留判级不因归并而湮没）。
3. **承重锚点独立验证**：裁定不依赖条目转述，对 4 组归并的载重代码锚点重读 live code 核实——
   - `StreamScalarFunctionResolver.java`（L31 volatile default / L46 裸 LinkedHashMap / L49-51 公开 register / L59-71 编译期面 / L73-76 close 空壳 / L88-97 逐记录 call）：证实 C1-001、C3-002、C2-001 为同一代码面，C6-003 为同类不同机制；
   - `ReplayableCdcSourceFunction.java`（ReplaySubscription L133-163：裸 ArrayList onCancelTasks、cancel 迭代非幂等、无逐回调隔离）：证实 C3-004 的全部内容被 C6-002 包含；
   - `StreamModelDslBuilder.materializeSideInputs`（L1526-1550：snapshot 裸存、无 unmodifiableMap）：证实 C1-004、C2-002、C3-003 为同一物化点；
   - `SqlScalarXplPrinter.printBetween`（L291-305，test 两次传入 printBinaryOp）与 `StreamSqlExprCompiler.compileBetween`（L489-500，`test.eval(r)` ×2）：证实 C9-003/C9-004 为同一语义根因的两通道实现。
4. **跨单元对账**：逐条对照 cra-index P1 台账（WI 1.1 × 10、WI 1.2 × 10，见 §4.5）。

---

## 2. 合并裁定表

### 2.1 合并组（6 个多成员缺陷族）

| 族 ID | 判定 | 成员条目（→角色） | 主条目 | 最终严重度 | 修复验证须覆盖的失效面 |
|---|---|---|---|---|---|
| **MA-01** | 同一缺陷 | C1-001（主）、C3-002、C2-001 | **C1-001** | **P1**（族内最高，C1 复核保留） | ①逐记录 `call()` 读 × `register()` 写竞态（xpl 声明面，SqlScalarXplPrinter 发射的 UDF 分支）②编译期 `contains/resolve/registeredNames/namesList` 迭代 × put 的 CME 掩盖 fail-fast ③`registerAsDefault` 运行期静默换绑已编译作业 ④sql.md §3a「编译期一次解析」docs bug 同修 |
| **MA-02** | 同族相邻 | C1-004（主）、C2-002、C3-003 | **C1-004** | **P2** | ①xpl 体写内层 snapshot map → 跨记录/跨 subtask 污染（语义违约）②bindings 直通 scope variables 的写穿透（未声明赋值 → UOE 崩溃，诊断面错位）③应用侧线程写同一活引用 → 跨线程撕裂（F1 形态）。修复点两处：materializeSideInputs 冻结 + newCallScope(bindings) 隔离/overlay，缺一不闭合 |
| **MA-03** | 同一缺陷（同代码点） | C3-001（主）、C6-001、C7-001 | **C3-001** | **P2**（两维度复核同向降级后终裁） | ①readReplayIndex 非数字 NFE 注入 ②writeReplayIndex offset store 故障 ③cancelled 单标志无失败位（getCancelReason 失败后仍返回 CANCEL_REASON_STOP）。修复走父类 pendingError 通道、仅成功路径 cancel；验证须含 C10-005 失败注入用例且不破坏 L130 cancel 不抛锚 |
| **MA-04** | 同一缺陷（C6-002 严格包含 C3-004） | C6-002（主）、C3-004 | **C6-002** | **P2** | ①裸 ArrayList：append/remove × cancel 迭代并发（CME/漏执行）②cancel 非 check-and-set、双 cancel 路径回调双跑 ③无逐回调异常隔离 ④`stop()` 绕过注册表的语义不对称。修复建议组合平台 `io.nop.commons.lang.impl.Cancellable` |
| **MA-05** | 同族相邻 | C6-004（主）、C3-006 | **C6-004** | **P2** | ①`RecordColumnAccess.GETTER_CACHE`：动态类族场景反射表永驻 ②`SqlRowAggregateOps.CACHE`：动态编译服务形态 spec JSON 累积（该载体现有 javadoc 包络声明，修复以「声明拒绝条件或上界」闭合）。两载体同一工单，验证双面 |
| **MA-06** | 同族相邻 | C9-003（主）、C9-004 | **C9-003** | **P3** | ①print 通道：BETWEEN test ×2、CASE test ×M、复核补充的 null-guard 多嵌入面（printBinary ×2 / printAnd/printOr ×3 / printNot/printUnary ×2 / printIn ×(N+1)）②eval 通道：compileBetween test.eval ×2（修正第 1 轮「eval 通道单次求值」对照表述）。修复 = print 通道 let 前置绑定 + eval 通道局部变量提取；WI9 等价钉补「副作用 UDF 置于 BETWEEN/CASE test」用例 |

### 2.2 独立立案（14 条，判定 = 不同）

| 立案 ID | 判定说明 | 严重度 |
|---|---|---|
| C1-002 | ROOT_SCOPE 静态共享 eval 根作用域无写守卫——与 MA-02 同经 `newCallScope` 漏斗但根因不同（结构性不变量侵蚀 vs 快照契约穿透；MA-02 修 bindings 值，本条修根作用域守卫），不同 | P2 |
| C1-003 | runEntered CAS 非粘性（deployTask 槽位替换交叠窗口）——与 C6-007 同文件同「跨 run 复用契约面」但机制独立（守卫竞态 vs cancelled 确定性陈旧），根因不同、修复不同（attempt 身份守卫 vs 入口复位标志），**裁定不同、维持两立**（与两维度复核一致；两修复须联调并共受 C10-006 契约钉反向约束） | P2 |
| C1-005 | build() 就地改写调用方持有的 StreamModel——独立 | P2 |
| C3-005 | GETTER_CACHE computeIfAbsent 锁内反射内省（类加载锁倒置隐患）——与 MA-05 同载体不同根因（回调副作用位置 vs 无逐出）、不同失效层（死锁挂起 vs 内存滞留），**不同**，与 MA-05 互引 | P2 |
| C3-007 | ReplayEngine 取消无 interrupt 响应点（有界幽灵窗口）——与 MA-03 同文件不同机制（取消响应性 vs 失败语义），不同 | P3 |
| C3-008 | region 重启 Phase-3 流水线下兄弟 initializeState REPLACE 清零已推进游标（复核修正机制：后续 checkpoint 携带滞后源位点）——与 C6-007 同文件不同机制（restore-vs-run 交叠 vs 跨 run 标志残留），不同 | P1 |
| C3-009 | 共享 UDF × 各子任务 open() 覆写共享 RuntimeContext → 键控状态系统性收敛单一 backend（顺序 open 亦然；根因载体 ProcessOperator 在 nop-stream-core）——与 C1-006 同类不同机制（系统性 last-writer-wins vs 良性惰性初始化竞争），**不同**，互引 | P1 |
| C6-003 | StreamScalarFunctionResolver 静态 default 只注册不清除（close 空壳/无解绑/替换不关旧值）——与 MA-01 同类不同机制（生命周期对称性 vs 并发访问纪律），**不同**，互引 | P2 |
| C6-005 | ReplayEngine.subscribe 无重复订阅防护（每调用新起 emitter、多订阅共享 cancelled）——与 C3-007/MA-04 同文件不同契约面，不同 | P2 |
| C6-007 | cancelled volatile 跨 run 永不复位 → 重启零事件假成功 EOS（纯顺序陈旧，P1）——见 C1-003 行，不同、维持两立 | P1 |
| C1-006 | UserHistoryEnricher.lastCityState 无同步惰性初始化（后果良性、模式传播风险）——独立 | P3 |
| C1-007 | InMemoryBeanFunctionResolver 活视图冒充 snapshot——独立 | P3 |
| C9-001 | keyBy/join/tap 每记录双跳 scope 链分配放大——独立（与 MA-06 同为热路径但非同一根因） | P3 |
| C9-002 | 连续聚合 begin() 每记录重复缓存查找 ×2——与 MA-06 同属「重复功」表象但根因不同（同表达式冗余调用 vs 编译产物子表达式重复求值）、修复不同（局部变量提取 vs 通道改造），不同 | P3 |

**条目覆盖核对**：MA-01(3) + MA-02(3) + MA-03(3) + MA-04(2) + MA-05(2) + MA-06(2) = 15 条入族；独立立案 14 条；15 + 14 = **29 = §1.1 缺陷条目合计**，每条恰好落入一处，无遗漏无重复。驳回项 C6-006 不计入。

---

## 3. 独立裁定与复核提议不一致处

**推翻复核提议的裁定：0 处。** 各维度复核已登记的 cross-ref 全部经独立裁定维持；另有 5 处裁定动作超出登记 cross-ref（追加归并/分类学精化），证据如下——

| # | 裁定动作 | 与复核提议的关系 | 证据 |
|---|---|---|---|
| 1 | C2-001 = C1-001 判「同一缺陷」并入 MA-01，终判级 P1 | **一致**（抽查复核原提议「随 C1-001 P1 归并」） | live code 实核：两条目引文为同一文件同一行段（L46-51/88-97），证据逐字相同 |
| 2 | C2-002 与 C1-004 及新成员 C3-003 判「**同族相邻**」（抽查复核原表述为「同一机制……归并」） | **分类学精化**（归并方向一致；「同一缺陷」改判「同族相邻」） | 三条目失效层不同构：C1-004 = 内层快照被写（语义违约）、C2-002 = scope variables 写穿透（UOE 面，其修复点在 newCallScope 而非 materializeSideInputs）、C3-003 = 应用侧活引用跨线程撕裂（数据竞争）。修复须两处落点（物化冻结 + bindings 隔离）方可全闭合，故按口径属「同一根因不同失效层」。live code 实核 materializeSideInputs L1534-1548 snapshot 裸存 + 无 unmodifiableMap |
| 3 | **C3-002 并入 MA-01**（复核未登记此 cross-ref） | **追加归并（新增）** | C3-002 证据片段与 C1-001 逐字同源（同一 `functions` LinkedHashMap、同一 `register`/`call`、同一 namesList CME 变体），根因失效层完全重合；C3-002 自身误报排除节亦申明其立案面即「列表容器并发纪律」 |
| 4 | **C3-003 并入 MA-02**（复核未登记此 cross-ref） | **追加归并（新增）** | 与 C1-004 同一物化代码点（materializeSideInputs L1526-1550）、同一 owner doc 契约锚（user-guide WI11「冻结只读快照」）、同一修复点（unmodifiableMap/防御拷贝）；C3-003 的并发撕裂面是该根因的失效层之一而非独立根因 |
| 5 | **C3-004 并入 MA-04（C6-002 为主）**（复核未登记此 cross-ref） | **追加归并（新增）** | live code 实核 ReplaySubscription L133-163：C3-004 的唯一缺陷面（裸 ArrayList × cancel 迭代）被 C6-002 缺陷②完全包含；C6-002 另有三缺陷 + stop 不对称 + 平台 Cancellable 契约基准，为严格超集。C3-004 自身已声明「不与 C3-001 重复计」，与 MA-03 无涉 |
| 6 | **C3-006 并入 MA-05（C6-004 为主）**（复核未登记此 cross-ref） | **追加归并（新增）+ 跨维度张力消解** | 同一缺陷类（进程级静态缓存：键假设静态有限、无逐出、无上界），两失效载体（GETTER_CACHE 动态类族 / SqlRowAggregateOps.CACHE 动态编译服务）。张力消解：C6 维度配对表曾判 SqlRowAggregateOps.CACHE「已声明有界，非缺陷」，C3 维度终表保留 C3-006（P3）——裁定取 C3 终表为准（两表为不同判定对象：C6 表判「配对完整性语境」，C3 判「包络声明缺口」），族修复「声明拒绝条件或加上界」同时满足双方口径 |
| 7 | C7-001 = C3-001 = C6-001 判「同一缺陷」，主条目 C3-001，P2 | **一致**（C3/C6 复核同向降级 P2、C7 复核同向并案；C6-001/C7-001 条目原文均显式申明随 C3-001 归并） | 三条目同一代码点 runReplay L165-190、同一失效层（失败伪装 EOS），C7-001 的 getCancelReason 观察为同一根因的同一修复面（失败位） |
| 8 | C6-007 与 C1-003 维持两立 | **一致**（C6 复核锚点 3 同向；C10-006 已预登记两修复须联调） | 机制独立（CAS 守卫竞态 vs 确定性标志陈旧）、修复不同（attempt 身份 vs 入口复位）、判级差异（P2 vs P1）由机制强度决定 |
| 9 | C9-003 + C9-004 判「同族相邻」（复核授权裁定） | **行使授权，非不一致** | live code 实核双通道：print 通道 test 文本两次嵌入（L295/297）、eval 通道 `test.eval(r)` 两次调用（L495/496）——同一语义根因（BETWEEN 降维复制 test）两个失效层，单一修复工单双通道覆盖 |

---

## 4. 合并后统计

### 4.1 总量

| 项 | 数值 |
|---|---|
| 原始保留条目（C1+C3+C6+抽查） | **29**（P1×4、P2×17、P3×8） |
| 并入缺陷族的成员条目 | 15（分布于 6 族） |
| 独立立案 | 14 |
| **合并后缺陷族/立案单元总数** | **20**（6 多成员族 + 14 独立） |
| P0 缺陷族 | **0** |
| P1 缺陷族 | **4** |
| P2 缺陷族 | **10** |
| P3 缺陷族 | **6** |
| 自洽核对 | 15 + 14 = 29 ✓；4 + 10 + 6 = 20 ✓ |
| C10 缺保护登记 | 8 条（P2，单列不并入缺陷族） |

### 4.2 P1（4 族）

| 族 ID | 主条目 | 标题 | 涉及模块 |
|---|---|---|---|
| MA-01 | C1-001（成员 C3-002、C2-001） | UDF 注册表非线程安全：逐记录 call() 无锁读 × 公开 register() 任意线程写，无守卫无生命周期文档（+运行期静默换绑 + sql.md §3a docs bug） | nop-stream-sql |
| C3-008 | C3-008 | region 重启 Phase-3 流水线下兄弟 initializeState REPLACE 清零已推进游标 → 后续 checkpoint 携带滞后源位点 → 恢复重放已反映窗口 | nop-stream-fraud-example（编排面关联 nop-stream-runtime） |
| C3-009 | C3-009 | 共享 UDF × 各子任务 open() 覆写共享 RuntimeContext → 并行 ≥2 键控状态系统性收敛单一 backend（顺序 open 亦然） | nop-stream-fraud-example + nop-stream-core（根因载体 ProcessOperator/AbstractUdfStreamOperator） |
| C6-007 | C6-007 | cancelled volatile 跨 run 永不复位 → 区域重启共享实例上重启 run 循环顶零事件静默 EOS 假成功 | nop-stream-fraud-example |

### 4.3 P2（10 族）

| 族 ID | 主条目 | 标题 | 涉及模块 |
|---|---|---|---|
| MA-02 | C1-004（成员 C2-002、C3-003） | sideInput「冻结只读快照」契约零强制：快照裸存直通逐记录 xpl 作用域（写污染/UOE 写穿透/跨线程撕裂三失效面） | nop-stream-flow（机制锚 nop-core EvalScopeImpl） |
| MA-03 | C3-001（成员 C6-001、C7-001） | ReplayEngine 失败被 catch-all+finally-cancel 伪装为正常 EOS，绕开父类可用而未用的 pendingError 通道 | nop-stream-fraud-example（对照面 nop-stream-connector-debezium） |
| MA-04 | C6-002（成员 C3-004） | ReplaySubscription 取消回调注册表三重偏离平台 Cancellable 契约（非幂等/无同步/无隔离 + stop 绕过） | nop-stream-fraud-example（契约基准 nop-commons） |
| MA-05 | C6-004（成员 C3-006） | 进程级静态缓存无上界无逐出（GETTER_CACHE / SqlRowAggregateOps.CACHE 双载体） | nop-stream-sql |
| C1-002 | C1-002 | XplFunctionSupport.ROOT_SCOPE：JVM 级静态共享 eval 根作用域，安全性依赖结构性不变量、无运行时写守卫 | nop-stream-flow（机制锚 nop-core） |
| C1-003 | C1-003 | DirectoryFileSourceFunction 的 CAS 单读者守卫非粘性：deployTask 槽位替换交叠窗口内新尝试静默零发射完成 | nop-stream-fraud-example（触发路径关联 nop-stream-core/runtime 恢复面） |
| C1-005 | C1-005 | StreamModelDslBuilder.build() 就地改写调用方持有的已解析 StreamModel，模型构建后成为被消耗的共享可变状态 | nop-stream-flow |
| C3-005 | C3-005 | RecordColumnAccess.GETTER_CACHE computeIfAbsent 锁内反射内省——类加载锁倒置死锁隐患（理论） | nop-stream-sql |
| C6-003 | C6-003 | StreamScalarFunctionResolver 静态 default 只注册不清除：close 空壳、无解绑、替换不关旧值 | nop-stream-sql |
| C6-005 | C6-005 | ReplayEngine.subscribe 无重复订阅防护：每调用新起 emitter 线程、多订阅共享 cancelled 标志 | nop-stream-fraud-example |

### 4.4 P3（6 族）

| 族 ID | 主条目 | 标题 | 涉及模块 |
|---|---|---|---|
| MA-06 | C9-003（成员 C9-004） | SQL 编译产物子表达式重复求值：BETWEEN test 双通道 ×2、CASE test ×M、null-guard 多嵌入面 | nop-stream-sql（print + eval 双通道） |
| C1-006 | C1-006 | UserHistoryEnricher.lastCityState 跨子任务共享 bean 上无同步惰性初始化（后果良性、模板传播风险） | nop-stream-fraud-example |
| C1-007 | C1-007 | InMemoryBeanFunctionResolver.getBeans() 活视图冒充「snapshot」，item-14 部署快照并发撕裂面 | nop-stream-flow |
| C3-007 | C3-007 | ReplayEngine 取消无 interrupt 响应点（有界幽灵窗口 + offset 越过未确认记录） | nop-stream-fraud-example |
| C9-001 | C9-001 | keyBy/join/tap 每记录双跳 scope 链——中间空 scope 纯属多余分配（2×） | nop-stream-flow |
| C9-002 | C9-002 | 连续聚合 begin() 每记录重复查找复合函数缓存 ×2 | nop-stream-sql |

### 4.5 C10 缺保护登记 ↔ 缺陷族对应（修哪族需补哪测试）

| C10 登记 | 对应缺陷族（主条目） | 收口要求 |
|---|---|---|
| C10-001 | MA-01（C1-001） | resolver 并发压测 trip-wire + namesList 无 CME 子用例 + 契约锚（与修复选定语义二选一钉住）——MA-01 收口门 |
| C10-002 | C3-008 | 同实例 restore-vs-run 交错测试（多文件「已完成文件」变体为稳健红形态）——C3-008 收口门 |
| C10-003 | C3-009 | LOCAL env P=2 有状态 keyed UDF 隔离测试——**core 根因修复的回归锚**（缺陷态红、根因修复后绿），归属登记须注明 |
| C10-004 | C6-007 | cancel 后同实例重跑断言重发 + 重跑后快照游标单调——C6-007 收口门（须「run 中 cancel」变体方在缺陷态红） |
| C10-005 | MA-03（C3-001） | 失败注入（非数字 replay index）断言 FAILED 语义；保留 L130 cancel 不抛锚——MA-03 收口门 |
| C10-006 | C1-003 | 重叠窗口注入测试；**附全矩阵唯一 trip-wire 反向约束**：修复不得使并行首入落败者继续读取（runEnteredGuardPreventsDoubleEmission 会红）——C1-003 与 C6-007 修复联调的共同门 |
| C10-007 | 解耦登记（规模/边界包络缺口） | MultiFile / BufferBoundary / 空目录 EOS 契约探针；与 C3-008 修正机制面（已完成文件游标）和 C6-007 假成功形状直接相关，建议随 C3-008/C6-007 修复批次落地 |
| C10-008 | 测试基建缺口（关联 C3-007、MA-04、C6-005） | emitter 线程有界终止断言 + C6-005 修复定型后补 double-subscribe 契约用例——随对应族修复落地 |

### 4.6 跨单元关联与 WI 1.1 / WI 1.2 台账对账

**对账结论：本单元 20 个缺陷族/立案与 WI 1.1（10 条 P1）+ WI 1.2（10 条 P1）台账零重复立项**。逐条核对：

| WI 1.1 P1 台账 | 对账 | | WI 1.2 P1 台账 | 对账 |
|---|---|---|---|---|
| C1-001 SplitEnumerator 无锁直调 | 与 MA-01 同编号不同缺陷（本单元无 SplitEnumerator），clean | | MA-02 RocksDB gauge | 本单元无 RocksDB，clean |
| C1-006 任务分配账本 | clean | | MA-03 CEP copyForSubtask 引用透传 | 与 C3-009 同缺陷类（浅拷贝共享）不同载体（CEP 算子 vs ProcessOperator/RuntimeContext），**不重复**，关联登记 |
| C4-001 LOCAL 池互等死锁 | 本单元无线程池，clean | | MA-04 Avro registry computeIfAbsent 锁内 HTTP | 与 C3-005 同缺陷类（锁内副作用）不同载体/单元，**不重复**，关联登记 |
| C4-002 CachingTableLookup | clean | | MA-05 2PC 持久化登记无保留 | clean |
| C4-009 OpsJobManager.submit | clean | | MA-06 Debezium 引擎占共享池 | 与 C3-007 不同（幽灵窗口 vs 引擎阻塞共享池），clean |
| C5-010 PollingJdbc 全量物化 | clean | | MA-07 Debezium 停止/启动无终结保证 | 与 C1-003 同族先例（重启交叠）不同载体，**不重复**，关联登记 |
| C7-011 standby checkpoint | clean | | MA-08 RocksDB 全量 checkpoint | clean |
| C8-003 msg_queue 无界 | 与 MA-05 同「无界」类不同载体，clean | | C3-004 Debezium 枚举器双登记 | clean |
| C8-011 launch poller 饥饿 | clean | | C8-001 Debezium reader offset 领先 | 与 C3-008 同「checkpoint 位点一致性」主题不同载体/机制，**不重复**，关联登记 |
| FAM-1 materialization store 无界 | 与 MA-05 同类不同载体，clean | | C8-006 Kafka 2PC 事务裕度 | clean |

**跨单元关联单列（不重复计数，建议关联登记）**：

1. **C3-009 → nop-stream-core（WI 1.1 单元）**：根因载体 ProcessOperator.copyForSubtask / AbstractUdfStreamOperator.open。WI 1.1 全目录零 ProcessOperator/RuntimeContext 面登记（C3 复核核实 + 本裁定对账确认），**该面对 WI 1.1 是覆盖空洞而非重复项**。建议：(a) 对 runtime/core 单元补关联登记（含状态 RichFunction UDF 的失效面外溢需子项复核）；(b) C10-003 测试作为 core 根因修复的回归锚挂 core 工单。
2. **C3-008 → nop-stream-runtime（编排面）**：restartRegion Phase-3「rebuild→restore→submit 流水线」在 runtime 单元，WI 1.1 对 SupervisionLoop 仅涉 C4/C8 维度面。建议对 runtime 单元关联登记编排侧修复候选（全部 rebuild+restore 完成后再统一 submit，可同时闭合 Debezium offset 兄弟面）。
3. **C1-003 / C6-007 → nop-stream-core/runtime 恢复路径**：deployTask 槽位替换与 deepCopy 实例共享语义面；同族先例 WI 1.2 MA-07（重启交叠）已 open，关联不重复。
4. **C3-005 / MA-05 → 缺陷类关联**：与 WI 1.2 MA-04（锁内副作用）、WI 1.1 C8-003/FAM-1（无界账本）同缺陷类不同载体，修复时可共享模式与测试样式。

---

## 5. 对修复批次划分的建议（仅建议，不做决定）

> P1 四族按 cra-index 纪律全部进入 Phase 8 批量修复；P2 十族按 7.1 逐项裁决（in-scope / deferred+Why Not Blocking）；P3 六族为优化候选。以下按「同模块同工单、跨单元单列、测试随批」原则建议四批——

- **批次 A：nop-stream-sql 通道（1×P1 + 3×P2 + 3×P3）**：MA-01（P1）+ C6-003（同类生命周期面，同文件一次收口）+ C3-005（同载体）+ MA-05 + MA-06 + C9-002。可再分 A1（并发/生命周期：MA-01、C6-003、C3-005，含 sql.md §3a docs bug 同修）与 A2（包络+热路径：MA-05、MA-06、C9-002）。收口测试：C10-001；MA-06 验证须 print/eval 双通道 + WI9 副作用 UDF 钉。
- **批次 B：nop-stream-flow 构建期/作用域（4×P2 + 2×P3）**：MA-02 + C1-002（同经 newCallScope 漏斗，修拒不冲突）+ C1-005 + C1-007 + C9-001。MA-02 验证三失效面全覆盖；C1-002 若选 EvalScopeImpl write-guard 方案则触发 nop-core protected area（plan-first），建议优先选「每次新建根作用域」零依赖方案。
- **批次 C：fraud-example source 生命周期（3×P1 + 4×P2 + 2×P3，高联调密度）**：C3-008 + C6-007 + C1-003（同文件跨 run 契约面，三修复须联调且共受 C10-006 契约钉反向约束）+ MA-03 + MA-04 + C6-005 + C3-007 + C1-006。收口测试：C10-002、C10-004、C10-005、C10-006、C10-007（建议随批）、C10-008（随批）。MA-03 修复走父类 pendingError 通道，如需动父类 protected 缝则涉及 connector-debezium 单元协同。
- **批次 D：跨单元协同（单列，不入本单元工单闭合）**：C3-009 的 core 根因修复（ProcessOperator/RuntimeContext 按子任务装配）+ C3-008 的 runtime 编排面修复候选。C10-003 回归锚挂 core 工单；本单元侧可先落文档/声明性防御（不替代根因）。
- **顺序建议**：C 批（P1 密集 + 测试空洞最大）最先；A1（唯一 P1 之 MA-01）并行；B/D 随后。每批以「先落 C10 缺保护测试（缺陷态红）→ 修复（转绿）」同时完成修复验证与回归锚，避免无锚合入。

---

## 附：29 条保留条目 → 裁定结果完整映射（防遗漏/防重复核对表）

| 条目 | 原终判级 | 裁定去向 | | 条目 | 原终判级 | 裁定去向 |
|---|---|---|---|---|---|---|
| C1-001 | P1 | MA-01（主） | | C6-001 | P2 | MA-03（成员） |
| C1-002 | P2 | 独立 | | C6-002 | P2 | MA-04（主） |
| C1-003 | P2 | 独立 | | C6-003 | P2 | 独立 |
| C1-004 | P2 | MA-02（主） | | C6-004 | P2 | MA-05（主） |
| C1-005 | P2 | 独立 | | C6-005 | P2 | 独立 |
| C1-006 | P3 | 独立 | | C6-007 | P1 | 独立 |
| C1-007 | P3 | 独立 | | C2-001 | P2 | MA-01（成员） |
| C3-001 | P2 | MA-03（主） | | C2-002 | P2 | MA-02（成员） |
| C3-002 | P2 | MA-01（成员） | | C7-001 | P2 | MA-03（成员） |
| C3-003 | P2 | MA-02（成员） | | C9-001 | P3 | 独立 |
| C3-004 | P2 | MA-04（成员） | | C9-002 | P3 | 独立 |
| C3-005 | P2 | 独立 | | C9-003 | P3 | MA-06（主） |
| C3-006 | P3 | MA-05（成员） | | C9-004 | P3 | MA-06（成员） |
| C3-007 | P3 | 独立 | | （C6-006 | 驳回 | 不入裁定） |
| C3-008 | P1 | 独立 | | C3-009 | P1 | 独立 |
