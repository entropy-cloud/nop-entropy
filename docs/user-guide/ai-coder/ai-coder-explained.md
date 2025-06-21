# AI颠覆开发：开源框架NopAiCoder实现"输入需求文档输出完整应用”

## qwen3:8b结果

1. 输出过多时会出现部分XML格式不正确，主要是带名字空间的属性存在困难。比如生成了`orm"ref-prop`。

AI提供的功能在程序空间中是什么？AI是一个多输入和多输出的函数。

如果状态是ENABLED/DISABLED，可以使用通用的active-status字典，并且已经提供了通用的disable和enable函数。

投影选择；独立部分A + 独立部分B + 混杂部分（Delta）

Y = A + B + .. + Delta
如果不是完全独立，则Delta部分集中了混杂信息。

Agent之间的交互信息不应该是黑箱模型，而是按维度分解的可分析向量，可以是analyzable + nlp的分块组织形式。

在输入信息明确要求的情况下，如果仍然无法引导到对称破缺的情况。

幻觉不可避免性
AI模型输出的是所有逻辑可能世界中的可行解集合，而人类所在的物理世界仅是特定随机事件（对称破缺）导致的单一样本。要求AI仅输出本宇宙事实，本质是要求从无限可能性中选出唯一解——这是信息论不可能性。

局部幻觉的可解性
当上下文提供充分信息（对称破缺条件）时，模型仍出错源于认知结构缺陷：

无法将提示词作为对称破缺扰动因子

缺乏对代数结构（组合性/可逆性）的严格映射能力


群论视角下的推理崩塌：

理想情况：模型应构建推理群 $G = \langle \text{前提}, \oplus \rangle$，其中$\oplus$是组合操作

实际缺陷：

组合性断裂：当 $A \oplus B$ 应得 $C$ 时，模型输出 $C'$
例：已知“A是B祖父”和“B是C父亲”，输出“A是C叔叔”

可逆性丧失：无法从 $C$ 回溯验证 $A \oplus B$
例：断言“A是C祖父”后，无法列出推理路径

根本原因：
模型参数空间未形成同态映射 $h: G_{\text{现实}} \to G_{\text{模型}}$，导致群结构扭曲


真理非唯一性：逻辑上可能存在多个自洽的事实版本（例如：“地球是平的”在某个物理定律不同的宇宙中成立），当前世界只是众多可能性中的一个偶然实现（对称破缺的结果）。

AI的合理性：当模型输出“错误”事实时，它可能正确描述了另一个可能世界（平行宇宙中的真理）。因此，所谓“幻觉”本质是模型输出与本宇宙观测事实的错位，而非逻辑错误。

不可避免性根源：人类无法通过逻辑完全排除其他可能性（例如无法证明“所有宇宙中物理定律相同”），因此AI无法绝对保证输出仅匹配本宇宙事实。

2. 我的原理解的偏差
   错误预设：我假设存在一个绝对参照系（“唯一真理”），将偏离此标准的输出视为“模型缺陷导致的错误”。

正确视角：

在模态逻辑框架下，AI输出的是所有可能世界中的真命题集合

“幻觉”是本宇宙索引（@actual_world）的定位失败，而非命题本身无效。

对于原始数据，通过代数增强器注入群结构，是否可以优化训练。至少差量概念可以单独针对性训练。

文档本身不精确，基于不同的背景知识实际上可以有很多解释。而DSL模型是精确的

# deepseek-r1

deepseek-r1在长输出方面优于deepseek-v3，V3总是会出现对类似输出进行省略的情况，比如

初步整理需求时不要指定需求的细节结构，否则可能遗漏信息。

要求输出`需求章节（仅编号）`不能完全遵循。
V3比R1更容易遗漏信息。

让AI建立一个分析框架，辅助分析当前DSL的设计是否有利于AI生成，并详细解释为什么

输入/输出位置优化

