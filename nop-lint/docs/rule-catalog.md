# nop-lint 规则目录（rule catalog）

> 由 `ai-dev/tools/gen-lint-rule-catalog.mjs` 从规则 metadata 确定性生成——手改无效（`--check` 门禁强制再生成）。规则语义/severity/id 变更必须升 version 并在规则头注记录（版本策略见 design 01 §2）。


## antipattern

| id | severity | version | autoFixable | message | source |
|---|---|---|---|---|---|
| antipattern/catch-npe | warning | 1.0 | false | 禁止捕获 NullPointerException，请做空值防御 (Do not catch NullPointerException; guard against the null instead) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #9 |
| antipattern/double-brace-init | warning | 1.0 | false | 禁止双括号初始化，匿名子类持有外部实例引用 (Double-brace initialization creates a leak-prone anonymous subclass; build the collection plainly) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #3 (design 06 §4.1 DoubleBraceInitialization) |
| antipattern/empty-if-block | warning | 1.0 | false | if 空语句块：删除或补齐条件处理 (Empty if block; remove it or handle the condition) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #6 |
| antipattern/empty-sync-block | warning | 1.0 | false | synchronized 空语句块：持锁却什么都不做 (Empty synchronized block; it acquires the monitor and protects nothing) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #8 |
| antipattern/negated-equals | warning | 1.0 | false | 使用 !(a == b) 取反等价判断，请直接写 a != b (Negated equality; write a != b instead) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #23 |
| antipattern/new-primitive-boxing | warning | 1.0 | false | 禁止显式装箱构造 new Integer/Long/...，请使用 valueOf 或自动装箱 (Do not box primitives with deprecated constructors; use valueOf or autoboxing) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #7 |
| antipattern/print-stack-trace | warning | 1.0 | false | 禁止 printStackTrace，请将异常交给日志门面 (Do not call printStackTrace; pass the throwable to the logging facade) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #5 (design 06 §4.1 AvoidPrintStackTrace) |
| antipattern/system-exit | warning | 1.0 | false | 禁止 System.exit 直接退出 JVM (Do not call System.exit; return/throw and let the entry point manage the process lifecycle) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #4 |
| antipattern/throw-in-finally | warning | 1.0 | false | 禁止在 finally 块中抛出异常，会吞掉原始异常 (Do not throw inside finally; it swallows the in-flight exception) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #10 |

## api

| id | severity | version | autoFixable | message | source |
|---|---|---|---|---|---|
| api/no-nonslf4j-logger-call | warning | 1.0 | false | 疑似非日志门面调用 (log-family call on a non-logger receiver; go through the logging facade) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #17 |
| api/no-proxy-hostile-method | warning | 1.0 | false | public 方法不可被覆写，代理无法拦截 (public proxy-hostile method: a proxy cannot intercept what cannot be overridden) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #16 |
| nop/bizmodel-dao-access | error | 1.0 | false | BizModel must not call dao() directly; use the CrudBizModel unified flow | ai-dev/design/nop-lint/00-overview.md + docs-for-ai service-layer anti-pattern table [metadata.category=api-contract] |
| nop/bizmodel-safe-api | error | 1.0 | false | dao() data-plane calls bypass the CrudBizModel safe APIs | docs-for-ai service-layer anti-pattern table [metadata.category=api-contract] |
| nop/ibiz-missing-annotation | error | 1.0 | false | I*Biz interface methods must declare @BizQuery/@BizMutation/@BizAction | ai-dev/tools/check-ibiz-interfaces.mjs [metadata.category=api-contract] |
| nop/ibiz-missing-context | error | 1.0 | false | I*Biz interface methods must accept IServiceContext as the last parameter | ai-dev/tools/check-ibiz-interfaces.mjs [metadata.category=api-contract] |

## exception

