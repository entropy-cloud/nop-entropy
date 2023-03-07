parser grammar XPathExpr;

import XPathCommon;

options {
    tokenVocab=XPathLexer;
}

parameterList
    :  parameter (Comma parameter)*
    ;

//requiredParameterList
//     : requiredParameter (Comma requiredParameter)*
//     ;
//
//parameter
//     : requiredParameter
//     | optionalParameter
//     ;

parameter
  :   identifierOrPattern
  ;



//restParameter
//    : decoratorList? '...' Identifier typeAnnotation?
//    ;

// requiredParameter
//     : decoratorList? accessibilityModifier? identifierOrPattern typeAnnotation? initializer
//     ;

//accessibilityModifier
//    : Public
//    | Private
//    | Protected
//    ;

identifierOrPattern
    : identifier
    ;

// A.5 Interface
//
//interfaceDeclaration
//    : Interface Identifier typeParameters? interfaceExtendsClause? objectType SemiColon?
//    ;
//
//interfaceExtendsClause
//    : Extends classOrInterfaceTypeList
//    ;
//
//classOrInterfaceTypeList
//    : typeReference (Comma typeReference)*
//    ;

namespaceName
    : Identifier ('.' identifier)*;

arrayLiteral
    : ('[' elementList? CloseBracket)
    ;

elementList
    : arrayElement (Comma arrayElement)* Comma?
    ;

arrayElement                      // ECMAScript 6: Spread Operator
    : Ellipsis? singleExpression
    ;

objectLiteral
    : OpenBrace (propertyAssignment (Comma propertyAssignment)*)? Comma? CloseBrace
    ;

// MODIFIED
propertyAssignment
    : propertyName ':' singleExpression                # PropertyExpressionAssignment
//    : propertyName (':' |'=') singleExpression                # PropertyExpressionAssignment
    | '[' key=singleExpression CloseBracket ':' value=singleExpression           # ComputedPropertyExpressionAssignment
//    | getAccessor                                             # PropertyGetter
//    | setAccessor                                             # PropertySetter
//    | generatorMethod                                         # MethodProperty
    | identifierOrKeyword                                     # PropertyShorthand
    | '...' singleExpression                                  # RestParameterInObject
    ;

//
//getAccessor
//    : getter OpenParen CloseParen typeAnnotation? OpenBrace functionBody CloseBrace
//    ;
//
//setAccessor
//    : setter OpenParen identifierOrPattern typeAnnotation? CloseParen OpenBrace functionBody CloseBrace
//    ;
//
//getter
//    : Get propertyName
//    ;
//
//setter
//    : Set propertyName
//    ;


arguments
    : OpenParen argumentList? CloseParen
    ;

argumentList
    : argument (Comma argument)* Comma?
    ;

argument                      // ECMAScript 6: Spread Operator
    :  singleExpression
    ;

expressionSequence
    : singleExpression (Comma singleExpression)*
    ;

initExpression
   : Identifier '=' singleExpression
   ;

initExpressionSequence
   : initExpression (Comma initExpression) *
   ;

//functionExpressionDeclaration
//    : Function Identifier OpenParen parameterList? CloseParen typeAnnotation? OpenBrace functionBody CloseBrace
//    ;

singleExpression
    :
    Identifier {this.notLineTerminator()}? TemplateStringLiteral           # TemplateStringExpression  // ECMAScript 6
//    | iteratorBlock                                                          # IteratorsExpression // ECMAScript 6
   // | generatorBlock                                                         # GeneratorsExpression // ECMAScript 6
  //  | generatorFunctionDeclaration                                           # GeneratorsFunctionExpression // ECMAScript 6
  //  | yieldStatement                                                         # YieldExpression // ECMAScript 6
    | This                                                                   # ThisExpression
    | Identifier                                                             # IdentifierExpression
    | Super                                                                  # SuperExpression
    | literal                                                                # LiteralExpression
    | arrayLiteral                                                           # ArrayLiteralExpression
    | objectLiteral                                                          # ObjectLiteralExpression
    | OpenParen singleExpression CloseParen                                             # ParenthesizedExpression
    //functionExpressionDeclaration                                          # FunctionExpression
//    | Class Identifier? classTail                                            # ClassExpression
    | obj=singleExpression '[' index=singleExpression CloseBracket                            # MemberIndexExpression
    | singleExpression op=('?.'|'.') identifierOrKeyword                               # MemberDotExpression
    | singleExpression arguments                                             # CallExpression
    | Switch OpenParen value=argument Comma argumentList CloseParen             # SwitchExpression
    | If OpenParen argument Comma argumentList  CloseParen                              # IfExpression
    | singleExpression {this.notLineTerminator()}? '++'                      # PostIncrementExpression
    | singleExpression {this.notLineTerminator()}? '--'                      # PostDecreaseExpression
    | singleExpression {this.notLineTerminator()}? '!'                      # ChainExpression
//    | Void singleExpression                                                  # VoidExpression
    | Typeof singleExpression                                                # TypeofExpression
    | op='++' singleExpression                                                  # PreIncrementExpression
    | op='--' singleExpression                                                  # PreDecreaseExpression
    | op='+' singleExpression                                                   # UnaryPlusExpression
    | op='-' singleExpression                                                   # UnaryMinusExpression
    | op='~' singleExpression                                                   # BitNotExpression
    | op='!' singleExpression                                                   # NotExpression
    | left=singleExpression bop=('*' | '/' | '%') right=singleExpression                    # MultiplicativeExpression
    | left=singleExpression bop=('+' | '-') right=singleExpression                          # AdditiveExpression
    | left=singleExpression bop=('<<' | '>>' | '>>>') right=singleExpression                # BitShiftExpression
    | left=singleExpression bop=('<' | '>' | '<=' | '>=') right=singleExpression            # RelationalExpression
    | left=singleExpression Instanceof right=qualifiedType                           # InstanceofExpression
    | left=singleExpression In right=singleExpression                                   # InExpression
    | left=singleExpression bop=('==' | '!=' | '===' | '!==') right=singleExpression        # EqualityExpression
    | left=singleExpression '&' right=singleExpression                                  # BitAndExpression
    | left=singleExpression '^' right=singleExpression                                  # BitXOrExpression
    | left=singleExpression '|' right=singleExpression                                  # BitOrExpression
    | left=singleExpression bop=('&&' | AndLiteral) right=singleExpression                  # LogicalAndExpression
    | left=singleExpression bop=('||' | OrLiteral)  right=singleExpression                  # LogicalOrExpression
    | left=singleExpression NullCoalesce right=singleExpression                         # NullCoalesceExpression
    | <assoc=right> test=singleExpression '?' consequence=singleExpression
    ':' alternate=singleExpression             #TernaryExpression
//    |  singleExpression '=' singleExpression                                  # AssignmentExpression
//    | singleExpression assignmentOperator singleExpression                   # AssignmentOperatorExpression
  //  | typeArguments expressionSequence?                                      # GenericTypes
    ;

//asExpression
//    : namedType
//    ;


//stringExprSequence
//    : SE_String stringExprSequence
//    | SE_ExprStart statementList SE_ExprEnd stringExprSequence*
//    ;