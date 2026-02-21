# docs-for-ai 子目录重构方案

## 文档概述

本文档说明当前 `docs-for-ai/` 目录结构存在的问题，以及为何需要重构、应该如何重构。

> **目标读者**：AI 助手（作为文档的实际使用者）、文档维护者
>
> **适用范围**：仅针对 `docs-for-ai/` 目录的组织结构调整，不涉及文档内容修改

---

## 一、现状分析

### 1.1 当前目录结构

```
docs-for-ai/
├── 00-quick-start/       (1 file)   - 快速入门
├── 01-core-concepts/    (5 files)   - 核心概念
├── 02-architecture/     (6 files)   - 架构设计
├── 03-development-guide/ (19 files) - 开发指南
├── 04-core-components/  (9 files)   - 核心组件
├── 05-xlang/           (6 files)   - XLang 语言
├── 06-utilities/       (15 files)  - 工具类
├── 07-best-practices/  (5 files)   - 最佳实践
├── 08-examples/         (4 files)   - 示例代码
├── 09-quick-reference/ (3 files)   - 快速参考
├── 10-meta/            (2 files)   - 元文档（问题目录）
├── 11-test-and-debug/  (2 files)   - 测试调试
├── 12-tasks/           (11 files)  - 任务手册
└── 13-reference/       (1 file)    - 参考资料
```

**共计 14 个子目录，89 个文档文件。**

### 1.2 当前 INDEX.md 的作用

INDEX.md 是 AI 的入口文件，提供了：
1. **决策入口表**：告诉 AI "做 X 任务 → 看 Y 文档"
2. **反模式清单**：告诉 AI 哪些做法是错误的
3. **开发流程总览**：告诉 AI 完整的开发步骤
4. **代码模式示例**：告诉 AI 正确的代码怎么写
5. **目录映射表**：列出每个目录的用途

INDEX.md 尝试解决"AI 不知道该看什么文档"的问题，但效果有限。

---

## 二、为什么需要重构

### 2.1 问题一：目录边界模糊

**现象**：多个目录的内容存在重叠，AI 难以判断"这个问题应该去哪个目录找答案"。

| 目录 | 定位 | 实际内容 | 问题 |
|------|------|----------|------|
| `01-core-concepts/` | 核心概念 | overview, delta-basics, nop-vs-traditional | 但 `bizmodel-guide` 也是核心概念，却放在 03 |
| `03-development-guide/` | 开发指南 | bizmodel, service, crud, processor... | 内容太多（19 个文件），边界不清 |
| `02-architecture/` | 架构设计 | orm-architecture, graphql-architecture | 但 ORM 本身是"核心组件"，与 04 重复 |
| `04-core-components/` | 核心组件 | ioc-container, transaction, exception | 但"组件"和"架构"的区别是什么？ |
| `07-best-practices/` | 最佳实践 | code-style, testing | 与 11-test-and-debug 交叉 |
| `06-utilities/` | 工具类 | StringHelper, BeanTool... | 与 09-quick-reference 区别何在？ |

**根本原因**：当前按**技术主题**划分目录，而非按**AI 的决策场景**划分。

### 2.2 问题二：任务型内容占比过低

| 目录 | 文件数 | 占比 | 类型 |
|------|--------|------|------|
| `12-tasks/` | 11 | 12% | 任务型（告诉 AI 怎么做） |
| 其他目录 | 81 | 88% | 知识型（告诉 AI 是什么） |

**问题**：`12-tasks/` 只占 12%，且仅覆盖 7 个场景。AI 在大多数情况下找不到"具体操作步骤"，只能去读大段的开发指南。

INDEX.md 的决策入口表只有 12 行，意味着大多数开发场景没有对应的任务手册。

### 2.3 问题三：10-meta 目录定位混乱

**当前 10-meta 内容**：
- `DOCUMENTATION_TEMPLATE.md` - 文档格式模板
- `code-style-config.md` - Checkstyle 配置

**问题分析**：

