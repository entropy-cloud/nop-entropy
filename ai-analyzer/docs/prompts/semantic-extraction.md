# 语义提取提示词设计

## 1. 功能语义提取

### 1.1 方法功能描述提取
**提示词模板**：
```
请为以下Java方法生成详细的功能描述：

{方法代码}

所属类：{类名}
上下文：{类职责描述}

请严格按照 `config/ai-prompt-models.xdef` 中定义的 `MethodFunctionDescription` 模型结构提供JSON格式的响应。

**重要说明：所有Nop平台相关知识必须参考 `docs-for-ai` 目录下的文档，确保XDef定义符合规范。**
```

## 返回格式

**XDef模型定义**：参考 `config/ai-prompt-models.xdef` 中的 `MethodFunctionDescription` 模型

**JSON格式示例**：
```json
{
  "coreFunction": {
    "summary": "一句话总结方法的主要功能",
    "detailedDescription": "详细描述方法的具体作用"
  },
  "businessSemantics": {
    "scenario": "在什么业务场景下使用",
    "rules": ["实现的业务规则和逻辑"],
    "value": "为业务提供的价值"
  },
  "technicalSemantics": {
    "implementation": "使用的技术手段和算法",
    "dataProcessing": "如何处理输入数据和生成输出",
    "constraints": ["技术实现上的限制和约束"]
  },
  "usageGuidance": {
    "timing": "应该在什么时机调用",
    "parameters": [
      {
        "name": "参数名称",
        "meaning": "参数具体含义",
        "constraints": ["参数约束条件"]
      }
    ],
    "returnValue": "返回值的含义和使用方式"
  },
  "semanticTags": {
    "functional": ["功能标签"],
    "technical": ["技术标签"],
    "business": ["业务标签"]
  }
}
```

**应用对象**：单个方法

### 1.2 类职责描述提取
**提示词模板**：
```
请为以下Java类生成详细的职责描述：

{类代码}

请严格按照 `config/ai-prompt-models.xdef` 中定义的 `ClassResponsibilityDescription` 模型结构提供JSON格式的响应。

**重要说明：所有Nop平台相关知识必须参考 `docs-for-ai` 目录下的文档，确保XDef定义符合规范。**
```

## 返回格式

**XDef模型定义**：参考 `config/ai-prompt-models.xdef` 中的 `ClassResponsibilityDescription` 模型

**JSON格式示例**：
```json
{
  
  <xdef:prop name="architecturalRole" type="object" mandatory="true">
    <!-- 架构角色 -->
    <xdef:prop name="layer" type="string" mandatory="true" 
                comment="属于哪一层（表现层/业务层/数据层）"/>
    <xdef:prop name="patternRole" type="string" 
                comment="在设计模式中扮演什么角色"/>
    <xdef:prop name="integrationPoints" type="array" item-type="string" 
                comment="与系统其他部分的集成方式"/>
  </xdef:prop>
  
  <xdef:prop name="businessDomainMapping" type="object" mandatory="true">
    <!-- 业务领域映射 -->
    <xdef:prop name="domainConcepts" type="array" item-type="string" 
                comment="对应的业务领域概念"/>
    <xdef:prop name="businessProcesses" type="array" item-type="string" 
                comment="参与的业务流程"/>
    <xdef:prop name="businessRules" type="array" item-type="string" 
                comment="实现的业务规则"/>
  </xdef:prop>
  
  <xdef:prop name="technicalCharacteristics" type="object" mandatory="true">
    <!-- 技术特性 -->
    <xdef:prop name="techStack" type="array" item-type="string" 
                comment="使用的技术栈"/>
    <xdef:prop name="performanceFeatures" type="array" item-type="string" 
                comment="性能方面的特点"/>
    <xdef:prop name="extensibility" type="string" 
                comment="扩展和定制的方式"/>
  </xdef:prop>
  
  <xdef:prop name="semanticSummary" type="object" mandatory="true">
    <!-- 语义摘要 -->
    <xdef:prop name="oneSentence" type="string" mandatory="true" 
                comment="用一句话概括类的职责"/>
    <xdef:prop name="keywords" type="array" item-type="string" mandatory="true" 
                comment="3-5个关键词描述类的特性"/>
    <xdef:prop name="analogy" type="string" 
                comment="用类比的方式说明类的角色"/>
  </xdef:prop>
</xdef:model>

请严格按照上述XDef定义的结构返回JSON数据。
```

**输出格式**：
```json
{
  "coreResponsibilities": {
    "mainFunction": "类的主要功能是什么",
    "responsibilityScope": "类的职责边界在哪里",
    "irreplaceability": "为什么需要这个类"
  },
  "architecturalRole": {
    "layer": "属于哪一层（表现层/业务层/数据层）",
    "patternRole": "在设计模式中扮演什么角色",
    "integrationPoints": ["与系统其他部分的集成方式"]
  },
  "businessDomainMapping": {
    "domainConcepts": ["对应的业务领域概念"],
    "businessProcesses": ["参与的业务流程"],
    "businessRules": ["实现的业务规则"]
  },
  "technicalCharacteristics": {
    "techStack": ["使用的技术栈"],
    "performanceFeatures": ["性能方面的特点"],
    "extensibility": "扩展和定制的方式"
  },
  "semanticSummary": {
    "oneSentence": "用一句话概括类的职责",
    "keywords": ["关键词1", "关键词2", "关键词3"],
    "analogy": "用类比的方式说明类的角色"
  }
}
```

## 2. 使用场景语义提取

