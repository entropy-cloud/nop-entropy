# nop-ioc 设计文档索引

> 本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

记录 `nop-ioc` 容器的架构决策与使用契约。`nop-ioc` 是 Nop 平台的 IoC 容器实现，本目录只记录**架构决策层面**的内容（选了什么方案、为什么、拒绝了什么），不记录代码层面的类签名与实现细节——源码是代码层面的唯一事实。

## 文档结构

| 文档 | 层级 | 职责 |
|------|------|------|
| `bean-dependency-semantics.md` | Architecture Baseline | Bean 间三类依赖（ref / depends-on / ioc:before-after）的语义契约、依赖图与运行时强制创建的职责边界 |
| `bean-init-concurrency-locking.md` | Architecture Decision | 并发初始化同一 bean 生命周期时的加锁纪律（owner 线程 + wait/notify 状态机、锁外回调），修复 ctx↔P 锁序反转死锁 |

## 阅读顺序

1. 必读：`bean-dependency-semantics.md` —— 依赖语义是理解容器初始化行为的基础。
2. 必读：`bean-init-concurrency-locking.md` —— 并发初始化加锁纪律，修复 2026-08-20 现场死锁（Plan 343）。
3. 按需：源码锚点见各文档末尾"源码锚点"段落，可直接跳转到 `nop-core-framework/nop-ioc/` 对应实现。

本子系统随设计积累逐步补充 Vision 层与更多专题文档。
