/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.xlang.ast.ArrayBinding;
import io.nop.xlang.ast.ArrayElementBinding;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.AwaitExpression;
import io.nop.xlang.ast.BetweenOpExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.BlockStatement;
import io.nop.xlang.ast.BraceExpression;
import io.nop.xlang.ast.BreakStatement;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.CastExpression;
import io.nop.xlang.ast.CatchClause;
import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.CollectOutputExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.ConcatExpression;
import io.nop.xlang.ast.ContinueStatement;
import io.nop.xlang.ast.DeleteStatement;
import io.nop.xlang.ast.DoWhileStatement;
import io.nop.xlang.ast.EmptyStatement;
import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.ExpressionStatement;
import io.nop.xlang.ast.ForInStatement;
import io.nop.xlang.ast.ForOfStatement;
import io.nop.xlang.ast.ForStatement;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.GenNodeAttrExpression;
import io.nop.xlang.ast.GenNodeExpression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.ImportDefaultSpecifier;
import io.nop.xlang.ast.ImportNamespaceSpecifier;
import io.nop.xlang.ast.InExpression;
import io.nop.xlang.ast.InstanceOfExpression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.NewExpression;
import io.nop.xlang.ast.ObjectBinding;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.PropertyBinding;
import io.nop.xlang.ast.RegExpLiteral;
import io.nop.xlang.ast.RestBinding;
import io.nop.xlang.ast.ReturnStatement;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.SpreadElement;
import io.nop.xlang.ast.SuperExpression;
import io.nop.xlang.ast.SwitchCase;
import io.nop.xlang.ast.TemplateStringExpression;
import io.nop.xlang.ast.TemplateStringLiteral;
import io.nop.xlang.ast.TextOutputExpression;
import io.nop.xlang.ast.ThisExpression;
import io.nop.xlang.ast.ThrowStatement;
import io.nop.xlang.ast.TryStatement;
import io.nop.xlang.ast.TypeOfExpression;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.UpdateExpression;
import io.nop.xlang.ast.UsingStatement;
import io.nop.xlang.ast.VariableDeclaration;
import io.nop.xlang.ast.VariableDeclarator;
import io.nop.xlang.ast.WhileStatement;
import io.nop.xlang.ast.XLangASTProcessor;

public class TypeInferenceProcessor extends XLangASTProcessor<ReturnTypeInfo, TypeInferenceState> {
    @Override
    public ReturnTypeInfo processIfStatement(IfStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getTest(), context);
        TypeInferenceState s1 = context.newChild(); // true Branch

        // inferInstance(node.getTest(), s1);
        ReturnTypeInfo r1 = processAST(node.getConsequent(), s1);

        if (node.getAlternate() != null) {
            TypeInferenceState s2 = context.newChild();
            ReturnTypeInfo r2 = processAST(node.getAlternate(), s2);
            r1 = union(r1, r2);
        } else {
            r1 = union(r1, null);
        }

