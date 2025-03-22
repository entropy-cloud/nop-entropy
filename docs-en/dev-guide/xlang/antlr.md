
In the Nop platform, by defining name conventions and metadata settings in grammar files (g4), we can automatically parse and generate an AST syntax tree, significantly reducing the cost of AST restructuring.


# AST Mapping Convention

A Parse tree (ParseTree) compared to an Abstract Syntax Tree (AST) contains more detailed information. In theory, by adding some annotations to the Parse tree, we can ignore unnecessary parts and only retain the parts we need. Specifically, we have defined the following annotation rules:


## 1. Mapping of a Parsing Rule to an AST Node

The most natural scenario is when a parsing rule directly corresponds to an AST node. The name of the parsing rule directly corresponds to the class name of the AST node. For example:

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

The `sqlDelete` rule directly corresponds to the `SqlDelete` class. The ANTLR grammar allows specifying a variable name for a specific syntax element using `altLabel`, which is particularly useful for marking attributes of AST nodes. For example, `tableName=sqlTableName` indicates that the `tableName` attribute is parsed based on the `sqlTableName` rule.

Meanwhile, any part not annotated with `altLabel` will be ignored, such as the `DELETE` keyword.


### 2. Mapping of Each Parsing Rule Branch to an AST Node

A single parsing rule may correspond to multiple AST nodes, where each branch represents a different situation. The type of the parsed object becomes the base class for all branches. For example:

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

abstract class SqlInsert extends SqlStatement {
}

abstract class SqlUpdate extends SqlStatement {
}

abstract class SqlDelete extends SqlStatement {
}
```

`sqlStatement` corresponds to the base class `SqlStatement` for all branches. If a rule name starts with `ast_`, the return type of the parsing function will be the base class `XLangASTNode`. For instance, in `XLangParser.g4`, the `ast_topLevelStatement` rule returns `XLangASTNode`.


### 3. Mapping of Multiple Parsing Rules or Branches to a Single AST Node

Multiple parsing rules or branches may correspond to a single AST node. For example:

```
expression_single
    : left=expression_single operator=('*' | '/' | '%') right=expression_single    # BinaryExpression_multiplicative
    | left=expression_single operator=('+' | '-') right=expression_single        # BinaryExpression_additive
    ...
```

ANTLR relies on the order of parsing rules to determine operator precedence. Therefore, the same `BinaryExpression` can be split into multiple syntax branches based on different operators. This is a common scenario where multiple grammar rules map to the same AST type. By adding suffix names (e.g., `BinaryExpression_multiplicative` and `BinaryExpression_additive`), we can distinguish between different situations in the grammar file.


### 4. Mapping of Parsing Rules to AST Node Attributes

Some parsing rules do not correspond directly to an AST node but instead map to an attribute of an AST node. For example:

```
assignmentExpression
    :  left=leftHandSide operator=assignmentOperator_ right=expression_single eos__    
    ;
```

```
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
```

```java
class AssignmentExpression extends Expression {
    XLangOperator operator;
    Expression left;
    Expression right;
}
```

The `assignmentOperator_` rule resolves to an `AssignmentExpression` object's `operator` property, which is of type `XLangOperator`. For rules that do not correspond to abstract syntax tree nodes, we conventionally name them with a trailing underscore `_`. This means that all parsing rules that do not map directly to AST nodes are named with underscores. For example, if a rule's name is not ending with `_`, its parsed result must be an abstract syntax tree node type.

### 5. Mapping Parsing Rules to AST Node Lists

A single parsing rule may correspond to one or more AST node lists. For example:

- Function parameters
- SQL statement columns

For instance:

```sql
sqlInsert
    : INSERT INTO tableName=sqlTableName LP_ {indent();} columns=columnNames_ { br();} RP_
        ( values=sqlValues | select=sqlSelect )
    ;
```

Here, `sqlInsert` corresponds to an AST node list representing an INSERT statement.

Each list is defined by a specific rule. For example:

```sql
columnNames_
    : e=sqlColumnName (COMMA_ e=sqlColumnName)*
    ;
```

This rule defines how column names are parsed and mapped to the `List` type.

### 6. Ignoring Some Syntax Tree Nodes

Some parsing rules may not correspond to any part of the abstract syntax tree. For example:

```eos__
    : SemiColon
    | EOF
    | {this.lineTerminatorAhead()}?
    | {this.closeBrace()}?
    ;
```

We conventionally exclude rules ending with `__` from being part of the abstract syntax tree.

### 7. Simplifying Terminal Symbols Mapping

To simplify terminal symbol mapping, we can use the `astProp` attribute instead of defining individual rules for each terminal. For example:

```literal
options {
    astProp = value;
}
:
    StringLiteral
    | NumericLiteral;

literal_string
    : value = StringLiteral;
```

> The `options` is a built-in mechanism in ANTLR that allows custom extendable attributes.

In our conventions, terminal nodes will not directly correspond to abstract syntax tree nodes. They may map to at most one AST node attribute. For instance, the `literal_string` rule parses `StringLiteral` and maps it to the `value` attribute of a `Literal` AST node.

### 8. Helper Rules

Sometimes, we introduce helper rules for convenience. For example:

```expression_initializer
    : '=' expression_single
    ;
