# 全仓代码审查报告索引

> 轮次目录：`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/`
> 关联 roadmap：`ai-dev/backlog/full-code-review-roadmap.md`
> 纪律：每份审计报告产出即在本索引登记一行；closure audit 通过后才允许把对应工作项标 `done`（在 roadmap 中回填）。

## 报告清单

| 工作项 | 报告文件 | P0 | P1 | P2 | P3 | 报告状态 | closure audit |
|---|---|---|---|---|---|---|---|
| 0.1–0.4 | `00-scope-and-dimension-matrix.md` | — | — | — | — | done（编制期产物） | —（编制期自查，roadmap 对抗审查替代） |
| 0.5 | `00-scope-and-dimension-matrix.md`（§1/§2/§6 基线刷新 + v6 新增子模块登记表；执行载体 plan 2307） | — | — | — | — | done（矩阵刷新，无新发现） | 独立子 agent（见 plan 2307 Closure 段） |
| 1.1 | `1.1-dependency-graph-and-boundaries.md` | 0 | 0 | 2 | 3 | done（裁决：通过——零环/api 零污染/T1 装配零越界；P2×2 入 R1.0，P3×3 watch；初版 P2-1.1-003 经 closure audit 证伪撤回） | 独立子 agent 四轮（FAIL→FAIL→残留→PASS，见 plan 2308 Closure 段） |
| 1.2 | `1.2-xlang-structure-pipeline-audit.md` | 0 | 1 | 10 | 18 | done（裁决：通过——链路全连通正结论；P1×1 包级 SCC 三档治理 + P2×10 进 R1.0；JsPromise already-fixed；XLangASTOptimizer 实为生成物） | 独立子 agent（见 plan 2309 Closure 段） |
| 1.3 | `1.3-nop-core-structure-audit.md` | 0 | 0 | 3 | 29 | done（裁决：通过——四依赖边合规/VFS 链连通/初始化幂等正结论；P2×3 进 R1.0：SCC(10) 两红线治理+disabled-initializers 死配置+reinitialize 失败残留；watch×5） | 独立子 agent（见 plan 2310 Closure 段） |
| 1.4 | `1.4-kernel-utils-codegen-xdefs-audit.md` | 0 | 0 | 6 | 34 | done（裁决：通过——模板链 16/16 抽样、xdef 114/114 闭合正结论；P2×6 工具巨类治理进 R1.0；docs 移交 4 项；驳回 1（LittleEndian 证伪）） | 独立子 agent（见 plan 2311 Closure 段） |
| 1.5 | `1.5-xlang-backend-misc-audit.md` | 0 | 0 | 5 | 6 | done（裁决：通过——ExecToJavaTranslator 保留+monitor、truffle 差集 0 缺口、e2e 位置合理三项正结论；P2×5 测试缺口带修复精算进 R1.0；驳回 1；过程安全事件 1 起已登记） | 独立子 agent（见 plan 2312 Closure 段） |
| 1.6 | `1.6-core-framework-structure-audit.md` | 0 | 0 | 3 | 9 | done（裁决：通过——组内无环/职责纯度全合格正结论；CORE-02 still-deferred、弱加密 WARN 现状、CORE-01 docs 闭环三项标定；P2×3 进 R1.0） | 独立子 agent（见 plan 2313 Closure 段） |
| 1.7 | `1.7-persistence-servicefw-boundary-audit.md` | 0 | 0 | 13 | 18 | done（裁决：通过——分层零违规/单桥/api 纯净正结论；P2×13 进 R1.0（AST 模板包死代码 3494 行 plan-first 工单队首）；降级 3——初审 P1×2 经复核降 P2） | 独立子 agent（见 plan 2314 Closure 段） |
| 1.8 | `1.8-integration-periphery-audit.md` | 0 | **1** | 6 | 20 | done（裁决：通过附 P1×1——file/credential-app 半拆进 R1.0；红旗法 0 反向边正结论；根级资产 47 项归属裁定 3 条；降级 1） | 独立子 agent（见 plan 2315 Closure 段） |
| 1.9 | `1.9-jq-refactor-first-audit.md` | 0 | **1** | 9 | 21 | done（裁决：通过附 P1×1——jq 工具接缝资源耗尽（OOM 双通路实证+默认链无门禁）进 R1.0；430/430 基线、refactor 四段全通正结论） | 独立子 agent（见 plan 2316 Closure 段） |
| 1.10 | `1.10-bytecode-duckdb-first-audit.md` | 0 | **2** | 5 | 26 | done（裁决：通过附 P1×2——bytecode exit 1 契约缺口+ObligationToken 死循环（第 1 轮正结论被推翻并落账）进 R1.0；duckdb 契约与 doc 一致正结论） | 独立子 agent（见 plan 2317 Closure 段） |
| 2.1 | `2.1-orm-ormmodel-deep-audit.md` | 0 | **2** | 15 | 41 | done（裁决：通过附 P1×2——detach 一缓契约混杂+DELETING 装配重置（会话状态机通道）进 R1.0；模型校验矩阵全绿/乐观锁闭环正结论；check2 三 P1 全部 already-fixed；降级 1——dim04-17 探针实证后 P1→P2） | 独立子 agent（见 plan 2318 Closure 段） |
| 2.2 | `2.2-orm-eql-deep-audit.md` | **3** | **11** | 17 | 29 | done（裁决：不通过附 P0×3——否定编码根因簇（NOT `_some` 反转/嵌套 `_all` 量词丢失/NOT+嵌套否定错位，测试钉住错误期望）触发即时通道→plan 2320；P1×11 含 DML 渲染缺口族+EQL DML 缺实体过滤器；check2 P0 join 放大 already-fixed 但 1:1 数据掩盖缺口坐实；60 条唯一发现全探针实证） | 独立子 agent（见 plan 2319 Closure 段） |
| 2.3 | `2.3-dao-migration-dbtool-deep-audit.md` | 0 | **1** | 19 | 36 | done（裁决：通过无 P0 附 P1×1——callFunc 绑定偏移进 R1.0；**check2 点名 4 条全部 already-fixed**（migration 两 P0 双探针实测）；方言缺口族×5+事务簿记×4+recordMigration 翻倍等 P2×19；降级 5、驳回立案 1（dup≡P3-2.1-024）） | 独立子 agent（见 plan 2321 Closure 段） |
| 2.4 | `2.4-nosql-cdc-deep-audit.md` | 0 | **1** | 12 | 25 | done（裁决：通过无 P0 附 P1×1——rate_limit.lua 集群 CROSSSLOT 进 R1.0；**check2 P0 复裁定 false-positive（三重独立复现）**、P1-2 already-fixed、**P1-3 still-live（MFA codec 类型不对称）进 R1.0 输入**；RESP3 逐原语矩阵 10 通道全兼容；降级 3、升级 1、跨维度去重 3） | 独立子 agent（见 plan 2322 Closure 段） |
| 2.5 | `2.5-orm-model-compliance-scan.md` | 0 | **5** | 14 | 21 | done（裁决：通过无 P0 附 P1×5——UK/唯一索引零物化族×4（job/batch/retry 部署层防线缺失；a20 复核反转升格）+字段长度覆盖族×1（rule precision=50 静默覆盖 json-4k）；**平台级发现：DDL 生成器不物化 `<index>`**；维度 8 独立复算 67/51/16 逐文件证实；demo 3 条单列；降 1 改判 2 驳 0） | 独立子 agent（见 plan 2323 Closure 段） |
| 2.6 | `2.6-biz-graphql-deep-audit.md` | 0 | **7** | 14 | 12 | done（裁决：不通过附 P1×7 三族——GraphQL fragment 校验族×2+DataLoader 挂死、权限公开面族×3（approval-support/纯 xbiz 静默公开 16 项/@BizLoader 死注解）、后台直调零事务；**check2 九条点名全部 already-fixed**（两条语义级验证）；CrudBizModel 拆分评估交付（宽职责单管线/700 行可下沉/禁机械拆类）；fail-open 三处裁定交付；改判 2 驳 0） | 独立子 agent（见 plan 2324 Closure 段） |
| 2.7 | `2.7-bizmodel-ioc-compliance-scan.md` | 0 | 0 | **4** | 20 | done（裁决：通过无 P0/P1 附 P2×4——孤儿注册族×2（MakerChecker 三重失配⊕dim13-03 合并/DatasetAuth 完整生成仅注册断）+_module 缺失×5 JAR+RetryScanner 同型先例未同步；**NopIoC 硬规则全仓 211 文件零违规**（独立复算）；驳回 1 假阳性反向发现 i18n 缺失族群 447/3987；降 1 新增 1） | 独立子 agent（见 plan 2325 Closure 段） |
| 2.8 | `2.8-stream-sql-first-audit.md` | **1** | **3** | 12 | 14 | done（裁决：不通过附 P0×1——SumAgg 混型累加静默错值（探针实测三例错值一例塌缩）触发即时通道→plan 2327 已修复（三通道精确累加器+双单调旗标，86/86 全绿）；P1×3（null 键误配/putAll seq 丢失/completeUnmatched 双发）；**prior art 零推翻**（WI12/WI13/WI17 已修项 live 复核成立）、2.2 不波及 stream SQL 路径；30 条全保留、降 1 驳 1；定向测试排除 2 个预存失败后三模块全绿） | 独立子 agent（见 plan 2326 Closure 段） |
| 3.1 | `3.1-wf-deep-audit.md` | 0 | **2** | 11 | 10 | done（裁决：通过无 P0 附 P1×2——approval-support 绕引擎双事实源（与 P1-2.6-005 分立互补同批修复）+超时 dueAction 通道被 allowCallByUser 阻断（与 job bean 失配叠加同批）；**check2 12 条点名零虚假申报**（P1×4 零推翻+P2×8 含 2 still-live）；WF-01/02/03 六条全标定（F-WF-02-1 回灌 3.2）；维度 22 引擎侧适配交付；降 0 驳 0） | 独立子 agent（见 plan 2328 Closure 段） |
| 3.2 | `3.2-task-deep-audit.md` | 0 | 0 | **10** | 13 | done（裁决：通过无 P0/P1 附 P2×9——恢复完整性族×4（persist 死契约〔P1 降级高位〕/taskInputs 死代码/ACTIVE resume 跳过 initInputs/迭代边界顶替）+装配契约族×2（store 无生产装配/CallTaskStep 绕过门控）+错误列残留+**F-WF-02-1 深审维持 still-live 修复裁定交付**；continuation-skip P0 复核 already-fixed 与 6.4 一致维持；check 系列 14 条盘点零遗漏；降 1 驳 0） | 独立子 agent（见 plan 2329 Closure 段） |
| 3.3 | `3.3-batch-deep-audit-a.md` | 0 | **3** | 11 | 14 | done（裁决：不通过附 P1×3——orm-reader 节点解析未接线静默全表加载/processor order 声明失效/allOf 掩蔽 fail-fast 根因终态误持久化 KILLED 不可重启〔TestBatchTaskCheckpoint flaky 收编升级为真产品缺陷〕进 R1.0；**check2 五条 P1 标定全部 already-fixed**（:19/:50/:75/:125 修复+测试复跑绿、:101 归 3.4 对照一致）；28 条全保留降 0 驳 0） | 独立子 agent（见 plan 2330 Closure 段） |
| 3.4 | `3.4-batch-deep-audit-b.md` | 0 | **2** | 9 | 16 | done（裁决：不通过附 P1×2——snapshotBuilder restore 空列表静默丢弃 chunk/historyStore mandatory 三列 dead-on-arrival 进 R1.0；**check2 七条 3.4 域条目标定全覆盖**（:101 深审维持已修复/:125 修复不可落地由 P1-3.4-002 承载/:153/:180/:205/:231/:249 已修验证）；27 条全保留降 0 驳 0） | 独立子 agent（见 plan 2331 Closure 段） |
| 3.5 | `3.5-sys-deep-audit.md` | 0 | **1** | 11 | 13 | done（裁决：通过附 P1×1——owner doc 分布式锁入口 ILockService 全仓不存在（文档→API→装配三层断裂，与死装配族 P2-3.5-008/009 同批修复互引）进 R1.0；**check2 13 条标定全部落定**（9 already-fixed + :261 still-live 维持〔DSL 调用点实证〕+ :285 partially fixed + :217 false-positive〔runbook 已文档化 audit/audit-save 分工〕）；**check 锁 P0 反向 already-fixed** 与 6.4 一致（附 14-09 测试证据收窄注记：生产谓词在全部锁测试中零执行，变异全绿）；编译断裂归因钉死 plan 2306 bc9c444cf3 单文件 import 写错（一行修正归 R1.x）；25 条全保留降 0 驳 0；A 面 2 轮 12 条/B 面 5 轮 13 条） | 独立子 agent（见 plan 2332 Closure 段） |
| 3.6 | `3.6-report-deep-audit.md` | 0 | **2** | 5 | 12 | done（裁决：通过附 P1×2——①removeCell 展开区域起点减号〔check:103 从未处置仍 live，offset≠0 删错行列〕②角色级报表/数据源权限表全套配置面全仓零消费、引擎取数无角色校验〔契约断裂，nop-datav 下游双轨已成现实〕）进 R1.0；P2×5 含两条挂死（TableExpander 跨维度父格调度死循环/uniqueName `X(1)` 后缀×sheet 循环死循环，独立复现实证）置队首；**check2 26 条标定零虚假申报**（:19 COUNTIF/SUMIF P0 已修复〔复核 8/8 测试确认〕、14+ already-fixed 附修复点、:298 维持暂缓转 R1.0 设计决策项、:508 文件删除核实）；撞车合并 12-05≡13-02 单条目双位点；净 19 条降 0 驳 0；A/B 面各 3 轮深挖） | 独立子 agent（见 plan 2333 Closure 段） |
| 3.7 | `3.7-rule-dyn-deep-audit.md` | 0 | **1** | 10 | 11 | done（裁决：通过附 P1×1——动态模块 moduleName 可取静态平台模块名遮蔽 VFS 第一优先层〔口令哈希直读 published="false" 静默击穿+静态对象重写+ORM 替换，主 agent 终裁 P1〕进 R1.0；P2×10 含 multiMatch 双存储分叉/缓存失效双缺口/@DataBean 缺失 RPC 必败/IEvalScope 锁外写；**check2 31 条零升级零虚假申报**（28 already-fixed + :168 false-positive 维持 + :204 平台级维持暂缓 + :310 日志落库转 R1.0 backlog）；**DYN-01 三处 runWithoutTenantId 维持 JUSTIFIED** 无新逃逸点；13-01 降级 P3（escapeSQLName 反证）、10-08 duplicated 并入 P1-2.5-005；净 22 条降 1 升 1 驳 0；A/B 面各 4 轮深挖） | 独立子 agent（见 plan 2334 Closure 段） |
| 3.8 | `3.8-tcc-retry-file-deep-audit.md` | 0 | **5** | 5 | 14 | done（裁决：通过附 P1×5 三族——文件权限面族×2（NopFileRecord 零 auth 任意读取链/attachFile 劫持物理删除链）+引擎执行表零 auth 平台代调提权（:290 处置前提被 live 证伪升级，P1-2.6-006 延伸，终裁 P1）+状态机恢复族×2（CANCELLING 崩溃恢复死锁 100 轮遗弃/TccContext 缺失静默 fork）进 R1.0；**check 18 条标定全覆盖**（10 already-fixed + :154/:175 partially〔处置记录过期非回归，残余 P2-3.8-004 承载〕+ 5 still-live 与处置一致 + :290 升级）；**:27 P0 already-fixed**〔TestTccRunner 10/10〕+**:61 交叉 plan 2282 通过**〔TestRetryEngineImpl 30/30〕；B 面 1 驳回（13-26 __TEMP__ 短路证伪）、A 面撞车索引 1 不计数；净 24 条降 0 升 1 驳 1；A 面 3 轮/B 面 2 轮） | 独立子 agent（见 plan 2335 Closure 段） |
| 3.9 | `3.9-gateway-network-deep-audit.md` | 0 | 0 | **9** | 20 | done（裁决：通过无 P0/P1 新发现附 P2×9 进 R1.0 输入池——**AiAuth 鉴权失败语义终裁 fail-closed**〔warn 脱敏后 throw 401〕；**86 条标定全覆盖零虚假申报**〔50 already-fixed + 9 partially 台账行 + 1 adjudicated〔:83 按 plan 2283〕+ 26 still-live + 0 FP〕；**check2 两 P0 already-fixed**〔:19/:43，107+115 测试实测〕+ check :23 AF；**NET 族 P0 三条 already-fixed 实测**〔:21/:45/:70 三组测试亲跑绿〕；**check :49/:86 两条 P1 确认维持**〔live 缺陷亲核在位〕进 R1.0；A 面 1 降级（12-03 不可达证伪）；净 29 条新发现降 1 驳 0；A/B 面各 3 轮） | 独立子 agent（见 plan 2336 Closure 段） |
| 3.10 | `3.10-auth-credential-incremental-audit.md` | 0 | **1** | 1 | 4 | done（裁决：通过附 P1×2 进 R1.0——①P1-3.10-001 CRED-01 no-finding 全称假命题与 live 矛盾〔安全台账链虚假保证，已实际误导一轮 :172 零处置，复核升格〕②P1-check-mcc-172 主密钥 passphrase 泄漏标定维持〔双文件域 9 throw 点亲读在位，修复与 P1-check2-nosql-67 同批〕；**29 条 auth 标定全覆盖零冲突**（check2 9 AF+6 deferred 维持/check 13 AF+1 partially——复核勘误）+:44/:19 两 P1 already-fixed 零漂移 + check :23 P0 already-fixed 双确认（P0 不变量在拆分 diff 逐字保持）；MFA codec 消费点侧取证闭环（Redis 后端功能性失效 fail-closed）；diff 四 commit 全部通过；CRED ledger 一致态零漂移；净 6 条新发现（A 面 1 驳回〔13-04 前提不成立〕、B 面 1 升格）；A 面 2 轮/B 面轻量 2 轮） | 独立子 agent（见 plan 2337 Closure 段） |
| 3.11 | `3.11-security-crosscut-recheck.md` | 0 | 0 | **1** | 1 | done（裁决：通过无 P0/P1 新发现附 P2×1/P3×1——**item 14 全 14 条标定完成：0 已修/12 仍 live/2 缓解生效**（复核 14/14 亲读确认、判级抽验 P2 全成立、零驳回零改判）；F-F2-1 升 P1 合并 3.8 P1-3.8-001/002 族 + F-WF-02-1 带修复裁定直入 R1.0；**P2 裁决例外 12 条转 R1.0**（P2 裁决台账登记）；grep 复扫三模式 123 处全量核实、口径重放全一致、撞车去重 7 项；新发现 P2-3.11-001 JWT 主密钥族 22 文件（含 nop-batch-app 键漂移死配置）+ P3-3.11-001 SSO client-secret 第 4 实例；A/B 面各 2 轮） | 独立子 agent（见 plan 2338 Closure 段） |
| 4.1 | `4.1-error-handling-audit.md` | 0 | 0 | **3** | 3 | done（裁决：通过无 P0/P1 附 P2×3 进 R1.0 输入池——**bare RT 全仓仅 5-6 处仅 1 违规 P3**、BizModel 面 ErrorCode 为主合规率 84.3%（复核修正后口径）+ .param() 91.3%、api 面校准后零违规、3348 catch 合规留证+rethrow 64.0% 正结论；P2×3：错误码字符串伪装系统族 65 处/40 文件/14 模块组 + RangeSplitUtils 中文消息 + ToolSchemaConverter 吞异常降级无参工具（复核加重：null 系全部真实工具常态路径；升级触发=agent 切原生 tool-calling）；复核修正透明记载（合规率 88.7→84.3 口径修正、09-04 处数 9→28）；P3×3 watch；零驳回；6.2 移交 catches.tsv 清单已备；A/B 面各 2 轮 + 复核） | 独立子 agent（见 plan 2339 Closure 段） |
| 4.2 | — | | | | | todo | |
| 4.3 | — | | | | | todo | |
| 4.4 | — | | | | | todo | |
| 4.5 | — | | | | | todo | |
| 5.1 | — | | | | | todo | |
| 5.2 | — | | | | | todo | |
| 5.3 | — | | | | | todo | |
| 6.1 | — | | | | | todo | |
| 6.2 | — | | | | | todo | |
| 6.3 | — | | | | | todo | |
| 6.4 | — | | | | | todo | |
| 6.5 | — | | | | | todo | |
| 6.6 | — | | | | | todo | |
| 6.7 | — | | | | | todo | |
| 6.8 | — | | | | | todo | |
| 6.9 | — | | | | | todo | |
| 7.1 | — | | | | | todo | |
| 7.2 | — | | | | | todo | |
| 7.3 | — | | | | | todo | |
| 7.4 | — | | | | | todo | |
| 7.5 | — | | | | | todo | |
| 7.6 | — | | | | | todo | |
| 7.7 | — | | | | | todo | |

