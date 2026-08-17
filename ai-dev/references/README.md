# references — 外部论文/资料存档

本目录存放平台开发过程中引用的外部论文与资料（PDF 原文 + 转存的 Markdown），供 AI 与开发者离线查阅。

## 目录结构

```
references/
├── README.md               # 本文件
└── <topic>/                # 每个主题/论文一个子目录
    ├── README.md           # 元数据：出处、版本、转换方式、用途
    ├── *.pdf               # 原文 PDF（能找到时必存）
    └── *.md                # PDF 转存的 Markdown（保留公式）
```

## 约定

- **原文优先**：能找到 PDF 源文件的一定保存 PDF 并以其为准；Markdown 仅作为可检索/可引用版本。
- **公式保留**：转存 Markdown 必须保留论文公式（本仓库论文多为 Unicode 数学排版 PDF，文本层直接提取即可保真；若文本层缺失公式，需使用 OCR/公式识别工具并人工核验）。
- **元数据**：每个子目录的 `README.md` 记录论文标题、作者、出处 URL、抓取日期与版本、转换工具与命令、以及该论文在项目中的用途/关联。
- **版权注意**：仅存论文开放下载的预印本/作者公开发布版本。

## 当前条目

| 论文 | 子目录 | 关联 |
|------|--------|------|
| A Programming Paradigm for Spatiotemporal Composability（Cordis 论文） | [cordis-paper/](cordis-paper/README.md) | deepseek-harness 的 Cordis 插件架构设计论文 |
