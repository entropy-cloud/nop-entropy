# XLang 元编程简明指南

## 1. 概述

元编程是指"写生成代码的代码"，它允许开发者创建更高级别的抽象和动态生成代码。XLang作为面向LowCode领域的通用程序语言，提供了一整套系统化的元编程机制，包括宏函数、编译期表达式、自定义宏标签、差量生成与合并等功能。

XLang的元编程设计基于可逆计算理论，实现了 `App = Delta x-extends Generator<DSL>` 的编程范式，支持从局部函数实现到整个模块目录的代码生成。

## 2. 宏函数

宏函数是在编译期执行，自动生成Expression抽象语法树节点的函数。

### 2.1 宏函数定义

宏函数需要添加`@Macro`注解，第一个参数必须是`IXLangCompileScope`类型，第二个参数必须是`CallExpression`类型，返回值必须是`Expression`类型。

```java
@Macro
public static Expression xpl(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
    return TemplateMacroImpls.xpl(scope, expr);
}
```

### 2.2 宏函数使用

宏函数在编译时执行，将函数调用对应的AST作为参数传入，生成新的Expression返回。

```javascript
// 编译期执行xpl宏函数，解析XML模板生成Expression
let result = xpl `<c:if test="${x}">aaa</c:if>`

// 编译期执行xpath宏函数，解析XPath表达式生成Selector对象
let selector = xpath `a/b[@id='test']/@attr`
let value = node.selectOne(selector); // 运行期直接使用编译期生成的selector
```

### 2.3 内置宏函数

XLang内置了多种宏函数：

| 函数名 | 说明 |
|-------|------|
| xml | 解析XML文本得到XNode节点 |
| xpl | 解析Xpl模板文本得到Expression |
| sql | 解析Xpl模板文本得到生成SQL语句的Expression |
| jpath | 解析json path得到JPath对象 |
| xpath | 解析XSelector文本得到XSelector对象 |
| selection | 解析类似GraphQL Query的对象属性选择文本 |
| order_by | 解析order by语句片段得到排序字段列表 |
| location | 返回调用函数所在的源码位置 |
| IF | 实现类似Excel公式中IF函数的功能 |
| SWITCH | 实现类似Excel公式中SWITCH函数的功能 |

## 3. 编译期表达式

编译期表达式在编译时执行，结果直接成为抽象语法树的一部分。

### 3.1 语法格式

编译期表达式使用`#{expr}`格式：

```xml
<!-- 编译期执行表达式，结果直接替换 -->
<div xpl:if="#{false}">此节点会在编译期被删除</div>

<!-- 在普通表达式中使用编译期表达式 -->
<c:if test="${x > #{MyConstants.MIN_VALUE}}">
  条件满足时执行
</c:if>
```

### 3.2 编译期脚本

使用`<macro:script>`标签可以在编译期执行复杂逻辑：

```xml
<macro:script>
    import test.MyModelHelper;
    // 编译期加载模型文件
    const myModel = MyModelHelper.loadModel('/nop/test/test.my-model.xml');
</macro:script>

<!-- 使用编译期变量 -->
<div>模型名称: #{myModel.name}</div>
```

### 3.3 编译期生成

使用`<macro:gen>`标签可以在编译期执行Xpl模板，生成新的AST：

```xml
<macro:gen>
    <c:for items="#{myModel.fields}" var="field">
        <field name="#{field.name}" type="#{field.type}" />
    </c:for>
</macro:gen>
```

## 4. 自定义宏标签

宏标签是XPL模板语言中的特殊标签，其source段在编译后立刻执行，然后再编译生成的结果。

### 4.1 定义宏标签

```xml
<lib x:schema="/nop/schema/xlib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <tags>
    <MyMacroTag macro="true" outputMode="node">
      <slot name="default" slotType="node" />
      
      <source>
        <c:script>
          // 编译期处理逻辑
          const processed = processSlot(slot_default);
        </c:script>
        
        <!-- 生成新的AST节点 -->
        <c:if test="${condition}">
          ${processed}
        </c:if>
      </source>
    </MyMacroTag>
  </tags>
</lib>
```

### 4.2 使用宏标签

```xml
<c:import from="/path/to/my-macro.xlib" />

<lib:MyMacroTag>
  <original-content />
</lib:MyMacroTag>
```

## 5. 编译得到AST

通过`<c:ast>`标签可以获取内容对应的抽象语法树：

