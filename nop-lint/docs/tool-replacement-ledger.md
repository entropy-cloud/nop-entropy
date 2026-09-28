# nop-lint 工具替代统一账本（tool replacement ledger）

> Status: active 账本（防腐门禁在档；逐工具终裁行与分面标注待各 wave item 回填）
> Created: 2026-09-28
> 防腐门禁: `node ai-dev/tools/check-lint-tool-replacement-ledger.mjs`（主检查）/ 同命令 `self-test` 子命令（正控）；exit 0 = 账本结构与词表合法
> 书写纪律: 本文件所有表格单元格内禁止 `|` 与 `\|` 字符（分隔一律用 `/` 或 `；`）——checker 列数校验会响亮拒绝违例
> 状态分工：**工具级终裁执行期状态以 [roadmap](../../ai-dev/backlog/nop-lint-tool-replacement-roadmap.md) 为准**（含 wave 进度，本账本不双写）；本文件是行级账本索引 + 终裁汇总出口（roadmap item 20）+ out-of-scope 记录承载（roadmap item 19）。

## 定位

回答"nop-lint 能否替代 Java 社区静态检查工具 X"的唯一汇总出口。**裁定口径 = 各工具的核心缺陷发现面**（正确性/资源/并发/安全/数据流/平台不变式）；风格/可选面按 out-of-purpose 归档，字节码级检测按 out-of-principle（纯源码原则）归档——两轴定义见 roadmap Purpose。判定纪律见 roadmap Hard constraints（裁定必须证据化：对照记录 / 机制面证据 / 重估触发条件，禁止无对照的替代宣称）。

## 工具级终裁表（由 roadmap item 20 回填收敛；Checkstyle/PMD 已裁，其余待裁）

> 回填机制（门禁强制）：终裁 ≠ `待裁` 时，证据列必须含至少一个仓内文档锚链接（对照记录 / 机制面证据所在），残余范围必须非 `—`（`out-of-scope` 行写划出面清单或 `全工具`）——无证据终裁不可入库。

| 工具 | 终裁 | 残余范围 | 证据 | roadmap items |
|---|---|---|---|---|
| Checkstyle 10.21.1 | core-face-replaced | 风格/可选面 8 行 out-of-purpose（显式不迁移） | [行级账本 §2/§4.2](./checkstyle-pmd-migration.md) | 3 |
| PMD 7.26.0 | replaced-partial | ImplicitSwitchFallThrough deferred 面（机制缺口）+ report-only 接线保留 | [行级账本 §2/§4.3](./checkstyle-pmd-migration.md) | 4 |
| check-\*.mjs ×24 | 待裁 | — | — | 5 |
| SpotBugs 4.9.8.3 | keep-tool | 字节码专属面 out-of-principle（全 pattern 目录 HC1）；风格/边缘面 out-of-purpose（排除后实际触发极低）；1 处 RCN 命中不立项 | [实跑记录](./checkstyle-pmd-migration.md) §4.3 | 9–11 |
| SonarQube | 待裁 | — | — | 12–14 |
| ErrorProne（未接线） | 待裁 | — | — | 15–16 |
| NullAway / 空类型系统族 | 待裁 | — | — | 17 |
| ArchUnit | 待裁 | — | — | 18 |

终裁词表（门禁 enum-set，定义见 roadmap §工具级终裁词表）：

- `core-face-replaced`：该工具的**核心缺陷发现面**已由 nop-lint 承接（行级映射 + 对照通过 + 原接线点按判据处置）；风格/字节码残余按 out-of-purpose / out-of-principle 归档。
- `replaced-partial`：核心面部分承接；未承接部分的归因（机制缺口 vs 原则外）逐行记录。
- `keep-tool`：核心面大头超出 per-file 源码引擎问题域，工具整体保留。
- `out-of-scope`：非 lint 问题域（覆盖率/依赖 CVE/格式化/git 历史），归档不占用工作面。

## 分面裁定登记

> 分面词表（门禁 enum-set，定义见 roadmap Purpose 分面表）：`core`（核心缺陷发现面，职责内）/ `out-of-purpose`（风格/可选面，显式记录不做——这不是替代债，是范围声明）/ `out-of-principle`（字节码级检测，纯源码原则正式记录不追）/ `out-of-scope`（问题域外，账本归档）。分面标注列 = `/` 分隔的 token 串；roadmap item 2 语境下的 "optional" 风格分面落账本时归 `out-of-purpose` 轴。

