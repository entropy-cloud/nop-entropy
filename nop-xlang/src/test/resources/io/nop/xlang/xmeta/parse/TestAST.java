/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.entropy.expr.ast;

import io.entropy.expr.ast.XLangAST.XLangASTNode;
import io.entropy.model.struct.tool.ASTDefinition;
import io.nop.api.core.annotations.meta.PropMeta;

import java.util.List;
import java.util.Set;

@ASTDefinition(baseClass = XLangASTNode.class)
public class TestAST {
    enum PropertyKind {
        init,
        get,
        set
    }

    enum VariableKind {
        CONST,
        LET,
    }

    enum XLangClassKind {

    }

    enum XLangOperator {
    }

    enum XLangOutputMode {

    }

    abstract class XLangASTNode {

    }

    abstract class Expression {
    }

    abstract class Statement extends Expression {
    }

    interface IdentifierOrPattern {

    }

    abstract class ModuleDeclaration extends Statement {

    }

    abstract class Declaration extends Statement {

    }

    class Program extends Expression {
        String sourceType; //: "script" | "module";
        @PropMeta(mandatory = true, depends = {"a", "b"})
        List<XLangASTNode> body; //: [ Statement | ModuleDeclaration ];
    }

    class Identifier extends Expression implements IdentifierOrPattern {
        String name;
    }

    class Literal extends Expression implements MetaValue {
        Object value;
    }

    class TemplateStringLiteral extends Literal {

    }

    class RegExpLiteral extends Literal {
        String pattern;
        String flags;
    }

    class BlockStatement extends Statement {
        List<Expression> body;
    }

    class EmptyStatement extends Statement {

    }

    class ReturnStatement extends Statement {
        Expression argument;
    }

    class BreakStatement extends Statement {
    }


    class ContinueStatement extends Statement {
    }

    class IfStatement extends Statement {
        Expression test;
        Expression consequent;
        Expression alternate;
    }

    class SwitchStatement extends Statement {
        Expression discriminant;
        List<SwitchCase> cases;
        Expression defaultCase;
    }

    class SwitchCase {
        Expression test;
        Expression consequent;
    }

    class ThrowStatement extends Statement {
        Expression argument;
    }

    class TryStatement extends Statement {
        Expression block;
        CatchClause catchHandler;
        Expression finalizer;
    }

    class CatchClause {
        Identifier name;
        NamedTypeNode varType;
        Expression body;
    }

    class WhileStatement extends Statement {
        Expression test;
        Expression body;
    }

    class DoWhileStatement extends Statement {
        Expression body;
        Expression test;
    }

    class VariableDeclarator {
        IdentifierOrPattern id;
        NamedTypeNode varType;
        Expression init;
    }

    class VariableDeclaration extends Declaration {
        VariableKind kind; // kind: "var";
        List<VariableDeclarator> declarators;
    }

    class ForStatement extends Statement {
        Expression init; // init:VariableDeclaration |Expression |null;
        Expression test;
        Expression update;
        Expression body;
    }

    class ForOfStatement extends Statement {
        Identifier varStatus;
        Expression left; //: VariableDeclaration |  Pattern;
        Expression right;
        Expression body;
    }

    class ForRangeStatement extends Statement {
        Identifier var;
        Identifier varStatus;
        Expression begin;
        Expression end;
        Expression step;
        Expression body;
    }

    class ForInStatement extends Statement {
        Expression left;
        Expression right;
        Expression body;
    }

//    class WithStatement extends Statement {
//        Expression object;
//        Expression body;
//    }

    class DeleteStatement extends Statement {
        Expression argument;
    }

    abstract class OptionalExpression extends Expression {
        boolean optional;
    }

    class ChainExpression extends OptionalExpression {
        Expression value;
    }

    class ThisExpression extends Expression {
    }

    class SuperExpression extends Expression {

    }

    class TemplateStringExpression extends Expression {
        Identifier id;
        TemplateStringLiteral value;
    }

