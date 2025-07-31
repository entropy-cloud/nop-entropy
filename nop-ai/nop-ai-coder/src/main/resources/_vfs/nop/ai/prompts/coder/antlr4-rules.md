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