```

This helper rule simplifies the definition of expression initializers, mapping directly to an AST node without additional parsing logic.

### 9. Avoid Name Conflicts

Due to the limitations in the implementation of ANTLR, it does not allow the reuse of `altLabel` with an existing rule name. When a conflict arises, you can avoid this by appending an underscore `_` to the `altLabel`.

### 10. Set Fixed Attribute Values

Sometimes, we want to directly set the value of an attribute of an AST node when parsing a rule, instead of mapping it from the parse tree. For example:

```
expression_single
    :
    ...
    | <astAssign=computed> object=expression_single '[' property=expression_single CloseBracket # MemberExpression_index
    | object=expression_single '.' property=identifier_ex # MemberExpression_dot
    | <astAssign='optional:false'>expr=expression_single {this.notLineTerminator()}? optional='!' # ChainExpression 
    ...
    ;
```

In ANTLR, you can specify fixed attribute values using `<name=value>`. For example, `astAssign` is an extended option that can be set as `<astAssign=computed>`. If the attribute is a boolean, it can be simplified to `<astAssign=true>`.

### 11. Formatting Rules

To format the AST in a readable manner, you can add indentation and line breaks in the actions section of your `.g4` file.

```
sqlDelete
    : DELETE FROM tableName=sqlTableName AS? alias=sqlAlias? 
      (<br> where=sqlWhere)?
    ;
```

This rule indicates that after `alias`, a newline is required.

Currently, the code generator only supports the above syntax. Therefore, third-party ANTLR files may need to be modified before they can be integrated with the code generator to generate an abstract syntax tree parser.

## Manual Code Writing

The Nop platform's code generator generates classes like `XXXBuildVisitor` to convert the parse tree into specific AST objects. It requires some helper functions for terminal nodes to be manually implemented.

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

The code generator will automatically generate the `BuildVisitor` framework, and only a few mappings need to be implemented. For example, how to map `assignmentExpression` rule to `XLangOperator` in the AST.


Moreover, `ASTNode` provides `normalize` and `validate` functions. By customizing these functions, we can first normalize the abstract syntax tree node structure (e.g., filling in default or simplified parts of the AST node), and then validate the abstracted AST node against its defined requirements, such as ensuring that an `Identifier` node has a non-empty `name`.


## Automatically Generating AST Node Classes

When using an parser like ANTLR, AST node classes are typically manually written, which can be labor-intensive. Additionally, each Java file often corresponds to a specific AST node type, making it difficult to gain an overall understanding of the entire AST structure. Furthermore, operations such as copying, comparing, traversing, and transforming AST nodes involve repetitive code patterns that are costly to maintain and error-prone. Therefore, we defined a domain-specific language (DSL) for defining AST structures, which directly generates various AST node classes along with auxiliary classes like `ASTVisitor`, `ASTOptimizer`, and `ASTProcessor`.

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
        String sourceType; //: "script" | "module"

        @PropMeta(mandatory = true)
        List<XLangASTNode> body; //: [ Statement | ModuleDeclaration ]
    }

    class Identifier extends Expression implements IdentifierOrPattern {
        @PropMeta(mandatory = true)
        String name;
    }
}
```

We define the AST structure using a domain-specific language called "xjava". Interestingly, "xjava" is essentially standard Java. We use the `@PropMeta` annotation to introduce additional metadata. The Nop platform utilizes the Janino library to parse xjava files and convert them into `ObjMeta`, which in turn generates corresponding Java classes.

> `ObjMeta` is a data schema defined within the Nop platform, resembling JSON Schema. It uses its own XML-based syntax for defining structures. Essentially, you can define `ObjMeta` using either XML or xjava formats, and both formats are interchangeable due to built-in conversion rules. In some cases, multiple definition formats may coexist, allowing flexibility in how structures are defined.

> For example:
  - When performing visual editing, you might choose between XML or JSON formats (Nop platform defines conversion rules for XML and JSON).
  - During manual review or refactoring using an IDE, xjava format is more practical.

If we compare this with gRPC's `proto` format, "xjava" can also describe `proto` structures. This is possible as long as additional metadata is allowed through annotations like `@PropMeta`.

According to the principles of reversible computing:
- **Data and metadata form a complementary relationship**.
- Combining data and metadata results in a comprehensive information description.
- In certain scenarios, data and metadata may appear interchangeable, but their separation ensures proper reversibility.

```
Structure = Data1 + Metadata1 = Data2 + Metadata2
```

In different representation formats:
- If we focus only on the data part (e.g., under a searchlight), structures might not be reversible.
- Reversibility issues arise when transformations introduce lossy steps or when metadata is missing.

$$
Data1 \approx Data2 \\ Data1 \neq Data2 \\
$$

Treating metadata as an essential component of information expression is crucial for achieving reversibility as per reversible computing theory:

$$
Data1 \approx Data2 \\ Data1 + Metadata1 \equiv Data2 + Metadata2 \\
$$

