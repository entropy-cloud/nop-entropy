# AI模型管理

- registerShortName: true
- appName: nop-ai
- entityPackageName: io.nop.ai.dao.entity
- basePackageName: io.nop.ai
- maven.groupId: io.github.entropy-cloud
- maven.artifactId: nop-ai
- allowIdAsColName: true

## 1 gen-extends

## 2 post-extends

## 3 域定义

## 4 字典定义

### 4.1 ai/message_type

- 中文名: 消息类型
- 描述: 对话消息的角色类型

#### 4.1.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 002 | 用户 | USER |  | 用户输入消息 | 
| 003 | 助手 | TOOL |  | AI回复消息 | 


### 4.2 ai/project_language

- 中文名: 项目语言
- 描述: 项目使用的编程语言类型

#### 4.2.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | Java | JAVA |  | Java语言项目 | 
| 002 | Python | PYTHON |  | Python语言项目 | 
| 003 | JavaScript | JAVASCRIPT |  | JavaScript/TypeScript项目 | 
| 004 | Go | GO |  | Go语言项目 | 
| 005 | C# | CSHARP |  | C#语言项目 | 
| 006 | C++ | CPP |  | C++语言项目 | 
| 007 | 其他语言 | OTHER |  | 其他编程语言 | 


### 4.3 ai/rule_type

- 中文名: 规则类型
- 描述: 项目规则分类

#### 4.3.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | 编码规范 | CODING_STYLE |  | 代码格式和命名规则 | 
| 002 | 安全规则 | SECURITY |  | 安全检测和防护规则 | 
| 003 | 性能规则 | PERFORMANCE |  | 性能优化规则 | 
| 004 | 架构规则 | ARCHITECTURE |  | 系统架构约束 | 
| 005 | 自定义规则 | CUSTOM |  | 用户自定义规则 | 


### 4.4 ai/config_type

- 中文名: 配置类型
- 描述: 项目配置项的数据类型

#### 4.4.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | 文本 | TEXT |  | 字符串类型配置 | 
| 002 | 数值 | NUMBER |  | 数字类型配置 | 
| 003 | 布尔 | BOOLEAN |  | 真假值配置 | 


### 4.5 ai/model_provider

- 中文名: AI供应商
- 描述: 第三方AI服务提供商

#### 4.5.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | OpenAI | OPENAI |  | OpenAI服务 | 
| 002 | Claude | CLAUDE |  | Claude服务 | 
| 003 | 本地模型 | LOCAL |  | 本地部署模型 | 


### 4.6 ai/requirement_type

- 中文名: 需求类型
- 描述: 需求条目的分类

#### 4.6.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | 总览 | OVERVIEW |  | 需求概览 | 
| 002 | 模块 | MODULE |  | 功能模块需求 | 
| 003 | 用例 | CASE |  | 测试用例需求 | 


### 4.7 ai/status_type

- 中文名: 状态类型
- 描述: 数据记录状态

#### 4.7.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | 草稿 | DRAFT |  | 可编辑状态 | 
| 002 | 初步定稿 | PRE_FINAL |  | 需人工确认 | 
| 003 | 最终定稿 | FINAL |  | 不可修改状态 | 


### 4.8 ai/file_format

- 中文名: 文件格式
- 描述: 知识库文档格式类型

#### 4.8.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | 纯文本 | TEXT |  | 无格式文本 | 
| 002 | Markdown | MARKDOWN |  | Markdown格式 | 


### 4.9 ai/module_type

- 中文名: 模块类型
- 描述: DSL生成模块分类

#### 4.9.1 字典项

|值|名称|代码|英文名|描述|
| --- | --- | --- | --- | --- | 
| 001 | ORM模块 | ORM |  | 数据库模型 | 
| 002 | API模块 | API |  | 服务接口 | 
| 003 | UI模块 | UI |  | 页面定义 | 


## 5 实体定义

### 5.1 NopAiProject

- 表名: nop_ai_project
- 是否视图: false
- 类名: NopAiProject
- 中文名: AI项目
- 备注: 存储AI项目基本信息
- 查询空间: default

