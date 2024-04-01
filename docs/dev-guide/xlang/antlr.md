在Nop平台中，基于g4描述文件，仅仅增加一些命名约定和元数据设置，即可自动解析得到AST语法树，从而极大降低了AST重构的成本。

# AST映射约定

语法解析树(ParseTree)相比于抽象语法树(AST)而言，包含了更多的细节信息，因此理论上说，只要在ParseTree上增加一些标注，忽略我们不关心的部分，只保留我们需要的部分即可。具体来说，我们约定了如下的标注规则：

## 1. 一条解析规则映射为一种AST节点

最自然的情况是一条解析规则直接对应于一个抽象语法树节点，解析规则的名称直接对应于抽象语法树节点的类名。例如

```
sqlDelete
    : DELETE FROM tableName=sqlTableName AS? alias=sqlAlias? where=sqlWhere?
    ;

class SqlDelete extends SqlStatement {
    SqlTableName tableName;
    SqlAlias alias;
    SqlWhere where;
}
```

sqlDelete规则直接对应于SqlDelete类。antlr语法允许通过altLabel为某个语法元素指定明确的变量名，这一机制正好可以用于标记语法树节点的属性，例tableName=sqlTableName
表示根据sqlTableName规则解析得到tableName属性。同时所有没有被altLabel标注的部分将被忽略，例如DELETE关键字。

### 2. 解析规则的每个分支映射为一种AST节点

一条解析规则也有可能对应多种语法树节点，每个分支都对应一种不同情况，解析得到的对象类型为所有分支对象的基类。例如：

```
sqlStatement
    : sqlSelect
    | sqlInsert
    | sqlUpdate
    | sqlDelete;

abstract class SqlStatement {
}

abstract class SqlSelect extends SqlStatement {
}

class SqlQuerySelect extends SqlSelect {
   ...
}

class SqlUpdate extends SqlStatement{
   ...
}    
```

sqlStatement对应于所有分支结果的基类SqlStatement，每个语法分支解析得到一种特定情况。

> 如果规则名以ast\_为前缀，则表示解析函数返回的类型为抽象语法树节点的公共基类。例如XLangParser.g4文件中，ast\_topLevelStatement规则的返回类型为XLangASTNode。XLangASTNode是XLangAST中定义的所有抽象语法树节点的公共基类。

### 3. 多个解析规则或者多个分支映射为同一种AST节点

不同的解析规则，或者一条解析规则的不同分支可能对应于同一种抽象语法树节点。例如：

```
expression_single
    : left=expression_single operator=('*' | '/' | '%') right=expression_single    # BinaryExpression_multiplicative
    | left=expression_single operator=('+' | '-') right=expression_single        # BinaryExpression_additive
    ...;
```

antlr依赖于解析规则的顺序来确定运算符优先级，因此同样的BinaryExpression会根据operator的不同拆分成多个语法分支。这种多条语法规则解析得到同一抽象语法树类型节点的情况很常见，我们可以通过增加后缀名的方式来区分不同的情况。在上面的例子中, BinaryExpression\_multiplicative和BinaryExpression\_additive规则的解析结果都是BinaryExpression类型。同时整个expression\_single规则本身解析得到的类型为Expression类型。

### 4. 解析规则映射为AST节点的属性

有些解析规则不是对应于抽象语法树节点，而是对应于抽象语法树节点的某个属性。例如

```
assignmentExpression
    :  left=leftHandSide operator=assignmentOperator_ right=expression_single eos__    
    ;

assignmentOperator_
    : Assign
    | MultiplyAssign
    | DivideAssign
    | ModulusAssign
    | PlusAssign
    | MinusAssign
    | LeftShiftArithmeticAssign // '<<='
    | RightShiftArithmeticAssign //'>>='
    | RightShiftLogicalAssign // '>>>='
    | BitAndAssign // '&='
    | BitXorAssign // '^='
    | BitOrAssign // '|='
    ;

class AssignmentExpression extends Expression {
    XLangOperator operator;
    Expression left;
    Expression right;
}
```

assignmentOperator\_规则解析得到AssignmentExpression对象的operator属性，类型为XLangOperator。对于这种不对应于抽象语法树节点的解析规则，我们约定它的名称以`_`结尾。注意，这也等价于约定**所有解析规则，如果后缀名不是`_`,则它的解析结果必须是抽象语法树节点类型**。

