In the Nop platform, based on g4 grammar files, by adding only a few naming conventions and metadata settings, you can automatically parse and obtain the AST, which greatly reduces the cost of AST reconstruction.

# AST Mapping Conventions

Compared with the Abstract Syntax Tree (AST), the Parse Tree (ParseTree) contains more detailed information. Therefore, in theory, as long as we add some annotations on the ParseTree, ignore the parts we don't care about, and keep only what we need, we can achieve our goal. Specifically, we define the following annotation rules:

## 1. One parsing rule maps to one kind of AST node

The most natural case is that a parsing rule directly corresponds to an AST node, and the name of the rule corresponds directly to the class name of the AST node. For example:

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

The sqlDelete rule directly corresponds to the SqlDelete class. The antlr grammar allows specifying explicit variable names for grammar elements via altLabel. This mechanism can be used to mark properties of AST nodes. For example, tableName=sqlTableName indicates that the tableName property is parsed according to the sqlTableName rule. At the same time, all parts not marked with altLabel will be ignored, such as the DELETE keyword.

### 2. Each alternative of a parsing rule maps to a kind of AST node

A single parsing rule may also correspond to multiple AST nodes, with each alternative corresponding to a different case. The parsed object type is the base class of the objects produced by all alternatives. For example:

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

The sqlStatement rule maps to the base class SqlStatement for the results of all alternatives, and each grammar alternative parses to a specific case.

> If a rule name is prefixed with ast_, it indicates that the return type of the parse function is the common base class of AST nodes. For example, in the XLangParser.g4 file, the return type of the ast_topLevelStatement rule is XLangASTNode. XLangASTNode is the common base class defined for all AST nodes in XLangAST.

### 3. Multiple parsing rules or alternatives map to the same AST node type

Different parsing rules, or different alternatives of one rule, may map to the same AST node. For example:

```
expression_single
    : left=expression_single operator=('*' | '/' | '%') right=expression_single    # BinaryExpression_multiplicative
    | left=expression_single operator=('+' | '-') right=expression_single        # BinaryExpression_additive
    ...;
```

antlr relies on the order of parsing rules to determine operator precedence, so the same BinaryExpression is split into multiple grammar alternatives based on the different operators. This situation, where multiple grammar rules parse to the same AST node type, is very common. We can distinguish the different cases by appending suffixes. In the example above, both BinaryExpression_multiplicative and BinaryExpression_additive parse to the BinaryExpression type. Meanwhile, the overall expression_single rule itself parses to the type Expression.

### 4. A parsing rule maps to a property of an AST node

Some parsing rules do not correspond to an AST node, but to a property of an AST node. For example:

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

The assignmentOperator_ rule maps to the operator property of the AssignmentExpression object, whose type is XLangOperator. For parsing rules that do not correspond to AST nodes, we stipulate that their names end with `_`. Note that this is equivalent to the convention that **all parsing rules whose names do not end with `_` must produce an AST node type**.

### 5. A parsing rule maps to a list of AST nodes

A parsing rule may also correspond to a list of AST nodes, such as a function's parameter list or a field list in an SQL statement. For example:

```
sqlInsert
    : INSERT INTO tableName=sqlTableName LP_ {indent();} columns=columnNames_ { br();} RP_
        ( values=sqlValues | select=sqlSelect)
    ;

columnNames_
    : e=sqlColumnName (COMMA_ e=sqlColumnName)*
    ;    
```

We stipulate that each list corresponds to a separate parsing rule, and within that rule the property e is used to mark the element part of the list. For example, the columnNames_ rule parses into a List type.

### 6. Ignore certain parse tree nodes

Some parsing rule results may not correspond to any part of the AST. For example, the end-of-statement marker:

```
eos__
    : SemiColon
    | EOF
    | {this.lineTerminatorAhead()}?
    | {this.closeBrace()}?
    ;
```

We stipulate that rules ending with `__` do not participate in AST construction.

### 7. Simplify the mapping of terminal symbols

For mapping rules where terminal symbols directly correspond to AST properties, it would be too complex to define a separate parsing rule for each terminal symbol. This can be simplified using the astProp attribute annotation.

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

> options is an antlr built-in metadata extension mechanism that allows setting custom extensible attributes.

Note that in our conventions, terminal symbols will not directly correspond to AST nodes; at most they correspond to a single property of an AST node. For example, in the literal_string rule, the terminal symbol StringLiteral is parsed to the value property of a Literal node.

Where multiple terminal symbols correspond to the same AST node, you can uniformly specify the target property name via the astProp option, avoiding the need to specify each alternative case individually.

In the example above, all alternatives of literal must correspond to a single terminal symbol, and their parse results all map to the value property of the Literal node.

