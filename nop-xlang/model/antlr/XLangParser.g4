parser grammar XLangParser;

import XLangTypeSystem;

options {
    tokenVocab=XLangLexer;
    superClass=XLangParserBase;
}

// SupportSyntax

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

// ECMAPart
program
    : body=topLevelStatements_ EOF
    ;

topLevelStatements_:
   e=ast_topLevelStatement*;

ast_topLevelStatement
    :
     moduleDeclaration_import  # ModuleDeclaration_import2
    | ast_exportStatement # ast_exportStatement2
    | Export declaration=functionDeclaration # ExportDeclaration_func
    | Export declaration=typeAliasDeclaration # ExportDeclaration_type
    | Export declaration=variableDeclaration_const # ExportDeclaration_const
    | statement  # Statement_top
    ;


statement
    : blockStatement
    | variableDeclaration
    | emptyStatement
//    | abstractDeclaration //ADDED
//    | decoratorList
//    | classDeclaration
//    | interfaceDeclaration //ADDED
//    | namespaceDeclaration //ADDED
    | ifStatement
    | statement_iteration
    | continueStatement
    | breakStatement
    | returnStatement
//    | yieldStatement
//    | withStatement
//    | labelledStatement
    | switchStatement
    | throwStatement
    | tryStatement
//    | tryWithResourcesStatement
//    | debuggerStatement
    | functionDeclaration
//    | arrowFunctionDeclaration
//    | generatorFunctionDeclaration
    | typeAliasDeclaration //ADDED
    | enumDeclaration      //ADDED
    | expressionStatement
    | assignmentExpression
//    | Export statement
    ;


//abstractDeclaration
//    : Abstract (Identifier callSignature | variableStatement) eos
//    ;

moduleDeclaration_import
    : importDeclaration | importAsDeclaration
    ;

importDeclaration
   : Import OpenBrace specifiers=importSpecifiers_ CloseBrace From source=literal_string eos__
//    : (Multiply | multipleImportStatement) (As identifierName)? From StringLiteral eos
    ;

importSpecifiers_
options{
   elementAstNodeName=ModuleSpecifier;
}:
     e=importSpecifier (Comma e=importSpecifier)*;

importSpecifier
   : imported=identifier (As local=identifier)?;

importAsDeclaration
    : Import source=ast_importSource (As local=identifier)? eos__
    ;

ast_importSource:
    qualifiedName
    | literal_string;

ast_exportStatement
    : exportNamedDeclaration
    ;

exportNamedDeclaration
   : Export OpenBrace specifiers=exportSpecifiers_ CloseBrace From source=literal_string eos__;

exportSpecifiers_:
   e=exportSpecifier (Comma e=exportSpecifier)*;

exportSpecifier
   : local=identifier (As exported=identifier)?;

blockStatement
    : OpenBrace body=statements_ CloseBrace
    ;

statements_
options{
  elementAstNodeName=Expression;
}:
  e=statement*;

variableDeclaration_const:
   kind=Const declarators=variableDeclarators_  eos__;

variableDeclaration
    //: varModifier? bindingPattern typeAnnotation? initializer SemiColon?
     : kind=varModifier_ declarators=variableDeclarators_ eos__
//    | accessibilityModifier? varModifier? ReadOnly? variableDeclarationList SemiColon?
//    | Declare varModifier? variableDeclarationList SemiColon?
    ;

varModifier_
    : Let
    | Const
    ;

variableDeclarators_
    : e=variableDeclarator (Comma e=variableDeclarator)*
    ;

variableDeclarator
   : id=ast_identifierOrPattern varType=namedTypeNode_annotation? init=expression_initializer?
//    : ( identifierOrKeyWord | arrayLiteral | objectLiteral) typeAnnotation? singleExpression? ('=' typeParameters? singleExpression)? // ECMAScript 6: Array & Object Matching
    ;

emptyStatement
    : SemiColon
    ;