| 文件 | 问题 |
|------|------|
| `DOCUMENTATION_TEMPLATE.md` | 包含无效链接（如 `../examples/complete-crud-example.md` 不存在） |
| `DOCUMENTATION_TEMPLATE.md` | 与 `MAINTENANCE.md` 内容重叠（都讲文档编写规范） |
| `code-style-config.md` | 与 `07-best-practices/code-style.md` 重复 |
| 整体定位 | 声称"文档维护者使用"，但 `code-style-config.md` 是代码规范，与文档维护无关 |

**结论**：10-meta 作为独立目录存在价值不足，内容应合并到更合适的位置。

### 2.4 问题四：目录命名不一致

| 目录 | 命名风格 |
|------|----------|
| `00-quick-start/` | 数字前缀 + `-` |
| `10-meta/` | 数字前缀，无 `-` |
| `11-test-and-debug/` | 数字前缀 + `-` + `-` |
| `12-tasks/` | 数字前缀，无 `-` |

### 2.5 问题五：AI 导航路径不清晰

**AI 面临的困惑**：

```
我需要"新增一个字段"，该看什么？

选项 A: 12-tasks/add-field-and-validation.md
选项 B: 01-core-concepts/delta-basics.md
选项 C: 03-development-guide/orm-advanced-features.md
选项 D: 04-core-components/dto-standards.md
```

虽然 INDEX.md 给了答案，但：
1. INDEX.md 本身有 500+ 行，AI 需要先读完才能找到答案
2. AI 经常跳过 INDEX.md 直接搜索，导致"迷路"
3. 没有清晰的"层次结构"让 AI 知道"找不到答案时应该上升一层"

---

## 三、重构原则

### 3.1 核心原则：按 AI 决策场景划分

**不是**按"这是关于 ORM 的文档"划分，**而是**按"AI 现在处于什么状态"划分。

| AI 状态 | 目录 | 回答的问题 |
|---------|------|------------|
| "我要做 X，具体怎么操作？" | 任务手册 | 步骤 1-2-3，直接可执行 |
| "这个功能背后的原理是什么？" | 开发指南 | 为什么这样设计 |
| "Delta 是什么？" | 核心概念 | 理论基础 |
| "ORM 引擎为什么这样实现？" | 架构设计 | 设计决策 |
| "这个 API 怎么调用？" | 参考资料 | 参数、返回值 |

### 3.2 层次化原则

```
任务手册 (Task)
    ↓ 不够详细
开发指南 (Guide)
    ↓ 需要理解原理
核心概念 (Concept)
    ↓ 需要深入理解
架构设计 (Architecture)
    ↓ 需要查 API
参考资料 (Reference)
```

AI 找不到答案时，应该**上升一层**而不是"平行搜索"。

### 3.3 任务优先原则

**目标**：任务手册覆盖 80% 以上的常见开发场景。

- 每个具体开发任务 → 至少有一个对应的任务手册
- 任务手册必须是**可直接执行**的步骤，不是概念解释

### 3.4 精简原则

- 合并功能重叠的目录
- 删除无效链接和冗余内容
- 确保每个目录有明确的唯一职责

---

## 四、重构方案

### 4.1 目标结构

```
docs-for-ai/
├── 01-getting-started/         # 入门（原 00-quick-start 改名）
├── 02-task-reference/          # 任务手册（原 12-tasks 改名并扩容）
├── 03-development-guide/       # 开发指南（保留，精简内容）
├── 04-core-concepts/           # 核心概念（原 01-core-concepts 改名）
├── 05-architecture/            # 架构设计（原 02-architecture 改名）
├── 06-xlang/                   # XLang 语言（原 05-xlang 改名）
├── 07-best-practices/          # 最佳实践（保留）
├── 08-testing-and-debugging/   # 测试与调试（原 11-test-and-debug 改名）
├── 09-reference/               # 参考资料（合并 06-utilities + 09-quick-reference + 13-reference）
├── 10-examples/                # 示例代码（原 08-examples 改名）
├── INDEX.md                    # AI 入口文件
└── MAINTENANCE.md              # 文档维护指南（合并 10-meta 内容）
```

**变化总结**：