When information is transmitted between different systems, it can be viewed as a transformation problem. Thus, cross-system transmission requires structured data with clear headers, such as `data` and `header`. Proper handling of these ensures that the message is accurately interpreted.

To store untamed data, we need an expandable structure for raw data and its metadata:

$$
Data1 \approx Data2 \\ Data1 + Metadata1 \equiv Data2 + Metadata2 \\
$$

According to reversible computing principles:
- **Data and metadata are interdependent**.
- Their combination must allow for accurate reconstruction of the original information.

## Deep Syntax vs. Shallow Syntax

In this document, **we leverage the built-in extensible metadata definition in ANTLR**, enabling us to achieve functionality beyond the original author's intentions.

## Deep Syntax vs. Shallow Syntax

The grandfather of modern linguistics, Noam Chomsky, introduced the Transformational-Generative Grammar theory in the 1950s, sparking what is known as the "Chomsky Revolution" in the field of linguistics.

> Terms like "regular grammar," "context-free grammar," and "context-sensitive grammar" originate from Chomsky's renowned levels.

Chomsky believed that each sentence has two structural levels—**deep structure** and **shallow structure**. The **deep structure** refers to the internal syntactic relationships between phrases or clauses, but these relationships cannot be directly observed from their linear sequences in the shallow structure. In the generative component (i.e., the grammar), he emphasized that it determines all factors influencing sentence interpretation, including the abstract syntactic expressions that define how a sentence's components interact.

The **shallow structure** is described as the final stage of a sentence's syntax, derived from the deep structure through transformational rules. While the shallow structure reflects the surface grammatical features of a sentence (e.g., word order and tense), it does not capture the underlying syntactic relationships.

Through the Transformational-Generative Grammar theory, sentences with different surface structures but equivalent meanings can be related—e.g., "Mary cleaned the room" and "The room was cleaned by Mary." Here, their deep structures are identical, while their shallow structures differ.

In simple terms, **deep syntax** determines meaning, while the **shallow structure** determines form. The deep structure is derived from transformational rules that convert abstract syntactic expressions into concrete ones, determining a sentence's ultimate form. A single deep structure can correspond to multiple shallow structures.

From this perspective, an abstract syntax tree (AST) represents the deep structure of a language, while an ANTLR-generated file defines the shallow structure. If we stick to Chomsky's framework and focus on stable domain structures (i.e., ASTs), why not establish a general, stable AST format instead of compromising with unstable, format-multiplying shallow structures? Is this not akin to Lisp?

The Nop platform's overall strategic direction can be described as revolves around the AST node structure. While it opts for XML and JSON over traditional Lisp syntax due to its more user-friendly nature, its primary objective is to serve as a domain-specific language workbench.

In the Nop platform, developing a domain-specific language requires one fundamental requirement: an abstract syntax tree definition file. The Nop platform generates AST node classes based on various definitions like xdef/xjava/xmeta. These ASTs can be derived from XML/JSON without writing any code, and they can be serialized back to XML/JSON.

If you need a syntax similar to traditional programming languages, you can supplement the ANTLR-generated files with additional definitions.

The reversible nature of transformational theory is not limited to a specific low-code product; it's a widely applicable next-generation software construction theory. Within this framework, scattered practices can be unified under a coherent theory.

The applicability of transformational theory is not confined to a single system—it thrives when all upstream and downstream systems adhere to reversible computing principles. **Only when all software components follow the reversible computing principles can we achieve unprecedented granularity in software reuse**. This is precisely what Lisp language embodies.

The Nop platform's overall technical strategy revolves around AST node structures, choosing between XML and JSON over traditional Lisp syntax due to its more user-friendly nature. However, the primary goal of the Nop platform is to become a general-purpose domain-specific language workbench.

The Nop platform's design objective is to minimize the cost of developing new domain-specific languages by providing comprehensive technical support. While syntax (e.g., specific programming languages) may be secondary, semantics (e.g., domain-specific concepts and their interactions) are paramount.

To develop a new domain-specific language on the Nop platform, the most basic requirement is an abstract syntax tree definition file. The Nop platform generates AST node classes based on various definitions like xdef/xjava/xmeta. These ASTs can be derived from XML/JSON without writing any code and can be serialized back to XML/JSON.

If you need a syntax similar to traditional programming languages, you can supplement the ANTLR-generated files with additional definitions.

Reversible computing theory is not limited to a specific low-code product; it's a widely applicable next-generation software construction theory. Within this framework, scattered practices can be unified under a coherent theory.

The applicability of transformational theory is not confined to a single system—it thrives when all upstream and downstream systems adhere to reversible computing principles. **Only when all software components follow the reversible computing principles can we achieve unprecedented granularity in software reuse**. This is precisely what Lisp language embodies.

> References

For more details on the techniques discussed in this document, please refer to the following resources:

- [XLangAST.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/ast/io/nop/xlang/ast/XLangAST.xjava)  
- [XLangParser.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/antlr/XLangParser.g4)

- [EqlAST.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava)  
- [DMLStatement.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/antlr/DMLStatement.g4)