expressionStatement
    : {this.notOpenBraceAndNotFunction()}? expression=expression_single eos__
    ;

ifStatement
    : If OpenParen test=expression_single CloseParen consequent=statement (Else alternate=statement)?
    ;

statement_iteration
    : Do body=statement While OpenParen test=expression_single CloseParen eos__       # DoWhileStatement
    | While OpenParen test=expression_single CloseParen body=statement              # WhileStatement
    | For OpenParen init=expression_forInit? SemiColon test=expression_single? SemiColon
        update=sequenceExpression? CloseParen body=statement     # ForStatement
    | For OpenParen left=expression_iterationLeft In right=expression_single CloseParen body=statement  # ForInStatement
    | For OpenParen left=expression_iterationLeft Identifier{this.p("of")}? right=expression_single CloseParen
        body=statement         # ForOfStatement
    ;

expression_iterationLeft
    : identifier  # Identifier_for
     | kind=varModifier_ declarators_single=variableDeclarator # VariableDeclaration_for
    ;

//    : Var
//    | Let
//    | Const
//    ;

continueStatement
    : Continue eos__
    ;

breakStatement
    : Break eos__
    ;

returnStatement
    : Return ({this.notLineTerminator()}? argument=expression_single)? eos__
    ;

//yieldStatement
//    : Yield ({this.notLineTerminator()}? expressionSequence)? eos
//    ;

//withStatement
//    : With OpenParen singleExpression CloseParen statement
//    ;

assignmentExpression
    :  left=expression_leftHandSide operator=assignmentOperator_ right=expression_single eos__
    ;

expression_leftHandSide
    : identifier
      | memberExpression
     ;

switchStatement
    : Switch OpenParen discriminant=expression_single CloseParen OpenBrace cases=switchCases_ defaultCase=statement_defaultClause? CloseBrace
    ;

switchCases_:
    e=switchCase+;

switchCase
    : Case test=expression_single ':' consequent=blockStatement?
    ;

statement_defaultClause
    : Default ':' blockStatement?
    ;
//
//labelledStatement
//    : Identifier ':' statement
//    ;

throwStatement
    : Throw {this.notLineTerminator()}? argument=expression_single eos__
    ;

tryStatement
    :  Try block=blockStatement (catchHandler=catchClause)? finalizer=blockStatement_finally
    ;

//tryWithResourcesStatement
//	:	Try resourceSpecification blockStatement catchClause? finallyBlock?
//	;

//catchProduction
//    : Catch OpenParen Identifier (':' typeName)? CloseParen block
//    ;

catchClause
    : Catch OpenParen name=identifier (':' varType=parameterizedTypeNode)? CloseParen body=blockStatement
    ;


blockStatement_finally
    : Finally blockStatement
    ;

//resourceSpecification
//    : OpenParen resources ';'? CloseParen
//    ;

//resources
//    : varModifier? variableDeclarators
//    ;

//
//debuggerStatement
//    : Debugger eos
//    ;

functionDeclaration
    : decorators_=decorators? Function name=identifier OpenParen params=parameterList_? CloseParen
        returnType=namedTypeNode_annotation? body=blockStatement
    ;


parameterList_
    :  e=parameterDeclaration (Comma e=parameterDeclaration)*
    ;

//requiredParameterList
//     : requiredParameter (Comma requiredParameter)*
//     ;
//
//parameter
//     : requiredParameter
//     | optionalParameter
//     ;

parameterDeclaration
  :   decorators_=decorators? name=ast_identifierOrPattern type=namedTypeNode_annotation? initializer=expression_initializer?
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

ast_identifierOrPattern
    : identifier
    | arrayBinding
    | objectBinding
    ;

expression_initializer
    : '=' expression_single
    ;

arrayBinding
    : '[' elements=arrayElementBindings_ (Comma restBinding_=restBinding |Comma)? CloseBracket # ArrayBinding_full
    | OpenBracket restBinding_=restBinding Comma? CloseBracket # ArrayBinding_rest
    ;