#### 5.1.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | language | language |  | 项目语言 |  |  |  | VARCHAR | 4 |  | ai/project_language | 项目使用的编程语言类型：JAVA, PYTHON等 |  |  |  | 
| 3 |  | false | true | name | name |  | 项目名称 |  |  |  | VARCHAR | 100 |  |  |  |  |  |  | 
| 4 |  | false | false | prototype_id | prototypeId |  | 模板项目ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 5 |  | false | false | project_dir | projectDir |  | 项目目录 |  |  |  | VARCHAR | 400 |  |  | 项目在文件系统中的存储路径，例如：/data/projects/order-system |  | textarea |  | 

#### 5.1.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | prototype | NopAiProject |  | prototypeId=id, |  |  |  |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-many | projectRules | NopAiProjectRule | project | id=projectId, | 项目规则 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | configs | NopAiProjectConfig | project | id=projectId, | 配置项 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | requirements | NopAiRequirement | project | id=projectId, | 需求列表 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | generatedFiles | NopAiGenFile | project | id=projectId, | 生成文件 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.1.3 唯一键列表

#### 5.1.4 别名列表

#### 5.1.5 计算属性列表

#### 5.1.6 索引列表


### 5.2 NopAiProjectRule

- 表名: nop_ai_project_rule
- 是否视图: false
- 类名: NopAiProjectRule
- 中文名: 项目规则
- 备注: 存储项目规则配置
- 查询空间: default

#### 5.2.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | project_id | projectId |  | 项目ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | false | knowledge_id | knowledgeId |  | 知识库ID |  |  |  | VARCHAR | 36 |  |  | 关联的知识库条目，规则可能基于某个知识文档 |  |  |  | 
| 4 |  | false | true | rule_name | ruleName |  | 规则名称 |  |  |  | VARCHAR | 100 |  |  | 规则标识名称，如：code_format_rule, naming_convention |  |  |  | 
| 5 |  | false | true | rule_content | ruleContent |  | 规则内容 |  |  |  | VARCHAR | 4000 |  |  | 规则的具体内容（JSON/YAML/文本） |  | textarea |  | 
| 6 |  | false | false | rule_type | ruleType |  | 规则类型 |  |  |  | VARCHAR | 50 |  |  | 规则分类，如：CODING_STYLE, SECURITY, PERFORMANCE |  |  |  | 
| 7 |  | false | true | is_active | isActive |  | 是否启用 |  |  |  | BOOLEAN |  |  |  |  |  |  |  | 

#### 5.2.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | project | NopAiProject | projectRules | projectId=id, |  |  | 项目规则 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-one | knowledge | NopAiKnowledge | relatedRules | knowledgeId=id, |  |  | 关联规则 |  |  |  |  |  |  | false | false |  |  |  | 

#### 5.2.3 唯一键列表

#### 5.2.4 别名列表

#### 5.2.5 计算属性列表

#### 5.2.6 索引列表


### 5.3 NopAiProjectConfig

- 表名: nop_ai_project_config
- 是否视图: false
- 类名: NopAiProjectConfig
- 中文名: 项目配置
- 备注: 项目级配置项管理
- 查询空间: default

#### 5.3.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | project_id | projectId |  | 项目ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | config_name | configName |  | 配置名称 |  |  |  | VARCHAR | 50 |  |  |  |  |  |  | 
| 4 |  | false | true | config_value | configValue |  | 配置值 |  |  |  | VARCHAR | 200 |  |  |  |  |  |  | 
| 5 |  | false | true | config_type | configType |  | 配置类型 |  |  |  | VARCHAR | 4 |  | ai/config_type |  |  |  |  | 

#### 5.3.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | project | NopAiProject | configs | projectId=id, |  |  | 配置项 |  |  |  |  |  |  | false | false |  |  |  | 

#### 5.3.3 唯一键列表

#### 5.3.4 别名列表

#### 5.3.5 计算属性列表

#### 5.3.6 索引列表


### 5.4 NopAiModel

- 表名: nop_ai_model
- 是否视图: false
- 类名: NopAiModel
- 中文名: AI模型
- 备注: 第三方AI模型注册信息
- 查询空间: default

