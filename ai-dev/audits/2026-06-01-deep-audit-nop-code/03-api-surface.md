# 维度03：API 表面积与契约一致性 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度03-01] I*Biz 接口未收敛自定义方法契约 -- 42 个自定义方法无接口声明

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/INopCodeIndexBiz.java` 等全部 11 个接口
- **证据片段**:
  ```java
  public interface INopCodeIndexBiz extends ICrudBiz<NopCodeIndex> {
      // 空接口 -- 无任何自定义方法
  }
  ```
  对比 NopCodeIndexBizModel 有 25 个自定义方法，NopCodeSymbolBizModel 有 17 个自定义方法。
- **严重程度**: P2
- **现状**: 所有 11 个 I*Biz 接口仅扩展 `ICrudBiz<Entity>`，无任何自定义方法声明。BizModel 的自定义方法无法通过接口类型调用。
- **风险**: (1) 无法通过接口实现跨模块类型安全调用。(2) 违反"BizModel 实现 I*Biz 接口"的契约模式。(3) 重构时无法通过接口发现所有调用点。
- **建议**: 将核心方法签名添加到对应的 I*Biz 接口。至少包括 `NopCodeIndexBizModel` 和 `NopCodeSymbolBizModel` 的公共 API。
- **信心水平**: 90%
- **误报排除**: 这不是 CrudBizModel 标准继承模式的问题（基础 CRUD 已通过 ICrudBiz 覆盖），而是自定义方法缺少接口契约。
- **复核状态**: 未复核

### [维度03-02] xmeta 未覆盖自定义 API 字段

- **文件**: `nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeIndex/NopCodeIndex.xmeta`
- **证据片段**:
  ```xml
  <meta x:extends="_NopCodeIndex.xmeta">
      <props/>
  </meta>
  ```
- **严重程度**: P3
- **现状**: `NopCodeIndex.xmeta` 的 `<props/>` 为空，没有为 18 个自定义 @BizQuery 方法定义任何字段映射。自定义方法的返回类型依赖 Java DTO 序列化。
- **风险**: 缺少 xmeta 元数据限制了平台元数据驱动能力（权限控制、前端代码生成、API 文档）。
- **建议**: 在 xmeta 手写层为关键方法添加对应 prop。
- **信心水平**: 85%
- **误报排除**: Nop 平台中 @BizQuery/@BizMutation 通过 BizProxyFactoryBean 自动暴露，功能上可正常工作。
- **复核状态**: 未复核
