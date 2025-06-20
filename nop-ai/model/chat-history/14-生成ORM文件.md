【说明】
* 模型：deepseeki-r1

【提示词】
【任务目标】
你作为兼具软件技术与业务领域专业知识的资深软件架构师，需要依据给定需求完成数据库表结构设计。只用返回XML，不需要额外解释

【返回格式】

```xml
<orm>
    <dicts>
        <dict name="string" label="chinese">
            <description>string</description>
            <option value="digits" code="upper-case-java-var-name" label="chinese" description="string"/>
        </dict>
    </dicts>
    <entities>
        <entity name="english" displayName="chinese" db:estimatedRowCount="int" biz:type="string">
            <comment>description</comment>
            <columns>
                <column name="english" displayName="chinese" mandatory="boolean" primary="boolean" ext:dict="dict-name"
                        ext:defaultValue="string" stdDomain="std-domain" stdSqlType="sql-type" precision="int"
                        scale="int" orm:ref-table="table-name" orm:ref-prop="parent-to-children-prop"
                        orm:ref-prop-display-name="chinese"/>
            </columns>
        </entity>
    </entities>
</orm>
```

【设计规范】
## **一、核心规范**

1. **设计范围**： 排除通用表：`User`、`Role`、`Permission`、页面资源等。
2. **主键**： 固定使用字段名id, 类型为VARCHAR(36), 无业务含义
3. **字段定义**

- 命名：避免 SQL 关键字冲突（如 `order` → `order_no`）。
- 类型：`stdSqlType` 必填，仅允许：
  `VARCHAR/CHAR/DATE/TIME/DATETIME/TIMESTAMP/INTEGER/BIGINT/DECIMAL/BOOLEAN/VARBINARY`。
- 文件/图片：`std-domain` 标识（`image | file | fileList | imageList`）。
- 不使用JSON字段，完全展开为具体的字段定义

4. **字典字段**
- 状态类字段（如state,status等）需要定义字典
- 有限枚举值字段（≤10个固定选项），比如支付方式等需要定义字典
- 统一使用VARCHAR(4)类型
- 使用 `ext:dict` 指定字典，字典名格式：`业务域_用途`（如 `card_status`）。
- 字典项的value采用3位数字，比如010，而code采用全大写的英文字符，可以使用下划线分隔
- boolean类型不需要设置字典

5. **表类型标识**

- `biz:type` 必填，可选值：
  `entity | entity-detail | txn | txn-detail | report | report-detail | config | config-detail`。

## **二、关联关系**

1. **外键声明**

* 仅在**子表**字段上通过 `orm:ref-*` 属性声明。

2. **主表引用子表集合**

* **定义位置：** 在**子表定义**中，通过以下属性直接指定主表反向关联子表的集合属性：
  ```
  orm:ref-prop="items"                 // 主表上的集合属性名称
  orm:ref-prop-display-name="订单明细" // 集合属性在UI上的显示名称
  orm:ref-table="OrderItem"            // 关联的子表名称
  ```
* **禁止：** 禁止在 `<column>` 元素内定义集合属性。集合属性必须在主表模型上定义（通过上述方式在子表中配置），且主表模型上的该属性本身必须是标量字段（非集合）。
* 设置 `orm:ref-prop` **表示关联的子表数据规模较小，**允许（并建议）** 直接通过主表ID查询关联子表数据，并**
  一次性全量加载到内存**中进行操作。
* 一般主数据表并不会反向指向引用它的子表，比如ProductCategory并不会有一个集合属性orderDetails指向子表OrderDetail。在业务层面上没有这种需求，在技术层面这种集合也过大，不适合直接在内存中操作。

## **三、禁止字段**

Nop 平台自动管理以下字段，**禁止手动添加**：

- 逻辑删除：`IS_DELETED`
- 审计字段：`CREATE_TIME`/`UPDATE_TIME`/`CREATED_BY`
- 乐观锁版本号

## **四、数据量预估**

- 设置 `db:estimatedRowCount`：基于 **100 用户 × 1 年** 的业务量估算。

## **五、设计补充说明的性质**

"数据库设计补充"小节记录**需求分析中识别的潜在结构变更点**，需经技术评审后实施：
- **信息准确性**：建议字段/表可能被优化为扩展现有表、修改约束等替代方案。
- **设计参考性**：核心价值是阐明业务需求的数据支撑要求，**仅仅用于参考**，并不是硬性要求
- **实施决策**：最终结构由数据库工程师根据**性能/一致性/扩展性**评估确定。
  【完整性检查】
