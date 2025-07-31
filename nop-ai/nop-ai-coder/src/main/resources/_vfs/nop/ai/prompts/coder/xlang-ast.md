```java
package io.nop.xlang.ast;

import io.nop.api.core.annotations.meta.PropMeta;

import java.util.List;

public class XLangAST {
  // Preserved enums from original
  enum VariableKind {
    CONST,
    LET
  }

  enum XLangOperator {
    // Sample operators
    PLUS, MINUS, MULTIPLY, DIVIDE
  }

  enum PropertyKind {
    init,
    get,
    set
  }

  abstract class XLangASTNode {
  }

  abstract class Expression extends XLangASTNode {
  }

  abstract class Statement extends Expression {
  }

  class Identifier extends Expression implements IdentifierOrPattern {
    @PropMeta(mandatory = true)
    String name;
  }

  class Literal extends Expression {
    Object value;
  }

  class BlockStatement extends Statement {
    @PropMeta(mandatory = true)
    List<Expression> body;
  }

  class VariableDeclaration extends Declaration {
    @PropMeta(mandatory = true)
    VariableKind kind;

    @PropMeta(mandatory = true, minItems = 1)
    List<VariableDeclarator> declarators;
  }

  class BinaryExpression extends Expression {
    @PropMeta(mandatory = true)
    XLangOperator operator;

    @PropMeta(mandatory = true)
    Expression left;

    @PropMeta(mandatory = true)
    Expression right;
  }

  class FunctionDeclaration extends Declaration {
    @PropMeta(mandatory = true)
    Identifier name;

    @PropMeta(mandatory = true)
    List<ParameterDeclaration> params;

    @PropMeta(mandatory = true)
    Expression body;
  }

  class Program extends Expression {
    @PropMeta(mandatory = true)
    List<XLangASTNode> body;
  }

  interface IdentifierOrPattern {
  }

  abstract class Declaration extends Statement {
  }

  class ParameterDeclaration {
    @PropMeta(mandatory = true)
    XLangASTNode name;
  }

  class VariableDeclarator {
    XLangASTNode id;
    Expression init;
  }
}
```

* 非空属性需要标注`@PropMeta(mandatory=true)`注解。如果有可能为空，则一定不要增加这个注解。