| 操作 | 数量 | 说明 |
|------|------|------|
| 改名 | 7 个目录 | 统一命名风格，按优先级重排 |
| 合并 | 4 个目录 → 2 个 | 06+09+13→09-reference，10-meta→MAINTENANCE.md |
| 保留 | 4 个目录 | 03-development-guide, 07-best-practices, INDEX.md, MAINTENANCE.md |
| 删除 | 1 个目录 | 10-meta（内容合并到 MAINTENANCE.md） |

**目录数量**：14 → 10（减少 4 个）

> **注意**：`04-core-components/` 的内容将按类型拆分：
> - 架构说明（如 ioc-architecture）→ `05-architecture/`
> - 使用指南（如 transaction, exception-handling）→ `03-development-guide/` 或 `07-best-practices/`
> - API 参考（如 dto-standards）→ `09-reference/`

### 4.2 详细说明

#### 01-getting-started/ — 入门

```
01-getting-started/
├── quickstart.md       # 10 分钟快速入门
└── setup.md          # 环境搭建
```

**定位**：首次接触 Nop 的开发者/AI
**回答**：如何开始一个 Nop 项目

#### 02-task-reference/ — 任务手册（核心）

```
02-task-reference/
├── create-entity.md           # 如何新建实体
├── add-field.md              # 如何新增字段
├── add-validation.md         # 如何添加校验规则
├── write-bizmodel-method.md  # 如何编写业务方法
├── custom-query.md           # 如何自定义查询
├── extend-crud.md            # 如何扩展 CRUD
├── extend-api.md             # 如何扩展 API 返回字段
├── transaction.md            # 如何控制事务
├── error-handling.md         # 如何处理错误
├── write-test.md             # 如何写单元测试
├── deploy.md                 # 如何部署
├── debug.md                  # 如何调试
└── ...                       # 持续扩充
```

**定位**：AI 的**首选**目录
**回答**："我要做 X，步骤 1-2-3 是什么？"
**原则**：
- 每个文件对应一个**具体**开发任务
- 步骤清晰，直接可执行
- 包含"为什么这样做"的简要说明（1-2 句）

#### 03-development-guide/ — 开发指南

```
03-development-guide/
├── bizmodel.md               # BizModel 编写规范
├── service-layer.md          # 服务层开发
├── crud.md                   # CRUD 开发
├── ddd.md                    # DDD 在 Nop 中的实践
├── processor.md              # Processor 开发
├── data-access.md            # 数据访问
├── querybean.md              # QueryBean 详解
├── filterbean.md             # FilterBean 详解
├── api.md                    # GraphQL API 开发
├── project-structure.md       # 项目结构
├── delta-customization.md    # Delta 定制
└── ...
```

**定位**：需要理解某个功能背后的原理
**回答**："为什么要这样写？"
**与任务手册的区别**：
- 任务手册：告诉 AI 怎么做（步骤）
- 开发指南：告诉 AI 为什么这样做（原理）

#### 04-core-concepts/ — 核心概念

```
04-core-concepts/
├── overview.md               # Nop 平台概述
├── reversible-computation.md # 可逆计算理论
├── delta.md                  # Delta 差量机制
├── nop-vs-spring.md          # Nop vs Spring
├── nop-vs-traditional.md     # Nop vs 传统低代码
├── ai-development.md         # AI 开发规范
└── ...
```

**定位**：建立理论基础
**回答**："Nop 的核心理念是什么？"
**原则**：不包含具体代码示例，只讲概念和原理

#### 05-architecture/ — 架构设计

```
05-architecture/
├── orm.md                    # ORM 引擎架构
├── graphql.md                # GraphQL 引擎架构
├── ioc.md                    # IoC 容器架构
├── codegen.md                # 代码生成机制
├── workflow.md               # 工作流引擎
├── batch.md                  # 批处理引擎
└── ...
```

**定位**：理解系统内部实现
**回答**："这个功能内部是怎么实现的？"
**原则**：面向需要深入调试或扩展框架的开发者

#### 06-xlang/ — XLang 语言

```
06-xlang/
├── xdef.md                   # XDef 元模型
├── xpl.md                    # XPL 模板语言
├── xscript.md                # XScript 脚本
├── meta-programming.md       # 元编程
└── ...
```