    class ArrayExpression extends Expression {
        // spread element is not expression
        List<XLangASTNode> elements;
    }

    class ObjectExpression extends Expression {
        List<XLangASTNode> properties;
    }

    class PropertyAssignment extends Expression {
        Expression key; //: Literal | Identifier | Expression;
        Expression value; //: Expression;
        PropertyKind kind; //"init" | "get" | "set";
        boolean method;
        boolean shorthand;
        boolean computed;
    }

    class ParameterDeclaration {
        Decorators decorators;
        IdentifierOrPattern name;
        NamedTypeNode type;
        Expression initializer;
        boolean implicit;
    }

    class FunctionDeclaration extends DecoratedDeclaration {
        @PropMeta(mandatory = true)
        Identifier name;

        @PropMeta(mandatory = true)
        List<ParameterDeclaration> params; //:[Pattern ];

        NamedTypeNode returnType;
        boolean resultOptional;

        @PropMeta(mandatory = true)
        Expression body;
        int modifiers;
    }

    class ArrowFunctionExpression extends Expression {
        //Identifier id;
        List<ParameterDeclaration> params; //:[Pattern ];
        NamedTypeNode returnType;
        Expression body; // : FunctionBody | Expression;
        boolean expression;
    }

    /*
    enum UnaryOperator {
    "-" | "+" | "!" | "~" | "typeof" | "void" | "delete"
    }*/

    class UnaryExpression extends Expression {
        //type: "UnaryExpression";
        XLangOperator operator; //: UnaryOperator;
        //prefix: boolean;
        Expression argument; //: Expression;
    }

    class UpdateExpression extends Expression {
        //type: "UpdateExpression";
        XLangOperator operator;
        Expression argument;
        boolean prefix;
    }

    /*
    enum BinaryOperator {
    "==" | "!=" | "===" | "!=="
         | "<" | "<=" | ">" | ">="
         | "<<" | ">>" | ">>>"
         | "+" | "-" | "*" | "/" | "%"
         | "|" | "^" | "&" | "in"
         | "instanceof"
     }

     enum LogicalOperator {
        "||" | "&&" | "??"
     }

      Nullish Coalescing
     */
    class BinaryExpression extends Expression {
        //type: "BinaryExpression";
        XLangOperator operator; //: BinaryOperator;
        Expression left;
        Expression right;
    }

    class InExpression extends Expression {
        Expression left;
        Expression right;
    }

    class ExpressionStatement extends Statement {
        Expression expression;
    }

    /**
     * enum AssignmentOperator {
     * "=" | "+=" | "-=" | "*=" | "/=" | "%="
     * | "<<=" | ">>=" | ">>>="
     * | "|=" | "^=" | "&="
     * }
     */
    class AssignmentExpression extends Expression {
        //type: "AssignmentExpression";
        XLangOperator operator; //: AssignmentOperator;
        Expression left;
        Expression right;
    }

    class LogicalExpression extends Expression {
        //type: "LogicalExpression";
        XLangOperator operator;
        Expression left;
        Expression right;
    }

    class MemberExpression extends OptionalExpression {
        //type: "MemberExpression";
        Expression object;
        Expression property;
        boolean computed; // computed 为true 对应于a[b], 而为false时，对应a.b, b为Identifier
    }

    class CallExpression extends Expression {
        //type: "CallExpression";
        Expression callee;
        List<Expression> arguments;
    }

    class NewExpression extends Expression {
        //type: "NewExpression";
        NamedTypeNode callee;
        List<Expression> arguments;
    }

    class SpreadElement {
        Expression argument;
    }
//
//    // a ternary ?/: expression.
//    class ConditionalExpression extends Expression {
//        Expression test;
//        Expression alternate;
//        Expression consequent;
//    }

    // for(x=1,y=2;;)这种通过逗号分隔的表达式
    class SequenceExpression extends Expression {
        //type: "SequenceExpression";
        List<Expression> expressions;
    }

    class ConcatExpression extends Expression {
        //type: "SequenceExpression";
        List<Expression> expressions;
    }

