# Log 编写指南

> Status: active guide
> Created: 2026-05-17

## 定位

`ai-dev/logs/` 是执行过程的**证据层**，不是 source of truth。

日志与 plans 的关系：
- **Plan** 定义"做什么、怎么验证"
- **Log** 记录"实际做了什么、关键事实、偏差、验证结果"
- **Closure audit** 看 plan 的 checklist + log 的验证证据

## 写什么

每条日志记录应该包含：

1. **关联的 Plan**（如果有）— 明确写 Plan 编号和路径
2. **做了什么** — 关键改动的事实摘要（改了什么、影响范围、量化数据）
3. **验证结果** — 具体的测试命令和结果
4. **偏差**（如果有）— 实际做法与计划不同的地方及原因
5. **doc-sync 裁定** — 明确写"更新了 XX 文档"或"No owner-doc update required"

## 条目格式

```markdown
### YYYY-MM-DD（时间段或主题）

- Plan NN 完成：<<关键改动的事实摘要，不是 plan phase 的复述>>
- 偏差：<<与计划不同的地方及原因>>
- 验证：`./mvnw test -pl nop-job` 通过
- Commit: `abc1234`
- Doc-sync: `docs-for-ai/XX.md` 已更新 / No owner-doc update required
```

## 不写什么

- 主观评价（"降维打击"、"远超"）
- 重复 design 文档的内容（日志只链接，不复制）
- **按 plan 的 phase 结构逐条复述**（日志的组织方式是"实际发生了什么"，不是"plan 第几步做了什么"）
- 模糊的验证描述（"测试通过" → 要写具体命令和结果）

## 规则

1. 一天一个文件，路径：`ai-dev/logs/{year}/{month}-{day}.md`
2. 新条目追加在文件顶部（倒序）
3. 保持简短，链接到 plans / design / 源码路径
4. 日志是 append-only 历史，不回写旧条目
5. 有 plan 时，日志记录**执行的关键事实**（改了什么、影响多大、有无偏差），不是 plan 的影子。plan 的 checklist 在 plan 文件里，日志不再按 phase 结构重列
6. 无 plan 时（ad-hoc 分析、探索、bug 修复），日志就是主要记录，可以写得更详细