**定位**：DSL 和脚本开发
**回答**："如何定义自己的 DSL？"

#### 07-best-practices/ — 最佳实践

```
07-best-practices/
├── code-style.md               # 代码规范（合并 code-style-config.md）
├── error-handling.md           # 错误处理
├── security.md                 # 安全规范
└── performance.md              # 性能优化
```

**定位**：写出正确的代码
**回答**："什么是推荐的做法？"

#### 08-testing-and-debugging/ — 测试与调试

```
08-testing-and-debugging/
├── autotest-guide.md           # AutoTest 录制回放框架
├── unit-testing.md             # 单元测试实践
├── integration-testing.md      # 集成测试
└── debugging.md                # 调试指南
```

**定位**：测试和调试 Nop 应用
**回答**："如何测试我的代码？如何调试问题？"
**来源**：原 `11-test-and-debug/`（改名）
**保留原因**：
- autotest 是 Nop 平台特有的测试框架，是核心特性
- 测试/调试是开发流程中的独立环节，与"代码规范"等最佳实践性质不同
- INDEX.md 决策入口已有测试相关任务，是高频场景

#### 09-reference/ — 参考资料（三合一）

```
08-reference/
├── api/                        # API 速查
│   ├── string-helper.md
│   ├── bean-tool.md
│   ├── collection-helper.md
│   └── ...
├── troubleshooting.md          # 问题排查
└── source-anchors.md           # 源码锚点
```

**定位**：快速查找
**回答**："这个方法怎么用？"
**合并来源**：`06-utilities/` + `09-quick-reference/` + `13-reference/`

#### 10-examples/ — 示例代码

```
10-examples/
├── graphql/                    # GraphQL 示例
├── xdef/                       # XDef 示例
└── bizmodel/                   # BizModel 示例
```

**定位**：完整代码参考
**回答**："有没有完整的例子？"
**来源**：原 `08-examples/`

#### MAINTENANCE.md — 文档维护指南（扩充）

**合并 10-meta 内容后，MAINTENANCE.md 将包含**：

```markdown
# docs-for-ai 文档维护指南

## 文档定位
（保留现有内容）

## 文档分类标准（新增）

| 内容类型 | 应放置目录 | 示例 |
|----------|-----------|------|
| 具体操作步骤 | 02-task-reference/ | add-field.md |
| 功能原理说明 | 03-development-guide/ | bizmodel.md |
| 理论基础 | 04-core-concepts/ | delta.md |
| 系统内部实现 | 05-architecture/ | orm.md |
| DSL 开发 | 06-xlang/ | xdef.md |
| 推荐做法 | 07-best-practices/ | code-style.md |
| 测试与调试 | 08-testing-and-debugging/ | autotest-guide.md |
| API 查询 | 09-reference/ | string-helper.md |
| 完整示例 | 10-examples/ | graphql-example.md |

## 文档模板（从 10-meta/DOCUMENTATION_TEMPLATE.md 合并）

### 必须章节
1. 概述（必须）
2. 核心功能/基本使用（必须）
3. 示例代码（推荐）
4. 最佳实践（推荐）
5. 相关文档（必须）

### 格式规范
（从 DOCUMENTATION_TEMPLATE.md 提取）

## 代码风格检查（从 10-meta/code-style-config.md 合并）

### Checkstyle 配置
（从 code-style-config.md 提取核心内容）

## 自包含性要求（保留现有内容）

## 质量保障清单（保留现有内容）

## 定期维护（保留现有内容）
```

---

## 五、10-meta 目录处理方案

### 5.1 为什么删除 10-meta

| 原因 | 说明 |
|------|------|
| **内容重叠** | `DOCUMENTATION_TEMPLATE.md` 与 `MAINTENANCE.md` 都讲文档规范 |
| **无效链接** | `DOCUMENTATION_TEMPLATE.md` 包含不存在的示例链接 |
| **定位混乱** | 声称"文档维护者使用"，但 `code-style-config.md` 是代码规范 |
| **AI 不需要** | AI 不会主动查阅"如何写文档"的元文档 |

### 5.2 内容迁移映射