#### 5.4.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | provider | provider |  | 供应商 |  |  |  | VARCHAR | 4 |  | ai/model_provider |  |  |  |  | 
| 3 |  | false | true | model_name | modelName |  | 模型名称 |  |  |  | VARCHAR | 50 |  |  |  |  |  |  | 
| 4 |  | false | false | base_url | baseUrl |  | API地址 |  |  |  | VARCHAR | 200 |  |  |  |  |  |  | 
| 5 |  | false | false | api_key | apiKey |  | API密钥 |  |  |  | VARCHAR | 100 |  |  |  |  |  |  | 

#### 5.4.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-many | responses | NopAiChatResponse | model | id=modelId, | 调用记录 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.4.3 唯一键列表

#### 5.4.4 别名列表

#### 5.4.5 计算属性列表

#### 5.4.6 索引列表


### 5.5 NopAiRequirement

- 表名: nop_ai_requirement
- 是否视图: false
- 类名: NopAiRequirement
- 中文名: 需求条目
- 备注: 结构化需求管理
- 查询空间: default

#### 5.5.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | project_id | projectId |  | 项目ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | req_number | reqNumber |  | 需求编号 |  |  |  | VARCHAR | 20 |  |  |  |  |  |  | 
| 4 |  | false | true | title | title |  | 需求标题 |  |  |  | VARCHAR | 200 |  |  |  |  |  |  | 
| 5 |  | false | false | content | content |  | 需求内容 |  |  |  | VARCHAR | 4000 |  |  |  |  | textarea |  | 
| 6 |  | false | true | version | version |  | 当前版本 |  |  |  | VARCHAR | 10 |  |  |  |  |  |  | 
| 7 |  | false | false | parent_id | parentId |  | 父需求ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 8 |  | false | true | type | type |  | 需求类型 |  |  |  | VARCHAR | 4 |  | ai/requirement_type |  |  |  |  | 
| 9 |  | false | false | ai_summary | aiSummary |  | AI摘要 |  |  |  | VARCHAR | 1000 |  |  |  |  | textarea |  | 
| 10 |  | false | true | status | status |  | 状态 |  |  |  | VARCHAR | 4 |  | ai/status_type |  |  |  |  | 

#### 5.5.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | project | NopAiProject | requirements | projectId=id, |  |  | 需求列表 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-one | parent | NopAiRequirement | children | parentId=id, |  |  | 子需求 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-many | children | NopAiRequirement | parent | id=parentId, | 子需求 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | historyRecords | NopAiRequirementHistory | requirement | id=requirementId, | 历史版本 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | generatedFiles | NopAiGenFile | requirement | id=requirementId, | 关联文件 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | testCases | NopAiTestCase | requirement | id=requirementId, | 测试用例 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.5.3 唯一键列表

#### 5.5.4 别名列表

#### 5.5.5 计算属性列表

#### 5.5.6 索引列表


### 5.6 NopAiRequirementHistory

- 表名: nop_ai_requirement_history
- 是否视图: false
- 类名: NopAiRequirementHistory
- 中文名: 需求历史
- 备注: 需求变更版本记录
- 查询空间: default

#### 5.6.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | requirement_id | requirementId |  | 需求ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | version | version |  | 版本号 |  |  |  | VARCHAR | 10 |  |  |  |  |  |  | 
| 4 |  | false | true | content | content |  | 需求内容 |  |  |  | VARCHAR | 4000 |  |  |  |  | textarea |  | 

#### 5.6.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | requirement | NopAiRequirement | historyRecords | requirementId=id, |  |  | 历史版本 |  |  |  |  |  |  | false | false |  |  |  | 

#### 5.6.3 唯一键列表

#### 5.6.4 别名列表

#### 5.6.5 计算属性列表

#### 5.6.6 索引列表


### 5.7 NopAiKnowledge

- 表名: nop_ai_knowledge
- 是否视图: false
- 类名: NopAiKnowledge
- 中文名: 知识库
- 备注: 领域知识文档存储
- 查询空间: default

#### 5.7.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | title | title |  | 标题 |  |  |  | VARCHAR | 200 |  |  |  |  |  |  | 
| 3 |  | false | false | content | content |  | 内容 |  |  |  | VARCHAR | 4000 |  |  |  |  | textarea |  | 
| 4 |  | false | true | format | format |  | 格式类型 |  |  |  | VARCHAR | 4 |  | ai/file_format |  |  |  |  | 

