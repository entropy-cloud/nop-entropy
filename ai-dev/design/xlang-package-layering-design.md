# nop-xlang 包级分层治理设计（三档）

**日期**：2026-10-09
**来源**：full-code-review 1.2 审计 P1-1.2-001（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.2-xlang-structure-pipeline-audit.md`）；修复工作项 R1.1（plan 2364）
**范围**：`nop-kernel/nop-xlang` 包级依赖结构；不涉及任何执行语义变更
**状态**：active

---

## 一、设计结论

1. nop-xlang 包级导入图按「三档治理」管理：**豁免登记**（档一）、**单边割离**（档二）、**内核红线**（档三）。三档共同目标是阻止 21 节点强连通分量（SCC）随开发继续增长，并把已确认的可避免耦合逐边拆除。
2. 第一条割离边为 **expr→compile**（审计实验证实的性价比最高单边：割后 compile 整包出环）。割离采用「expr 侧接口 + compile 侧实现 + ServiceLoader 装配 + fail-loud」的接缝形态。
3. 内核红线：包级导入图中**禁止新增会形成新环的方向边**；13 节点内核（api/ast/scope/exec/expr/utils/xdef/xdsl/xmeta/xpath/xpl + feature/XLangConstants，节点清单以当次 SCC 实测为准）**禁止新增入边**，接口-实现适配边（impl 边）须逐条登记入档一豁免表后方可引入。

## 二、档一：豁免登记（协同环与合法适配边）

以下包级双向耦合经审计逐环甄别为**协同必然或假环**，登记豁免、不强行割离：

| 环 / 边 | 性质 | 裁定依据 |
|---|---|---|
| facade/router 互指 | 协同环 | facade 与 router 职责互为表里 |
| XLangConstants 根枢纽聚合 | 常量接口假环 | 常量共享不构成语义耦合 |
| ast↔scope（XLangASTNode↔LexicalScope） | 元模型共生 | 数据模型互引，拆解成本高于收益 |
| xdef/xdsl/xmeta 三角互缠 | 元模型三件套共生 | 145 条互向 import 中 IFACE 契约边为正当方向；仅砍非 IFACE 边（未立项，见 §五） |
| compile→expr（`IExecutableExprCompiler` 适配边） | 接口-实现适配边 | R1.1 割离 expr→compile 的接缝实现边；不形成环（expr 无回到 compile 的路径），登记为红线 §四的首条适配边豁免 |

## 三、档二：expr→compile 单边割离

### 裁定

- `io.nop.xlang.expr` 包**不 import `io.nop.xlang.compile`**。这是 expr 包的硬边界：包内任何文件不得出现 `io.nop.xlang.compile` 导入。
- 割离点为 `XLangExprParser.buildExecutable`（原 expr 包内唯一直接使用 compile 类的方法）。该方法保留在 `IXLangExprParser` 接口上（调用方 `XplParseHelper.runMacroExpr` 以 `IXLangExprParser` 形参调用、`ExprEvalHelper`/`LexicalScopeAnalysis`/`XLangCompileTool` 经 `scope.getCompiler()` 调用，公开契约不变）。
- 实现形态：expr 侧新增 `IExecutableExprCompiler` 接口与委托逻辑；compile 侧新增实现类承载原管线（词法作用域分析 → 可选类型推导 → 可执行体构建）；经 `META-INF/services` + 解析缓存装配（volatile 双检查锁缓存，支持显式注册覆盖与 `set(null)` 显式未注册态，`DslModelHelper.g_excelModelLoaderFactory` ServiceLoader 先例同款思路）；未注册时以具名 `ErrorCode` 响亮失败，不允许静默跳过。
- 行为不变式：`buildExecutable` 的求值语义、日志行为（trace 打印、类型推导警告）、配置门控（`nop.xlang.type-inference.enabled` 默认 off）全部保持不变。

### 已拒绝的替代方案

| 方案 | 拒绝原因 |
|---|---|
| `XplCompiler`（xpl）直接实现并 import compile | compile→xpl 已存在（3 个类引用跨 2 文件：LexicalScopeAnalysis、BuildExecutableProcessor），将形成 compile↔xpl 二元环，违反红线 |
| 把 `XLangExprParser` 整类迁入 compile | `XplCompiler extends XLangExprParser`，xpl→compile 新边，同样成环 |
| 把六个 compile 协作类迁入 expr | `BuildExecutableProcessor` 重度依赖 exec（exec→expr 已存在），expr→exec 成环；且属大搬运，违反最小改动纪律 |
| 静态初始化器互注册（compile 类 static 块调 expr setter） | 类加载顺序不可保证，单测直调路径（不经 CoreInitialization）会漏注册 |
| 反射/`Class.forName` 按名加载 | 字符串化耦合，绕过导入图度量，属作弊形态 |

## 四、档三：内核红线

1. **零新环**：`io.nop.xlang` 包级导入图（含 XLangConstants 等根类，口径同审计脚本 `_tmp/dim01-r2-scc.py`）中不允许新增任何会形成新环的方向边。
2. **内核入边冻结**：SCC 内核成员（割离后以实测 SCC 为准）禁止新增入边；接口-实现适配边为唯一例外，须登记入 §二豁免表并附「不成环」论证。
3. **验证命令**：
   - 边检查（覆盖 import 与限定名内联使用两种形态，不锚定 `import ` 前缀）：`grep -rn "io\.nop\.xlang\.compile" nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/` 必须为空。
   - SCC 复测：`python3 ai-dev/tools/xlang-package-scc-check.py`（审计复现脚本 `_tmp/dim01-r2-scc.py` 的版本化副本，只扫 main 源码），SCC 成员数不得高于割离后基线。
4. lint 规则固化（把 §四.1/§四.2 固化为 nop-lint 或 CI 校验）为后续优化项，不在 R1.1 交付面内。

## 五、移交与后续

- 剩余已验证出环割边（script/delta/filter/backend/xt/functions 各自的出环边，compile 已随 expr→compile 割离出环）与 xdef/xdsl/xmeta 非 IFACE 边治理：留作后续小步割离候选（optimization candidate），不在 R1.1 范围。
- 关联审计条目：P2-1.2-003（exec→xpl 反向边，经接口/回调解耦，属同批割离范式的后续应用）。