arrayElementBindings_:
  e=arrayElementBinding (Comma e=arrayElementBinding)*;

arrayElementBinding
    : identifier_=identifier initializer=expression_initializer?;

objectBinding
    : OpenBrace (properties=propertyBindings_)? (Comma restBinding_=restBinding |Comma)? CloseBrace # ObjectBinding_full
    | OpenBrace restBinding_=restBinding Comma? CloseBrace # ObjectBinding_rest
    ;

propertyBindings_:
    e=propertyBinding (Comma e=propertyBinding)*;

// MODIFIED
propertyBinding
    : propName=propertyName_ ':' identifier_=identifier  initializer=expression_initializer? #PropertyBinding_full
    | identifier_=identifier initializer=expression_initializer? #PropertyBinding_simple
    ;

restBinding
    : '...' identifier_=identifier initializer=expression_initializer?
    ;

//Ovveride ECMA
//classDeclaration
//    : Abstract? Class Identifier typeParameters? classHeritage classTail
//    ;
//
//classHeritage
//    : classExtendsClause? implementsClause?
//    ;
//
//classTail
//    :  OpenBrace classElement* CloseBrace
//    ;

//classExtendsClause
//    : Extends typeReference
//    ;
//
//implementsClause
//    : Implements classOrInterfaceTypeList
//    ;

// Classes modified
//classElement
//    : constructorDeclaration
//    | decoratorList? propertyMemberDeclaration
//    | indexMemberDeclaration
//    | statement
//    ;

//propertyMemberDeclaration
//    : propertyMemberBase propertyName '?'? typeAnnotation? initializer? SemiColon                   # PropertyDeclarationExpression
//    | propertyMemberBase propertyName callSignature ( (OpenBrace functionBody CloseBrace) | SemiColon)           # MethodDeclarationExpression
//    | propertyMemberBase (getAccessor | setAccessor)                                                # GetterSetterDeclarationExpression
//    | abstractDeclaration                                                                           # AbstractMemberDeclaration
//    ;

//propertyMemberBase
//    : Async? accessibilityModifier? Static? ReadOnly?
//    ;

//indexMemberDeclaration
//    : indexSignature SemiColon
//    ;

//generatorMethod
//    : '*'?  Identifier OpenParen formalParameterList? CloseParen OpenBrace functionBody CloseBrace
//    ;
//
//generatorFunctionDeclaration
//    : Function '*' Identifier? OpenParen formalParameterList? CloseParen OpenBrace functionBody CloseBrace
//    ;
//
//generatorBlock
//    : OpenBrace generatorDefinition (Comma generatorDefinition)* Comma? CloseBrace
//    ;
//
//generatorDefinition
//    : '*' iteratorDefinition
//    ;

//iteratorBlock
//    : OpenBrace iteratorDefinition (Comma iteratorDefinition)* Comma? CloseBrace
//    ;
//
//iteratorDefinition
//    : '[' singleExpression CloseBracket OpenParen formalParameterList? CloseParen OpenBrace functionBody CloseBrace
//    ;

//formalParameterList
//    : formalParameterArg (Comma formalParameterArg)* (Comma lastFormalParameterArg)?
//    | lastFormalParameterArg
//    | arrayLiteral                              // ECMAScript 6: Parameter Context Matching
//    | objectLiteral (':' formalParameterList)?  // ECMAScript 6: Parameter Context Matching
//    ;
//
//formalParameterArg
//    : decorator?  Identifier Question? typeAnnotation? ('=' singleExpression)?      // ECMAScript 6: Initialization
//    ;

//accessibilityModifier
//    : Public
//    | Private
//    | Protected
//    ;

//lastFormalParameterArg                        // ECMAScript 6: Rest Parameter
//    : Ellipsis Identifier
//    ;

//functionBody
//    : statementList?
//    ;

arrayExpression
    : '[' elements=elementList_? CloseBracket
    ;

elementList_
    : e=ast_arrayElement (Comma e=ast_arrayElement)* Comma?
    ;