## P0 发现追踪（即时通道）

> 审计中发现 P0 必须当即处理（就地修复或异步注入修复 plan），不得留到批量修复波次。发现编号格式：`P0-<工作项号>-<序号>`。

| Finding ID | 报告 | 描述（一句话） | 修复路径 | 修复状态 |
|---|---|---|---|---|
| P0-2.2-001 | `2.2-orm-eql-deep-audit.md` | `NOT (col._some P)` 语义反转（∃(¬P)≠¬∃(P)，文档 §5 明文契约） | plan 2320 簇A（异步注入） | fixed（2026-10-04，findLogicUnit NOT 吸收+parity 编码，testNotSomeCondition 等回归） |
| P0-2.2-002 | `2.2-orm-eql-deep-audit.md` | 嵌套 `_all` 量词静默翻转/丢失（`_all._some`≡`_some._all`；测试钉住错误期望） | plan 2320 簇A（逐层 XOR+修订测试） | fixed（2026-10-04，shouldNegateExists XOR 重写+testThreeLevelNestedOperators 期望修订+4 条嵌套矩阵回归） |
| P0-2.2-003 | `2.2-orm-eql-deep-audit.md` | NOT+嵌套集合路径否定错位（¬∃∃P→∃∃¬P；与 001 同根） | plan 2320 簇A | fixed（2026-10-04，unwrapNotChain 剥离+P1-2.2-003 崩溃同步消除（negateConditionForAll detach），testNotNestedSomeSome/testNotNestedSomeAll 回归） |
| P0-2.8-001 | `2.8-stream-sql-first-audit.md` | SumAgg 混型累加静默错值（整数部分丢弃+类型塌缩，连带 dim10-10 BigDecimal 截断/回绕；探针实测） | plan 2327 | fixed（2026-10-05，三通道精确累加器+双单调旗标+merge 同步，探针七行全过+7 回归钉，86/86 与三模块 BUILD SUCCESS） |

