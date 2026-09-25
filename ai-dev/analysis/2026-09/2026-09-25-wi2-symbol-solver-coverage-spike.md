# WI2 Spike — JavaParser SymbolSolver 在 nop-entropy 多模块源码树上的类型解析率实测与 P1 rename 符号域裁定

> 日期：2026-09-25
> 类型：spike 实测 + Decision（roadmap WI2，`ai-dev/backlog/nop-refactor-roadmap.md` M0）
> Spike 代码：`_tmp/wi2-spike/`（WI2SymbolSolverSpike.java + cp.txt + results.txt + run.log；按 roadmap 裁定不进模块）
> 结论先行：**P1 rename 符号域 v1 裁定为"单模块内"；引用搜索落点裁定为"操作器内嵌轻量索引"，不消费 nop-code 查询面。**

## 一、实测设置

- 被测对象：nop-entropy 自身（Maven 多模块，309 个含 `src/main/java` 的模块目录）。
- 求解器两档：
  - **SINGLE**：`ReflectionTypeSolver`（JDK）+ `JavaParserTypeSolver`（本模块源码根）——"v1 单模块符号域"候选；
  - **GLOBAL**：`ReflectionTypeSolver` + `JavaParserTypeSolver`（全部被采样模块的源码根 + 存在时的 `target/generated-sources/*`）——"classpath/源码可达域"候选。
- 采样：优先强制纳入 refactor 目标域模块（nop-lint / nop-utils / nop-code / nop-core / nop-xlang 前缀），其余名额分层抽样，共 25 模块 84 文件（每模块至多 6 文件、均匀步进取样）。
- 指标：`ClassOrInterfaceType` 引用双档解析率、解析成功的分类（io.nop.* / 平台 / 第三方）、io.nop.* 中的跨模块解析数、`MethodCallExpr` 解析率（GLOBAL）。
- 版本：javaparser 3.26.3（`nop-kernel/nop-dependencies` 钉定）+ javassist 3.30.2-GA + guava 33.4.8-jre；独立 javac/java 运行，零模块侵入。

## 二、实测结果（results.txt 全文见 `_tmp/wi2-spike/results.txt`）

汇总（5292 个类型引用 / 4886 个方法调用）：

| 指标 | 数值 |
|---|---|
| 类型引用解析率 GLOBAL（全源码根） | **72.3%**（3826/5292） |
| 类型引用解析率 SINGLE（单模块） | 61.9%（3278/5292） |
| 解析成功中：io.nop.* | 1295 |
| 解析成功中：平台（java./javax./jakarta.） | 2531 |
| 解析成功中：第三方 | 0 |
| **io.nop.* 中的跨模块解析** | **18 / 5292（0.3%）** |
| 未解析 GLOBAL | 1466（27.7%） |
| 方法调用解析率 GLOBAL | 55.5%（2710/4886） |

模块级显著形态：

- 第三方依赖重的模块在无第三方 jar solver 时解析面崩塌：nop-lint-core **9.0%**、nop-lint-java **14.4%**、nop-java-parser **30.7%**（其类型引用大量是 `com.github.javaparser.*` 等，不在本次 solver classpath 上）。
- API/自包含模块解析率高：nop-code-api 92.5%、nop-code-core 97.4%、nop-code-flow 99.0%、nop-shell 96.5%、nop-api-core 96.5%。
- GLOBAL 对 SINGLE 的增益仅 ~10pp；增益集中在跨模块聚合型模块（nop-code-service 56.1%→95.8%、nop-lint-graphql 47.2%→81.7%）。
- 异常样本：nop-router SINGLE 100% > GLOBAL 74.9%——跨模块同名简单类型造成的 solver 序干扰（CombinedTypeSolver 命中错误模块的同名源文件），属源码根模式的固有噪声，如实记录。

## 三、结果解读