| 工具 | 分面标注 | 依据 / 证据 | 重估触发 | 状态 |
|---|---|---|---|---|
| Checkstyle 10.21.1 | core / out-of-purpose | [行级账本 §2/§4.2](./checkstyle-pmd-migration.md)（core 9 行承接 + 风格 8 行归档，对照零 diff） | 风格面入 mandate 或对照被推翻 | core-face-replaced |
| PMD 7.26.0 | core / out-of-purpose | [行级账本 §2/§4.3](./checkstyle-pmd-migration.md)（core 7 行承接对照零 diff + 风格 1 行归档 + deferred 1 行机制缺口未入库） | ImplicitSwitchFallThrough 表达力具备时（机制缺口）；风格面入 mandate 或对照被推翻 | replaced-partial |
| check-\*.mjs ×24 | 待裁 | — | — | 待裁 |
| SpotBugs 4.9.8.3 | out-of-principle / out-of-purpose | 字节码级分析器（HC1 纯源码原则——核心面判定依赖 class-file 信息，超出 per-file 引擎问题域）；排除后实际触发极低 | 实跑记录 plan 24 | keep-tool |
| SonarQube | 待裁 | — | — | 待裁 |
| ErrorProne（未接线） | 待裁 | — | — | 待裁 |
| NullAway / 空类型系统族 | 待裁 | — | — | 待裁 |
| ArchUnit | 待裁 | — | — | 待裁 |

### 规则级分面表（62 条复审 + item 3a +4 + item 4a +5 + item 5 +2 = live 70 行 + remove 4 行，roadmap item 2/3a/4a/5，plan nop-lint/16、17、19、21）

> 分面：core = 核心缺陷发现面（正确性/资源/并发/安全/数据流/平台不变式）；out-of-purpose = 风格/可选面。处置词表与裁定约束见 design 02 §5（keep / demote-info / remove；landed 行禁 remove；core 行必 keep）。remove 行在处置落地后**保留在本表**（证据链：理由 + 重估触发，Hard constraint 3），防腐门禁按"live 规则恰一行 + remove 行不 live"双向看守。裁定汇总：复审 core 46 / out-of-purpose 16（demote-info 8 / remove 4 / keep 4）；item 3a 新增 4 行全 core/keep；item 4a 新增 5 行全 core/keep（no-branching-in-loop-body、no-clone-without-cloneable、clone-return-type-mismatch、proper-clone-implementation、no-hardcoded-iv）；item 5 新增 2 行全 core/keep（nop-bean-naming、nop-orm-icons，XNode）；item 7 新增 1 行 core/keep（closeable-not-closed）；implicit-switch-fall-through 因形状近似对照不可逐条裁定（PMD 6 vs 形状 219 extras）按 Deferred 裁定不入库（机制缺口：dataflow 判定 + falls-through 注释豁免），行 status = deferred。