        return r1;
    }

    ReturnTypeInfo union(ReturnTypeInfo r1, ReturnTypeInfo r2) {
        if (r1 == null && r2 == null)
            return null;

        if (r1 == null) {
            r2.setOtherBranchNoReturn(true);
            return r2;
        }

        if (r2 == null) {
            r1.setOtherBranchNoReturn(true);
            return r1;
        }

        return null;
    }

    @Override
    public ReturnTypeInfo processLogicalExpression(LogicalExpression node, TypeInferenceState context) {
        ReturnTypeInfo r1 = processAST(node.getLeft(), context);
        ReturnTypeInfo r2 = processAST(node.getRight(), context.newChild());
        return union(r1, r2);
    }

    @Override
    public ReturnTypeInfo processProgram(Program node, TypeInferenceState context) {
        return super.processProgram(node, context);
    }

    @Override
    public ReturnTypeInfo processIdentifier(Identifier node, TypeInferenceState context) {
        return super.processIdentifier(node, context);
    }

    @Override
    public ReturnTypeInfo processLiteral(Literal node, TypeInferenceState context) {
        return super.processLiteral(node, context);
    }

    @Override
    public ReturnTypeInfo processTemplateStringLiteral(TemplateStringLiteral node, TypeInferenceState context) {
        return super.processTemplateStringLiteral(node, context);
    }

    @Override
    public ReturnTypeInfo processRegExpLiteral(RegExpLiteral node, TypeInferenceState context) {
        return super.processRegExpLiteral(node, context);
    }

    @Override
    public ReturnTypeInfo processBlockStatement(BlockStatement node, TypeInferenceState context) {
        return super.processBlockStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processEmptyStatement(EmptyStatement node, TypeInferenceState context) {
        return super.processEmptyStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processReturnStatement(ReturnStatement node, TypeInferenceState context) {
        return super.processReturnStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processBreakStatement(BreakStatement node, TypeInferenceState context) {
        return super.processBreakStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processContinueStatement(ContinueStatement node, TypeInferenceState context) {
        return super.processContinueStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processSwitchCase(SwitchCase node, TypeInferenceState context) {
        return super.processSwitchCase(node, context);
    }

    @Override
    public ReturnTypeInfo processThrowStatement(ThrowStatement node, TypeInferenceState context) {
        return super.processThrowStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processTryStatement(TryStatement node, TypeInferenceState context) {
        return super.processTryStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processCatchClause(CatchClause node, TypeInferenceState context) {
        return super.processCatchClause(node, context);
    }

    @Override
    public ReturnTypeInfo processWhileStatement(WhileStatement node, TypeInferenceState context) {
        return super.processWhileStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processDoWhileStatement(DoWhileStatement node, TypeInferenceState context) {
        return super.processDoWhileStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processVariableDeclarator(VariableDeclarator node, TypeInferenceState context) {
        return super.processVariableDeclarator(node, context);
    }

    @Override
    public ReturnTypeInfo processVariableDeclaration(VariableDeclaration node, TypeInferenceState context) {
        return super.processVariableDeclaration(node, context);
    }

    @Override
    public ReturnTypeInfo processForStatement(ForStatement node, TypeInferenceState context) {
        return super.processForStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processForOfStatement(ForOfStatement node, TypeInferenceState context) {
        return super.processForOfStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processForInStatement(ForInStatement node, TypeInferenceState context) {
        return super.processForInStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processDeleteStatement(DeleteStatement node, TypeInferenceState context) {
        return super.processDeleteStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processChainExpression(ChainExpression node, TypeInferenceState context) {
        return super.processChainExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processThisExpression(ThisExpression node, TypeInferenceState context) {
        return super.processThisExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processSuperExpression(SuperExpression node, TypeInferenceState context) {
        return super.processSuperExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processTemplateStringExpression(TemplateStringExpression node, TypeInferenceState context) {
        return super.processTemplateStringExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processArrayExpression(ArrayExpression node, TypeInferenceState context) {
        return super.processArrayExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processObjectExpression(ObjectExpression node, TypeInferenceState context) {
        return super.processObjectExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processPropertyAssignment(PropertyAssignment node, TypeInferenceState context) {
        return super.processPropertyAssignment(node, context);
    }

    @Override
    public ReturnTypeInfo processParameterDeclaration(ParameterDeclaration node, TypeInferenceState context) {
        return super.processParameterDeclaration(node, context);
    }

    @Override
    public ReturnTypeInfo processFunctionDeclaration(FunctionDeclaration node, TypeInferenceState context) {
        return super.processFunctionDeclaration(node, context);
    }

    @Override
    public ReturnTypeInfo processArrowFunctionExpression(ArrowFunctionExpression node, TypeInferenceState context) {
        return super.processArrowFunctionExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processUnaryExpression(UnaryExpression node, TypeInferenceState context) {
        return super.processUnaryExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processUpdateExpression(UpdateExpression node, TypeInferenceState context) {
        return super.processUpdateExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processBinaryExpression(BinaryExpression node, TypeInferenceState context) {
        return super.processBinaryExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processInExpression(InExpression node, TypeInferenceState context) {
        return super.processInExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processExpressionStatement(ExpressionStatement node, TypeInferenceState context) {
        return super.processExpressionStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processAssignmentExpression(AssignmentExpression node, TypeInferenceState context) {
        return super.processAssignmentExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processMemberExpression(MemberExpression node, TypeInferenceState context) {
        return super.processMemberExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processCallExpression(CallExpression node, TypeInferenceState context) {
        return super.processCallExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processNewExpression(NewExpression node, TypeInferenceState context) {
        return super.processNewExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processSpreadElement(SpreadElement node, TypeInferenceState context) {
        return super.processSpreadElement(node, context);
    }

    @Override
    public ReturnTypeInfo processSequenceExpression(SequenceExpression node, TypeInferenceState context) {
        return super.processSequenceExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processConcatExpression(ConcatExpression node, TypeInferenceState context) {
        return super.processConcatExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processBraceExpression(BraceExpression node, TypeInferenceState context) {
        return super.processBraceExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processObjectBinding(ObjectBinding node, TypeInferenceState context) {
        return super.processObjectBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processPropertyBinding(PropertyBinding node, TypeInferenceState context) {
        return super.processPropertyBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processRestBinding(RestBinding node, TypeInferenceState context) {
        return super.processRestBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processArrayBinding(ArrayBinding node, TypeInferenceState context) {
        return super.processArrayBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processArrayElementBinding(ArrayElementBinding node, TypeInferenceState context) {
        return super.processArrayElementBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processImportDefaultSpecifier(ImportDefaultSpecifier node, TypeInferenceState context) {
        return super.processImportDefaultSpecifier(node, context);
    }

    @Override
    public ReturnTypeInfo processImportNamespaceSpecifier(ImportNamespaceSpecifier node, TypeInferenceState context) {
        return super.processImportNamespaceSpecifier(node, context);
    }

    @Override
    public ReturnTypeInfo processAwaitExpression(AwaitExpression node, TypeInferenceState context) {
        return super.processAwaitExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processUsingStatement(UsingStatement node, TypeInferenceState context) {
        return super.processUsingStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processTextOutputExpression(TextOutputExpression node, TypeInferenceState context) {
        return super.processTextOutputExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processEscapeOutputExpression(EscapeOutputExpression node, TypeInferenceState context) {
        return super.processEscapeOutputExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processCollectOutputExpression(CollectOutputExpression node, TypeInferenceState context) {
        return super.processCollectOutputExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processCompareOpExpression(CompareOpExpression node, TypeInferenceState context) {
        return super.processCompareOpExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processAssertOpExpression(AssertOpExpression node, TypeInferenceState context) {
        return super.processAssertOpExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processBetweenOpExpression(BetweenOpExpression node, TypeInferenceState context) {
        return super.processBetweenOpExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processGenNodeExpression(GenNodeExpression node, TypeInferenceState context) {
        return super.processGenNodeExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processGenNodeAttrExpression(GenNodeAttrExpression node, TypeInferenceState context) {
        return super.processGenNodeAttrExpression(node, context);
    }


    @Override
    public ReturnTypeInfo processTypeOfExpression(TypeOfExpression node, TypeInferenceState context) {
        return super.processTypeOfExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processInstanceOfExpression(InstanceOfExpression node, TypeInferenceState context) {
        return super.processInstanceOfExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processCastExpression(CastExpression node, TypeInferenceState context) {
        return super.processCastExpression(node, context);
    }

}