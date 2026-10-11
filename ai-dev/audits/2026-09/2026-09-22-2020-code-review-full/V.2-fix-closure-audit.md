# V.2 修复闭环独立回归审核报告（full-code-review 轮次 Wave 3 整体审核）

> 审核身份：独立 fresh-session V.2 closure auditor（本报告即交付物）
> 执行日期：2026-10-10
> 工作树基线：branch `audit/full-code-review`，HEAD `feb823a7b0`（V.1 全量回归提交），`git status` 干净
> 审计参考：`ai-dev/skills/audit-remediation-verification-prompt.md`（fix-status↔live 追溯 / zero-test / 凭证跨层三类检查）
> 审核范围：roadmap V.2「全部 P0（4 条）+ 关键 P1 抽样（6 项）回归」+ Anti-Hollow 抽查（3 项：调用链连通 + 无静默跳过）
> 方法：对每项做 (a) live 代码逐锚核读 (b) 目标测试在本工作树实跑重放 (c) `git log` 修复 commit 触及目标文件核验 (d) 入口→出口调用链追踪

## 判定总览

| # | 项目 | Finding | Plan | live 代码 | 测试重放 | git 溯源 | 结论 |
|---|---|---|---|---|---|---|---|
| A1 | eql NOT 语义族 | P0-2.2-001/002/003 | 2320 | PASS | 32/32 绿 | b2aa0255bd | **PASS** |
| A2 | SumAgg 三通道累加器 | P0-2.8-001 | 2327 | PASS | 19/19 绿 | 816a394d12 | **PASS** |
| B1 | orm 会话状态机 | P1-2.1-001/002 | 2368 | PASS | 3/3 绿 | 1dda8bf985 | **PASS** |
| B2 | 密钥/codec 安全面 | P1-2.4-001 / P1-check2-nosql-67 / P1-check-mcc-172 | 2373 | PASS | 24+33+2 绿 + 集群 8/8（docker） | f4652d0bcb | **PASS** |
| B3 | 权限公开面 | P1-2.6-005/006/007 | 2378 | PASS | 2/2 绿 | 3a6eeedcd1 | **PASS** |
| B4 | search fail-closed + getDoc | P1-4.4-S-02..06 | 2394 | PASS | lucene 22 + core 19 绿 | 4aa3e2e1f2 | **PASS** |
| B5 | DataLoader 调度生命周期 | P1-2.6-004 | 2376 | PASS | 2+5 绿 | 0278526349 | **PASS** |
| B6 | stream-sql join 三修复 | P1-2.8-001/002/003 | 2389 | PASS | 11+9+3 绿 | 78f46b4180 | **PASS** |
| C1 | SumAgg 调用链连通 | — | 2327 | PASS（链路完整） | — | — | **PASS** |
| C2 | auth 声明引擎消费链 | — | 2378 | PASS（链路完整） | — | — | **PASS** |
| C3 | requestDispatch 无 no-op | — | 2376 | PASS（链路完整） | — | — | **PASS** |

**最终判定：PASS（零 Blocker / 零 Major / 零整改项；2 条 Minor 级观察登记见 §问题清单，均不阻塞）**

---

## A. 全部 P0 修复核验（4 条）

### A1. P0-2.2-001/002/003（nop-orm-eql NOT 语义族，plan 2320）— PASS

