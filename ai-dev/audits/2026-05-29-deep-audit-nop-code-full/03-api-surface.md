# 维度03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] I*Biz 接口未声明 BizModel 中的任何自定义方法

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/INopCodeIndexBiz.java`
- **行号**: L8-9
- **证据片段**:
  ```java
  public interface INopCodeIndexBiz extends ICrudBiz<NopCodeIndex>{
  }
  ```
- **严重程度**: P2
- **现状**: 所有 11 个 I*Biz 接口均仅继承 ICrudBiz<Entity>，无自定义方法。NopCodeIndexBizModel 有 20+ 自定义 @BizQuery/@BizMutation，NopCodeSymbolBizModel 有 15 自定义方法，均未在接口中声明。
- **风险**: 接口契约形同虚设，外部模块无法通过接口了解可用操作。
- **建议**: 将 NopCodeIndexBizModel 和 NopCodeSymbolBizModel 的公开方法签名同步到对应 I*Biz 接口。
- **信心水平**: 确定
- **误报排除**: 空 CRUD BizModel 的空壳接口可接受，但有自定义方法的 BizModel 应同步接口。
- **复核状态**: 未复核

### [维度03-02] NopCodeFileBizModel 直接暴露 core 模型类型

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java`
- **行号**: L34-58
- **证据片段**:
  ```java
  @BizQuery
  public CodeFileAnalysisResult getByPath(...) {
      return codeIndexService.getFile(indexId, filePath);
  }
  ```
- **严重程度**: P3
- **现状**: getByPath() 返回 CodeFileAnalysisResult（core 层 POJO），包含 sourceCode 字段。
- **风险**: CodeFileAnalysisResult 包含可能很大的 sourceCode 字段。但 @BizLoader 的 GraphQL selection 机制可以控制字段暴露。
- **建议**: 保持可接受，但建议使用专门的 DTO 替代。
- **信心水平**: 很可能
- **误报排除**: Nop 平台中 BizModel 返回非 ORM 实体对象在某些场景下是合理的。
- **复核状态**: 未复核

### [维度03-03] IncrementalStatus 作为内部静态类直接暴露到 API

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: L247-302
- **证据片段**:
  ```java
  @BizQuery
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) { ... }
  
  public static class IncrementalStatus { ... }
  ```
- **严重程度**: P3
- **现状**: 手写 POJO 内部类作为 @BizQuery 返回值，不在 api/dto 包中。
- **建议**: 移至 api/dto 包并纳入 DTO 命名规范。
- **信心水平**: 很可能
- **误报排除**: 功能上可行，但不利于外部消费者发现。
- **复核状态**: 未复核

## 通过项

1. 无 Map<String, Object> 反模式
2. @BizQuery/@BizMutation 注解使用正确
3. xmeta 字段权限控制合理（sourceCode published=false）