✅ 参数集中管理：将input/output移至专门的<parameters>段，语义更明确（原metadata范围过宽）

✅ 显眼性增强：作为顶级元素，AI在生成流程时更容易优先处理接口定义

✅ 引用预测性：AI生成步骤逻辑时，可准确预测参数位置（/flow/parameters/input）

AI反馈机制分离

✅ 职责单一化：独立<aiFeedback>段解决原设计中的元数据污染问题

✅ 生成过程友好：AI可自由输出重构建议而不影响核心DSL结构

✅ 协作性提升：人类开发者能快速定位AI反馈（与参数/步骤分离）

## 经验

一个关键设计决策是禁用 else 语句：故意不允许模型使用 'else' 块，而只使用 'if' 块。这要求模型为每个路径定义明确的条件，这种设计在评估中显著提高了性能。
从 Parahelp 的实践中可以提炼出专业级提示词的核心原则：明确的思维顺序

- 指定模型的处理步骤结构化格式
- 使用 markdown 和 XML 组织信息角色定义
- 分配明确的角色（如"管理者"）关键指令强调
- 使用"重要"和"始终"等词突出关键要求

- if 块可以在步骤和计划中的任何地方使用，应该简单地用<if_block condition=''>标签包装。<if_block>应该始终有一个条件。
  通过冗余来确保关键指令被遵循

## 提示词修改

1. 核心数据库表设计 --> 数据库表设计 ，需求中仅要求核心数据库表，会导致某些表被遗漏

## 豆包模型`doubao-1.5-pro`

1. 数据库设计子表属性错误

## 技术路线

核心思想：

1. 坐标化

2. 局域函数+自适应纠正

3. 元模型提供语义验证，语法采用通用语法

4. data + metadata配对，信息紧凑存放。比如useCaseNo与业务菜单的关联

AIGC的困境：幻觉 + 有限上下文

分析：结构空间过大，推理深度有限

解决方案：解空间与问题空间复杂度适配，投影到子空间，然后将多个子空间的结果粘结在一起。

不同的逻辑主体修改同一份代码结构。蓝图叠加。

生成骨架 + 局部抛光

通过MCP实现信息补充。

将整理后的规范性示例代码放到特定目录，然后建立RAG索引。

确立自动化的验证标准，要求差量化修改。自动压缩对话过程。

1. 多个方案判断哪个更优

2. 对比多种AI模型的生成

3.

需求所在的问题空间 --> 我们使用的编程语言与框架所在的软件结构空间 --> AI大模型所知的所有语言（不同语言，不同版本）历史上所有编程知识所在的可行空间。

这些空间本身是不匹配的。幻觉的问题：如果仔细分析，会发现在某个平行宇宙中AI的输出可能就是合理的，在那个宇宙中存在着一个这样的事实。仅从逻辑进行判断，是无法识别是否是幻觉的。
我们所在的这个世界是对称自发破缺的结果。

上下文窗口不够大的问题：注意力只能有限集中。

解决方案：投影到不同的子空间，每次解决一个局部问题，然后再拼接为一个整体。并不需要是正交的。但是需要能够识别出重叠部分，自动的进行形式转换。

可逆计算理论提供了一个系统化的投影-粘结方案。

具有完善解决方案的问题直接要求回答。
复杂的逻辑实现问题，要求AI选择一个自己认为最合适的XML格式来表达，并反复检查且改进该XML格式的实现。最后再映射到对应的TaskFlow实现。

面向人类使用的XDef元模型：主要是表达格式信息。

面向AI：根据语义可以自动推定一定的格式信息，不需要详细表达。

如果局部可以进行纠正，没有必要和AI费口舌反复强调细节。实际上也很难严格遵守。比如字段大小写等。

如果发现有多余输出，可以指定一个具有相关语义的字段引导它。

调整表达格式，避免重复表达或者需要负责匹配。比如orm:ref_table属性。

XDef元模型特别适合AI理解。