**live 代码**（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionOperatorTransformer.java`）：

- **P0-2.2-001（NOT `_some` 反转）**：`shouldNegateExists`（:457-473）已重写为逐层 XOR 编码——`EXISTS-not(scope_i) = (o_i==ALL) XOR (o_{i-1}==ALL) XOR (absorbedNotParity 奇偶)`，javadoc 注明 truth table 权威口径；吸收 NOT 经 `countAbsorbedNots`（:471-487）→ `CollectionScope.absorbedNotParity`（CollectionScope.java:44/:276-280，仅根 scope 在 :308 设置）编码进 EXISTS-not；`unwrapNotChain`（:491-497）剥离 fresh clone 根部 NOT 链（:404/:565 两调用点）。
- **P0-2.2-002（嵌套 `_all` 量词翻转/丢失）**：XOR 规则逐层生效；`hasChild()` 守卫依赖已删除，叶子条件取反仅由本 scope 自身量词决定（`replaceLogicNode` :573-578，注释「∀P 编码为 NOT EXISTS(¬P)，与吸收的 NOT 无关」）。
- **P0-2.2-003（NOT+嵌套否定错位）**：`negateConditionForAll` 的 SqlNotExpr 分支（:674-678）`inner.setASTParent(null)` detach 在位（带 ast-node-not-allow-multiple-parent 崩溃根因注释），¬∀P≡∃(¬P) 吸收语义正确。
- git 溯源：`b2aa0255bd fix(orm-eql): 修复集合操作符否定编码簇（P0-2.2-001/002/003+P1-2.2-003）…（plan 2320，closure audit PASS）`。

**测试重放**：任务给定的 `TestNotSomeCondition` 等名义按 plan 2320 记录定位为 `TestCollectionOperatorTransformer`（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/`）内的测试方法而非独立类。实跑 `./mvnw test -pl nop-persistence/nop-orm-eql -am -Dtest='TestCollectionOperatorTransformer'`：**32/32 全绿（Failures 0 / Errors 0），EXIT=0**。覆盖任务点名全部四条 + 完整 NOT 矩阵：`testNotSomeCondition`(:279)、`testThreeLevelNestedOperators`(:189，期望已修订)、`testNotNestedSomeSome`(:328)、`testNotNestedSomeAll`(:338)，另有 `testNotSomeWithoutParentheses`/`testNotAllCondition`/`testDoubleNotSomeCondition`/`testNotNestedAllSome`/`testNestedAllSomeQuantifierOrder`（_all._some≠_some._all 反转判别）/`testNestedAllAllQuantifierOrder`/`testThreeLevelAllSomeSome`/`testThreeLevelSomeSomeAll`/`testNotSomeAndConditionMergedUnderNot` 共 13 条 plan 2320 回归。断言均为完整 SQL 等值（非「不崩溃」弱断言）。

### A2. P0-2.8-001（SumAgg 三通道精确累加器，plan 2327）— PASS

**live 代码**（`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamSqlAggregation.java:210-286`）：

- 累加器 6 槽：`{0L, 0.0, BigDecimal.ZERO, FALSE, FALSE, FALSE}`（integral/floating/decimal/sawFloating/sawDecimal/sawValue，:230）；`add`（:233-254）三通道分流——Double/Float→浮点槽+`acc[3]=TRUE`，BigDecimal/BigInteger→BigDecimal 槽（BigInteger 经 `new BigDecimal((BigInteger) v)` 无回绕）+`acc[4]=TRUE`，其余 `toNumber().longValue()` 整数槽；getResult（:257-270）阶梯 sawDecimal→BigDecimal（合成浮点+整数槽）/ else sawFloating→Double（+long）/ else Long / else null——**双旗标单调，0.0 类型塌缩消除**。
- merge（:277-288）三槽各自相加+双旗标 OR+sawValue OR——plan 2327 对抗审查 Blocker 修复（merge 路径同步）在位。
- dim10-10 连带：BigDecimal/BigInteger 不再走 `longValue()` 截断路径。
- git 溯源：`816a394d12 audit(full-code-review): 2.8 stream-sql 首审…+ P0-2.8-001 SumAgg 混型累加修复（plan 2327）`。

**测试重放**：`./mvnw test -pl nop-stream/nop-stream-sql -am -Dtest='TestStreamSqlAggregations'`：**19/19 全绿，EXIT=0**。plan 2327 七条回归钉全部在位且通过：`sumMixedIntegralAndFloatingKeepsIntegralPart`(:123)、`sumMixedOrdersAllAgree`(:132)、`sumFloatingCollapseEliminated`(:141)、`sumBigDecimalStreamIsExact`(:151)、`sumBigIntegerDoesNotRewind`(:160)、`sumLongPlusBigDecimalMixRuling`(:169，契约分叉②)、`mergeCombinesAccumulators`(:113)；断言值+类型双钉。

---

## B. 关键 P1 抽样回归（6 项）

### B1. R1.5（orm 会话状态机，plan 2368）— PASS

**live 代码**（`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java`）：