1. **跨模块类型解析在源码根模式下不可用**：5292 个引用中仅 18 个解析到别的模块的 io.nop.* 声明。"classpath 可达域"作为 rename 符号域的可靠性前提不成立。
2. **第三方 jar 缺位是解析率天花板的主因**：未解析 1466 个的主体是第三方类型。补齐它们需要 Maven 模型 → classpath 装配器（25e 分析文档 §99 已识别为 OpenRewrite 用构建插件解决的同一问题）——这正是"引入编译器级工程模型"的复杂度起点，与 vision 原则 9 的复杂度预算和原则 2 的无状态语义（每请求装配整个工程模型）直接冲突。
3. **rename 阶梯实际需要的解析面远小于全量类型解析**：WI10（局部变量/参数，单文件）只需 ScopeAnalyzer 级作用域语义（审计已确认正确）；WI11（字段/非虚方法/类型，模块域）的跨文件引用改写靠"简单名 + 包名/import 绑定"的结构化过滤即可命中绝大多数真实引用，歧义形态（同简单名多来源）按 fail-closed 进 nonApplied——与 roadmap WI11"跨模块引用按裁定显式 nonApplied 或支持，不静默漏改"一致。
4. **测量口径限制（如实声明）**：本 spike 未测"编译类路径模式"（`ClasspathTypeSolver` over `target/classes` + 第三方 jar）。不补测的理由：即便编译模式解析率更高，它要求全部模块已构建 + 每请求装配全工程 classpath，无状态服务的成本模型不成立，且仍需 Maven 模型装配器——结论不因该模式改变。

## 四、裁定（roadmap WI2 Decision）

### 裁定 1：P1 rename 符号域 v1 = 单模块内（module-scoped）

- WI10 局部变量/参数：单文件，ScopeAnalyzer 语义（与 roadmap 原文一致，spike 无新增约束）。
- WI11 字段/非虚方法/类型：模块内符号域；跨文件引用改写按"简单名 + 同包/import 绑定"的内嵌索引过滤，歧义或跨模块引用显式 `nonApplied`（unresolved-target / out-of-scope），不静默漏改。
- 被拒绝的替代：classpath 可达域——理由见 §三.1/§三.2（实测 0.3% 跨模块解析率 + Maven classpath 装配器的复杂度与无状态冲突）。若未来需要工程级 rename，升级路径是独立的"工程装配器"立项（design 层另议），不在本 roadmap 预算内。

### 裁定 2：引用搜索落点 = 操作器内嵌轻量索引，不消费 nop-code

- WI9 的符号解析适配 SPI 的 Java 实现内嵌：目标模块文件按需 JavaParser 解析 + 声明索引（简单名 → 声明节点）+ 引用过滤（同包 / import / 限定名），无持久索引、无外部服务依赖。
- nop-code 不进入 v1 依赖拓扑：其查询面是带索引栈的服务形态（nop-code-service 的 CodeSearch/CodeQuery/CodeIndex/CodeGraph 服务族），生命周期与部署形态和无状态 refactor 操作不匹配（vision 原则 7：进程内快路径）；roadmap §七"不引入外部索引器"同向。baseline §二架构图中的 CORE→CODE 边按本裁定收窄：v1 不接线，CODE 保留为未来工程级能力的候选底座（design 01 增注记录）。
- 被拒绝的替代：nop-code 查询面——理由：服务栈形态错配 + 为 codemod/rename 引入运行时服务依赖违反复杂度预算。

## 五、对 WI9 plan 的转录义务

roadmap WI2 交付要求："裁定记录与覆盖率数字落当日 log + analysis 文档，WI9 plan 起草时转录进其 Current Baseline"。本文件 §二/§四 即转录源；WI9 plan 的 Current Baseline 必须引用：72.3%/61.9%/18(0.3%) 三个数字、"单模块内"符号域裁定、内嵌索引落点裁定。

## 六、复现

```bash
cd _tmp/wi2-spike
CP=$(cat cp.txt)
javac -cp "$CP" -d classes WI2SymbolSolverSpike.java
java -Xmx6g -cp "$CP:classes" WI2SymbolSolverSpike <repo-root> > results.txt 2> run.log
```
