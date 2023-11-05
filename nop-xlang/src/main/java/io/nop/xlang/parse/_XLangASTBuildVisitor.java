
package io.nop.xlang.parse;

import io.nop.xlang.parse.antlr.XLangParserBaseVisitor;
import io.nop.xlang.parse.antlr.XLangParser.*;
import io.nop.api.core.exceptions.NopException;  //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.util.SourceLocation; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.commons.util.CollectionHelper;//NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.commons.util.StringHelper;//NOPMD - suppressed UnusedImports - Auto Gen Code
import org.antlr.v4.runtime.tree.ParseTree;
import io.nop.antlr4.common.ParseTreeHelper;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.nop.xlang.ast.XLangASTNode;



// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public abstract class _XLangASTBuildVisitor extends XLangParserBaseVisitor<XLangASTNode>{

      public io.nop.xlang.ast.ArrayBinding visitArrayBinding_full(ArrayBinding_fullContext ctx){
          io.nop.xlang.ast.ArrayBinding ret = new io.nop.xlang.ast.ArrayBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.elements != null){
               ret.setElements((buildArrayElementBindings_(ctx.elements)));
            }else{
               ret.setElements(Collections.emptyList());
            }
            
            if(ctx.restBinding_ != null){
               ret.setRestBinding((visitRestBinding(ctx.restBinding_)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ArrayBinding visitArrayBinding_rest(ArrayBinding_restContext ctx){
          io.nop.xlang.ast.ArrayBinding ret = new io.nop.xlang.ast.ArrayBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.restBinding_ != null){
               ret.setRestBinding((visitRestBinding(ctx.restBinding_)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.ArrayExpression visitArrayExpression_expr(ArrayExpression_exprContext ctx){
           ArrayExpressionContext node = ctx.arrayExpression();
           return node == null ? null : visitArrayExpression(node);
        }
      
      public io.nop.xlang.ast.ArrayTypeNode visitArrayTypeNode(ArrayTypeNodeContext ctx){
          io.nop.xlang.ast.ArrayTypeNode ret = new io.nop.xlang.ast.ArrayTypeNode();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.componentType != null){
               ret.setComponentType((visitNamedTypeNode(ctx.componentType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.ArrowFunctionExpression visitArrowFunctionExpression_expr(ArrowFunctionExpression_exprContext ctx){
           ArrowFunctionExpressionContext node = ctx.arrowFunctionExpression();
           return node == null ? null : visitArrowFunctionExpression(node);
        }
      
      public io.nop.xlang.ast.ArrowFunctionExpression visitArrowFunctionExpression_full(ArrowFunctionExpression_fullContext ctx){
          io.nop.xlang.ast.ArrowFunctionExpression ret = new io.nop.xlang.ast.ArrowFunctionExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.params != null){
               ret.setParams((buildParameterList_(ctx.params)));
            }else{
               ret.setParams(Collections.emptyList());
            }
            
            if(ctx.returnType != null){
               ret.setReturnType((visitNamedTypeNode_annotation(ctx.returnType)));
            }
            if(ctx.body != null){
               ret.setBody((visitExpression_functionBody(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ArrowFunctionExpression visitArrowFunctionExpression_single(ArrowFunctionExpression_singleContext ctx){
          io.nop.xlang.ast.ArrowFunctionExpression ret = new io.nop.xlang.ast.ArrowFunctionExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.params_single != null){
               ret.setParams(io.nop.commons.util.CollectionHelper.safeSingletonList(visitParameterDeclaration_simple(ctx.params_single)));
            }
            if(ctx.body != null){
               ret.setBody((visitExpression_functionBody(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.BinaryExpression visitBinaryExpression(BinaryExpressionContext ctx){
          io.nop.xlang.ast.BinaryExpression ret = new io.nop.xlang.ast.BinaryExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitExpression_single(ctx.left)));
            }
            if(ctx.operator != null){
               ret.setOperator((BinaryExpression_operator(ctx.operator)));
            }
            if(ctx.right != null){
               ret.setRight((visitExpression_single(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.BraceExpression visitBraceExpression(BraceExpressionContext ctx){
          io.nop.xlang.ast.BraceExpression ret = new io.nop.xlang.ast.BraceExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitExpression_single(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.CallExpression visitCallExpression(CallExpressionContext ctx){
          io.nop.xlang.ast.CallExpression ret = new io.nop.xlang.ast.CallExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.callee != null){
               ret.setCallee((visitExpression_single(ctx.callee)));
            }
            if(ctx.optional != null){
               ret.setOptional((CallExpression_optional(ctx.optional)));
            }
            if(ctx.arguments != null){
               ret.setArguments((buildArguments_(ctx.arguments)));
            }else{
               ret.setArguments(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.CastExpression visitCastExpression(CastExpressionContext ctx){
          io.nop.xlang.ast.CastExpression ret = new io.nop.xlang.ast.CastExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((visitExpression_single(ctx.value)));
            }
            if(ctx.asType != null){
               ret.setAsType((visitNamedTypeNode(ctx.asType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ChainExpression visitChainExpression(ChainExpressionContext ctx){
          io.nop.xlang.ast.ChainExpression ret = new io.nop.xlang.ast.ChainExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
             ret.setOptional(false);
          
            if(ctx.expr != null){
               ret.setExpr((visitExpression_single(ctx.expr)));
            }
            if(ctx.optional != null){
               ret.setOptional((ChainExpression_optional(ctx.optional)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.DoWhileStatement visitDoWhileStatement(DoWhileStatementContext ctx){
          io.nop.xlang.ast.DoWhileStatement ret = new io.nop.xlang.ast.DoWhileStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.body != null){
               ret.setBody((visitStatement(ctx.body)));
            }
            if(ctx.test != null){
               ret.setTest((visitExpression_single(ctx.test)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ExportDeclaration visitExportDeclaration_const(ExportDeclaration_constContext ctx){
          io.nop.xlang.ast.ExportDeclaration ret = new io.nop.xlang.ast.ExportDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.declaration != null){
               ret.setDeclaration((visitVariableDeclaration_const(ctx.declaration)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ExportDeclaration visitExportDeclaration_func(ExportDeclaration_funcContext ctx){
          io.nop.xlang.ast.ExportDeclaration ret = new io.nop.xlang.ast.ExportDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.declaration != null){
               ret.setDeclaration((visitFunctionDeclaration(ctx.declaration)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ExportDeclaration visitExportDeclaration_type(ExportDeclaration_typeContext ctx){
          io.nop.xlang.ast.ExportDeclaration ret = new io.nop.xlang.ast.ExportDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.declaration != null){
               ret.setDeclaration((visitTypeAliasDeclaration(ctx.declaration)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ForInStatement visitForInStatement(ForInStatementContext ctx){
          io.nop.xlang.ast.ForInStatement ret = new io.nop.xlang.ast.ForInStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitExpression_iterationLeft(ctx.left)));
            }
            if(ctx.right != null){
               ret.setRight((visitExpression_single(ctx.right)));
            }
            if(ctx.body != null){
               ret.setBody((visitStatement(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ForOfStatement visitForOfStatement(ForOfStatementContext ctx){
          io.nop.xlang.ast.ForOfStatement ret = new io.nop.xlang.ast.ForOfStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitExpression_iterationLeft(ctx.left)));
            }
            if(ctx.right != null){
               ret.setRight((visitExpression_single(ctx.right)));
            }
            if(ctx.body != null){
               ret.setBody((visitStatement(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ForStatement visitForStatement(ForStatementContext ctx){
          io.nop.xlang.ast.ForStatement ret = new io.nop.xlang.ast.ForStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.init != null){
               ret.setInit((visitExpression_forInit(ctx.init)));
            }
            if(ctx.test != null){
               ret.setTest((visitExpression_single(ctx.test)));
            }
            if(ctx.update != null){
               ret.setUpdate((visitSequenceExpression(ctx.update)));
            }
            if(ctx.body != null){
               ret.setBody((visitStatement(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.Identifier visitIdentifier_expr(Identifier_exprContext ctx){
           IdentifierContext node = ctx.identifier();
           return node == null ? null : visitIdentifier(node);
        }
      
        public io.nop.xlang.ast.Identifier visitIdentifier_for(Identifier_forContext ctx){
           IdentifierContext node = ctx.identifier();
           return node == null ? null : visitIdentifier(node);
        }
      
      public io.nop.xlang.ast.IfStatement visitIfStatement_expr(IfStatement_exprContext ctx){
          io.nop.xlang.ast.IfStatement ret = new io.nop.xlang.ast.IfStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
             ret.setTernaryExpr(true);
          
            if(ctx.test != null){
               ret.setTest((visitExpression_single(ctx.test)));
            }
            if(ctx.consequent != null){
               ret.setConsequent((visitExpression_single(ctx.consequent)));
            }
            if(ctx.alternate != null){
               ret.setAlternate((visitExpression_single(ctx.alternate)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.InExpression visitInExpression(InExpressionContext ctx){
          io.nop.xlang.ast.InExpression ret = new io.nop.xlang.ast.InExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitExpression_single(ctx.left)));
            }
            if(ctx.right != null){
               ret.setRight((visitExpression_single(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.InstanceOfExpression visitInstanceOfExpression(InstanceOfExpressionContext ctx){
          io.nop.xlang.ast.InstanceOfExpression ret = new io.nop.xlang.ast.InstanceOfExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((visitExpression_single(ctx.value)));
            }
            if(ctx.refType != null){
               ret.setRefType((visitNamedTypeNode(ctx.refType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.IntersectionTypeDef visitIntersectionTypeDef(IntersectionTypeDefContext ctx){
          io.nop.xlang.ast.IntersectionTypeDef ret = new io.nop.xlang.ast.IntersectionTypeDef();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.types != null){
               ret.setTypes((buildIntersectionTypeDef_(ctx.types)));
            }else{
               ret.setTypes(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.Literal visitLiteral_expr(Literal_exprContext ctx){
           LiteralContext node = ctx.literal();
           return node == null ? null : visitLiteral(node);
        }
      
      public io.nop.xlang.ast.MacroExpression visitMacroExpression(MacroExpressionContext ctx){
          io.nop.xlang.ast.MacroExpression ret = new io.nop.xlang.ast.MacroExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expr != null){
               ret.setExpr((visitExpression_single(ctx.expr)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.MemberExpression visitMemberExpression_dot(MemberExpression_dotContext ctx){
          io.nop.xlang.ast.MemberExpression ret = new io.nop.xlang.ast.MemberExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.object != null){
               ret.setObject((visitExpression_single(ctx.object)));
            }
            if(ctx.optional != null){
               ret.setOptional((MemberExpression_optional(ctx.optional)));
            }
            if(ctx.property != null){
               ret.setProperty((visitIdentifier_ex(ctx.property)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.MemberExpression visitMemberExpression_dot2(MemberExpression_dot2Context ctx){
          io.nop.xlang.ast.MemberExpression ret = new io.nop.xlang.ast.MemberExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.object != null){
               ret.setObject((visitExpression_single(ctx.object)));
            }
            if(ctx.property != null){
               ret.setProperty((visitIdentifier_ex(ctx.property)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.MemberExpression visitMemberExpression_index(MemberExpression_indexContext ctx){
          io.nop.xlang.ast.MemberExpression ret = new io.nop.xlang.ast.MemberExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
             ret.setComputed(true);
          
            if(ctx.object != null){
               ret.setObject((visitExpression_single(ctx.object)));
            }
            if(ctx.optional != null){
               ret.setOptional((MemberExpression_optional(ctx.optional)));
            }
            if(ctx.property != null){
               ret.setProperty((visitExpression_single(ctx.property)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.MemberExpression visitMemberExpression_index2(MemberExpression_index2Context ctx){
          io.nop.xlang.ast.MemberExpression ret = new io.nop.xlang.ast.MemberExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
             ret.setComputed(true);
          
            if(ctx.object != null){
               ret.setObject((visitExpression_single(ctx.object)));
            }
            if(ctx.property != null){
               ret.setProperty((visitExpression_single(ctx.property)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.ModuleDeclaration visitModuleDeclaration_import2(ModuleDeclaration_import2Context ctx){
           ModuleDeclaration_importContext node = ctx.moduleDeclaration_import();
           return node == null ? null : visitModuleDeclaration_import(node);
        }
      
      public io.nop.xlang.ast.NewExpression visitNewExpression(NewExpressionContext ctx){
          io.nop.xlang.ast.NewExpression ret = new io.nop.xlang.ast.NewExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.callee != null){
               ret.setCallee((visitParameterizedTypeNode(ctx.callee)));
            }
            if(ctx.arguments != null){
               ret.setArguments((buildArguments_(ctx.arguments)));
            }else{
               ret.setArguments(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ObjectBinding visitObjectBinding_full(ObjectBinding_fullContext ctx){
          io.nop.xlang.ast.ObjectBinding ret = new io.nop.xlang.ast.ObjectBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.properties != null){
               ret.setProperties((buildPropertyBindings_(ctx.properties)));
            }else{
               ret.setProperties(Collections.emptyList());
            }
            
            if(ctx.restBinding_ != null){
               ret.setRestBinding((visitRestBinding(ctx.restBinding_)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ObjectBinding visitObjectBinding_rest(ObjectBinding_restContext ctx){
          io.nop.xlang.ast.ObjectBinding ret = new io.nop.xlang.ast.ObjectBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.restBinding_ != null){
               ret.setRestBinding((visitRestBinding(ctx.restBinding_)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.ObjectExpression visitObjectExpression_expr(ObjectExpression_exprContext ctx){
           ObjectExpressionContext node = ctx.objectExpression();
           return node == null ? null : visitObjectExpression(node);
        }
      
        public io.nop.xlang.ast.ParameterizedTypeNode visitParameterizedTypeNode_named(ParameterizedTypeNode_namedContext ctx){
           ParameterizedTypeNodeContext node = ctx.parameterizedTypeNode();
           return node == null ? null : visitParameterizedTypeNode(node);
        }
      
      public io.nop.xlang.ast.PropertyAssignment visitPropertyAssignment_assign(PropertyAssignment_assignContext ctx){
          io.nop.xlang.ast.PropertyAssignment ret = new io.nop.xlang.ast.PropertyAssignment();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.key != null){
               ret.setKey((visitExpression_propName(ctx.key)));
            }
            if(ctx.value != null){
               ret.setValue((visitExpression_single(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.PropertyAssignment visitPropertyAssignment_computed(PropertyAssignment_computedContext ctx){
          io.nop.xlang.ast.PropertyAssignment ret = new io.nop.xlang.ast.PropertyAssignment();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
             ret.setComputed(true);
          
            if(ctx.key != null){
               ret.setKey((visitExpression_single(ctx.key)));
            }
            if(ctx.value != null){
               ret.setValue((visitExpression_single(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.PropertyAssignment visitPropertyAssignment_shorthand(PropertyAssignment_shorthandContext ctx){
          io.nop.xlang.ast.PropertyAssignment ret = new io.nop.xlang.ast.PropertyAssignment();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.key != null){
               ret.setKey((visitIdentifier_ex(ctx.key)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.PropertyBinding visitPropertyBinding_full(PropertyBinding_fullContext ctx){
          io.nop.xlang.ast.PropertyBinding ret = new io.nop.xlang.ast.PropertyBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.propName != null){
               ret.setPropName((PropertyBinding_propName(ctx.propName)));
            }
            if(ctx.identifier_ != null){
               ret.setIdentifier((visitIdentifier(ctx.identifier_)));
            }
            if(ctx.initializer != null){
               ret.setInitializer((visitExpression_initializer(ctx.initializer)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.PropertyBinding visitPropertyBinding_simple(PropertyBinding_simpleContext ctx){
          io.nop.xlang.ast.PropertyBinding ret = new io.nop.xlang.ast.PropertyBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.identifier_ != null){
               ret.setIdentifier((visitIdentifier(ctx.identifier_)));
            }
            if(ctx.initializer != null){
               ret.setInitializer((visitExpression_initializer(ctx.initializer)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.SequenceExpression visitSequenceExpression_init(SequenceExpression_initContext ctx){
          io.nop.xlang.ast.SequenceExpression ret = new io.nop.xlang.ast.SequenceExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expressions != null){
               ret.setExpressions((buildInitExpressions_(ctx.expressions)));
            }else{
               ret.setExpressions(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.Statement visitStatement_top(Statement_topContext ctx){
           StatementContext node = ctx.statement();
           return node == null ? null : visitStatement(node);
        }
      
      public io.nop.xlang.ast.SuperExpression visitSuperExpression(SuperExpressionContext ctx){
          io.nop.xlang.ast.SuperExpression ret = new io.nop.xlang.ast.SuperExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.TemplateStringExpression visitTemplateStringExpression(TemplateStringExpressionContext ctx){
          io.nop.xlang.ast.TemplateStringExpression ret = new io.nop.xlang.ast.TemplateStringExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.id != null){
               ret.setId((visitIdentifier(ctx.id)));
            }
            if(ctx.value != null){
               ret.setValue((visitTemplateStringLiteral(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ThisExpression visitThisExpression(ThisExpressionContext ctx){
          io.nop.xlang.ast.ThisExpression ret = new io.nop.xlang.ast.ThisExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.TypeNameNode visitTypeNameNode_named(TypeNameNode_namedContext ctx){
          io.nop.xlang.ast.TypeNameNode ret = new io.nop.xlang.ast.TypeNameNode();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.typeName != null){
               ret.setTypeName((TypeNameNode_typeName(ctx.typeName)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.TypeNameNode visitTypeNameNode_predefined_named(TypeNameNode_predefined_namedContext ctx){
           TypeNameNode_predefinedContext node = ctx.typeNameNode_predefined();
           return node == null ? null : visitTypeNameNode_predefined(node);
        }
      
      public io.nop.xlang.ast.TypeOfExpression visitTypeOfExpression(TypeOfExpressionContext ctx){
          io.nop.xlang.ast.TypeOfExpression ret = new io.nop.xlang.ast.TypeOfExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.argument != null){
               ret.setArgument((visitExpression_single(ctx.argument)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.UnaryExpression visitUnaryExpression(UnaryExpressionContext ctx){
          io.nop.xlang.ast.UnaryExpression ret = new io.nop.xlang.ast.UnaryExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.operator != null){
               ret.setOperator((UnaryExpression_operator(ctx.operator)));
            }
            if(ctx.argument != null){
               ret.setArgument((visitExpression_single(ctx.argument)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.UnionTypeDef visitUnionTypeDef(UnionTypeDefContext ctx){
          io.nop.xlang.ast.UnionTypeDef ret = new io.nop.xlang.ast.UnionTypeDef();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.types != null){
               ret.setTypes((buildUnionTypeDef_(ctx.types)));
            }else{
               ret.setTypes(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.UpdateExpression visitUpdateExpression(UpdateExpressionContext ctx){
          io.nop.xlang.ast.UpdateExpression ret = new io.nop.xlang.ast.UpdateExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.operator != null){
               ret.setOperator((UpdateExpression_operator(ctx.operator)));
            }
            if(ctx.argument != null){
               ret.setArgument((visitExpression_single(ctx.argument)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.VariableDeclaration visitVariableDeclaration_for(VariableDeclaration_forContext ctx){
          io.nop.xlang.ast.VariableDeclaration ret = new io.nop.xlang.ast.VariableDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.kind != null){
               ret.setKind((VariableDeclaration_kind(ctx.kind)));
            }
            if(ctx.declarators_single != null){
               ret.setDeclarators(io.nop.commons.util.CollectionHelper.safeSingletonList(visitVariableDeclarator(ctx.declarators_single)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.VariableDeclaration visitVariableDeclaration_init(VariableDeclaration_initContext ctx){
          io.nop.xlang.ast.VariableDeclaration ret = new io.nop.xlang.ast.VariableDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.kind != null){
               ret.setKind((VariableDeclaration_kind(ctx.kind)));
            }
            if(ctx.declarators != null){
               ret.setDeclarators((buildVariableDeclarators_(ctx.declarators)));
            }else{
               ret.setDeclarators(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.WhileStatement visitWhileStatement(WhileStatementContext ctx){
          io.nop.xlang.ast.WhileStatement ret = new io.nop.xlang.ast.WhileStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.test != null){
               ret.setTest((visitExpression_single(ctx.test)));
            }
            if(ctx.body != null){
               ret.setBody((visitStatement(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.Expression> buildArguments_(Arguments_Context ctx){
    java.util.List<io.nop.xlang.ast.Expression> list = new ArrayList<>();
    List<Expression_singleContext> elms = ctx.expression_single();
    if(elms != null){
      for(Expression_singleContext elm: elms){
         list.add(visitExpression_single(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.ArrayBinding visitArrayBinding(ArrayBindingContext ctx){
        
            return (io.nop.xlang.ast.ArrayBinding)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.ArrayElementBinding visitArrayElementBinding(ArrayElementBindingContext ctx){
          io.nop.xlang.ast.ArrayElementBinding ret = new io.nop.xlang.ast.ArrayElementBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.identifier_ != null){
               ret.setIdentifier((visitIdentifier(ctx.identifier_)));
            }
            if(ctx.initializer != null){
               ret.setInitializer((visitExpression_initializer(ctx.initializer)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.ArrayElementBinding> buildArrayElementBindings_(ArrayElementBindings_Context ctx){
    java.util.List<io.nop.xlang.ast.ArrayElementBinding> list = new ArrayList<>();
    List<ArrayElementBindingContext> elms = ctx.arrayElementBinding();
    if(elms != null){
      for(ArrayElementBindingContext elm: elms){
         list.add(visitArrayElementBinding(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.ArrayExpression visitArrayExpression(ArrayExpressionContext ctx){
          io.nop.xlang.ast.ArrayExpression ret = new io.nop.xlang.ast.ArrayExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.elements != null){
               ret.setElements((buildElementList_(ctx.elements)));
            }else{
               ret.setElements(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ArrowFunctionExpression visitArrowFunctionExpression(ArrowFunctionExpressionContext ctx){
        
            return (io.nop.xlang.ast.ArrowFunctionExpression)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.AssignmentExpression visitAssignmentExpression(AssignmentExpressionContext ctx){
          io.nop.xlang.ast.AssignmentExpression ret = new io.nop.xlang.ast.AssignmentExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitExpression_leftHandSide(ctx.left)));
            }
            if(ctx.operator != null){
               ret.setOperator((AssignmentExpression_operator(ctx.operator)));
            }
            if(ctx.right != null){
               ret.setRight((visitExpression_single(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.AssignmentExpression visitAssignmentExpression_init(AssignmentExpression_initContext ctx){
          io.nop.xlang.ast.AssignmentExpression ret = new io.nop.xlang.ast.AssignmentExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.left != null){
               ret.setLeft((visitIdentifier(ctx.left)));
            }
            if(ctx.operator != null){
               ret.setOperator((AssignmentExpression_operator(ctx.operator)));
            }
            if(ctx.right != null){
               ret.setRight((visitExpression_single(ctx.right)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.XLangASTNode visitAst_arrayElement(Ast_arrayElementContext ctx){
        
            return (io.nop.xlang.ast.XLangASTNode)this.visitChildren(ctx);
          
      }
            
        public io.nop.xlang.ast.ExportNamedDeclaration visitAst_exportStatement(Ast_exportStatementContext ctx){
           ExportNamedDeclarationContext node = ctx.exportNamedDeclaration();
           return node == null ? null : visitExportNamedDeclaration(node);
        }
      
        public io.nop.xlang.ast.XLangASTNode visitAst_exportStatement2(Ast_exportStatement2Context ctx){
           Ast_exportStatementContext node = ctx.ast_exportStatement();
           return node == null ? null : visitAst_exportStatement(node);
        }
      
      public io.nop.xlang.ast.XLangASTNode visitAst_identifierOrPattern(Ast_identifierOrPatternContext ctx){
        
            return (io.nop.xlang.ast.XLangASTNode)this.visitChildren(ctx);
          
      }
            
      public io.nop.xlang.ast.XLangASTNode visitAst_importSource(Ast_importSourceContext ctx){
        
            return (io.nop.xlang.ast.XLangASTNode)this.visitChildren(ctx);
          
      }
            
      public io.nop.xlang.ast.XLangASTNode visitAst_metaValue(Ast_metaValueContext ctx){
        
            return (io.nop.xlang.ast.XLangASTNode)this.visitChildren(ctx);
          
      }
            
      public io.nop.xlang.ast.XLangASTNode visitAst_objectProperty(Ast_objectPropertyContext ctx){
        
            return (io.nop.xlang.ast.XLangASTNode)this.visitChildren(ctx);
          
      }
            
      public io.nop.xlang.ast.XLangASTNode visitAst_topLevelStatement(Ast_topLevelStatementContext ctx){
        
            return (io.nop.xlang.ast.XLangASTNode)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.BlockStatement visitBlockStatement(BlockStatementContext ctx){
          io.nop.xlang.ast.BlockStatement ret = new io.nop.xlang.ast.BlockStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.body != null){
               ret.setBody((buildStatements_(ctx.body)));
            }else{
               ret.setBody(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.BlockStatement visitBlockStatement_finally(BlockStatement_finallyContext ctx){
           BlockStatementContext node = ctx.blockStatement();
           return node == null ? null : visitBlockStatement(node);
        }
      
      public io.nop.xlang.ast.BreakStatement visitBreakStatement(BreakStatementContext ctx){
          io.nop.xlang.ast.BreakStatement ret = new io.nop.xlang.ast.BreakStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.CatchClause visitCatchClause(CatchClauseContext ctx){
          io.nop.xlang.ast.CatchClause ret = new io.nop.xlang.ast.CatchClause();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((visitIdentifier(ctx.name)));
            }
            if(ctx.varType != null){
               ret.setVarType((visitParameterizedTypeNode(ctx.varType)));
            }
            if(ctx.body != null){
               ret.setBody((visitBlockStatement(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ContinueStatement visitContinueStatement(ContinueStatementContext ctx){
          io.nop.xlang.ast.ContinueStatement ret = new io.nop.xlang.ast.ContinueStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.Decorator visitDecorator(DecoratorContext ctx){
          io.nop.xlang.ast.Decorator ret = new io.nop.xlang.ast.Decorator();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((visitQualifiedName(ctx.name)));
            }
            if(ctx.value != null){
               ret.setValue((visitMetaObject(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.Decorator> buildDecoratorElements_(DecoratorElements_Context ctx){
    java.util.List<io.nop.xlang.ast.Decorator> list = new ArrayList<>();
    List<DecoratorContext> elms = ctx.decorator();
    if(elms != null){
      for(DecoratorContext elm: elms){
         list.add(visitDecorator(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.Decorators visitDecorators(DecoratorsContext ctx){
          io.nop.xlang.ast.Decorators ret = new io.nop.xlang.ast.Decorators();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators_ != null){
               ret.setDecorators((buildDecoratorElements_(ctx.decorators_)));
            }else{
               ret.setDecorators(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.XLangASTNode> buildElementList_(ElementList_Context ctx){
    java.util.List<io.nop.xlang.ast.XLangASTNode> list = new ArrayList<>();
    List<Ast_arrayElementContext> elms = ctx.ast_arrayElement();
    if(elms != null){
      for(Ast_arrayElementContext elm: elms){
         list.add(visitAst_arrayElement(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.EmptyStatement visitEmptyStatement(EmptyStatementContext ctx){
          io.nop.xlang.ast.EmptyStatement ret = new io.nop.xlang.ast.EmptyStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.EnumDeclaration visitEnumDeclaration(EnumDeclarationContext ctx){
          io.nop.xlang.ast.EnumDeclaration ret = new io.nop.xlang.ast.EnumDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((visitIdentifier(ctx.name)));
            }
            if(ctx.members != null){
               ret.setMembers((buildEnumMembers_(ctx.members)));
            }else{
               ret.setMembers(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.EnumMember visitEnumMember(EnumMemberContext ctx){
          io.nop.xlang.ast.EnumMember ret = new io.nop.xlang.ast.EnumMember();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((visitIdentifier(ctx.name)));
            }
            if(ctx.value != null){
               ret.setValue((visitLiteral(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.EnumMember> buildEnumMembers_(EnumMembers_Context ctx){
    java.util.List<io.nop.xlang.ast.EnumMember> list = new ArrayList<>();
    List<EnumMemberContext> elms = ctx.enumMember();
    if(elms != null){
      for(EnumMemberContext elm: elms){
         list.add(visitEnumMember(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.ExportNamedDeclaration visitExportNamedDeclaration(ExportNamedDeclarationContext ctx){
          io.nop.xlang.ast.ExportNamedDeclaration ret = new io.nop.xlang.ast.ExportNamedDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.specifiers != null){
               ret.setSpecifiers((buildExportSpecifiers_(ctx.specifiers)));
            }else{
               ret.setSpecifiers(Collections.emptyList());
            }
            
            if(ctx.source != null){
               ret.setSource((visitLiteral_string(ctx.source)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ExportSpecifier visitExportSpecifier(ExportSpecifierContext ctx){
          io.nop.xlang.ast.ExportSpecifier ret = new io.nop.xlang.ast.ExportSpecifier();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.local != null){
               ret.setLocal((visitIdentifier(ctx.local)));
            }
            if(ctx.exported != null){
               ret.setExported((visitIdentifier(ctx.exported)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.ExportSpecifier> buildExportSpecifiers_(ExportSpecifiers_Context ctx){
    java.util.List<io.nop.xlang.ast.ExportSpecifier> list = new ArrayList<>();
    List<ExportSpecifierContext> elms = ctx.exportSpecifier();
    if(elms != null){
      for(ExportSpecifierContext elm: elms){
         list.add(visitExportSpecifier(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.ExpressionStatement visitExpressionStatement(ExpressionStatementContext ctx){
          io.nop.xlang.ast.ExpressionStatement ret = new io.nop.xlang.ast.ExpressionStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expression != null){
               ret.setExpression((visitExpression_single(ctx.expression)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.Expression visitExpression_forInit(Expression_forInitContext ctx){
        
            return (io.nop.xlang.ast.Expression)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.Expression visitExpression_functionBody(Expression_functionBodyContext ctx){
        
            return (io.nop.xlang.ast.Expression)this.visitChildren(ctx);
          
      }
            
        public io.nop.xlang.ast.Expression visitExpression_initializer(Expression_initializerContext ctx){
           Expression_singleContext node = ctx.expression_single();
           return node == null ? null : visitExpression_single(node);
        }
      
      public io.nop.xlang.ast.Expression visitExpression_iterationLeft(Expression_iterationLeftContext ctx){
        
            return (io.nop.xlang.ast.Expression)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.Expression visitExpression_leftHandSide(Expression_leftHandSideContext ctx){
        
            return (io.nop.xlang.ast.Expression)this.visitChildren(ctx);
          
      }
            
      public io.nop.xlang.ast.Expression visitExpression_propName(Expression_propNameContext ctx){
        
            return (io.nop.xlang.ast.Expression)this.visitChildren(ctx);
          
      }
            
      public io.nop.xlang.ast.Expression visitExpression_single(Expression_singleContext ctx){
        
            return (io.nop.xlang.ast.Expression)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.FunctionArgTypeDef visitFunctionArgTypeDef(FunctionArgTypeDefContext ctx){
          io.nop.xlang.ast.FunctionArgTypeDef ret = new io.nop.xlang.ast.FunctionArgTypeDef();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.argName != null){
               ret.setArgName((visitIdentifier(ctx.argName)));
            }
            if(ctx.optional != null){
               ret.setOptional((FunctionArgTypeDef_optional(ctx.optional)));
            }
            if(ctx.argType != null){
               ret.setArgType((visitNamedTypeNode_annotation(ctx.argType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.FunctionDeclaration visitFunctionDeclaration(FunctionDeclarationContext ctx){
          io.nop.xlang.ast.FunctionDeclaration ret = new io.nop.xlang.ast.FunctionDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators_ != null){
               ret.setDecorators((visitDecorators(ctx.decorators_)));
            }
            if(ctx.name != null){
               ret.setName((visitIdentifier(ctx.name)));
            }
            if(ctx.params != null){
               ret.setParams((buildParameterList_(ctx.params)));
            }else{
               ret.setParams(Collections.emptyList());
            }
            
            if(ctx.returnType != null){
               ret.setReturnType((visitNamedTypeNode_annotation(ctx.returnType)));
            }
            if(ctx.body != null){
               ret.setBody((visitBlockStatement(ctx.body)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> buildFunctionParameterTypes_(FunctionParameterTypes_Context ctx){
    java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> list = new ArrayList<>();
    List<FunctionArgTypeDefContext> elms = ctx.functionArgTypeDef();
    if(elms != null){
      for(FunctionArgTypeDefContext elm: elms){
         list.add(visitFunctionArgTypeDef(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.FunctionTypeDef visitFunctionTypeDef(FunctionTypeDefContext ctx){
          io.nop.xlang.ast.FunctionTypeDef ret = new io.nop.xlang.ast.FunctionTypeDef();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.typeParams != null){
               ret.setTypeParams((buildTypeParameters_(ctx.typeParams)));
            }else{
               ret.setTypeParams(Collections.emptyList());
            }
            
            if(ctx.args != null){
               ret.setArgs((buildFunctionParameterTypes_(ctx.args)));
            }else{
               ret.setArgs(Collections.emptyList());
            }
            
            if(ctx.returnType != null){
               ret.setReturnType((visitNamedTypeNode(ctx.returnType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public io.nop.xlang.ast.Identifier visitIdentifier(IdentifierContext ctx){
    io.nop.xlang.ast.Identifier ret = new io.nop.xlang.ast.Identifier();
    ret.setLocation(ParseTreeHelper.loc(ctx));
    ret.setName(Identifier_name(ParseTreeHelper.terminalNode(ctx)));
    ret.normalize();
    ret.validate();
    return ret;
}
      
      public io.nop.xlang.ast.Identifier visitIdentifier_ex(Identifier_exContext ctx){
          io.nop.xlang.ast.Identifier ret = new io.nop.xlang.ast.Identifier();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((Identifier_name(ctx.name)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.IfStatement visitIfStatement(IfStatementContext ctx){
          io.nop.xlang.ast.IfStatement ret = new io.nop.xlang.ast.IfStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.test != null){
               ret.setTest((visitExpression_single(ctx.test)));
            }
            if(ctx.consequent != null){
               ret.setConsequent((visitStatement(ctx.consequent)));
            }
            if(ctx.alternate != null){
               ret.setAlternate((visitStatement(ctx.alternate)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ImportAsDeclaration visitImportAsDeclaration(ImportAsDeclarationContext ctx){
          io.nop.xlang.ast.ImportAsDeclaration ret = new io.nop.xlang.ast.ImportAsDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.source != null){
               ret.setSource((visitAst_importSource(ctx.source)));
            }
            if(ctx.local != null){
               ret.setLocal((visitIdentifier(ctx.local)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ImportDeclaration visitImportDeclaration(ImportDeclarationContext ctx){
          io.nop.xlang.ast.ImportDeclaration ret = new io.nop.xlang.ast.ImportDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.specifiers != null){
               ret.setSpecifiers((buildImportSpecifiers_(ctx.specifiers)));
            }else{
               ret.setSpecifiers(Collections.emptyList());
            }
            
            if(ctx.source != null){
               ret.setSource((visitLiteral_string(ctx.source)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ImportSpecifier visitImportSpecifier(ImportSpecifierContext ctx){
          io.nop.xlang.ast.ImportSpecifier ret = new io.nop.xlang.ast.ImportSpecifier();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.imported != null){
               ret.setImported((visitIdentifier(ctx.imported)));
            }
            if(ctx.local != null){
               ret.setLocal((visitIdentifier(ctx.local)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.ModuleSpecifier> buildImportSpecifiers_(ImportSpecifiers_Context ctx){
    java.util.List<io.nop.xlang.ast.ModuleSpecifier> list = new ArrayList<>();
    List<ImportSpecifierContext> elms = ctx.importSpecifier();
    if(elms != null){
      for(ImportSpecifierContext elm: elms){
         list.add(visitImportSpecifier(elm));
      }
    }
    return list;
}
      
public java.util.List<io.nop.xlang.ast.Expression> buildInitExpressions_(InitExpressions_Context ctx){
    java.util.List<io.nop.xlang.ast.Expression> list = new ArrayList<>();
    List<AssignmentExpression_initContext> elms = ctx.assignmentExpression_init();
    if(elms != null){
      for(AssignmentExpression_initContext elm: elms){
         list.add(visitAssignmentExpression_init(elm));
      }
    }
    return list;
}
      
public java.util.List<io.nop.xlang.ast.NamedTypeNode> buildIntersectionTypeDef_(IntersectionTypeDef_Context ctx){
    java.util.List<io.nop.xlang.ast.NamedTypeNode> list = new ArrayList<>();
    List<NamedTypeNodeContext> elms = ctx.namedTypeNode();
    if(elms != null){
      for(NamedTypeNodeContext elm: elms){
         list.add(visitNamedTypeNode(elm));
      }
    }
    return list;
}
      
public io.nop.xlang.ast.Literal visitLiteral(LiteralContext ctx){
    io.nop.xlang.ast.Literal ret = new io.nop.xlang.ast.Literal();
    ret.setLocation(ParseTreeHelper.loc(ctx));
    ret.setValue(Literal_value(ParseTreeHelper.terminalNode(ctx)));
    ret.normalize();
    ret.validate();
    return ret;
}
      
public io.nop.xlang.ast.Literal visitLiteral_numeric(Literal_numericContext ctx){
    io.nop.xlang.ast.Literal ret = new io.nop.xlang.ast.Literal();
    ret.setLocation(ParseTreeHelper.loc(ctx));
    ret.setValue(Literal_value(ParseTreeHelper.terminalNode(ctx)));
    ret.normalize();
    ret.validate();
    return ret;
}
      
public io.nop.xlang.ast.Literal visitLiteral_string(Literal_stringContext ctx){
    io.nop.xlang.ast.Literal ret = new io.nop.xlang.ast.Literal();
    ret.setLocation(ParseTreeHelper.loc(ctx));
    ret.setValue(Literal_value(ParseTreeHelper.terminalNode(ctx)));
    ret.normalize();
    ret.validate();
    return ret;
}
      
      public io.nop.xlang.ast.MemberExpression visitMemberExpression(MemberExpressionContext ctx){
        
            return (io.nop.xlang.ast.MemberExpression)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.MetaArray visitMetaArray(MetaArrayContext ctx){
          io.nop.xlang.ast.MetaArray ret = new io.nop.xlang.ast.MetaArray();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.elements != null){
               ret.setElements((buildMetaArrayElements_(ctx.elements)));
            }else{
               ret.setElements(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.XLangASTNode> buildMetaArrayElements_(MetaArrayElements_Context ctx){
    java.util.List<io.nop.xlang.ast.XLangASTNode> list = new ArrayList<>();
    List<Ast_metaValueContext> elms = ctx.ast_metaValue();
    if(elms != null){
      for(Ast_metaValueContext elm: elms){
         list.add(visitAst_metaValue(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.MetaObject visitMetaObject(MetaObjectContext ctx){
          io.nop.xlang.ast.MetaObject ret = new io.nop.xlang.ast.MetaObject();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.properties != null){
               ret.setProperties((buildMetaObjectProperties_(ctx.properties)));
            }else{
               ret.setProperties(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.MetaProperty> buildMetaObjectProperties_(MetaObjectProperties_Context ctx){
    java.util.List<io.nop.xlang.ast.MetaProperty> list = new ArrayList<>();
    List<MetaPropertyContext> elms = ctx.metaProperty();
    if(elms != null){
      for(MetaPropertyContext elm: elms){
         list.add(visitMetaProperty(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.MetaProperty visitMetaProperty(MetaPropertyContext ctx){
          io.nop.xlang.ast.MetaProperty ret = new io.nop.xlang.ast.MetaProperty();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((visitIdentifier(ctx.name)));
            }
            if(ctx.value != null){
               ret.setValue((visitAst_metaValue(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ModuleDeclaration visitModuleDeclaration_import(ModuleDeclaration_importContext ctx){
        
            return (io.nop.xlang.ast.ModuleDeclaration)this.visitChildren(ctx);
          
      }
            
      public io.nop.xlang.ast.NamedTypeNode visitNamedTypeNode(NamedTypeNodeContext ctx){
        
            return (io.nop.xlang.ast.NamedTypeNode)ctx.accept(this);
          
      }
            
        public io.nop.xlang.ast.NamedTypeNode visitNamedTypeNode_annotation(NamedTypeNode_annotationContext ctx){
           NamedTypeNodeContext node = ctx.namedTypeNode();
           return node == null ? null : visitNamedTypeNode(node);
        }
      
      public io.nop.xlang.ast.ObjectBinding visitObjectBinding(ObjectBindingContext ctx){
        
            return (io.nop.xlang.ast.ObjectBinding)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.ObjectExpression visitObjectExpression(ObjectExpressionContext ctx){
          io.nop.xlang.ast.ObjectExpression ret = new io.nop.xlang.ast.ObjectExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.properties != null){
               ret.setProperties((buildObjectProperties_(ctx.properties)));
            }else{
               ret.setProperties(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.XLangASTNode> buildObjectProperties_(ObjectProperties_Context ctx){
    java.util.List<io.nop.xlang.ast.XLangASTNode> list = new ArrayList<>();
    List<Ast_objectPropertyContext> elms = ctx.ast_objectProperty();
    if(elms != null){
      for(Ast_objectPropertyContext elm: elms){
         list.add(visitAst_objectProperty(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.ObjectTypeDef visitObjectTypeDef(ObjectTypeDefContext ctx){
          io.nop.xlang.ast.ObjectTypeDef ret = new io.nop.xlang.ast.ObjectTypeDef();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.types != null){
               ret.setTypes((buildObjectTypeElements_(ctx.types)));
            }else{
               ret.setTypes(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.PropertyTypeDef> buildObjectTypeElements_(ObjectTypeElements_Context ctx){
    java.util.List<io.nop.xlang.ast.PropertyTypeDef> list = new ArrayList<>();
    List<PropertyTypeDefContext> elms = ctx.propertyTypeDef();
    if(elms != null){
      for(PropertyTypeDefContext elm: elms){
         list.add(visitPropertyTypeDef(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.ParameterDeclaration visitParameterDeclaration(ParameterDeclarationContext ctx){
          io.nop.xlang.ast.ParameterDeclaration ret = new io.nop.xlang.ast.ParameterDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.decorators_ != null){
               ret.setDecorators((visitDecorators(ctx.decorators_)));
            }
            if(ctx.name != null){
               ret.setName((visitAst_identifierOrPattern(ctx.name)));
            }
            if(ctx.type != null){
               ret.setType((visitNamedTypeNode_annotation(ctx.type)));
            }
            if(ctx.initializer != null){
               ret.setInitializer((visitExpression_initializer(ctx.initializer)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ParameterDeclaration visitParameterDeclaration_simple(ParameterDeclaration_simpleContext ctx){
          io.nop.xlang.ast.ParameterDeclaration ret = new io.nop.xlang.ast.ParameterDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((visitIdentifier(ctx.name)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.ParameterDeclaration> buildParameterList_(ParameterList_Context ctx){
    java.util.List<io.nop.xlang.ast.ParameterDeclaration> list = new ArrayList<>();
    List<ParameterDeclarationContext> elms = ctx.parameterDeclaration();
    if(elms != null){
      for(ParameterDeclarationContext elm: elms){
         list.add(visitParameterDeclaration(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.ParameterizedTypeNode visitParameterizedTypeNode(ParameterizedTypeNodeContext ctx){
          io.nop.xlang.ast.ParameterizedTypeNode ret = new io.nop.xlang.ast.ParameterizedTypeNode();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.typeName != null){
               ret.setTypeName((ParameterizedTypeNode_typeName(ctx.typeName)));
            }
            if(ctx.typeArgs != null){
               ret.setTypeArgs((buildTypeArguments_(ctx.typeArgs)));
            }else{
               ret.setTypeArgs(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.Program visitProgram(ProgramContext ctx){
          io.nop.xlang.ast.Program ret = new io.nop.xlang.ast.Program();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.body != null){
               ret.setBody((buildTopLevelStatements_(ctx.body)));
            }else{
               ret.setBody(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.PropertyAssignment visitPropertyAssignment(PropertyAssignmentContext ctx){
        
            return (io.nop.xlang.ast.PropertyAssignment)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.PropertyBinding visitPropertyBinding(PropertyBindingContext ctx){
        
            return (io.nop.xlang.ast.PropertyBinding)ctx.accept(this);
          
      }
            
public java.util.List<io.nop.xlang.ast.PropertyBinding> buildPropertyBindings_(PropertyBindings_Context ctx){
    java.util.List<io.nop.xlang.ast.PropertyBinding> list = new ArrayList<>();
    List<PropertyBindingContext> elms = ctx.propertyBinding();
    if(elms != null){
      for(PropertyBindingContext elm: elms){
         list.add(visitPropertyBinding(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.PropertyTypeDef visitPropertyTypeDef(PropertyTypeDefContext ctx){
          io.nop.xlang.ast.PropertyTypeDef ret = new io.nop.xlang.ast.PropertyTypeDef();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.readonly != null){
               ret.setReadonly((PropertyTypeDef_readonly(ctx.readonly)));
            }
            if(ctx.name != null){
               ret.setName((PropertyTypeDef_name(ctx.name)));
            }
            if(ctx.optional != null){
               ret.setOptional((PropertyTypeDef_optional(ctx.optional)));
            }
            if(ctx.valueType != null){
               ret.setValueType((visitStructuredTypeDef_annotation(ctx.valueType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.QualifiedName visitQualifiedName(QualifiedNameContext ctx){
          io.nop.xlang.ast.QualifiedName ret = new io.nop.xlang.ast.QualifiedName();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((QualifiedName_name(ctx.name)));
            }
            if(ctx.next != null){
               ret.setNext((visitQualifiedName(ctx.next)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
        public io.nop.xlang.ast.QualifiedName buildQualifiedName_(QualifiedName_Context ctx){
           QualifiedNameContext node = ctx.qualifiedName();
           return node == null ? null : visitQualifiedName(node);
        }
      
        public io.nop.xlang.ast.Identifier buildQualifiedName_name_(QualifiedName_name_Context ctx){
           IdentifierContext node = ctx.identifier();
           return node == null ? null : visitIdentifier(node);
        }
      
      public io.nop.xlang.ast.RestBinding visitRestBinding(RestBindingContext ctx){
          io.nop.xlang.ast.RestBinding ret = new io.nop.xlang.ast.RestBinding();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.identifier_ != null){
               ret.setIdentifier((visitIdentifier(ctx.identifier_)));
            }
            if(ctx.initializer != null){
               ret.setInitializer((visitExpression_initializer(ctx.initializer)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ReturnStatement visitReturnStatement(ReturnStatementContext ctx){
          io.nop.xlang.ast.ReturnStatement ret = new io.nop.xlang.ast.ReturnStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.argument != null){
               ret.setArgument((visitExpression_single(ctx.argument)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.SequenceExpression visitSequenceExpression(SequenceExpressionContext ctx){
          io.nop.xlang.ast.SequenceExpression ret = new io.nop.xlang.ast.SequenceExpression();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.expressions != null){
               ret.setExpressions((buildSingleExpressions_(ctx.expressions)));
            }else{
               ret.setExpressions(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.Expression> buildSingleExpressions_(SingleExpressions_Context ctx){
    java.util.List<io.nop.xlang.ast.Expression> list = new ArrayList<>();
    List<Expression_singleContext> elms = ctx.expression_single();
    if(elms != null){
      for(Expression_singleContext elm: elms){
         list.add(visitExpression_single(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.SpreadElement visitSpreadElement(SpreadElementContext ctx){
          io.nop.xlang.ast.SpreadElement ret = new io.nop.xlang.ast.SpreadElement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.argument != null){
               ret.setArgument((visitExpression_single(ctx.argument)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.Statement visitStatement(StatementContext ctx){
        
            return (io.nop.xlang.ast.Statement)this.visitChildren(ctx);
          
      }
            
        public io.nop.xlang.ast.BlockStatement visitStatement_defaultClause(Statement_defaultClauseContext ctx){
           BlockStatementContext node = ctx.blockStatement();
           return node == null ? null : visitBlockStatement(node);
        }
      
      public io.nop.xlang.ast.Statement visitStatement_iteration(Statement_iterationContext ctx){
        
            return (io.nop.xlang.ast.Statement)ctx.accept(this);
          
      }
            
public java.util.List<io.nop.xlang.ast.Expression> buildStatements_(Statements_Context ctx){
    java.util.List<io.nop.xlang.ast.Expression> list = new ArrayList<>();
    List<StatementContext> elms = ctx.statement();
    if(elms != null){
      for(StatementContext elm: elms){
         list.add(visitStatement(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.StructuredTypeDef visitStructuredTypeDef(StructuredTypeDefContext ctx){
        
            return (io.nop.xlang.ast.StructuredTypeDef)this.visitChildren(ctx);
          
      }
            
        public io.nop.xlang.ast.StructuredTypeDef visitStructuredTypeDef_annotation(StructuredTypeDef_annotationContext ctx){
           StructuredTypeDefContext node = ctx.structuredTypeDef();
           return node == null ? null : visitStructuredTypeDef(node);
        }
      
      public io.nop.xlang.ast.SwitchCase visitSwitchCase(SwitchCaseContext ctx){
          io.nop.xlang.ast.SwitchCase ret = new io.nop.xlang.ast.SwitchCase();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.test != null){
               ret.setTest((visitExpression_single(ctx.test)));
            }
            if(ctx.consequent != null){
               ret.setConsequent((visitBlockStatement(ctx.consequent)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.SwitchCase> buildSwitchCases_(SwitchCases_Context ctx){
    java.util.List<io.nop.xlang.ast.SwitchCase> list = new ArrayList<>();
    List<SwitchCaseContext> elms = ctx.switchCase();
    if(elms != null){
      for(SwitchCaseContext elm: elms){
         list.add(visitSwitchCase(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.SwitchStatement visitSwitchStatement(SwitchStatementContext ctx){
          io.nop.xlang.ast.SwitchStatement ret = new io.nop.xlang.ast.SwitchStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.discriminant != null){
               ret.setDiscriminant((visitExpression_single(ctx.discriminant)));
            }
            if(ctx.cases != null){
               ret.setCases((buildSwitchCases_(ctx.cases)));
            }else{
               ret.setCases(Collections.emptyList());
            }
            
            if(ctx.defaultCase != null){
               ret.setDefaultCase((visitStatement_defaultClause(ctx.defaultCase)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.TemplateStringLiteral visitTemplateStringLiteral(TemplateStringLiteralContext ctx){
          io.nop.xlang.ast.TemplateStringLiteral ret = new io.nop.xlang.ast.TemplateStringLiteral();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.value != null){
               ret.setValue((TemplateStringLiteral_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.ThrowStatement visitThrowStatement(ThrowStatementContext ctx){
          io.nop.xlang.ast.ThrowStatement ret = new io.nop.xlang.ast.ThrowStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.argument != null){
               ret.setArgument((visitExpression_single(ctx.argument)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.XLangASTNode> buildTopLevelStatements_(TopLevelStatements_Context ctx){
    java.util.List<io.nop.xlang.ast.XLangASTNode> list = new ArrayList<>();
    List<Ast_topLevelStatementContext> elms = ctx.ast_topLevelStatement();
    if(elms != null){
      for(Ast_topLevelStatementContext elm: elms){
         list.add(visitAst_topLevelStatement(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.TryStatement visitTryStatement(TryStatementContext ctx){
          io.nop.xlang.ast.TryStatement ret = new io.nop.xlang.ast.TryStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.block != null){
               ret.setBlock((visitBlockStatement(ctx.block)));
            }
            if(ctx.catchHandler != null){
               ret.setCatchHandler((visitCatchClause(ctx.catchHandler)));
            }
            if(ctx.finalizer != null){
               ret.setFinalizer((visitBlockStatement_finally(ctx.finalizer)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.TupleTypeDef visitTupleTypeDef(TupleTypeDefContext ctx){
          io.nop.xlang.ast.TupleTypeDef ret = new io.nop.xlang.ast.TupleTypeDef();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.types != null){
               ret.setTypes((buildTupleTypeElements_(ctx.types)));
            }else{
               ret.setTypes(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.TypeNode> buildTupleTypeElements_(TupleTypeElements_Context ctx){
    java.util.List<io.nop.xlang.ast.TypeNode> list = new ArrayList<>();
    List<StructuredTypeDefContext> elms = ctx.structuredTypeDef();
    if(elms != null){
      for(StructuredTypeDefContext elm: elms){
         list.add(visitStructuredTypeDef(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.TypeAliasDeclaration visitTypeAliasDeclaration(TypeAliasDeclarationContext ctx){
          io.nop.xlang.ast.TypeAliasDeclaration ret = new io.nop.xlang.ast.TypeAliasDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.typeName != null){
               ret.setTypeName((visitIdentifier(ctx.typeName)));
            }
            if(ctx.typeParams != null){
               ret.setTypeParams((buildTypeParameters_(ctx.typeParams)));
            }else{
               ret.setTypeParams(Collections.emptyList());
            }
            
            if(ctx.defType != null){
               ret.setDefType((visitStructuredTypeDef(ctx.defType)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.NamedTypeNode> buildTypeArguments_(TypeArguments_Context ctx){
    java.util.List<io.nop.xlang.ast.NamedTypeNode> list = new ArrayList<>();
    List<NamedTypeNodeContext> elms = ctx.namedTypeNode();
    if(elms != null){
      for(NamedTypeNodeContext elm: elms){
         list.add(visitNamedTypeNode(elm));
      }
    }
    return list;
}
      
public io.nop.xlang.ast.TypeNameNode visitTypeNameNode_predefined(TypeNameNode_predefinedContext ctx){
    io.nop.xlang.ast.TypeNameNode ret = new io.nop.xlang.ast.TypeNameNode();
    ret.setLocation(ParseTreeHelper.loc(ctx));
    ret.setTypeName(TypeNameNode_typeName(ParseTreeHelper.terminalNode(ctx)));
    ret.normalize();
    ret.validate();
    return ret;
}
      
      public io.nop.xlang.ast.TypeNode visitTypeNode_unionOrIntersection(TypeNode_unionOrIntersectionContext ctx){
        
            return (io.nop.xlang.ast.TypeNode)ctx.accept(this);
          
      }
            
      public io.nop.xlang.ast.TypeParameterNode visitTypeParameterNode(TypeParameterNodeContext ctx){
          io.nop.xlang.ast.TypeParameterNode ret = new io.nop.xlang.ast.TypeParameterNode();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((visitIdentifier(ctx.name)));
            }
            if(ctx.upperBound != null){
               ret.setUpperBound((visitNamedTypeNode(ctx.upperBound)));
            }
            if(ctx.lowerBound != null){
               ret.setLowerBound((visitNamedTypeNode(ctx.lowerBound)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.TypeParameterNode> buildTypeParameters_(TypeParameters_Context ctx){
    java.util.List<io.nop.xlang.ast.TypeParameterNode> list = new ArrayList<>();
    List<TypeParameterNodeContext> elms = ctx.typeParameterNode();
    if(elms != null){
      for(TypeParameterNodeContext elm: elms){
         list.add(visitTypeParameterNode(elm));
      }
    }
    return list;
}
      
public java.util.List<io.nop.xlang.ast.NamedTypeNode> buildUnionTypeDef_(UnionTypeDef_Context ctx){
    java.util.List<io.nop.xlang.ast.NamedTypeNode> list = new ArrayList<>();
    List<NamedTypeNodeContext> elms = ctx.namedTypeNode();
    if(elms != null){
      for(NamedTypeNodeContext elm: elms){
         list.add(visitNamedTypeNode(elm));
      }
    }
    return list;
}
      
      public io.nop.xlang.ast.VariableDeclaration visitVariableDeclaration(VariableDeclarationContext ctx){
          io.nop.xlang.ast.VariableDeclaration ret = new io.nop.xlang.ast.VariableDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.kind != null){
               ret.setKind((VariableDeclaration_kind(ctx.kind)));
            }
            if(ctx.declarators != null){
               ret.setDeclarators((buildVariableDeclarators_(ctx.declarators)));
            }else{
               ret.setDeclarators(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.VariableDeclaration visitVariableDeclaration_const(VariableDeclaration_constContext ctx){
          io.nop.xlang.ast.VariableDeclaration ret = new io.nop.xlang.ast.VariableDeclaration();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.kind != null){
               ret.setKind((VariableDeclaration_kind(ctx.kind)));
            }
            if(ctx.declarators != null){
               ret.setDeclarators((buildVariableDeclarators_(ctx.declarators)));
            }else{
               ret.setDeclarators(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.xlang.ast.VariableDeclarator visitVariableDeclarator(VariableDeclaratorContext ctx){
          io.nop.xlang.ast.VariableDeclarator ret = new io.nop.xlang.ast.VariableDeclarator();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.id != null){
               ret.setId((visitAst_identifierOrPattern(ctx.id)));
            }
            if(ctx.varType != null){
               ret.setVarType((visitNamedTypeNode_annotation(ctx.varType)));
            }
            if(ctx.init != null){
               ret.setInit((visitExpression_initializer(ctx.init)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.xlang.ast.VariableDeclarator> buildVariableDeclarators_(VariableDeclarators_Context ctx){
    java.util.List<io.nop.xlang.ast.VariableDeclarator> list = new ArrayList<>();
    List<VariableDeclaratorContext> elms = ctx.variableDeclarator();
    if(elms != null){
      for(VariableDeclaratorContext elm: elms){
         list.add(visitVariableDeclarator(elm));
      }
    }
    return list;
}
      
  /**
   * rules: CallExpression
   */
  public abstract boolean CallExpression_optional(org.antlr.v4.runtime.Token token);

  /**
   * rules: ChainExpression
   */
  public abstract boolean ChainExpression_optional(org.antlr.v4.runtime.Token token);

  /**
   * rules: functionArgTypeDef
   */
  public abstract boolean FunctionArgTypeDef_optional(org.antlr.v4.runtime.Token token);

  /**
   * rules: MemberExpression_dot,MemberExpression_index
   */
  public abstract boolean MemberExpression_optional(org.antlr.v4.runtime.Token token);

  /**
   * rules: propertyTypeDef
   */
  public abstract boolean PropertyTypeDef_optional(org.antlr.v4.runtime.Token token);

  /**
   * rules: propertyTypeDef
   */
  public abstract boolean PropertyTypeDef_readonly(org.antlr.v4.runtime.Token token);

  /**
   * rules: VariableDeclaration_for,VariableDeclaration_init,variableDeclaration
   */
  public abstract io.nop.xlang.ast.VariableKind VariableDeclaration_kind(ParseTree node);

  /**
   * rules: variableDeclaration_const
   */
  public abstract io.nop.xlang.ast.VariableKind VariableDeclaration_kind(org.antlr.v4.runtime.Token token);

  /**
   * rules: assignmentExpression
   */
  public abstract io.nop.xlang.ast.XLangOperator AssignmentExpression_operator(ParseTree node);

  /**
   * rules: assignmentExpression_init
   */
  public abstract io.nop.xlang.ast.XLangOperator AssignmentExpression_operator(org.antlr.v4.runtime.Token token);

  /**
   * rules: BinaryExpression
   */
  public abstract io.nop.xlang.ast.XLangOperator BinaryExpression_operator(org.antlr.v4.runtime.Token token);

  /**
   * rules: UnaryExpression
   */
  public abstract io.nop.xlang.ast.XLangOperator UnaryExpression_operator(org.antlr.v4.runtime.Token token);

  /**
   * rules: UpdateExpression
   */
  public abstract io.nop.xlang.ast.XLangOperator UpdateExpression_operator(org.antlr.v4.runtime.Token token);

  /**
   * rules: literal,literal_numeric,literal_string
   */
  public abstract java.lang.Object Literal_value(ParseTree node);

  /**
   * rules: templateStringLiteral
   */
  public abstract java.lang.Object TemplateStringLiteral_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: identifier,identifier_ex
   */
  public abstract java.lang.String Identifier_name(ParseTree node);

  /**
   * rules: parameterizedTypeNode
   */
  public abstract java.lang.String ParameterizedTypeNode_typeName(ParseTree node);

  /**
   * rules: PropertyBinding_full
   */
  public abstract java.lang.String PropertyBinding_propName(ParseTree node);

  /**
   * rules: propertyTypeDef
   */
  public abstract java.lang.String PropertyTypeDef_name(ParseTree node);

  /**
   * rules: qualifiedName
   */
  public abstract java.lang.String QualifiedName_name(ParseTree node);

  /**
   * rules: TypeNameNode_named,typeNameNode_predefined
   */
  public abstract java.lang.String TypeNameNode_typeName(ParseTree node);

}
 // resume CPD analysis - CPD-ON