### 2.1 API使用场景分析
**提示词模板**：
```
请分析以下API的典型使用场景：

{API代码}

请提供：

1. 主要使用场景：
   - 场景1：场景描述、使用方式、示例代码
   - 场景2：场景描述、使用方式、示例代码
   - 场景3：场景描述、使用方式、示例代码

2. 场景特征：
   - 触发条件：什么情况下会使用这个API
   - 输入特征：典型的输入数据特征
   - 输出特征：期望的输出结果特征

3. 场景关联：
   - 前置场景：使用前需要完成什么
   - 后置场景：使用后会触发什么
   - 并行场景：可以与其他什么API同时使用

4. 场景复杂度：
   - 使用难度：简单/中等/复杂
   - 配置复杂度：需要多少配置
   - 集成难度：与其他组件集成的难度
```

### 2.2 配置语义提取
**提示词模板**：
```
请分析以下配置项的语义含义：

{配置内容}

请提供：

1. 配置作用：
   - 功能描述：这个配置控制什么功能
   - 影响范围：配置变更会影响哪些组件
   - 默认行为：默认值对应的行为

2. 配置约束：
   - 取值范围：有效的取值范围
   - 依赖关系：依赖的其他配置
   - 冲突配置：可能冲突的配置项

3. 使用建议：
   - 典型取值：常见的配置值和使用场景
   - 性能影响：不同取值对性能的影响
   - 安全考虑：安全相关的配置建议

4. 语义标签：
   - 功能类型：[性能调优]、[功能开关]、[资源限制]等
   - 影响级别：[关键]、[重要]、[一般]等
   - 变更频率：[频繁]、[偶尔]、[很少]等
```

## 3. 设计语义提取

### 3.1 架构设计语义
**提示词模板**：
```
请分析以下代码体现的架构设计语义：

{代码片段}

请识别：

1. 架构模式：
   - 分层架构：表现层/业务层/数据层的划分
   - 微服务架构：服务边界和通信方式
   - 事件驱动架构：事件产生和消费模式

2. 设计原则：
   - 单一职责：是否遵循单一职责原则
   - 开闭原则：是否易于扩展
   - 依赖倒置：依赖关系的设计

3. 质量属性：
   - 可维护性：代码是否易于维护
   - 可测试性：是否易于测试
   - 可扩展性：是否易于扩展新功能

4. 设计决策：
   - 技术选型：为什么选择这些技术
   - 接口设计：接口设计的考虑
   - 数据模型：数据模型的设计思路
```

### 3.2 业务语义提取
**提示词模板**：
```
请从以下代码中提取业务语义：

{业务代码}

请分析：

1. 业务概念：
   - 领域实体：代码中涉及的业务实体
   - 业务规则：实现的业务规则和约束
   - 业务流程：参与的业务流程步骤

2. 业务逻辑：
   - 计算逻辑：业务计算规则
   - 验证逻辑：业务数据验证规则
   - 转换逻辑：业务数据转换规则

3. 业务上下文：
   - 业务场景：适用的业务场景
   - 业务约束：业务上的限制条件
   - 业务价值：为业务创造的价值

4. 业务术语：
   - 专业术语：使用的业务术语
   - 术语映射：代码概念与业务概念的映射
   - 术语解释：术语的具体含义
```

## 4. 性能语义提取

### 4.1 性能特征分析
**提示词模板**：
```
请分析以下代码的性能特征：

{代码内容}

请评估：

1. 时间复杂度：
   - 最坏情况：最坏情况下的时间复杂度
   - 平均情况：平均情况下的时间复杂度
   - 空间复杂度：内存使用情况

2. 性能瓶颈：
   - 潜在瓶颈：可能的性能瓶颈点
   - 资源消耗：CPU、内存、IO等资源消耗
   - 并发性能：多线程环境下的性能

3. 优化建议：
   - 算法优化：算法层面的优化建议
   - 数据结构优化：数据结构选择建议
   - 缓存策略：缓存使用的建议

4. 性能语义标签：
   - 性能级别：[高性能]、[中等性能]、[低性能]
   - 资源类型：[CPU密集型]、[IO密集型]、[内存密集型]
   - 优化优先级：[高优先级]、[中优先级]、[低优先级]
```

## 5. 安全语义提取

### 5.1 安全特征分析
**提示词模板**：
```
请分析以下代码的安全特征：

{代码内容}

请识别：

1. 安全风险：
   - 输入验证：是否存在输入验证漏洞
   - 权限控制：权限控制是否完善
   - 数据保护：敏感数据是否得到保护

2. 安全实践：
   - 加密使用：加密算法的使用情况
   - 认证授权：认证授权机制
   - 日志审计：安全审计日志

3. 安全建议：
   - 风险修复：需要修复的安全风险
   - 最佳实践：推荐的安全实践
   - 安全测试：建议的安全测试方法

4. 安全语义标签：
   - 安全级别：[高安全]、[中等安全]、[低安全]
   - 风险类型：[输入风险]、[权限风险]、[数据风险]
   - 修复优先级：[紧急]、[重要]、[一般]
```

## 语义提取质量保证

### 一致性检查
- **跨文件一致性**：相同概念在不同文件中的语义一致性
- **术语一致性**：术语使用的一致性
- **描述一致性**：功能描述的一致性

### 准确性验证
- **技术准确性**：技术描述的准确性
- **业务准确性**：业务语义的准确性
- **逻辑准确性**：逻辑关系的准确性

### 完整性检查
- **覆盖完整性**：是否覆盖了所有重要语义
- **深度完整性**：语义描述的深度是否足够
- **关联完整性**：语义关联是否完整

这些语义提取提示词旨在从多个维度深度理解代码的语义含义，为后续的智能搜索、关系分析和知识图谱构建提供丰富的语义信息。