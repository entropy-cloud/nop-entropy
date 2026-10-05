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
| 3.6 | — | | | | | todo | |
| 3.7 | — | | | | | todo | |
| 3.8 | — | | | | | todo | |
| 3.9 | — | | | | | todo | |
| 3.10 | — | | | | | todo | |
| 3.11 | — | | | | | todo | |
| 4.1 | — | | | | | todo | |
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


## P2 裁决台账（例外通道）

> 仅当审计报告显式开辟 P2 裁决例外时在此登记；R1.0 逐项裁决（修 / deferred 且带 Why Not Blocking Closure + Successor），裁决结果同步回 roadmap 行。

| Finding ID | 报告 | 描述 | 裁决结果 | Successor |
|---|---|---|---|---|

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