- **P1-2.1-001**：`detach`（:1378-1414）head `checkContext()+checkValid()`（:1380-1381）、级联先行（`detachProp` 递归在 `entity.orm_detach()` :1407 之前，注释说明顺序颠倒 NPE 面）、`cache.remove(entity)`（:1413）——与 evict（:304-308 同构含 `cache.remove` :307）完全对齐。
- **P1-2.1-002**：`internalAssemble` head 守卫（:374-377）`curState.isDeleting() || curState.isDeleted() → return`，裁定语义注释在位（「同事务先删后查合法，跳过装配不静默取消删除意图」）；PROXY 初始化分支（`!isManaged()` :379 起）不受影响。

**测试重放**：plan 2368 记录的新测试类实际名为 `TestSessionDetachAndDeletingAssembly`（`nop-persistence/nop-orm/src/test/java/io/nop/orm/session/`，任务给定的 `TestOrmSession*` 前缀无同名词类）。实跑：**3/3 全绿，EXIT=0**——`testDeletingEntityNotResetByQueryAssembly`（save→flush→delete 断言 DELETING→同会话 EQL select 后**仍 DELETING**→flush 后 jdbc 行数=0，修复前行残留/UPDATE 复活判别）、`testDetachRemovesFromSessionCacheAndFlush`（contains=false + post-detach 修改不落库行值断言 + 另一 dirty 实体正常落库）、`testDetachOnClosedSessionFailsLoud`（assertThrows）。判别断言真实（行数/行值/状态三重）。

### B2. R1.8（密钥/codec 安全面，plan 2373）— PASS

**live 代码**：

- **hash tag**：`LettuceRateLimiter.java:44-50` 两构造点 `tokensKey()="{"+key+"}:tokens"`、`timestampKey()="{"+key+"}:timestamp"`——tryAcquireAsync(:60) 与读侧均经 helper，Cluster 同槽。
- **PrefixTextCodec/peek**：`RedisMfaChallengeStore.java:98-135`——`peek` 在 `instanceof Number` 之外补 String 数值解析（`parseTimestampText` :133-137 `Long.parseLong(text.trim())`，非数字保持 null 语义 fail-closed），注释明记 codec 类型不对称根因。
- **keyId-only 泄漏点**：`DefaultCredentialKeyProvider.java:106`（R1.8 注记）+ `entryInvalid`（:191-198）——param 仅 `rejectReason` + 条件 `masterKeyId` + `<entry length=N>` 非材料定位符，entry 派生字符零输出；`VaultCredentialKeyProvider.java` migration 循环（:251-277）三 throw 点经 `migrationKeyInvalid`（:384-396）——冒号前 keyId 前缀或长度标记，**passphrase 永不输出**（注释在位）。7 点全覆盖。

**测试重放**（任务给的 `nop-credential-core` 模块不存在，实际按 plan 2373 记录定位为 credential-service + kms-vault + auth-service 三个测试落点）：

- `./mvnw test -pl nop-credential/nop-credential-service,nop-credential/nop-credential-kms-vault -am -Dtest='TestDefaultCredentialKeyProvider,TestVaultCredentialKeyProvider'`：**24/24 + 33/33 全绿，EXIT=0**。泄漏回归（TestDefaultCredentialKeyProvider:50-73）三形态断言 `getMessage()` 不含 passphrase 材料、含 keyId/reason——真实行为断言非空断言。
- `./mvnw test -pl nop-auth/nop-auth-service -am -Dtest='TestRedisStorePeekRealCodecRoundTrip'`：**2/2 全绿**——经真实 PrefixEncodeHelper 编解码往返（无桩绕过序列化，闭合审计指出的缺陷逃逸根因）。
- **docker 加腿**（本审核环境 docker 可用）：`TestLettuceClusterService`（testcontainers 真实 Redis Cluster，`-Dnop.test.docker.enabled=true`）：**8/8 全绿 0 跳过，EXIT=0**——含 rateLimiter `tryAcquire` 在真实 cluster 上执行（修复前 CROSSSLOT 必炸的端到端防线）。

### B3. R1.12（权限公开面，plan 2378）— PASS

**live 代码**：