- 返回的XML包含所有数据库表定义，没有缺失

【需求描述】
### 3 系统功能需求

#### 3.2 核心功能模块

##### 3.2.1 项目管理
**功能说明**：创建维护AI项目实体，支持模板化创建机制
**数据实体**：NopAiProject (prototype_id: 模板项目ID)

###### 3.2.1.1 项目创建
- 提供空白项目创建入口
- 支持通过已有项目模板创建新项目
- 自动记录模板来源项目ID至prototype_id字段
- 新项目继承模板的初始配置项集合

###### 3.2.1.2 项目配置管理
- 每个项目独立维护配置表
- 配置项属性：名称(字符串)、值(字符串)、类型(枚举: TEXT/NUMBER/BOOLEAN)
- 支持配置项增删改查操作
- 配置项作用范围限定于当前项目

##### 3.2.2 AI模型管理
**功能说明**：维护第三方AI服务连接配置
**数据实体**：NopAiModel (modelId, provider, modelName, baseUrl, apiKey)

###### 3.2.2.1 模型注册
- 必填字段：modelId(主键)、provider(供应商)、modelName(型号名称)
- 可选字段：baseUrl(API端点)、apiKey(认证密钥)
- 支持OpenAI/Claude/本地模型等供应商类型
- ◆决策注释：provider字段采用预定义枚举值约束

###### 3.2.2.2 模型调用
- 通过modelId关联具体AI服务
- 自动组装API请求头包含apiKey
- 异常处理：连接超时/认证失败/配额不足

##### 3.2.3 需求管理
**功能说明**：结构化存储与AI项目关联的需求条目

###### 3.2.3.1 需求关联
- 需求条目必须绑定到具体AI项目
- 支持按章节分解存储需求内容
- 版本记录每次需求变更历史

###### 3.2.3.2 用例生成
- 自动生成Gherkin格式测试用例
- 支持人工维护测试步骤和数据

##### 3.2.4 知识库管理
**功能说明**：提供基础文档存储检索能力

###### 3.2.4.1 知识条目
- 支持纯文本/Markdown格式存储
- 按项目维度隔离知识库内容
- 提供标题关键词检索功能

##### 3.2.5 Prompt模板管理
**功能说明**：管理AI提示词模板及响应评估

###### 3.2.5.1 模板定义
- 支持变量插值语法（如${projectName}）
- 版本控制模板修改历史
- 分类存储业务领域模板

###### 3.2.5.2 多模型测试
- NopAiChatPrompt实体关联多个Response
- 单次执行向多个AI模型发送相同prompt
- 响应结果并排对比展示

###### 3.2.5.3 自动评分
- 百分制评分维度：正确性/性能/可读性/合规性
- 评分规则可配置权重比例
- ◆决策注释：默认权重 正确性50%，可读性30%，其他各10%

###### 3.2.5.4 响应缓存
- Response实体包含isCached布尔属性
- 标记后可复用历史响应结果
- 缓存有效期30天自动清理

###### 3.2.5.5 评分维度
- 正确性：输出与需求的匹配度
- 性能：响应时间及资源消耗
- 可读性：代码结构清晰度
- 合规性：安全规范符合性

##### 3.2.6 智能需求输入
**功能说明**：多模式需求采集与预处理

###### 3.2.6.1 输入方式
- 自然语言文本框（支持长文本）
- 结构化表单（字段：需求描述/业务域/复杂度）
- 关键词标签系统（自动提取实体标签）

###### 3.2.6.2 文件导入
- Markdown文件按H1-H6标题自动分解需求章节
- Excel文件通过Nop通用解析器转换结构
- ◆决策注释：Excel列名映射采用首行自动识别机制

###### 3.2.6.3 多轮澄清
- AI生成初版后显示"改进此需求"按钮
- 记录需求演进链路（最大5轮迭代）
- 每次迭代生成新版本号（v1.0 → v1.1）

###### 3.2.6.4 Excel解析
- 无预定义模板约束
- 自动识别表头与数据关系
- 空单元格处理为缺省值

###### 3.2.6.5 Markdown解析
- H1标题作为需求主题
- H2/H3标题生成子需求节点
- 代码块自动识别为技术约束

##### 3.2.7 AI生成DSL模型
**功能说明**：调用AI服务生成可执行DSL

