parser grammar XLangTypeSystem;

import XLangCommon;

options {
    tokenVocab=XLangBaseLexer;
}

// TypeScript SPart
// A.1 Types

// 定义type时指定的泛型参数列表 type A<T> = {}
typeParameters_
    : '<' (e=typeParameterNode (',' e=typeParameterNode)*)? '>'
    ;

typeParameterNode
    : name=identifier ('extends' upperBound=namedTypeNode | 'super' lowerBound=namedTypeNode) ?
//    | typeParameters
    ;

// new A<typeArgument>(argument)调用时指定的参数
typeArguments_
    : '<' (e=namedTypeNode (',' e=namedTypeNode)*)? '>'
    ;


//typeArgument
//    : type_
//    ;

structuredTypeDef
    : typeNode_unionOrIntersection
    | objectTypeDef
    | tupleTypeDef
    | namedTypeNode
    | functionTypeDef
//    | constructorType
//    | typeGeneric
  //  | StringLiteral
    ;

// java不支持union类型
typeNode_unionOrIntersection
    : types= intersectionTypeDef_ #IntersectionTypeDef
    | types= unionTypeDef_ #UnionTypeDef
    ;

intersectionTypeDef_:
    e=namedTypeNode ('&' e=namedTypeNode)+;

unionTypeDef_:
    e=namedTypeNode ('|' e=namedTypeNode)+;

//unionOrIntersectionOrPrimaryType
//    : unionOrIntersectionOrPrimaryType '&' unionOrIntersectionOrPrimaryType #Intersection
//    | unionOrIntersectionOrPrimaryType '|' unionOrIntersectionOrPrimaryType #Union
//    | primaryType #Primary
//    ;

// 简化类型系统的定义，不使用括号
//primaryType
//     :
//     //predefinedType                                #PredefinedPrimType
//     //| typeReference                                 #ReferencePrimType
//     objectType                                //    #ObjectPrimType
//     //| primaryType {notLineTerminator()}? '[' CloseBracket    #ArrayPrimType
//     | '[' tupleElementTypes CloseBracket                //     #TuplePrimType
//  //   | typeQuery                                     #QueryPrimType
// //    | This                                          #ThisPrimType
// //    | typeReference Is primaryType                  #RedefinitionOfType
//     ;

tupleTypeDef: '[' types=tupleTypeElements_ CloseBracket;

tupleTypeElements_
options{
  elementAstNodeName=TypeNode;
}:
    e=structuredTypeDef (',' e=structuredTypeDef)*;

//tupleElementTypes
//    : structuredType (',' structuredType)*
//    ;

namedTypeNode
    : typeNameNode_predefined   # TypeNameNode_predefined_named
    | typeName=qualifiedName_   # TypeNameNode_named
    | parameterizedTypeNode    # ParameterizedTypeNode_named
    | componentType=namedTypeNode {notLineTerminator()}? '[' CloseBracket  # ArrayTypeNode
    ;

//simpleType_:   namedTypeNode;

typeNameNode_predefined
options{
   astProp=typeName;
}
    : Any
    | Number
    | Boolean
    | String
    | Symbol
    | Void
    ;

parameterizedTypeNode
    : typeName=qualifiedName_ typeArgs=typeArguments_?
//    : typeName nestedTypeGeneric?
    ;

// nestedTypeGeneric
//     : typeIncludeGeneric
//     | typeGeneric
//     ;

// 去除 typeIncludeGeneric
// nestedTypeGeneric
//    : typeGeneric
//    ;

// I tried recursive include, but it's not working.
// typeGeneric
//    : '<' typeArgumentList typeGeneric?'>'
//    ;
//
// TODO: Fix recursive
//


// typeIncludeGeneric
//     :'<' typeArgumentList '<' typeArgumentList ('>' bindingPattern '>' | '>>')
//     ;

objectTypeDef
    : '{' types=objectTypeElements_ ','? '}';

objectTypeElements_:
      e=propertyTypeDef (',' e=propertyTypeDef)*;

//typeMember_
//    : propertyTypeDef
//    | callSignature
//    | constructSignature
//    | indexSignature
//    | methodSignature

//
//arrayType
//    : primaryType {notLineTerminator()}? '[' CloseBracket
//    ;
//
//tupleType
//    : '[' tupleElementTypes CloseBracket
//    ;

