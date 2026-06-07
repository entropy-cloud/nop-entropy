# 维度 06：Delta 定制合规性

## 第 1 轮（初审）

未发现问题。

### 检查范围

在 nop-code 全模块中搜索 `_vfs/_delta/` 目录——未发现。所有定制均通过同目录下的非下划线文件覆盖下划线生成文件，是标准 Nop Delta 定制模式。

### x:extends 使用检查

| 文件 | x:extends 目标 | 正确性 |
|------|---------------|--------|
| NopCodeSymbol.xmeta | _NopCodeSymbol.xmeta | 正确 |
| NopCodeFile.xmeta | _NopCodeFile.xmeta | 正确 |
| NopCodeIndex.xmeta | _NopCodeIndex.xmeta | 正确 |
| 所有 NopCodeXxx.xbiz | _NopCodeXxx.xbiz | 正确 |
| _service.beans.xml | /nop/biz/defaults/service-base.beans.xml | 正确 |

### x:override 使用检查

- NopCodeSymbol.xmeta 中 kind prop 使用 x:override="merge" — 语义正确
- 其余手写 Delta 文件均为追加定制 — 语义正确

### Delta 与原始文件冲突

所有手写 xmeta/xbiz 文件内容简洁（5-14 行），仅追加或合并特定属性，无冲突。