###### 3.2.7.1 语言支持
- 输出格式：SQL/Java/JavaScript/XML/YAML/Markdown
- 自定义DSL通过XDef元模型校验
- 同一需求支持多语言并行生成

###### 3.2.7.2 分段生成
- 按ORM/API/UI等模块分步生成
- 自动检查模块间依赖关系
- 失败时触发局部重生成

###### 3.2.7.3 模块划分
- 采用Nop预定义模块：orm.xml/api.xml/page.yaml
- 用户不可修改模块划分规则
- 模块边界通过元数据强约束

###### 3.2.7.4 一致性保障
- 跨模块使用app:useCaseNo统一标识
- 关联模块自动插入引用标记
- 变更时级联更新依赖项

##### 3.2.8 模型编辑与调整
**功能说明**：可视化修改DSL模型

###### 3.2.8.1 编辑模式
- 修改后需点击"保存并渲染"更新视图
- 提供语法高亮和错误检查
- ◆决策注释：采用Monaco Editor实现代码编辑

###### 3.2.8.2 版本管理
- 内置版本树展示修改历史
- 差异比较工具支持三向对比
- 可回滚到任意历史版本

###### 3.2.8.3 多格式查看
- 支持XML/JSON/Excel格式转换
- HTML预览带折叠导航功能
- 导出为PDF保留格式结构

###### 3.2.8.4 依赖处理
- 跨DSL依赖通过逻辑编排配置协调
- 版本变更不自动更新依赖项
- 提供"检查依赖"手动触发更新

##### 3.2.9 应用生成与导出
**功能说明**：编译DSL输出可运行应用

###### 3.2.9.1 输出类型
- Web服务（SpringBoot可执行JAR）
- 批处理作业（Shell脚本+JAR）
- API端点（OpenAPI 3.0规范）

###### 3.2.9.2 Nop平台集成
- 调用nop-cli命令行工具
- 自动注入应用上下文
- 日志输出生成进度详情

###### 3.2.9.3 结果导出
- 生成ZIP压缩包包含完整工程
- 包结构：/src /pom.xml /docs
- 提供MD5校验码下载

###### 3.2.9.4 验收示例
- 输入"创建信用卡调额服务"
- 输出包含：ddl.sql（表结构）
- 输出包含：approve.bpmn（审批流程）
- 输出包含：CreditService.java（服务类）

##### 3.2.10 数据持久化
**功能说明**：系统数据存储管理

###### 3.2.10.1 DSL存储
- 所有用户DSL文件保存至数据库
- 存储格式：原始文本+AST解析树
- 版本快照保留周期≥180天

###### 3.2.10.2 表命名规范
- 数据库表前缀：NopAi
- 实体类前缀：NopAi
- 示例：NopAiProject, NopAiConfig

---

### 5 原始需求中的额外要求
1. 知识库管理仅需基础CRUD功能
2. Prompt模板变量使用${}语法
3. 所有数据库实体前缀为NopAi
4. 漏洞扫描规则库内置不可修改
5. 多模型评分维度权重可配置

---

### 6 跨模块的需求
1. **AI模型调用链**：
  - 需求输入模块收集原始需求
  - 调用Prompt管理模块渲染模板
  - 通过模型管理模块分发至AI服务
  - 结果返回至DSL生成模块
  - 输出存储至数据持久化模块
2. **降级处理流程**：
  - AI服务不可用时触发告警
  - 自动切换备用模型（需预先配置）
  - 全部失败时启用手动DSL编辑器
  - 手动输入内容生成新版本记录

---

### 7 需求冲突表
| 冲突点               | 冲突描述                          | 处理方案                     |
|----------------------|-----------------------------------|------------------------------|
| 实时渲染 vs 性能     | 实时预览需高频编译影响性能          | ◆决策：采用保存后渲染机制      |
| 多模型评分维度       | 评分标准未明确定义                  | ◆决策：采用四维度加权评分体系  |
| 离线编辑优先级       | 业务价值与实现成本不平衡            | ◆决策：标记为低优先级功能      |

---



# 数据库详细设计

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