| 原文件 | 目标位置 | 处理方式 |
|--------|----------|----------|
| `10-meta/DOCUMENTATION_TEMPLATE.md` | `MAINTENANCE.md` | 合并为"文档模板"章节，删除无效链接 |
| `10-meta/code-style-config.md` | `07-best-practices/code-style.md` | 合并 Checkstyle 配置部分 |

### 5.3 迁移后 MAINTENANCE.md 结构

```
MAINTENANCE.md
├── 文档定位
├── 文档分类标准（新增）
├── 文档模板（从 DOCUMENTATION_TEMPLATE.md 合并）
├── 格式规范（从 DOCUMENTATION_TEMPLATE.md 提取）
├── 代码风格检查（从 code-style-config.md 合并）
├── 自包含性要求
├── 质量保障清单
├── 定期维护
└── 相关文档
```

---

## 六、AI 导航决策树

重构后的目录结构支持以下决策流程：

```
┌─────────────────────────────────────────────────────────────────┐
│                    AI 文档查找决策树                              │
└─────────────────────────────────────────────────────────────────┘

Q1: 你现在处于什么状态？
│
├── "我第一次接触 Nop" → 01-getting-started/ → 04-core-concepts/
│
├── "我要做 X，具体怎么操作？"
│   ├── 有现成任务手册 → 02-task-reference/
│   └── 没有 → 03-development-guide/ (搜索相关主题)
│
├── "这个功能为什么这样设计？" → 03-development-guide/
│
├── "Nop 的核心理念是什么？" → 04-core-concepts/
│
├── "这个内部是怎么实现的？" → 05-architecture/
│
├── "我想定义自己的 DSL" → 06-xlang/
│
├── "什么是正确的做法？" → 07-best-practices/
│
├── "如何测试/调试我的代码？" → 08-testing-and-debugging/
│
├── "这个 API 怎么调用？" → 09-reference/
│
└── "有没有完整例子？" → 10-examples/
```

---

## 七、迁移步骤

### 7.1 阶段一：准备工作

```bash
# 1. 创建备份
git checkout -b docs-refactoring
git add .
git commit -m "chore: 备份当前文档结构"
```

### 7.2 阶段二：创建新结构

```bash
# 1. 创建新目录
mkdir -p docs-for-ai/01-getting-started
mkdir -p docs-for-ai/02-task-reference
mkdir -p docs-for-ai/08-testing-and-debugging
mkdir -p docs-for-ai/09-reference/api

# 2. 移动文件（按映射表）
# 入门
mv docs-for-ai/00-quick-start/* docs-for-ai/01-getting-started/

# 任务手册
mv docs-for-ai/12-tasks/* docs-for-ai/02-task-reference/

# 核心概念（改名）
mv docs-for-ai/01-core-concepts/* docs-for-ai/04-core-concepts/

# 架构（改名）
mv docs-for-ai/02-architecture/* docs-for-ai/05-architecture/

# XLang（改名）
mv docs-for-ai/05-xlang/* docs-for-ai/06-xlang/

# 测试调试（改名）
mv docs-for-ai/11-test-and-debug/* docs-for-ai/08-testing-and-debugging/

# 参考资料合并
mv docs-for-ai/06-utilities/* docs-for-ai/09-reference/api/
mv docs-for-ai/09-quick-reference/* docs-for-ai/09-reference/
mv docs-for-ai/13-reference/* docs-for-ai/09-reference/

# 示例（改名）
mv docs-for-ai/08-examples/* docs-for-ai/10-examples/
```

### 7.3 阶段三：合并 10-meta

```bash
# 10-meta 内容合并到 MAINTENANCE.md（手动编辑）
#    - DOCUMENTATION_TEMPLATE.md → MAINTENANCE.md 的"文档模板"章节
#    - code-style-config.md → 07-best-practices/code-style.md
```

### 7.4 阶段四：拆分 04-core-components

```bash
# 按内容类型拆分到不同目录
# 架构说明（如 ioc-architecture.md）→ 05-architecture/
# 使用指南（如 transaction.md, exception-handling.md）→ 03-development-guide/
# API 参考（如 dto-standards.md, error-codes.md）→ 09-reference/
```

