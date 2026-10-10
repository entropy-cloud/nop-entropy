# nop-find Design Docs

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

nop-find 是 Nop 平台的独立模糊文件查找工具（fuzzy file finder），对标 zcode `@` 面板 / VSCode Ctrl+P / fzf file mode。给定短 query，按匹配质量返回 top-K 排序文件路径。与 nop-rg 互补：nop-rg 做内容搜索（grep）和精确 glob，nop-find 做"按部分名字找文件"的模糊查找。

## 阅读顺序

1. `00-vision.md` — Vision 层：产品定位、成功标准、非目标
2. `01-architecture.md` — Architecture Baseline 层：模块结构、打分算法、CLI 契约、关键决策

## 与 nop-rg 的边界

| | nop-rg | nop-find |
|---|---|---|
| **用途** | 文件内容搜索（grep）+ 精确 glob | 文件名/路径模糊查找（相关性排序） |
| **匹配语义** | 字节/正则/glob 精确匹配 | 三级打分（前缀 < 子串 < 子序列）+ top-K |
| **输出** | 命中行/文件列表 | 按分数排序的 top-K 文件路径 |
| **典型 query** | `NopException`、`*.java` | `scord`（→ SearchCoordinator）、`noprgcli` |
| **参考对象** | ripgrep | zcode workspace-file-search / fzf file mode |
