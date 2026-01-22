# 跨文件分析提示词设计

## 1. 类完整上下文分析

### 1.1 多文件类理解分析
**提示词模板**：
```
请分析以下Java类的完整上下文，基于多个相关文件：

主类文件：
{主类代码}

相关接口文件：
{接口代码}

实现类文件：
{实现类代码}

配置文件：
{配置文件内容}

请严格按照 `config/ai-prompt-models.xdef` 中定义的 `MultiFileClassContextAnalysis` 模型结构提供JSON格式的响应。

**重要说明：所有Nop平台相关知识必须参考 `docs-for-ai` 目录下的文档，确保XDef定义符合规范。**
```

## 返回格式

**XDef模型定义**：参考 `config/ai-prompt-models.xdef` 中的 `MultiFileClassContextAnalysis` 模型

**JSON格式示例**：
```json
{
  </xdef:prop>
  
  <xdef:prop name="interfaceImplementation" type="object" mandatory="true" comment="接口实现关系">
    <xdef:prop name="implementedInterfaces" type="array" item-type="object" comment="实现的接口">
      <xdef:prop name="interfaceName" type="string" mandatory="true" comment="接口名"/>
      <xdef:prop name="implementationCompleteness" type="string" mandatory="true" 
                  comment="实现完整度：complete/partial"/>
      <xdef:prop name="contractFulfillment" type="string" mandatory="true" 
                  comment="契约履行情况：full/partial/none"/>
    </xdef:prop>
  </xdef:prop>
  
  <xdef:prop name="inheritanceContext" type="object" mandatory="true" comment="继承上下文">
    <xdef:prop name="parentClass" type="string" comment="父类"/>
    <xdef:prop name="inheritanceDepth" type="integer" mandatory="true" comment="继承深度"/>
    <xdef:prop name="methodOverrides" type="array" item-type="object" comment="方法重写">
      <xdef:prop name="methodName" type="string" mandatory="true" comment="方法名"/>
      <xdef:prop name="overrideType" type="string" mandatory="true" 
                  comment="重写类型：full/partial/extension"/>
    </xdef:prop>
  </xdef:prop>
  
  <xdef:prop name="crossFileDependencies" type="object" mandatory="true" comment="跨文件依赖">
    <xdef:prop name="importedClasses" type="array" item-type="string" comment="导入的类"/>
    <xdef:prop name="externalDependencies" type="array" item-type="object" comment="外部依赖">
      <xdef:prop name="dependencyType" type="string" mandatory="true" 
                  comment="依赖类型：compile/runtime/test"/>
      <xdef:prop name="dependencyName" type="string" mandatory="true" comment="依赖名称"/>
      <xdef:prop name="usagePattern" type="string" mandatory="true" comment="使用模式"/>
    </xdef:prop>
  </xdef:prop>
  
  <xdef:prop name="configurationIntegration" type="object" mandatory="true" comment="配置集成">
    <xdef:prop name="configProperties" type="array" item-type="object" comment="配置属性">
      <xdef:prop name="propertyName" type="string" mandatory="true" comment="属性名"/>
      <xdef:prop name="propertyType" type="string" mandatory="true" comment="属性类型"/>
      <xdef:prop name="defaultValue" type="string" comment="默认值"/>
      <xdef:prop name="configFile" type="string" mandatory="true" comment="配置文件"/>
    </xdef:prop>
  </xdef:prop>
  
  <xdef:prop name="usageContext" type="object" mandatory="true" comment="使用上下文">
    <xdef:prop name="typicalUsageScenarios" type="array" item-type="string" 
                comment="典型使用场景"/>
    <xdef:prop name="integrationPoints" type="array" item-type="string" 
                comment="集成点"/>
    <xdef:prop name="lifecycleManagement" type="string" mandatory="true" 
                comment="生命周期管理"/>
  </xdef:prop>
</xdef:model>

请严格按照上述XDef定义的结构返回JSON数据。
```