### 7.5 阶段五：更新内部链接

```bash
# 检查并更新所有文档中的内部链接
# 特别注意：
# - DOCUMENTATION_TEMPLATE.md 中的无效链接需删除
# - 所有指向旧目录的链接需更新
```

### 7.6 阶段六：更新 INDEX.md

更新 INDEX.md 的目录映射表，指向新的目录结构。

### 7.7 阶段七：清理旧目录

```bash
# 确认迁移完成后，删除旧目录
rmdir docs-for-ai/00-quick-start
rmdir docs-for-ai/01-core-concepts
rmdir docs-for-ai/02-architecture
rmdir docs-for-ai/04-core-components
rmdir docs-for-ai/05-xlang
rmdir docs-for-ai/06-utilities
rmdir docs-for-ai/08-examples
rmdir docs-for-ai/09-quick-reference
rmdir docs-for-ai/10-meta
rmdir docs-for-ai/11-test-and-debug
rmdir docs-for-ai/12-tasks
rmdir docs-for-ai/13-reference
```

---

## 八、验收标准

### 8.1 结构验收

| 检查项 | 标准 |
|--------|------|
| 目录数量 | 10 个子目录 + 2 个根文件 |
| 命名风格 | 统一使用 `NN-name-name/` 格式 |
| 10-meta | 已删除，内容已合并 |
| 测试调试 | 保留独立目录 `08-testing-and-debugging/` |

### 8.2 功能验收

| 检查项 | 标准 |
|--------|------|
| AI 导航 | 给定 10 个典型问题，AI 应在 2 次文件读取内找到答案 |
| 任务覆盖 | 02-task-reference/ 覆盖 ≥15 个常见场景 |
| 链接有效 | 所有内部链接可访问 |
| 无效链接 | DOCUMENTATION_TEMPLATE.md 中的无效链接已删除 |

### 8.3 内容验收

| 检查项 | 标准 |
|--------|------|
| 文档迁移 | 所有旧文档都已迁移到新目录 |
| 内容完整 | 文档内容未丢失 |
| 链接更新 | 所有内部链接已更新为新路径 |
| MAINTENANCE.md | 已包含文档分类标准和模板 |

---

## 九、总结

| 维度 | 当前状态 | 目标状态 |
|------|----------|----------|
| 目录数量 | 14 个 | 10 个 |
| 目录划分依据 | 技术主题 | AI 决策场景 |
| 任务手册占比 | 12% | ≥20%（持续扩充） |
| 导航清晰度 | INDEX.md 500 行 | 分层决策树 |
| 命名一致性 | 不一致 | 统一风格 |
| 10-meta 目录 | 独立存在（冗余） | 已合并到 MAINTENANCE.md |
| 测试调试 | 11-test-and-debug | 08-testing-and-debugging（保留独立） |

**核心理念**：让 AI 在**最短路径**内找到**最相关**的文档，而不是在大量知识型文档中"搜索"答案。

---

## 附录：目录映射表

| 当前目录 | 目标目录 | 操作 |
|---------|---------|------|
| `00-quick-start/` | `01-getting-started/` | 改名 |
| `12-tasks/` | `02-task-reference/` | 改名 + 扩容 |
| `03-development-guide/` | `03-development-guide/` | 保留 + 精简 |
| `01-core-concepts/` | `04-core-concepts/` | 改名 |
| `02-architecture/` | `05-architecture/` | 改名 |
| `04-core-components/` | 按内容拆分 | 架构→05，指南→03，参考→09 |
| `05-xlang/` | `06-xlang/` | 改名 |
| `07-best-practices/` | `07-best-practices/` | 保留 |
| `11-test-and-debug/` | `08-testing-and-debugging/` | 改名（保留独立） |
| `06-utilities/` | `09-reference/api/` | 合并 |
| `09-quick-reference/` | `09-reference/` | 合并 |
| `13-reference/` | `09-reference/` | 合并 |
| `08-examples/` | `10-examples/` | 改名 |
| `10-meta/` | `MAINTENANCE.md` | 合并（删除目录） |