- **xbiz auth 声明 16 项/7 文件全在位**：`approval-support.xbiz`（nop-wf-core，:7/:51/:83/:118/:153 五 mutation `roles="admin"`）、`NopWfDefinition.xbiz`（:11 publish/:14 unpublish，每动作单权限串）、`NopAuthUser.xbiz`（:7 `active_findPage` + `NopAuthUser:query|NopAuthUser:findPage`；`NopAuthUser_tenant.xbiz` 经 `x:extends="NopAuthUser.xbiz"` 继承）、`NopDynModule.xbiz`×3、`NopMetaDataContract.xbiz`×2（本地覆盖文件）、`NopMetaTagLabel.xbiz`×2（本地覆盖文件）、`NopRuleDefinition.xbiz`×1。
- **双配置键**：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/ApiConfigs.java:95-100`——`nop.auth.require-auth-checker` / `nop.auth.strict-action-auth`，均默认 false。
- **fail-fast 消费点**：`GraphQLEngine.java:383-384`（enableActionAuth && require && checker==null → 抛 `ERR_GRAPHQL_NO_ACTION_AUTH_CHECKER`，GraphQLErrors.java:431）；`GraphQLActionAuthChecker.isAllowAccess`（:118 起）——auth!=null && checker==null && require → false（fail-closed）、auth==null && strict → deny（:135-138），checkAuth 层三处 auth 解引用 null 安全（:100-116）。

**测试重放**：`./mvnw test -pl nop-metadata/nop-metadata-service -am -Dtest='TestApprovalActionAuthEnforcement'`：**2/2 全绿，EXIT=0**——真实引擎（`@NopTestConfig(enableActionAuth=TRUE)`）下非 admin 调 `NopMetaDataContract__approve` 被 `ERR_AUTH_NO_PERMISSION` 拒绝 + admin 放行（任务要求的「plan 2378 记录的 2/2 用例」）。合并终值断言侧：`TestUseApprovalE2E`（nop-wf-service :283-302）经 IBizObjectManager 取 merged definition 断言五动作 auth 终值非 null 且 roles 含 admin（非源 XML 断言）。

### B4. R1.28（nop-search fail-closed + getDoc 签名，plan 2394）— PASS

**live 代码**：

- **S-06 fail-closed**：`SearchEngineBizModel.java`（nop-search-core `io/nop/search/core/biz/`）`checkAllowAccess`（:108-114）——`IUserContext.get()==null || !isUserInRole(ADMIN)` → 抛 `nop.err.search.no-permission`（:99-102）；入口 `indexDir` :77 / :82 调用。
- **路径白名单**：`getLocalFile`（:120-167）——resourceLocator 豁免 + `CFG_SEARCH_INDEX_DIR_ROOTS`（:96-97 `nop.search.index-dir-roots`）：未配置时绝对路径拒绝、配置时 canonical path 须落根内（canonical 解析 IOException 亦拒绝）。
- **S-05 签名**：`ISearchEngine.java:27` `getDoc(@Name("topic") String topic, @Name("docId") String docId)`；`LuceneSearchEngine.getDoc:313` 单 topic 查找；`SearchEngineBizModel.getDoc:186-188` 传 topic。
- **S-02/S-03/S-04**：`buildQuery` 空 query→`MatchAllDocsQuery`（:871-873，注释说明 MUST 空子句恒空根因）；`rollback(topic, writer)`（:1153-1173）先移除 searcherManagers/indexWriters 条目再 `writer.rollback()`，两调用点（:855/:1112）传 topic。

**测试重放**：`./mvnw test -pl nop-search/nop-search-lucene,nop-search/nop-search-core,nop-search/nop-search-api -am`：**lucene 22（0 失败，1 skip=预存 `@Disabled indexAll` 批量工具方法）+ core 19（0 失败），EXIT=0**。判别与访问控制测试类在位且通过：`TestSearchFixesDiscriminative` 4/4（S-02/03/04/05 判别）、`TestSearchBizModelAccessControl` 7/7（S-06）、`TestLuceneFilterBeanTransformer` 9/9（S-03 NOT 锚点）、`TestSearchEngineBizModel` 7/7。

### B5. R1.10b（DataLoader 调度生命周期，plan 2376）— PASS

**live 代码**（`nop-service-framework/nop-graphql/nop-graphql-core`）：

- `IGraphQLExecutionContext.java:41` `void requestDispatch();` 抽象方法（无 default 空实现）。
- `GraphQLExecutionContext`：`dispatchScheduled` AtomicBoolean（:47）；`requestDispatch`（:162-173）CAS 去重 → `GlobalExecutors.getExecutor(NOP_GLOBAL_WORKER).execute(this::runDispatchCycle)`（:167）；提交失败清标志+`NopException.adapt` 重抛（:169-171，**无吞异常路径**）；`runDispatchCycle`（:175-184）finally 先清标志再查注册表非空续调（终止不变式注释在位）；`dispatchCycleInContext`（:186-194）经 `getContext().execute(...)` 请求 context 绑定执行 `dispatchAll()`。
- `BeanMethodBatchFetcher`：注册成功分支 `context.requestDispatch()`（:76）；`loader.load` 在 synchronized(context) 临界区内（:64-83，strand 竞态修复）。

**测试重放**：`./mvnw test -pl nop-service-framework/nop-graphql/nop-graphql-core -am -Dtest='TestGraphQLDataLoaderDispatchLifecycle,TestBeanMethodBatchFetcher'`：**2/2 + 5/5 全绿，EXIT=0**。判别用例（独立线程模拟异步 body 注册 loader 并 await，3s 超时断言）+ 批保护（burst 50 键全覆盖不变式）+ 既有 5 用例（含 NoAutoDispatchContext 记录型替身确定性化）全过。

### B6. R1.22（stream-sql join null 键/insertionSeq/水位前缘，plan 2389）— PASS

**live 代码**：

- `EquiJoinOperator.java`（nop-stream-core `operators/join/`）：`NULL_EQUI_KEY_BUFFER_PREFIX = "\u0001null\u0001"` 保留哨兵（:100）；`bufferKeyOf` null→哨兵（:426-434）；`emitMatches` null 早退（:309-317，SQL NULL≠NULL 语义注释，记录仍入缓冲走 OUTER 补齐/trim）；`completeUnmatched` 水位前缘过滤（:344 `tsKey[0] > currentWatermark` 跳过）；`pendingRestoredBuffer` stash（:113 声明，restoreState :202 暂存，open :151-155 合并+清引用——restore-before-open 生产序 NPE 同面消除）。
- `PerKeyOrderedBuffer.java:64-78`：`putAll` 遍历合并条目取最大 seq，`insertionSeq = max(insertionSeq, maxMergedSeq+1)`（:75-76），javadoc 声明 restore 后 seq 单调不变量。

**测试重放**（plan 2389 记录类名）：`./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-core -am -Dtest='TestEquiJoinHashSemantics,TestPerKeyOrderedBuffer,TestEquiJoinWithCheckpoint'`：**11/11 + 9/9 + 3/3 全绿，EXIT=0**（门内附带 TestEquiJoinWindowTimeout 5/5、TestEquiJoinParallelismInvariant 1/1 亦绿）。null 键族判别在位：`nullEquiKeysNeverMatchAnything`(:205)、`nullEquiKeysCompleteAsUnmatchedOnFull`(:221)。

---

## C. Anti-Hollow 抽查（3 项）

### C1. SumAgg 累加器被聚合管线真实调用 — PASS

入口→出口链逐环亲读：`StreamAggregatorFunctionResolver.resolve`（SPI `io.nop.stream.flow.spi.IAggregatorFunctionResolver` 实现，:54 经 `StreamSqlAggregations.buildComposite`、:56 经 `resolve(fnId)`）→ `StreamSqlAggregation` 工厂 `case "sum": return new SumAgg(argEvaluator)`（StreamSqlAggregation.java:101-105）→ `SqlRowCompositeFunction` 子聚合器真实调用 `subFns[i].createAccumulator()/add()/getResult()/merge()`（:52-103）。SPI 注册点：`nop-stream-sql/src/main/resources/_vfs/nop/stream/sql/beans/app-aggregator.beans.xml`（bean `nopStreamAggregatorFunctionResolver`，module marker `_vfs/nop/stream/sql/_module` 装载）。**链路无断点、无未调用实现**。

### C2. auth 声明被 GraphQL 引擎真实消费 — PASS

xbiz `<auth>` →（x:extends 合并）→ `BizModelToGraphQLDefinition.toOperationDefinition:80 field.setAuth(actionModel.getAuth())` 与 loader 路径 `toBuilder:121 field.setAuth(loaderModel.getAuth())` → `GraphQLFieldDefinition.auth`（:60）→ `isAllowAccess(context):135 → GraphQLActionAuthChecker.isAllowAccess(auth, context)` → 引擎入口 `GraphQLExecutor:72/:155 GraphQLActionAuthChecker.INSTANCE.check(context)`。B3 的 enforcement 测试经真实引擎 `executeRpcAsync` 断言拒绝/放行，证明终值链运行时生效——非仅元数据存在。

### C3. requestDispatch 被 fetcher 调用且周期真实触发（无 no-op 路径）— PASS

- 全仓 grep：`IGraphQLExecutionContext` 生产实现唯一（`GraphQLExecutionContext`；测试侧 `NoAutoDispatchContext` 为 plan 2376 声明的记录型替身，非生产静默 no-op）。
- 调用链：`BeanMethodBatchFetcher:76`（每次注册成功必调）→ `requestDispatch:162`（CAS 失败=在途周期承接，非丢弃）→ worker 池 `runDispatchCycle:175` → `getContext().execute:176` → `dispatchCycleInContext:186 → dispatchAll():197`（快照+清空+逐 loader dispatch）→ 终止不变式（finally 清标志→查注册表→非空续调 :179-183）。
- 失败路径响亮：worker 拒绝/提交异常 → 清标志 + `NopException.adapt` 重抛（:169-171）；周期内异常经 promise.whenComplete 记录（:189-192）非吞。B5 判别测试（stash fetcher 单文件即双红、恢复双绿）独立证明该链是活性承载路径而非摆设。

---

## 问题清单

**Blocker：无。Major：无。**

Minor / 观察级（均不阻塞，登记备查）：

1. **[Minor-观察] VaultCredentialKeyProvider `keys`（vault 路径映射）非法条目点仍 param 完整 entry**（`VaultCredentialKeyProvider.java:206/:213`，`ARG_KEY_MAPPING`）。裁定：**非泄漏非缺陷**——该循环的 entry 形态为 `keyId:vault-secret-path`（存储地址，非解密材料；材料由 :225-228 运行时 fetchMaterial 才入进程），不在 P1-check-mcc-172 审计点名的 7 个 passphrase 点范围内。留作未来加固观察（若 path 语义演化为携带内联材料需同步收敛），勿双立。
2. **[Minor-流程] 任务输入与 live 命名三处出入**（均有 plan 记录可追溯，核验不受阻）：①任务给的模块路径 `nop-orm/nop-orm-eql` 实为 `nop-persistence/nop-orm-eql`；②`TestNotSomeCondition` 等是 `TestCollectionOperatorTransformer` 内方法而非独立类；③`nop-credential-core` 模块不存在（实际 credential-service / kms-vault），`TestOrmSession*` 实际类名 `TestSessionDetachAndDeletingAssembly`。后续轮次的 V.x 任务书建议直接引用 plan 记录的名义+落点。
3. **[记录] 测试 skip 口径**：nop-search-lucene 1 skip = 预存 `@Disabled indexAll()`（批量索引工具方法，plan 2394 基线即含）；`TestLettuceClusterService` 8/8 零跳过（本环境 docker 可用，已按 docker-on 口径实跑）。

## 核验口径说明

- 测试重放均在本工作树（HEAD `feb823a7b0`）实跑，`-am` 反应式构建保证依赖取自源码而非陈旧 .m2 工件；逐项 EXIT=0。
- 测试计数与各 plan Closure 段声明的基线一致（eql 32 / stream-sql 19 / orm 3 / credential 24+33 / mfa-peek 2 / cluster 8 / enforcement 2 / lucene 22+core 19 / graphql 2+5 / join 11+9+3）。
- git 溯源：8 个修复文件的最近相关 commit 全部命中对应 plan 的修复提交（见判定总列表列），无「声明已修但 commit 未触及目标文件」的 OVERCLAIM。
- 本审核为只读审核 + 报告落盘，未修改任何产品代码/测试。
