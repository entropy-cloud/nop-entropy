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
| 4.2 | `4.2-test-effectiveness-audit.md` | 0 | **6** | 9 | 0 | done（裁决：通过附 P1×6/P2×9 进 R1.0——**zero-test 120 模块（占 374 的 32%）口径重放 SET-MATCH-EXACT**：93 合理/25 缺口/2 移交〔quarkus-web/spring-web-starter 两大 HTTP 适配层零测试 P1，spring 形态无 e2e 缓解〕；**@Disabled 57→真禁用 53**（51 合理+2 OS 门控）+升格 2（reconciler 唯一收敛测试被禁 8 周无 successor/TestXplTask 裸禁 9 个月）+误报 2；反模式抽检 28 行仅 2 命中、变异推演 14 个发现侧全「改错仍绿」（TestStringHelper 复核扩充至 5 不触 SUT+1 零断言 P1/StringHelper.isWhitespace 双语义并存 P2）；**biz-auth-api 2 test 有效约束移出清单**（roadmap 点名模块裁定）；零驳回；A/B 两面各 2 轮 + 独立复核） | 独立子 agent（见 plan 2340 Closure 段） |
| 4.3 | `4.3-giant-files-audit.md` | 0 | 0 | **4** | 0 | done（裁决：通过附 P2×4 拆分建议走 P2 裁决台账转 R1.0（CodeIndexService 六域混载/JobCoordinator 七域两簇/ReActAgentExecutor 心跳块/GraphModelCheckpointExecutor 恢复族——队首 004 静态无互锁）；**基线重放零漂移**（33=9 生成物+11 已裁定+13 待新裁定）；保留 6/monitor 3 台账登记（含触发条件：EqlTransformVisitor >2000/WorkflowEngineImpl >2500+测试扩容前置/UserMfaSelfService >2000+ask-first）；撞车 11/11 无漂移（XNode 未越 3500 线/CrudBizModel 禁机械拆类维持/StringHelper P2-1.4-001 未实施）；1.3 转交 3 项登记闭环；零附带行为发现；复核勘误 4 处透明记载；单盘点 agent 分批 3 批 + 复核） | 独立子 agent（见 plan 2341 Closure 段） |
| 4.4 | `4.4-format-search-message-cluster-audit.md` | 0 | 0 | **7** | 11 | done（裁决：通过无 P0/P1 新发现附 P2×7 进 R1.0 输入池（09-20 partial-dup 与 P2-4.1-001 同批）+ P3×11 watch；**标定 50 条全落定**——format 16〔12 AF+2 partially+1 wontfix+1 still-live：条目 5 生成器-文法对齐探针 0/13 往返实证〕/ search 19〔1 驳回 P0 已修 3 测试复跑绿 + 18 保留 P1×5 live〕/ msg-cluster 15 全成立〔P1×4 全确认〕；mermaid 三 P1 plan 2306 修复验证通过；LuceneSearchEngine 有条件拆分裁定〔6 簇拆 2 保 4 先修 5 P1〕；TODO 35 处三类评定（真缺陷 2/文档化 33）；kafka 生命周期 5 点裁决移交 7.7；6.8 preempt 声明（search 标定已完成引用本报告）；三面各 1-2 轮 + 三独立复核，零驳回） | 独立子 agent（见 plan 2342 Closure 段） |
| 4.5 | `4.5-frontend-devtools-migration-runner-utils-audit.md` | 0 | 0 | **1** | 7 | done（裁决：通过无 P0/P1 附 P2×1 进 R1.0 输入池（TestXdslResourceTransformer 零断言——复核加强 processXDef 不可达）+ P3×7 watch；**标定台账 88 条零翻案**：dev-tools 25〔17 闭环+6 成立+2 补位〕/ nop-utils 23/23 已修〔P0×2 重点核对〕/ runner-cli 17/17 未修 / frontend-benchmark 19/19 未修 / migration 4/4 未修移交 demo-migration；**CLI 双入口三模块两真入口一死占位裁定**（EmptyMain 死占位/NopCliApplication 真 main/jdk11 漂移 3 项）；依赖卫生十模块 1 项 P3 违规（nop-java-parser 三跳传递）+ utils 重复度仅 2 同名+1 组跨模块复制；debugger 并发族 6 处修复全部到位；两面各 1 轮 + 双独立复核，零驳回） | 独立子 agent（见 plan 2343 Closure 段） |
| 4.6 | `4.6-lint-incremental-audit.md` | 0 | 0 | **4** | 7 | done（裁决：通过无 P0/P1 附 P2×4 进 R1.0 输入池（09-01 checkFile 表外扩展名 NPE 击穿错误面〔复核 JDK 26 独立复现〕/09-02 tsc 桥重启不重绑 project〔FakeTscPeer 不建模 NO_PROJECT 补强〕/02-01 Mojo threadSafe 并行竞态〔对采用者触发——Mojo 未接线 grep 复证〕/16-01 白名单 silent-empty〔升级触发=GraphQL 面成主通道〕）+ P3×7 watch；**maven-plugin「零新管线语义」成立**（代码追踪同型 CLI 7 行逐点表）；**fail-closed 10 项语义点全部成立**（namespace 既住裁定复核准确——FileNamespaceHandler 默认放行亲核）；三层账本对账残余项现状一致、重估触发均未到 + 3 项账本漂移（纪律面移交 tool-replacement 维护）；js 适配面合格；单 agent + 复核，零驳回） | 独立子 agent（见 plan 2344 Closure 段） |
| 5.1 | `5.1-docs-consistency-audit.md` | 0 | **3** | 5 | 7 | done（裁决：通过附 P1×3 进 R1.0——①事务默认表述族（18-01+18-20 分立立案互引 P1-2.6-001：service-layer/api-and-graphql/INDEX 默认规则/runbook/nop-task×3/TXN-001 共 6+ 文件，统一入口双矩阵）②@IocBean 幻影教程（nop-job.md，与 P1-3.5-001 同批互引）③P1-3.5-001 ILockService 标定维持；P2×5 进 R1.0 输入池 + P3×7 watch；**136 条核对声明一致率 ≈89%**；复核 15/15 全保留零驳回；撞车 7/7 未修复维持；12 篇抽样 + INDEX 路由 + source-anchors 全覆盖） | 独立子 agent（见 plan 2345 Closure 段） |
| 5.2 | `5.2-design-drift-audit.md` | 0 | **1** | 3 | 3 | done（裁决：通过附 P1×1 进 R1.0（maker-checker/03 现在时描述完全未实现的待审锁/guard/TTL 机制〔orm 表 23 列无锁列亲读、错误码零命中〕）+ P2×3 进 R1.0 输入池 + P3×3 watch；**草稿态违规 39 处整体立案 P2-5.2-001 走 P2 裁决台账**〔P1×30+P2×9/29 篇，编制勘正复核沿记的 19 篇口径——初审台账逐篇 head 亲核〕；口径重放 5/5 精确；②类 1 处改判透明记载（xdef-declarative-validation ②→①——执行器实存）；7/12 篇零 drift 正结论） | 独立子 agent（见 plan 2346 Closure 段） |
| 5.3 | `5.3-api-contract-version-health-audit.md` | 0 | 0 | **4** | **11** | done（裁决：通过无 P0/P1 附 P2×4 进 R1.0 输入池——bom 5 活跃幻影+tests 死依赖/nop-code 组版本漂移 1.0.0-SNAPSHOT 零留痕+11 api 未入 bom/ChatMessage role→type 改名存量会话不可读〔升 P1 触发条件已登记〕/WfChangeActorRequestBean propId=6 重复固化三层）+ P3×11（P3-5.3-015 watch-only）+ 线索 8 条；**机械 10/10 组口径独立重放吻合**、22 条原始条目零整条驳回、7 组撞车终裁合并、复核补扫新发现 P3-5.3-014（graph-api 孤儿 DTO 簇——closure audit E7 勘误：TestApiTypes 模块自有测试引用漏计，结论存活）；26 模块 pom 零 impl 反向依赖与 1.1 互证、wf/rule api.xml 与 live 零漂移、唯一真幻影 IRpcStatusReporter 正结论面） | 独立子 agent（agent_c657239e，FAIL→处方 R1-R3 落实后轻量复核 PASS，见 plan 2347 Closure 段） |
| 6.1 | `6.1-hollow-implementation-scan.md` | 0 | 0 | **1** | **4** | done（裁决：通过——**373 裁定样本〔60 全裁+29 快裁+240 冻结抽样+44 P4〕扫描行 a 类违规仅 4 条 + 盘点附带观察 1 = 5 条**（P2×1 Pulsar seekToPosition 空实现配置静默失效〔撞车史：2026-05-20 pulsar 审查发现 7 附注 residual，挂接 7.7/R1.0，修复须与 4.4 M-03 Kafka seek 统一语义〕+ P3×4 ParseTreeFormatter 空壳类归并 1.2 域 slice/dev-tools 安全悬置族/NopMetaModuleBizModel 699>600 阈值过期/spring-mcp-server 包名 servrer）；high+P1:medium 全裁零违规、P1:low 29/29 合法；**69 个在建空壳模块〔48×1+21×2〕全数合法定位零意外**；抽样 SHA-256 重放逐字节一致、台账抽验 56/373≈15% 全证实；scanner 口径校准三条（P6 子串/P8 continue/P6b 注释）watch 不立案；closure audit 处方 R1-R3（覆盖度量 389→373/台账行级归属/盘点 66→69）已落实） | 独立子 agent（agent_0a0753ae 复核 PASS；closure audit agent_bc940723，FAIL→处方 R1-R3 落实后轻量复核 PASS，见 plan 2348 Closure 段） |
| 6.2 | `6.2-silent-skip-swallow-scan.md` | 0 | 0 | **4** | **25** | done（裁决：通过——**宇宙 1111 行全量裁定净违规 29 案/34 位点 = P2×4 案 6 位点〔ArgumentCleanupStage:93+ArgumentValueCoercionStage:88 同源对 schema 解析合流/JqBuiltins:1683 recurse(f) 吞输出上限守卫/AiFileTool:178+:195 canonical IOException 误报 ERR_MCP_PATH_ESCAPE 安全码/FileHelper:486 双丢 cause〕+ P3×25 案 28 位点**；B2B 734 行仅 1 违规验证 4.1 边界转换预判；U 承接终裁修复 3/维持 2/升级 1〔U-6→P3-6.2-025〕；归并 4.1 [维度09-20] ×1 + SocketClient:150 归属 P3-3.9-014；复核 16 发现 100% 亲读+QA 锚 41 处零漏检+台账抽验 96 行零误判+宇宙对账精确；closure audit 一次 PASS 六项全过〔O1-O4 非阻塞观察已处理：O4 占位行清理〕） | 独立子 agent（agent_1b77fa59 复核 PASS；closure audit agent_56138042 一次 PASS，见 plan 2349 Closure 段） |
| 6.3 | `6.3-wiring-integrity-audit.md` | 0 | 0 | 0 | **3** | done（裁决：通过——**四链主链全连通零 P0/P1/P2**，42 机制边全量台账 + 复核 8/8 边独立追踪全通 + E3 四链达标 E1 零依赖；P3×3 进 R1.0（nop-dao exec 绑定护栏缺失/exec-maven-plugin 死属性/db-migration beans 双注册表零消费）；dup×3 复核纠正撞车漏检归并 P2-1.2-008/P3-2.3-034/P3-2.6-008；勘误 6 项含 A3-04 幻影类名；closure audit 处方 R1-R2（plan 头部勘误注记补记/四项勾选补齐）已落实） | 独立子 agent（agent_1fb93037 复核 PASS；closure audit agent_010b3bc3，FAIL→处方 R1-R2 落实后轻量复核 PASS，见 plan 2350 Closure 段） |
| 6.4 | `6.4-check-p0-verification.md` | 0 | 0 | **1** | 0 | done（裁决：通过——**check 36 P0 在 master 无一条保持 P0 级 live**：already-fixed 35 单元〔实跑覆盖 36/36 闭环，17 次实跑 95 tests 全绿〕+ still-live 1 单元 **P2-6.4-001**〔#3 ai-rest residual P0→P2 降档，残余复核收窄——OAuth 服务端换取已在 93d47833f0 实施〕+ #6a FP-as-P0 勘误拆目〔SUMMARY 定级错挂；缺陷本体 P1 live 纯引用 4.4 S-06 不双登记〕+ #6b SUMMARY 遗漏真 P0 补录 already-fixed；closure audit 处方 R1-R3 纯文本级已落实〔tests 58→68 勘误/补勾/抽查缺口裁定注记〕） | 独立子 agent（agent_966d1623 复核 PASS；closure audit agent_a51bf7e4，FAIL→处方 R1-R3 落实后轻量复核 PASS，见 plan 2351 Closure 段） |
| 6.5 | `6.5-check-p1-batch-a.md` | 0 | 0 | **1** | 0 | done（裁决：通过——批次 a 48 条 P1 对账闭合 + 冻结抽样 44/48=91.7% 标定：**AF 43 / still-live 1 / dup 0 / FP 0**；P2-6.5-001 nosql-cdc ZERO 半分支 GETEX PX 0 配置陷阱〔P1→P2 终裁：显式配置触发+0 生产调用方+失败响亮〕入 P2 裁决台账；db-migration#P1-6 判 AF〔check2 fb3f860729 闭环双实测〕；撞车零 dup；复核 120 tests 全绿零翻案 + 冻结协议重放机械精确；closure audit 处方 R1 纯文本〔plan Phase 2 状态漏更〕已落实） | 独立子 agent（agent_7b8e911e 复核 PASS；closure audit agent_bf8fb954，FAIL→处方 R1 落实后轻量复核 PASS，见 plan 2352 Closure 段） |
| 6.6 | `6.6-check-p1-batch-b.md` | 0 | 0 | **2** | 0 | done（裁决：通过——批次 b 46 条 P1 对账闭合〔13/13 diff+统计核对+三向账平〕+ 冻结抽样 29/46=63.0% 标定零翻案——canonical **AF 16 / dup 11〔9 归并 AF 目标+2 归并轮内 still-live 承载 P1-3.1-002/P1-3.6-001〕/ still-live 2 / FP 0**；P2-6.6-001 graphql 订阅推送桥未接线〔GraphQLSubscriptionManager+subscription-defaults.beans.xml〕+ P2-6.6-002 batch 续写语义缺失〔均复核终裁 P2〕；承接 11 条轮内终判锚点全实测成立；复核 dup 锚点 9/11 亲验+AF 实跑 4 全绿；closure audit 处方 R1/R2 纯文本〔§二 表格 canonical 收敛+P2-6.6-001 主体类名勘正〕已落实） | 独立子 agent（agent_25c5fd59 复核 PASS；closure audit agent_15646630，FAIL→处方 R1/R2 落实后轻量复核 PASS，见 plan 2353 Closure 段） |
| 6.7 | `6.7-check-p1-batch-c.md` | 0 | **11** | **8** | 0 | done（裁决：通过——批次 c 46 条 P1 对账闭合〔枚举 diff 一致+统计核对 12/12+三向账平〕+ 冻结抽样 33/46=71.7% 标定零翻案——canonical **AF 13 / dup 1〔归并 6.4 #5 修复目标〕/ still-live 19 / FP 0**，复核终裁 **P1×11〔方言 role/伪影/工具 DSL/agent 循环×2/stream 定时器与 restore 位点/CodeIndexService×3〕/ P2×8〔复核降档：桩不可达/未接线/输入不触发/残余窄化〕**；承接 1 条 P1-check-mcc-172 维持；dup 锚 1/1 亲验+33 锚全量 grep 零新增同根+16 修复提交 master 全可达；closure audit 一次 PASS 四项全过〔3 Minor 形式差：空台账标题/SHA 位置/个别 1-2 行级锚点偏移，符号级准确不阻塞〕） | 独立子 agent（agent_f5709e50 聚焦复核 PASS；Phase 2 复核 agent_037068a3 PASS 零翻案；closure audit agent_01452dcb 一次 PASS，见 plan 2354 Closure 段） |
| 6.8 | `6.8-check-p1-batch-d.md` | 0 | **6** | **2** | 0 | done（裁决：通过——批次 d 69 条 P1 对账闭合〔枚举 diff 一致+统计核对 17/17+SUMMARY 分相账平〕+ **承接实测 C=43**〔4.4/4.5/3.9 行级锚回读零错配：AF 22+partially 2+adj-deferred 1+still-live 18〕+ 冻结抽样 22 条标定零翻案——canonical **AF 14 / dup 0 / still-live 8 / FP 0**，终裁 **P1×6〔spring removeCookie NPE/Yunpian areaCode/splitter 布局颠倒/Feishu 零 ACK/variant 双约定/saver 误删基础表〕/ P2×2〔JavaScriptService nop-js 出 reactor+DemoBizModel 匿名 NPE〕**；双轨 84.6% 净池+65≥35 严格两 MET；SftpClient residual=AF 附 adjudicated-deferred〔plan 2283 承载〕；**承接 still-live 18 条沿在先行标识补登记 P1 汇总**〔含勿双立注记〕；closure audit 处方 R1 纯文本〔index P1-4.5-R1 行缺陷主体勘正〕+顺手拆 :118 遗留合并行已落实） | 独立子 agent（agent_d2cf04c9 聚焦复核 PASS；Phase 2 复核 agent_23db8c13 PASS 零翻案；closure audit agent_55f0b61c，FAIL→处方 R1 落实后轻量复核 PASS，见 plan 2355 Closure 段） |
| 6.9 | `6.9-check-p2p3-watchonly-archive.md` | 0 | 0 | 0 | 0 | done（裁决：通过——check P2/P3 宇宙 694 条〔P2 359+P3 335×54 单元〕三分类账平闭合：**①轮内已标定 148〔still-live 未登记 77→升级候选〕+②处置块已标 315〔抽验 33/35=94.3% 真实率+实跑 19 条〕+③watch-only 231**；升级候选定稿冻结 **130 行=维持 109+降级留痕 17+新增 4**〔含 nop-core-framework#P2-L108 修复被 plan-2309 重构静默回退发现〕上报 R1.0 裁决通道；三分类边界抽验 33/33=100% 零翻案；**check 系列四层〔P0/P1/P2/P3〕全部收口**；流程缺陷 F1–F4 勘正表登记〔SUMMARY 三口径/4.5 台账缺口等〕） | 独立子 agent（agent_877eef9f 聚焦复核 10 项+N1–N4 核认 PASS；Phase 2 复核 agent_a76fcb85 PASS 零翻案；closure audit agent_9bccf11e 一次 PASS 六项全过〔4 Minor 不阻塞：sample.py 补落重放一致/哈希笔误 5f301a139b 勘正/91 vs 90 口径注记/勾选补齐〕，见 plan 2356 Closure 段） |
| 7.1 | `7.1-nop-ai-incremental-audit.md` | 0 | **2** | **6** | **2** | done（裁决：通过——nop-ai 148 提交增量面三维审计全覆盖〔分配表零遗漏零重复；域外 86 提交登记移交；deploy/model 处置；MCP 无基线面全量〕——发现 **10 条全部 still-live 亲读证实零翻案：P0=0/P1×2〔WI25 懒重投递状态机破坏/PgVectorStore search 列缺失〕/P2×6/P3×2**；撞车移交 19 笔抽验 12/12+安全面 H-A3-SEC-01 移交 security 族合规；维度 12 零独立发现；千行回涨 git 实测=4〔AgentToolDispatcher 2.35x 最陡〕；重构波 WI20–22 三维判据全过；closure audit 一次 PASS 五项全过〔5 Minor 不阻塞：P2-7.1-004 归属勘正 nop-ai-rag/补 diff 锚 97be7d367a 等〕） | 独立子 agent（agent_16ad4c5c 聚焦复核 PASS；Phase 2 复核 agent_d28b3267 PASS 零翻案；closure audit agent_330eae76 一次 PASS，见 plan 2357 Closure 段） |
| 7.2 | `7.2-nop-stream-incremental-audit.md` | 0 | **1** | **1** | 0 | done（裁决：通过——nop-stream 62 提交覆盖账 **62=在审 35+已审域 27** 账平+四套 WI 映射+域外 20/62 登记+backlog 98 行〔96 P2+2 P3〕批量标定 **35/35/28** 深抽零翻案——发现 **P1-7.2-001 在窗回归〔WI4 工厂 PARALLEL vs 测试断言失配，实跑复现〕**+P2-7.2-001 keyTuple 静默折叠〔P3 升档〕；**AR-06 升级撤销改 P2 残余收敛**〔plan 368 已闭生产链路〕升级上报定稿 0 条 P1 级；R1.0 存量 P1-6.7-007/008 零触碰维持；零 P0；closure audit 一次 PASS 六项全过〔3 Minor 不阻塞：backlog 行数简写 102=98+4 留痕/残余收敛子类基数注记/台账压缩头部块〕） | 独立子 agent（agent_c66ea00b 聚焦复核 M1 二次核认 PASS；Phase 2 复核 agent_6e3e6892 PASS 零事实翻案；closure audit agent_ff01000b 一次 PASS，见 plan 2358 Closure 段） |
| 7.3 | `7.3-nop-metadata-incremental-audit.md` | 0 | 0 | 0 | 0 | done（裁决：通过——**重定基**：台账「P1×1 live」实为 MR2 已修复——终验 **PASS 四锚一致**〔7478eeffe0 亲读+HEAD 祖先+:55/:65 合规形态+arm-index :240 镜像〕+状态分叉缺陷登记〔台账 open vs index fixed，规则 #2 不回写登记于报告〕+P2×16 三分类 **fixed 8/still-open 5/watch 3**〔行 4 七位点 7/7 fixed 含 MetaTableProfiler 消亡勘正；16/16 行全量独立核验〕+落账缺口登记〔维度16-01〕；零 P0；still-open 5 归 P2 台账通道；closure audit FAIL→处方 R1〔§二 8 行 canonical 收敛〕落实后轻量复检 PASS） | 独立子 agent（agent_9621ec45 聚焦复核 PASS；Phase 2 复核 agent_af1eb682 PASS 零翻案；closure audit agent_aa2bb7c7，FAIL→R1 落实后轻量复检 PASS，见 plan 2359 Closure 段） |
| 7.4 | `7.4-nop-code-gated-incremental-audit.md` | 0 | **3** | **3** | 0 | done（裁决：通过——gated 三点核对登记完备〔张力注记：CRUD 面已覆盖但非 CRUD 面+AR-155/170 待确认，roadmap 未更新〕+Phase 4 successor 验证 PASS+6 提交巡检+三新扩展结构巡检 hollow 全 EXIT=0 零缺陷——发现 **6 条零翻案：P0=0/P1×3〔LlmSemanticEdgeExtractor 生产不可达 roadmap done 误导面+评测自证空转 MRR=1.0+证据链断裂〕进 R1.0/P2×3〔含 roadmap-done+plan-draft 系统性脱钩流程缺陷族〕**；冻结哈希重放 MATCH；closure audit 一次 PASS 四项全过〔3 Minor 不阻塞：P2 台账合并行先例/triage 表 _tmp 留痕/时态表述〕） | 独立子 agent（agent_1d1262e3 聚焦复核 M1 残留核认 PASS；Phase 2 复核 agent_9231c882 PASS 零翻案；closure audit agent_69c001b7 一次 PASS，见 plan 2360 Closure 段） |
| 7.5 | `7.5-nop-job-r10-incremental-audit.md` | 0 | 0 | 0 | 0 | done（裁决：通过——R10 **17 条全量标定 already-fixed 17/17 零翻案**——承接对账 17/17 全覆盖〔plan 267/268/269 全 completed+保持性 325/338/339/344〕+抽验 11/17=65% 含 P1×5 全覆盖+独立实跑 51 tests 全绿；**增量巡检零编号发现**〔50 提交零偏差+域外 20 登记+样本 5 深检全净〕+Watch 注记 3 条低于门槛；残留 2 条 non-blocking 声明属实；零 P0；6.6 零同缺陷+ID 空间防撞登记；closure audit 一次 PASS 四项全过〔3 Minor 不阻塞：R10 summary 行锚 +1 偏移勘正/W-1 锚 1 行偏移/台账标题合并口径注记〕） | 独立子 agent（agent_eed12f8e 聚焦复核二次核认 PASS；Phase 2 复核 agent_6edac754 PASS 零翻案；closure audit agent_aff4dff4 一次 PASS，见 plan 2361 Closure 段） |
| 7.6 | `7.6-nop-datav-followups-audit.md` | 0 | 0 | **1** | 0 | done（裁决：通过——followups open **P2×52+裁定项 #72 精确枚举**+抽样 **25 条=48.1%≥45%** 三分类 **fixed 2/watch 2/still-open 21** 零翻案〔复核抽验 10/25=40% 全维持〕+45 提交增量巡检 **P2-7.6-001 残留裸 ISE 双重遗漏链**〔W4 引入+P2-19 枚举漏+W7 修复漏；复核新证 W3 时点 ISE=0〕+撞车四面全量〔fc5b390ae↔6.7 datav#P1-1 同面非修复关系核验〕+#72 评估〔中低紧急宜随 R1.0 ACL 收口〕；零 P0；closure audit 一次 PASS 五项全过〔3 Minor 不阻塞：_tmp 日期/标题沿用注记/Q1-Q2 已吸收〕） | 独立子 agent（agent_036d5ac9 聚焦复核残留核认 PASS；Phase 2 复核 agent_2d6f637d PASS 零翻案；closure audit agent_ccb3b4fc 一次 PASS，见 plan 2362 Closure 段） |
| 7.7 | `7.7-pulsar-record-residual-audit.md` | 0 | **1** | 0 | **3** | done（裁决：通过——40 条全量现状复核零翻案：pulsar 17〔AF 14/still-live 3 全 P3〕+plan-203 审查 23〔R1 fixed 15/still-live 1→**派生 P1-7.7-001 readCollection fixed 分支 subStart 错序双重跳过**〔零启用场景 P1 附 R1.0 升级依据〕/R2 closed 5+late 1〔R2-6 窗内被修复正向发现〕+partial 1〕+**kafka 5 点移交整理**〔M-01/M-03 维持+INIT/ALWAYS 区分裁决+非 ApiMessage 统一 JSON 方案+P3-4.4-008 低成本收口〕+seek 三处同点位整理=**P2-6.1-001 唯一承载**；冻结双 SHA 重放 MATCH；零 P0；closure audit FAIL→处方落实〔P1-7.7-001 行归位 P1 汇总+同类错位行 P2-7.3 勘正+勾选/日期口径〕后轻量复检 PASS） | 独立子 agent（agent_fb56b1fa 聚焦复核 PASS；Phase 2 复核 agent_53d13f18 PASS 零翻案；closure audit agent_e8c16727，FAIL→处方落实后轻量复检 PASS，见 plan 2363 Closure 段） |

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
| P1-1.2-001 | `1.2-xlang-structure-pipeline-audit.md` | R1.x（包级 SCC 三档治理，expr→compile 单边割离性价比最高） | fixed（2026-10-09，plan 2364——expr 包零 compile 引用接缝割离 + SCC 21→20 compile 出环实测 + 三档治理成文） |
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
| P1-2.6-007 | `2.6-biz-graphql-deep-audit.md` | R1.0 修复族 C（@BizLoader 死注解——buildFetcherField setAuth+xdef 补 auth+测试升级运行时断言） | open |
| P1-2.8-001 | `2.8-stream-sql-first-audit.md` | R1.0（EquiJoinOperator null 等值键误配——修复需覆盖 OUTER 补齐 matched 语义） | open |
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
| P1-4.2-001 | `4.2-test-effectiveness-audit.md` | R1.0（nop-quarkus-web vertx HTTP 上下文适配层零测试补齐；e2e quarkus:dev 粗粒度缓解在案） | open |
| P1-4.2-002 | `4.2-test-effectiveness-audit.md` | R1.0（nop-spring-web-starter servlet HTTP 上下文适配层零测试补齐——无任何 e2e 缓解，P1 最强） | open |
| P1-4.2-003 | `4.2-test-effectiveness-audit.md` | R1.0（nop-batch-orm ORM 批处理 upsert 语义测试补齐） | open |
| P1-4.2-004 | `4.2-test-effectiveness-audit.md` | R1.0（nop-cluster-sentinel 限流 entry/exit 配对与降级映射测试补齐） | open |
| P1-4.2-005 | `4.2-test-effectiveness-audit.md` | R1.0（nop-fsm 状态机引擎转移语义直接测试补齐） | open |
| P1-4.2-006 | `4.2-test-effectiveness-audit.md` | R1.0（TestStringHelper 重写：删/改 5 个不触 SUT 方法+testUUID 补断言+isWhitespace pinning——与 P2-4.2-009 合并执行） | open |
| P1-5.1-001 | `5.1-docs-consistency-audit.md` | R1.0（与 P1-2.6-001 同批互引——service-layer.md:66/:277 + api-and-graphql.md:174 事务入口双矩阵改写） | open |
| P1-5.1-002 | `5.1-docs-consistency-audit.md` | R1.0（与 P1-2.6-001 同批互引——INDEX:38 + transaction-boundaries.md + nop-task.md×3 + TXN-001；文档修复不受代码修复阻塞） | open |
| P1-5.1-003 | `5.1-docs-consistency-audit.md` | R1.0（与 P1-3.5-001 同批互引——nop-job.md:246-262 @IocBean 幻影改 beans.xml 注册教程） | open |
| P1-5.2-001 | `5.2-design-drift-audit.md` | R1.0（maker-checker/03 头部与各节「目标架构（未落地）」标注+删现在时机制陈述；与 P2-2.7-001 同批互引；文档修复不受代码修复阻塞） | open |
| P1-6.7-001 | `6.7-check-p1-batch-c.md` | R1.0（AnthropicDialect.getRole assistant→"model" 多轮必 400；role 改回+TestAnthropicDialect :394 断言同步+thinking signature 残余〔CL-1〕同批） | open |
| P1-6.7-002 | `6.7-check-p1-batch-c.md` | R1.0（OpenAi/Gemini/Ollama 工具轮 content=arguments JSON 污染+四方言 reasoning 双发；跳过置入/不双发+roundtrip 等价测试，ResponsesDialect 为范本） | open |
| P1-6.7-003 | `6.7-check-p1-batch-c.md` | R1.0（LocalToolFileSystem.readLines totalLines 填请求值假总行数；countLines 或置 null） | open |
| P1-6.7-004 | `6.7-check-p1-batch-c.md` | R1.0（LocalToolFileSystem.grep 文件路径静默空假阴性；文件时 grepInFile 或显式错误+单文件用例） | open |
| P1-6.7-005 | `6.7-check-p1-batch-c.md` | R1.0（ReActAgentExecutor POST_REASONING BAIL 不移除已持久化 assistant/tool_call 会话复挂；bail 前 remove 或补 error response） | open |
| P1-6.7-006 | `6.7-check-p1-batch-c.md` | R1.0（AgentCompactionCoordinator maxTokens 误作上下文窗口；独立 maxContextTokens 配置+改写钉死断言） | open |
| P1-6.7-007 | `6.7-check-p1-batch-c.md` | R1.0（TimerServiceManager 吞定时器异常静默丢水印发射；不吞异常 addSuppressed+改固化缺陷契约的测试） | open |
| P1-6.7-008 | `6.7-check-p1-batch-c.md` | R1.0（SourceReaderOperator.restoreState restore-先于-open 静默丢 split 位点；preOpenSplits 式暂存+restore→open→run 回归） | open |
| P1-6.7-009 | `6.7-check-p1-batch-c.md` | R1.0（CodeIndexService.analyzeChanges `file:` rootPath 建 git 必败且异常折叠为无变更；rootPath 规范化+异常不折叠 truncated=true） | open |
| P1-6.7-010 | `6.7-check-p1-batch-c.md` | R1.0（triggerIncrementalIndex 先删后不补+指纹照存文件永久消失；失败文件不纳入指纹/先分析后删写） | open |
| P1-6.7-011 | `6.7-check-p1-batch-c.md` | R1.0（deleteFileRecords 依赖边删除无 indexId scoping 跨索引互删；补 eq("indexId",indexId)+同类 helper 作用域核对） | open |
| P1-6.8-001 | `6.8-check-p1-batch-d.md` | R1.0（ServletHttpServerContext.removeCookie 无 null 守卫登出链 NPE；补 cookies==null 守卫对齐 getCookie 形态；zero-test 证据弱化登记） | open |
| P1-6.8-002 | `6.8-check-p1-batch-d.md` | R1.0（YunpianSmsSender null areaCode 拼接——6.4 #8 同根未修残腿；null 守卫+两腿 parity 回归与 6.4 #8 同批） | open |
| P1-6.8-003 | `6.8-check-p1-batch-d.md` | R1.0（TestCaseJsonDataSplitter 产物目录布局与消费方契约颠倒静默不可加载；输出路径对齐+splitter→loader 往返测试） | open |
| P1-6.8-004 | `6.8-check-p1-batch-d.md` | R1.0（FeishuClient DATA 帧零 ACK 回发+headers 丢失；按协议回 ACK+恢复 headers 透传+wire 契约测试） | open |
| P1-6.8-005 | `6.8-check-p1-batch-d.md` | R1.0（AutoTestCaseData variant 双约定——输出表永不校验框架假绿；统一 variant 路径约定+录制→校验往返测试） | open |
| P1-6.8-006 | `6.8-check-p1-batch-d.md` | R1.0（AutoTestCaseDataSaver.removeInputTable 无 variant 守卫静默删除 default 基础表；补 isDefaultVariant 守卫对齐 saveInputTable 对称性） | open |
| P1-7.1-001 | `7.1-nop-ai-incremental-audit.md` | R1.0（WI25 DefaultBackgroundTaskRegistry 懒重投递状态机双重破坏——失败/超时任务被永久误报 COMPLETED；重投区分源状态+补 FAILED/TIMEOUT 源用例） | open |
| P1-7.1-002 | `7.1-nop-ai-incremental-audit.md` | R1.0（PgVectorStore.search 列读取缺失——生产后端 hits content 恒 null 静默错误结果；回读 content/metadata+pgvector 路径内容断言测试） | open |
| P1-7.2-001 | `7.2-nop-stream-incremental-audit.md` | R1.0（TestStreamConnectorRegistryDiscovery 断言与 WI4 工厂语义失配在窗回归——模块测试门禁恒红实跑复现；同步测试断言到 PARALLEL 禁止回退工厂语义） | open |
| P1-7.4-001 | `7.4-nop-code-gated-incremental-audit.md` | R1.0（LlmSemanticEdgeExtractor 生产不可达——CodeIndexService:285-291 硬编码 3 提取器零接线+roadmap done 误导面+G1-b 静默缺失复合缺陷；补接线或双向状态勘正；**条件判读登记供拆面**） | open |
| P1-7.4-002 | `7.4-nop-code-gated-incremental-audit.md` | R1.0（SearchQualityEvalTest 评测自证空转——零生产参与 MRR=1.0/F1=1.0 不可作质量证据；接真实链路或显式标注 smoke） | open |
| P1-7.4-003 | `7.4-nop-code-gated-incremental-audit.md` | R1.0（plan 31 证据链断裂——`[x]` 载体从未运行+@Disabled+closure 自审非独立；状态勘正+indexDirectory 验证补做或条目移除） | open |
| P1-check-netrpc-24 | `6.8-check-p1-batch-d.md`（承接：3.9 §2.5 表#1 标定维持） | R1.0（check/net-http-rpc.md#P1-1 JdkHttpClient :320-323 错误路径 still-live——沿 3.9 §2.5#1 行修复方向；3.9 仅登记 gw-49/86 本条独立） | open |
| P1-4.4-A5 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.1 A面 条目5 标定维持） | R1.0（check/format-misc.md#P1-5 生成器-文法对齐探针 0/13 still-live；**与 P2-4.4-001/002 合并的是修复立项通道**——P1 登记位单点保留） | open |
| P1-4.4-S-02 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.2 标定维持） | R1.0（check/nop-search.md#P1-1 空 query 构造空 BooleanQuery 恒空；LuceneSearchEngine 先修 5 条 P1〔S-02..06〕再动拆分——4.4 执行顺序约束） | open |
| P1-4.4-S-03 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.2 标定维持） | R1.0（check/nop-search.md#P1-2 visitNot 纯 MUST_NOT 匹配空集；同 S-02..06 先修批次） | open |
| P1-4.4-S-04 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.2 标定维持） | R1.0（check/nop-search.md#P1-3 rollback 后缓存 map 残留单条坏文档打死 topic；同 S-02..06 先修批次） | open |
| P1-4.4-S-05 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.2 标定维持） | R1.0（check/nop-search.md#P1-4 getDoc 忽略 topic 跨 topic 串读；**修复触 nop-search-api 签名属 plan-first 区**） | open |
| P1-4.4-S-06 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.2 标定维持） | R1.0（check/nop-search.md#P1-5 indexDir 任意路径+checkAllowAccess 空实现；**安全面**；S-06 注 6.4 #6a 纯引用裁定勿双立） | open |
| P1-4.4-C1 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.3 标定维持） | R1.0（check/msg-cluster-cred.md#P1-1 选主注解 @cfg; 笔误装配即启动失败；修笔误+装配冒烟） | open |
| P1-4.4-C2 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.3 标定维持） | R1.0（check/msg-cluster-cred.md#P1-2 LeastActive 未取模下标越界；取模+TestLoadBalance 补 LeastActive 用例） | open |
| P1-4.4-C3 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.3 标定维持） | R1.0（check/msg-cluster-cred.md#P1-3 SentinelFlowControl runAsync Context 泄漏；finally 无条件 exit；**与 P1-4.2-004 sentinel 补测同批**） | open |
| P1-4.4-C4 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.3 标定维持） | R1.0（check/msg-cluster-cred.md#P1-4 NacosNamingService @@ 无防护；pos 守卫对齐 getNormalizedServiceName；**与 P2-4.2-001 nacos 补测互补**） | open |
| P1-4.4-M01 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.3 标定维持） | R1.0（check/msg-cluster-cred.md#P1-5 suspend/resume 调用线程直操 consumer+rebalance 后静默失效；pause 语义迁移+rebalance listener） | open |
| P1-4.4-M02 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.3 标定维持） | R1.0（check/msg-cluster-cred.md#P1-6 cancel 先 close 后等线程退出 CME 吞掉泄漏；close/await 顺序重排） | open |
| P1-4.4-M03 | `6.8-check-p1-batch-d.md`（承接：4.4 §2.3 标定维持） | R1.0（check/msg-cluster-cred.md#P1-7 seekToPosition 分区分配前执行全 no-op；迁移 ConsumerRebalanceListener+区分 ALWAYS/INIT 语义+**重写掩盖测试 TestKafkaConsumeTask**；P2-6.1-001 邻接非同条，7.7 移交①②） | open |
| P1-4.5-R1 | `6.8-check-p1-batch-d.md`（承接：4.5 §2.3 标定维持） | R1.0（check/runner-cli.md#P1-1 CliDiffCommand.loadModel 恒用 newModelPath diff 恒空——resolveRelativePathResource(modelPath)+-O/-N required+资源存在校验；4.5 §2.3#1 行修复方向） | open |
| P1-4.5-R2 | `6.8-check-p1-batch-d.md`（承接：4.5 §2.3 标定维持） | R1.0（check/runner-cli.md#P1-2 CDI factory 不可用 @Inject null；同 R1 共享引导批次，pom exclude 在位未修复） | open |
| P1-4.5-D1 | `6.8-check-p1-batch-d.md`（承接：4.5 §2.5 标定维持） | R1.0（check/demo-migration.md#P1-2 addCatalog off-by-one；**修复时同步删 runner 死副本 GenOrmHelper**——4.5 撞车去重表指令） | open |
| P1-4.5-B1 | `6.8-check-p1-batch-d.md`（承接：4.5 §2.4 抽查表标定维持） | R1.0（check/frontend-benchmark.md#P1-2 PageProvider transformPermissions 竞态原地删键可变共享缓存；copy-on-write 或防御性拷贝；复核勘正补入清单第 18 条） | open |
| P1-7.7-001 | `7.7-pulsar-record-residual-audit.md` | R1.0（nop-record readCollection fixed 分支 subStart 在 subInput 后捕获——急切 reader 双重跳过；捕获点前移+补「急切+非流式+fixed 集合」回归测试；R1.0 可从严升 P0 依据已附） | open |


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
| P2-4.3-001 | `4.3-giant-files-audit.md` | CodeIndexService 六域混载 2991 行（拆分：持久化/git/增量三簇） | 待 R1.0 裁决（成本整体搬迁型） | R1.0 |
| P2-4.3-002 | `4.3-giant-files-audit.md` | JobCoordinator 七域混载 2845 行（拆分：LeaderEpochController+liveness；高成本，禁动锁结构，TestJobCoordinatorRecoveryConcurrency 放大为前置） | 待 R1.0 裁决 | R1.0 |
| P2-4.3-003 | `4.3-giant-files-audit.md` | ReActAgentExecutor 心跳/steering 块 ~410 行未随抽取波走完（1935 行） | 待 R1.0 裁决（与既有 *Coordinator 抽取波同构，中成本） | R1.0 |
| P2-4.3-004 | `4.3-giant-files-audit.md` | GraphModelCheckpointExecutor 恢复/状态重组族 ~640 行混载（1591 行；静态无互锁+委托桩在位） | 待 R1.0 裁决（**队首**——成本最低） | R1.0 |
| P2-5.2-001 | `5.2-design-drift-audit.md` | 草稿态违规整体立案 39 处/29 篇（实现已落地仍标草案/Draft——nop-lint 13 篇族/nop-wf extensions/nop-refactor/nop-graph/usage-and-billing/metadata 03-04-12 等） | 待 R1.0 裁决（Rule #14 重写波次 + 索引同步） | R1.0 |
| P2-6.4-001 | `6.4-check-p0-verification.md` | check #3 ai-rest 扫码登录 residual（P0→P2 降档）：事件签名验证缺失+回调端点无限流+ChannelLoginApiBizModel:56-67 javadoc 陈旧（主向量已闭合；OAuth 服务端换取已在 93d47833f0 实施——复核收窄） | 待 R1.0 裁决（残余三项修复；ai-rest owner doc 同步） | R1.0 |
| P2-6.5-001 | `6.5-check-p1-batch-a.md` | check 溯源 nosql-cdc#P1-1 ZERO 半分支：NosqlCache.getAsync 仅 null 守卫，Duration.ZERO 透传 GETEX PX 0 被 Redis 拒绝（null 半分支已由 check2 fc2b9347f6 修；P1→P2 终裁：显式配置触发+0 生产调用方+失败响亮） | 待 R1.0 裁决（CacheConfig 归一化+TestNosqlCache 补 ZERO 用例；与 fc2b9347f6 同文件同批） | R1.0 |
| P2-6.6-001 | `6.6-check-p1-batch-b.md` | check 溯源 nop-graphql#P1-3：订阅推送桥整体未接线（GraphQLSubscriptionManager+subscription-defaults.beans.xml:10-15——bean 永不加载+registerSubscription/unregisterSubscription 生产零调用方；已修两面〔死属性删除+@PostConstruct+装配守卫测试 2/2〕验证；复核终裁 P2：非回归无公开契约，2.6 相邻订阅面 P2 先例） | 待 R1.0 裁决三选一（接线设计后补 autoconfig 装配+测试/显式休眠声明/删除） | R1.0 |
| P2-6.6-002 | `6.6-check-p1-batch-b.md` | check 溯源 nop-batch#P1-2：续写 append 语义未落地（IResourceRecordIO 仅 openOutput；防截断护栏 fail-fast 已闭数据丢失面；复核终裁 P2：跨模块公共接口设计项） | 待 R1.0 裁决（IResourceRecordIO 增 append 形态或显式声明不支持） | R1.0 |
| P2-6.7-001 | `6.7-check-p1-batch-c.md` | check 溯源 ai-rest#P1-5：ShellCommandChecker 引号绕过（命令名/redirect target 不剥引号+解释器仅拦无参形态；复核终裁 P2：ExternalCommandAdapter 执行桩不可达，6.6 订阅桥同型） | 待 R1.0 裁决（unquote+解释器 `-c` 形态拒绝/白名单；**接入真实执行时升回安全 P1 优先**） | R1.0 |
| P2-6.7-002 | `6.7-check-p1-batch-c.md` | check 溯源 ai-toolkit-skills#P1-1：BashExecutor.parseEnv XML schema 通路断（直子节点 vs `<envs><env/>` 孙节点；复核终裁 P2：JSON 通路已覆盖+仓内 fromNode 无 main 调用方；注 4be11f2eba 仅 audit 分支——master 面只大不小） | 待 R1.0 裁决（childByTag("envs") 孙节点遍历+schema 回归） | R1.0 |
| P2-6.7-003 | `6.7-check-p1-batch-c.md` | check 溯源 ai-toolkit-skills#P1-5：DockerBashSandbox 特征串误判残余（partially-fixed——主体已修 9a495d728c+测试钉住；复核终裁 P2：触发窄化+诊断截断非全丢） | 待 R1.0 裁决（特征串限定 docker stderr 前缀/退出码 125 组合） | R1.0 |
| P2-6.7-004 | `6.7-check-p1-batch-c.md` | check 溯源 nop-code#P1-2：继承/使用边删除不重建残余（partially-fixed——call 族已修 3760af93a9+回归 8/8；复核终裁 P2：残余 2/3 边族+派生数据可重建） | 待 R1.0 裁决（N3.1-s 模式推广两边族/符号 ID 确定性派生） | R1.0 |
| P2-6.7-005 | `6.7-check-p1-batch-c.md` | check 溯源 nop-graph#P1-1：PathQueryExecutor 多跳 edgeType 仅扫 start 出边（复核终裁 P2：查询 API 无生产调用方，6.6 同型降档） | 待 R1.0 裁决（与 P2-6.7-006 Bfs 边谓词重载打包；接线前修） | R1.0 |
| P2-6.7-006 | `6.7-check-p1-batch-c.md` | check 溯源 nop-graph#P1-2：Bfs.traverseFiltered visited 语义反转（无条件 add+返回集含被拒节点；复核终裁 P2：唯一调用方 PathQueryExecutor 自身未接线） | 待 R1.0 裁决（独立 result 集合/改名+2 跳 edgeType 用例） | R1.0 |
| P2-6.7-007 | `6.7-check-p1-batch-c.md` | check 溯源 nop-graph#P1-3：PageRank 权重未按 Σw_out 归一化（复核终裁 P2：生产输入均匀权重=1 不触发） | 待 R1.0 裁决（归一化；接入加权图前须修） | R1.0 |
| P2-6.7-008 | `6.7-check-p1-batch-c.md` | check 溯源 nop-graph#P1-4：LeidenDetector 双向边对权重加倍（复核终裁 P2：指标质量偏置无功能失效；归一化键从未落地 git log -S 零命中） | 待 R1.0 裁决（min/max 归一化键+edgeWeights 传递+守恒用例） | R1.0 |
| P2-6.8-001 | `6.8-check-p1-batch-d.md` | check 溯源 frontend-benchmark#P1-1：JavaScriptService.refreshConfig AIOOBE+工厂绕过双缺陷面（复核终裁 P2：**nop-js 已注释出 reactor**〔pom.xml:22 亲读〕——P2-6.6-001 未接线同判例） | 待 R1.0 裁决（复活 nop-js 时先修+Truffle/JDK 兼容〔CL-3〕，或显式休眠声明+移除 web-defaults 装配引用；**升级触发=nop-js 复活入 reactor**） | R1.0 |
| P2-6.8-002 | `6.8-check-p1-batch-d.md` | check 溯源 demo-migration#P1-1：DemoBizModel 匿名调用 IUserContext.get() 裸取 token NPE 无 @Auth（复核终裁 P2：demo 单端点响亮 500 零契约受累，P2-6.5-001 同判例） | 待 R1.0 裁决（demo 波次补 @Auth 或匿名守卫+owner-doc 坏模式注记） | R1.0 |
| P2-7.1-001 | `7.1-nop-ai-incremental-audit.md` | WI25 AgentSessionLifecycle 离线注入 staging 语义解析失败静默无日志升级为 STEER（诊断信息丢失面） | 待 R1.0 裁决（解析失败 LOG.warn+显式分支注记） | R1.0 |
| P2-7.1-002 | `7.1-nop-ai-incremental-audit.md` | ReActAgentExecutor.canPublishExecutionCompleted 排除面缺 blocked+javadoc 失真（当前潜伏——blocked 全仓唯一写入在 plan 局部状态） | 待 R1.0 裁决（排除面补 blocked+javadoc 同步；升级触发=blocked 态接入 ctx） | R1.0 |
| P2-7.1-003 | `7.1-nop-ai-incremental-audit.md` | WI25 三新文件 11 处裸 IAE/ISE（plan 356 裸异常归零三周后增量回潮；复核勘正 11 非 12） | 待 R1.0 裁决（换 NopException+ErrorCode；归 4.1 族同批） | R1.0 |
| P2-7.1-004 | `7.1-nop-ai-incremental-audit.md` | nop-ai-rag PgVectorStore.toMetadataJson（:269-286，closure audit 勘正归属）手工拼 JSON 无转义（合法 metadata 含引号/换行即入库失败） | 待 R1.0 裁决（JSON 序列化器替代手工拼接） | R1.0 |
| P2-7.1-005 | `7.1-nop-ai-incremental-audit.md` | WI17 ToolManagerImpl.pathQueues 条目永不清理（每路径键永久留存 tail future 无界慢增长） | 待 R1.0 裁决（完成态清理或弱引用化） | R1.0 |
| P2-7.1-006 | `7.1-nop-ai-incremental-audit.md` | WI17 排队路径 executeNow.get() 同步抛出致 result future 永久 pending（默认 toolTimeoutMs=300s 钳为 5min 超时；显式关闭 timeout 时永久 pending） | 待 R1.0 裁决（同步抛转 future 异常完成+兜底文档化） | R1.0 |
| P2-7.2-001 | `7.2-nop-stream-incremental-audit.md` | WI5 JdbcTwoPhaseCommitSink.keyTuple 无类型标定/无 NUL 转义——异键静默折叠丢 op（P3 升档；测试盲区互证） | 待 R1.0 裁决（keyTuple 加类型标定+NUL 转义+异键用例） | R1.0 |
| AR-06〔backlog〕 | `7.2-nop-stream-incremental-audit.md`（invariant-loop 承载） | DebeziumMessageSource.dispatchEvent 逐 consumer 吞 collect 异常+offset 前移（复核撤销 P1 级升级：窗内 e20b7cef1b plan 368 R5-CON-02/03 已同形态闭合生产链路——P2 残余收敛） | 待 R1.0 裁决（**升级触发=绕过 DebeziumCdcSourceFunction 直接订阅**） | R1.0 |
| P2-7.6-001 | `7.6-nop-datav-followups-audit.md` | NopDatavDashboardBizModel.java:552 并行聚合层残留裸 ISE（W4 引入+源审计 P2-19 枚举漏+W7 修复漏双重遗漏链；复核新证 W3 时点 ISE=0） | 待 R1.0 裁决（typed 化与 W7 已修 4 处同批） | R1.0 |
| P2-7.6-still-open×21 | `7.6-nop-datav-followups-audit.md`（nop-datav-audit-followups.md 承载） | nop-datav followups open P2 still-open 21（抽样 25 条中标定；全清单 `_tmp/fcr-7.6-audit-a.md` §1；低成本快赢 5 处打包建议） | 待 R1.0 批量裁决（沿 P2 台账通道；未抽样余量 27 维持 backlog） | R1.0 |
| backlog still-open ×35 | `7.2-nop-stream-incremental-audit.md`（invariant-loop 承载） | invariant-loop Follow-up Backlog still-open P2×35（含残余收敛 10；近缘留痕 4 条）——全清单 `_tmp/fcr-7.2-backlog.csv`+标定台账 §二 | 待 R1.0 批量裁决（沿 P2 台账通道；watch-only 28 条终态不进） | R1.0 |
| P2-7.3-still-open×5 | `7.3-nop-metadata-incremental-audit.md`（arm-unclosed-findings-nop-metadata.md 承载） | nop-metadata 台账 P2 still-open 5 行（维度07-004 DTO List<Map> 未类型化/维度16-01 AutoTest 快照覆盖/维度16-03 重复 CRUD 反模式/维度16-07 data-auth 测试只验 XML/维度07-03 queryAggregation 11 参数无 @RequestBean——MR3/MR4 归属） | 待 R1.0 批量裁决（沿 P2 台账通道；fixed 8/watch 3 终态不进） | R1.0 |
| P2-7.4-001..003 | `7.4-nop-code-gated-incremental-audit.md` | nop-ai-rag compile 依赖零使用/LlmEdgeBudget 失败消费不回滚+竞态（措辞已收紧）/**roadmap done+plan draft 三例系统性脱钩流程缺陷族**（建议 R1.0 统一裁定状态对账机制） | 待 R1.0 裁决 | R1.0 |

## MR/MV/MG 状态与收口证据

> Wave 2/3 工作项（R1.0、R1.x 动态行、R2.0、V.1–V.3、G.1–G.3）的状态翻转与收口证据在此登记；R1.x 展开时逐行追加。

| 工作项 | 状态 | 证据 / 结论 |
|---|---|---|
| R1.0 | done | 汇聚 109 open P1 → 44 行 R1.1–R1.42+R1.6a/b/c 增补 roadmap MR 表；交叉核对零漏单；对抗审查 FAIL→3 Major+8 Minor 全修；登记 v7 |
| R1.1 | done | plan 2364（2026-10-09）：expr→compile 单边割离——expr 包零 compile 引用（`IExecutableExprCompiler` 接缝 + META-INF/services + fail-loud），公开契约零改动；SCC 21→20 compile 出环实测；三档治理成文（设计文档 + owner doc）+ 红线脚本版本化；nop-xlang 859 tests 全绿 + 接缝回归 4 用例；closure audit 见 plan 2364 Closure 段 |
| R2.0 | todo | |
| V.1 | todo | |
| V.2 | todo | |
| V.3 | todo | |
| G.1 | todo | |
| G.2 | todo | |
| G.3 | todo | |

## 汇总

- `summary.md`：MV 里程碑收口时撰写（总体发现分布、修复闭环率、残留风险清单）。
