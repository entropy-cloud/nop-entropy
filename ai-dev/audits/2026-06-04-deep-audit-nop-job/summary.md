# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-job
- **审核日期**: 2026-06-04
- **执行维度**: 全部 21 个维度
- **目标范围**: nop-job 全部 11 个子模块（api/core/codegen/dao/coordinator/worker/meta/service/web/app/retry-adapter）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01-依赖图 | 1 | 4 | 0 | 2 | 0 | 2 |
| 02-模块职责 | 2 | 3 | 0 | 0 | 0 | 3 |
| 03-API表面积 | 1 | 1 | 0 | 1 | 0 | 0 |
| 04-ORM模型 | 1 | 5 | 0 | 3 | 1 | 1 |
| 05-生成管线 | 1 | 1 | 0 | 1 | 0 | 0 |
| 06-Delta定制 | 1 | 0 | 0 | 0 | 0 | 0 |
| 07-BizModel | 1 | 6 | 0 | 4 | 0 | 2 |
| 08-IoC与Bean | — | — | — | — | — | — |
| 09-错误处理 | 1 | 3 | 0 | 2 | 0 | 1 |
| 10-XDSL | 1 | 1 | 0 | 1 | 0 | 0 |
| 11-XMeta对齐 | 1 | 0 | 0 | 0 | 0 | 0 |
| 12-GraphQL | 1 | 0 | 0 | 0 | 0 | 0 |
| 13-安全权限 | 1 | 0 | 0 | 0 | 0 | 0 |
| 14-异步事务 | 1 | 3 | 0 | 2 | 0 | 1 |
| 15-类型安全 | 1 | 4 | 0 | 3 | 0 | 1 |
| 16-测试覆盖 | 1 | 2 | 0 | 1 | 0 | 1 |
| 17-代码风格 | 1 | 3 | 0 | 1 | 0 | 2 |
| 18-文档一致性 | 1 | 0 | 0 | 0 | 0 | 0 |
| 19-命名一致性 | 1 | 0 | 0 | 0 | 0 | 0 |
| 20-跨模块契约 | 1 | 2 | 0 | 0 | 0 | 2 |
| 21-测试有效性 | 1 | 2 | 0 | 1 | 0 | 1 |
| **合计** | — | **36** | **0** | **22** | **1** | **17** |

注：维度 08（IoC 与 Bean 配置）在初审阶段被维度 02 和 10 覆盖，未独立派发。

## 按严重程度分布（复核后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 0    | —       |
| P2      | 6    | ORM 唯一键竞态、代码重复、错误处理、测试缺失 |
| P3      | 16   | 命名、风格、死代码、隐性依赖 |

## 关键发现摘要（复核后保留的 P2）

### P2 发现

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 01-01 | nop-job-dao/pom.xml | dao→core 分层例外（合理架构决策，需文档化） |
| 04-01 | model/nop-job.orm.xml | 唯一键对并发手动触发可能导致 DB 约束异常 |
| 07-02 | NopJobFireBizModel.java | cancelFire 成功路径多一次不必要的 loadFire 查询 |
| 07-04 | NopJobFireBizModel + NopJobScheduleBizModel | buildRecoveryFire 与 buildManualFire ~80% 重复代码 |
| 09-01 | calendar 包（4 个类） | 12 处 IllegalArgumentException 而非 NopException |
| 16-01 | TestNopJobFireBizModel | rerunFire 的 PAUSED 状态调度缺少正向测试 |

## 总评

nop-job 模块的整体质量**高于平均水平**。

**架构设计**：11 个子模块形成了清晰的分层架构（api→core→dao→meta→service→web→app），加上 coordinator、worker、retry-adapter 三个职责明确的横向模块。依赖方向正确，无循环依赖。框架特定依赖（Quarkus）正确地仅出现在 app 模块。

**代码质量**：三实体模型（Schedule/Fire/Task）层次清晰。BizModel 实现规范（正确继承 CrudBizModel、注解使用规范、权限控制合理）。ORM 模型字段 i18n 翻译完整，索引设计精确匹配查询模式。错误处理整体遵循两档策略（除 Calendar 遗留代码）。

**测试质量**：29 个测试文件约 150 个测试方法，有效测试比例约 90%。乐观锁竞争（TestJobFireStoreRace）、多实例并发（TestJobConcurrency）、SUSPICIOUS 状态转换等高风险场景都有高质量覆盖。

**主要改进方向**：
1. 唯一键 04-01 的并发场景需要应用层预检查
2. buildRecoveryFire/buildManualFire 的代码重复应提取公共方法
3. Calendar 类的错误处理需要统一为 NopException 模式
4. rerunFire PAUSED 路径需要补充正向测试

## 本次审核盲区自评

