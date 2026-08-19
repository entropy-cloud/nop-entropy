# xlang-execution 子系统设计

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

XLang 执行层的统一架构：解释器（基线与兜底）+ `nop-xlang-java` / `nop-xlang-truffle` 双后端的选择机制、注册 SPI、对拍验证框架与模块边界。三后端分工原则与不可违反约束在 Vision 层定义。

## 文档结构与职责边界

| 文档 | 层级与职责 | 状态 |
|---|---|---|
| 00-vision | Vision 层：问题定位（native 无 JIT / 动态脚本无 JIT）、三后端分工原则、成功标准（对拍基准 + 方向性性能门槛）、显式 non-goals、不可违反约束 | active |
| 01-architecture-baseline | Architecture Baseline 层：后端选择机制（判定输入/时机/降级决策树）、后端注册 SPI（ScriptCompilerRegistry 先例）、三后端对拍验证框架、模块边界与依赖方向、与 ResourceComponentManager 和构建管线的集成边界 | active |

## 阅读顺序

1. **必读**：`00-vision.md` → `01-architecture-baseline.md`（本子系统全部结论在这两篇）
2. **按需深入**：java 后端细节 → `../xlang-java/01-architecture-baseline.md`；truffle 后端细节 → `../xlang-truffle/02-architecture-baseline.md`（含 `../xlang-truffle/00-vision.md`）
3. **扩展方向**：Truffle 框架外部知识速查与 SimpleLanguage 源码地图 → `../xlang-truffle/01-truffle-knowledge.md`

## 关联

- XLang 现解释器实现：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`
- 后端注册先例：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/script/ScriptCompilerRegistry.java`
- 模型加载集成锚点：`nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java`