ast_arrayElement                      // ECMAScript 6: Spread Operator
    : expression_single
    | spreadElement
    ;

spreadElement:
     Ellipsis argument=expression_single;

objectExpression
    : OpenBrace (properties=objectProperties_)? Comma? CloseBrace
    ;

objectProperties_:
    e=ast_objectProperty (Comma e=ast_objectProperty)*;

ast_objectProperty:
   propertyAssignment | spreadElement;

// MODIFIED
propertyAssignment
    : key=expression_propName ':' value=expression_single                # PropertyAssignment_assign
    | <astAssign=computed> '[' key=expression_single CloseBracket ':' value=expression_single # PropertyAssignment_computed
//    | getAccessor                                             # PropertyAssignment_getter
//    | setAccessor                                             # PropertyAssignment_setter
//    | generatorMethod                                         # PropertyAssignment_method
    | key=identifier_ex                                     # PropertyAssignment_shorthand
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


arguments_
    : OpenParen (e=expression_single (Comma e=expression_single)* Comma?)? CloseParen
    ;

sequenceExpression
    : expressions=singleExpressions_
    ;

singleExpressions_:
    e=expression_single (Comma e=expression_single)*;

assignmentExpression_init
   : left=identifier operator='=' right=expression_single
   ;

expression_forInit
   : expressions=initExpressions_  # SequenceExpression_init
   | kind=varModifier_ declarators=variableDeclarators_ # VariableDeclaration_init
   ;

initExpressions_
options{
   elementAstNodeName=Expression;
}:
    e=assignmentExpression_init (Comma e=assignmentExpression_init) *;

//functionExpressionDeclaration
//    : Function Identifier OpenParen parameterList? CloseParen typeAnnotation? OpenBrace functionBody CloseBrace
//    ;

expression_single
    :
  //       xplNodeExpression {this.supportXplExpr()}?               # XplNodeExpression_expr
    //functionExpressionDeclaration                                          # FunctionExpression
     arrowFunctionExpression                                               # ArrowFunctionExpression_expr   // ECMAScript 6
    | <astAssign=computed> object=expression_single (optional=OptionalDot)? '[' property=expression_single CloseBracket     #   MemberExpression_index
    | object=expression_single optional=(OptionalDot|'.') property=identifier_ex               #  MemberExpression_dot
//    | Class Identifier? classTail                                            # ClassExpression
    | <astAssign='optional:false'>expr=expression_single {this.notLineTerminator()}? optional='!' # ChainExpression
    | New callee=parameterizedTypeNode arguments=arguments_?                         # NewExpression
    | callee=expression_single (optional=OptionalDot)? arguments=arguments_                         # CallExpression
    | argument=expression_single {this.notLineTerminator()}? operator='++'                      # UpdateExpression//_postInc
    | argument=expression_single {this.notLineTerminator()}? operator='--'                      # UpdateExpression//_postDec
//    | Delete memberExpression   # DeleteExpression
    | Typeof argument=expression_single                                                # TypeOfExpression
