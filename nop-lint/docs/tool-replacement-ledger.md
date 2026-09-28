# nop-lint 工具替代统一账本（tool replacement ledger）

> Status: active 账本（防腐门禁在档；逐工具终裁行与分面标注待各 wave item 回填）
> Created: 2026-09-28
> 防腐门禁: `node ai-dev/tools/check-lint-tool-replacement-ledger.mjs`（主检查）/ 同命令 `self-test` 子命令（正控）；exit 0 = 账本结构与词表合法
> 书写纪律: 本文件所有表格单元格内禁止 `|` 与 `\|` 字符（分隔一律用 `/` 或 `；`）——checker 列数校验会响亮拒绝违例
> 状态分工：**工具级终裁执行期状态以 [roadmap](../../ai-dev/backlog/nop-lint-tool-replacement-roadmap.md) 为准**（含 wave 进度，本账本不双写）；本文件是行级账本索引 + 终裁汇总出口（roadmap item 20）+ out-of-scope 记录承载（roadmap item 19）。

## 定位

回答"nop-lint 能否替代 Java 社区静态检查工具 X"的唯一汇总出口。**裁定口径 = 各工具的核心缺陷发现面**（正确性/资源/并发/安全/数据流/平台不变式）；风格/可选面按 out-of-purpose 归档，字节码级检测按 out-of-principle（纯源码原则）归档——两轴定义见 roadmap Purpose。判定纪律见 roadmap Hard constraints（裁定必须证据化：对照记录 / 机制面证据 / 重估触发条件，禁止无对照的替代宣称）。

## 工具级终裁表（由 roadmap item 20 回填收敛，现全部待裁）

> 回填机制（门禁强制）：终裁 ≠ `待裁` 时，证据列必须含至少一个仓内文档锚链接（对照记录 / 机制面证据所在），残余范围必须非 `—`（`out-of-scope` 行写划出面清单或 `全工具`）——无证据终裁不可入库。

| 工具 | 终裁 | 残余范围 | 证据 | roadmap items |
|---|---|---|---|---|
| Checkstyle 10.21.1 | 待裁 | — | — | 3 |
| PMD 7.26.0 | 待裁 | — | — | 4 |
| check-\*.mjs ×24 | 待裁 | — | — | 5 |
| SpotBugs 4.9.8.3 | 待裁 | — | — | 9–11 |
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
| Checkstyle 10.21.1 | 待裁 | — | — | 待裁 |
| PMD 7.26.0 | 待裁 | — | — | 待裁 |
| check-\*.mjs ×24 | 待裁 | — | — | 待裁 |
| SpotBugs 4.9.8.3 | 待裁 | — | — | 待裁 |
| SonarQube | 待裁 | — | — | 待裁 |
| ErrorProne（未接线） | 待裁 | — | — | 待裁 |
| NullAway / 空类型系统族 | 待裁 | — | — | 待裁 |
| ArchUnit | 待裁 | — | — | 待裁 |

### 规则级分面表（62 条复审 + item 3a 新增 4 条 = live 62 行 + remove 4 行，roadmap item 2/3a，plan nop-lint/16、17）

> 分面：core = 核心缺陷发现面（正确性/资源/并发/安全/数据流/平台不变式）；out-of-purpose = 风格/可选面。处置词表与裁定约束见 design 02 §5（keep / demote-info / remove；landed 行禁 remove；core 行必 keep）。remove 行在处置落地后**保留在本表**（证据链：理由 + 重估触发，Hard constraint 3），防腐门禁按"live 规则恰一行 + remove 行不 live"双向看守。裁定汇总：复审 core 46 / out-of-purpose 16（demote-info 8 / remove 4 / keep 4）；item 3a 新增 4 行全 core/keep（string-literal-equality、no-native-method、no-raw-throws、covariant-equals）。

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
| quality/string-literal-equality | core | keep | 字符串 ==/!= 引用比较 bug（正确性面，item 3a 新增） | — |
| quality/no-native-method | core | keep | native 禁令=纯 Java 平台不变式（item 3a 新增） | — |
| exception/no-raw-throws | core | keep | Nop 异常契约声明面（item 3a 新增） | — |
| quality/covariant-equals | core | keep | 协变 equals 破坏等价契约（正确性面，item 3a 新增） | — |

## 行级账本索引（三本既有账，状态计数以各自门禁为准）

| 账本 | 行数 | 状态分布 | 防腐门禁 |
|---|---|---|---|
| [checkstyle-pmd 迁移映射](./checkstyle-pmd-migration.md) | 26（checkstyle 17 + pmd 9） | item 3a 后：landed 11 / out-of-purpose 8 / keep-pmd 7——checkstyle 侧可移除待 plan 18 切换；pmd 侧 keep 为主 | `ai-dev/tools/check-lint-tool-migration-mapping.mjs` |
| [check-\*.mjs 迁移 manifest](../../ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md) | 24 | maintain-mjs 7 / exclude 7 / migrated-pending-switchover 5 / candidate 2 / deferred 3（与文档分类汇总行一致，2026-09-28 修正） | `ai-dev/tools/check-lint-migration-manifest.mjs` |
| [PMD/EP coverage manifest](../../nop-lint/nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml) | 186（去重后） | tier 1 = 30 已落地带 live fixture（item 2 分面复审翻转 out-of-purpose 3 行后）；tier 2/3 = 机制面前瞻；out-of-purpose 3 = 已移出规则锚 | `ai-dev/tools/check-lint-coverage-manifest.mjs` |

## out-of-scope 记录（由 roadmap item 19 落稿；先例已裁：PMD CPD → design 06 §7.2，backlog token-shingling 分析器）
