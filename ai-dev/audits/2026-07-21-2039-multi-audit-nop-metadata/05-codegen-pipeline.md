# 维度05：生成管线完整性 — 第1轮（初审）

> 审计模块: nop-metadata

## 发现清单

### [维度05-01] gen-orm.xgen 缺少标准第三步 (orm-model 模板)

- **文件**: `nop-metadata/nop-metadata-codegen/postcompile/gen-orm.xgen:1-7`
- **证据片段**:
  ```xml
  <c:script>
  codeGenerator.withTargetDir("../").renderModel(
      '../../model/nop-metadata.orm.xml','/nop/templates/orm', '/',$scope);
  codeGenerator.withTargetDir("../nop-metadata-dao/src/main/java").renderModel(
      '../../nop-metadata-dao/src/main/resources/_vfs/nop/metadata/orm/app.orm.xml',
      '/nop/templates/orm-entity','/',$scope);
  </c:script>
  ```
- **严重程度**: P3
- **现状**: 只包含 2 步（/orm + /orm-entity），缺少标准管线的第 3 步 `renderModel(app.orm.xml, '/nop/templates/orm-model', ...)`。对比 `nop-job-codegen` 明确包含该步骤。
- **风险**: 缺少模型派生资源，在模型热加载验证等场景下可能出问题。
- **建议**: 对齐标准管线，追加 orm-model 第 3 步。
- **信心水平**: 确定
- **误报排除**: 对比 nop-job、nop-auth 等参考模块均包含此步骤。
- **复核状态**: 未复核

### [维度05-02] gen-crud-api.xgen 被注释禁用

- **文件**: `nop-metadata/nop-metadata-meta/postcompile/gen-crud-api.xgen:1-10`
- **严重程度**: P3（信息性）
- **现状**: CRUD API 代码生成默认禁用。设计决策，不是疏忽。
- **风险**: 未来需要为 `nop-metadata-api` 提供强类型 RPC 接口时需补齐。
- **建议**: 维持当前状态。当 api 模块需要填充时取消注释并调整 targetDir。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度05-03] gen-meta.xgen 引用路径正确但应注意 phase 时序

- **文件**: `nop-metadata/nop-metadata-meta/precompile/gen-meta.xgen:1-4`
- **严重程度**: P3（信息性）
- **现状**: 引用 `app.orm.xml`（运行时 delta 汇聚文件），位于 precompile 阶段。
- **风险**: 如果 `_app.orm.xml` 尚未生成（codegen 未执行），precompile 阶段会报错。
- **建议**: 添加注释说明对 nop-metadata-dao 的构建顺序依赖。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度05-04] 所有 39 个实体在各层间完全一致（正向）

- **严重程度**: 无问题
- **现状**: 39 实体在各层（model/entity/xmeta/xbiz/page/BizModel/I*Biz）数量完全一致。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度05-05] _module 文件为空

- **文件**: `nop-metadata/nop-metadata-dao/src/main/resources/_vfs/nop/metadata/_module`
- **严重程度**: P3
- **现状**: `_module` 文件（0 字节）被创建但内容为空。
- **风险**: 可能影响跨模块资源查找。但其他业务模块（nop-job）的 `_module` 也为空，可能是平台级约定。
- **建议**: 检查是否需要填充模块 ID 或版本信息。
- **信心水平**: 有趣的猜测
- **复核状态**: 未复核

### [维度05-06] gen-page.xgen 和 precompile2/gen-i18n.xgen 管线完整（正向）

- **严重程度**: 无问题
- **现状**: web 模块双阶段生成管线正确配置。
- **复核状态**: 未复核

## 总结

生成管线整体通过，39 个实体在每层数量完全对齐。主要改进项：gen-orm.xgen 缺少 orm-model 第 3 步（P3）。
