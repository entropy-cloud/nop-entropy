# 维度 11：XMeta 与 BizModel 对齐

## 审计日期
2026-06-06

## 第 1 轮（初审）

### [维度11-01] NopCodeSymbolBizModel 18个方法未在 xbiz 中声明

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:50-247`
- **证据片段**:
  ```java
  @BizModel("NopCodeSymbol")
  public class NopCodeSymbolBizModel extends CrudBizModel<NopCodeSymbol> {
      @BizQuery public SymbolDTO getBySymbolId(...) { ... }
      // 17 more @BizQuery/@BizMutation/@BizLoader methods...
  ```
  xbiz 仅有空 `<actions/>`。
- **严重程度**: P3
- **现状**: 全部方法通过 Java 注解发现，xbiz 为空。Nop 平台注解驱动模式可正常工作。
- **风险**: 仅通过 xbiz 无法看到 API 表面积。
- **建议**: 可接受的注解驱动模式，无需修改。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度11-02] @BizLoader for SymbolDTO 无 xmeta prop 定义

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:101-127`
- **严重程度**: P3
- **现状**: usages 和 sourceCode 加载器注册在 SymbolDTO.class 而非实体类。xmeta 无对应 prop。
- **建议**: 如需 GraphQL 可发现性，可添加 prop 定义。当前运行时正常。
- **信心水平**: 可能
- **复核状态**: 未复核

### 合规项
- 全部 11 个实体有 xmeta（生成 + 手写扩展）
- dict 定义与 YAML 文件一致
- 字段权限控制合理（如 sourceCode published=false）