functionTypeDef
    : typeParams=typeParameters_? OpenBrace args=functionParameterTypes_? CloseBrace '=>' returnType=namedTypeNode
    ;

//constructorType
//    : 'new' typeParameters? OpenBrace parameterList? CloseBrace '=>' type_
//    ;

//typeQuery
//    : 'typeof' typeQueryExpression
//    ;
//
//typeQueryExpression
//    : Identifier
//    | (identifierName '.')+ identifierName
//    ;

propertyTypeDef
    : decorators? readonly=ReadOnly? name=propertyName_ optional='?'? valueType=structuredTypeDef_annotation?
    ;

// 标记类型时主要使用name，不做结构化类型标记。结构化类型定义只在类型定义时使用
namedTypeNode_annotation
    : ':' namedTypeNode
    ;

structuredTypeDef_annotation
    : ':' structuredTypeDef
    ;


functionParameterTypes_
    : e=functionArgTypeDef (',' e=functionArgTypeDef)*
    ;

// requiredParameterList
//     : requiredParameter (',' requiredParameter)*
//     ;

// parameter
//     : requiredParameter
//     | optionalParameter
//     ;

functionArgTypeDef
  :   argName=identifier (optional='?')? argType=namedTypeNode_annotation?
  ;

// optionalParameter
//     : decoratorList? ( accessibilityModifier? identifierOrPattern ('?' typeAnnotation? | typeAnnotation? initializer))
//     ;

//functionRestParameterType
//    : '...' Identifier typeAnnotation?
//    ;

// requiredParameter
//     : decoratorList? accessibilityModifier? identifierOrPattern typeAnnotation? initializer
//     ;

//accessibilityModifier
//    : Public
//    | Private
//    | Protected
//    ;

//identifierOrPattern
//    : identifierName
//    | bindingPattern
//    ;

//constructSignature
//    : 'new' typeParameters? OpenBrace parameterList? CloseBrace typeAnnotation?
//    ;

//indexSignature
//    : '[' Identifier ':' String CloseBracket structuredTypeAnnotation
//    ;
//
//methodSignature
//    : decorators? propertyName_ '?'? callSignature
//    ;
//
//callSignature
//    : typeParameters? OpenBrace functionParameterTypes? CloseBrace typeAnnotation?
//    ;


typeAliasDeclaration
    : decorators ? 'type' typeName=identifier typeParams=typeParameters_? '=' defType=structuredTypeDef SemiColon?
    ;

//constructorDeclaration
//    : accessibilityModifier? Constructor OpenBrace formalParameterList? CloseBrace ( ('{' functionBody '}') | SemiColon)?
//    ;

// A.5 Interface
//
//interfaceDeclaration
//    : Export? Declare? Interface Identifier typeParameters? interfaceExtendsClause? objectType SemiColon?
//    ;
//
//interfaceExtendsClause
//    : Extends classOrInterfaceTypeList
//    ;
//
//classOrInterfaceTypeList
//    : typeReference (',' typeReference)*
//    ;

// A.7 Interface

enumDeclaration
    : Enum name=identifier '{'members=enumMembers_ ','? '}'
    ;

enumMembers_:
       e=enumMember (',' e=enumMember)*;
//
//enumBody
//    : enumMemberList ','?
//    ;
//
//enumMemberList
//    : enumMember (',' enumMember)*
//    ;

enumMember
    : name=identifier ('=' value=literal)?
    ;

// A.8 Namespaces
//
//namespaceDeclaration
//    : Namespace namespaceName '{' statementList? '}'
//    ;


// Ext.2 Additions to 1.8: Decorators

decorators
    : decorators_=decoratorElements_ ;

decoratorElements_:
    e=decorator+;

decorator
    : '@' name=qualifiedName (OpenBrace  value=metaObject CloseBrace)?
    ;

metaObject
    : '{' (properties=metaObjectProperties_ ','? )? '}';

metaObjectProperties_:
    e=metaProperty (',' e=metaProperty)*;

metaProperty
   : name=identifier ':' value=ast_metaValue
   ;

metaArray
    : '[' (elements=metaArrayElements_)? ','? CloseBracket;

metaArrayElements_:
    e=ast_metaValue(',' e=ast_metaValue)*;

ast_metaValue
    : literal
    | metaObject
    | metaArray
    | qualifiedName
    ;
