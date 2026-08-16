# xlang-truffle 子系统设计

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

为 nop-xlang 提供 Truffle 执行后端（JVM 部署形态下的 XLang 提速），支持多线程运行。当前处于**知识准备阶段**——尚未进入架构设计，先沉淀实现所需的 Truffle 框架通用知识。

## 文档结构与职责边界

| 文档 | 层级与职责 | 状态 |
|---|---|---|
| 01-truffle-knowledge | 知识层：Truffle 框架外部知识（执行模型 / 核心 API / 多线程模型 / DSL 规范 / SimpleLanguage 地图）+ XLang 映射草案（含推荐倾向，属**草案级**判断，正式决策待 00/02 接管） | active |
| 00-vision | Vision 层：目标、非目标、成功标准（决策性内容，待启动设计时创建） | 待创建 |
| 02-architecture-baseline | Architecture Baseline 层：模块划分、翻译器结构、Context 池设计、与现解释器的关系。进入设计阶段时，01 中决策性结论必须提炼至此，纯知识部分保留在 01 或迁 `ai-dev/analysis/` | 待创建 |

> 注：知识层文档含外部框架类签名引用，与 `ai-dev/design/00-design-writing-guide.md` "design 不记录具体代码"的字面约定有张力；本目录以 README 显式声明"知识准备阶段"作为过渡态豁免——**架构设计启动后，决策与契约必须沉淀到 00/02，01 不得继续承载决策**。

## 阅读路径

1. **必读**：`01-truffle-knowledge.md`（全部）——事实来源为 `~/sources/graal` 一手源码，引用带路径行号
2. **按需深入**：本地 Truffle 官方文档 `~/sources/graal/truffle/docs/`（Optimizing / Safepoints / Exit / AOTOverview / bytecode_dsl）
3. **扩展方向**：oracle/graal sparse checkout 追加 `compiler` 模块可读 PE 实现

## 关联

- XLang 现解释器实现：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`
- native image 形态的 XLang 提速走构建期 Java 转译路线，与本目录互补不重叠
