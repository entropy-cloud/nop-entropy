# xlang-truffle 子系统设计

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

为 XLang 提供 Truffle 执行后端（目标模块 `nop-xlang-truffle`，`nop-kernel` 下由实现阶段创建）：**JVM 部署形态下运行时动态脚本的 JIT 提速**。定位、成功标准、non-goals 与 java 后端的互补边界在 Vision 层定义；语言实现、帧映射、多线程运行时在 Architecture Baseline 层定义。

## 文档结构与职责边界

| 文档 | 层级与职责 | 状态 |
|---|---|---|
| 00-vision | Vision 层：定位（JVM 形态动态脚本 JIT 提速）、成功标准（对拍 + 方向性性能门槛，量化归 I7）、显式 non-goals（不做 parser Truffle 化 / 不做 native 形态 / 一期不做 instrumentation）、与 java 后端互补边界 | active |
| 02-architecture-baseline | Architecture Baseline 层：XLangLanguage/XLangContext 设计、对象与帧/slot 映射、多线程架构（Context 池 + 共享 Engine + SHARED）、两级内联缓存准则、与 nop-js Engine 共享评估、依赖钉版（25.x LTS + 条件钉版）、原 Open Questions 裁定汇总 | active |
| 01-truffle-knowledge | **知识参考层**：Truffle 框架外部知识速查（执行模型 / 核心 API / 多线程机制 / DSL 规范）+ SimpleLanguage 源码地图。**不承载 XLang 侧设计决策**（决策已全部收口至 00/02） | active（职责缩限） |

> 豁免边界声明：`01-truffle-knowledge.md` 含外部框架的类签名与代码示例引用，与 `ai-dev/design/00-design-writing-guide.md`"design 不记录具体代码"的字面约定存在张力。本 README 显式声明豁免——**豁免范围仅限"外部框架（GraalVM/Truffle）的知识速查与源码地图"**（其事实来源为 `~/sources/graal` 一手源码，属稳定参考材料而非过程记录），**不得扩展到 nop 自身的设计决策与代码**（nop 侧决策一律在 00/02，代码层面事实以源码为唯一依据）。

## 阅读顺序

1. **必读**：`00-vision.md` → `02-architecture-baseline.md`（本子系统全部决策在这两篇）
2. **按需深入**：统一选择机制 / 注册 SPI / 对拍框架 → `../xlang-execution/`；java 后端 → `../xlang-java/01-architecture-baseline.md`
3. **参考速查**：`01-truffle-knowledge.md`（Truffle API 用法、DSL 注解规范、SL 实现地图——实现期查阅，不含决策）；本地 Truffle 官方文档 `~/sources/graal/truffle/docs/`
4. **扩展方向**：oracle/graal sparse checkout 追加 `compiler` 模块可读 partial evaluation 实现

## 关联

- XLang 现解释器实现：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`
- 统一架构（选择机制 / SPI / 对拍）：`../xlang-execution/`
- native image 形态的 XLang 提速走构建期 Java 转译路线（`../xlang-java/`），与本目录互补不重叠