#### 5.7.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-many | relatedRules | NopAiProjectRule | knowledge | id=knowledgeId, | 关联规则 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.7.3 唯一键列表

#### 5.7.4 别名列表

#### 5.7.5 计算属性列表

#### 5.7.6 索引列表


### 5.8 NopAiPromptTemplate

- 表名: nop_ai_prompt_template
- 是否视图: false
- 类名: NopAiPromptTemplate
- 中文名: 提示词模板
- 备注: AI提示词模板定义
- 查询空间: default

#### 5.8.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | name | name |  | 模板名称 |  |  |  | VARCHAR | 100 |  |  |  |  |  |  | 
| 3 |  | false | true | content | content |  | 模板内容 |  |  |  | VARCHAR | 4000 |  |  |  |  | textarea |  | 
| 4 |  | false | false | category | category |  | 分类 |  |  |  | VARCHAR | 50 |  |  |  |  |  |  | 
| 5 |  | false | false | inputs | inputs |  | 输入规范 |  |  |  | VARCHAR | 1000 |  |  |  |  | textarea |  | 
| 6 |  | false | false | outputs | outputs |  | 输出规范 |  |  |  | VARCHAR | 1000 |  |  |  |  | textarea |  | 

#### 5.8.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-many | historyRecords | NopAiPromptTemplateHistory | template | id=templateId, | 历史版本 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | requests | NopAiChatRequest | template | id=templateId, | 测试请求 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.8.3 唯一键列表

#### 5.8.4 别名列表

#### 5.8.5 计算属性列表

#### 5.8.6 索引列表


### 5.9 NopAiPromptTemplateHistory

- 表名: nop_ai_prompt_template_history
- 是否视图: false
- 类名: NopAiPromptTemplateHistory
- 中文名: 模板历史
- 备注: 提示词模板版本记录
- 查询空间: default

#### 5.9.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | template_id | templateId |  | 模板ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | version | version |  | 版本号 |  |  |  | VARCHAR | 10 |  |  |  |  |  |  | 
| 4 |  | false | true | content | content |  | 模板内容 |  |  |  | VARCHAR | 4000 |  |  |  |  | textarea |  | 

#### 5.9.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | template | NopAiPromptTemplate | historyRecords | templateId=id, |  |  | 历史版本 |  |  |  |  |  |  | false | false |  |  |  | 

#### 5.9.3 唯一键列表

#### 5.9.4 别名列表

#### 5.9.5 计算属性列表

#### 5.9.6 索引列表


### 5.10 NopAiChatRequest

- 表名: nop_ai_chat_request
- 是否视图: false
- 类名: NopAiChatRequest
- 中文名: 对话请求
- 备注: 多模型测试请求记录
- 查询空间: default

#### 5.10.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | false | template_id | templateId |  | 模板ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | session_id | sessionId |  | 会话ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 4 |  | false | false | system_prompt | systemPrompt |  | 系统提示词 |  |  |  | VARCHAR | 65536 |  |  |  |  | textarea |  | 
| 5 |  | false | true | user_prompt | userPrompt |  | 用户提示词 |  |  |  | VARCHAR | 65536 |  |  |  |  | textarea |  | 
| 6 |  | false | true | message_type | messageType |  | 消息类型 |  |  |  | VARCHAR | 4 |  | ai/message_type |  |  |  |  | 
| 7 |  | false | true | request_timestamp | requestTimestamp |  | 请求时间戳 |  |  |  | TIMESTAMP |  |  |  |  |  |  |  | 
| 8 |  | false | true | hash | hash |  | 内容哈希 |  |  |  | VARCHAR | 64 |  |  |  |  |  |  | 
| 9 |  | false | false | metadata | metadata |  | 元数据 |  |  |  | VARCHAR | 2000 |  |  |  |  | textarea |  | 

#### 5.10.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | template | NopAiPromptTemplate | requests | templateId=id, |  |  | 测试请求 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-many | responses | NopAiChatResponse | request | id=requestId, | 响应列表 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.10.3 唯一键列表

#### 5.10.4 别名列表

#### 5.10.5 计算属性列表

#### 5.10.6 索引列表


