# 维度 05：生成管线完整性 — nop-code 模块

## 第 1 轮（初审）

管线链 **model→codegen→dao→meta→service→web** 对所有 11 个实体结构完整闭合。所有 xgen 脚本存在且正确配置。

### [维度05-01] NopCodeSemanticEdge 缺少 i18n-en:displayName — 级联到空英文标签

- **文件**: `nop-code/model/nop-code.orm.xml:890-892`
- **证据片段**: 实体缺少 `i18n-en:displayName`，导致生成文件中 i18n 为 null/空。
- **严重程度**: P2
- **现状**: 10 个其他实体均有英文本地化，仅 SemanticEdge 缺失。
- **建议**: 添加 `i18n-en:displayName="Semantic Edge"` 然后重新生成。
- **信心水平**: 确定
- **误报排除**: 生成文件确认传播了空值。
- **复核状态**: 未复核

### [维度05-02] code/call_type dict 未在源模型中定义

- **文件**: `nop-code/model/nop-code.orm.xml:546`（引用）和 `22-87`（dicts 节）
- **证据片段**: 列引用 `code/call_type` 但 dicts 节无此定义，仅有独立 dict.yaml 文件。
- **严重程度**: P3
- **现状**: 与其他 6 个在源模型中定义的 dict 不一致。
- **建议**: 在源模型 `<dicts>` 节添加 `code/call_type` 定义。
- **信心水平**: 很可能
- **误报排除**: 其他 6 个 dict 在源模型中定义且有生成文件，仅 call_type 例外。
- **复核状态**: 未复核

### [维度05-03] nop-code-codegen pom.xml 缺少 classpathScope=test

- **文件**: `nop-code/nop-code-codegen/pom.xml:37-43`
- **证据片段**: exec-maven-plugin 未指定 `<classpathScope>test</classpathScope>`，而 meta 和 web 模块正确指定。
- **严重程度**: P3
- **现状**: NopCodeCodeGen.java 在 test scope 中，但 exec 插件使用默认 compile scope。
- **建议**: 添加 `<classpathScope>test</classpathScope>`。
- **信心水平**: 很可能
- **误报排除**: 2 个其他模块正确配置了 classpathScope=test。
- **复核状态**: 未复核

### [维度05-04] NopCodeSemanticEdge 缺少 ext:icon

- **文件**: `nop-code/model/nop-code.orm.xml:890-892`
- **严重程度**: P3
- **现状**: 10 个其他实体均有 ext:icon，SemanticEdge 使用默认图标。
- **建议**: 添加 `ext:icon="git-merge"` 或类似图标。
- **信心水平**: 确定
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 05-01 | P2 | nop-code.orm.xml:890 | SemanticEdge 缺少英文本地化级联 |
| 05-02 | P3 | nop-code.orm.xml:546 | call_type dict 不在源模型中 |
| 05-03 | P3 | codegen/pom.xml | exec-maven-plugin 缺少 classpathScope |
| 05-04 | P3 | nop-code.orm.xml:890 | SemanticEdge 缺少 ext:icon |
