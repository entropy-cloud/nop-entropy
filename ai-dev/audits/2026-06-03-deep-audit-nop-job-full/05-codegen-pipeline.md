# 维度 05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] 4 个幽灵 Web 页面目录引用不存在的 xmeta

- **文件**: `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobAssignment/`、`NopJobDefinition/`、`NopJobInstance/`、`NopJobInstanceHis/`
- **证据片段**:
  ```xml
  <!-- NopJobAssignment/_gen/_NopJobAssignment.view.xml -->
  <view ... bizObjName="NopJobAssignment" ...>
    <objMeta>/nop/job/model/NopJobAssignment/NopJobAssignment.xmeta</objMeta>
  </view>
  ```
- **严重程度**: P2
- **现状**: nop-job-web 的 pages 目录中存在 4 个幽灵实体的完整页面产物。这些页面引用的 xmeta 路径在 nop-job-meta 中不存在（当前 ORM 模型仅定义了 3 个实体）。这些是从旧版 ORM 模型遗留的未清理产物。
- **风险**: 如果有人通过直接 URL 访问这些页面路径将因 xmeta 缺失而报错；增加维护者认知负担；代码搜索时产生噪音。
- **建议**: 删除 4 个幽灵页面目录及其全部内容。
- **信心水平**: 高
- **误报排除**: gen-page.xgen 脚本只会为当前存在的 xmeta 生成页面，重新生成不会产出这 4 个目录。它们是旧模型遗留产物。
- **复核状态**: 未复核

## 无问题确认清单

| 检查步骤 | 结论 |
|---------|------|
| 源模型文件 | 存在且格式正确，3 个实体 |
| codegen 生成脚本 | 三步链路路径均正确 |
| dao 生成产物 | _app.orm.xml、Entity Java、I*Biz 接口均与源模型一致 |
| meta 生成脚本 | precompile/gen-meta.xgen 和 postcompile/gen-i18n.xgen 正确 |
| meta 生成产物 | 3 个 xmeta（生成+保留层）正确 |
| web 生成脚本 | precompile/gen-page.xgen 和 precompile2/gen-i18n.xgen 正确 |
| service xbiz 与 BizModel 对应 | 全部 8 个自定义 action 正确对应 |
| POM Maven 插件配置 | exec-maven-plugin 配置正确，构建顺序由 reactor 自动调整 |