### 8. Auxiliary rules

Sometimes, for ease of writing, we introduce auxiliary rules. For example:

```
expression_initializer
    : '=' expression_single
    ;
```

Currently, only one kind of auxiliary rule is allowed: the kind shown above, where only one grammar component inside the rule ultimately corresponds to an AST node. This is equivalent to adding some terminal symbols (that do not end up in the AST) around an existing AST rule. The result type parsed by an auxiliary rule is the same as that of the inner rule.

### 9. Avoid naming collisions

Due to antlr implementation constraints, it does not allow an altLabel to duplicate an existing rule name. When a conflict occurs, you can avoid it by appending a trailing underscore to the altLabel.

```
arrayBinding
    : '[' elements=arrayElementBindings_ (Comma restBinding_=restBinding |Comma)? CloseBracket # ArrayBinding_full
    | OpenBracket restBinding_=restBinding Comma? CloseBracket # ArrayBinding_rest
    ;
```

restBinding itself is a rule name and also corresponds to the class name of the AST node. Therefore, in the arrayBinding rule, you cannot use restBinding as the altLabel property name; you need to replace it with restBinding_.

### 10. Set fixed property values

Sometimes when obtaining an AST node from a parsing rule, we want to set a certain property of the AST node directly to a fixed value, rather than mapping the property from the ParseTree. For example:

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

antlr allows adding so-called ELEMENT_OPTIONS metadata to grammar elements in the form `<name=value>`. astAssign is an extension option we added. Its format is astAssign='name1:value1,name2:value2'. For boolean properties, you can use a short form with only the property name. For example, `<astAssign=computed>` means that after parsing the AST node, the code will automatically set astNode.computed = true.

### 11. Formatting rules

To support formatted output of syntax trees, you can add indent and br annotations in the action sections of the g4 file.

```
sqlDelete
    : DELETE FROM tableName=sqlTableName AS? alias=sqlAlias? 
      (<br> where=sqlWhere)?
    ;
```

The rule above indicates that after outputting alias, a line break is needed.

> Currently, when reading antlr grammar files, the code generator supports only the above cases. Therefore, third-party antlr grammars need to be adapted before they can be integrated with the code generator to directly produce an AST parser.

## Code that needs to be handwritten

The XXXBuildVisitor class generated by the Nop platform's code generator is responsible for converting antlr's ParseTree into the specified AST objects. It requires a few helper functions to parse terminal symbols.

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

The code generator automatically generates the BuildVisitor framework; you only need to fill in a small number of mapping functions. For example, how to map from the operator in the ParseTree of the assignmentExpression rule to the XLangOperator enum value.

In addition, ASTNode provides normalize and validate functions. By customizing these two functions, after AST node initialization, we can first normalize the AST node structure based on the parse results (e.g., populate default parts or simplify the AST structure), and then validate that the abstract AST nodes meet the specification requirements. For instance, the Identifier node can automatically validate that the name property is not empty.

## Automatically generating AST node classes

When using an Antlr parser, AST node classes are usually handwritten, which is a certain amount of work. Moreover, typically one Java file corresponds to one AST node type, which makes it hard to get an intuitive overview of the entire AST. In addition, operations such as AST copying, comparison, traversal, and transformation are pattern-like boilerplate code: expensive to write by hand and prone to errors. Therefore, we defined a description grammar for ASTs, and generate various AST node classes directly from it, as well as auxiliary processing classes such as ASTVisitor/ASTOptimizer/ASTProcessor.

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

We define the AST structure using the so-called xjava syntax. Interestingly, xjava syntax is just ordinary Java syntax, and we can introduce additional descriptive information via annotations such as @PropMeta. The Nop platform uses the Janino library to parse xjava files, convert them into ObjMeta definitions, and then generate corresponding Java classes from ObjMeta.

> ObjMeta is an object metadata specification defined in the Nop platform, similar to JSON Schema, and it has an XML definition syntax. In other words, you can define ObjMeta either in XML format or xjava format, and XML and xjava formats can be converted to each other. This situation—one structure having multiple representational formats—is exactly the concept of multiple Representations emphasized in Reversible Computation.
> In different usage scenarios, we can choose the appropriate representation as needed. For example, for visual editing we can choose XML or JSON formats (the Nop platform defines reversible conversion rules between XML and JSON), while for human reading or leveraging IDE refactoring we can use the xjava format.

If we compare it with the proto format used in gRPC, we’ll find that xjava can also describe Protocol Buffers structures—so long as we allow introducing additional metadata via annotation classes!

According to Reversible Computation, metadata and data form a complementary relationship. Only metadata + data together fully describe structural information. Moreover, under certain conditions, there is no essential difference between metadata and data, and they can be transformed into each other.

