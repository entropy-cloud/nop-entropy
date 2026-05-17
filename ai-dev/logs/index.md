# AI 开发日志

`ai-dev/logs/` 是执行过程的证据层。Plan 定义"做什么"，Log 记录"实际做了什么、验证结果"。

它不是 source of truth。规范性文档在 `docs-for-ai/` 和 `ai-dev/design/`。

编写规范见 `ai-dev/logs/00-log-writing-guide.md`。

## 结构

```text
ai-dev/logs/
├── index.md
├── 2026/
│   ├── 05-11.md
│   ├── 05-05.md
│   ├── 05-03.md
│   ├── 05-02.md
│   ├── 04-22.md
│   ├── 04-19.md
│   ├── 04-14.md
│   ├── 04-10.md
│   └── 04-09.md
└── 2027/
```

**路径约定**：`ai-dev/logs/{year}/{month}-{day}.md`

## 写作规则

1. 一天一个文件。
2. 新条目追加在文件顶部，按倒序记录。
3. 保持简短，优先链接 `docs-for-ai/`、`ai-dev/plans/` 或相关代码路径。
4. 日志只记录短期上下文、关键决策和下一步，不复制 canonical doc 内容。
5. 如果变更已经改变默认规则，先更新 `docs-for-ai/`，再写日志。

## 条目模板

```markdown
# AI 开发日志 — YYYY-MM-DD

### YYYY-MM-DD

- 做了什么。
- 相关路径：`docs-for-ai/...`、`ai-dev/plans/...`、`module/...`
- 关键决策：...
- 下一步：...
```

## 索引（倒序）

### 2026-05

- [05-17](2026/05-17.md) — 吸引子层治理：`ai-dev/design/README.md` 新增（层级+precedence model）；对比 chaos-flux PPT 方法论分析；Plan 15 完成+Plan 16 迁移+Plan 17 新建；文档治理（plan guide 同步、design guide 新建、AGENTS.md 更新）
- [05-11](2026/05-11.md) — Plan 14 nop-job 质量优化：TimeoutChecker N+1 批量预加载、Planner 锁冲突 Micrometer counter + DEBUG 日志、ErrorCode 规范化（5 个新定义）、LocalJobScheduler @Deprecated 标记
- [05-05](2026/05-05.md) — SpotBugs findings 详细分析文档：nop-kernel 6 子模块 40 个 findings 源码级验证（3 true positive / 2 false positive / 35 design choice）
- [05-03](2026/05-03.md) — SpotBugs 排除规则扩展、PMD 升级 3.28.0、nop-kernel qa profile、设计文档更新
- [05-02](2026/05-02.md) — nop-code 多语言代码索引设计：nop-java-parser 精简为适配器，新增 nop-code-core 通用模型+分析算法，新增三个语言适配器子模块（Java/Python/TypeScript），tree-sitter NG 确认可用

### 2026-04

- [04-22](2026/04-22.md) — relation writeMode 元数据链路打通：补齐 `schema.xdef`/`obj-schema.xdef` 共享定义、刷新 `nop-xlang` 生成结果，并为 `nop-biz` 增加 writeMode 校验测试
- [04-19](2026/04-19.md) — relation writeMode 设计收敛：以 ObjMeta 读写对称性和同构 relation payload 为核心，确认 `OrmEntityCopier` 为 prop-level routing 主落点，去除 action-style relation op 表述
- [04-14](2026/04-14.md) — 仅在 `nop-ai-agent/docs/` 内拆分 Agent 设计草稿，保留原大文档不动，收敛为总览 / 运行时 / 扩展 / 可靠性 / 路线图五篇专题文档
- [04-10](2026/04-10.md) — 复核 `docs-for-ai` 审查意见，修正路由缺口、DTO 规则、CRUD hook / QueryBean 指南，并补充 XLang/XPL 基础文档
- [04-09](2026/04-09.md) — `docs-for-ai` 重构收口、自动维护规则、`ai-dev/logs` 机制建立
