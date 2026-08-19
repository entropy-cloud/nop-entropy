# xlang-java 子系统设计

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

`nop-xlang-java` 执行后端（目标模块，`nop-kernel` 下由实现阶段创建）：Executable 树 → Java 源码的构建期转译器。服务"编译期可确定资源"的零解释执行，是 native image 形态的唯一提速路线。愿景层不设独立 00-vision——双后端统一愿景（分工原则、成功标准、不可违反约束）定义在 `../xlang-execution/00-vision.md`，本目录只承载 java 后端专属架构。

## 文档结构与职责边界

| 文档 | 层级与职责 | 状态 |
|---|---|---|
| 01-architecture-baseline | Architecture Baseline 层：转译器总体结构、~137 节点类映射策略（分类 + 覆盖矩阵 + fail-fast）、SourceLocation 保真（静态常量内嵌）、生成类加载与 ResourceComponentManager 集成（生成类优先/解释器兜底）、`_gen/` 构建任务、EvalMethod 调用约定 | active |
| 00-vision | 不设独立文件，见 `../xlang-execution/00-vision.md`（双后端统一愿景） | — |

## 阅读顺序

1. **必读**：`../xlang-execution/00-vision.md` → `../xlang-execution/01-architecture-baseline.md`（统一选择机制 / 注册 SPI / 对拍框架）→ 本目录 `01-architecture-baseline.md`
2. **按需深入**：truffle 后端（互补边界）→ `../xlang-truffle/02-architecture-baseline.md`
3. **扩展方向**：构建管线接入与 native image 配置 → `nop-codegen` 的 `GraalvmConfigGenerator` 通路

## 关联

- 节点清单基线：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（live 137 文件）
- 调用约定先例：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/janino/JaninoScriptCompiler.java`
- Java 源编译通路：`nop-kernel/nop-javac/`（`JdkJavaCompiler` 等）