### 5. 解析规则映射为AST节点列表

一条解析规则也有可能对应一个AST节点列表，例如函数的参数列表或者SQL语句中的字段列表等。例如

```
sqlInsert
    : INSERT INTO tableName=sqlTableName LP_ {indent();} columns=columnNames_ { br();} RP_
        ( values=sqlValues | select=sqlSelect)
    ;

columnNames_
    : e=sqlColumnName (COMMA_ e=sqlColumnName)*
    ;    
```

我们约定每个列表都对应一条单独的解析规则，且在这个规则中通过属性e来标记列表元素对应的部分。例如columnNames\_规则解析得到List类型。

### 6. 忽略某些语法树节点

有些解析规则的结果可能不对应于抽象语法树的任何部分。例如Statement的结束符

```
eos__
    : SemiColon
    | EOF
    | {this.lineTerminatorAhead()}?
    | {this.closeBrace()}?
    ;
```

我们约定以`__`结尾的规则不参与抽象语法树构建。

### 7. 简化终端符号的映射

对于终端符号直接对应的抽象语法树映射规则，如果针对每个终端符号都约定一条解析规则，则太过复杂，可以通过astProp属性标注来简化。

```
literal
options{
  astProp=value;
}:
  StringLiteral
  | NumericLiteral;

literal_string:
   value = StringLiteral;  
```

> options是antlr内置的一种元数据扩展机制，允许设置自定义的可扩展属性。

注意，在我们的约定中，**终端节点不会直接对应于抽象语法树节点，它最多对应抽象语法树节点的一个属性**。例如在literal\_string规则中根据StringLiteral这个
终端节点解析得到Literal节点的value属性。

对于多个终端符号都对应于同一种抽象语法树节点的情况，可以统一通过 astProp选项来指明解析得到的属性名，从而避免针对每种分支情况单独进行指定。

在上面的例子中，literal的所有分支都必须对应于单个终端符号，它们的解析结果都对应于Literal节点的value属性。

### 8. 辅助性规则

有时为了编写上的便利，我们会引入一些辅助性规则。例如

```
expression_initializer
    : '=' expression_single
    ;
```

目前只允许一种辅助规则，就是上例中所展示的，解析规则内部只有一个语法成分最终对应于抽象语法树节点。相当于是在已有抽象语法树规则的基础上增加一些最终没有进入抽象语法树的终端符号。
辅助规则解析得到的结果类型与内部规则的解析结果类型相同。

### 9. 回避命名冲突

由于antlr实现层面的限制，它不允许altLabel与已经存在的规则名重复。当出现冲突时，可以通过为altLabel增加\_后缀的方式来回避名称冲突。

```
arrayBinding
    : '[' elements=arrayElementBindings_ (Comma restBinding_=restBinding |Comma)? CloseBracket # ArrayBinding_full
    | OpenBracket restBinding_=restBinding Comma? CloseBracket # ArrayBinding_rest
    ;
```

restBinding本身是解析规则名，它也对应于抽象语法树节点的类名，因此在arrayBinding规则中不能使用restBinding作为altLabel属性名，需要把它替换为restBinding\_。

### 10. 设置固定的属性值

有时我们希望根据某条解析规则得到AST节点时，直接将AST节点的某个属性设置为固定值，而不是从ParseTree中映射得到属性值。例如

```
expression_single
    :
    ...
    | <astAssign=computed> object=expression_single '[' property=expression_single CloseBracket     #   MemberExpression_index
    | object=expression_single '.' property=identifier_ex               #  MemberExpression_dot
    | <astAssign='optional:false'>expr=expression_single {this.notLineTerminator()}? optional='!' # ChainExpression 
    ...
    ;
```

antlr中允许通过`<name=value>`的形式为解析元素增加所谓的ELEMENT\_OPTIONS元数据。astAssign是我们增加的一个扩展选项，它的格式为astAssign='name1:value1,name2:value2'。如果是boolean属性，则可以简写为属性名，例如 \<astAssign=computed\>表示解析得到AST节点后，自动设置astNode.computed=true。

### 11. 格式化规则

为了实现语法树的格式化输出，可以在g4文件的action段中增加indent和br标注。

```
sqlDelete
    : DELETE FROM tableName=sqlTableName AS? alias=sqlAlias? 
      (<br> where=sqlWhere)?
    ;
```

