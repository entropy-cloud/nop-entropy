# ai-dev/tools/

开发辅助工具集。每个工具可独立运行。

## 工具索引

| 脚本 | 用途 | 运行 |
|------|------|------|
| `check-doc-links.mjs` | 检查 `docs-for-ai/` 和 `ai-dev/` 下所有 `.md` 文件的路径引用是否指向存在的文件 | `node ai-dev/tools/check-doc-links.mjs [--strict]` |

## check-doc-links.mjs

检查 markdown 文件中反引号路径和 `[链接]path` 是否指向存在的文件或目录。

```bash
# 基本运行，输出报告但不阻断
node ai-dev/tools/check-doc-links.mjs

# 严格模式，有 error 时 exit code 1（用于 CI）
node ai-dev/tools/check-doc-links.mjs --strict
```

### 跳过规则

以下路径会被跳过不检查：

- **占位符**：basename 以 `XX`、`Xxx` 等大写占位符开头的（如 `_gen/_Xxx.view.xml`）
- **模板路径**：`/XX-` 或 `/xxx[/.]` 片段
- **精确白名单**（`SKIP_TARGETS`，按需维护）：`view.xml/page.yaml`（两种文件类型并称）、`docs-for-ai/XX.md`（模板占位）、`_gen/_LitemallGoods.view.xml`（外部项目）
- **外部项目前缀**（`SKIP_PREFIXES`）：`app-mall-`、`nop-app-mall/`、`nop-chaos-flux/`
- **历史文件**：`ai-dev/logs/`、`ai-dev/analysis/`、`ai-dev/audits/`、`*/archive/` 下的文件跳过 BROKEN_LINK 检查
- **已完成计划**：`ai-dev/plans/` 中标记 `Plan Status: completed` 的文件跳过 BROKEN_LINK 检查
- **BOUNDARY 豁免**：`docs-for-ai/90-maintenance/` 允许引用 `ai-dev/`；`AGENTS.md`、`nop-entropy-e2e/` 允许外部引用
- **单文件跳过**：`ai-dev/plans/50-doc-link-check-fix-all.md`（该计划本身描述的就是要修复的问题路径）

### 输出

报告写入 `_tmp/` 目录（JSON + Markdown），同时输出到终端。

## 新增工具的约定

1. 工具脚本放 `ai-dev/tools/`，扩展名 `.mjs`
2. 在本文件的"工具索引"表格中登记
3. 每个工具脚本头部注释写清用途和参数
4. `PROJECT_ROOT` 通过 `resolve(import.meta.dirname, '..', '..')` 获取
