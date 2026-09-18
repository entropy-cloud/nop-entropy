# nop-rg Vision

**日期**：2026-09-18
**状态**：草案

---

## 产品定位

nop-rg 是 Nop 平台的高性能文件搜索工具，在 JVM 上提供等价于 ripgrep 的内容搜索（grep）和文件名模式匹配（glob）能力。

**核心价值主张**：为 Nop 生态提供纯 Java 的文件搜索基础设施，可用于 CI/CD 管线、IDE 集成、代码分析工具链，不依赖外部原生二进制。

## 成功标准

1. **标量搜索性能**：在典型代码库（10GB 以下）上，标量搜索吞吐量达到原生 ripgrep 的 50% 以上
2. **资源安全性**：所有内存映射资源通过 Arena 管理，搜索结束后无残留文件锁或堆外内存泄漏
3. **行为兼容**：默认尊重 `.gitignore`，输出格式与 `rg --json` 兼容
4. **可降级**：Vector 加速仅在 JDK 25+ 且显式启用（`--vector`）时生效，不可用时自动降级到标量搜索；`RegexSearcher` 作为正则匹配回退，功能完整可用

## 非目标（Non-Goals）

- **不追求 100% GNU grep 兼容**：不实现所有命令行选项
- **不试图超越原生 ripgrep**：目标是"足够好"而非"最快"
- **不提供 GUI**：纯 CLI + API
- **不替代 nop-search**：nop-rg 是文件系统扫描工具，nop-search 是索引搜索引擎，两者互补
- **不做在线索引**：FM-Index 是可选加速，不作为核心依赖

## 设计收敛路径

| 阶段 | 目标 | 判定标准 |
|------|------|---------|
| Phase 1 | 核心可用 | 标量搜索 + glob + .gitignore + CLI，可通过 `rg` 对比测试 |
| Phase 2 | 性能达标 | 大文件分块 + 并行搜索 + JMH 基准测试 |
| Phase 3 | 可选加速 | Vector API 加速（需显式启用，JDK 25+） |
| Phase 4 | 索引加速 | FM-Index 集成（可选加速，作为 nop-rg-core 可选依赖，不单独建模块；仅静态数据场景） |

## 关键约束

1. **Java 版本**：`nop-rg-core` 最低 JDK 22+（FFM API 正式版），pom.xml 直接指定 `maven.compiler.release=22`；nop-rg 通过 JDK 激活 profile 挂入根 reactor（同 `nop-utils` `java21-modules` 模式，JDK < 22 自动跳过），不强制升级项目全局 Java 版本
2. **零重量级依赖**：核心模块不依赖 Lucene、JGit 等大型库
3. **I/O 与计算分离**：文件遍历和内容搜索在不同并行层完成
