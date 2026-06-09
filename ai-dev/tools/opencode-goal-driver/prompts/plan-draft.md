# Plan Draft Procedure

为模块 {module} 拟定一个开发计划。每个计划只包含 **一个** 工作项。

## 来源判断

- 如果上一步 ROADMAP_CHECK 提供了 <NEXT_ITEM>，基于该工作项拟定计划
- 如果有审计发现（ai-dev/audits/），基于审计发现拟定
- 两者都有时优先 NEXT_ITEM
- 都没有则不创建计划

## 硬性要求

1. 阅读并遵循 ai-dev/plans/00-plan-authoring-and-execution-guide.md
2. 每个计划 **只包含一个工作项**，不要打包多个
3. 计划文件名格式：{YYYY}-{MM}-{DD}-{NNN}-{slug}.md，放在 ai-dev/plans/ 下
4. 文件开头必须包含以下格式（check-plan-status.mjs 依赖此格式解析状态）：

```markdown
> **Plan Status**: active
> **Module**: {module}
> **Work Item**: L0-1 (或对应的工作项编号)

# 计划标题
```

5. 合理划分 Phase（可执行的、有明确 Exit Criteria 的增量）
6. 明确写出 Exit Criteria（可验证的条件，如"文件存在"、"测试通过"、"编译通过"）

## 输出格式

```
<PLAN_RESULT>created</PLAN_RESULT>
```
或
```
<PLAN_RESULT>none</PLAN_RESULT>
```