**输出格式**：
```json
{
  "classIdentity": {
    "className": "类名",
    "package": "包名",
    "relatedFiles": ["相关文件列表"]
  },
  "interfaceImplementation": {
    "implementedInterfaces": [
      {
        "interfaceName": "接口名",
        "implementationCompleteness": "complete/partial",
        "contractFulfillment": "full/partial/none"
      }
    ]
  },
  "inheritanceContext": {
    "parentClass": "父类",
    "inheritanceDepth": 2,
    "methodOverrides": [
      {
        "methodName": "方法名",
        "overrideType": "full/partial/extension"
      }
    ]
  },
  "crossFileDependencies": {
    "importedClasses": ["导入的类"],
    "externalDependencies": [
      {
        "dependencyType": "compile/runtime/test",
        "dependencyName": "依赖名称",
        "usagePattern": "使用模式"
      }
    ]
  },
  "configurationIntegration": {
    "configProperties": [
      {
        "propertyName": "属性名",
        "propertyType": "属性类型",
        "defaultValue": "默认值",
        "configFile": "配置文件"
      }
    ]
  },
  "usageContext": {
    "typicalUsageScenarios": ["典型使用场景"],
    "integrationPoints": ["集成点"],
    "lifecycleManagement": "生命周期管理"
  }
}
```

## 2. 模块间关系分析

### 2.1 跨模块调用分析
**提示词模板**：
```
请分析以下跨模块的调用关系：

模块A中的调用者类：
{调用者类代码}

模块B中的被调用类：
{被调用类代码}

模块间配置：
{模块配置}

请分析：
1. 模块边界跨越方式
2. 接口契约定义
3. 数据传递模式
4. 错误处理机制
5. 性能影响评估

请按照结构化JSON格式返回分析结果。
```

## 3. 包级别分析

### 3.1 包内类关系分析
**提示词模板**：
```
请分析以下包内的类关系：

包结构：
{包内所有类文件列表}

主要类文件：
{主要类代码}

辅助类文件：
{辅助类代码}

请分析：
1. 包内类的协作模式
2. 公共接口和内部实现
3. 包级别的设计模式
4. 包的职责边界
5. 包的可复用性评估
```

## 4. 项目级别分析

### 4.1 项目架构分析
**提示词模板**：
```
请基于以下项目结构进行分析：

项目模块结构：
{模块列表和关系}

核心模块代码：
{核心模块代码}

配置和构建文件：
{配置文件内容}

请分析：
1. 整体架构风格
2. 模块划分合理性
3. 依赖关系复杂度
4. 扩展性和维护性
5. 架构改进建议
```

## 5. 依赖链分析

### 5.1 完整依赖链分析
**提示词模板**：
```
请分析以下类的完整依赖链：

目标类：
{目标类代码}

直接依赖类：
{直接依赖类代码}

间接依赖类：
{间接依赖类代码}

第三方依赖：
{依赖配置}

请构建完整的依赖图谱并分析：
1. 依赖深度和广度
2. 循环依赖检测
3. 依赖稳定性评估
4. 依赖解耦建议
```

## 6. 配置与代码关联分析

### 6.1 配置驱动代码分析
**提示词模板**：
```
请分析以下配置与代码的关联关系：

配置文件：
{配置文件内容}

对应的Java类：
{Java类代码}

请分析：
1. 配置项到代码的映射关系
2. 配置驱动的行为变化
3. 配置验证和错误处理
4. 配置热更新支持
5. 配置安全性分析
```

## 应用策略

### 上下文收集策略
1. **文件关联性识别**：基于import语句和包结构识别相关文件
2. **配置关联分析**：分析配置文件与代码的对应关系
3. **依赖链构建**：构建完整的依赖关系网络
4. **文档关联**：关联相关的文档和注释

### 分析深度控制
1. **广度优先**：先分析直接相关的文件
2. **深度扩展**：根据需要逐步扩展分析范围
3. **相关性过滤**：基于语义相关性过滤无关文件
4. **优先级排序**：重要文件优先分析

### 结果整合策略
1. **多源信息融合**：整合多个文件的分析结果
2. **冲突检测和解决**：检测和解决不同文件间的信息冲突
3. **上下文一致性验证**：验证分析结果的一致性
4. **综合评估**：基于完整上下文进行综合评估