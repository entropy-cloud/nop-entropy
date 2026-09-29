# DeepWiki Plan — selftest-fixture

> Status: approved
> Target: ./target @ HEAD
> Depth: standard
> Language: zh

## 3. 页面契约

| 路径 | 所属章 | 标题 | 职责 | 源文件 | relatedPages | 计划图表 |
|------|--------|------|------|--------|--------------|----------|
| good.md | 指南 | 正常页 | 自检基准：合法引用/合法 mermaid/达标密度 | target/pom.xml、target/src/Foo.java | bad.md | graph TD |
| bad.md | 指南 | 缺陷页 | 自检缺陷样本：越界行号/空括号死链/坏 mermaid/纯文本 Sources | target/pom.xml | good.md | graph TD |

## 5. 覆盖缺口与风险

无（fixture 仅服务 selftest）。