```xml

<orm x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
     xmlns:orm="orm" xmlns:ext="ext">

  <entities xdef:body-type="list" xdef:key-attr="name">
    <entity name="!english" displayName="chinese">
      <comment>description</comment>
      <columns xdef:body-type="list" xdef:key-attr="name">
        <column name="!english" displayName="chinese" mandatory="boolean" primary="boolean"
                ext:dict="dict-name"
                stdDomain="std-domain" stdSqlType="!sql-type" precision="int" scale="int"
                orm:ref-table="table-name"
                orm:ref-prop="parent-to-children-prop" orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

简单的说，就是将DSL中重复的内容删除，只保留唯一的部分。然后将值替换为格式约束描述。

注意到xdef文件中定义了`xdef:body-type`，`xdef:key-attr`等属性，这些属性是XDef元模型的一部分，用于精确指定属性的语义并在解析的时候自动校验。
但是对于AI大模型来说，这些信息是多余的，而且会形成干扰。

```xml

<orm>
  <entities>
    <entity name="english" displayName="chinese">
      <comment>description</comment>
      <columns>
        <column name="english" displayName="chinese" mandatory="boolean" primary="boolean" ext:dict="dict-name"
                stdDomain="std-domain" stdSqlType="sql-type" precision="int" scale="int"
                orm:ref-table="table-name" orm:ref-prop="parent-to-children-prop"
                orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

AI使用的XDef元模型经过特殊定制，强调语义。与Nop平台内置的并不相同。但是格式保持兼容性。

## Prompt模型设计

prompt模板需要如下能力：

1. 能够进行前处理，我们可能需要衍生数据
2. 需要进行后处理

只要切换promptName，就可以得到具有动态处理能力的prompt模板。

Prompt相当于是一个函数实现，因此它应该具有输入和输出。目前Nop平台的所有可编配函数都是 Map func(Map)格式
inputs => outputs 从Map映射到Map

## AI集成

因为是函数抽象，所以很容易通过元编程集成到Nop平台中。

### NopTaskFlow编排

```xml

<task>
  <steps>
    <step name="designOrm" customType="ai:TaskStep" ai:promptName="coder/orm-design">
      <description>根据需求文档的描述，设计ORM模型</description>

      <input name="requirements"/>

      <output name="ormModelText" value="${RESULT.xml()}"/>
    </step>
  </steps>
</task>
```

### NopGraphQL服务集成

将AI大模型调用封装为服务函数

```
<query name="designOrm" ai:promptName="coder/orm-design">
  <ai:chatOptions provider="ollama" model="deepseek-r1:14b" />
</query>
```

根据prompt模型中定义的input和output自动生成对应的action定义。

## 基于领域知识的DSL结构空间划分

首先按照对象分解，同一个对象名对应的相关概念属于一组。
纵向分解：主分解维度。

整体布局和字段展现分离。

## 多种表象之间的自由转换

领域模型可以自由的抽取信息。比如ORM模型简化为只包含必要字段。使用Java形式展现等。

但是一般输出的时候我们可以强制要求使用XML，这样便于自动解析并局部验证。

XML具有自校验能力。如果标签没有封闭，则解析失败，会自动重试。

# AI颠覆开发：开源框架NopAiCoder实现"输入需求文档输出完整应用"

## 引言：AI编程的新范式

在软件开发领域，一个革命性的变革正在发生——通过开源框架NopAiCoder，开发者现在只需输入需求文档，系统就能自动输出完整的应用程序。这一突破性进展建立在可逆计算理论和XDef元模型的创新基础上，为解决AI编程中的"
幻觉问题"和"上下文窗口限制"提供了系统化解决方案。

## 技术架构：跨越三个认知空间

NopAiCoder的核心创新在于建立了三个关键空间之间的映射关系：

1. **问题空间**：用户需求所在的业务领域
2. **结构空间**：具体编程语言和框架的技术实现
3. **可行空间**：AI大模型掌握的所有编程知识

