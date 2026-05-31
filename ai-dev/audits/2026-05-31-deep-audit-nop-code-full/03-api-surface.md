# 审核维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] I*Biz 接口全部为空壳，未声明任何自定义方法

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/INopCodeIndexBiz.java:8-9`
- **证据片段**:
  ```java
  public interface INopCodeIndexBiz extends ICrudBiz<NopCodeIndex>{
  }
  ```
  对比 BizModel 有 22 个自定义方法
- **严重程度**: P1
- **现状**: 11 个 I*Biz 接口均只继承 ICrudBiz<Entity>，未声明 BizModel 中的任何自定义方法。
- **风险**: 接口无法作为类型安全契约使用。外部调用者无法通过接口获知可用操作。
- **建议**: 至少补全 NopCodeIndexBiz 和 NopCodeSymbolBiz 的方法签名。
- **信心水平**: 90%
- **误报排除**: Nop 平台通过反射绑定方法，运行时不受影响。但接口契约形同虚设。
- **复核状态**: 未复核

### [维度03-02] NopCodeIndexBizModel 中大量方法未被任何前端或测试调用

- **严重程度**: P3
- **现状**: 16+ 个公开 API 方法无前端引用和测试覆盖。
- **建议**: 至少为每个公开 API 添加基础集成测试。
- **复核状态**: 未复核
