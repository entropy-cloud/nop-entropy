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

## 维护原则

1. 新文档默认只引用 `docs-for-ai/` 内部文档。
2. 不要让 `docs/`、示例、原始代码片段或其他非 `docs-for-ai/` 材料承担默认规范角色。
3. 同一主题尽量只有一个 canonical owner；其他文档引用它，不要重复定义。
4. 如果维护任务中发现文档与实现不一致，先做最小必要的 LSP / 定义确认；只有不足时才读少量源码，并回改文档。
5. 专题过于冷门或只适合局部模块时，优先不放进主导航，保持文档主树聚焦当前高频任务。

## 新增或修改文档前的检查项

- 这篇文档的角色是否清晰。
- 是否已经有对应 canonical doc，可以补章节而不是新开平行文档。
- 是否会把边界场景误写成默认模板。
- 是否能在 `04-reference/source-anchors.md` 找到或补上实现锚点。
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

1. `INDEX.md`
2. `03-runbooks/`
3. `02-core-guides/`
4. `04-reference/source-anchors.md`
5. `90-maintenance/`

## 相关文档

- `../INDEX.md`
- `../04-reference/source-anchors.md`