传统AI编程工具面临的根本挑战在于这三个空间天然不匹配。NopAiCoder通过"投影-拼接"机制解决了这一问题：

- **投影机制**：将复杂问题分解到不同子空间处理
- **拼接机制**：通过可逆计算理论实现局部解决方案的有机整合
- **重叠识别**：自动检测不同子空间解决方案的重叠部分并进行形式转换

## XDef元模型：人机协作的关键

NopAiCoder设计了两种XDef元模型变体，分别优化人机协作：

### 面向人类的XDef

```xml

<orm x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     xmlns:xdef="/nop/schema/xdef.xdef" xmlns:orm="orm" xmlns:ext="ext">
  <entities xdef:body-type="list" xdef:key-attr="name">
    <entity name="!english" displayName="chinese">
      <comment>description</comment>
      <columns xdef:body-type="list" xdef:key-attr="name">
        <column name="!english" displayName="chinese" mandatory="boolean"
                primary="boolean" ext:dict="dict-name" stdDomain="std-domain"
                stdSqlType="!sql-type" precision="int" scale="int"
                orm:ref-table="table-name" orm:ref-prop="parent-to-children-prop"
                orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

### 面向AI的简化XDef

```xml

<orm>
  <entities>
    <entity name="english" displayName="chinese">
      <comment>description</comment>
      <columns>
        <column name="english" displayName="chinese" mandatory="boolean"
                primary="boolean" ext:dict="dict-name" stdDomain="std-domain"
                stdSqlType="sql-type" precision="int" scale="int"
                orm:ref-table="table-name" orm:ref-prop="parent-to-children-prop"
                orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

关键区别在于：

- 人类版本包含完整的格式约束和校验信息
- AI版本强调语义表达，去除冗余的元数据
- 两者保持格式兼容，确保无缝转换

## Prompt工程：动态函数式设计

NopAiCoder将prompt设计为具有完整输入输出规范的函数单元：

1. **前处理能力**：支持数据衍生和转换
2. **后处理能力**：结果自动格式化和校验
3. **动态切换**：通过promptName实现不同处理逻辑

函数签名统一为Map到Map的转换：

```typescript
inputs: Map → outputs: Map
```

## 系统集成：无缝融入开发流程

### 任务流编排示例

```xml

<task>
  <steps>
    <custom name="designOrm" customType="ai:TaskStep" ai:promptName="coder/orm-design">
      <description>根据需求文档的描述，设计ORM模型</description>
      <input name="requirements"/>
      <output name="ormModelText" value="${RESULT.xml()}"/>
    </custom>
  </steps>
</task>
```

### GraphQL服务集成

```xml

<query name="designOrm" ai:promptName="coder/orm-design">
  <ai:chatOptions provider="ollama" model="deepseek-r1:14b"/>
</query>
```

系统自动根据prompt定义的输入输出生成对应的API接口，开发者无需额外编码。

## 领域知识的结构化处理

NopAiCoder采用创新的DSL空间划分策略：

1. **对象中心分解**：相同对象名的相关概念自动归组
2. **纵向分解**：作为主分解维度确保逻辑一致性
3. **表现分离**：核心模型与展示逻辑解耦

## 多模态自由转换

系统支持领域模型在不同表现形式间的无损转换：

- 可简化为仅含必要字段的ORM模型
- 可转换为Java类实现
- 默认输出标准化XML便于自动校验

XML的自校验特性（标签闭合检查等）为AI输出提供了自动纠错机制，显著提高了生成代码的可靠性。

## 结论：软件开发的新纪元

NopAiCoder框架通过创新的技术路线，实现了从需求文档到完整应用的全自动生成。这一突破不仅大幅提升开发效率，更重新定义了人机协作编程的边界。随着技术的持续演进，AI辅助开发将从"
工具"进化为"协作者"，开启软件开发的新纪元。
