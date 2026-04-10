# 文档维护规则

本目录不是开发入口。开发入口仍然是 `docs-for-ai/INDEX.md`。

## 维护目标

`docs-for-ai/` 的目标不是收录一切知识，而是保证：

1. AI 能在最短路径内命中正确文档。
2. 普通开发 AI 只靠 `docs-for-ai/` 就能做出正确默认判断。
3. 示例和边界材料不会反向污染当前默认做法。

## 开发 AI 与维护 AI 的边界

1. 普通开发 AI 只读 `docs-for-ai/`。
2. 普通开发 AI 不读 `docs/`。
3. 普通开发 AI 不读其他非 `docs-for-ai/` 文档目录。
4. 普通开发 AI 一般也不直接读源码；如果 `docs-for-ai/` 仍不足以回答问题，优先用 `04-reference/` 给出的锚点做 LSP / definition lookup。
5. 只有文档维护任务，或阻塞性例外场景，才允许少量直接源码阅读。

## 当前要求

1. `docs-for-ai/` 才是当前唯一运行时信息源。
2. `docs-for-ai/` 只描述当前项目的最新默认规则与高频任务路径。
3. 主干文档不写迁移说明、审计说明或历史对比说明。

## 当前目录角色

| 目录 | 角色 |
|------|------|
| `00-start-here/` | AI 默认规则与总入口补充 |
| `01-repo-map/` | 仓库结构与定位表 |
| `02-core-guides/` | Canonical Pattern，回答“默认应该怎么做” |
| `03-runbooks/` | Runbook，回答“这件事具体怎么做” |
| `04-reference/` | Reference，回答“实现锚点和安全速查是什么” |
| `90-maintenance/` | 治理规则 |

## 配套辅助目录

| 目录 | 角色 |
|------|------|
| `ai-dev/plans/` | 临时设计稿与执行计划，不替代 `docs-for-ai/` |
| `ai-dev/logs/` | 每日开发日志，记录短期上下文与关键决策，不替代 `docs-for-ai/` |

## 自动维护触发条件

日常开发任务中，出现以下任一情况时，必须在同一任务内顺手维护 `docs-for-ai/`：

1. 当前依赖的 `docs-for-ai/` 规则与实际实现不一致。
2. 为了完成任务，你不得不用 LSP / definition lookup 才确认了一个文档没写清楚的高频规则。
3. 本次代码改动改变了默认做法、高频入口、文件位置、推荐 API、常见坑或边界判断。
4. 当前高频任务在 `docs-for-ai/` 中没有合适 owner doc，只能靠临时口头解释完成。

默认不要把“文档修复”留到以后；如果当前任务已经暴露出文档问题，就应在本次任务中一起修正。

## 维护原则

1. 新文档默认只引用 `docs-for-ai/` 内部文档。
2. 不要让 `docs/`、示例、原始代码片段或其他非 `docs-for-ai/` 材料承担默认规范角色。
3. 同一主题尽量只有一个 canonical owner；其他文档引用它，不要重复定义。
4. 如果维护任务中发现文档与实现不一致，先做最小必要的 LSP / 定义确认；只有不足时才读少量源码，并回改文档。
5. 专题过于冷门或只适合局部模块时，优先不放进主导航，保持文档主树聚焦当前高频任务。

## 开发日志

`ai-dev/logs/` 是每日开发日志目录，不是 source of truth。source of truth 仍然是 `docs-for-ai/`。

### 什么时候必须记日志

以下情况完成后，必须追加当天日志：

1. significant code change。
2. significant `docs-for-ai/` 修正或补文档。
3. 关键默认规则被澄清、收紧或替换。

### 日志规则

1. 路径约定：`ai-dev/logs/{year}/{month}-{day}.md`
2. 一天一个文件。
3. 新条目追加在文件顶部，按倒序记录。
4. 日志只写短上下文、关键决策、相关路径和下一步，不复制 canonical doc 内容。
5. 如果当天第一次建日志文件，还要更新 `ai-dev/logs/index.md`。

### 日志格式

```markdown
# AI 开发日志 — YYYY-MM-DD

### YYYY-MM-DD

- 做了什么。
- 相关路径：`docs-for-ai/...`、`ai-dev/plans/...`、`module/...`
- 关键决策：...
- 下一步：...
```

## 新增或修改文档前的检查项

- 这篇文档的角色是否清晰。
- 是否已经有对应 canonical doc，可以补章节而不是新开平行文档。
- 是否会把边界场景误写成默认模板。
- 是否能在 `../04-reference/source-anchors.md` 找到或补上实现锚点。
- 是否引入了与当前默认做法无关的边界材料或说明。

## 高风险模式巡检

建议定期搜索：

```bash
rg -n "dao\(\)\.(getEntityById|find(All|Page|First)?ByQuery|saveEntity|updateEntity|deleteEntity)" docs-for-ai
rg -n "@Inject\s+private|@Value\(" docs-for-ai
rg -n "gen-service\.xgen|gen-web\.xgen" docs-for-ai
```

命中后要先判断：

1. 这是不是普通 BizModel 的默认示例。
2. 如果不是，边界是否已经写清楚。

## 维护顺序

1. 对应 owner doc（通常是 `02-core-guides/`、`03-runbooks/` 或 `04-reference/`）
2. `INDEX.md`（如果路由发生变化）
3. `04-reference/source-anchors.md`（如果实现锚点发生变化）
4. `ai-dev/logs/{year}/{month}-{day}.md`
5. `ai-dev/logs/index.md`（如果当天第一次建日志文件）
6. `90-maintenance/maintenance-rules.md` 与 `AGENTS.md`（如果维护流程本身发生变化）

## 相关文档

- `../../AGENTS.md`
- `../../ai-dev/logs/index.md`
- `../INDEX.md`
- `../04-reference/source-anchors.md`
