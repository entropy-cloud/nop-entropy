# nop-xlang-truffle 后端愿景（xlang-truffle）

**日期**：2026-08-19
**范围**：XLang 的 Truffle 执行后端：定位、成功标准、显式 non-goals、与 java 后端的互补边界
**状态**：active（目标模块 `nop-kernel` 下 `nop-xlang-truffle` 由实现阶段创建）

---

## 一、设计结论

1. `nop-xlang-truffle` 的定位是**JVM 部署形态下运行时动态脚本的 JIT 提速**：把运行时编译出的 Executable 树翻译为 Truffle AST（XLangLanguage），在 GraalVM 运行时上经 partial evaluation 获得 JIT。
2. 收益边界诚实声明：**真正收益在 GraalVM 部署形态**；stock JVM 上 Truffle 纯解释执行，收益只来自 DSL 生成的紧凑特化节点，不承诺超过解释器（量化归 I7 基准）。
3. Truffle 框架长期存在性有充分外部证据（GraalVM "独立多语言运行时"战略的核心底座，Maven 构件独立分发，Oracle 支持承诺至 2030+），长期投资风险可控。
4. 与 java 后端判据互斥（编译期确定 vs 运行时动态）、模块互不依赖，兜底一律回解释器。

## 二、定位与问题

统一愿景（`../xlang-execution/00-vision.md`）已定义两条痛点与本后端的分工位置。本节只回答"为什么动态脚本的后端是 Truffle"：

- 动态源在运行时才产生，**无法走构建期转译**（java 后端的"编译期可确定"判据不满足；native image 之外的 JVM 形态虽可运行时编译字节码，但该路线已被统一愿景否决为默认路线——类加载/卸载管理与字节码工具链的结构性复杂度）。
- Truffle 的结构性价值恰好匹配：**parse（翻译）即执行**，无需字节码工具链；类型画像 + 节点特化在运行时渐进优化；GraalVM 形态下 partial evaluation 把热点 CallTarget 编译为机器码。
- 平台已有 polyglot Context 嵌入先例（`nop-js` 封装 GraalJS），嵌入模式与风险已知。

## 三、成功标准

1. **正确性（对拍，一票否决）**：同一 Executable 树，Truffle 翻译 AST 的执行结果与解释器一致（返回值 / 副作用 / 异常语义三层断言，统一对拍框架承载）。正确性验证阶段允许以 `EXCLUSIVE` 单 Context 与解释器对拍（保守过渡，不追求共享），验证通过后切 `SHARED` 上池。
2. **性能门槛（方向性，量化归 I7）**：GraalVM 形态显著优于解释器；stock JVM 形态以"不显著劣于解释器"为底线。本层不发明具体数字，基准口径（负载集、形态矩阵、阈值）由 I7 落定。
3. **多线程正确性**：Context 池并发求值下行为正确——输出缓冲（`IEvalOutput`）与求值状态按 Context 隔离，无跨 Context 共享可变状态；并发求值无串行化瓶颈回归。验证载体：SHARED 池化形态的并发求值正确性断言（与单线程结果一致、无跨 Context 串值），见 [02-architecture-baseline.md](02-architecture-baseline.md) §五，纳入 I4 实现计划验收。
4. **边界干净**：Truffle 依赖零泄漏（`org.graalvm.*` 只出现在本模块）；解释器测试零回归。

## 四、显式 Non-Goals

- **不做 xlang parser / 宏的 Truffle 化**：上游 XplCompiler（宏/标签全展开）保持不变；Truffle 层只承担"执行后端"职责，`Source` 为合成源、AST 程序化构造（框架允许无 parser 的语言实现）。
- **不做 native image 形态的 XLang Truffle 后端**：native 内 guest 代码虽有运行时 JIT（GraalVM 25 默认），但镜像体积、warmup、双解释器维护成本与"Generator 优于 Interpreter"原则都指向构建期转译——native 提速唯一路线是 java 后端（见 `../xlang-execution/00-vision.md` §五）。Truffle 后端只服务 JVM 部署形态。
- **一期不做 instrumentation / debugger 集成**：Truffle `ExecutionEventListener` 与 instrument 工具链留作后续（接入 nop 链路追踪）。
- **不做 guest 侧线程原语的 XLang 语言级暴露**（`Env.newTruffleThreadBuilder`、`TruffleSafepoint` 等）：仅当未来 XLang 增加语言级线程/exit 语义时才需对接（框架知识见 `01-truffle-knowledge.md` §4.5）。

## 五、与 java 后端的互补边界

| 维度 | nop-xlang-java | nop-xlang-truffle |
|---|---|---|
| 适用判据 | 编译期可确定资源（`_vfs` 静态资源） | 运行时动态产生的脚本/表达式 |
| 产物形态 | 构建期 `_gen/` 源码 → class（closed-world 友好） | 运行时翻译 AST（无构建期产物） |
| 部署形态 | JVM + native image | 仅 JVM（GraalVM 获 JIT，stock JVM 解释） |
| 模块关系 | 互不依赖；同一统一选择机制（`../xlang-execution/01-architecture-baseline.md`）下按产生时机裁决；兜底同为解释器 | 同左 |

判据在统一愿景层定义（"代码的产生时机"），两后端各自架构见 `../xlang-java/01-architecture-baseline.md` 与本目录 `02-architecture-baseline.md`。

## 六、与已有设计的关系

- 统一愿景与架构：[../xlang-execution/00-vision.md](../xlang-execution/00-vision.md)、[../xlang-execution/01-architecture-baseline.md](../xlang-execution/01-architecture-baseline.md)
- 本后端架构：[02-architecture-baseline.md](02-architecture-baseline.md)
- 框架知识速查与 SimpleLanguage 源码地图：[01-truffle-knowledge.md](01-truffle-knowledge.md)（知识参考层，不承载 XLang 决策）
