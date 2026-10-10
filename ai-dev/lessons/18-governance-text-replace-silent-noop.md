# 18: 治理文本的静默替换失配——python replace 无断言等于没做

> Date: 2026-10-10
> Severity: Medium — full-code-review 轮次 R2.0 closure audit 拦下：宣称「台账头部归属重定向已同步」但 live 文件零改动（replace 目标串与实文差一个「P2」词，无断言静默 no-op）；同轮 V.1 的 roadmap 行更新亦因断言失败中止后未补跑 index/log，提交先于治理回填

## 场景

两起同源事件：

1. **plan 2409（R2.0）**：批处理脚本对 index P2 台账头部做归属重定向，`str.replace(old, new)` 的 `old` 串凭记忆写成「…显式开辟裁决例外时在此登记；R1.0 逐项裁决…」，实文是「…显式开辟 **P2** 裁决例外…」——差一个词，replace 静默 no-op。后续 roadmap R2.0 行、daily log、plan checklist 三处都宣称「头部已同步」，直到独立 closure audit 亲读 live 才拦下（Blocker 级：勾选项 only-partial landing）。
2. **V.1 收口**：roadmap V.1 行的 `old` 串断言失败使脚本中止，index 行与 daily log 未执行，而 `git commit` 因是独立命令照常执行——治理回填落后于提交。

同轮另一处正向证据：R1.10b 的 graphql 代码补丁因带了 `assert old in src` 在编译前就暴露 `setInvoke` 挂错对象；R2.0 的 P2 台账 45 行批量替换因 `assert hit == 45` 确认了全量命中。**同一个会话里，带断言的替换全部安全，不带断言的替换全部出事。**

## 根因

1. **`str.replace` 的静默 no-op 语义**：目标串不匹配时返回原文不报错；写治理文本的人对「原文长什么样」的记忆可信度远低于自己刚写下的断言。
2. **多文件收口脚本的原子性缺失**：脚本中途 assert 失败退出后，后续文件的更新不会执行，而外层 shell 的 commit 命令独立执行——形成「提交已发生、治理未回填」的时间窗。
3. **宣称先于验证**：roadmap/daily log/plan checklist 的「已同步」措辞写在了执行之前（批量勾选 `- [ ]`→`- [x]` 的脚本先跑完了勾选）。

## 正确做法

1. **治理文本替换必须带断言**：`assert old in src`（替换前）+ 替换后计数核对（如 `grep -c` 旧模式归零/新模式计数等于预期行数——R2.0 台账的 `assert hit == 45` 与 `grep "R2.0 裁决"`=45 是可复制的样板）。
2. **收口顺序固化**：执行 → 断言验证（工具门禁）→ 治理回填 → 提交。commit 命令不要与治理脚本同窗并发；脚本失败时**不得**继续执行 commit。
3. **长文本定位优先用唯一短锚**：replace 的 `old` 串从 live 文件现场截取（`sed -n`/`grep -n` 先读再写），不凭记忆重构；跨「P2 裁决例外」这类一词之差就是 no-op。
4. **独立 closure audit 是最后的防线**：它必须亲读 live 文件而非采信 plan/daily log 的宣称——本轮正是这一层拦下了三处「宣称已做」。

## 判定规则

> **任何「已在 X 文件同步/登记/勘正」的宣称，必须能给出该文件中新增文本的 grep 证据**；给不出的宣称按未完成处理。批量替换脚本没有断言与计数核对，视同未执行。

## 适用范围

- 治理文本批量回填（roadmap 行、index 台账、daily log、plan checklist）
- 任何对他人文档的大面积程序化编辑（含代码文件的批量 patch——R1.x 各批的 python-patch 判别先例均带 assert，可复制）
