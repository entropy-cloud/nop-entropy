# 346 check2 审计 P0-P3 条目全量处置

> Plan Status: active
> Last Reviewed: 2026-08-24
> Source: `ai-dev/audits/check2/`（26 份单元报告，447 条发现：P0=13 / P1≈70 / P2≈135 / P3≈229，以各报告发现列表为准）
> Related: `ai-dev/plans/344-check-audit-p1-p2-p3-remediation.md`（第一轮 check 系列处置，已完成的单元其修复可能已覆盖 check2 同位置发现，需逐条复核而非继承结论）

## Purpose

把 `ai-dev/audits/check2/` 26 份单元报告中全部 P0-P3 条目收口：每条获得明确的终态裁定并在原报告中标注，修复项附带回归测试，非修复项写清理由。check2 是对同一 live code 的独立复审（2026-08-23 基线），此后仅 nop-batch/nop-dyn 两单元在 08-24 经 plan344 修复，其余发现预计大部分仍为 live defect——每条必须对照当前代码复核，不得从 check 系列报告继承结论。

## Current Baseline

- 2026-08-23: check2 Phase 1（16 单元）+ Phase 2（5 单元）+ Phase 3 批次 3A（nop-report/nop-rule/nop-batch/nop-dyn）报告落盘；`../audits/check2/nop-metadata.md`（13 条）报告完整但未提交（前一会话中断遗留）。
- 2026-08-24: plan344 Phase 4 完成 nop-batch、nop-dyn 两单元修复（标注在 check/ 系列报告）；check2/nop-batch.md、check2/nop-dyn.md 未标注，其发现需对照已修复代码复核（预计大量"复查已修复/非问题"）。
- P0 抽查（2026-08-24）：StringHelper.parseQuery、AiAuthGatewayInterceptor Authorization 头、GlobalFunctions AND/OR subList 三处均确认仍为 live defect。
- 其余 check2 报告 0 标注；`grep '^### \[P[0123]\]' 计数` 与 `grep '处置（fix-ai-check' 计数` 的差值即剩余工作量。

## Goals

- 26 份报告中每条 P0-P3 条目末尾出现 `> **处置（fix-ai-check 分支，YYYY-MM-DD）**: ...` 标注，终态四类之一（与 plan344 惯例一致）：
  1. **已修复**：附修复说明 + 回归测试（行为类修复必须有能区分对错的测试；纯文案/死代码删除等注明免测试理由）。
  2. **复查非问题**：审计前提有误，或该位置已被后续修复覆盖（注明对应的修复 commit/测试）。
  3. **裁定暂缓**：需设计决策/大范围改造，写明决策点与影响面。
  4. **裁定不修复**：长期产品化维护角度不值得修（死代码无调用方、修复回归风险大于收益等），写明理由。
- 修复后对应模块 `./mvnw test -pl <module>`（必要时 `-am`）绿；新增错误码同步 i18n（zh-CN/en）。
- 新增错误码遵循两档策略：框架核心/公共 API 用 ErrorCode 常量，模块内部可用模块异常类；禁止 bare RuntimeException。

## Non-Goals

- 不重开 plan344 已裁定的暂缓/不修复条目（除非 check2 报告提供了新证据）。
- 不做超出条目最小修复范围的重构；修复中新发现的缺陷记录在标注里，不扩大范围。
- 不处理 check2 尚未产出报告的单元（nop-excel、format-record、file-retry-tcc 等 in-progress/pending 审计单元不在本 plan 范围）。
- 不修改 `docs-for-ai/` 规范内容（除非修复行为与 owner doc 冲突，此时按 AGENTS.md 上报）。

## Scope

### In Scope

- 26 份 check2 报告（Phase 划分见下）的 P0-P3 条目处置：复核、修复、测试、标注。
- 与修复直接相关的测试文件新增/调整、错误码与 i18n 资源。
- `ai-dev/audits/check2/ai-check2-roadmap.md` 进度日志、`ai-dev/logs/` 每日日志。

### Out Of Scope

- 各报告"补充说明（核查过但未立为发现的项）"段落。
- check2 审计本身未完成的单元（报告不存在的）。

## Execution Plan

> 单条处置流程：live code 复核（行号/证据以当前分支为准，先查该位置是否已被 plan344 等修复覆盖）→ 行为类修复先写红测试（stash 法验证红）→ 最小修复 → 绿 → 模块全量测试 → 报告条目标注。每份报告全部条目标注完成才算该单元完成。每个单元一个独立子代理执行，主会话负责验收、提交、plan/log 维护。

### Phase 1 - 含 P0 的单元（13 条 P0 优先清零）

