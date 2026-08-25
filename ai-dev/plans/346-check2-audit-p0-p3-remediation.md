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

Status: in progress
Targets: 8 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] nop-core.md（P1×3 P2×6 P3×10，共 19 条）— 2026-08-25 完成：19 修复 + 附加任务复查非问题 1（FilterBeanToSQLTransformer contains/ilike 同源疑点：产出经 env.compileSql 进入 EQL 编译层，eql 单元 08-24 的 ILIKE 方言修复已覆盖，值全 .param() 绑定，%/_ 透传为平台惯例——写入报告「超条目处置」节）。P1×3 全修（XNode.removeAll 倒序遍历/NioFileWatchService watchKeyMap 改 ConcurrentHashMap+IOException 不再杀循环/BeanCopier._copyToCollection 先 clear）。P2×6 全修（ExecutionContextImpl 终态竞态锁内双检/DefaultVirtualFileSystem.refresh 先建后关/DefaultDirectedGraph 悬空边/JsonMerger.mergeMap 无入参副作用/CsvRecordInput·Output 构造泄漏 safeClose）。P3×10 全修（resolveModelLoader 冒号校验+新错误码×2/reinitialize 回写/containsCycle allowLoop/ResourceCacheEntry volatile×2/JsonTool.loadDeltaBean static/EvalScopeImpl locations 同步/ResourceHelper INFO→DEBUG/freeze(false) detach 错误类型/ZipResourceStore 泄漏/四处单例 volatile）。新增 4 测试类+6 既有扩展共 22 用例；stash 红 18 用例（其余竞态/签名/日志类免红注明）；nop-core 277 tests 绿（2 既有 skip）。超范围新发现记录（未修）：DefaultDirectedGraph.removeVertex 自环边 NPE、JsonMerger.mergeList 分支返 listB 引用同族。框架核心保护纪律：全部最小修复，无契约变更。
- [x] nop-api-core.md（P1×2 P2×9 P3×14，共 25 条）— 2026-08-25 完成：25 条全部修复。P1×2（QuerySourceBean/QueryFieldBean.cloneInstance 丢字段，含 TreeBean 深拷贝）。P2×9（disableExpireTime 代理生效/PointBean off-by-one×2/stringToLong 单字符单位 NPE/stringToNumber e·E 并存拒绝/IntRange·LongRange 空段错误码/removeHeader 同锁/PlaceholderConfigReference volatile/CrudApiPageIterator 无限拉取三态 eof/appendHeaders 敏感头脱敏——顺带修同族两缺陷：FALSE 分支丢末页数据+hasNext eof 判序，两迭代器仓内无调用方）。P3×14（whenComplete 补 LOG.error/TimeOut.isExpired/registerNamedConverter Guard/StaticBeanContainer.getBean 抛 unknown bean/FilterBeans 委托 varargs/replaceChild 判空/stringToMonthDay 数值校验/treeEquals Objects.equals/DictBean volatile×2/MultiCsvSet notNull 语义澄清/freeze cascade 透传/deepMerge 不污染 m1/encodeStringMap 尾分隔/setFieldNames 替换语义）。新增 8 测试类+11 既有扩展；stash 红 21 条（4 条竞态/日志/语义澄清免红注明）；nop-api-core 115 tests 绿。无新增 ErrorCode。
- [x] kernel-small.md（P1×1 P2×6 P3×6，共 13 条）— 2026-08-25 完成：12 修复 + 1 文档方案（RowNumberRecordInput unchecked cast 改 javadoc 类型约束说明）。P1: DataParameterBinders.FLOAT 改 getDouble/setDouble 对齐 DOUBLE 声明（红 CCE）。P2×6 全修（ReflectClass.remove 键统一 getSignature+删 mergeList 死代码/MarkdownSectionMerger summary/TableViewToMarkdownTableConverter padToColumns——报告建议的 setSize 截断语义不适用已注明/PomModelMerger child 依赖+modules 不继承/CodeBlock.append 复用 buf/getObjectConstructor LinkedHashMap 兜底）。P3 11 修（SingleColumnRow 抛 ERR_DATASET_IS_READONLY/JdkJavaCompiler -source 取 Runtime 版本+URI 错误码化/KernelCliValidateCommand printStackTrace→LOG/requireField 传参纠正）。新增 7 测试类+2 扩展；stash 红 11 处；dataset 15+codegen 23+markdown 80+record-mapping 51+javac 7 绿。**超范围新发现（未修，记 follow-up）**：nop-kernel-cli pom maven-compiler `-proc:only`（commit 7f8b955822 笔误）致模块源码完全不编译、10234 class 全来自依赖、测试从未执行。
- [x] nop-core-framework.md（P1×4 P2×10 P3×9，共 23 条）— 2026-08-25 完成：20 修复 + 1 复查非问题 + 1 暂缓 + 1 不修复。P1×4 全修（BeanParentResolver 环引用前置 RESOLVING+异常复位/ProducedBeanInstance 新增 proxyHandler 引用解 JDK 代理强转 CCE/ConfigExpressionProcessor 每个 ${} 独立 configVars/ConfigStarter.getProfiles 改 getName()——超审计一并修 nop.profile.parent ValueWithLocation 转换崩溃）。P2 修 9 + ServiceProxy equals/hashCode 维持 W4 Phase 1 保留裁定记复查非问题（dumpDisabled getMissingClass/set 用 LinkedHashSet/app-beans include OR 语义/Files.walk try-with-resources/ChangeSubscriptions 逐监听器隔离/unloadPlugin 同侧成功路径/KeySetHelper RFC7518 最小八位组/Log4j2 精确查找/prototype runXpl 独立 scope/ServiceProxy 解包 ITE）。P3 修 7 + 不修复 1（良性竞态现状允许）+ 暂缓 1（条件求值 5 轮上限的收敛语义需设计裁定）。新增 14 测试类+2 扩展+夹具；stash 红 18 处形态吻合（SOE/CCE/注入错值/profiles 空/ITE 等）；7 子模块 207 tests 绿。附带修复预存失败 TestAop.testDynamicGen（上游 nop-core AOP 模板行为变化致 nop-ioc 期望夹具漂移，非本单元 23 条）。审计建议勘误 1 处（DisabledEvalScope.newChildScope 不可用）。模块路径注记：nop-core-framework 单元的子模块实际为根下 nop-core-framework/{nop-ioc,nop-config,nop-plugin/*,nop-log/*,nop-security,nop-boot}。
- [x] nop-orm.md（P1×3 P2×8 P3×9，共 20 条）— 2026-08-25 完成：18 修复 + 1 复查非问题 + 1 暂缓。P1×3 全修（getIdText 复合主键遍历 pkColumns/existsDict 传去前缀 sqlName/游标分页按完整排序键生成 keyset 条件——支持 desc/null/is null，不支持场景抛新错误码 ERR_ORM_CURSOR_ORDER_BY_NOT_COLUMN·ERR_ORM_CURSOR_SORT_VALUE_NULL；顺带修 queryToFindPrevSql 空 filter 漏 where）。P2 修 6 + 复查非问题 1（MdxQuerySplitter——报告把 getRelation(name,false) 的 ignoreUnknown 语义读反，未找到实际抛 ERR_ORM_UNKNOWN_PROP）+ 暂缓 1（DaoResourceFileStore.detachFile 唯一引用时物理删除文件，两个统一方向均有数据后果需产品决策）。P3×9 全修（含 flushAsync 失败路径补发 onFailure——waitAll 对 rejected ResolvedPromise 不透传异常以双源判定缓解，根因在 nop-api-core 记 follow-up；load/lock/batchLoad SQL volatile 单槽缓存+方言守卫）。新增 10 测试类+2 扩展+测试夹具 app.orm.xml 增 revision 测试实体（非产品模型）；stash 红 13 处；nop-orm 197 tests 绿（基线约 178，6 skip 既有）。
- [x] orm-periph.md（P1×2 P2×3 P3×8，共 13 条）— 2026-08-25 完成：13 条全部修复。P1×2（RpcEntityPersistDriver.loadAsync 改传 ids 列表+响应按 List 解析按主键匹配——复核发现仅改参数名会 CCE 一并修；无数据补 markMissing 与 batchLoadAsync/Jdbc/Td 对齐）。P2×3（hasLazyColumn 恢复 false/addJoin 改 continue+ERR_PDM_REFERENCE_NO_JOIN_COLUMN/getCollectionModel pos<=0 返 null）。P3×8（Pdm 缺 Name/Code 错误码化/drainTo 攒批契约+maxElements 防御/OrmReferenceModel ormModel 死代码三处全删/TdSqlHelper binder 死参全链路删除+删除分支按 querySpace 解析方言/GeometryTypeHandler.fromLiteral 显式 UnsupportedOperationException/removeViewsNoPk tables+tablesByCode 同移+WARN/getColumnPropIds 声明序/toUpperCase Locale.ROOT——土耳其语 locale 红验证）。新增 2 测试类+6 扩展共 18 用例；stash 红 13 处；rpc 7+orm-model 9+pdm 9+orm-data 6+tdengine 13+geo 13 全绿。超范围未修记录：PdmModelParser 不解析 Model 根下顶层 c:Views（静默忽略，建议独立裁定）。
- [ ] nop-dao.md（P1×2 P2×6 P3×10，共 18 条）
- [ ] xlang-java-truffle.md（P1×2 P2×3 P3×5，共 10 条）

Exit Criteria:

- [ ] 同 Phase 1 前三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 业务层其余单元

Status: planned
Targets: 8 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [ ] nop-biz.md（P1×3 P2×5 P3×5，共 13 条）
- [ ] nop-graphql.md（P1×2 P2×5 P3×8，共 15 条）
- [ ] nop-auth.md（P1×2 P2×4 P3×9，共 15 条）
- [ ] nop-sys.md（P1×1 P2×4 P3×8，共 13 条）
- [ ] nop-wf.md（P1×4 P2×8 P3×3，共 15 条）
- [ ] nop-batch.md（P1×5 P2×6 P3×9，共 20 条；08-24 已修，预计大量复查已修复）
- [ ] nop-dyn.md（P1×4 P2×7 P3×5，共 16 条；08-24 已修，预计大量复查已修复）
- [ ] nop-metadata.md（P1×2 P2×6 P3×5，共 13 条）

Exit Criteria:

- [ ] 同 Phase 1 前三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

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

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）
