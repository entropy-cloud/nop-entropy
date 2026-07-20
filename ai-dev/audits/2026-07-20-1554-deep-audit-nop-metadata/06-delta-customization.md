# 维度 06：Delta 定制合规性 — 审计报告

> 初审结果（待复核）

## 审计结论：无 Delta 定制文件

对 nop-metadata 所有 8 个子模块进行了彻底扫描（包括 `src/main/resources/_vfs/_delta/` 目录），未发现任何 Delta 定制文件。

| 子模块 | 是否检查 _delta | 结果 |
|--------|---------------|------|
| nop-metadata-api | 是 | 无 |
| nop-metadata-app | 是 | 无 |
| nop-metadata-codegen | 是 | 无 |
| nop-metadata-core | 是 | 无 |
| nop-metadata-dao | 是 | 无 |
| nop-metadata-meta | 是 | 无 |
| nop-metadata-service | 是 | 无 |
| nop-metadata-web | 是 | 无 |

## 说明

nop-metadata 作为一个全新的以 codegen 为先的模块，尚无通过 Delta 进行的跨模块定制。当前模块中的所有定制都是通过标准保留层机制进行的（不带 `_` 前缀的手写 `.xmeta`、`.xbiz`、`.view.xml`、`main.page.yaml` 文件），符合 delta-customization.md 中的规则：
> "如果是在自己的模块里扩展生成保留层，优先使用非下划线文件。"
