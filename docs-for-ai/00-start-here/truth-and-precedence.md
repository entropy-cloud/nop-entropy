# Source of Truth and Precedence

当多个信息来源对同一事实有不同描述时，按此优先级解决冲突。

## Precedence Rules

### 代码行为相关

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | **实际代码行为**（运行时） | 最终真相。如果文档和代码不一致，以代码为准，但必须修复文档 |
| 2 | **ORM 模型**（`model/*.orm.xml`） | 数据结构的权威来源 |
| 3 | **xmeta**（`meta/*.xmeta.yaml`） | API schema 的权威来源 |
| 4 | **`docs-for-ai/`** | 平台使用规范。当 `docs-for-ai/` 与代码冲突，视为 docs bug 优先修复 |
| 5 | **`ai-dev/`** | 开发过程记录。不作为代码行为的权威来源 |

### 开发指导相关

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | **`AGENTS.md`** | AI 行为的最高约束 |
| 2 | **`docs-for-ai/00-start-here/`** | 全局默认和项目状态 |
| 3 | **`docs-for-ai/02-core-guides/`** | 领域规范（service-layer、architecture-principles 等） |
| 4 | **`docs-for-ai/03-runbooks/`** | 具体操作步骤 |
| 5 | **`ai-dev/` 中的 00-*-guide.md** | 文档写作规范 |

## Conflict Resolution Protocol

当发现冲突时：

1. **代码 vs `docs-for-ai/`**：以代码为准。在同一任务中修复 `docs-for-ai/`（视为 docs bug）。如果修复会改变用户可见行为，则需 `ask-first`。
2. **`docs-for-ai/` vs `ai-dev/`**：以 `docs-for-ai/` 为准。`ai-dev/` 是开发过程记录，不作为规范性引用。
3. **生成文件（`_gen/`、`_*.xml`）vs 源模型**：以源模型（`model/*.orm.xml`）为准。生成文件是派生物，重新生成后覆盖。
4. **`docs-for-ai/` 内部冲突**：以更具体的 guide 为准（如 `service-layer.md` 优于 `INDEX.md` 的默认规则摘要）。

## Stale Documentation

| 状态 | 含义 | AI 行为约束 |
|------|------|------------|
| `fresh` | 文档与代码一致 | 正常工作 |
| `partially stale` | 部分文档过时 | 仅在已验证新鲜的切片上工作，其余视为 `plan-first` |
| `stale` | 文档明显过时 | `research-only` 直到基线重建 |
| `unknown` | 未验证 | 等同 `stale` |

AI 可从实际代码证据更新过时文档，但不可自行将 `stale` 标记为 `fresh`。

## Related

- `./project-context.md` — 文档新鲜度标记位置
- `../02-core-guides/architecture-principles.md` — "模型是源头"原则
- `../../AGENTS.md` — "Generated Files And Docs" 节