Status: completed
Targets: 10 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] nop-commons.md（P0×1 P1×5 P2×7 P3×15，共 28 条）— 2026-08-24 完成：26 修复 + 2 暂缓（IoHelper 原生反序列化 ObjectInputFilter 白名单需平台裁定 / DateHelper Locale 语义需平台决策）。P0: parseQuery 多值收集 put(key,list)。红验证失败形态逐条吻合（splitChunk 在 HEAD 上 OOM 崩 JVM 为最强红证据）；268 tests 绿（+38 新用例）；报告两处事实偏差（P3-16 死校验非误抛 NPE、P3-23 OOM 非 /by zero）在标注中纠正；28/28 标注。附带修复 FileHelper.countLines 空文件 0 容量死循环（报告外新发现）；新发现 nop-xlang EvalHelper MINUS 分支疑用 MathHelper.min 已记录待 nop-xlang 单元处置
- [x] nop-xlang.md（P0×1 P1×1 P2×3 P3×5，共 10 条）— 2026-08-24 完成：7 修复 + 3 暂缓（EvalBackendRouter 热路径锁需 benchmark+CLD 契约被 truffle 30 处测试消费 / XDslExtender 环引用需错误码归属+物理 resourcePath 键控设计 / bare ISE 16 处错误码化因 i18n 聚合在 nop-cli-core 超单元范围）。P0: AND/OR 宏 subList(1,size())（AND(true,true) 曾恒 false）。超审计新发现 2 项一并修复：EvalHelper MINUS 误用 MathHelper.min（binaryOp(MINUS,1,2) 曾得 1）+ XplParseHelper 常量折叠 OptionalValue 未解包（const x=1+2 曾折叠得 0）。596 tests 绿（+12 用例）；10/10 标注。待跟进（记录未修）：SimpleSchemaValidator.validate 调 checkRange 实参 (bizObjName,propName) 顺序颠倒（潜伏，当前调用方两值多为 null）
- [x] nop-orm-eql.md（P0×1 P2×5 P3×5，共 11 条）— 2026-08-24 完成：10 修复 + 1 复查非问题（`_all` 比较符翻转 NULL 语义——SQL Kleene 三值逻辑下翻转与 NOT 包裹等价，审计前提有误）。P0: 集合属性表源转换在外层查询生成多余 join（CollectionTableSourceHelper buildRelationJoin 后 removeIf 撤销 propJoins 注册，红验证 APP_ROLE 曾出现 2 次）。P2: returning `*` 方言检查前移/hex·bit 双重剥离改裸串直解/ILIKE 静默降级改抛 ERR_EQL_NOT_SUPPORT_ILIKE/to-one 表源丢别名（三参重载透传 userAlias）。P3: 死代码 92 行删除/聚合 `*` 越界抛错/addTable 先检查后 put/Date 字面量 ISO TIMESTAMP/SqlParameterMarker.clone 复制 masked。57 tests 绿（+12）+ nop-orm 回归 178 绿；11/11 标注。待跟进：nop-core FilterBeanToSQLTransformer contains 拼 ilike 同源问题（归 nop-core 单元记录）
- [x] db-migration.md（P0×2 P1×4 P2×7 P3×3，共 16 条）— 2026-08-24 完成：16 条全部修复 + 超审计项 1（DEFAULT 三处裸拼接点镜像 nop-dyn checkDefaultValue 校验，新错误码）。P0: ① 9 种变更解析即 CCE（根因 xdef：key-attr 被截断 + 17 变更/5 precondition 模型缺 bean-extends-type + preconditions 集合声明缺失；改 migration.xdef 后官方生成器 XCodeGenerator.renderModel 再生 19 个 _gen 类，非手改；scanner 映射扩 16 种执行器）② insert/update `<column>` 解析为 DynamicObject（xdef 补 xdef:name 指向既有 InsertColumnModel/UpdateColumnModel）。P1: 历史表 success 列方言化+参数绑定/DdlSyntax 方言族助手（AUTO_INCREMENT/COMMENT ON/ALTER COLUMN 等）/checksum 序列化变更内容+repeatable 重跑/表级 PK·UK·FK DDL。P2: 错误码两参形态/precondition 接入引擎+大小写不敏感/ignore·runOn·contexts·labels·failOnError 接线/recordMigration UPDATE-or-INSERT/null INDEX_NAME NPE。P3: mssql 别名/排序拷贝/死代码。95 tests 绿（基线 58，+37）+ dbtool 4 绿 + 全仓无 db-migration.model 外部导入（基类变更封闭）；16/16 标注。新发现记录：H2 USERS 系统表负向断言陷阱/executeMark 无执行器静默跳过/Oracle 无 INFORMATION_SCHEMA/mssql remark 静默丢弃
- [x] nosql-cdc.md（P0×1 P1×2 P2×9 P3×13，共 25 条）— 2026-08-24 完成：22 修复 + 1 复查非问题 + 2 暂缓，25/25 标注。P0 复查非问题：RESP3 下 Lua boolean 恒被真实 Redis 扁平化为整数 1/null（6.2/7.2/7.4/8.10 四版本 raw socket `HELLO 3`+`EVAL "return {true,42}"` 实测均回 `:1`/`:42`，lettuce 实客户端 MULTI 返回 [Long 1, Long 42] 无异常）——审计仅凭字节码推断未接真实服务端，误报。P1: PubSub 消息串投（SubscriptionEntry 补 topic 过滤）/PrefixTextCodec Number/Boolean 往返退化（暂缓：nop-auth ask-first + PrefixEncodeHelper 在 nop-core plan-first 保护区，已落 javadoc 值类型契约）。P2: cancelled 污染/ClientResources 泄漏（线程计数红 167→179）/verifyPeer 默认 true 接入/expireAfterAccess NPE/getAll 过滤缺失 key/空集合守卫补全/getTopN(0)/parseTokenCount 小数/hash removeIfMatch 原子 Lua。P3: 13 条中 11 修复（死常量删除/parseHostPort IPv6/sentinelNodes 配置落地/session_set_field.lua EXISTS 守卫/sendAsync 委托真实 publish/订阅失败回滚/forEachEntryAsync mget/…）+ 1 暂缓（$d: 任意类加载白名单属平台安全决策）。新增 25 用例（TestLettuceNosqlService +14 docker、TestLettuceRedisConnectionProvider 新类 6、TestLettuceRateLimiterValidation 新类 3、nosql-core 首个测试 TestNosqlCache 2）；stash 红验证 17 处（docker 13 + dockerless 4）形态吻合；无 docker 76 run/0 fail（67 skip 为 docker-gated）、带 `-Dnop.test.docker.enabled=true` 76/0/0；`-am` 链含 nop-cdc BUILD SUCCESS。报告事实纠正 2 处写入标注（P2-6 失败形态实为客户端 IAE；putAllAsync 实无防护）。新发现记录：RoundRobinSupplier 构造即同步建连致 provider.start() 无可达 Redis 时失败
- [x] gateway-bizauth.md（P0×2 P1×4 P2×5 P3×7，共 18 条）— 2026-08-24 完成：18 条全部修复（另有 4 个嵌套暂缓子项附决策点写于对应标注内：可信代理 IP 解析/断连取消管道/默认扩展名黑名单/per-fallback 凭证注入）。P0×2 同根因（Vertx/Servlet 链路 header key 小写化，混合大小写读取恒 null）：AiAuthGatewayInterceptor 改 IHttpServerContext.HEADER_AUTHORIZATION 小写常量（曾启用即全量 401）；AiRateLimitGatewayInterceptor 改 x-forwarded-for + normalizeKey（限流 key 曾全塌缩 default 共享 1 QPS）。P1: 拒绝异常双丢失路径（同步抛出穿透+异步被 ErrorMessageManager 改写 httpStatus=0，叠加旧兜底 200 实收 HTTP 200 比审计"语义 5xx"更严重，标注中已修正报告事实）/BufferedStreamingPublisher demand=0 时 onComplete 丢尾部（pendingComplete 挂起终态）/限流桶换 LocalCache TTL+上限（与 P0-2 耦合统筹）/isAllowedRedirectUri 前缀绕过（URI 边界判定，封堵 host 后缀+userinfo+端口伪装）。P2: API key/token 日志脱敏/两 store maxEntries 上界/Retry-After 三路径钳制 30s/fallback 默认剥离 authorization/x-api-key/cookie+maxRetries 落地。P3: forward(null) 抛 IAE/header 过滤双侧小写/扩展名双侧 toLowerCase/configLoaded volatile/ChunkFileUpload 空实现抛 UnsupportedOperationException/错误响应兜底 500/实例缓存 volatile。新增 30 用例/6 新测试类（biz-file-core 首建测试基建）；stash 红验证失败形态逐条吻合；nop-gateway 88 + biz-auth-core 70 + biz-file-core 5 = 163 tests 绿。新发现记录：LocalSmsCodeStore 同源惰性清理模式/Retry-After 恒静态值/单模块测试从 project-local-repo 解析旧 SNAPSHOT jar 环境隐患
- [x] nop-job.md（P0×1 P1×2 P2×2 P3×13，共 18 条）— 2026-08-25 完成：17 修复 + 1 暂缓（P3-7 trigger 热路径重建缓存需缓存键/状态性证明设计决策 + benchmark 支撑，报告自述无功能危害）。主体修复于 08-24 commit 35623e49cf（14 条：P0 Once 双层修复=OnceTrigger 持久化判定 + planner isTriggerExhausted 防线，端到端 planner 测试覆盖；P1-1 存活链 @Inject @Nullable 接线 + worker-service-name 配置；P1-2 poll 线程池 + poll-timeout-ms；P2-1 CLAIMED 认领写 startTime 纳入回收扫描；P2-2 yearDays 闰年校验新错误码；P3×9），08-25 补齐 4 条残留（P3-8 drainBatch null 排序键防御/P3-10 BeanContainerInvokerResolver instanceof/P3-11 insertTasksAndMarkFireDispatching 改 boolean + dispatcher 条件计数/P3-3 空壳 FireFactory 删除于 08-24 commit）+ HttpRpcPollTaskClient 双 import 格式修正。红验证：3 条新修复 stash 红（fetchCalls 1≠2 / CCE≠NopException / firesDispatched 0≠1），08-24 部分引用其 commit 内红验证测试。core+local+dao+coordinator+service 五模块 BUILD SUCCESS（coordinator 197 run 0 fail 含 6 专名新用例）。报告事实纠正 1 处（P3-6 ThreadLocal 跨线程干扰前提不成立，真实缺陷仅内存驻留）；18/18 标注。
- [x] nop-task.md（P0×2 P1×6 P2×6 P3×5，共 19 条）— 2026-08-25 完成：14 修复 + 5 暂缓。P0×2（suspend 出口 metrics.endStep(meter) 判空 NPE + TaskImpl 挂起分支：SUSPENDED(20) 持久化、不 runCleanup/endTask/COMPLETED，恢复不短路）。P1 修复 3（catch 出口 endTask 判空/BuildOutput 包装透传 SUSPEND/Graph 错误边可达性——生产方 nextOnError 在 TaskStepExecution 层被拦截的事实纠正写入标注）+ 暂缓 3（fork 分支共享 stepPath 竞态需 ORM 唯一索引属 plan-first 保护区/continuation-skip 不恢复 outputs 与 stateBean 未持久化均需持久化格式向后兼容设计）。P2 修复 5 + 暂缓 1（persistVars 死代码涉 nop-xdefs 契约变更）。P3 修复 4 + 暂缓 1（DaoTaskStateStore 装配决策且 _dao.beans.xml 为生成文件）。新增 9 测试类 17 用例 + 8 个 task.xml 资源；stash 红验证 14 红 3 守卫绿（形态：NPE/ERR_TASK_UNKNOWN_NEXT_STEP/state.isDone 等）；nop-task-core 116 tests 绿。
- [x] nop-report.md（P0×1 P1×5 P2×10 P3×10，共 26 条）— 2026-08-25 完成：21 修复 + 2 暂缓 + 3 不修复。P0: COUNTIF/SUMIF 多字符操作符字典序比较（显式匹配 >=/<=/<> 优先；顺带修复 SUMIF 无 sumRange 时迭代器别名错位——编写测试时发现的相邻缺陷）。P1×5 全修（avg 分母/childCell col 分支/getExpandableRowParent 错递归/resolveAllCellsInColParent 误用 getRowDescendants/FontManager.registerSystemFonts 接线）。P2 修 9 + 暂缓 1（minReuse/maxReuse 语义需引擎 owner 裁定）。P3 修 7 + 暂缓 1（getRowIndex O(n²) 需索引缓存设计）+ 不修复 2（getCol/getRow 契约统一爆炸半径大且现实 NPE 路径已消除/ExcelRecordInput 全量载入为既定设计权衡 + 聚合 Excel 语义偏差改返回值破坏兼容性）。新增 11 测试类 24 例 + 既有测试 +16 例；stash 红验证逐条形态吻合（含 TextWrapHelper wrapByWord 死循环至 OOM 的 JVM 级红）；core 64+pdf 12+docx 2+demo 24+service 0 全绿。
- [x] nop-rule.md（P0×1 P1×3 P2×3 P3×8，共 15 条）— 2026-08-25 完成：12 修复 + 1 复查非问题 + 2 暂缓。P0: DecoratedExecutableRule 正常路径 return 前调 afterExecute（对齐 rule.xdef 契约）。P1×3 全修（NormalizeInput inputs==null 判空/Excel 注释白名单校验恒真反转/DaoRuleModelSaver 重复 predicate 一次性消费）。P2 修 2（mandatory 未命中不抛 + logMessage 降 debug）+ 复查非问题 1（空输入规则编译抛裸 IAE 前提有误——ApiStringHelper.isEmptyObject 不判空集合，stash 实测无输入规则可正常编译，保留行为固化测试）。P3 修 7 + 暂缓 2（visitOr 空 children 恒真为 nop-core/nop-xlang/nop-rule 三处平台统一惯例需平台层裁定/规则日志落库功能补全需缺省 saver 设计决策，现状默认关闭无错误行为）。新增 4 测试类 16 例 + 2 处追加 + xlsx fixture；stash 红验证 8 处形态吻合（含全链路 upload→save→executeRule 的重复 predicate 红，须 core/dao/service 同 reactor 跑避免 m2 旧构件误红）；8 子模块 43 tests 绿。

