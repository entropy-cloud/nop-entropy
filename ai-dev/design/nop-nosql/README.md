# Nop NoSQL 设计文档

> Status: active
> Created: 2026-05-20
> Updated: 2026-05-20

## 定位

nop-nosql 是 Nop 平台的 NoSQL 数据访问层，提供面向业务语义的渐进式 NoSQL 抽象。

核心设计思想：**不针对单一 Redis 后端，提供 NoSQL 体系的渐进式封装，提供具有业务语义的函数，不强调暴露后端的选项和能力。**

应用代码通过队列、限流器、锁、排行榜等业务概念与 NoSQL 交互，而非直接调用 Redis 数据结构命令。

本目录记录 nop-nosql 的架构决策、模块边界、核心设计约束和使用契约。

## 设计文档结构

| 文档 | 职责 | 状态 |
|------|------|------|
| `architecture.md` | 整体架构：三层模型（业务模式/原语/驱动）、6 个业务模式接口设计、模块划分、实现状态 | active |

## 阅读顺序

1. `architecture.md` §1-2 — 理解三层模型和设计哲学
2. `architecture.md` §4 — 理解 6 个业务模式接口的具体设计（Queue/Lock/RateLimiter/Ranking/Counter/SessionStore）
3. `architecture.md` §5-6 — 理解 INosqlService 入口设计和实现状态

## 快速心智模型

```
service = injected INosqlService

service.queue("email-queue").enqueue(task)        // 队列
service.lock("order:123").tryLock(30s)             // 锁
service.rateLimiter("api:"+userId, config).tryAcquire(1)  // 限流
service.ranking("game:scores").getTopN(10)         // 排行榜
service.counter("page:views").increment(1)          // 计数器
service.sessionStore("sess").set(id, data, 30min)   // 会话
```

## 模块成熟度

当前模块处于**功能完整阶段**：
- 驱动层：Lettuce 连接管理基本可用，仅支持单机直连
- 原语层：KV 操作完整；Hash/List/Set/ZSet 操作已实现，hashOps/listOps/setOps/zSetOps 返回可用实现
- 业务模式层：6 个业务模式（Queue/Lock/RateLimiter/Ranking/Counter/SessionStore）接口及 Lettuce 实现已完成
- Lua 脚本：5 个脚本已注册到 RedisScripts，其中 remove_if_match、rate_limit、put_if_absent_or_match 已被代码引用
- 已知债务：forEachEntry/forEachEntryAsync 未实现（SCAN），getMessageService() 返回 null
- 无 IoC beans.xml 注册文件

## 与其他 Nop 模块的关系

- nop-nosql-core 依赖 nop-commons（`IAsyncMap` 接口）、nop-core（PrefixEncodeHelper 序列化）、nop-dao
- nop-nosql-lettuce 依赖 lettuce-core 和 nop-nosql-core
- `NosqlCache` 适配了 Nop 平台的 `ICache` 接口，可作为缓存后端
- `INosqlService.getMessageService()` 返回 `IMessageService`，与 Nop 消息体系集成（Pub/Sub，未实现）