### 5.11 NopAiChatResponse

- 表名: nop_ai_chat_response
- 是否视图: false
- 类名: NopAiChatResponse
- 中文名: 响应结果
- 备注: AI模型响应记录
- 查询空间: default

#### 5.11.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | request_id | requestId |  | 请求ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | session_id | sessionId |  | 会话ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 4 |  | false | true | model_id | modelId |  | 模型ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 5 |  | false | true | ai_provider | aiProvider |  | 供应商 |  |  |  | VARCHAR | 4 |  | ai/model_provider |  |  |  |  | 
| 6 |  | false | true | ai_model | aiModel |  | 模型名称 |  |  |  | VARCHAR | 50 |  |  |  |  |  |  | 
| 7 |  | false | true | response_content | responseContent |  | 响应内容 |  |  |  | VARCHAR | 65536 |  |  |  |  | textarea |  | 
| 8 |  | false | true | response_timestamp | responseTimestamp |  | 响应时间戳 |  |  |  | TIMESTAMP |  |  |  |  |  |  |  | 
| 9 |  | false | false | prompt_tokens | promptTokens |  | 请求Token数 |  |  |  | INTEGER |  |  |  | 请求消息消耗的Token数量 |  |  |  | 
| 10 |  | false | false | completion_tokens | completionTokens |  | 响应Token数 |  |  |  | INTEGER |  |  |  | 响应消息消耗的Token数量 |  |  |  | 
| 11 |  | false | false | response_duration_ms | responseDurationMs |  | 响应耗时(毫秒) |  |  |  | INTEGER |  |  |  |  |  |  |  | 
| 12 |  | false | false | correctness_score | correctnessScore |  | 正确性分 |  |  |  | DECIMAL | 5 | 2 |  |  |  |  |  | 
| 13 |  | false | false | performance_score | performanceScore |  | 性能分 |  |  |  | DECIMAL | 5 | 2 |  |  |  |  |  | 
| 14 |  | false | false | readability_score | readabilityScore |  | 可读性分 |  |  |  | DECIMAL | 5 | 2 |  |  |  |  |  | 
| 15 |  | false | false | compliance_score | complianceScore |  | 合规性分 |  |  |  | DECIMAL | 5 | 2 |  |  |  |  |  | 

#### 5.11.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | request | NopAiChatRequest | responses | requestId=id, |  |  | 响应列表 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-one | model | NopAiModel | responses | modelId=id, |  |  | 调用记录 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-many | generatedFiles | NopAiGenFile | chatResponse | id=chatResponseId, | 生成产物 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | testCases | NopAiTestCase | chatResponse | id=chatResponseId, | 生成用例 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.11.3 唯一键列表

#### 5.11.4 别名列表

#### 5.11.5 计算属性列表

#### 5.11.6 索引列表


### 5.12 NopAiGenFile

- 表名: nop_ai_gen_file
- 是否视图: false
- 类名: NopAiGenFile
- 中文名: 生成文件
- 备注: AI生成的DSL模型文件
- 查询空间: default

#### 5.12.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | project_id | projectId |  | 项目ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | false | requirement_id | requirementId |  | 需求ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 4 |  | false | true | module_type | moduleType |  | 模块类型 |  |  |  | VARCHAR | 4 |  | ai/module_type |  |  |  |  | 
| 5 |  | false | true | content | content |  | 文件内容 |  |  |  | VARCHAR | 65536 |  |  | 当前文件内容 |  | textarea |  | 
| 6 |  | false | true | file_path | filePath |  | 文件路径 |  |  |  | VARCHAR | 200 |  |  |  |  |  |  | 
| 7 |  | false | false | chat_response_id | chatResponseId |  | 响应ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 8 |  | false | true | status | status |  | 状态 |  |  |  | VARCHAR | 4 |  | ai/status_type |  |  |  |  | 

#### 5.12.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | project | NopAiProject | generatedFiles | projectId=id, |  |  | 生成文件 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-one | requirement | NopAiRequirement | generatedFiles | requirementId=id, |  |  | 关联文件 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-one | chatResponse | NopAiChatResponse | generatedFiles | chatResponseId=id, |  |  | 生成产物 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-many | historyRecords | NopAiGenFileHistory | genFile | id=genFileId, | 历史版本 |  |  |  |  |  |  |  |  |  |  |  |  |  | 
|  | to-many | testCases | NopAiTestCase | genFile | id=genFileId, | 测试用例 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.12.3 唯一键列表