| 规则 | 分面 | 处置 | 裁定理由 | 重估触发 |
|---|---|---|---|---|
| antipattern/catch-npe | core | keep | 捕获 NPE 掩盖空指针缺陷（正确性面） | — |
| antipattern/double-brace-init | core | keep | 匿名子类持外部引用，泄漏与序列化缺陷面 | — |
| antipattern/empty-if-block | out-of-purpose | demote-info | 空 if 块偏死代码/可读性，缺陷信号弱 | 定位重放宽或空块缺陷数据支持升档 |
| antipattern/empty-sync-block | core | keep | 持锁空块=并发保护失效（并发面） | — |
| antipattern/negated-equals | out-of-purpose | remove | 纯风格改写（取反等价），零缺陷信号 | 风格面入 mandate |
| antipattern/new-primitive-boxing | out-of-purpose | demote-info | API 卫生（编译器已告警 deprecated 构造器），非缺陷拦截 | 定位重放宽 |
| antipattern/print-stack-trace | core | keep | 绕过日志门面=平台不变式+异常吞没相邻面 | — |
| antipattern/system-exit | core | keep | JVM 生命周期平台不变式（服务端禁直退） | — |
| antipattern/throw-in-finally | core | keep | finally 抛异常吞在途异常（正确性面） | — |
| api/no-nonslf4j-logger-call | core | keep | 日志门面平台不变式 | — |
| api/no-proxy-hostile-method | core | keep | 代理契约平台不变式 | — |
| nop/bizmodel-dao-access | core | keep | BizModel 数据面契约（error） | — |
| nop/bizmodel-safe-api | core | keep | CrudBizModel 安全 API 契约（error） | — |
| nop/ibiz-missing-annotation | core | keep | I*Biz 注解契约（error） | — |
| nop/ibiz-missing-context | core | keep | IServiceContext 契约（error） | — |
| exception/empty-finally-block | core | keep | 清理逻辑缺失（资源/异常面） | — |
| exception/equals-null | core | keep | equals(null) 恒 false 判空笔误（正确性+autofix） | — |
| exception/no-catch-throwable | core | keep | 吞 Error（异常纪律面） | — |
| exception/no-throw-npe | core | keep | 显式抛 NPE（空指针面+autofix） | — |
| exception/throw-null | core | keep | throw null 必以 NPE 收场（空指针面） | — |
| nop/no-empty-catch | core | keep | 空 catch 吞异常（异常纪律面） | — |
| nop/no-log-getmessage | core | keep | 丢栈=错误处理契约面 | — |
| nop/no-raw-exception | core | keep | 异常类型纪律平台不变式 | — |
| nop/silent-swallow | core | keep | 静默吞异常 CI 硬门禁（error） | — |
| nop-orm-mandatory-default | core | keep | ORM mandatory 列缺省值（数据完整性） | — |
| nop-orm-unique-key | core | keep | 唯一约束静默丢失（error） | — |
| nop-xbiz-auth-not-sole-guard | core | keep | auth 非唯一守卫（安全面） | — |
| nop/no-direct-datasource-inject | core | keep | 数据访问门面平台不变式 | — |
| nop/no-vfs-violation | core | keep | VFS 平台不变式 | — |
| nop/query-limit-required | core | keep | 查询无上限（数据面不变式） | — |
| quality/biginteger-instantiation | out-of-purpose | demote-info | 缓存常量 API 卫生，非缺陷拦截 | 定位重放宽 |
| quality/collection-size-nonnegative | core | keep | size()<0 恒假=常量条件 bug（数据流面） | — |
| quality/control-statement-braces | out-of-purpose | demote-info | 花括号惯例；悬挂语句类低频拦截保留 info 档 | 缺陷数据显示有升档价值 |
| quality/empty-while-body | core | keep | 悬挂分号致死循环/漏体（正确性面，信号高于空 if） | — |
| quality/for-loop-can-be-foreach | out-of-purpose | remove | API 现代化建议，零缺陷信号 | 风格面入 mandate |
| quality/loose-coupling-hashset | out-of-purpose | remove | 面向接口声明纯风格 | 风格面入 mandate |
| quality/method-cognitive-complexity | out-of-purpose | keep | 可维护性度量非缺陷拦截；deep-only 已不在 standard 强制面（requires METRICS） | 度量面入 mandate 或 standard 化时重裁 |
| quality/method-cyclomatic-complexity | out-of-purpose | keep | 同上（deep-only；landed 行禁 remove） | 度量面入 mandate 或 standard 化时重裁 |
| quality/method-npath-complexity | out-of-purpose | keep | 同上（deep-only） | 度量面入 mandate 或 standard 化时重裁 |
| quality/no-constant-condition | core | keep | 常量条件=典型逻辑 bug（数据流面） | — |
| quality/no-finalize | core | keep | finalize 覆写破坏资源释放语义（资源面；landed 行禁 remove） | — |
| quality/no-return-null | core | keep | null 返回=NPE 源（空指针预防面） | — |
| quality/no-self-compare | core | keep | 恒真比较=typo/逻辑 bug（数据流面） | — |
| quality/no-star-import | out-of-purpose | keep | 纯风格；已 info 档；landed 行禁 remove | 风格面入 mandate（随工具终裁处置） |
| quality/no-system-out | core | keep | 日志门面平台不变式（landed 行） | — |
| quality/no-transactional-annotation | core | keep | Nop 无 Spring 事务注解（平台不变式，error） | — |
| quality/random-mod | core | keep | 取模偏差/负值（正确性+autofix） | — |
| quality/replace-hashtable | out-of-purpose | demote-info | 遗留 API 现代化；autofix 保留 AI 自纠价值 | 定位重放宽 |
| quality/replace-vector | out-of-purpose | demote-info | 同上（autofix 保留自纠价值） | 定位重放宽 |
| quality/self-assigned-local | core | keep | 自赋值=typo 信号（deep-only 数据流面） | — |
| quality/self-comparison | core | keep | compareTo 自比较=typo（正确性面） | — |
| quality/self-equals | core | keep | equals 自比较=typo（正确性面） | — |
| quality/simplify-boolean-expression | out-of-purpose | remove | 可读性改写，零缺陷信号 | 风格面入 mandate |
| quality/string-instantiation | out-of-purpose | demote-info | 冗余构造（perf/style），非缺陷拦截 | 定位重放宽 |
| quality/unused-local-variable | core | keep | 计算结果未用=逻辑缺环信号（deep-only 数据流面） | — |
| quality/use-collection-isempty | out-of-purpose | demote-info | 惯例改写；autofix 保留 AI 自纠价值 | 定位重放宽 |
| security/no-class-forname | core | keep | 反射加载注入面（安全） | — |
| security/no-des-encryption | core | keep | 弱加密（安全） | — |
| security/no-hardcoded-crypto | core | keep | 硬编码密钥（安全；landed 行） | — |
| security/no-md5-digest | core | keep | 弱摘要（安全） | — |
| security/no-runtime-exec | core | keep | 命令执行注入面（安全） | — |
| security/no-sensitive-literal | core | keep | 敏感字面量（安全） | — |
| quality/no-branching-in-loop-body | core | keep | 循环体直达分支=残余逻辑缺失信号（控制流面，item 4a 新增） | — |
| quality/no-clone-without-cloneable | core | keep | clone 契约破坏（正确性面，item 4a 新增；父链闭包 delta 在档） | 父链形态误报出现时重裁 |
| quality/clone-return-type-mismatch | core | keep | 协变拷贝契约破坏（正确性面，item 4a 新增） | — |
| quality/proper-clone-implementation | core | keep | clone 构造自身破坏拷贝语义（正确性面，item 4a 新增；type-resolution 语法代理 delta 在档） | — |
| security/no-hardcoded-iv | core | keep | 硬编码 IV（安全面，窄面 landed；污点面=机制缺口，item 4a 新增） | 污点面机制落地时（item 7 联动） |
| nop-bean-naming | core | keep | IoC bean 命名强约定（平台不变式，item 5 新增；REF 跨文件面维持 mjs） | — |
| nop-orm-icons | core | keep | 源模型图标完整性（平台不变式，item 5 新增） | — |
| quality/closeable-not-closed | core | keep | 资源泄漏（v1 保守面，item 7 新增） | L3 路径敏感 acquire/release 配对分析器 |
| quality/string-literal-equality | core | keep | 字符串 ==/!= 引用比较 bug（正确性面，item 3a 新增） | — |
| quality/no-native-method | core | keep | native 禁令=纯 Java 平台不变式（item 3a 新增） | — |
| exception/no-raw-throws | core | keep | Nop 异常契约声明面（item 3a 新增） | — |
| quality/covariant-equals | core | keep | 协变 equals 破坏等价契约（正确性面，item 3a 新增） | — |