    class BraceExpression extends Expression {
        Expression expr;
    }

    class ObjectBinding implements IdentifierOrPattern {
        List<PropertyBinding> properties;
        RestBinding restBinding;
    }

    class PropertyBinding {
        String propName;
        Identifier identifier;
        Expression initializer;
    }

    class RestBinding {
        Identifier identifier;
        Expression initializer;
    }

    class ArrayBinding implements IdentifierOrPattern {
        List<ArrayElementBinding> elements;
        RestBinding restBinding;
    }

    class ArrayElementBinding {
        Identifier identifier;
        Expression initializer;
    }

    abstract class ModuleSpecifier {

    }

    // export const foo = 1;
    class ExportDeclaration extends Declaration {
        Declaration declaration;
    }

    // export {foo} from "mod";
    class ExportNamedDeclaration extends ModuleDeclaration {
        List<ExportSpecifier> specifiers;
        Literal source;
    }

    // export * from "mod"
    class ExportAllDeclaration extends ModuleDeclaration {
        Literal source;
    }

    // export {bar as foo}
    class ExportSpecifier extends ModuleSpecifier {
        Identifier local;
        Identifier exported;
    }

    class ImportDeclaration extends ModuleDeclaration {
        //type: "ImportDeclaration";
        List<ModuleSpecifier> specifiers; // [ ImportSpecifier | ImportDefaultSpecifier | ImportNamespaceSpecifier ];
        Literal source;
    }

    // import java.util.List as JavaList;
    // import "/nop/core/c.xlib";
    class ImportAsDeclaration extends ModuleDeclaration {
        XLangASTNode source;
        Identifier local;
    }

    //import {foo as bar} from "mod"
    class ImportSpecifier extends ModuleSpecifier {
        //type: "ImportSpecifier";
        Identifier local;
        Identifier imported;
    }

    // import foo from "mod.js"
    class ImportDefaultSpecifier extends ModuleSpecifier {
        //type: "ImportDefaultSpecifier";
        Identifier local;
    }

    // import * as foo from "mod.js"
    class ImportNamespaceSpecifier extends ModuleSpecifier {
        //type: "ImportNamespaceSpecifier";
        Identifier local;
    }

    // [...obj]
    class AwaitExpression extends Expression {
        //type: "AwaitExpression";
        Expression argument;
    }

    class Decorators {
        List<Decorator> decorators;
    }

    class QualifiedName implements MetaValue {
        String name;
    }

    class Decorator {
        QualifiedName name; // CallExpression/Identifier
        MetaObject value;
    }

    // fixed value at compile time
    interface MetaValue {

    }

    class MetaObject implements MetaValue {
        List<MetaProperty> properties;
    }

    class MetaProperty {
        Identifier name;
        MetaValue value;
    }

    class MetaArray implements MetaValue {
        List<MetaValue> elements;
    }

    class UsingStatement extends Statement {
        VariableDeclaration vars; //: VariableDeclaration |  Pattern;
        Expression body;
    }

    class XplNodeExpression extends Expression {
        String tagName;
        List<XplAttrExpression> attrs;
    }

    class XplAttrExpression {
        String name;
        Expression value;
    }

    class MacroExpression extends Expression {
        Expression expr;
    }

    // 忽略表达式的返回值，通过scope.output输出
    abstract class OutputExpression extends Expression {

    }

    enum XLangEscapeMode {

    }

    class TextOutputExpression extends OutputExpression {
        String text;
    }

    class EscapeOutputExpression extends OutputExpression {
        XLangEscapeMode escapeMode;
        Expression text;
    }

    class CollectOutputExpression extends Expression {
        XLangOutputMode outputMode;
        Expression body;
    }

    abstract class FilterOpExpression extends Expression {
        String op;
        String errorCode;
        String label;
    }

    class CompareOpExpression extends FilterOpExpression {
        Expression left;
        Expression right;
    }

    class AssertOpExpression extends FilterOpExpression {
        Expression value;
    }

