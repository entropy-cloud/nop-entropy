# eql-cross-db-compat 设计目录

本目录收录 EQL 跨数据库兼容性治理（roadmap：`ai-dev/backlog/eql-cross-db-compat-roadmap.md`）产生的架构决策与书面裁决。按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 文档清单与阅读顺序

| 文档 | 层级 | 职责边界 | 状态 |
|---|---|---|---|
| `2026-10-03-wi0-gate-adjudications.md` | Decision（前置决策门裁决） | WI0 四门 G-1..G-4 的结论 / 证据引用 / 影响与回滚 / 裁决人注记；G-1/G-3/G-4 已裁决，G-2 维持 open | active |
| `2026-10-03-wi10a-in-list-length-policy.md` | Decision（策略裁决） | WI10a `IN` 列表长度上限策略：阈值来源（Oracle 1000 唯一硬依据 / MSSQL 2100 不适用论证 / 其余 unverified）、字面量 vs 参数化取舍、超阈值行为与改写形态、与 `IPaginationHandler` 扩展点的关系、临时表生命周期与事务语义；推荐方案 + 被拒备选。实现归属 WI10b | active（2026-10-03 独立审查 approve-with-nits 后定稿） |
| `2026-10-10-eql-upsert-and-returning-design.md` | Architecture Baseline（语法契约） | EQL 写语句扩展：`upsert` 语法契约（中立形态 + 三方言 lowering + 冲突键编译期校验 + H2 形态约束）、`insert`/`delete ... returning` 契约、3 个新方言能力位、错误面（零新增码 + 1 个专用码）、执行侧与模块边界、与 `nop-dao` 批量 upsert 原语的并存边界、证据等级纪律、被拒替代方案。实施由 `ai-dev/plans/2380-eql-upsert-and-returning-grammar.md` 承接 | active（Plan 2380 已过两轮独立子 agent 对抗审查，Blocker 清零，待执行） |

必读路径：先读 roadmap「前置决策门（Gate）」节了解四门问题是什么，再读裁决文档看结论；`upsert` / `returning` 语法契约读 `2026-10-10-eql-upsert-and-returning-design.md`；实施细节由对应 WI 的 plan（WI3 / WI9a / WI9b / 2380）承接，不在本目录展开。

## 职责边界

- 本目录只记录**已裁决**的决策与其证据链、回滚方式；执行过程与日志见 `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md` 与 `ai-dev/logs/`。
- 后续 WI 产生的新裁决（如 G-2 的产品侧裁决、WI10a 的 IN 列表策略）追加到本目录，并在上表登记。