//    | Void singleExpression                                                  # VoidExpression
    | operator='++' argument=expression_single                                  # UpdateExpression//_preInc
    | operator='--' argument=expression_single                                   # UpdateExpression//_preDec
    | operator='+' argument=expression_single                                    # UnaryExpression//_plus
    | operator='-' argument=expression_single                                    # UnaryExpression//_minus
    | operator='~' argument=expression_single                                    # UnaryExpression//_bitNot
    | operator='!' argument=expression_single                                    # UnaryExpression//_not
    | left=expression_single operator=('*' | '/' | '%') right=expression_single                    # BinaryExpression//_multiplicative
    | left=expression_single operator=('+' | '-') right=expression_single                          # BinaryExpression//_additive
    | left=expression_single operator='??' right=expression_single                                # BinaryExpression//_coalesce
    | left=expression_single operator=('<<' | '>>' | '>>>') right=expression_single                # BinaryExpression//_bitShift
    | left=expression_single operator=('<' | '>' | '<=' | '>=') right=expression_single            # BinaryExpression//_relational
    | value=expression_single Instanceof refType=namedTypeNode                           # InstanceOfExpression
    | left=expression_single In right=expression_single                                   # InExpression
    | left=expression_single operator=('==' | '!=' | '===' | '!==') right=expression_single        # BinaryExpression //_equality
    | left=expression_single operator='&' right=expression_single                                  # BinaryExpression//_bitAnd
    | left=expression_single operator='^' right=expression_single                                  # BinaryExpression//_bitXOr
    | left=expression_single operator='|' right=expression_single                                  # BinaryExpression//_bitOr
    | left=expression_single operator=('&&' | AndLiteral) right=expression_single                  # BinaryExpression//_logicalAnd
    | left=expression_single operator=('||' | OrLiteral)  right=expression_single                  # BinaryExpression//_logicalOr
    | <astAssign=ternaryExpr>test=expression_single '?' consequent=expression_single ':' alternate=expression_single            # IfStatement_expr
//    |  singleExpression '=' singleExpression                                  # AssignmentExpression
//    | singleExpression assignmentOperator singleExpression                   # AssignmentOperatorExpression
    | id=identifier {this.notLineTerminator()}? value=templateStringLiteral           # TemplateStringExpression  // ECMAScript 6
//    | iteratorBlock                                                          # IteratorsExpression // ECMAScript 6
   // | generatorBlock                                                         # GeneratorsExpression // ECMAScript 6
  //  | generatorFunctionDeclaration                                           # GeneratorsFunctionExpression // ECMAScript 6
  //  | yieldStatement                                                         # YieldExpression // ECMAScript 6
    | This                                                                   # ThisExpression
    | literal                                                                # Literal_expr
    | Super                                                                  # SuperExpression
    | identifier                                                       # Identifier_expr
    | arrayExpression                                                           # ArrayExpression_expr
    | objectExpression                                                          # ObjectExpression_expr
    | OpenParen expr=expression_single CloseParen                                # BraceExpression
    | CpExprStart expr=expression_single CloseBrace {this.supportCpExpr()}?                  # MacroExpression
   //  | typeArguments expressionSequence?                                      # GenericTypes
    | value=expression_single As asType=namedTypeNode                         # CastExpression
    ;

templateStringLiteral: value=TemplateStringLiteral;
//asExpression
//    : namedType
//    ;

//xplNodeExpression
//   :  XplExprStart tagName=XName attrs=xplAttrs_? XplExprEnd
//   ;
//
//xplAttrs_:
//    OpenParen (e=xplAttrExpression (Comma e=xplAttrExpression)* Comma?)? CloseParen;
//
//xplAttrExpression
//  : name=XName XplAssign value=expression_single
//  ;

//xplAttrName:
//  XName;// | StringLiteral;

arrowFunctionExpression
    :(OpenParen params=parameterList_? CloseParen returnType=namedTypeNode_annotation?) Arrow body=expression_functionBody # ArrowFunctionExpression_full
    | params_single=parameterDeclaration_simple Arrow body=expression_functionBody # ArrowFunctionExpression_single
//    : Async? arrowFunctionParameters typeAnnotation? '=>' arrowFunctionBody
    ;

parameterDeclaration_simple:
   name=identifier;

expression_functionBody
    : expression_single
    | blockStatement
    ;

memberExpression:
    <astAssign=computed> object=expression_single '[' property=expression_single CloseBracket     #   MemberExpression_index2
    | object=expression_single '.' property=identifier_ex               #  MemberExpression_dot2
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

eos__
    : SemiColon
    | EOF
    | {this.lineTerminatorAhead()}?
    | {this.closeBrace()}?
    ;

//stringExprSequence
//    : SE_String stringExprSequence
//    | SE_ExprStart statementList SE_ExprEnd stringExprSequence*
//    ;