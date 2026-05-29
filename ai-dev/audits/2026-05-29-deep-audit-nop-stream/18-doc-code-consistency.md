# 维度18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] module-groups.md 遗漏 nop-stream 模块分组

- **文件**: `docs-for-ai/01-repo-map/module-groups.md:9-20`
- **证据片段**:
  ```markdown
  | 分组 | 主要路径 | 作用 |
  |------|---------|------|
  | 基础内核 | `nop-kernel/` | ... |
  | 核心框架 | `nop-core-framework/` | ... |
  | ... | ... | ... |
  | WIP 实验模块 | `nop-code/` | ... |
  ```
  nop-stream（607 个 Java 文件、9 个子模块）未出现在分组表中。
- **严重程度**: P2
- **现状**: nop-stream 是根 pom.xml 中的顶级模块，体量与已列入的 nop-code 相当，拥有独立的设计文档目录，但 module-groups.md 未包含。
- **风险**: AI agent 无法从 module-groups.md 建立正确的模块认知地图。
- **建议**: 在 module-groups.md 中添加 nop-stream 模块分组条目。
- **信心水平**: 确定
- **误报排除**: 不是审美问题。导航文档缺少完整顶级模块是结构性缺失。
- **复核状态**: 未复核

---

### [维度18-02] source-anchors.md 缺少 nop-stream 异常类锚点

- **文件**: `docs-for-ai/04-reference/source-anchors.md`
- **证据片段**:
  ```markdown
  // error-handling.md L124-127 引用 StreamException/StreamRuntimeException 作为参考实现
  // 但 source-anchors.md 中无对应锚点
  ```
- **严重程度**: P3
- **现状**: `error-handling.md` 将 `StreamException`/`StreamRuntimeException` 作为模块异常类参考实现展示，但 `source-anchors.md` 无对应锚点。
- **风险**: AI agent 按文档指引需要确认异常类定义时，缺少锚点导致无法快速定位。
- **建议**: 在 source-anchors.md 中添加 StreamRuntimeException 的锚点条目。
- **信心水平**: 确定
- **误报排除**: error-handling.md 明确引用了这两个类，缺少锚点导致文档体系不自洽。
- **复核状态**: 未复核

---

## 正面确认（无问题项）

- `StreamRuntimeException extends NopException` 与 error-handling.md 描述一致 ✓
- `StreamException extends StreamRuntimeException` 与描述一致 ✓
- 双构造器（String + ErrorCode）与描述一致 ✓
- nop-stream/README.md 模块状态表与实际代码一致 ✓
- README 快速开始 API 签名与 StreamExecutionEnvironment 一致 ✓
- NopStreamErrors 错误码命名 `nop.err.stream.*` 符合规范 ✓