```xml
<Validator ignoreUnknownAttrs="true" macro="true">
  <attr name="obj" defaultValue="$scope" runtime="true" optional="true" />
  <slot name="default" slotType="node" />
  
  <source>
    <c:script>
      import io.nop.biz.lib.BizValidatorHelper;
      
      // 编译期解析得到ValidatorModel
      let validatorModel = BizValidatorHelper.parseValidator(slot_default);
      
      // 获取AST并替换标识符
      let ast = xpl `
           <c:ast>
              <c:script>
                 import io.nop.biz.lib.BizValidatorHelper;
                 if(obj == '$scope') obj = $scope;
                 BizValidatorHelper.runValidatorModel(validatorModel, obj, svcCtx);
              </c:script>
           </c:ast>
       `;
      
      // 将编译期变量替换到AST中
      return ast.replaceIdentifier("validatorModel", validatorModel);
    </c:script>
  </source>
</Validator>
```

## 6. XDSL的差量生成与合并

Nop平台中所有DSL都支持`x:extends`差量合并机制，实现了可逆计算理论所要求的计算模式。

### 6.1 基本语法

```xml
<model x:extends="base1,base2">
    <!-- 编译期生成的差量 -->
    <x:gen-extends>
        <generated-node1 />
        <generated-node2 />
    </x:gen-extends>
    
    <!-- 当前模型的修改 -->
    <node id="existing" modified="true" />
    <new-node />
    
    <!-- 后续修改 -->
    <x:post-extends>
        <post-node1 />
        <post-node2 />
    </x:post-extends>
</model>
```

### 6.2 合并顺序

合并结果为：
```
post-node2 x-extends post-node1 x-extends model x-extends generated-node2 x-extends generated-node1 x-extends base2 x-extends base1
```

## 7. 数据驱动的差量化代码生成器

XCodeGenerator是Nop平台提供的数据驱动代码生成器，它将模板路径看作是微格式的DSL，由模板自身的组织结构控制代码生成过程。

### 7.1 模板路径模式

```
/src/{package.name}/{model.webEnabled}{model.name}Controller.java.xgen
```

此模式表示：遍历package下的每个model，对每个webEnabled属性为true的model生成一个Controller.java类。

### 7.2 集成方式

XCodeGenerator可以与maven打包工具集成，在Java代码编译前和编译后执行代码生成动作，类似Java注解处理器(APT)技术，但使用更简单、直观。

## 8. 最佳实践

1. **优先使用内置宏函数**：内置宏函数经过优化，性能更好，可靠性更高
2. **合理使用编译期表达式**：编译期表达式可以提高运行时性能，但过度使用会增加编译时间
3. **保持宏标签简洁**：宏标签的source段应该保持简洁，复杂逻辑应封装到Java类中
4. **利用差量合并机制**：对于复杂模型，使用x:extends实现模型的模块化和可扩展性
5. **模板组织合理**：XCodeGenerator模板应按照清晰的目录结构组织，便于维护
6. **调试技巧**：使用编译期日志和调试信息，帮助定位编译期问题

## 9. 应用场景

1. **DSL扩展**：为自定义DSL添加新的语法和功能
2. **代码生成**：生成重复性代码，如Controller、Service、DAO等
3. **模型驱动开发**：从设计模型自动生成代码实现
4. **差量定制**：支持多租户、多环境的代码定制
5. **动态配置**：根据配置动态生成代码
6. **跨语言转换**：实现不同语言之间的代码转换

## 10. 总结

XLang提供了一整套强大的元编程机制，包括宏函数、编译期表达式、自定义宏标签、差量生成与合并等功能。这些机制共同构成了一个系统化的代码生成方案，支持从局部函数实现到整个模块目录的生成。

XLang的元编程设计基于可逆计算理论，实现了`App = Delta x-extends Generator<DSL>`的编程范式，为LowCode平台开发提供了强大的支持。通过合理使用这些元编程功能，可以显著提高开发效率，实现更高层次的代码复用和抽象。

XLang的元编程机制具有以下特点：

- **系统化**：覆盖了从局部表达式到整个模块的生成
- **易于使用**：采用熟悉的XML和TypeScript语法
- **高性能**：编译期执行，减少运行时开销
- **可扩展**：支持自定义宏函数和宏标签
- **差量化**：支持模型的差量生成和合并
- **数据驱动**：模板路径控制生成过程

这些特点使得XLang的元编程机制成为构建LowCode平台和领域特定语言的重要工具。