# 2026-10-02 WI10 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

> 来源：plan 2302（WI10 format 第二批）执行期发现。按 roadmap 硬边界记录，修复独立立项。mermaid 1-4 项同根（文法文件缺陷），可合并一个立项。

## 1. mermaid 文法 CLASS/STATE token 重复定义且字面量冲突（P1 嫌疑）

- Problem：`nop-format/nop-mermaid/model/antlr/BaseRules.g4` 第 6-7 行（`CLASS:'classDiagram'`/`STATE:'stateDiagram'`）与第 42-43 行（`CLASS:'class'`/`STATE:'state'`）重复定义。ANTLR 取首个定义，'class'/'state' 被词法化为 Identifier_ → 标准输入 `classDiagram class Foo {…}`、`stateDiagram state A : "desc"` 解析失败（nop.err.antlr.not-end-properly），MermaidClassNode/MermaidStateNode 解析路径实际不可达。
- 锚定：TestMermaidASTParser 特征化用例（testClassKeywordLexesAsIdentifier/testStateKeywordLexesAsIdentifier）。

## 2. mermaid DIRECTION token 无词法定义（P2 嫌疑）

- Problem：`Mermaid.g4:47` 引用 `DIRECTION`，但 BaseRules.g4 无该 lexer 规则 → direction 语句永不可达，`direction TB` 中 'direction' 被解析为普通 FlowNode。

## 3. mermaid sequenceMessage 与 flowEdge 规则同构（P2 嫨疑）

- Problem：`mermaidSequenceMessage` 与 `mermaidFlowEdge` 规则体完全同构（label 位于 to 之前而非 `:` 之后）→ adaptivePredict 恒选靠前的 flowEdge，序列消息一律解析为 MermaidFlowEdge。

## 4. mermaid SLL 阶段异常绕过 LL 二阶段重试（P2 嫌疑）

- Problem：AbstractParseTreeParser 仅捕获 ParseCancellationException，SLL 阶段 checkEnd 抛出的 NopException 绕过 LL 重试——使上述文法问题表现为不可恢复的 not-end-properly。

## 5. pdf RCPathCellDataLocator.buildFlatTable 合并单元格双重展开（P1 嫌疑）

- Problem：约 L205，TableBlock.getCell 解析为真实单元格后再次按 rowspan/colspan 展开 → 合并区贴表格边缘时抛 ArrayIndexOutOfBoundsException（实测复现）；位于中部时覆盖其后行首列文本（平坦化数据损坏）。
- 影响：pdf 表格抽取对含合并单元格的表格产出错误数据或崩溃。

## 6. pdf DashPatternDetector 滑窗锚定（P3 嫌疑）

- Problem：walk 的容差滑窗在失配窗口处跳过而非中断，噪声前缀会把模式 start 锚定为 0（实测 mod=2/start=0/len=8）。