## 核心缺陷类覆盖矩阵

> 核心缺陷类 × 现有规则的覆盖盘点（tool-replacement roadmap item 6，plan nop-lint/22）。规则↔缺陷类**多对多合法**；规则 id 为口径（与目录前缀存在已知差异）。优先级词表：P1 = roadmap 已排期机制裁定/落地（item 7/8）、P2 = SpotBugs/后续 wave 候选、P3 = watch。**刷新归属**：item 7/8/10 落新规则时，对应行由该 plan 同步更新（与 RULE_FACET_CENSUS 同责，防腐门禁看守钉名与 live 规则集一致性）。

| 缺陷类 | 现有规则 | manifest 机制 tier | 机制缺口 | 优先级 |
|---|---|---|---|---|
| 资源泄漏 | quality/no-finalize；exception/empty-finally-block；antipattern/double-brace-init；quality/closeable-not-closed | 无 acquire/release 对应行 | **acquire/release 路径配对零规则**：close() 调用面、try-with-resources 豁免判定、跨分支释放路径——v1 = pattern+scope 保守面已落地（closeable-not-closed，三豁免面）；L3 路径敏感 acquire/release 配对分析器 = Deferred（触发 = v1 误报数据不支持） | P1 |
| 空指针 | exception/equals-null；exception/no-throw-npe；exception/throw-null；antipattern/catch-npe；quality/no-return-null | null 相关 manifest 行均为 tier1 pattern 面（已落地）；null-flow 无任何 manifest 行（机制前瞻空缺） | **正式裁定（item 8，plan 24）**：①pattern 面 5 条已覆盖 NPE 语法子面（显式 throw null、equals(null) 判空笔误、catch NPE、return null）；②L3 null-flow（解引用前判空路径传播）= Deferred——DataFlowAnalyzer v1 flow-insensitive，path-sensitive 为 successor surface，与 item 7 Option A 同族共享触发；③NullAway 式全程序注解推导 = not-replaceable（超出 per-file 引擎问题域，Hard constraint 1 纯源码原则；重估 = 纯源码原则被推翻） | P1 |
| 吞异常 | nop/no-empty-catch；nop/silent-swallow；exception/no-catch-throwable；exception/empty-finally-block；antipattern/throw-in-finally | tier1（silent-swallow/empty-catch 面 landed） | 面已覆盖（CI 硬门禁）；例外：catch 内条件性吞（半吞）无规则 | P3 |
| 错误处理契约 | nop/no-raw-exception；nop/no-log-getmessage；antipattern/catch-npe；exception/no-raw-throws | tier1/2 | NopException ErrorCode 参数面由 mjs 门禁维持（design 12 裁定维持 mjs）；引擎面无缺口 | P3 |
| 并发 | antipattern/empty-sync-block | 无对应行 | 锁获取/释放配对、double-checked locking、wait/notify 面零规则——需类型面（Lock/Monitor）+ 路径分析，机制成本高 | P3 |
| 安全面 | security/no-class-forname；security/no-des-encryption；security/no-hardcoded-crypto；security/no-md5-digest；security/no-runtime-exec；security/no-sensitive-literal；security/no-hardcoded-iv；nop-xbiz-auth-not-sole-guard | tier1（HardCodedCryptoKey/InsecureCryptoIv 面 landed） | 加密算法强度判定（AES 模式/密钥长度）需类型+常量分析——tier2 机制前瞻 | P2 |
| 注入面 | security/no-runtime-exec；security/no-class-forname | tier2 | SQL/命令**动态拼接**注入需跨过程 taint 分析——roadmap item 14 裁定为 not-replaceable 候选（out-of-principle 待正式记录） | P2 |
| 数据流 bug | quality/self-assigned-local；quality/unused-local-variable；quality/no-constant-condition；quality/random-mod；quality/string-literal-equality；quality/collection-size-nonnegative；quality/no-self-compare；quality/self-comparison；quality/self-equals；exception/equals-null | tier1（dataflow 面 landed） | 方法内 def-use 已覆盖常见形态；跨方法数据流超出 per-file 引擎问题域（out-of-principle 候选） | P3 |
| 平台不变式 | antipattern/system-exit；antipattern/print-stack-trace；api/no-nonslf4j-logger-call；api/no-proxy-hostile-method；nop/no-raw-exception；quality/no-system-out；quality/no-transactional-annotation；quality/no-native-method；nop/no-direct-datasource-inject；nop/no-vfs-violation；nop/query-limit-required；nop-bean-naming；nop-orm-icons；nop-orm-mandatory-default；nop-orm-unique-key；nop-xbiz-auth-not-sole-guard；nop/bizmodel-dao-access；nop/bizmodel-safe-api；nop/ibiz-missing-annotation；nop/ibiz-missing-context | tier1（平台契约面 landed） | 面=平台契约演进驱动（新契约=新规则），无通用机制缺口 | P3 |
| 正确性/逻辑契约 | quality/no-clone-without-cloneable；quality/clone-return-type-mismatch；quality/proper-clone-implementation；quality/covariant-equals；quality/empty-while-body；quality/no-branching-in-loop-body；quality/no-finalize；antipattern/double-brace-init | 部分 tier1（item 3a/4a 面） | equals/hashCode 契约对、compareTo 一致性等相邻面无规则（SpotBugs 面候选，item 9 盘点） | P2 |

