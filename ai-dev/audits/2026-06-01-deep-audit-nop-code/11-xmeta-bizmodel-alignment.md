# 维度11：XMeta 与 BizModel 对齐 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度11-01] NopCodeIndexBizModel 25 个自定义方法在 xmeta 中无对应 prop

- **文件**: `nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeIndex/NopCodeIndex.xmeta`
- **证据片段**:
  ```xml
  <meta x:extends="_NopCodeIndex.xmeta">
      <props/>
  </meta>
  <!-- 空壳 -- 无任何 prop 定义 -->
  ```
  对比 NopCodeIndexBizModel 有 25 个 @BizQuery/@BizMutation 方法。
- **严重程度**: P2
- **现状**: 20+ 个自定义方法在 GraphQL API 上暴露，但 xmeta 中完全没有对应的 prop 定义。xmeta 仅定义了 9 个 ORM 字段 prop 和 9 个关联 prop。
- **风险**: (1) 方法的参数/返回值无法通过 xmeta 进行权限控制细化。(2) 前端代码生成器无法自动识别这些 API。(3) API 文档生成缺少元数据。
- **建议**: 在 xmeta 手写层为关键方法添加对应 prop。
- **信心水平**: 90%
- **误报排除**: Nop 平台中 @BizQuery/@BizMutation 通过 BizProxyFactoryBean 自动暴露，功能上可正常工作。但缺少 xmeta 元数据限制了平台能力。
- **复核状态**: 未复核

### [维度11-02] NopCodeSymbolBizModel 的 @BizLoader 方法针对非实体类型，无对应 xmeta prop

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:101,116`
- **证据片段**:
  ```java
  @BizLoader(forType = SymbolDTO.class)
  public List<AnnotationUsageDTO> usages(@ContextSource SymbolDTO symbol, ...) { ... }
  ```
- **严重程度**: P2
- **现状**: 2 个 @BizLoader 和 15 个 @BizQuery 方法在 xmeta 中均无对应定义。`SymbolDTO` 没有 xmeta 定义。
- **风险**: 缺少 xmeta 元数据限制了平台元数据驱动能力。
- **建议**: 为关键方法和 BizLoader 添加 xmeta 定义。
- **信心水平**: 85%
- **误报排除**: `@BizLoader` 的 `forType` 属性支持非实体类型，功能上可工作。
- **复核状态**: 未复核

## 无问题确认

- **8 个空壳 BizModel 与 xmeta 对齐正确**: 仅继承 CrudBizModel 默认 CRUD，xmeta 自动生成标准 CRUD prop。
- **权限控制一致性**: `@Auth(roles="admin")` 用于管理操作，`@Auth(permissions="code-query")` 用于查询，`code-source-read` 用于源码读取。分层合理。
