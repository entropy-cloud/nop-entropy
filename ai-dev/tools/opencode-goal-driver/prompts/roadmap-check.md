# Roadmap Check Procedure

检查模块 {module} 的路线图，选出 **唯一一个** 最应该现在做的工作项。

## 步骤

1. 查找 ai-dev/design/ 下与 {module} 相关的 *roadmap*.md 文件
2. 读取路线图全文，特别关注 §2"当前状态"、§3"规划优先级指引"、§4"工作项清单"和 §5"技术债"
3. 找到所有标记为 ❌ 或 ⚠️ 的工作项
4. 对每个未完成项，用 grep/glob 检查代码库中是否已实际存在实现（排除 _gen 生成代码）
5. 按以下优先级从高到低选择 **唯一一个** 最紧迫的工作项：
   - P0 技术债（构建/加载失败类）
   - 前置层（Layer 0）阻塞项
   - Layer 1 核心接口
   - 更高层的扩展
   - 同层内选依赖最少的（被其他项依赖最多的优先）
6. 输出选择结果

## 输出格式

如果没有未实现项（所有 ❌ 都已实际实现）：
```
<ROADMAP_RESULT>complete</ROADMAP_RESULT>
```

如果有未实现项：
```
<ROADMAP_RESULT>pending</ROADMAP_RESULT>
<NEXT_ITEM id="工作项编号" layer="层级" priority="P0|P1|P2">选中理由和现状描述</NEXT_ITEM>
<ROADMAP_ITEMS><item id="编号" priority="P0|P1|P2|P3">所有未实现项摘要</item></ROADMAP_ITEMS>
```
