# bash 前端替换 roadmap — 三轮对抗性审查记录（达成共识）

> Reviewees: `ai-dev/backlog/nop-ai-shell-bash-cst-frontend-roadmap.md`
> Sessions: R1 `ses_ef5e1f69cffe3Sf6IF2kF4JzBJ` → R2 `ses_ef5d1971cffeqpiTT1hojwcjVu` → R3 `ses_ef5c0b96cffekRCxUjr9Phj4dP`
> 终审结论：**CONSENSUS**（无 Blocker，可进入执行）

## 流程

每轮均为独立 fresh-session 子 agent 对抗性审查（含想象性分析），作者修复后换新 session
再审，直到无 Blocker 达成共识。

## R1（2 Blocker + 7 Major + 8 Minor）→ 全部修复

关键发现：金等价按字面不可满足（旧测试断言 `ParseException`、`VAR= | echo` 旧抛新必收、
模型无 `equals`）→ 增设金等价协议；word 数据模型三要求互斥 → 两阶段裁定；Tier 矩阵
owner doc 实为 `nop-ai-shell-syntax-spec.md`；换行被旧 lexer 当空白吞并；`&&`/`||` 优先级
与 bash 不一致；`EMPTY_VALUE` 引用失实（实为 nop-treesitter token）等。

## R2（1 Blocker + 6 Major + 4 Minor）→ 全部修复

R1 修复验证 14/15 FIXED；新发现核心是 **N1：word 扁平化裁定与 in-scope `$()`/`$VAR` 互斥**
（`echo '$HOME'` 剥引号后单引号抑制失效且无门禁可抓；`echo $(pwd)` 扁平后无法定位且
re-parse 被禁）→ 重开为两阶段裁定：B 扁平复刻怪癖 / C 引入结构化 word segment（item 6）。
另：break/continue/return 无专属 CST 节点（按 command_name 识别）、空输入应裁定为成功
no-op 而非拒绝、`for` 循环变量绑定无归属、nop-ai-shell 实为 orphan module（消费方仅
test-scoped）、mission CHECK `-am` 对 nop-ai-agent 存在下游盲区、item 7 依赖边错。

## R3（终审，CONSENSUS）

R2 修复抽查全部落地（N1 五处联动无矛盾）；两阶段 word 方案确认工程可行，关键不变量
——**dump 通道的 word 投影恒为 B 阶段展平函数，`args` 保留为 segments 的派生扁平视图**——
已落字（Major N-1）。同批收口：compound/redirected_statement 作 pipeline stage 结构化拒绝
+ "未列入映射表一律结构化拒绝"默认规则（Major N-2）；D9 补 `lastExitCode`（Minor）；
mermaid 补 D0 入边（Minor）；mission CHECK 口径注明（Minor）；本记录归档（Minor）。

## 遗留给 Stage A 的裁定清单

见 roadmap Stage A deliverables 1-11（全部要求纸面可裁定，其中信号载体已可由代码事实
锁定排除"结果扩展"）。
