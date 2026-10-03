# WI5 Closure Audit——09-wi5-translation-golden-and-doc.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与 plan 起草审查 agent 及执行 agent 均不同 task）
- 裁定：三轮审计——首轮 FAIL（4 必修项）→ 二轮 FAIL（收敛至 2 处文本小修）→ 三轮（结轮）**PASS**

## 1. 三轮累计核验（全部闭合）

| 项 | 裁定 | 证据 |
|---|---|---|
| 5 方言 frame golden（h2/postgresql/duckdb/postgis/h2gis） | PASS | 6 组 frame 查询 + named 无 frame 对照，7 标记齐全，91 行纯 SQL（首轮 Major-1 日志污染已清洗）；逐行 contains 断言真实有效 |
| fail-fast 参数化断言 | PASS | 6 方言 × 3 单位 = 18 断言，errorCode + param feature 与 live OrmEqlConstants / EqlTransformVisitor:352-373 逐字一致；frameClosedDialects/frameDialects 集合与 x:extends 链吻合，db2 不在任一集合 |
| 文档三层交集口径 | PASS | 「grammar 接受度 ∩ 方言能力位 ∩ 方言继承链 + 函数登记附加维度保留（:40 未动）」；「支持标准 SQL 全部子句」全域 grep 零残留；W2 未进语法层标注；**零 ai-dev/ 路径引用**；开启 5 方言与 fail-fast 6 方言双清单并列（三轮补齐） |
| 矩阵文档 §4 交叉注记 | PASS | WI5 注记行在位；postgis 重复行已删（二轮 Minor-2）；h2gis/duckdb/postgis 继承外溢表述准确 |
| 回归 | PASS | TestDialectWindowSqlSnapshot 21/21；nop-orm-eql -am 全量 116 绿（audit 实测） |
| 工具门禁 | PASS | check-doc-links --strict 0（audit 实测）；scan-hollow 0 |
| 未越界 | PASS | git 全集 = 声明文件；roadmap diff 为空；无产品代码变更 |
| W2 分账 | PASS | Deferred But Adjudicated 合规（out-of-scope improvement + Why Not Blocking Closure 引 W1/W2 分账） |

## 2. 三轮 FAIL 项与处置

- 首轮：Major-1（4 golden 尾部时间戳日志污染）→ 清洗为纯 SQL；Major-2（矩阵 §4 注记漏做——python 替换未命中静默失败）→ 断言式替换补做；Major-3（测试类 W2 标注漏做）→ 补；Major-4（日志失实宣称）→ 显式勘误。
- 二轮：Major-A（Minor-1 fail-fast 清单宣称已做而实际未命中——第二次静默失败）→ 断言式替换 + grep 验证落地；Minor-2（postgis 重复行）→ 删旧行。
- 教训固化（日志）：python 文本替换必须 `assert old in s`（本日三次静默未命中案例：WI0d 勾选、WI5 注记、WI5 owner doc）。

## 3. Phase 4 收口确认

- [x] roadmap WI5 `todo` → `done`（括注零圆括号字符）；解析器 items 31 milestones 7
- [x] plan Phase 1-2 checklist 与 Closure Gates 勾齐，Plan Status → completed
- [x] check-plan-checklist.mjs --strict 退出码 0
- [x] check-doc-links.mjs --strict 退出码 0；scan-hollow 高危零发现
