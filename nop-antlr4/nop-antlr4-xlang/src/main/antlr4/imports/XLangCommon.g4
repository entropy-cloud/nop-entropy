parser grammar XLangCommon;

options {
    tokenVocab=XLangBaseLexer;
}

qualifiedName
    : name=qualifiedName_name_ (Dot next=qualifiedName)?
    ;

qualifiedName_name_:
  identifier;

qualifiedName_: qualifiedName;

propertyName_
    : identifierOrKeyword_
    | StringLiteral
//    | IntegerLiteral
    ;

expression_propName:
   identifier_ex | literal_string;

identifier_ex:
   name=identifierOrKeyword_;

identifier
options{
  astProp=name;
}: From|TypeAlias|Identifier;

identifierOrKeyword_
    : identifier
    | reservedWord_
    ;

reservedWord_
    : keyword_
    | NullLiteral
    | BooleanLiteral
    | AndLiteral
    | OrLiteral
    ;

keyword_
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
options{
  astProp=value;
}
    : NullLiteral
    | BooleanLiteral
    | StringLiteral
    | TemplateStringLiteral
    | RegularExpressionLiteral
    | literal_numeric
    ;

literal_numeric
options{
  astProp=value;
}
    : DecimalIntegerLiteral
    | HexIntegerLiteral
//    | OctalLiteral
    | DecimalLiteral
//    | HexFloatLiteral
    | BinaryIntegerLiteral
    ;

literal_string
options{
 astProp=value;
}: StringLiteral;