上面的规则表示在输出alias之后，需要回车换行。

> 目前代码生成器读取antlr语法文件时只支持以上几种情况，因此对于第三方编写的antlr语法文件需要经过改写后才能与代码生成器集成在一起，直接生成抽象语法树解析器。

## 需要手工编写的代码

Nop平台的代码生成器生成的XXXBuildVisitor类负责将Antlr的ParseTree转换为指定的抽象语法树对象，它会要求补充一些解析终端节点的帮助函数。

```java
public class XLangASTBuildVisitor extends _XLangASTBuildVisitor {

    /**
     * rules: assignmentExpression
     */
    public io.nop.xlang.ast.XLangOperator AssignmentExpression_operator(ParseTree node) {
        return XLangParseHelper.operator(token(node));
    }
    ...
}    
```

代码生成器会自动生成BuildVisitor框架代码，只需要填写少数几个映射函数即可。例如assignmentExpression规则中如何从ParseTree的operator映射得到XLangOperator枚举值。

此外，ASTNode提供了normalize和validate函数，通过定制这两个函数，我们在AST节点初始化之后可以先根据解析情况规范抽象语法树节点结构（例如填充AST节点的缺省部分或者简化AST节点结构），然后再验证抽象AST节点满足规范定义要求，例如Identifier节点自动验证name属性不为空等。

## 自动生成AST节点类

使用Antlr解析器时，一般AST节点类是手工编写的，有一定的工作量。而且一般情况下一个java文件对应一个AST节点类型，很难让人对AST节点树的整体情况有个直观的认识。
此外，AST节点的复制、比较、遍历、转换等操作都是一些模式比较固定的模板代码，手工编写成本较高且容易出现错误。因此我们定义了一种抽象语法树的描述语法，根据这个语法定义直接生成各种AST节点类，以及ASTVisitor/ASTOptimizer/ASTProcessor等辅助处理类。

```java
import io.nop.api.core.annotations.meta.PropMeta;

public class XLangAST {
    enum PropertyKind {
        init,
        get,
        set
    }

    abstract class XLangASTNode {

    }

    abstract class Expression {
    }

    interface IdentifierOrPattern {

    }

    class Program extends Expression {
        String sourceType; //: "script" | "module";

        @PropMeta(mandatory = true)
        List<XLangASTNode> body; //: [ Statement | ModuleDeclaration ];
    }

    class Identifier extends Expression implements IdentifierOrPattern {
        @PropMeta(mandatory = true)
        String name;
    }
    ...
}    
```

我们通过所谓的xjava语法来定义AST语法树结构。有趣的是，xjava语法就是普通的java语法，我们通过@PropMeta注解等可以为它引入额外的描述信息。Nop平台
利用Janino库解析xjava文件，并将它转换为ObjMeta定义，然后再根据ObjMeta生成对应的Java类。

> ObjMeta是Nop平台中定义的一种类似json schema的对象元数据规范，它本身存在一种xml定义语法。也就是说既可以通过xml格式来定义ObjMeta，也可以使用xjava格式来定义ObjMeta，而且XML格式和xjava格式可以互相转换。这种同一结构存在多种定义格式的情况，正是可逆计算理论中所强调的多重表象（Representation）的概念。
> 在不同的使用场景中我们可以根据需要选择合适的表象。例如，可视化编辑的时候我们可以选择XML格式或者JSON格式（Nop平台中定义了XML和json的可逆转换规则），而在需要人工查看或者利用IDE重构的时候使用xjava格式。

如果和grpc中使用的proto格式做个比较，我们会发现，xjava也可以用于描述proto buffer结构，只要允许通过注解类引入额外的元数据信息即可！

根据可逆计算理论，**元数据和数据之间形成一种互补关系**。元数据+数据才构成对结构信息的完整描述，而且在一定情况下**元数据和数据之间不存在本质性的区别，它们之间可以互相转换**。

```
Structure = 数据1 + 元数据1 = 数据2 + 元数据2
```

在不同的表象中，如果我们只关注聚光灯下的数据部分，则可能发现它们是不等价的，信息转换存在失真和扭曲的情况。

$$
数据1 \approx 数据2 \\ 数据1 \ne 数据2 \\
$$

只有把元数据看作是信息表达的一个必要的、不可或缺的部分，总是将元数据和数据配对作为一个整体来表达，我们才能达到可逆计算理论所要求的可逆性。

