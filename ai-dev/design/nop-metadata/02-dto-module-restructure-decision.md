# DTO 模块依赖重构决策

> Status: active
> Date: 2026-07-21
> Source: `ai-dev/plans/311-nop-metadata-dto-module-restructure.md` Phase 1

## Problem

`nop-metadata-dao` 包含 `I*Biz` 接口（如 `INopMetaTableBiz`），其方法返回 `Map<String, Object>`。DTO 定义在 `nop-metadata-service/.../dto/`。由于 `nop-metadata-dao` 不能依赖 `nop-metadata-service`（Maven 禁止循环依赖），接口无法引用 DTO 类型，导致无法实现强类型 GraphQL schema 推导。

## 候选方案评估

### (a) 新建 `nop-metadata-dto` 共享模块

| 维度 | 评估 |
|------|------|
| 变更范围 | ❌ 最大。需新建模块目录、调整父 pom、修改 codegen 管线、调整所有模块 pom 依赖 |
| 编译验证 | ✅ 可行 |
| 框架修改 | ✅ 不涉及 |
| 向后兼容 | ✅ |
| 未来可扩展 | ✅ |

### (a') 将 DTO 移入 `nop-metadata-core`

| 维度 | 评估 |
|------|------|
| 变更范围 | ✅ 最小。仅修改已有模块的 pom 依赖和文件位置 |
| 编译验证 | ✅ `nop-metadata-dao` 需新增 core 依赖；`nop-metadata-service` 已依赖 core |
| 框架修改 | ✅ 不涉及 |
| 向后兼容 | ✅ |
| 未来可扩展 | ✅ 新增 DTO 直接放入 core，dao 和 service 自动可见 |

**关键依赖分析**：
- DTO 仅依赖 `nop-api-core`（`@DataBean` 注解）和 JDK (`Serializable`)
- `nop-metadata-core` 当前仅依赖 `nop-api-core`——DTO 移入无新增外部依赖
- `ErrorDTO` / `KeyValueDTO` 当前在 `nop-metadata-dao/.../dto/`，需一同移入 core
- `nop-metadata-dao` 当前未依赖 `nop-metadata-core`，需新增

### (b) `I*Biz` 接口移入 `nop-metadata-service`

| 维度 | 评估 |
|------|------|
| 变更范围 | ⚠️ 中等。需移动 40 个接口文件，调整所有 BizModel 的 import |
| 编译验证 | ✅ 可行 |
| 框架修改 | ✅ 不涉及 |
| 向后兼容 | ❌ 外部模块若 `@Inject I*Biz` 需改为依赖 `nop-metadata-service` |
| 未来可扩展 | ⚠️ 未来外部模块若需跨模块注入，依赖耦合更大 |

### (c) 自定义 BizProxy 实现

| 维度 | 评估 |
|------|------|
| 变更范围 | ✅ 极小（仅改 proxy 实现） |
| 编译验证 | ✅ 可行 |
| 框架修改 | ❌ 需要修改 `nop-biz` 框架核心模块 |
| 向后兼容 | ⚠️ 框架级别修改，影响面大 |
| 未来可扩展 | ⚠️ 依赖框架定制 |

## 裁定：方案 (a')

选择将 DTO 移入 `nop-metadata-core`，理由：

1. **变更范围最小**：不新建模块，不移动 I*Biz 接口，不修改框架核心
2. **依赖链清晰**：`nop-metadata-dao → nop-metadata-core → nop-api-core`，`nop-metadata-service → nop-metadata-core`
3. **零框架修改**：不触碰 `nop-biz`/`nop-core`/`nop-xlang`
4. **向后兼容**：I*Biz 接口仍留在 `nop-metadata-dao`，外部模块依赖不变；仅返回类型从 `Map<String, Object>` 变为具体 DTO
5. **未来可扩展**：新 DTO 直接放入 `nop-metadata-core/.../dto/`，所有模块自动可见

## 执行计划

1. 将 `nop-metadata-service/.../dto/` 下 24 个 DTO 移入 `nop-metadata-core/.../dto/`
2. 将 `nop-metadata-dao/.../dto/` 下 `ErrorDTO` + `KeyValueDTO` 移入 `nop-metadata-core/.../dto/`
3. 包名从 `io.nop.metadata.service.dto` / `io.nop.metadata.dao.dto` 统一为 `io.nop.metadata.core.dto`
4. `nop-metadata-dao` 的 pom.xml 新增 `nop-metadata-core` 依赖
5. 更新所有 import 语句（BizModel、Test）
6. 变更 I*Biz 接口返回类型为对应 DTO
7. 变更 BizModel 方法返回类型和内部构造逻辑为 DTO 实例化