| id | severity | version | autoFixable | message | source |
|---|---|---|---|---|---|
| exception/empty-finally-block | warning | 1.0 | false | finally 空语句块：删除或补齐清理逻辑 (empty finally block; remove it or add the cleanup) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #13 (PMD:EmptyFinallyBlock) |
| exception/equals-null | warning | 1.0 | false | equals(null) 恒为 false，应为 == null 判空 (equals(null) is always false; use == null) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #3 (PMD:EqualsNull + EP:EqualsNull) |
| exception/no-catch-throwable | warning | 1.0 | false | 禁止捕获 Throwable，会吞掉 Error (Do not catch Throwable; Errors must propagate — catch the specific exception) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #22 [metadata.category=exception-handling] |
| exception/no-throw-npe | warning | 1.0 | false | 禁止显式抛出 NullPointerException (do not throw NullPointerException deliberately) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #12 (PMD:AvoidThrowingNullPointerException) |
| exception/throw-null | warning | 1.0 | false | 禁止 throw null，会以 NPE 收场 (throw null immediately fails with a NullPointerException) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #7 (EP:ThrowNull) |
| nop/no-empty-catch | warning | 1.0 | false | Empty catch block swallows the exception without handling; pass it to a logger, rethrow, or add a comment explaining why it is intentionally ignored | ai-dev/tools/rules/java-lint-empty-catch.yml [metadata.category=exception-handling] |
| nop/no-log-getmessage | warning | 1.0 | false | Only e.getMessage() is used, full stack trace is lost. Pass the exception object to the logger (e.g. LOG.error(msg, e)), or rethrow a wrapped exception | ai-dev/tools/rules/java-lint-getmessage-only.yml [metadata.category=exception-handling] |
| nop/no-raw-exception | warning | 1.0 | false | Use NopException or a module-level exception class (e.g. NopAiException, StreamException) instead of RuntimeException/Exception | ai-dev/tools/rules/java-lint-bare-runtimeexception.yml [metadata.category=exception-handling] |
| nop/silent-swallow | error | 1.0 | false | Catch block silently swallows the exception; rethrow it or propagate an ErrorCode-bearing exception (none of: throw / NopMetadataException / .errorCode( / ErrorCode. / Errors. / BizException / Biz.fatal() | ai-dev/tools/check-silent-swallow.mjs [metadata.category=exception-handling] |

## nop

| id | severity | version | autoFixable | message | source |
|---|---|---|---|---|---|
| nop-orm-mandatory-default | warning | 1.0 | false | ORM mandatory column should declare a defaultValue (mandatory 列应有 defaultValue) | ai-dev/design/nop-lint/01-pattern-dsl.md §3.5 |
| nop-orm-unique-key | error | 1.0 | false | ORM unique-key 缺少 constraint 或 columns 属性（DDL 将静默跳过唯一约束）(ORM unique-key is missing a non-empty constraint or columns attribute; the DDL silently skips the unique constraint) | ai-dev/tools/check-orm-unique-key-constraint.mjs (INV-UK) |
| nop-xbiz-auth-not-sole-guard | warning | 1.0 | false | xbiz action auth declaration is not assertable under test and must not be the sole guard | ai-dev/design/nop-lint/01-pattern-dsl.md §3.5 |
| nop/no-direct-datasource-inject | warning | 1.0 | false | 禁止直接注入 DataSource，请通过 DAO/服务门面访问数据 (Do not inject DataSource directly; access data through a DAO/service facade) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #2 (manifest tier2, API 契约扩展) |
| nop/no-vfs-violation | warning | 1.0 | false | Direct file IO bypasses VFS; use IResource / VFS APIs (readText/getStdPath) instead of java.io/java.nio file access | ai-dev/tools/check-vfs-violations.mjs [metadata.category=vfs-usage] |
| nop/query-limit-required | warning | 1.0 | false | 查询未设置结果上限，请先 setLimit/setMaxResults (query without a result cap; call setLimit/setMaxResults on the query first) | ai-dev/tools/check-nop-code-invariants.mjs (INV-04, manifest 12 row #16) |

## quality

| id | severity | version | autoFixable | message | source |
|---|---|---|---|---|---|
| quality/biginteger-instantiation | warning | 1.0 | false | 用 BigInteger.ZERO/ONE/TEN 或 valueOf (use the cached constant or valueOf) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #11 (PMD:BigIntegerInstantiation) |
| quality/collection-size-nonnegative | warning | 1.0 | false | size() >= 0 恒为 true (collection size is never negative) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #6 (EP:SizeGreaterThanOrEqualsZero) |
| quality/control-statement-braces | warning | 1.0 | false | if 语句体必须使用大括号 (if statement body must be wrapped in braces) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #30 (design 06 §4.1 ControlStatementBraces) |
| quality/empty-while-body | warning | 1.0 | false | while 空循环体：忙等或死代码 (Empty while body; busy-wait or dead code) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #28 |
| quality/for-loop-can-be-foreach | warning | 1.0 | false | 此 for 循环可改写为 foreach (indexed loop over .size() can be a for-each) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #29 替换 (design 06 §4.1 ForLoopCanBeForeach) |
| quality/loose-coupling-hashset | warning | 1.0 | false | 声明类型应使用 Set 接口而非具体实现 HashSet (Loose coupling: declare the Set interface, not the HashSet implementation) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #25 (design 06 §4.1 LooseCoupling) |
| quality/method-cognitive-complexity | warning | 1.0 | false | 方法认知复杂度过高 (method cognitive complexity exceeds 15) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #12 (design 06 §4.1 CognitiveComplexity) |
| quality/method-cyclomatic-complexity | warning | 1.0 | false | 方法圈复杂度过高 (method cyclomatic complexity exceeds 10) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #11 (design 06 §4.1 CyclomaticComplexity) |
| quality/method-npath-complexity | warning | 1.0 | false | 方法 NPath 路径复杂度过高 (method NPath complexity exceeds 200) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #13 (design 06 §4.1 NPathComplexity) |
| quality/no-constant-condition | warning | 1.0 | false | 恒定条件：分支不依赖运行时状态 (constant condition; the branch never varies) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #1 (ESLint no-constant-condition) |
| quality/no-finalize | warning | 1.0 | false | 禁止覆写 finalize (Do not override finalize; deprecated, unreliable and a resurrection hazard) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #24 (design 06 §4.1, JEP 421) |
| quality/no-return-null | warning | 1.0 | false | 禁止裸 return null（Bare `return null` returned; return an Optional/empty value or document the nullability) | ai-dev/design/nop-lint/02-rule-library.md §1 |
| quality/no-self-compare | warning | 1.0 | false | 自比较：表达式与自身比较恒为常量 (self-compare; the operands are identical) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #2 (ESLint no-self-compare) |
| quality/no-star-import | info | 1.0 | false | 禁止星号导入 (Wildcard import; import the concrete types) | ai-dev/design/nop-lint/02-rule-library.md §1 |
| quality/no-system-out | warning | 1.0 | false | 禁止直接使用 System.out/System.err 输出，请使用日志门面 (Do not write to System.out/System.err; use the logging facade) | ai-dev/design/nop-lint/02-rule-library.md §1 |
| quality/no-transactional-annotation | error | 1.0 | false | 业务代码禁止使用 Spring @Transactional（Nop 使用平台事务面）(Spring @Transactional is banned in Nop business code; use the platform transaction surface) | ai-dev/design/nop-lint/02-rule-library.md §1 |
| quality/random-mod | warning | 1.0 | false | nextInt() % n 分布有偏，请用 nextInt(n) (modulo of nextInt() is biased; use nextInt(n)) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #8 (EP:RandomModInteger) |
| quality/replace-hashtable | warning | 1.0 | false | 用 Map/ConcurrentHashMap 替代遗留 Hashtable (Replace legacy Hashtable with Map/ConcurrentHashMap) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #26 (design 06 §4.1 ReplaceHashtableWithMap) |
| quality/replace-vector | warning | 1.0 | false | 用 List/ArrayList 替代遗留 Vector (Replace legacy Vector with List/ArrayList) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #27 (design 06 §4.1 ReplaceVectorWithList) |
| quality/self-assigned-local | warning | 1.0 | false | 变量自赋值无效 (self-assignment has no effect) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #15 |
| quality/self-comparison | warning | 1.0 | false | compareTo 自比较恒为 0 (self-comparison; compareTo on the same expression) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #4 (EP:SelfComparison) |
| quality/self-equals | warning | 1.0 | false | equals 自比较恒为 true (self-equals; equals on the same expression) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #5 (EP:SelfEquals) |
| quality/simplify-boolean-expression | warning | 1.0 | false | 布尔比较冗余：直接写 flag / !flag (redundant boolean comparison) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #14 (PMD:SimplifyBooleanExpressions) |
| quality/string-instantiation | warning | 1.0 | false | 多余的 String 拷贝 (redundant String instantiation) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #10 (PMD:StringInstantiation) |
| quality/unused-local-variable | warning | 1.0 | false | 未使用的局部变量 (unused local variable) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #14 |
| quality/use-collection-isempty | warning | 1.0 | false | 用 isEmpty() 判空集合 (use isEmpty() instead of size() == 0) | ai-dev/plans/nop-lint/2026-09-24-2300-1-pmd-ep-p0p1-batch.md enum #9 (PMD:UseCollectionIsEmpty) |

## security

| id | severity | version | autoFixable | message | source |
|---|---|---|---|---|---|
| security/no-class-forname | warning | 1.0 | false | 禁止 Class.forName 反射加载 (Do not load classes reflectively with Class.forName) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #19 (manifest 12 迁移吸收) |
| security/no-des-encryption | warning | 1.0 | false | 禁止 DES/DESede 加密算法 (DES is broken; use AES with modern key sizes) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #21 (manifest 12 迁移吸收) |
| security/no-hardcoded-crypto | warning | 1.0 | false | 硬编码密钥：加密密钥不得使用字符串字面量 (Hardcoded crypto key; load the key from configuration or a keystore) | ai-dev/design/nop-lint/02-rule-library.md §1 (PMD HardCodedCryptoKey) |
| security/no-md5-digest | warning | 1.0 | false | 禁止 MD5 摘要算法 (MD5 is broken for security purposes; use SHA-256 or stronger) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #20 (manifest 12 迁移吸收) |
| security/no-runtime-exec | warning | 1.0 | false | 禁止 Runtime.exec 直接执行系统命令 (Do not spawn OS processes with Runtime.exec; route through an audited execution seam) | ai-dev/plans/nop-lint/2026-09-24-1400-1-rule-library-48.md enum #18 (manifest 12 迁移吸收) |
| security/no-sensitive-literal | warning | 1.0 | false | 敏感字面量泄漏：日志/参数中携带 JDBC URL 或内联 SQL (Sensitive literal leaked into a log/param call: raw JDBC URL or inline SQL) | ai-dev/tools/check-sensitive-literal-leak.mjs (INV-SENSITIVE) |