## P1 发现汇总（待 R1.0 展开）

> 本表由各报告产出时回填，是 R1.0 的快捷视图；**R1.0 汇聚以 Wave 1 报告全集为准**，与本报表不一致时先调和再展开（差异记为流程缺陷）。仍 live 的 P1 经 R1.0 转为 roadmap 修正工作项（R1.x）并在此回填去向。

| Finding ID | 报告 | 目标 R1.x | 修复状态 |
|---|---|---|---|
| P1-1.2-001 | `1.2-xlang-structure-pipeline-audit.md` | R1.x（包级 SCC 三档治理，expr→compile 单边割离性价比最高） | open |
| P1-1.8-001 | `1.8-integration-periphery-audit.md` | R1.0（file/credential-app 半拆命运裁决：恢复 start()+web-orm-starter 或正式休眠+文档声明） | open |
| P1-1.9-001 | `1.9-jq-refactor-first-audit.md` | R1.0（jq 工具接缝资源耗尽：4 叶节点预算+cancelToken 接线+env-supplier，精算在案） | open |
| P1-1.10-001 | `1.10-bytecode-duckdb-first-audit.md` | R1.x（bytecode 分析层异常收口+CLI 兜底 ~17 行；CI 接线前必修） | open |
| P1-1.10-002 | `1.10-bytecode-duckdb-first-audit.md` | R1.x（ObligationToken equals/hashCode ~10 行+虚假 javadoc 修正+CLI 防线） | open |
| P1-2.1-001 | `2.1-orm-ormmodel-deep-audit.md` | R1.x（detach 对齐 evict 语义或文档契约+checkValid，补回归） | open |
| P1-2.1-002 | `2.1-orm-ormmodel-deep-audit.md` | R1.x（装配路径对非 MANAGED 状态响亮失败/跳过，补 delete→select→flush 回归） | open |
| P1-2.2-001 | `2.2-orm-eql-deep-audit.md` | R1.0 簇D（keyProp `_some` DML where 非法 SQL；nop-wf/nop-dyn 真实模型可达） | open |
| P1-2.2-002 | `2.2-orm-eql-deep-audit.md` | R1.0 簇D（keyProp order/having 死 join 排序静默失效） | open |
| P1-2.2-003 | `2.2-orm-eql-deep-audit.md` | plan 2320 簇A 连带（NOT `_all` 编译崩溃，dup dim10b-02/dim16-02；仅 detach 会落入反转须同设计） | open |
| P1-2.2-004 | `2.2-orm-eql-deep-audit.md` | R1.0 簇D（keyProp 投影/窗口/分组位置矩阵死 join，伞条目） | open |
| P1-2.2-005 | `2.2-orm-eql-deep-audit.md` | R1.0 簇D（UPDATE SET/RETURNING prop-join 渲染缺口，dup dim10c-15） | open |
| P1-2.2-006 | `2.2-orm-eql-deep-audit.md` | R1.0 簇B（生成别名绕过 assignedAliases 查重→撞名恒真/自比，dup dim10a-02） | open |
| P1-2.2-007 | `2.2-orm-eql-deep-audit.md` | R1.0 簇C（mergeConditions:154 覆盖 commonAnd→AND+OR 形态编译失败，=dim16-03） | open |
| P1-2.2-008 | `2.2-orm-eql-deep-audit.md` | R1.0 簇D canonical（DML where 属性路径 join 渲染缺口，编译成功执行期必败） | open |
| P1-2.2-009 | `2.2-orm-eql-deep-audit.md` | R1.0 簇E（EQL DML 不注入实体级缺省过滤器/租户列，单表形态静默跨租户写） | open |
| P1-2.2-010 | `2.2-orm-eql-deep-audit.md` | R1.0 簇D（相关子查询多段 owner 两层防线失效） | open |
| P1-2.2-011 | `2.2-orm-eql-deep-audit.md` | R1.0（eval 转换器丢 not like/not in 否定，公开 jar 唯一 SQL→FilterBean 入口静默反转） | open |
| P1-2.3-001 | `2.3-dao-migration-dbtool-deep-audit.md` | R1.0（callFunc 绑定偏移+OUT 类型硬编码合并修复，补带参回归；响亮失败零调用方） | open |
| P1-2.4-001 | `2.4-nosql-cdc-deep-audit.md` | R1.0（rate_limit.lua 双 key 加 hash tag 一行修+集群测试补 rateLimiter 用例；暴露面 caveat 在案） | open |
| P1-check2-nosql-67 | `2.4-nosql-cdc-deep-audit.md`（来源 check2/nosql-cdc.md:67 复核） | R1.0（PrefixTextCodec 类型不对称 still-live——MFA 票据失效；修复方向 a=String.valueOf+peek 解析最小改 / b=类型保留编码捆绑回滚毒化评估；MFA 域归 3.10） | open |
| P1-2.5-001 | `2.5-orm-model-compliance-scan.md` | R1.0（nop-metadata 6 状态列补 precision+重生成三方言+迁移） | open |
| P1-2.5-002 | `2.5-orm-model-compliance-scan.md` | R1.0（nop-job 3 UK 补 constraint+重生成 DDL；先补属性否则重生成仍为空） | open |
| P1-2.5-003 | `2.5-orm-model-compliance-scan.md` | R1.0（nop-batch UK 同 002+补 upgrade 脚本） | open |
| P1-2.5-004 | `2.5-orm-model-compliance-scan.md` | R1.0（nop-retry 2 唯一索引改写 unique-key+constraint，datav 路径已验证） | open |
| P1-2.5-005 | `2.5-orm-model-compliance-scan.md` | R1.0（nop-rule outputs precision 改 4000 或删列级让 domain 生效+重生成） | open |
| 平台工单-2.5 | `2.5-orm-model-compliance-scan.md` | R1.0 平台工单（DDL 生成器不物化 `<index>` 含 unique=true——auth 7/ai 4/retry 2/sys 2） | open |
| P1-2.6-001 | `2.6-biz-graphql-deep-audit.md` | R1.0（action 级事务装饰器死配置修复——后台直调写动作零事务；连带 owner doc 事务语义修正） | open |
| P1-2.6-002 | `2.6-biz-graphql-deep-audit.md` | R1.0 修复族 A（fragment body 变量误拒——GraphQLSelectionResolver 三位点一次闭合+三回归用例） | open |
| P1-2.6-003 | `2.6-biz-graphql-deep-audit.md` | R1.0 修复族 A（spread 指令变量误拒——@Exclude 特性死胎） | open |
| P1-2.6-004 | `2.6-biz-graphql-deep-audit.md` | R1.0 独立（DataLoader 调度点后移+终局兜底 dispatch+幂等+异步 op×批量 loader 回归） | open |
| P1-2.6-005 | `2.6-biz-graphql-deep-audit.md` | R1.0 修复族 C（approval-support 五 mutation 无 auth——先补 constraint 式逐文件验证合并终值） | open |
| P1-2.6-006 | `2.6-biz-graphql-deep-audit.md` | R1.0 修复族 C（纯 xbiz 静默公开 16 项清单——独立重扫已验证，移交执行） | open |
| P1-2.6-007 | `2.6-biz-graphql-deep-audit.md` | R1.0 修复族 C（@BizLoader 死注解——buildFetcherField setAuth+xdef 补 auth+测试升级运行时断言） | open || P1-2.8-001 | `2.8-stream-sql-first-audit.md` | R1.0（EquiJoinOperator null 等值键误配——修复需覆盖 OUTER 补齐 matched 语义） | open |
| P1-2.8-002 | `2.8-stream-sql-first-audit.md` | R1.0（PerKeyOrderedBuffer.putAll 不搬 insertionSeq——restore 后 seq 对位碰撞静默丢失，探针双面实证） | open |
| P1-2.8-003 | `2.8-stream-sql-first-audit.md` | R1.0（hash completeUnmatched 缺水位前缘过滤——ts>wm 补 null+再配对双发，判别回归已验证可行） | open |
| P1-3.1-001 | `3.1-wf-deep-audit.md` | R1.0（approval-support 绕引擎双事实源——wf 活跃时拒绝直改+受派人校验；与 P1-2.6-005 分立互补同批修复互引） | open |
| P1-3.1-002 | `3.1-wf-deep-audit.md` | R1.0（超时 dueAction 通道 allowCallByUser 阻断——系统身份旁路或契约修订；与 P2-3.1 bean 失配/remind 链同批） | open |
| P1-3.3-001 | `3.3-batch-deep-audit-a.md` | R1.0（orm-reader `<eql>` 接线或移除节点+Guard 覆盖 new 直构实例——静默全表加载） | open |
| P1-3.3-002 | `3.3-batch-deep-audit-a.md` | R1.0（processor order 排序接线+batch-dsl.md 勘正——参照 consumer :275 对照） | open |
| P1-3.3-003 | `3.3-batch-deep-audit-a.md` | R1.0（allOf AtomicReference CAS 收集首个非取消异常——终态误分类 KILLED 不可重启） | open |
| P1-3.4-001 | `3.4-batch-deep-audit-b.md` | R1.0（OrmBatchRecordSnapshotBuilder.restore 补 ret.add+`"orm-entity"` 魔法值文档） | open |
| P1-3.4-002 | `3.4-batch-deep-audit-b.md` | R1.0（historyStore mandatory 三列补默认值或显式赋值+钉定测试升级真 IEntityDao flush 校验） | open |
| P1-3.5-001 | `3.5-sys-deep-audit.md` | R1.0（nop-sys.md 分布式锁入口 ILockService 幻影——改为 IResourceLockManager 真实契约；与 P2-3.5-008/009 死装配族同批修复互引） | open |
| P1-3.6-001 | `3.6-report-deep-audit.md` | R1.0（CellRowExpander/CellColExpander.removeCell 展开区域起点减号——offset≠0 删错行列；补 offset≠0 回归〔P2-3.6-004 测试缺口同批〕） | open |
| P1-3.6-002 | `3.6-report-deep-audit.md` | R1.0（NopReportDefinitionAuth/DatasourceAuth/DatasetAuth 权限表零消费——引擎取数接入角色校验或显式休眠+文档勘正；nop-datav 双轨对账） | open |
| P1-3.7-001 | `3.7-rule-dyn-deep-audit.md` | R1.0（动态模块 moduleName 保留名检查+遮蔽防线——stdDomain 排除既有静态模块名或发布前冲突检测+遮蔽告警；与 P2-3.7-010 路径逃逸同批） | open |
| P1-3.8-001 | `3.8-tcc-retry-file-deep-audit.md` | R1.0（文件权限面族·读侧：NopFileRecord CRUD 零 auth + filePath/isPublic 可改 + LocalResourceStore .. 防御——动作 auth 补齐+路径校验；与 P1-2.6-006 修复族同批） | open |
| P1-3.8-002 | `3.8-tcc-retry-file-deep-audit.md` | R1.0（文件权限面族·毁侧：attachFile/detachFile 归属校验 + ORM flush 钩子触发面收敛；与 001 同批） | open |
| P1-3.8-003 | `3.8-tcc-retry-file-deep-audit.md` | R1.0（TCC/Retry 引擎执行表 CRUD 零 auth——动作 auth 补齐或引擎表退出公开 CRUD；P1-2.6-006 延伸项同批） | open |
| P1-3.8-004 | `3.8-tcc-retry-file-deep-audit.md` | R1.0（TCC CANCELLING 崩溃恢复死锁——endAsync cancel 半区对称路由+遗弃防线；镜像 confirm 半区已修在案） | open |
| P1-3.8-005 | `3.8-tcc-retry-file-deep-audit.md` | R1.0（参与方 inbound TccContext 桥接——TccContextInvoker 接线或显式声明单跳约束；与 SM 静默 fork 同批） | open |
| P1-check-gw-49 | `3.9-gateway-network-deep-audit.md`（check/gateway-bizauth.md :49 标定确认） | R1.0（GatewayHttpFilter 路由匹配前全量严格 JSON 解析——解析后置到路由命中或按路由 content-type 分流；缓解 http-filter.enabled opt-in） | open |
| P1-check-gw-86 | `3.9-gateway-network-deep-audit.md`（check/gateway-bizauth.md :86 标定确认） | R1.0（OAuth code+redirect 分支不下发 auth cookie/不初始化用户上下文——SSO 回调登录循环；与 auth-core 登录链同批） | open |
| P1-3.10-001 | `3.10-auth-credential-incremental-audit.md` | R1.0（CRED-01 no-finding 勘误注 + consolidation ledger 补 :172 行——安全台账链虚假保证修正） | open |
| P1-check-mcc-172 | `3.10-auth-credential-incremental-audit.md`（check/msg-cluster-cred.md :172 标定维持） | R1.0（主密钥 passphrase 异常 param 泄漏——keyId-only param 改造〔样板 Vault:268-269〕，Default+Vault 双 provider 7 泄漏点；与 P1-check2-nosql-67 codec 修复同批） | open |


