# 深度审核汇总报告 — nop-job

## 基本信息
- 审核模块: nop-job
- 审核日期: 2026-06-03
- 执行维度: 全部 21 维度
- 目标范围: nop-job 11 个子模块（约 20,957 行 Java 代码）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|------|------|------|
| 01 | 1 | 3 | 3 | 0 | 0 |
| 02 | 1 | 5 | 5 | 0 | 0 |
| 03 | 1 | 0 | 0 | 0 | 0 |
| 04 | 1 | 2 | 2 | 0 | 0 |
| 05 | 1 | 2 | 2 | 0 | 0 |
| 06 | 1 | 0 | 0 | 0 | 0 |
| 07 | 1 | 3 | 3 | 0 | 0 |
| 08 | 1 | 1 | 1 | 0 | 0 |
| 09 | 1 | 8 | 8 | 0 | 0 |
| 10 | 1 | 1 | 1 | 0 | 0 |
| 11 | 1 | 3 | 3 | 0 | 0 |
| 12 | 1 | 0 | 0 | 0 | 0 |
| 13 | 1 | 0 | 0 | 0 | 0 |
| 14 | 1 | 0 | 0 | 0 | 0 |
| 15 | 1 | 0 | 0 | 0 | 0 |
| 16 | 1 | 4 | 4 | 0 | 0 |
| 17 | 1 | 4 | 4 | 0 | 0 |
| 18 | 1 | 1 | 1 | 0 | 0 |
| 19 | 1 | 2 | 2 | 0 | 0 |
| 20 | 1 | 1 | 1 | 0 | 0 |
| 21 | 1 | 2 | 2 | 0 | 0 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 1 | IoC bean 无法自动发现 (08-01) |
| P2 | 15 | 计数器漂移(04), 代码重复(02×2, 07), 业务逻辑层级泄漏(02), 生成管线陈旧产物(05), 中文错误描述(09×2), 缺少参数上下文(09), 缺少测试(16×2), xmeta字段权限(10,11), 代码风格(17) |
| P3 | 20 | 未使用依赖(01×3), 域类型偏差(04), 样板代码(07), 陈旧模板(05), 命名不一致(19×2), 测试覆盖不足(16×2,21×2), import风格(17×3), 错误码格式(09×3), 文档偏差(18), 跨模块事件(20), IBiz放置(02) |

## 关键发现摘要

### P1 发现
| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 08-01 | job-retry-adapter.beans.xml | 文件名不匹配 IoC 自动发现模式，NopRetryJobRetryBridge 永远不会被加载 |

### P2 发现（按优先级）
| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 04-01 | JobFireStoreImpl:169-177 | cancelFire 遗漏 totalFireCount/failFireCount 计数器更新 |
| 08-01 | job-retry-adapter.beans.xml | IoC bean 自动发现失败 |
| 05-01 | web/pages/NopJob* | 4 个陈旧页面目录引用不存在的 xmeta |
| 02-01 | JobScheduleStoreImpl等 | Store 层重复定义状态常量而非使用 _NopJobCoreConstants |
| 02-02 | 8个文件 | 工具方法 defaultLong/defaultInt 等跨文件复制 |
| 02-03 | JobScheduleStoreImpl | DAO Store 层包含阻塞策略等业务逻辑 |
| 07-01 | 两个 BizModel | resolveTriggeredBy 完全重复 |
| 07-02 | BizModel+Store | cancelFire 可取消性判定逻辑分裂 |
| 09-01 | JobApiErrors:17,19 | ErrorCode description 硬编码中文 |
| 09-02 | JobCoreErrors:15-43 | 4 个 ErrorCode description 硬编码中文 |
| 09-05 | NopJobTaskBizModel:26 | delete() 缺少 .param() 上下文 |
| 10-01 | NopJobTask.xmeta | 5 个系统管理字段未标记只读 |
| 11-01 | NopJobSchedule.xmeta:5 | scheduleStatus insertable=false 但无 defaultValue 可能导致 NOT NULL 违反 |
| 16-01 | NopJobTaskBizModel | delete 封堵逻辑无测试 |
| 16-02 | RpcBroadcastTaskBuilder | 广播任务构建器零测试 |

## 总评

nop-job 是一个设计良好的分布式任务调度模块，采用了 coordinator/worker 分离架构，Store 层封装了事务边界，乐观锁处理多实例并发竞争。代码整体质量高，分层清晰，ORM 模型设计规范，Delta 定制用法正确。

**最严重的问题是 [08-01] IoC bean 自动发现失败**：`job-retry-adapter.beans.xml` 文件名不匹配 `app*.beans.xml` 模式，导致 `NopRetryJobRetryBridge` 永远不会被 IoC 容器加载，nop-retry 引擎集成形同虚设。修复方法简单：重命名为 `app-retry-adapter.beans.xml`。

**其次是 [04-01] 计数器漂移**：`cancelFire` 路径遗漏了 `totalFireCount` 和 `failFireCount` 的更新，随着取消操作累积，仪表盘统计数据将系统性偏低。

**代码重复是最大的维护成本**：Store 层重新定义状态常量、工具方法在 8 个文件中复制、`resolveTriggeredBy` 在两个 BizModel 中重复、`calculateFixedDelayNextFireTime` 跨层重复。建议提取共享工具类。

## 优先修复建议

1. **P1 [08-01]**: 将 `job-retry-adapter.beans.xml` 重命名为 `app-retry-adapter.beans.xml`
2. **P2 [04-01]**: 在 `JobFireStoreImpl.cancelFire` 中补充 totalFireCount/failFireCount 更新
3. **P2 [05-01]**: 删除 4 个陈旧 web 页面目录
4. **P2 [09-01/02]**: 将 ErrorCode description 改为英文
5. **P2 [02-01/02]**: 提取共享常量和工具方法到 nop-job-core
6. **P2 [10-01/11-01]**: 补充 xmeta 字段只读声明和 scheduleStatus 默认值
7. **P2 [16-01/02]**: 补充缺失的测试

## 本次审核盲区自评

1. 未执行 Maven 构建和测试（`./mvnw test -pl nop-job -am`），依赖代码静态分析
2. 未验证 checkstyle 基线
3. 未深入审查 calendar 包的算法正确性（仅标记缺少测试）
4. 对 nop-job-app 的运行时配置审查较浅
5. 依赖图谱基于 pom.xml 声明分析，未通过 dependency:tree 验证实际解析结果