```
Structure = Data1 + Metadata1 = Data2 + Metadata2
```

In different representations, if we only focus on the data under the spotlight, we may find them nonequivalent, with distortion and loss in transformation.

$$
Data1 \approx Data2 \\ Data1 \ne Data2 \\
$$

Only by recognizing metadata as a necessary and indispensable part of information expression—and always pairing metadata with data as a whole—can we achieve the reversibility required by Reversible Computation.

$$
Data1 \approx Data2 \\ Data1 + Metadata1 \equiv Data2 + Metadata2 \\
$$

Following the analysis of Reversible Computation, information transmission between different systems can also be regarded as a representation conversion problem. Therefore, cross-system message objects must have a data + header structure, and we need an extensible way to store metadata whose structure may not yet be fixed!

In the technical approach introduced in this article, it is precisely by leveraging antlr’s built-in extensible metadata definitions that we can achieve functionality beyond the original author’s intent.

## Deep Syntax vs. Surface Syntax

In the 1950s, Noam Chomsky, the founding father of modern language theory, proposed Transformational-Generative Grammar, triggering the so-called “Chomsky Revolution” in linguistics.

> The well-known concepts of regular grammars, context-free grammars, context-sensitive grammars, etc., come from the famous Chomsky hierarchy.

Chomsky posited that every sentence has two structural levels—deep structure and surface structure. Deep structure “refers to the inherent grammatical relations among the constituents of a phrase or sentence, relations that cannot be directly read off from their linear sequence. In generative grammar, it is the abstract syntactic representation of a sentence that determines all factors governing how the sentence is interpreted,” i.e., it determines meaning. Surface structure “refers to the final stage of a sentence’s syntactic representation, which is derived from deep structure; it is the result of a linear arrangement of the relationships among the constituents of the sentence used in communication,” i.e., the sentence’s realized form. Transformational-Generative Grammar links sentences that differ in surface form but share the same meaning. For example, “Mary cleaned the room” and “The room was cleaned by Mary” have different surface structures but the same deep structure.

Simply put, deep syntax determines semantics; deep structure is transformed into surface structure via transformation rules; and surface syntax determines the sentence’s final appearance. The same deep syntax can correspond to multiple surface syntaxes. In this light, the AST can be seen as the deep structure of a language, whereas the antlr grammar file specifies a kind of surface syntax.

If we push this line of thinking further: since what truly matters is a stable domain structure (i.e., the AST), why not stipulate a universal, stable form of AST representation and completely abandon unstable, varied surface syntax forms? Isn’t this essentially Lisp?

The Nop platform’s overall technical strategy revolves around AST tree structures, choosing XML and JSON—formats friendlier to editing—as structured representations instead of Lisp’s traditional syntax.

The Nop platform aims to be a general-purpose Domain Specific Language Workbench; that is, it provides a suite of technical support to create new domain languages and strives to minimize their development cost. The most important part of a DSL is its semantic structure (the domain-specific atomic concepts and how they interact), while the apparent syntactic form is secondary.

To develop a new domain language in the Nop platform, the fundamental requirement is just one: provide an AST definition file. The Nop platform will generate AST node classes based on various AST definition formats such as xdef/xjava/xmeta. These defined ASTs inherently support both XML and JSON representations. Without writing any code, you can parse ASTs from XML/JSON and serialize ASTs back to XML/JSON. If you need a syntax similar to traditional programming languages, you can additionally define an antlr grammar file.

Reversible Computation is not a simple design pattern aimed at a specific low-code product; it is a next-generation software construction theory with broad applicability. Within the theoretical framework of Reversible Computation, a series of scattered design practices can be given a unified theoretical explanation, naturally yielding a series of inherently consistent extensibility designs.

The scope of Reversible Computation is by no means limited to a single system. In fact, only when both upstream and downstream software adhere to Reversible Computation principles can we achieve unprecedented coarse-grained software reuse. In the next article, I will introduce a technical approach that integrates Reversible Computation with Office Word to implement template-based export. With just a few hundred lines of code, it delivers functionality similar to [poi-tl](http://deepoove.com/poi-tl/), and—for free—offers extensibility surpassing other approaches.

## References

For concrete configuration examples of the Antlr-based automatic AST parsing introduced in this article, see [XLangAST.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/ast/io/nop/xlang/ast/XLangAST.xjava) [XLangParser.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/antlr/XLangParser.g4)

[EqlAST.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava) [DMLStatement.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/antlr/DMLStatement.g4)
<!-- SOURCE_MD5:f42787ee793cfe152c12433d04bcba72-->