## P2 裁决台账（例外通道）

> 仅当审计报告显式开辟 P2 裁决例外时在此登记；R1.0 逐项裁决（修 / deferred 且带 Why Not Blocking Closure + Successor），裁决结果同步回 roadmap 行。

| Finding ID | 报告 | 描述 | 裁决结果 | Successor |
|---|---|---|---|---|
| F-WF-01-1 | `3.11-security-crosscut-recheck.md`（item14 承载） | 委托死代码锚点：nop_wf_user_delegate 孤儿零注册，live 走 auth 侧 substitution | 待 R1.0 裁决（cleanup+doc；与 P2-3.1-001 同批） | R1.0 |
| F-WF-01-2 | 同上 | wf.xdef check-start-auth 承诺引擎零消费，start 无补偿鉴权 | 待 R1.0 裁决（wire 或 remove 二选一；与 P2-3.1-003 同批） | R1.0 |
| F-WF-01-3 残余 | 同上 | 缓解在位后残余：notifySubFlowEndAsync 无 caller authz + results 未验注入 | 待 R1.0 裁决（API contract change）；缓解登记并行 | R1.0 |
| F-API1-1 | 同上 | error-message-public-for-no-mapping 默认 true，无映射错误公开 | 待 R1.0 裁决（缺省翻转 vs 部署 checklist 二选一） | R1.0 |
| F-API4-1 | 同上 | /px/ 全 header 透传无 outbound allowlist（可操纵路由）；缺省 inert | 待 R1.0 裁决（allowlist 设计） | R1.0 |
| F-AI1-1 | 同上 | LLM 网关 failover 账号切换 auth-header 累积 | 待 R1.0 裁决（sink 前移除或接受现状） | R1.0 |
| F-AI2-2 | 同上 | 双沙箱接缝未接线（Docker 沙箱不隔离 bash） | 待 R1.0 裁决（wiring 或文档裁定 SPI 边界） | R1.0 |
| F-N1-1 | 同上 | Apache 变体 followRedirects knob 静默 no-op（重定向全 header 转发）；变体零消费者 | 待 R1.0 裁决（disableRedirectHandling 接线；升级触发=变体进默认装配） | R1.0 |
| F-N2-2 | 同上 | MQTT 无 topic 级 ACL 模型且无 seam | 待 R1.0 裁决（ACL 新特性 or 信任边界文档化强制项） | R1.0 |
| F-N3-2 | 同上 | 流控制面 RPC 无鉴权（默认单 JVM 休眠，多 JVM 暴露） | 待 R1.0 裁决（文档化 ACL 姿态 or auth seam；升级触发=多 JVM 受支持拓扑） | R1.0 |
| F-D1-1 | 同上 | NopMetaEntity 物理浏览全列返回绕过 xmeta 字级可见性 | 待 R1.0 裁决（published/sensitive 过滤 or 文档化+默认收紧） | R1.0 |
| P2-3.11-001 | `3.11-security-crosscut-recheck.md` | 22 文件出厂 application.yaml 硬编码 JWT 签名主密钥族 + nop-batch-app 键漂移死配置 | 待 R1.0 裁决（空默认+已知默认值 fail-loud+与 check/demo-migration P2 同批；升级触发=enc-key 硬编码为生产主流路径） | R1.0 |

## MR/MV/MG 状态与收口证据

> Wave 2/3 工作项（R1.0、R1.x 动态行、R2.0、V.1–V.3、G.1–G.3）的状态翻转与收口证据在此登记；R1.x 展开时逐行追加。

| 工作项 | 状态 | 证据 / 结论 |
|---|---|---|
| R1.0 | todo | |
| R2.0 | todo | |
| V.1 | todo | |
| V.2 | todo | |
| V.3 | todo | |
| G.1 | todo | |
| G.2 | todo | |
| G.3 | todo | |

## 汇总

- `summary.md`：MV 里程碑收口时撰写（总体发现分布、修复闭环率、残留风险清单）。
