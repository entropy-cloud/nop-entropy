# nop-refactor — AI-First 代码修改工具链

> 定位：平台代码工具三件套"读（nop-code）/ 查（nop-lint）/ **改（nop-refactor）**"的"改"面。第一用户是 AI agent：GraphQL 结构化操作 + 机器可核验反馈载荷（self-verification），CLI 为同一引擎的批处理形态。
> 语义权威：本模块源码（RefactorResult/Verification record 与 RefactorOperation SPI 的 javadoc 即契约）。

## 模块拓扑

| 模块 | 职责 |
|---|---|
| `nop-refactor-core` | 语言无关操作框架：`RefactorOperation` SPI（check/plan）+ `RefactorOperationRunner`（apply/verify 框架单点——apply 经 nop-lint `EditPlanApplier`、verify 经 `RefactorVerifier.assemble`，各仅此一处调用）；`RewriteOperation`（codemod）/`RenameOperation`（rename 两档）；`SymbolResolverAdapter` 语言无关 symbol SPI；RefactorResult self-verification 载荷；CLI 批处理形态 |
| `nop-refactor-java` | Java 语言适配（SPI 第一实现）：内嵌声明索引 + 绑定过滤引用搜索（同包/import/限定名）+ rename 两档解析，消费 nop-java-parser 与 nop-lint-java ScopeAnalyzer 公开 API |
| `nop-refactor-graphql` | `Refactor__*` GraphQL 契约面（四 action）+ beans 装配（app-refactor.beans.xml：biz model + Java 适配器注入） |

依赖方向：java → core 单向（core 经 SPI 运行时注入适配，零 java 依赖）；graphql → core + java(runtime)。

## 操作清单 × verification 契约（能力目录）

| 操作 | 入口 | 输入 | 载荷 |
|---|---|---|---|
| 批量 pattern 改写（preview） | `Refactor__previewRewrite` / CLI `nop-refactor preview --rules <prefix> [--json] <files...>` | RewriteInput：rulesetPrefix + paths | RefactorResult（applied=false 不落盘） |
| 批量 pattern 改写（apply） | `Refactor__applyRewrite` / CLI `apply` | 同上 | RefactorResult（applied=true 原子落盘） |
| rename（preview） | `Refactor__previewRename` | RenameInput：paths（搜索域，java 文件集）+ fqn 或 path+byteOffset（恰一）+ newName | RefactorResult（跨文件改写计划 + symbolIntact） |
| rename（apply） | `Refactor__applyRename` | 同上 | RefactorResult（跨文件原子落盘） |

### RefactorResult 载荷（字段不缩水）

- `applied`：preview=false / apply=true（模式标志）。
- `edits`：per-file 结构化编辑（path + 字节区间 + 变更摘要）。
- `diff`：unified diff——AI 的主审读面。
- `verification`：
  - `parseOk`：每个被改文件重解析无 ERROR/missing 节点（AND 聚合）；重解析失败 fail-closed 抛错。
  - `errorNodeCount`：跨文件求和。
  - `residualDiagnostics`：配置规则子集的残留诊断数；`stats.residualRuleCount=0` 表示未配子集（与"已配零残留"机器可区分）。
  - `symbolIntact`：**非 null ⟺ rename RESOLVED 且无回滚**（改写前后目标符号引用计数对称断言，plan 期预演计算）；rewrite 面与 refusal/rollback 路径恒 null。
- `stats`：filesAffected / editsApplied / skipped 四桶（conflict/outOfScope/unresolvedTarget/rolledBack——nonApplied 的计数镜像）/ costTier（v1 恒 IN_PROCESS）/ residualRuleCount。
- `nonApplied`：未应用项显式枚举（reason 四态 CONFLICT/OUT_OF_SCOPE/UNRESOLVED_TARGET/ROLLED_BACK + path + detail）——部分失败是结构化结果，不是错误。

### rename 两档语义

- 第一档：局部变量/参数，单文件（offset 定位）。
- 第二档：字段/非虚方法/类型，模块内符号域（FQN 或 offset 定位）——TYPE 面同步 import 与限定名 mention（dot 边界），非绑定文件的 FQN mention 同样改写；FIELD/METHOD 保守面（receiver 型不可绑定 token → CONFLICT 拒绝）；stale 残留 → plan 期 CONFLICT 拒绝（不落盘损坏状态）。
- 冲突 fail-closed 进 nonApplied（CONFLICT），绝不静默漏改。

### 资源约束（fail-closed 沿 Lint__checkSource 先例）

- `nop.refactor.graphql.max-source-size`（默认 1MB，字节级前置门 + 读后 backstop）。
- `nop.refactor.graphql.max-target-files`（默认 512，读取前拒绝）。
- 退出码（CLI）：0 = 全部应用 / 1 = 存在 nonApplied / 2 = 错误中止。

## 复杂度预算（WI13 审计）

nop-refactor 三模块 main Java **4,010 行 / 33 文件**（core 1,848 + java 1,619 + graphql 543），对照 nop-lint 族锚点 21,927 行——预算余量 ~82%。能力归属上游增量（WI3–WI7 对 nop-lint 净 +399）单列归属——明细见平台 AI 开发记录区的 WI13 预算审计。

## Runbook 指针

- 规则/DSL 语义（transform/fix 载体语法）：`nop-lint/nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/` 下规则 YAML 即权威示例；lint-rule xdef（lint 模型注册）随 nop-lint-core 资源目录发布，规则 YAML 即权威示例。
- 编辑应用机制（冲突仲裁/原子写/回滚）：`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/`（EditPlanApplier/FixApplier javadoc）
- 操作框架与载荷类型：`nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/`（operation/symbol 包 + RefactorResult record javadoc）