    class BetweenOpExpression extends FilterOpExpression {
        Expression value;
        Expression min;
        Expression max;
        boolean excludeMin;
        boolean excludeMax;
    }

    class GenNodeExpression extends OutputExpression {
        String tagName;
        Expression extAttrs;
        List<GenNodeAttrExpression> attrs;
        Expression body;
        // textNode仅body属性有效
        boolean textNode;
    }

    class GenNodeAttrExpression extends OutputExpression {
        String name;
        Expression value;
    }

    class OutputXmlAttrExpression extends OutputExpression {
        String name;
        Expression value;
    }

    class OutputXmlExtAttrsExpression extends OutputExpression {
        Set<String> excludeNames;
        Expression extAttrs;
    }

    class SwitchExpression extends Expression {
        Expression discriminant;

        List<SwitchCaseExpression> cases;
        Expression defaultCase;
    }

    class SwitchCaseExpression extends Expression {
        Expression caseValue;
        Expression consequence;
    }

    class TypeOfExpression extends Expression {
        Expression argument;
    }

    class InstanceOfExpression extends Expression {
        Expression value;
        NamedTypeNode refType;
    }

    class CastExpression extends Expression {
        Expression value;
        NamedTypeNode asType;
    }

    abstract class TypeNode {
        boolean notNull;
    }

    abstract class NamedTypeNode extends TypeNode {
    }

    class ArrayTypeNode extends NamedTypeNode {
        NamedTypeNode componentType;
    }

    class ParameterizedTypeNode extends NamedTypeNode {
        String typeName;
        List<NamedTypeNode> typeArgs;
    }

    class TypeNameNode extends NamedTypeNode {
        String typeName;
    }

    abstract class StructuredTypeDef extends TypeNode {

    }

    class UnionTypeDef extends StructuredTypeDef {
        List<NamedTypeNode> types;
    }

    class IntersectionTypeDef extends StructuredTypeDef {
        List<NamedTypeNode> types;
    }

    class ObjectTypeDef extends StructuredTypeDef {
        List<PropertyTypeDef> types;
    }

    // [key:string]: number, propA?: MyType
    class PropertyTypeDef extends StructuredTypeDef {
        String name;
        TypeNode valueType;
        boolean anyName;  // true for indexSignature, eg. [key:string]:type
        boolean readonly;
        boolean optional;
    }

    class TupleTypeDef extends StructuredTypeDef {
        List<TypeNode> types;
    }

    class TypeParameterNode {
        Identifier name;
        NamedTypeNode upperBound;
        NamedTypeNode lowerBound;
    }

    class TypeAliasDeclaration extends Declaration {
        Identifier typeName;
        List<TypeParameterNode> typeParams;
        TypeNode defType;
    }

    class FunctionTypeDef extends StructuredTypeDef {
        List<TypeParameterNode> typeParams;

        List<FunctionArgTypeDef> args;

        boolean varArgs;

        NamedTypeNode returnType;
    }

    class FunctionArgTypeDef extends StructuredTypeDef {
        Identifier argName;
        NamedTypeNode argType;
        boolean optional;
    }

    class EnumDeclaration extends Declaration {
        Identifier name;
        List<EnumMember> members;
    }

    class EnumMember {
        Identifier name;
        Literal value;
    }

    abstract class DecoratedDeclaration extends Declaration {
        Decorators decorators;
    }

    class ClassDefinition extends DecoratedDeclaration {
        Identifier name;
        XLangClassKind classKind;
        List<TypeParameterNode> typeParams;

        ParameterizedTypeNode extendsType;
        List<ParameterizedTypeNode> implementTypes;

        List<FieldDeclaration> fields;

        List<FunctionDeclaration> methods;

        List<ClassDefinition> classDefinitions;
    }

    class FieldDeclaration extends DecoratedDeclaration {
        Identifier name;
        NamedTypeNode type;
        boolean optional;
        Expression initializer;
        int modifiers;
    }
}