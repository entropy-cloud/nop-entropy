# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-code（代码索引与分析模块）
- **审核日期**: 2026-06-06
- **执行维度**: 21 个维度全覆盖
- **目标范围**: nop-code 模块 13 个子模块，约 28K 行手写 Java 代码，85 个测试文件

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01 依赖图 | 1 | 1 | 0 | 1 | 0 | 0 |
| 02 模块职责 | 1 | 6 | 0 | 4 | 1 | 2 |
| 03 API 表面积 | 1 | 3 | 0 | 2 | 0 | 1 |
| 04 ORM 模型 | 1 | 4 | 0 | 4 | 0 | 0 |
| 05 生成管线 | 1 | 0 | 0 | 0 | 0 | 0 |
| 06 Delta 定制 | 1 | 0 | 0 | 0 | 0 | 0 |
| 07 BizModel | 1 | 3 | 0 | 1 | 2 | 0 |
| 08 IoC/Beans | 1 | 1 | 0 | 1 | 0 | 0 |
| 09 错误处理 | 1 | 3 | 0 | 3 | 0 | 0 |
| 10 XDSL/XLang | 1 | 0 | 0 | 0 | 0 | 0 |
| 11 XMeta 对齐 | 1 | 0 | 0 | 0 | 0 | 0 |
| 12 GraphQL/API | 1 | 0 | 0 | 0 | 0 | 0 |
| 13 安全权限 | 2 | 3 | 2 | 4 | 1(信息性) | 0 |
| 14 异步事务 | 2 | 3 | 2 | 5 | 0 | 0 |
| 15 类型安全 | 1 | 1 | 0 | 1 | 0 | 0 |
| 16 测试覆盖 | 1 | 2 | 0 | 2 | 0 | 0 |
| 17 代码风格 | 1 | 2 | 0 | 2 | 0 | 0 |
| 18 文档一致 | 1 | 1 | 0 | 1 | 0 | 0 |
| 19 命名一致 | 1 | 0 | 0 | 0 | 0 | 0 |
| 20 跨模块契约 | 1 | 1 | 0 | 1 | 0 | 0 |
| 21 测试有效性 | 1 | 4 | 0 | 4 | 0 | 0 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P1 | 4 | 安全校验绕过(2)、长事务(2) |
| P2 | 12 | God Class、API 契约、DTO 位置、竞态条件、测试质量 |
| P3 | 21 | 命名不一致、代码重复、格式问题、低价值测试 |

## 关键发现摘要

### P0 发现

无。

### P1 发现

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| [维度13-01] | CodeIndexService.java | validateLocalPath isDirectory() 条件导致路径校验可被绕过 |
| [维度13-04] | CodeIndexService.java | triggerIncrementalIndex 使用原始 vfsPath 验证而非解析后路径 |
| [维度14-01] | CodeIndexService.java | indexDirectory 长事务风险（分析+持久化在同一事务+锁中） |
| [维度14-05] | CodeIndexService.java | triggerIncrementalIndex 事务包含文件 I/O 和 CPU 分析 |

### P2 发现

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| [维度02-01] | CodeIndexService.java | God Class（1954 行，7+ 职责） |
| [维度04-01] | nop-code.orm.xml | CodeRelationType.MIXIN 枚举值未在字典中定义 |
| [维度04-02] | nop-code.orm.xml | code/language 字典已定义但 language 列未引用 |
| [维度03-01] | INopCodeIndexBiz.java 等 | I*Biz 接口未声明 BizModel 自定义方法 |
| [维度13-05] | ChangeAnalyzer.java | 数据库路径用于 git diff 命令执行 |
| [维度14-04] | CodeIndexService.java | deleteIndex 缺少 withIndexLock 竞态条件 |
| [维度16-01] | TestNopCodeIndexBizModel.java | 核心方法边界条件测试不充分 |
| [维度20-01] | nop-code-service/api/dto/ | 公开 DTO 放在 service 而非 api 模块 |
| [维度21-01] | TestEdgeProvenance.java | 大量 P-1 反模式（getter/setter 往返测试） |
| [维度21-02] | TestTruncatedFlag.java | 完全 P-1 反模式 |
| [维度07-01] | CodeIndexService.java | 委托类使用 *Service 命名违反平台约定（已降级为 P3） |
| [维度07-02] | NopCodeIndexBizModel.java | incrementalStatusMap 内存状态无持久化（已降级为 P3） |

## 总评

nop-code 模块整体工程质量中等偏上。生成管线完整闭合（维度 05 零发现）、ORM 关系定义正确、XDSL/XMeta 全部合规（维度 10/11 零发现）、命名一致性好（维度 19 零发现）。测试数量充足（85 个文件），核心算法有高质量测试（TestDeadCodeDetector、TestFlowDetector）。

主要风险集中在 **CodeIndexService** 这个 God Class（1954 行）：
1. **安全问题**（P1）：路径校验存在两个绕过场景，validateLocalPath 的 isDirectory() 条件和 triggerIncrementalIndex 使用未解析路径
2. **事务问题**（P1）：indexDirectory 和 triggerIncrementalIndex 的长事务风险
3. **架构问题**（P2）：职责混合、开闭原则局部违反、I*Biz 接口契约不完整

这些问题相互关联——CodeIndexService 承担了过多职责导致安全校验和事务边界的管理困难。

## 优先修复建议

1. **[P1] 修复 validateLocalPath**：移除 isDirectory() 条件，始终检查 allowedLocalRoot；统一 triggerIncrementalIndex 和 indexDirectory 的验证模式
2. **[P1] 拆分事务边界**：将 triggerIncrementalIndex 中的文件分析和变更检测移到事务外
3. **[P2] 修复 deleteIndex 竞态**：添加 withIndexLock 保护
4. **[P2] 拆分 CodeIndexService**：提取 ORM 持久化逻辑为 EntityMapper，拆分 ICodeIndexService 为聚焦接口
5. **[P2] 补全测试**：indexFile sourceCode 大小限制、triggerFullIndex 路径校验、图分析边界条件

## 本次审核盲区自评

1. 未执行 `./mvnw test -pl nop-code` 验证测试是否全部通过（运行时限制）
2. 未深入审计 tree-sitter JNI 绑定和内存管理的正确性
3. 未审计 nop-code-codegen 模板的生成逻辑正确性
4. 维度 02 中关于生成文件被手工编辑的发现（02-03、02-04）被复核驳回，但 codegen 重跑后的实际覆盖行为未验证
5. 安全审计未覆盖 VFS 路径解析的完整攻击面（resolveVfsPath 的所有调用路径）
