# nop-task DeepWiki

> 目标：nop-task · 结构契约：[PLAN.md](./PLAN.md) · 本文件由 gen-wiki-meta.mjs 生成，勿手改

```mermaid
mindmap
  root((nop-task))
    指南
      nop-task 总览：DSL 任务流编排引擎
      快速上手
      术语表
      阅读指南
      架构与数据流
    机制
      任务流执行管线：从 task.xml 到步骤输出
      状态持久化与恢复：挂起如何变成重入
    模块
      核心引擎与步骤抽象
      服务面与持久化对接
    主题
      错误模型：TaskErrors 体系
```

> 快照：nop-task @ ea3e35e6d0 · 2026-09-29

## 指南

- [nop-task 总览：DSL 任务流编排引擎](overview.md)
- [快速上手](quickstart.md)
- [术语表](glossary.md)
- [阅读指南](reading-guide.md)
- [架构与数据流](architecture.md)

## 机制

- [任务流执行管线：从 task.xml 到步骤输出](flows/task-execution.md)
- [状态持久化与恢复：挂起如何变成重入](flows/state-and-recovery.md)

## 模块

- [核心引擎与步骤抽象](modules/task-core.md)
- [服务面与持久化对接](modules/task-service-dao.md)

## 主题

- [错误模型：TaskErrors 体系](topics/error-model.md)
