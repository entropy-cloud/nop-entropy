# 维度 03：API 表面积与契约一致性 — nop-code 模块

## 第 1 轮（初审）

### [维度03-01] I*Biz 接口缺少自定义 BizModel 方法声明

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/INopCodeIndexBiz.java:1-7`（及其他 I*Biz 接口）
- **证据片段**: 所有 11 个 I*Biz 接口只继承 ICrudBiz<T>，无自定义方法声明，但 BizModel 有大量自定义方法（NopCodeIndexBizModel 24 个）。
- **严重程度**: P2
- **现状**: 外部调用者无法通过接口类型发现和调用自定义 API。
- **建议**: 在 INopCodeIndexBiz、INopCodeSymbolBiz、INopCodeFileBiz 中声明自定义方法。
- **信心水平**: 确定
- **误报排除**: 运行时通过反射和注解发现，但这是 API 契约完整性问题。
- **复核状态**: 未复核

### [维度03-02] NopCodeFile xmeta delta 未完全限制 sourceCode 的可写性

- **文件**: `nop-code-meta/.../NopCodeFile/NopCodeFile.xmeta:4-6`
- **证据片段**: Delta 设置 `published="false" queryable="false"` 但未覆盖 `insertable` 和 `updatable`（从基类继承 true）。
- **严重程度**: P2
- **现状**: 通过标准 CRUD 操作路径可写入 sourceCode 字段。
- **建议**: 添加 `insertable="false" updatable="false"`。
- **信心水平**: 很可能
- **误报排除**: Delta merge 不覆盖未声明的属性。
- **复核状态**: 未复核

### [维度03-03] NopCodeFileBizModel 的 @BizLoader(forType) 计算字段无 xmeta 声明

- **文件**: `NopCodeFileBizModel.java:58-97`
- **严重程度**: P3
- **现状**: 4 个 BizLoader forType=CodeFileAnalysisResult.class 无 xmeta prop。运行时工作但 GraphQL schema 不含这些字段。
- **建议**: 可接受的设计权衡，考虑添加文档。
- **信心水平**: 很可能
- **误报排除**: 框架支持 forType 无需 xmeta，运行时正常。
- **复核状态**: 未复核

### [维度03-04] selectFieldsByQuery 返回 Map<String, Object>（框架 API）

- **文件**: `CodeIndexService.java:530-541`
- **严重程度**: P3（框架标准模式）
- **现状**: 使用 ORM 标准 API `selectFieldsByQuery`。
- **建议**: 无需修改。
- **复核状态**: 未复核

### [维度03-05] 自定义 findPage_symbols/findPage_files 绕过 CrudBizModel 标准 QueryBean 分页

- **文件**: `NopCodeSymbolBizModel.java:66-99`
- **严重程度**: P2
- **现状**: 自定义分页逻辑绕过了 xmeta queryable 字段过滤和框架排序支持。
- **建议**: 考虑使用 CrudBizModel 标准 QueryBean 分页。
- **信心水平**: 很可能
- **误报排除**: 数据模型不直接通过 ORM entity 分页，是功能需求导致的设计选择。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 03-01 | P2 | INopCode*Biz.java | I*Biz 接口缺少自定义方法声明 |
| 03-02 | P2 | NopCodeFile.xmeta | sourceCode 可写性未完全限制 |
| 03-03 | P3 | NopCodeFileBizModel.java | BizLoader forType 无 xmeta |
| 03-04 | P3 | CodeIndexService.java | Map<String,Object> 框架API |
| 03-05 | P2 | NopCodeSymbolBizModel.java | 自定义分页绕过框架标准 |