Exit Criteria:

- [x] 10 份报告全部条目有处置标注（grep 对账：findings 计数 = 处置标注计数；2026-08-25 核验 nop-commons 28/28、nop-xlang 10/10、nop-orm-eql 11/11、db-migration 16/16、nosql-cdc 25/25、gateway-bizauth 18/18、nop-job 18/18、nop-task 19/19、nop-report 26/26、nop-rule 15/15）
- [x] 13 条 P0 终态均为已修复或复查非问题（P0 不允许暂缓/不修复）（2026-08-25 逐条核验：12 已修复 + 1 复查非问题 nosql-cdc RESP3）
- [x] 修复项对应模块测试绿（各单元标注内含命令与 run/fail 数；2026-08-25 主会话对 nop-task/nop-report/nop-rule 独立复跑 BUILD SUCCESS）
- [x] `ai-dev/logs/` 对应日期条目已更新（08-24 各单元 + 08-25 nop-job/nop-task/nop-report/nop-rule 条目）

### Phase 2 - 框架层其余单元

Status: completed
Targets: 8 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] nop-core.md（P1×3 P2×6 P3×10，共 19 条）— 2026-08-25 完成：19 修复 + 附加任务复查非问题 1（FilterBeanToSQLTransformer contains/ilike 同源疑点：产出经 env.compileSql 进入 EQL 编译层，eql 单元 08-24 的 ILIKE 方言修复已覆盖，值全 .param() 绑定，%/_ 透传为平台惯例——写入报告「超条目处置」节）。P1×3 全修（XNode.removeAll 倒序遍历/NioFileWatchService watchKeyMap 改 ConcurrentHashMap+IOException 不再杀循环/BeanCopier._copyToCollection 先 clear）。P2×6 全修（ExecutionContextImpl 终态竞态锁内双检/DefaultVirtualFileSystem.refresh 先建后关/DefaultDirectedGraph 悬空边/JsonMerger.mergeMap 无入参副作用/CsvRecordInput·Output 构造泄漏 safeClose）。P3×10 全修（resolveModelLoader 冒号校验+新错误码×2/reinitialize 回写/containsCycle allowLoop/ResourceCacheEntry volatile×2/JsonTool.loadDeltaBean static/EvalScopeImpl locations 同步/ResourceHelper INFO→DEBUG/freeze(false) detach 错误类型/ZipResourceStore 泄漏/四处单例 volatile）。新增 4 测试类+6 既有扩展共 22 用例；stash 红 18 用例（其余竞态/签名/日志类免红注明）；nop-core 277 tests 绿（2 既有 skip）。超范围新发现记录（未修）：DefaultDirectedGraph.removeVertex 自环边 NPE、JsonMerger.mergeList 分支返 listB 引用同族。框架核心保护纪律：全部最小修复，无契约变更。
- [x] nop-api-core.md（P1×2 P2×9 P3×14，共 25 条）— 2026-08-25 完成：25 条全部修复。P1×2（QuerySourceBean/QueryFieldBean.cloneInstance 丢字段，含 TreeBean 深拷贝）。P2×9（disableExpireTime 代理生效/PointBean off-by-one×2/stringToLong 单字符单位 NPE/stringToNumber e·E 并存拒绝/IntRange·LongRange 空段错误码/removeHeader 同锁/PlaceholderConfigReference volatile/CrudApiPageIterator 无限拉取三态 eof/appendHeaders 敏感头脱敏——顺带修同族两缺陷：FALSE 分支丢末页数据+hasNext eof 判序，两迭代器仓内无调用方）。P3×14（whenComplete 补 LOG.error/TimeOut.isExpired/registerNamedConverter Guard/StaticBeanContainer.getBean 抛 unknown bean/FilterBeans 委托 varargs/replaceChild 判空/stringToMonthDay 数值校验/treeEquals Objects.equals/DictBean volatile×2/MultiCsvSet notNull 语义澄清/freeze cascade 透传/deepMerge 不污染 m1/encodeStringMap 尾分隔/setFieldNames 替换语义）。新增 8 测试类+11 既有扩展；stash 红 21 条（4 条竞态/日志/语义澄清免红注明）；nop-api-core 115 tests 绿。无新增 ErrorCode。
- [x] kernel-small.md（P1×1 P2×6 P3×6，共 13 条）— 2026-08-25 完成：12 修复 + 1 文档方案（RowNumberRecordInput unchecked cast 改 javadoc 类型约束说明）。P1: DataParameterBinders.FLOAT 改 getDouble/setDouble 对齐 DOUBLE 声明（红 CCE）。P2×6 全修（ReflectClass.remove 键统一 getSignature+删 mergeList 死代码/MarkdownSectionMerger summary/TableViewToMarkdownTableConverter padToColumns——报告建议的 setSize 截断语义不适用已注明/PomModelMerger child 依赖+modules 不继承/CodeBlock.append 复用 buf/getObjectConstructor LinkedHashMap 兜底）。P3 11 修（SingleColumnRow 抛 ERR_DATASET_IS_READONLY/JdkJavaCompiler -source 取 Runtime 版本+URI 错误码化/KernelCliValidateCommand printStackTrace→LOG/requireField 传参纠正）。新增 7 测试类+2 扩展；stash 红 11 处；dataset 15+codegen 23+markdown 80+record-mapping 51+javac 7 绿。**超范围新发现（未修，记 follow-up）**：nop-kernel-cli pom maven-compiler `-proc:only`（commit 7f8b955822 笔误）致模块源码完全不编译、10234 class 全来自依赖、测试从未执行。
- [x] nop-core-framework.md（P1×4 P2×10 P3×9，共 23 条）— 2026-08-25 完成：20 修复 + 1 复查非问题 + 1 暂缓 + 1 不修复。P1×4 全修（BeanParentResolver 环引用前置 RESOLVING+异常复位/ProducedBeanInstance 新增 proxyHandler 引用解 JDK 代理强转 CCE/ConfigExpressionProcessor 每个 ${} 独立 configVars/ConfigStarter.getProfiles 改 getName()——超审计一并修 nop.profile.parent ValueWithLocation 转换崩溃）。P2 修 9 + ServiceProxy equals/hashCode 维持 W4 Phase 1 保留裁定记复查非问题（dumpDisabled getMissingClass/set 用 LinkedHashSet/app-beans include OR 语义/Files.walk try-with-resources/ChangeSubscriptions 逐监听器隔离/unloadPlugin 同侧成功路径/KeySetHelper RFC7518 最小八位组/Log4j2 精确查找/prototype runXpl 独立 scope/ServiceProxy 解包 ITE）。P3 修 7 + 不修复 1（良性竞态现状允许）+ 暂缓 1（条件求值 5 轮上限的收敛语义需设计裁定）。新增 14 测试类+2 扩展+夹具；stash 红 18 处形态吻合（SOE/CCE/注入错值/profiles 空/ITE 等）；7 子模块 207 tests 绿。附带修复预存失败 TestAop.testDynamicGen（上游 nop-core AOP 模板行为变化致 nop-ioc 期望夹具漂移，非本单元 23 条）。审计建议勘误 1 处（DisabledEvalScope.newChildScope 不可用）。模块路径注记：nop-core-framework 单元的子模块实际为根下 nop-core-framework/{nop-ioc,nop-config,nop-plugin/*,nop-log/*,nop-security,nop-boot}。
- [x] nop-orm.md（P1×3 P2×8 P3×9，共 20 条）— 2026-08-25 完成：18 修复 + 1 复查非问题 + 1 暂缓。P1×3 全修（getIdText 复合主键遍历 pkColumns/existsDict 传去前缀 sqlName/游标分页按完整排序键生成 keyset 条件——支持 desc/null/is null，不支持场景抛新错误码 ERR_ORM_CURSOR_ORDER_BY_NOT_COLUMN·ERR_ORM_CURSOR_SORT_VALUE_NULL；顺带修 queryToFindPrevSql 空 filter 漏 where）。P2 修 6 + 复查非问题 1（MdxQuerySplitter——报告把 getRelation(name,false) 的 ignoreUnknown 语义读反，未找到实际抛 ERR_ORM_UNKNOWN_PROP）+ 暂缓 1（DaoResourceFileStore.detachFile 唯一引用时物理删除文件，两个统一方向均有数据后果需产品决策）。P3×9 全修（含 flushAsync 失败路径补发 onFailure——waitAll 对 rejected ResolvedPromise 不透传异常以双源判定缓解，根因在 nop-api-core 记 follow-up；load/lock/batchLoad SQL volatile 单槽缓存+方言守卫）。新增 10 测试类+2 扩展+测试夹具 app.orm.xml 增 revision 测试实体（非产品模型）；stash 红 13 处；nop-orm 197 tests 绿（基线约 178，6 skip 既有）。
- [x] orm-periph.md（P1×2 P2×3 P3×8，共 13 条）— 2026-08-25 完成：13 条全部修复。P1×2（RpcEntityPersistDriver.loadAsync 改传 ids 列表+响应按 List 解析按主键匹配——复核发现仅改参数名会 CCE 一并修；无数据补 markMissing 与 batchLoadAsync/Jdbc/Td 对齐）。P2×3（hasLazyColumn 恢复 false/addJoin 改 continue+ERR_PDM_REFERENCE_NO_JOIN_COLUMN/getCollectionModel pos<=0 返 null）。P3×8（Pdm 缺 Name/Code 错误码化/drainTo 攒批契约+maxElements 防御/OrmReferenceModel ormModel 死代码三处全删/TdSqlHelper binder 死参全链路删除+删除分支按 querySpace 解析方言/GeometryTypeHandler.fromLiteral 显式 UnsupportedOperationException/removeViewsNoPk tables+tablesByCode 同移+WARN/getColumnPropIds 声明序/toUpperCase Locale.ROOT——土耳其语 locale 红验证）。新增 2 测试类+6 扩展共 18 用例；stash 红 13 处；rpc 7+orm-model 9+pdm 9+orm-data 6+tdengine 13+geo 13 全绿。超范围未修记录：PdmModelParser 不解析 Model 根下顶层 c:Views（静默忽略，建议独立裁定）。
- [x] nop-dao.md（P1×2 P2×6 P3×10，共 18 条）— 2026-08-26 完成：18 条全部修复。P1×2（JdbcBatcher onSuccess 区分 EXECUTE_FAILED(→0)/SUCCESS_NO_INFO(singleChange 保守 1) + isSupportBatchUpdateCount 改读 features 激活死配置；stopOnError=false 残留命令统一 failRemainingCommands 清空——4 个异常出口全覆盖）。P2×6（jdbcSet/setJsonString 索引 +1、SimpleDataSource 失败关连接、非批量异常路径回调契约、forceTxn 先 commit/rollback 后回调、runInTransactionAsync 回滚失败 suppressed——实现注记：ResolvedPromise.whenComplete 对失败 stage 只记日志不替换、exceptionally 吞成功路径原始异常，handle 为唯一正确挂点，红→修两轮）。P3×10（metrics 计时 finally 兜底/rs 死代码删除/getTableMeta 转义/translator 预编译——复核推翻报告 Pattern.quote 建议：duckdb errorCode 值本身即正则/UnknownEntityException toString/SnowflakeSequenceGenerator 全量重命名（含 nop-sys-dao 引用，-am reactor 编译验证）/escapeSQLName 空串新错误码/listeners CopyOnWriteArrayList/pagination limit 钳制/callFunc 读 OUT 参数——H2 execute() 恒 ResultSet 形态故不分流）。新增 6 测试类 + 既有 4 类扩展共 37 用例；stash 红 21 处（含 H2 内存库泄漏探针、事务 suppressed 双版）；nop-dao 132 tests 绿（45 skip 为既有 docker-gated）。
- [x] xlang-java-truffle.md（P1×2 P2×3 P3×5，共 10 条）— 2026-08-26 完成：8 修复 + 1 文档方案 + 1 不修复。P1×2（descriptor 声明恒 Object——未初始化读取对齐解释器 null 语义，推断 kind 保留 SlotMeta 供 typed 写入分派；翻译失败关联改 per-request 标志——XLangLanguage.parse 抛错路径置位经 TranslatedEval 透传，事件 map 降级为细节载荷，协议异常关联整体删除）。P2×3（close() 加 synchronized 与 pool() 同锁；Float NaN/±Infinity 字面量特判；SlotScan 补记 ForOf/ForIn/Try 运行时写入源）。P3：单例 INSTANCE 提前返回不设 section/wrapCallFuncException 首参传 LOC 常量（2 个 golden fixture 经 GeneratedFixtureMain 再生）/scope 契约 javadoc/SyntheticSources 按路径共享网格 Source（pow2 几何增长）；XExprNode @Child 裁定不修复（报告自述有意设计取舍）。新增/扩展 5 测试类 12 用例 + fixture 2 再生；stash 红 8 处（descriptor kind/ForOf 推断/Source 共享/陈旧事件误降级/close 泄漏/Float 字面量/wrapCallFunc 首参）；truffle 590 + xlang-java 495 tests 绿。

Exit Criteria:

- [x] 同 Phase 1 前三条（grep 对账：2026-08-26 核验 nop-core 19/19、nop-api-core 25/25、kernel-small 13/13、nop-core-framework 23/23、nop-orm 20/20、orm-periph 13/13、nop-dao 18/18、xlang-java-truffle 10/10——Phase 2 全 8 单元 141/141 条目标注）
- [x] `ai-dev/logs/` 对应日期条目已更新（08-25 六单元 + 08-26 nop-dao/xlang-java-truffle 条目）

### Phase 3 - 业务层其余单元

Status: completed
Targets: 8 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] nop-biz.md（P1×3 P2×5 P3×5，共 13 条）— 2026-08-26 完成（commit e3c2e4145a；08-28 回填勾选）: 12 修复 + 1 暂缓。P1×3（TreeEntityHelper 三方法增 IEntityModel 参数，CTE 锚点段+递归段按 isUseLogicalDelete 补 deleteFlag 过滤；DevStat 4 操作补 @Auth(roles=admin)；collectDecorator(BizActionModel) 补 cacheEvicts 消费）。P2: decoratorCollectors 经 beans.xml 显式接线/CacheActionDecorator 共享引用契约 javadoc/callActionAsync 三级判空错误码/fromEvalContext 适配修 CCE。P3: MakerChecker 补 bizObjName/参数描述回退/requireObjMeta/objDef null 放行/克隆清空全部主键列。暂缓 1（DecoratorCollector 默认注册需设计核定，记 follow-up: CTE 同缺租户过滤）。新增 7 测试类+3 扩展 31 用例；stash 红 9 轮 11 处；nop-biz 69 tests 绿
- [x] nop-graphql.md（P1×2 P2×5 P3×8，共 15 条）— 2026-08-26 完成（commit 8a15ebccc7；08-28 回填勾选）: 15 条全部修复。P1×2（GraphQLExecutor maker-checker 分支改用当前顶层字段 selection.getFieldDefinition + request 按 checkField 惯例取 opRequest 回落 args〔HTTP 路径原恒 null〕；偏移分页 hasNextPage 比较改 data.size()<生效 limit〔原恒 true〕）。P2×5（WS 身份绑定注入期执行使 4401/4403 可达/null variables 归一空 Map/订阅 onNext 不逐消息 complete/first·last 重置后按 maxFetchSize 钳制/grpc 枚举按 scalar 风格包装）。P3×8（错误码纠正/SIOOBE 防御/订阅单次序列化/deepClone 补拷 makerCheckerMeta/loader key deepToString/filter 与 fetchNext 新错误码，i18n zh/en 同步）。报告 P3 统计笔误（7→8）按发现列表修正。新增 8 测试类+5 扩展 29 用例（含 nop-graphql-orm 首个测试基建）；stash 红 14 处；core 122 + orm 8 tests 绿、grpc compile 通过
- [x] nop-auth.md（P1×2 P2×4 P3×9，共 15 条）— 2026-08-26 完成（commit 72e7cc0ae3；08-28 回填勾选）: 9 修复 + 6 暂缓。P1×2（DaoLoginSessionStore 复制粘贴错误 setLoginType(LOGOUT_TYPE_NONE) 改 setLogoutType，恢复 killLoginAsync 等对真实会话命中；LoginServiceImpl.incrementLoginFailCount 实例锁内 read-modify-write 消除丢失更新〔多节点共享缓存原子性记残留边界〕）。修复: checkExpired null fail-closed/Db CodeStore duplicate-key 回退 update/setSkew 仅漂移写入/sendSms code 判空/恢复码位掩码/日志脱敏/SSO NOT_IMPL 错误码。暂缓 6（定时清理需调度宿主+索引 DDL；4 条 ask-first 保护区〔权限判定/用户可见行为语义〕；可信代理跳数设计决策）。新增 6 测试类+19 用例；红验证 10 处；auth-service 424 + auth-sso 8 tests 绿
- [x] nop-sys.md（P1×1 P2×4 P3×8，共 13 条）— 2026-08-28 完成：10 修复 + 2 复查非问题 + 1 暂缓。P1: DefaultCodeRule `@seq:N` 超宽静默截断改抛新错误码 ERR_SYS_SEQ_VALUE_EXCEED_LIMIT（不复用语义不符的 ERR_SYS_CHAR_COUNT_EXCEED_LIMIT）。P2 修 4（持久订阅 suspend/resume 接线——SubscriptionState volatile suspended、全部挂起回 ConsumeLater 重投；审计拦截器无上下文线程 null 回退，连带修复超条目发现：INSERT 审计记录随外层会话 flush 回调静默丢弃，改独立会话 saveDirectly；事件清理扩展 FAILED+超滞留 WAITING/CLAIMED，新配置 nop.sys.event.cleanup-stale-waiting-days；SysCompactExtFieldHelper afterEntityChange 失效接线）。P3 修 5（三处重复 LOG.trace/锁空转退避/postUpdate 跳 version 列/取消订阅清空 topic/泛化 extN 空格回落 null）+ ERR_SYS_NO_SEQ 启用并解除 BaseTestCase 生产依赖。复查非问题 2: claimNonBroadcastEvents/processClaimedNonBroadcastEvent **非死代码**——nop-batch-sys 的 non-broadcast-consumer.batch.xml 以 XLang 调用（Java 向 grep 漏检 DSL 调用点，本条审计前提与处置子代理复核均漏检；本批曾删、主会话 reactor 验证暴露 nop-batch-sys 5 测试 no-obj-method 后恢复并修正标注）；restartElection 系 ILeaderElector 契约方法（failover 测试演练）。暂缓 1: audit-save vs audit 标签语义需产品裁定。新增 4 测试类+4 扩展共 14 用例；红验证 10 处；dao 52 + service 15 + nop-batch-sys 5 tests 绿
- [x] nop-wf.md（P1×4 P2×8 P3×3，共 15 条）— 2026-08-28 完成：13 修复 + 2 暂缓。P1×4 全修（doReject 步骤名误查 stepId 改 getLatestStepByName；joinGroupExpr 端到端接线——写侧持久化求值结果+读侧用 join 步骤自身 expr 求值，顺带消除孤儿 WAITING 实例；designer saveDocument 改 fail-closed〔既有 6 用例编码 fail-open 行为，同步改 admin 上下文〕；TO_ASSIGNED 空目标在状态变更前抛错）。P2 修 7 + 契约文档化 2（IWorkflowStep 管理方法/ApprovalFlowHelper"调用方自行鉴权" javadoc、IWorkflowExecutor 并发语义 javadoc）+ 暂缓 2（系统旁路信任通道——check1 075561f891 刻意 warn-only，需与步骤管理鉴权钩子合并设计评审；actor 匹配改变 join 复用拓扑，需先定义 actor×joinGroupExpr 组合语义）。P3 修 3（Integer 判等/未读 serviceContext 字段删除/其余）。新增 12 用例 + 2 个 xwf 测试模型；红验证 10 处；service 121（1 skip 既有 @Disabled）+ scheduler 7 + ai 4 tests 绿（core/dao 无测试源）。报告事实纠正 1 处（P2-10"非 NopException 中断循环"前提不成立——syncGet 已 adapt 包装）记入标注
- [x] nop-batch.md（P1×5 P2×6 P3×9，共 20 条）— 2026-08-28 完成：11 修复 + 9 复查非问题（后者已被 08-24 plan344 修复覆盖——逐条对照当前代码验证并重跑既有测试确认，非照抄 check 系列结论）。P1 本次修 3（BizExportTaskBuilder/FileBatchSupport 的 newExcelWriter 补 resourceLocator；JdbcPageBatchLoaderProvider 游标改 findPage 成功后才提交 range，重试重载失败页）+ 复查已修复 2（AsyncFetch semaphore 永久阻塞、saveProcessed 空实现——7defae0e69）。P2/P3 本次修 8（EtlTaskStateStore 原子写与状态文件崩溃截断、forTag 校验接线〔新错误码；构建期行为变更——依赖旧宽松行为的存量 .xbatch 将在加载期报错，发布说明提示〕、JdbcBatchConsumerProvider 表元数据跨 setup 残留、ResourceRecordConsumer 并发写序列化、masking 配置、死代码等）。新增 10 测试文件 16 用例；红验证 6 处+免红注明；9 模块 70 tests 绿
- [x] nop-dyn.md（P1×4 P2×7 P3×5，共 16 条）— 2026-08-28 完成：5 修复 + 11 复查非问题（P1×4 全部已被 08-24 plan344 commit 0cfa0dba 覆盖——逐条当前代码验证+测试跑绿）。本次修复: InMemoryCodeCache 懒生成 merged store 空窗（事实修正：审计"永远 miss"前提部分不成立——ResourceTreeNode.merge 别名节点使新文件经共享祖先可见，真实残留是字段 null 空窗+依赖别名实现细节）/OrmModelToDynEntityMeta 名字口径+relation 去重（连带修 2 邻接缺陷：查找表不写回复制 meta、首轮 to-one 关联静默丢弃）/getPage pageGroup 漂移（附带修复原 module.moduleName 过滤字段 xmeta 未暴露致合法查询必抛 unknown-query-prop）/APP_STATUS 常量语义修正/租户缓存并发 init 原子化。新增 3 测试文件+扩展共 6 用例；红验证 6 处；dao 14 + service 30（1 skip 既有 @Disabled）+ web 2 tests 绿
- [x] nop-metadata.md（P1×2 P2×6 P3×5，共 13 条）— 2026-08-28 完成：13 条全部修复（3 条含子项暂缓，决策点已记标注）。P1×2（ExpressionMeasureValidator FUNCTION_BLACKLIST 补 H2/PG 文件族 14 条+修正死条目 xp_cmdshell→XP_CMDSHELL，与 custom_sql 沙箱水位对齐；MetaTableProfiler 整列拉取 OOM 增 10000 行上限——超限降级 unavailable 不伪造值）。P2: `jdbc:h2:file:` 路径默认拒绝（新配置 nop.metadata.datasource.h2-file-allowed-dirs）/血缘 stale 边清理/delete 失败隔离/对账 fetch-limit 显式化（statistics 恒记 fetchedLimit+truncated）/密码不 trim/agg byte[] 类型守卫等。P3: dict item null 值/post-commit 契约文档化/重复实现收敛/日志策略等。新增 8 测试文件+mock/beans+扩展共 25 用例；红验证 17 处形态吻合；core+dao+service 1339 tests 绿（0 skip）。owner doc docs-for-ai/03-modules/nop-metadata.md 安全契约节补 4 条（黑名单文件族/h2:file 默认拒绝/对账上限/profiler 上限）。子项暂缓 3：版本号并发撞号（需 per-appId 锁或 DB 原子递增）、save 幽灵文档（onAfterCommit 或对账清理）、真 post-commit dispatch（与运行标记窗口联动设计）

Exit Criteria:

- [x] 8 份报告全部条目有处置标注（grep 对账：2026-08-28 核验 nop-biz 13/13、nop-graphql 15/15、nop-auth 15/15、nop-sys 13/13、nop-wf 15/15、nop-batch 20/20、nop-dyn 16/16、nop-metadata 13/13；全 26 份报告合计 447/447，另 2 条超条目处置节为合法附加）
- [x] 修复项对应模块测试绿（08-28 主会话全 reactor 独立复跑 23 模块验证：五单元全部 BUILD SUCCESS；期间暴露并修复 1 处跨模块回归——nop-sys 死代码删除误删 nop-batch-sys batch.xml XLang 调用的 claimNonBroadcastEvents/processClaimedNonBroadcastEvent，恢复后 nop-batch-sys 5/5 绿）
- [x] `ai-dev/logs/` 对应日期条目已更新（08-26 三单元回填记录 + 08-28 五单元处置与验证记录）

## Closure Gates

- [ ] 26 份报告全部 P0-P3 条目均有处置标注（grep `^### \[P[0123]\]` 总数 = 处置标注总数，无未标注条目）
- [ ] 13 条 P0 全部终态为已修复或复查非问题
- [ ] 所有"已修复"终态条目对应模块测试绿
- [ ] 无 in-scope 条目处于未裁定状态
- [ ] `./mvnw test`（或逐模块 `-pl` 等价覆盖全部修改模块）通过
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] 独立子 agent closure audit 完成并写入证据

## Deferred But Adjudicated

（执行中按报告逐条填充）

## Non-Blocking Follow-ups

（执行中填充：超出条目范围的新发现）

- **nop-kernel-cli 编译被禁用**（kernel-small 单元 2026-08-25 发现）：`nop-kernel/nop-kernel-cli/pom.xml` maven-compiler-plugin 配置 `-proc:only`（commit `7f8b955822`，作者本意 `-proc:full` 但当时 Windows JDK17 不识别）——该模块自身源码完全不生成 class（jar 内 10234 个 class 全来自依赖 shade），TestKernelCli 等测试从未被执行。Why Not Blocking Closure: 不属 check2 任何条目范围（plan346 Non-Goals 禁止超条目修复）；启用完整编译需独立验证模块源码可编译+测试可跑（可能暴露长期未编译的存量错误），应另立小 plan 或随下次 kernel 构建治理处理。
- **DefaultDirectedGraph.removeVertex 自环边 NPE**（nop-core 单元 2026-08-25 发现，未修）：`vertexMap.remove(v)` 先于 `_removeEdges`，自环边 source 查 map 得 null 解引用；removeMinorityVertices 路径不受影响。Successor：可在下次 nop-core 图工具维护时一并修（一行序调整+自环用例）。
- **JsonMerger.mergeList 多分支返回 listB 引用**（nop-core 单元 2026-08-25 发现，未修）：同族但无现实污染链（JsonExtender 对 list 无继续修改路径）。watch-only。
- **FutureHelper.waitAll 对 rejected ResolvedPromise 不透传异常**（nop-orm 单元 2026-08-25 发现，根因在 nop-api-core）：thenRun 包装吞掉 rejected 信号，nop-orm flushAsync 失败路径已用双源判定（errorRef）缓解，根因未动。Successor：nop-api-core FutureHelper 维护时统一修（需复核全部 waitAll 消费方语义）。
- **JdbcEntityPersistDriver.buildUpdateSql 的 lastUpdateSql 单槽缓存跨方言（shard）污染**（nop-orm 单元 2026-08-25 发现，未修）：本次新增的 load/lock/batchLoad 缓存已做方言守卫；lastUpdateSql 本身未改以控制 diff。watch-only。
- **SysCompactExtFieldHelper 多节点失效**（nop-sys 单元 2026-08-28）：afterEntityChange 只覆盖本节点，多节点部署需比照 SysCodeRuleGenerator 补 TTL/定时刷新兜底。Why Not Blocking Closure: 单节点行为已正确；多节点失效窗口属部署形态增强，非 live 功能缺陷。
- **audit-save vs audit 标签语义**（nop-sys 单元暂缓项）：需产品确认 "audit" 是否涵盖新增；若确认，一行改动 + 文档 + nop-credential 行为影响评估。
- **nop-wf 系统旁路信任通道 + 步骤管理鉴权钩子**（nop-wf P2-11/P2-8 增强）：需区分 owner 自转办/manager/admin/系统四类合法操作者，建议合并设计评审（check1 commit 075561f891 已刻意选 warn-only）。Why Not Blocking Closure: 现状 warn-only 为有意裁定，非缺陷。
- **nop-wf 多实例 join 语义**（P2-12 暂缓项）：actor 维度 × joinGroupExpr 维度组合规则需定义，或从签名移除 actor 参数；testCosign 断言单实例语义。
- **nop-metadata 版本号并发撞号 / save 幽灵文档 / 真 post-commit dispatch**（三个子项暂缓，决策点已记报告标注）：分别需 per-appId 锁或 DB 原子递增、onAfterCommit 写入或对账清理、与 R4.3 运行标记窗口联动设计。
- **nop-metadata deleteMeasureParseEdges 性能**（2026-08-28 超条目发现）：load+deleteEntity 循环，大集合性能差，建议改 deleteByQuery（与本次新 helper 同型）。optimization candidate。
- **nop_batch_task 唯一键**（plan344 遗留 + check2/nop-batch 复核维持）：需 ORM 模型变更，plan-first 保护区。
- **nop-batch forTag 校验为构建期行为变更**：依赖旧宽松行为的存量 .xbatch 配置将在任务加载期报错（审计建议方向）；发布说明需提示。
- **JDK 26 下 plexus-compiler in-process javac 对特定编译错误以 CompilerException: ConcurrentModificationException 崩溃**（nop-batch 单元 2026-08-28 踩坑）：替代正常诊断、误导排查，建议记 lessons 并在工具链升级时关注。
- **NopDynModule.xbiz publish/unpublish 的 APP_STATUS_* 同族常量**（nop-dyn 单元 2026-08-28）：与已修的 generateByAI 同族误用，值相同无行为差异。watch-only。
- **InMemoryResourceStore 的 merge 别名语义**（nop-dyn 单元 2026-08-28 发现，根在 nop-core）：merge 后两 store 共享子树是隐式契约，建议 nop-core 侧文档化或加防御注释，避免未来改拷贝式 merge 时静默破坏懒生成可见性。watch-only。
- **审计/处置方法论教训：Java 向 grep 需覆盖 XLang DSL 资源**（2026-08-28 nop-sys 跨模块回归暴露）：claimNonBroadcastEvents 被两轮独立"全仓库 grep 零调用"判定为死代码，实际被 nop-batch-sys 的 non-broadcast-consumer.batch.xml 以 XLang 表达式调用；判定"无调用方"必须同时 grep .batch.xml/.xbiz/.xlib 等 DSL 资源中的 `obj.method(` 形态。已记入 nop-sys 报告标注与 08-28 日志。

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）
