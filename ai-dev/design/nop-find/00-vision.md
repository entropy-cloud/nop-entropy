# nop-find Vision

**日期**：2026-10-10
**状态**：定稿（对应计划 `ai-dev/plans/2379-nop-find-tool.md`）

---

## 产品定位

nop-find 是 Nop 平台的独立模糊文件查找工具，在 JVM 上提供对标 zcode `@` 面板 / VSCode Ctrl+P / fzf file mode 的文件名/路径模糊搜索能力。

**核心价值主张**：为 Nop 生态（CLI、IDE 集成、AI agent 工具链）提供纯 Java 的"按部分名字找文件"基础设施，不依赖外部原生二进制（fzf/fd），打分算法经 zcode 大规模 monorepo 实测验证。

## 成功标准

1. **打分正确性**：三级打分（前缀 < 子串 < 子序列）排序符合规格；同输入同输出（确定性）
2. **Top-K 性能**：10 万文件候选、K=100、典型短 query 下，端到端（含并行遍历）< 500ms
3. **行为一致**：默认尊重 `.gitignore`、默认大小写不敏感、退出码契约与 nop-rg 一致（命中 0 / 未命中 1 / 错误 2）
4. **CLI 可用**：独立 `nop-find` 命令可用；`nop-cli find` 子命令透传可用
5. **零重量级依赖**：核心模块不引入 Lucene/JGit/fzf 等外部搜索库

## 非目标（Non-Goals）

- **不做内容搜索**：不读文件内容，只匹配文件名和路径（内容搜索归 nop-rg）
- **不做预构建索引**：实时扫描文件系统（索引/失效机制归 nop-search 场景）
- **不追求 fzf 全功能**：不做交互式 TUI、不做历史记录/多选；聚焦"给定 query 返回 top-K"的库与 CLI 能力
- **不追求 100% fzf/zcode 打分兼容**：打分公式是参考 zcode 思路自实现，数值不需要与 zcode 逐位一致，只需满足三级排序语义与性能
- **不做 GUI**：纯 CLI + API

## 设计收敛路径

| 阶段 | 目标 | 判定标准 |
|------|------|---------|
| Phase 1 | 核心可用 | 打分 + top-K + 遍历 + CLI 端到端，确定性测试通过 |
| Phase 2 | 性能达标 | JMH 基准固化；并行路径与单线程结果一致 |
| Phase 3 | 生态集成 | `nop-cli find` 透传可用；与 nop-rg 共享 walker 契约文档化 |

## 关键约束

1. **Java 版本**：复用 nop-rg-core 的 `ParallelFileWalker`（JDK 22+ 门控），nop-find 与 nop-rg 同 JDK 基线（`maven.compiler.release=22`，挂入根 reactor 走 `java22-modules` profile，JDK < 22 自动跳过）。打分算法本身为纯字符串操作，无 FFM 依赖，未来可独立下沉。
2. **零重量级依赖**：核心模块不依赖 Lucene、JGit、fzf 等大型库
3. **打分无状态**：`FuzzyFileScorer` 为纯函数；本实现无预建索引（实时扫描），预归一化落在遍历阶段（候选三字段各折叠一次），打分热路径不做 `toLowerCase`
4. **遍历语义对齐 nop-rg**：gitignore、隐藏文件、符号链接、错误快速失败契约与 `ParallelFileWalker` 一致