#### 5.12.4 别名列表

#### 5.12.5 计算属性列表

#### 5.12.6 索引列表


### 5.13 NopAiGenFileHistory

- 表名: nop_ai_gen_file_history
- 是否视图: false
- 类名: NopAiGenFileHistory
- 中文名: 文件历史
- 备注: 生成文件版本记录
- 查询空间: default

#### 5.13.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | gen_file_id | genFileId |  | 文件ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | version | version |  | 版本号 |  |  |  | VARCHAR | 10 |  |  |  |  |  |  | 
| 4 |  | false | true | content | content |  | 文件内容 |  |  |  | VARCHAR | 65536 |  |  | 当前文件内容 |  | textarea |  | 

#### 5.13.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | genFile | NopAiGenFile | historyRecords | genFileId=id, |  |  | 历史版本 |  |  |  |  |  |  | false | false |  |  |  | 

#### 5.13.3 唯一键列表

#### 5.13.4 别名列表

#### 5.13.5 计算属性列表

#### 5.13.6 索引列表


### 5.14 NopAiTestCase

- 表名: nop_ai_test_case
- 是否视图: false
- 类名: NopAiTestCase
- 中文名: 测试用例
- 备注: 生成的测试用例
- 查询空间: default

#### 5.14.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | requirement_id | requirementId |  | 需求ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | test_content | testContent |  | 测试内容 |  |  |  | VARCHAR | 2000 |  |  |  |  | textarea |  | 
| 4 |  | false | false | test_data | testData |  | 测试数据 |  |  |  | VARCHAR | 1000 |  |  |  |  | textarea |  | 
| 5 |  | false | false | gen_file_id | genFileId |  | 关联文件ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 6 |  | false | false | chat_response_id | chatResponseId |  | 响应ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 7 |  | false | true | status | status |  | 状态 |  |  |  | VARCHAR | 4 |  | ai/status_type |  |  |  |  | 

#### 5.14.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | requirement | NopAiRequirement | testCases | requirementId=id, |  |  | 测试用例 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-one | genFile | NopAiGenFile | testCases | genFileId=id, |  |  | 测试用例 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-one | chatResponse | NopAiChatResponse | testCases | chatResponseId=id, |  |  | 生成用例 |  |  |  |  |  |  | false | false |  |  |  | 
|  | to-many | testResults | NopAiTestResult | testCase | id=testCaseId, | 执行结果 |  |  |  |  |  |  |  |  |  |  |  |  |  | 

#### 5.14.3 唯一键列表

#### 5.14.4 别名列表

#### 5.14.5 计算属性列表

#### 5.14.6 索引列表


### 5.15 NopAiTestResult

- 表名: nop_ai_test_result
- 是否视图: false
- 类名: NopAiTestResult
- 中文名: 测试结果
- 备注: 测试用例执行结果
- 查询空间: default

#### 5.15.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 2 |  | false | true | test_case_id | testCaseId |  | 测试用例ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  | 
| 3 |  | false | true | execution_time | executionTime |  | 执行时间 |  |  |  | TIMESTAMP |  |  |  |  |  |  |  | 
| 4 |  | false | true | success | success |  | 是否成功 |  |  |  | BOOLEAN |  |  |  |  |  |  |  | 
| 5 |  | false | false | error_log | errorLog |  | 错误日志 |  |  |  | VARCHAR | 2000 |  |  |  |  | textarea |  | 

#### 5.15.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|英文名|关联中文名|关联英文名|备注|多对多属性名1|多对多属性名2|多对多属性显示名1|多对多属性显示名2|反向依赖|忽略依赖|控件|排序条件|唯一标识|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | 
|  | to-one | testCase | NopAiTestCase | testResults | testCaseId=id, |  |  | 执行结果 |  |  |  |  |  |  | false | false |  |  |  | 

#### 5.15.3 唯一键列表

#### 5.15.4 别名列表

#### 5.15.5 计算属性列表

#### 5.15.6 索引列表



