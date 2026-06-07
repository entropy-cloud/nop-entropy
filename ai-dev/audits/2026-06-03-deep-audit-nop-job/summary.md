# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-job
- **审核日期**: 2026-06-03
- **执行维度**: 全部 21 个维度
- **目标范围**: nop-job 模块全部 11 个子模块（api, core, codegen, dao, meta, coordinator, worker, service, web, app, retry-adapter）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | P2 | P3 | 信息性 |
|------|---------|-----------|-----------|----|----|--------|
| 01 依赖图与模块边界 | 1 | 8 | 0 | 2 | 4 | 2 |
| 02 模块职责与文件边界 | 1 | 4 | 0 | 1 | 3 | 0 |
| 03 API 表面积与契约一致性 | 1 | 3 | 0 | 0 | 3 | 0 |
| 04 ORM 模型与实体设计 | 1 | 5 | 0 | 0 | 5 | 0 |
| 05 生成管线完整性 | 1 | 0 | 0 | 0 | 0 | 1 |
| 06 Delta 定制合规性 | 1 | 0 | 0 | 0 | 0 | 1 |
| 07 BizModel 规范遵循 | 1 | 4 | 0 | 0 | 4 | 0 |
| 08 IoC 与 Bean 配置 | 1 | 0 | 0 | 0 | 0 | 1 |
| 09 错误处理与错误码 | 1 | 3 | 0 | 0 | 3 | 0 |
| 10 XDSL 与 XLang 正确性 | 1 | 0 | 0 | 0 | 0 | 1 |
| 11 XMeta 与 BizModel 对齐 | 1 | 2 | 0 | 1 | 1 | 0 |
| 12 GraphQL 与 API 层 | 1 | 0 | 0 | 0 | 0 | 2 |
| 13 安全与权限模型 | 1 | 2 | 0 | 2 | 0 | 0 |
| 14 异步与事务模式 | 1 | 4 | 0 | 2 | 2 | 0 |
| 15 类型安全与泛型使用 | 1 | 1 | 0 | 0 | 1 | 0 |
| 16 测试覆盖与质量 | 1 | 1 | 0 | 1 | 0 | 2 |
| 17 代码风格与规范 | 1 | 2 | 0 | 0 | 2 | 0 |
| 18 文档-代码一致性 | 1 | 0 | 0 | 0 | 0 | 2 |
| 19 命名与术语一致性 | 1 | 1 | 0 | 0 | 1 | 0 |
| 20 跨模块契约一致性 | 1 | 1 | 0 | 1 | 0 | 2 |
| 21 单元测试有效性 | 1 | 2 | 0 | 0 | 2 | 2 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 0    | —       |
| P2      | 8    | 事务安全(2)、权限缺失(2)、测试缺失(1)、代码重复(1)、xmeta字段保护(1)、接口语义(1) |
| P3      | 27   | 代码重复(7)、遗留文件(1)、风格问题(3)、命名/常量(3)、类型安全(1)、xmeta字段(1)、其他(11) |
| 信息性  | 14   | 合规确认(11)、正面观察(3) |

## 关键发现摘要

### P2 发现（8 项）

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 02-03 | JobScheduleStoreImpl.java | 乐观锁重试模式重复 7 次，字段恢复逻辑分散 |
| 11-02 | NopJobTask.xmeta | 创建时字段未设 updatable=false，可通过 API 修改 task 关键字段 |
| 13-01 | NopJobScheduleBizModel.java | 8 个自定义方法无权限注解，所有用户可执行全部操作 |
| 13-02 | nop-job.data-auth.xml | 数据权限完全为空，多租户场景下无隔离 |
| 14-01 | JobScheduleStoreImpl.java | 乐观锁耗尽后 fallback 到 updateEntityDirectly，极端并发下计数器可能漂移 |
| 14-02 | JobScheduleStoreImpl.java | overlayFireAndAdvanceSchedule 单事务内多表多行操作，高并发时可能锁竞争 |
| 16-01 | NopJobTaskBizModel (缺少测试) | delete() 覆写无测试保护 |
| 20-03 | NopRetryJobRetryBridge.java | onFireFailed() 始终返回 null，fire.retryRecordId 永远不会被填充 |

### P3 亮点

- 工具方法重复（5-7 个文件）：defaultLong/defaultInt/calculateDuration/addPartitionFilter
- Store 常量重复定义（3 个文件各自重新定义 _NopJobCoreConstants 中的常量）
- 日历类使用 IllegalArgumentException（非 NopException + ErrorCode）
- web 模块遗留 4 组无 ORM/xmeta 支撑的页面

## 总评

nop-job 是一个架构设计优秀、工程质量较高的标准业务模块。

**架构层面**：模块分层清晰（api→core→codegen→dao→meta→coordinator/worker/service→web→app），无循环依赖、无反向依赖。11 个子模块各司其职，coordinator/worker 的角色分离设计合理。生成管线完整闭合，Delta 使用合规。

**代码层面**：BizModel 实现规范（继承 CrudBizModel、使用标准注解和参数模式），错误处理遵循两档策略（日历类除外），IoC 配置正确（全部 setter 注入、无 Spring 注解误用），XDSL 文件语法和语义正确。

**测试层面**：核心引擎路径（Planner→Dispatcher→Worker→Completion→Timeout）测试覆盖全面，包含大量边界条件和竞态条件测试。BizModel 状态机转换测试充分。仅 NopJobTaskBizModel 缺少测试。

**主要改进方向**：(1) 权限模型——方法级和数据级权限均为空白；(2) 事务安全——乐观锁 fallback 路径的原子性可增强；(3) 代码去重——工具方法、常量定义、Mock Store 等多处在重复。

## 优先修复建议

1. **[高] 权限模型**：至少为 data-auth.xml 配置基于 namespaceId 的数据权限规则（影响：多租户安全）
2. **[高] xmeta 字段保护**：NopJobTask.xmeta 为所有引擎管理字段添加 updatable=false（影响：数据一致性）
3. **[中] NopJobTaskBizModel 测试**：新增测试验证 delete() 抛出异常（影响：安全约束回归保护）
4. **[中] 乐观锁 fallback**：考虑增加重试次数或在 fallback 中使用原子 SQL（影响：高并发场景数据精度）
5. **[低] 代码去重**：提取重复的工具方法、常量定义和 Mock Store（影响：维护成本）
6. **[低] 清理遗留**：删除 web 模块中无 ORM/xmeta 支撑的页面文件（影响：代码整洁度）

## 本次审核盲区自评

1. **未运行测试**：未执行 `./mvnw test -pl nop-job` 验证测试是否全部通过，审计基于代码阅读。
2. **权限模型验证有限**：未深入检查 app 层或网关层是否有统一的权限拦截器。P2 的权限发现可能在实际部署中被上层处理。
3. **nop-sys-dao 间接使用未完全确认**：维度01中 nop-sys-dao 的依赖可能通过 xbiz 配置或 IoC 反射机制间接使用，需进一步确认。
4. **日历类来源未追溯**：未确认日历类（BaseCalendar, DailyCalendar 等）是否完全从 Quartz 移植，如果是，修改 IllegalArgumentException 的优先级应更低。
5. **性能测试未覆盖**：未评估实际高并发场景下事务持有时间和乐观锁冲突频率。