## 行级账本索引（三本既有账，状态计数以各自门禁为准）

| 账本 | 行数 | 状态分布 | 防腐门禁 |
|---|---|---|---|
| [checkstyle-pmd 迁移映射](./checkstyle-pmd-migration.md) | 26（checkstyle 17 + pmd 9） | item 3b/4a 后：checkstyle 侧已切换移除（landed 9 + out-of-purpose 8）；pmd 侧 landed 7 / out-of-purpose 1 / deferred 1（全表 landed 16）——pmd 配置段不可移除 | `ai-dev/tools/check-lint-tool-migration-mapping.mjs` |
| [check-\*.mjs 迁移 manifest](../../ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md) | 24 | maintain-mjs 7 / exclude 7 / migrated-pending-switchover 5 / candidate 2 / deferred 3（与文档分类汇总行一致，2026-09-28 修正） | `ai-dev/tools/check-lint-migration-manifest.mjs` |
| [PMD/EP coverage manifest](../../nop-lint/nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml) | 186（去重后） | tier 1 = 33 已落地带 live fixture（item 2 翻转 out-of-purpose 3 行、item 3a 提升 2 行、item 4a 提升 1 行）；tier 2/3 = 机制面前瞻；out-of-purpose 3 = 已移出规则锚 | `ai-dev/tools/check-lint-coverage-manifest.mjs` |

## out-of-scope 记录（由 roadmap item 19 落稿；先例已裁：PMD CPD → design 06 §7.2，backlog token-shingling 分析器）
