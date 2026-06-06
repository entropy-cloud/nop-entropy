# 维度 18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] 文档 dict 清单中 code/provenance 未绑定到 ORM 字段

- **文件**: `docs-for-ai/03-modules/nop-code.md:95-106`
- **证据片段**:
  ```yaml
  # provenance.dict.yaml 存在
  label: 边来源
  options:
    - value: AST_EXTRACTION
    - value: SYMBOL_SOLVER
    - value: HEURISTIC
    - value: FRAMEWORK_INFERENCE
    - value: MANUAL
  ```
  但 ORM 中 NopCodeSemanticEdge.provenance 未声明 ext:dict="code/provenance"。
- **严重程度**: P2
- **现状**: provenance dict 存在于 yaml 文件中，但 ORM 模型中 provenance 字段未绑定该 dict。合法性依赖代码中 EdgeProvenance.valueOf() 枚举约束。
- **风险**: 自动生成的 UI 不会绑定 provenance 合法值列表。
- **建议**: 在 ORM 模型中对 provenance 字段添加 ext:dict="code/provenance"，或文档注明此 dict 仅用于 enum 约束。
- **信心水平**: 高
- **误报排除**: dict yaml 存在且正确，仅 ORM 绑定缺失。
- **复核状态**: 未复核

### [维度18-02] 文档 BizModel 清单不完整

- **文件**: `docs-for-ai/03-modules/nop-code.md:44-46`
- **证据片段**: 文档仅列出 2 个 BizModel，实际存在 11 个。NopCodeFileBizModel 有自定义方法但未文档化。
- **严重程度**: P2
- **现状**: 文档 Key BizModels 清单仅列出 NopCodeIndexBizModel 和 NopCodeSymbolBizModel。
- **风险**: 开发者可能不知道 NopCodeFileBizModel 暴露了文件级查询 API。
- **建议**: 补充 NopCodeFileBizModel 及其关键 API。其余空壳 BizModel 合并为一行说明。
- **信心水平**: 高
- **误报排除**: NopCodeFileBizModel 有自定义 @BizQuery 方法，不是空壳。
- **复核状态**: 未复核