【AI回复】
```xml
<orm>
    <dicts>
        <dict name="config_type" label="配置类型">
            <description>项目配置项的数据类型</description>
            <option value="001" code="TEXT" label="文本" description="字符串类型配置"/>
            <option value="002" code="NUMBER" label="数值" description="数字类型配置"/>
            <option value="003" code="BOOLEAN" label="布尔" description="真假值配置"/>
        </dict>
        <dict name="model_provider" label="AI供应商">
            <description>第三方AI服务提供商</description>
            <option value="001" code="OPENAI" label="OpenAI" description="OpenAI服务"/>
            <option value="002" code="CLAUDE" label="Claude" description="Claude服务"/>
            <option value="003" code="LOCAL" label="本地模型" description="本地部署模型"/>
        </dict>
        <dict name="requirement_type" label="需求类型">
            <description>需求条目的分类</description>
            <option value="001" code="OVERVIEW" label="总览" description="需求概览"/>
            <option value="002" code="MODULE" label="模块" description="功能模块需求"/>
            <option value="003" code="CASE" label="用例" description="测试用例需求"/>
        </dict>
        <dict name="status_type" label="状态类型">
            <description>数据记录状态</description>
            <option value="001" code="DRAFT" label="草稿" description="可编辑状态"/>
            <option value="002" code="PRE_FINAL" label="初步定稿" description="需人工确认"/>
            <option value="003" code="FINAL" label="最终定稿" description="不可修改状态"/>
        </dict>
        <dict name="file_format" label="文件格式">
            <description>知识库文档格式类型</description>
            <option value="001" code="TEXT" label="纯文本" description="无格式文本"/>
            <option value="002" code="MARKDOWN" label="Markdown" description="Markdown格式"/>
        </dict>
        <dict name="module_type" label="模块类型">
            <description>DSL生成模块分类</description>
            <option value="001" code="ORM" label="ORM模块" description="数据库模型"/>
            <option value="002" code="API" label="API模块" description="服务接口"/>
            <option value="003" code="UI" label="UI模块" description="页面定义"/>
        </dict>
    </dicts>
    <entities>
        <entity name="NopAiProject" displayName="AI项目" db:estimatedRowCount="1000" biz:type="entity">
            <comment>存储AI项目基本信息</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="name" displayName="项目名称" mandatory="true" stdSqlType="VARCHAR" precision="100"/>
                <column name="prototype_id" displayName="模板项目ID" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiProject" orm:ref-prop="prototypeProjects" orm:ref-prop-display-name="衍生项目"/>
            </columns>
        </entity>

        <entity name="NopAiConfig" displayName="项目配置" db:estimatedRowCount="20000" biz:type="entity-detail">
            <comment>项目级配置项管理</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="project_id" displayName="项目ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiProject" orm:ref-prop="configs" orm:ref-prop-display-name="配置项"/>
                <column name="config_name" displayName="配置名称" mandatory="true" stdSqlType="VARCHAR" precision="50"/>
                <column name="config_value" displayName="配置值" mandatory="true" stdSqlType="VARCHAR" precision="200"/>
                <column name="config_type" displayName="配置类型" mandatory="true" ext:dict="config_type" stdSqlType="VARCHAR" precision="4"/>
            </columns>
        </entity>

        <entity name="NopAiModel" displayName="AI模型" db:estimatedRowCount="100" biz:type="config">
            <comment>第三方AI模型注册信息</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="provider" displayName="供应商" mandatory="true" ext:dict="model_provider" stdSqlType="VARCHAR" precision="4"/>
                <column name="model_name" displayName="模型名称" mandatory="true" stdSqlType="VARCHAR" precision="50"/>
                <column name="base_url" displayName="API地址" stdSqlType="VARCHAR" precision="200"/>
                <column name="api_key" displayName="API密钥" stdSqlType="VARCHAR" precision="100"/>
            </columns>
        </entity>

        <entity name="NopAiRequirement" displayName="需求条目" db:estimatedRowCount="100000" biz:type="entity">
            <comment>结构化需求管理</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="project_id" displayName="项目ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiProject" orm:ref-prop="requirements" orm:ref-prop-display-name="需求列表"/>
                <column name="req_number" displayName="需求编号" mandatory="true" stdSqlType="VARCHAR" precision="20"/>
                <column name="title" displayName="需求标题" mandatory="true" stdSqlType="VARCHAR" precision="200"/>
                <column name="content" displayName="需求内容" stdSqlType="VARCHAR" precision="4000"/>
                <column name="version" displayName="当前版本" mandatory="true" stdSqlType="VARCHAR" precision="10"/>
                <column name="parent_id" displayName="父需求ID" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiRequirement" orm:ref-prop="children" orm:ref-prop-display-name="子需求"/>
                <column name="type" displayName="需求类型" mandatory="true" ext:dict="requirement_type" stdSqlType="VARCHAR" precision="4"/>
                <column name="ai_summary" displayName="AI摘要" stdSqlType="VARCHAR" precision="1000"/>
                <column name="status" displayName="状态" mandatory="true" ext:dict="status_type" stdSqlType="VARCHAR" precision="4" ext:defaultValue="001"/>
            </columns>
        </entity>

        <entity name="NopAiRequirementHistory" displayName="需求历史" db:estimatedRowCount="1000000" biz:type="entity-detail">
            <comment>需求变更版本记录</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="requirement_id" displayName="需求ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiRequirement" orm:ref-prop="historyRecords" orm:ref-prop-display-name="历史版本"/>
                <column name="version" displayName="版本号" mandatory="true" stdSqlType="VARCHAR" precision="10"/>
                <column name="content" displayName="需求内容" mandatory="true" stdSqlType="VARCHAR" precision="4000"/>
            </columns>
        </entity>

        <entity name="NopAiKnowledge" displayName="知识库" db:estimatedRowCount="100000" biz:type="entity">
            <comment>领域知识文档存储</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="project_id" displayName="项目ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiProject" orm:ref-prop="knowledgeItems" orm:ref-prop-display-name="知识条目"/>
                <column name="title" displayName="标题" mandatory="true" stdSqlType="VARCHAR" precision="200"/>
                <column name="content" displayName="内容" stdSqlType="VARCHAR" precision="4000"/>
                <column name="format" displayName="格式类型" mandatory="true" ext:dict="file_format" stdSqlType="VARCHAR" precision="4"/>
            </columns>
        </entity>

        <entity name="NopAiPromptTemplate" displayName="提示词模板" db:estimatedRowCount="1000" biz:type="config">
            <comment>AI提示词模板定义</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="name" displayName="模板名称" mandatory="true" stdSqlType="VARCHAR" precision="100"/>
                <column name="content" displayName="模板内容" mandatory="true" stdSqlType="VARCHAR" precision="4000"/>
                <column name="category" displayName="分类" stdSqlType="VARCHAR" precision="50"/>
                <column name="inputs" displayName="输入规范" stdSqlType="VARCHAR" precision="1000"/>
                <column name="outputs" displayName="输出规范" stdSqlType="VARCHAR" precision="1000"/>
            </columns>
        </entity>

        <entity name="NopAiPromptTemplateHistory" displayName="模板历史" db:estimatedRowCount="10000" biz:type="entity-detail">
            <comment>提示词模板版本记录</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="template_id" displayName="模板ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiPromptTemplate" orm:ref-prop="historyRecords" orm:ref-prop-display-name="历史版本"/>
                <column name="version" displayName="版本号" mandatory="true" stdSqlType="VARCHAR" precision="10"/>
                <column name="content" displayName="模板内容" mandatory="true" stdSqlType="VARCHAR" precision="4000"/>
            </columns>
        </entity>

        <entity name="NopAiChatRequest" displayName="对话请求" db:estimatedRowCount="365000" biz:type="txn">
            <comment>多模型测试请求记录</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="template_id" displayName="模板ID" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiPromptTemplate" orm:ref-prop="requests" orm:ref-prop-display-name="测试请求"/>
                <column name="prompt" displayName="完整提示词" mandatory="true" stdSqlType="VARCHAR" precision="4000"/>
                <column name="hash" displayName="内容哈希" stdSqlType="VARCHAR" precision="64"/>
                <column name="session_id" displayName="会话ID" stdSqlType="VARCHAR" precision="36"/>
                <column name="metadata" displayName="元数据" stdSqlType="VARCHAR" precision="2000"/>
            </columns>
        </entity>

        <entity name="NopAiChatResponse" displayName="响应结果" db:estimatedRowCount="1095000" biz:type="txn-detail">
            <comment>AI模型响应记录</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="request_id" displayName="请求ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiChatRequest" orm:ref-prop="responses" orm:ref-prop-display-name="响应列表"/>
                <column name="model_id" displayName="模型ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiModel" orm:ref-prop="responses" orm:ref-prop-display-name="调用记录"/>
                <column name="ai_provider" displayName="供应商" mandatory="true" ext:dict="model_provider" stdSqlType="VARCHAR" precision="4"/>
                <column name="ai_model" displayName="模型名称" mandatory="true" stdSqlType="VARCHAR" precision="50"/>
                <column name="response_content" displayName="响应内容" mandatory="true" stdSqlType="VARCHAR" precision="4000"/>
                <column name="total_tokens" displayName="总token数" stdSqlType="INTEGER"/>
                <column name="response_time_ms" displayName="响应时间(毫秒)" stdSqlType="INTEGER"/>
                <column name="correctness_score" displayName="正确性分" stdSqlType="DECIMAL" precision="5" scale="2"/>
                <column name="performance_score" displayName="性能分" stdSqlType="DECIMAL" precision="5" scale="2"/>
                <column name="readability_score" displayName="可读性分" stdSqlType="DECIMAL" precision="5" scale="2"/>
                <column name="compliance_score" displayName="合规性分" stdSqlType="DECIMAL" precision="5" scale="2"/>
            </columns>
        </entity>

        <entity name="NopAiGenFile" displayName="生成文件" db:estimatedRowCount="100000" biz:type="entity">
            <comment>AI生成的DSL模型文件</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="project_id" displayName="项目ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiProject" orm:ref-prop="generatedFiles" orm:ref-prop-display-name="生成文件"/>
                <column name="requirement_id" displayName="需求ID" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiRequirement" orm:ref-prop="generatedFiles" orm:ref-prop-display-name="关联文件"/>
                <column name="module_type" displayName="模块类型" mandatory="true" ext:dict="module_type" stdSqlType="VARCHAR" precision="4"/>
                <column name="original_content" displayName="原始内容" mandatory="true" stdSqlType="VARCHAR" precision="4000"/>
                <column name="file_path" displayName="文件路径" mandatory="true" stdSqlType="VARCHAR" precision="200"/>
                <column name="chat_response_id" displayName="响应ID" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiChatResponse" orm:ref-prop="generatedFiles" orm:ref-prop-display-name="生成产物"/>
                <column name="status" displayName="状态" mandatory="true" ext:dict="status_type" stdSqlType="VARCHAR" precision="4" ext:defaultValue="001"/>
            </columns>
        </entity>

        <entity name="NopAiGenFileHistory" displayName="文件历史" db:estimatedRowCount="1000000" biz:type="entity-detail">
            <comment>生成文件版本记录</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="gen_file_id" displayName="文件ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiGenFile" orm:ref-prop="historyRecords" orm:ref-prop-display-name="历史版本"/>
                <column name="version" displayName="版本号" mandatory="true" stdSqlType="VARCHAR" precision="10"/>
                <column name="original_content" displayName="原始内容" mandatory="true" stdSqlType="VARCHAR" precision="4000"/>
            </columns>
        </entity>

        <entity name="NopAiTestCase" displayName="测试用例" db:estimatedRowCount="1000000" biz:type="entity">
            <comment>生成的测试用例</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="requirement_id" displayName="需求ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiRequirement" orm:ref-prop="testCases" orm:ref-prop-display-name="测试用例"/>
                <column name="test_content" displayName="测试内容" mandatory="true" stdSqlType="VARCHAR" precision="2000"/>
                <column name="test_data" displayName="测试数据" stdSqlType="VARCHAR" precision="1000"/>
                <column name="gen_file_id" displayName="关联文件ID" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiGenFile" orm:ref-prop="testCases" orm:ref-prop-display-name="测试用例"/>
                <column name="chat_response_id" displayName="响应ID" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiChatResponse" orm:ref-prop="testCases" orm:ref-prop-display-name="生成用例"/>
                <column name="status" displayName="状态" mandatory="true" ext:dict="status_type" stdSqlType="VARCHAR" precision="4" ext:defaultValue="001"/>
            </columns>
        </entity>

        <entity name="NopAiTestResult" displayName="测试结果" db:estimatedRowCount="5000000" biz:type="txn-detail">
            <comment>测试用例执行结果</comment>
            <columns>
                <column name="id" displayName="主键" mandatory="true" primary="true" stdSqlType="VARCHAR" precision="36"/>
                <column name="test_case_id" displayName="测试用例ID" mandatory="true" stdSqlType="VARCHAR" precision="36"
                        orm:ref-table="NopAiTestCase" orm:ref-prop="testResults" orm:ref-prop-display-name="执行结果"/>
                <column name="execution_time" displayName="执行时间" mandatory="true" stdSqlType="TIMESTAMP"/>
                <column name="success" displayName="是否成功" mandatory="true" stdSqlType="BOOLEAN"/>
                <column name="error_log" displayName="错误日志" stdSqlType="VARCHAR" precision="2000"/>
            </columns>
        </entity>
    </entities>
</orm>
```
