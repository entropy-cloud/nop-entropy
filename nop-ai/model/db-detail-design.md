### 调整后的表结构清单

| 表名 | 中文名 | 说明 | 字段列表 | 需求章节 |
|------|--------|------|----------|----------|
| **NopAiProject** | 项目表 | 存储AI项目信息 | `ID`、`名称`、`原型项目ID` | 3.2.1 |
| **NopAiConfig** | 项目配置表 | 存储项目级配置项 | `ID`、`项目ID`、`配置名称`、`配置值`、`配置类型` | 3.2.1.2 |
| **NopAiModel** | AI模型表 | 注册的AI模型信息 | `ID`、`供应商`、`模型名称`、`API地址`、`API密钥` | 3.2.2.1 |
| **NopAiRequirement** | 需求表 | 结构化需求条目 | `ID`、`项目ID`、`需求编号`、`需求标题`、`需求内容`、`当前版本`、`父ID`、`类型`（ENUM: 'OVERVIEW','MODULE','CASE'）、`aiSummary`、`status`（ENUM: 'DRAFT','PRE_FINAL','FINAL'） | 3.2.3.1 |
| **NopAiRequirementHistory** | 需求历史表 | 需求变更历史记录 | `ID`、`需求ID`、`版本号`、`需求内容` | 3.2.3.1 |
| **NopAiKnowledge** | 知识库表 | 存储领域知识文档 | `ID`、`项目ID`、`标题`、`内容`、`格式类型` | 3.2.4.1 |
| **NopAiPromptTemplate** | 提示词模板表 | Prompt模板定义 | `ID`、`名称`、`模板内容`、`分类`、`inputs`（VARCHAR(1000)）、`outputs`（VARCHAR(1000)） | 3.2.5.1 |
| **NopAiPromptTemplateHistory** | 模板历史表 | 模板版本历史 | `ID`、`模板ID`、`版本号`、`模板内容` | 3.2.5.1 |
| **NopAiChatRequest** | 对话请求表 | 多模型测试请求 | `ID`、`模板ID`、`prompt`、`hash`、`sessionId`、`metadata`（JSON） | 3.2.5.2 |
| **NopAiChatResponse** | 响应结果表 | AI模型响应记录 | `ID`、`requestId`、`sessionId`、`模型ID`、`aiProvider`、`aiModel`、`响应内容`、`totalTokens`、`responseTimeMs`、`正确性分`、`性能分`、`可读性分`、`合规性分` | 3.2.5.2-5 |
| **NopAiGenFile** | 生成文件表 | 生成的模型文件 | `ID`、`项目ID`、`需求ID`、`模块类型`、`原始内容`、`文件路径`、`chatResponseId`、`status`（ENUM: 'DRAFT','PRE_FINAL','FINAL'） | 3.2.7, 3.2.10.1 |
| **NopAiGenFileHistory** | 文件历史表 | 文件版本历史记录 | `ID`、`genFileId`、`版本号`、`原始内容` | 3.2.8.2 |
| **NopAiTestCase** | 测试用例表 | 生成的测试用例 | `ID`、`需求ID`、`测试内容`、`测试数据`、`genFileId`、`chatResponseId`、`status`（ENUM: 'DRAFT','PRE_FINAL','FINAL'） | 3.2.3.2 |
| **NopAiTestResult** | 测试结果表 | 测试用例执行结果 | `ID`、`testCaseId`、`执行时间`、`是否成功`、`错误日志` | 新增 |

### 关键修改说明

1. **需求表增强**
- 增加 `需求编号` 作为业务唯一标识
- 添加 `父ID` 支持需求树形结构
- `类型` 字段区分需求类型（OVERVIEW/MODULE/CASE）
- 新增 `aiSummary` 存储AI生成的摘要
- 增加 `status` 状态字段（草稿/初步定稿/最终定稿）

2. **提示词模板优化**
- 新增 `inputs` 和 `outputs` 字段（VARCHAR(1000)）
- 支持定义模板的输入输出规范

3. **对话系统重构**
- 表名变更：`NopAiChatPrompt` → `NopAiChatRequest`
- 新增字段：
  - `sessionId` 关联对话会话
  - `metadata` 存储扩展信息（JSON格式）
  - `hash` 用于内容去重校验
- 新增响应表 `NopAiChatResponse` 包含：
  - `aiProvider`/`aiModel` 冗余模型信息
  - `totalTokens`/`responseTimeMs` 性能指标
  - 关联请求的 `requestId`

4. **DSL模型升级**
- 表名变更：`NopAiDslModel` → `NopAiGenFile`
- 移除 `AST解析树` 字段
- 新增关键字段：
  - `文件路径` 定位生成文件
  - `chatResponseId` 关联生成来源
  - `status` 文件定稿状态

5. **测试系统增强**
- 新增 `NopAiTestResult` 表记录执行结果
- 测试用例表移除 Gherkin 相关字段
- 新增 `测试内容` 和 `测试数据` 字段
- 通过 `genFileId` 关联被测试模型文件
- 增加 `status` 区分测试用例状态

6. **状态管理统一**
- 需求/生成文件/测试用例表均增加：
  ```sql
  status ENUM('DRAFT', 'PRE_FINAL', 'FINAL')
     NOT NULL DEFAULT 'DRAFT'
  ```
- 草稿状态可自由修改，定稿状态受保护

7. **精简结构**
- 移除 `NopAiGeneratedApp` 应用生成表
- 移除 `NopAiVulnerabilityReport` 漏洞报告表
- 历史表统一使用 `[表名]History` 命名规范

### 状态字段说明
所有状态字段均遵循相同规范：
- **DRAFT**：草稿状态，允许AI自动覆盖
- **PRE_FINAL**：初步定稿，需人工确认修改
- **FINAL**：最终定稿，禁止AI自动修改

此设计确保在需求→生成→测试全流程中，关键产物都有明确的生命周期状态管理，同时通过 `chatResponseId` 字段实现全链路溯源能力。