$$
数据1 \approx 数据2 \\ 数据1 + 元数据1 \equiv 数据2 + 元数据2 \\
$$

按照可逆计算理论的分析，信息在不同的系统之间传递时，也可以被看作是一个表象转换问题。因此，跨系统传递的消息对象一定是 data + header结构， 我们需要以一种可扩展的方式来保存结构未定的元数据！

在本文所介绍的技术方案中，**正是利用了antlr内置的可扩展元数据定义，我们才可以实现出乎原作者意图之外的功能**。

## 深层语法 vs. 浅层语法

现代语言理论的祖师爷乔姆斯基在上世纪50年代提出了转换-生成语法理论，在语言学领域掀起了所谓的"乔姆斯基革命"。

> 所谓正则文法、上下文无关文法、上下文相关文法等概念都来自于大名鼎鼎的乔姆斯基层级

乔姆斯基认为，每个句子都有两个结构层次―深层和表层。深层结构“指短语或句子成分之间的内在的语法关系,但这种语法关系不能直接从他们的线形序列上看出来。在生成语法里,指句子的抽象句法表达，它规定所有支配句子应如何解释的因素”，决定句子的意思。表层结构是”指句子的句法表达的最后阶段,是由深层结构转换得来的，是对实际上形成的句子各成分间的关系进行线形排列的结果"，是用于交际中的句子的形式。通过转换-生成语法理论,可以把表面形式不同而意思相同的句子联系起来。
例如“Mary cleaned the room”和“The room was cleaned by Mary”，表层结构不同，但深层结构却相同。

简单的说，深层语法决定语义，深层结构通过转换规则转化为浅层结构，浅层语法决定了句子最终的表现形式。同一深层语法可以对应于多种不同的浅层语法。对照这一理论，抽象语法树可以看作是语言的深层结构，而antlr语法文件所指定的实际上是一种浅层语法。

如果沿着上述理论进一步思考，既然真正重要的是稳定的领域结构（也就是抽象语法树），那么为什么不规定一个通用的、稳定的抽象语法树表达形式，而彻底放弃不稳定的、格式多样化的浅层语法表达形式呢？这不就是Lisp语言吗？

Nop平台的整体技术战略可以说正是围绕着AST节点树结构制定的，只是它选择了XML和JSON这种更加编辑友好的结构化格式，而不是传统的Lisp语法格式。

Nop平台的设计目标是成为一个通用的领域语言工作台(Domain Specific Language Workbench)，也就是说，它为创建新的领域语言提供了一系列的技术支持，力争将开发新的领域语言的成本降到最低。领域特定语言最重要的部分是它的语义结构（存在哪些领域特有的原子概念，以及这些概念之间如何相互作用），而表观的语法形式其实是一个次要问题。

在Nop平台中开发新的领域语言，最基本的要求只有一条：提供一个抽象语法树定义文件。Nop平台会根据xdef/xjava/xmeta等多种形式的抽象语法树定义文件来生成AST节点类。这些定义的抽象语法树都自带XML表示和JSON表示形式，无需编写任何代码即可从XML/JSON解析得到AST，并可以反向将AST序列化为XML/JSON。如果需要一个类似传统程序语言的语法形式，则可以补充定义一个antlr语法描述文件。

可逆计算理论并不是一个简单的针对某个特定低代码产品的设计模式，而是一种具有广泛适用性的下一代软件构造理论。在可逆计算的理论框架下，一系列分散的设计实践
可以获得统一的理论解释，并很自然的推导出一系列具有内在一致性的可扩展性设计方案。

可逆计算理论的应用范围也绝不限于是单个系统内部，实际上**只有当上下游所有软件都遵循可逆计算原理时，我们才可以实现前所未有的粗粒度软件复用**。下一篇文章中我将介绍一下可逆计算与office word结合实现模板导出的一个技术方案，它通过几百行代码即可提供类似[poi-tl](http://deepoove.com/poi-tl/)的功能，并且免费提供了超越其他技术方案的可扩展性。

## 参考

关于本文中介绍的Antlr自动解析AST技术，具体的配置实例可以参见 [XLangAST.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/ast/io/nop/xlang/ast/XLangAST.xjava) [XLangParser.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/antlr/XLangParser.g4)

[EqlAST.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava) [DMLStatement.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/antlr/DMLStatement.g4)
