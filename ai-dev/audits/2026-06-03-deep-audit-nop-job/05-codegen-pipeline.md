# 维度 05：代码生成管线审查

## 当前实体管线状态

当前 3 个活跃实体（NopJobSchedule、NopJobFire、NopJobTask）的生成管线完全闭环且正确：
- ORM 模型 → xmeta → xbiz → BizModel → IBiz → GraphQL API 链路完整
- 生成文件与手动 Delta 文件正确分离

## 发现

### [05-01] P2 — 4 个陈旧 web 页面目录引用不存在的 xmeta 路径

- **文件**: web/pages/ 下的 NopJobDefinition、NopJobInstance、NopJobInstanceHis、NopJobAssignment 目录
- **现状**: 这 4 个页面目录是之前模型重构的遗留产物，它们引用的 xmeta 路径已不存在（对应实体已从 ORM 模型中移除）。如果通过 URL 直接访问这些页面，将导致运行时错误。
- **影响**: 不影响正常功能使用（因为这些页面不会出现在导航菜单中），但如果有人手动拼 URL 访问，会出现错误。
- **建议**: 删除这 4 个陈旧的页面目录。

### [05-02] P3 — 5 个陈旧模板文件引用已移除的实体

- **文件**: nop-job-meta/_templates/ 下的 _NopJobDefinition.json、_NopJobInstance.json、_NopJobInstanceHis.json、_NopJobAssignment.json、_NopJobPlan.json
- **现状**: 这 5 个代码生成模板文件引用了已从 ORM 模型中移除的实体。
- **影响**: 不影响当前构建和运行，但会在重新执行代码生成时产生困惑或错误。
- **建议**: 删除这些陈旧的模板文件。
