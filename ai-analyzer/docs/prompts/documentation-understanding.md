# 文档理解提示词设计

## 1. 代码注释理解

### 1.1 注释与代码关联分析
**提示词模板**：
```
请分析以下Java代码及其注释的关联关系：

代码内容：
{代码内容}

相关注释：
{注释内容}

请严格按照 `config/ai-prompt-models.xdef` 中定义的 `CommentCodeCorrelationAnalysis` 模型结构提供JSON格式的响应。

**重要说明：所有Nop平台相关知识必须参考 `docs-for-ai` 目录下的文档，确保XDef定义符合规范。**
```

## 返回格式

**XDef模型定义**：参考 `config/ai-prompt-models.xdef` 中的 `CommentCodeCorrelationAnalysis` 模型

**JSON格式示例**：
```json
{
  
  <xdef:prop name="commentQuality" type="object" mandatory="true" comment="注释质量评估">
    <xdef:prop name="completeness" type="string" mandatory="true" 
                comment="完整性：complete/partial/minimal/none"/>
    <xdef:prop name="accuracy" type="string" mandatory="true" 
                comment="准确性：accurate/partially_accurate/inaccurate"/>
    <xdef:prop name="clarity" type="string" mandatory="true" 
                comment="清晰度：clear/moderate/confusing"/>
    <xdef:prop name="usefulness" type="string" mandatory="true" 
                comment="实用性：very_useful/useful/limited/useless"/>
  </xdef:prop>
  
  <xdef:prop name="semanticExtraction" type="object" mandatory="true" comment="语义提取">
    <xdef:prop name="functionalDescription" type="string" mandatory="true" 
                comment="功能描述提取"/>
    <xdef:prop name="usageGuidance" type="string" mandatory="true" 
                comment="使用指导提取"/>
    <xdef:prop name="constraintsAndLimitations" type="array" item-type="string" 
                comment="约束和限制提取"/>
    <xdef:prop name="examples" type="array" item-type="string" 
                comment="示例提取"/>
  </xdef:prop>
  
  <xdef:prop name="codeCommentAlignment" type="object" mandatory="true" comment="代码注释对齐度">
    <xdef:prop name="alignmentScore" type="float" mandatory="true" 
                comment="对齐度评分：0.0-1.0"/>
    <xdef:prop name="misalignments" type="array" item-type="object" 
                comment="不对齐的地方">
      <xdef:prop name="type" type="string" mandatory="true" 
                  comment="类型：outdated/incorrect/missing"/>
      <xdef:prop name="location" type="string" mandatory="true" 
                  comment="位置"/>
      <xdef:prop name="description" type="string" mandatory="true" 
                  comment="描述"/>
    </xdef:prop>
  </xdef:prop>
  
  <xdef:prop name="improvementSuggestions" type="object" mandatory="true" comment="改进建议">
    <xdef:prop name="commentEnhancements" type="array" item-type="string" 
                comment="注释增强建议"/>
    <xdef:prop name="missingComments" type="array" item-type="string" 
                comment="缺失的注释"/>
    <xdef:prop name="documentationGaps" type="array" item-type="string" 
                comment="文档缺口"/>
  </xdef:prop>
</xdef:model>

请严格按照上述XDef定义的结构返回JSON数据。
```

**输出格式**：
```json
{
  "commentCoverage": {
    "methodCommentCoverage": 0.8,
    "classCommentCoverage": 0.9,
    "fieldCommentCoverage": 0.6,
    "overallCoverage": 0.8
  },
  "commentQuality": {
    "completeness": "complete/partial/minimal/none",
    "accuracy": "accurate/partially_accurate/inaccurate",
    "clarity": "clear/moderate/confusing",
    "usefulness": "very_useful/useful/limited/useless"
  },
  "semanticExtraction": {
    "functionalDescription": "功能描述提取",
    "usageGuidance": "使用指导提取",
    "constraintsAndLimitations": ["约束和限制提取"],
    "examples": ["示例提取"]
  },
  "codeCommentAlignment": {
    "alignmentScore": 0.85,
    "misalignments": [
      {
        "type": "outdated/incorrect/missing",
        "location": "位置",
        "description": "描述"
      }
    ]
  },
  "improvementSuggestions": {
    "commentEnhancements": ["注释增强建议"],
    "missingComments": ["缺失的注释"],
    "documentationGaps": ["文档缺口"]
  }
}
```

## 2. API文档理解

### 2.1 Javadoc文档分析
**提示词模板**：
```
请分析以下Javadoc文档：

Javadoc内容：
{javadoc内容}

对应的代码：
{代码内容}

请提取：
1. API功能描述
2. 参数说明和约束
3. 返回值说明
4. 异常说明
5. 使用示例
6. 版本和兼容性信息

请按照结构化JSON格式返回分析结果。
```

## 3. 配置文件文档理解

### 3.1 配置文档分析
**提示词模板**：
```
请分析以下配置文件及其文档：

配置文件：
{配置文件内容}

配置文档：
{配置文档内容}

请分析：
1. 配置项的含义和用途
2. 配置项的约束和默认值
3. 配置之间的依赖关系
4. 配置的最佳实践
5. 配置的安全考虑
```

## 4. README和项目文档理解

### 4.1 项目文档综合分析
**提示词模板**：
```
请综合分析以下项目文档：

README文档：
{README内容}

架构文档：
{架构文档内容}

API文档：
{API文档内容}

请提取：
1. 项目概述和核心功能
2. 架构设计和模块划分
3. 快速开始指南
4. 配置和部署说明
5. 常见问题解答
6. 开发指南和最佳实践
```

## 5. 错误消息和日志理解

### 5.1 错误文档分析
**提示词模板**：
```
请分析以下错误消息和日志文档：

错误代码定义：
{错误代码定义}

错误处理文档：
{错误处理文档}

日志配置：
{日志配置文件}

请分析：
1. 错误分类和严重程度
2. 错误原因和解决方案
3. 日志级别和输出格式
4. 监控和告警配置
5. 故障排除指南
```

## 6. 测试文档理解

### 6.1 测试用例文档分析
**提示词模板**：
```
请分析以下测试文档：

测试用例：
{测试用例代码}

测试文档：
{测试文档内容}

测试报告：
{测试报告内容}

请分析：
1. 测试覆盖范围和深度
2. 测试策略和方法
3. 测试数据和环境要求
4. 测试执行和结果分析
5. 性能和安全测试
```

## 7. 部署和运维文档理解

### 7.1 运维文档分析
**提示词模板**：
```
请分析以下部署和运维文档：

部署指南：
{部署文档}

监控配置：
{监控配置文档}

运维手册：
{运维手册}

请分析：
1. 部署流程和依赖
2. 环境配置和要求
3. 监控指标和告警
4. 备份和恢复策略
5. 性能调优指南
```

## 文档理解策略

### 多文档关联分析
1. **文档类型识别**：识别不同类型的文档（API文档、配置文档、运维文档等）
2. **文档关联性分析**：分析文档之间的引用和关联关系
3. **信息一致性验证**：验证不同文档中信息的一致性
4. **知识图谱构建**：构建文档知识图谱

### 文档质量评估
1. **完整性评估**：评估文档的完整程度
2. **准确性验证**：验证文档内容的准确性
3. **时效性检查**：检查文档的更新时间
4. **实用性评估**：评估文档的实际使用价值

### 文档与代码关联
1. **代码引用分析**：分析文档中引用的代码片段
2. **示例代码验证**：验证文档中的示例代码是否正确
3. **API文档同步**：检查API文档与代码实现是否同步
4. **配置文档验证**：验证配置文档与实际配置的一致性