1. 未执行 `./mvnw test -pl nop-job` 验证全部测试是否通过（依赖基线命令未执行）
2. 未对 nop-job-app 的 Quarkus 集成进行运行时验证
3. 未检查 nop-job-retry-adapter 与 nop-retry-engine 的实际集成行为（仅在代码层面审查）
4. 性能相关发现（如 07-02 的冗余查询）未做实际基准测试量化影响

## 维度复核结论

### 驳回的发现（17 条）

- **01-02, 01-03**: 隐性 nop-core 依赖 — 通过 nop-orm 传递是平台标准行为
- **02-01**: I*Biz 接口放在 dao — 平台标准模式
- **02-02, 02-03**: `_gen/` 文件手写修改 — 复核确认为标准 code-gen 输出，无手写修改证据
- **04-04**: 缺少 delFlag — NopJobSchedule 使用状态机（ARCHIVED）等效软删除
- **04-05**: 缺少反向 to-many — 有意省略以避免 N+1，Store 层批量查询替代
- **07-03**: Map<String,Object> 参数 — 业务参数自由结构的合理表示
- **07-05**: FireBizModel 访问 Schedule Store — 合理的跨实体操作
- **09-02**: 非标准 ErrorCode 前缀 — 数据库兼容性需要
- **14-01**: Timeout 误报警告 — 复核后未确认误报逻辑
- **15-02**: RpcJobInvoker unchecked cast — Java 泛型固有局限
- **16-02**: triggerNow PAUSED 缺测试 — 不影响功能
- **17-01**: System.out.println — 仅在测试代码中
- **17-02**: executorKind 冗余设置 — 实为合理的反规范化
- **20-01**: 空壳接口 — 复核确认接口均有方法定义
- **20-02**: retryRecordId 被忽略 — 复核确认返回值被正确使用
- **21-02**: 测试方法名 — 复核确认命名已表达行为

### 降级的发现（1 条）

- **03-01**: P2→P3 — NopJobSchedule delete 保护可依赖 archiveSchedule 状态机间接实现

### 保留的全部发现

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 01-01 | P2 | nop-job-dao/pom.xml | dao→core 分层例外需文档化 |
| 01-04 | P3 | INopJobScheduleBiz.java | I*Biz 接口依赖 nop-core IServiceContext |
| 03-01 | P3 | NopJobScheduleBizModel.java | Schedule 缺少显式 delete 保护 |
| 04-01 | P2 | nop-job.orm.xml | 唯一键并发手动触发可能 DB 异常 |
| 04-02 | P3 | nop-job.orm.xml | IX_NOP_JOB_TASK_FIRE 前缀被 UK 覆盖 |
| 04-03 | P3 | nop-job.orm.xml | 9 个域定义未被使用 |
| 05-01 | P3 | _templates/ | 5 个过时实体模板 |
| 07-01 | P3 | NopJobScheduleBizModel.java | persistSchedule 绕过 CrudBizModel |
| 07-02 | P2 | NopJobFireBizModel.java | cancelFire 多次 loadFire 查询 |
| 07-04 | P2 | NopJobFireBizModel+NopJobScheduleBizModel | buildRecoveryFire/buildManualFire 重复 |
| 07-06 | P3 | NopJobTaskBizModel.java | 全限定 IServiceContext |
| 09-01 | P2 | calendar 包 | 12 处 IllegalArgumentException |
| 09-03 | P3 | JobCoreErrors.java | @Locale("zh-CN") 与英文描述不一致 |
| 10-01 | P3 | NopJobSchedule.xbiz | triggerNow 缺 overrideParams 参数声明 |
| 14-02 | P3 | JobPlannerScannerImpl.java | copyMap 不复制，方法名误导 |
| 14-03 | P3 | Scanner 生命周期 | shutdown 时可能有 1 个未完成 scan |
| 15-01 | P3 | Store 实现类 | daoProvider.daoFor 强制转换 |
| 15-03 | P3 | 8 个文件 | defaultLong/defaultInt 重复定义 |
| 15-04 | P3 | DailyCalendar.java | StringTokenizer 过时模式 |
| 16-01 | P2 | TestNopJobFireBizModel | rerunFire PAUSED 缺测试 |
| 17-03 | P3 | 测试文件 | 状态常量与 _NopJobCoreConstants 重复 |
| 21-01 | P2 | TestNopRetryJobRetryBridge | assertNotNull 未验证内容 |

## 优先修复建议

1. **[P2-04-01]** 在 `insertManualFire` 中增加 `hasWaitingFire` 式预检查，避免并发手动触发导致 DB 约束异常
2. **[P2-07-04]** 将 buildRecoveryFire/buildManualFire 的公共逻辑提取到共享 FireFactory 方法
3. **[P2-09-01]** 逐步将 Calendar 类的 IllegalArgumentException 替换为 NopException + ErrorCode
4. **[P2-16-01]** 补充 rerunFire PAUSED 状态的正向测试
5. **[P2-07-02]** 缓存 cancelFire 成功路径的 loadFire 结果
