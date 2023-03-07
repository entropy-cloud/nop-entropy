parser grammar XPathCommon;

options {
    tokenVocab=XPathLexer;
}

qualifiedType
    : Identifier ('.' identifier)*
    ;

qualifiedName
    : Identifier ('.' identifier)*
    ;

propertyName
    : identifierOrKeyword
    | StringLiteral
//    | IntegerLiteral
    ;

identifier: From|TypeAlias|Identifier;

identifierOrKeyword
    : identifier
    | reservedWord
    ;

reservedWord
    : keyword
    | NullLiteral
    | BooleanLiteral
    | AndLiteral
    | OrLiteral
    ;

keyword
    : Break
    | As
    | Do
    | Instanceof
    | Typeof
    | Case
    | Else
    | New
    | Var
    | Catch
    | Finally
    | Return
    | Void
    | Continue
    | For
    | Switch
    | While
    | Debugger
    | Function
    | This
    | With
    | Default
    | If
    | Throw
    | Delete
    | In
    | Try
    | ReadOnly
//    | Async
//    | From
    | Class
    | Enum
    | Extends
    | Super
    | Const
    | Export
    | Import
    | Implements
    | Let
    | Private
    | Public
    | Interface
    | Package
    | Protected
    | Static
//    | Yield
//    | Get
//    | Set
//    | Require
    | TypeAlias
    | String
    | Boolean
    | Number
    | Any
    | Symbol
    ;

literal
    : NullLiteral
    | BooleanLiteral
    | StringLiteral
    | TemplateStringLiteral
    | RegularExpressionLiteral
    | numericLiteral
    ;

numericLiteral
    : DecimalIntegerLiteral
    | HexIntegerLiteral
//    | OctalLiteral
    | DecimalLiteral
//    | HexFloatLiteral
    | BinaryIntegerLiteral
    ;

