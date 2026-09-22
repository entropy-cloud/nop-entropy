# Maker-Checker Vision

**日期**：2026-09-22
**范围**：nop-biz / nop-graphql / nop-sys / nop-sys-web（审核前端）；桥接 nop-wf
**状态**：active
**灵感来源**：Apache Fineract（pending command + 审批重放 + `_CHECKER` 派生权限）、Terraform（plan 快照 + serial staleness + apply 一致性复核）、Apache Syncope（实体↔流程 businessKey 绑定 + 审批事务隔离）、GitHub Environments（N-of-M 审批人配置面）

---

## 一、定位

对平台内任意**重要业务写操作**（biz mutation），提供机制级的"制单-复核"能力：maker 提交后变更**不生效**，落为待审记录；checker 在审核界面看到**修改前后对比**并批准后，系统**重放批准过的意图**使变更生效。业务代码只需声明，不写审批逻辑。

## 二、成功标准

1. 业务接入成本 ≤ 一行声明（xbiz `<maker-checker>` 或 `@BizMakerChecker`）+ 一个 try 方法；无 try 语义的动作可复用平台缺省 try。
2. 审核（checker 侧）完全复用 maker 侧执行管道，平台不为 checker 写第二套执行语义。
3. 同一业务对象待审期间：重复提交被拒、任何修改/删除被拒（含 maker 本人），且上述约束在**服务端强制**，前端隐藏按钮只是体验优化。
4. 审核页面能展示任意深度嵌套输入的字段级 before/after 对比，子表按主键分组为新增/删除/变更。
5. 全链路可审计：提交、批准、驳回、撤回、过期、执行失败、bypass 七类事件全部留痕。

## 三、Non-goals

- **不做**多级会签/条件路由/委托转办——那是 nop-wf 的领域（L2）；本机制（L1）只做单级 + 简单豁免，通过桥接 SPI 升级到 L2。
- **不做**审批表单自定义引擎（表单即原输入结构 + diff 展示）。
- **不覆盖**绕过 biz 层的直接 ORM/DAO 写入——机制边界在 biz/GraphQL 入口，文档明示此约束。
- **不做**审批记录的独立权限体系——复用 nop-auth action-auth/data-auth。

## 四、硬约束（不可违反）

1. **SoD 服务端强制**：approve 动作内校验 checker ≠ maker；任何角色配置都不能使 maker 批准自己的记录（系统豁免开关除外，且豁免必须留审计）。豁免出口仅限：`enable-same-checker` 全局开关 + checker-super-user 角色。
2. **生效前置**：批准之前不得对业务数据产生任何可见变更（try 阶段允许预留资源，但不得落业务表）。
3. **意图完整性**：批准与重放针对同一份快照；重放前必须校验快照哈希与目标数据版本，不一致即终止。
4. **锁必有终态释放**：待审锁只能因 记录到达终态 而释放；机制自身故障不得造成业务对象永久锁死（retry/force-cancel 出口必须存在并审计）。
5. **拦截点唯一**：maker 侧拦截与 checker 侧重放共用同一动作入口（GraphQL tryAction + biz 管道）；新增执行通道必须天然经过该入口。
6. **失败可见**：快照超限、脱敏载荷、重复提交、staleness 冲突均显式报错，不静默降级。

## 五、必须由人拍板的决策

- 哪些业务动作启用 maker-checker（业务方按声明决定；平台不默认开启任何业务动作）。
- 待审有效期 TTL 缺省值与超限阈值配置。
- checker-super-user 角色名单与 emergency bypass 角色名单。
- L2（nop-wf 会签）桥接的启用时机。
