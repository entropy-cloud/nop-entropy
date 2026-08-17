# A Programming Paradigm for Spatiotemporal Composability

Cordis 插件元框架的设计论文（`~/ai/deepseek-harness` 唯一引用的论文）。

## 元数据

| 项 | 值 |
|---|---|
| 标题 | A Programming Paradigm for Spatiotemporal Composability |
| 作者 | Yifan Shi（北京大学）、Wei Zhang（北京大学）、Tianyi Cui（DeepSeek-AI） |
| 版本 | Preprint，draft of August 13, 2026（repo 标注 "under active revision"） |
| 出处 | https://github.com/cordiverse/paper（`paper.pdf`，`main` 分支） |
| 抓取日期 | 2026-08-16 |
| 页数 | 88 页 |
| 内容概要 | 把 effect/coeffect 概念提升为运行时机制：revertible effects（每个上下文变换携带被跟踪的逆）、reactive coeffects（上下文变化按 coeffect 规范通知组件），统一为 context type 编程范式，并给出动态组合演算（component/fiber、withdrawal/iteration、preservation/temporal/spatial composability/progress/confluence 元理论）；Cordis 是其实现。 |

## 文件

- `paper.pdf` — 原文（GitHub repo 直接下载，与 repo 内最新版一致）
- `spatiotemporal-composability.md` — PDF 转存的 Markdown

## 转换方式

该 PDF 采用 Unicode 数学排版（Typst/XeLaTeX 风格），文本层包含完整 Unicode 公式（𝜕Γ、Γ ⊢ 𝑡、trackΓ/recoverΓ、𝜀 : 𝐷(𝐴) → 𝐴 等），无需 OCR 即可保真提取公式。转换命令：

```bash
# Python 3.12 venv 内：
pip install pymupdf4llm
python -c "import pymupdf4llm; open('out.md','w').write(pymupdf4llm.to_markdown('paper.pdf'))"
```

注意：marker-pdf 2.0.0（surya-ocr 0.22.1）的 OCR 路线不可用——其捆绑 llama.cpp 及当前 brew llama.cpp（10360）均不支持 surya-ocr-2 GGUF 的 `qwen35` 架构，且模型 repo 只有 qwen35 一个修订版（2026-08-16 实测），已放弃。

## 已知局限

- 少量交换图（commutative diagram）在文本提取下丢失版面结构（字符保留，行列错乱），如 §3.1.1 的 recover 图示。
- 下标/上标转为 `<sub>`/`<sup>` HTML 标记。
- 页码数字散落正文（提取自页脚）。
