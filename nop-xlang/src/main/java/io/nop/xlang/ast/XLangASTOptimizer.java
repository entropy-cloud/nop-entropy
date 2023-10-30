//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast;

import io.nop.core.lang.ast.optimize.AbstractOptimizer;

// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class XLangASTOptimizer<C> extends AbstractOptimizer<XLangASTNode,C>{

    public XLangASTNode optimize(XLangASTNode node,C context){
        switch(node.getASTKind()){
        
                case Program:
                return optimizeProgram((Program)node,context);
            
                case Identifier:
                return optimizeIdentifier((Identifier)node,context);
            
                case Literal:
                return optimizeLiteral((Literal)node,context);
            
                case TemplateStringLiteral:
                return optimizeTemplateStringLiteral((TemplateStringLiteral)node,context);
            
                case RegExpLiteral:
                return optimizeRegExpLiteral((RegExpLiteral)node,context);
            
                case BlockStatement:
                return optimizeBlockStatement((BlockStatement)node,context);
            
                case EmptyStatement:
                return optimizeEmptyStatement((EmptyStatement)node,context);
            
                case ReturnStatement:
                return optimizeReturnStatement((ReturnStatement)node,context);
            
                case BreakStatement:
                return optimizeBreakStatement((BreakStatement)node,context);
            
                case ContinueStatement:
                return optimizeContinueStatement((ContinueStatement)node,context);
            
                case IfStatement:
                return optimizeIfStatement((IfStatement)node,context);
            
                case SwitchStatement:
                return optimizeSwitchStatement((SwitchStatement)node,context);
            
                case SwitchCase:
                return optimizeSwitchCase((SwitchCase)node,context);
            
                case ThrowStatement:
                return optimizeThrowStatement((ThrowStatement)node,context);
            
                case TryStatement:
                return optimizeTryStatement((TryStatement)node,context);
            
                case CatchClause:
                return optimizeCatchClause((CatchClause)node,context);
            
                case WhileStatement:
                return optimizeWhileStatement((WhileStatement)node,context);
            
                case DoWhileStatement:
                return optimizeDoWhileStatement((DoWhileStatement)node,context);
            
                case VariableDeclarator:
                return optimizeVariableDeclarator((VariableDeclarator)node,context);
            
                case VariableDeclaration:
                return optimizeVariableDeclaration((VariableDeclaration)node,context);
            
                case ForStatement:
                return optimizeForStatement((ForStatement)node,context);
            
                case ForOfStatement:
                return optimizeForOfStatement((ForOfStatement)node,context);
            
                case ForRangeStatement:
                return optimizeForRangeStatement((ForRangeStatement)node,context);
            
                case ForInStatement:
                return optimizeForInStatement((ForInStatement)node,context);
            
                case DeleteStatement:
                return optimizeDeleteStatement((DeleteStatement)node,context);
            
                case ChainExpression:
                return optimizeChainExpression((ChainExpression)node,context);
            
                case ThisExpression:
                return optimizeThisExpression((ThisExpression)node,context);
            
                case SuperExpression:
                return optimizeSuperExpression((SuperExpression)node,context);
            
                case TemplateStringExpression:
                return optimizeTemplateStringExpression((TemplateStringExpression)node,context);
            
                case ArrayExpression:
                return optimizeArrayExpression((ArrayExpression)node,context);
            
                case ObjectExpression:
                return optimizeObjectExpression((ObjectExpression)node,context);
            
                case PropertyAssignment:
                return optimizePropertyAssignment((PropertyAssignment)node,context);
            
                case ParameterDeclaration:
                return optimizeParameterDeclaration((ParameterDeclaration)node,context);
            
                case FunctionDeclaration:
                return optimizeFunctionDeclaration((FunctionDeclaration)node,context);
            
                case ArrowFunctionExpression:
                return optimizeArrowFunctionExpression((ArrowFunctionExpression)node,context);
            
                case UnaryExpression:
                return optimizeUnaryExpression((UnaryExpression)node,context);
            
                case UpdateExpression:
                return optimizeUpdateExpression((UpdateExpression)node,context);
            
                case BinaryExpression:
                return optimizeBinaryExpression((BinaryExpression)node,context);
            
                case InExpression:
                return optimizeInExpression((InExpression)node,context);
            
                case ExpressionStatement:
                return optimizeExpressionStatement((ExpressionStatement)node,context);
            
                case AssignmentExpression:
                return optimizeAssignmentExpression((AssignmentExpression)node,context);
            
                case LogicalExpression:
                return optimizeLogicalExpression((LogicalExpression)node,context);
            
                case MemberExpression:
                return optimizeMemberExpression((MemberExpression)node,context);
            
                case EvalExpression:
                return optimizeEvalExpression((EvalExpression)node,context);
            
                case CallExpression:
                return optimizeCallExpression((CallExpression)node,context);
            
                case NewExpression:
                return optimizeNewExpression((NewExpression)node,context);
            
                case SpreadElement:
                return optimizeSpreadElement((SpreadElement)node,context);
            
                case SequenceExpression:
                return optimizeSequenceExpression((SequenceExpression)node,context);
            
                case ConcatExpression:
                return optimizeConcatExpression((ConcatExpression)node,context);
            
                case TemplateExpression:
                return optimizeTemplateExpression((TemplateExpression)node,context);
            
                case BraceExpression:
                return optimizeBraceExpression((BraceExpression)node,context);
            
                case ObjectBinding:
                return optimizeObjectBinding((ObjectBinding)node,context);
            
                case PropertyBinding:
                return optimizePropertyBinding((PropertyBinding)node,context);
            
                case RestBinding:
                return optimizeRestBinding((RestBinding)node,context);
            
                case ArrayBinding:
                return optimizeArrayBinding((ArrayBinding)node,context);
            
                case ArrayElementBinding:
                return optimizeArrayElementBinding((ArrayElementBinding)node,context);
            
                case ExportDeclaration:
                return optimizeExportDeclaration((ExportDeclaration)node,context);
            
                case ExportNamedDeclaration:
                return optimizeExportNamedDeclaration((ExportNamedDeclaration)node,context);
            
                case ExportAllDeclaration:
                return optimizeExportAllDeclaration((ExportAllDeclaration)node,context);
            
                case ExportSpecifier:
                return optimizeExportSpecifier((ExportSpecifier)node,context);
            
                case ImportDeclaration:
                return optimizeImportDeclaration((ImportDeclaration)node,context);
            
                case ImportAsDeclaration:
                return optimizeImportAsDeclaration((ImportAsDeclaration)node,context);
            
                case ImportSpecifier:
                return optimizeImportSpecifier((ImportSpecifier)node,context);
            
                case ImportDefaultSpecifier:
                return optimizeImportDefaultSpecifier((ImportDefaultSpecifier)node,context);
            
                case ImportNamespaceSpecifier:
                return optimizeImportNamespaceSpecifier((ImportNamespaceSpecifier)node,context);
            
                case AwaitExpression:
                return optimizeAwaitExpression((AwaitExpression)node,context);
            
                case Decorators:
                return optimizeDecorators((Decorators)node,context);
            
                case QualifiedName:
                return optimizeQualifiedName((QualifiedName)node,context);
            
                case Decorator:
                return optimizeDecorator((Decorator)node,context);
            
                case MetaObject:
                return optimizeMetaObject((MetaObject)node,context);
            
                case MetaProperty:
                return optimizeMetaProperty((MetaProperty)node,context);
            
                case MetaArray:
                return optimizeMetaArray((MetaArray)node,context);
            
                case UsingStatement:
                return optimizeUsingStatement((UsingStatement)node,context);
            
                case MacroExpression:
                return optimizeMacroExpression((MacroExpression)node,context);
            
                case TextOutputExpression:
                return optimizeTextOutputExpression((TextOutputExpression)node,context);
            
                case EscapeOutputExpression:
                return optimizeEscapeOutputExpression((EscapeOutputExpression)node,context);
            
                case CollectOutputExpression:
                return optimizeCollectOutputExpression((CollectOutputExpression)node,context);
            
                case CompareOpExpression:
                return optimizeCompareOpExpression((CompareOpExpression)node,context);
            
                case AssertOpExpression:
                return optimizeAssertOpExpression((AssertOpExpression)node,context);
            
                case BetweenOpExpression:
                return optimizeBetweenOpExpression((BetweenOpExpression)node,context);
            
                case GenNodeExpression:
                return optimizeGenNodeExpression((GenNodeExpression)node,context);
            
                case GenNodeAttrExpression:
                return optimizeGenNodeAttrExpression((GenNodeAttrExpression)node,context);
            
                case OutputXmlAttrExpression:
                return optimizeOutputXmlAttrExpression((OutputXmlAttrExpression)node,context);
            
                case OutputXmlExtAttrsExpression:
                return optimizeOutputXmlExtAttrsExpression((OutputXmlExtAttrsExpression)node,context);
            
                case TypeOfExpression:
                return optimizeTypeOfExpression((TypeOfExpression)node,context);
            
                case InstanceOfExpression:
                return optimizeInstanceOfExpression((InstanceOfExpression)node,context);
            
                case CastExpression:
                return optimizeCastExpression((CastExpression)node,context);
            
                case ArrayTypeNode:
                return optimizeArrayTypeNode((ArrayTypeNode)node,context);
            
                case ParameterizedTypeNode:
                return optimizeParameterizedTypeNode((ParameterizedTypeNode)node,context);
            
                case TypeNameNode:
                return optimizeTypeNameNode((TypeNameNode)node,context);
            
                case UnionTypeDef:
                return optimizeUnionTypeDef((UnionTypeDef)node,context);
            
                case IntersectionTypeDef:
                return optimizeIntersectionTypeDef((IntersectionTypeDef)node,context);
            
                case ObjectTypeDef:
                return optimizeObjectTypeDef((ObjectTypeDef)node,context);
            
                case PropertyTypeDef:
                return optimizePropertyTypeDef((PropertyTypeDef)node,context);
            
                case TupleTypeDef:
                return optimizeTupleTypeDef((TupleTypeDef)node,context);
            
                case TypeParameterNode:
                return optimizeTypeParameterNode((TypeParameterNode)node,context);
            
                case TypeAliasDeclaration:
                return optimizeTypeAliasDeclaration((TypeAliasDeclaration)node,context);
            
                case FunctionTypeDef:
                return optimizeFunctionTypeDef((FunctionTypeDef)node,context);
            
                case FunctionArgTypeDef:
                return optimizeFunctionArgTypeDef((FunctionArgTypeDef)node,context);
            
                case EnumDeclaration:
                return optimizeEnumDeclaration((EnumDeclaration)node,context);
            
                case EnumMember:
                return optimizeEnumMember((EnumMember)node,context);
            
                case ClassDefinition:
                return optimizeClassDefinition((ClassDefinition)node,context);
            
                case FieldDeclaration:
                return optimizeFieldDeclaration((FieldDeclaration)node,context);
            
                case CustomExpression:
                return optimizeCustomExpression((CustomExpression)node,context);
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
	public XLangASTNode optimizeProgram(Program node, C context){
        Program ret = node;

        
                    if(node.getBody() != null){
                    
                            java.util.List<io.nop.xlang.ast.XLangASTNode> bodyOpt = optimizeList(node.getBody(),true, context);
                            if(bodyOpt != node.getBody()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(bodyOpt); ret = node.deepClone();}
                                ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeIdentifier(Identifier node, C context){
        Identifier ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeLiteral(Literal node, C context){
        Literal ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeTemplateStringLiteral(TemplateStringLiteral node, C context){
        TemplateStringLiteral ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeRegExpLiteral(RegExpLiteral node, C context){
        RegExpLiteral ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeBlockStatement(BlockStatement node, C context){
        BlockStatement ret = node;

        
                    if(node.getBody() != null){
                    
                            java.util.List<io.nop.xlang.ast.Expression> bodyOpt = optimizeList(node.getBody(),true, context);
                            if(bodyOpt != node.getBody()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(bodyOpt); ret = node.deepClone();}
                                ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeEmptyStatement(EmptyStatement node, C context){
        EmptyStatement ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeReturnStatement(ReturnStatement node, C context){
        ReturnStatement ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeBreakStatement(BreakStatement node, C context){
        BreakStatement ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeContinueStatement(ContinueStatement node, C context){
        ContinueStatement ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeIfStatement(IfStatement node, C context){
        IfStatement ret = node;

        
                    if(node.getTest() != null){
                    
                            io.nop.xlang.ast.Expression testOpt = (io.nop.xlang.ast.Expression)optimize(node.getTest(),context);
                            if(testOpt != node.getTest()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { testOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTest(testOpt);
                            }
                        
                    }
                
                    if(node.getConsequent() != null){
                    
                            io.nop.xlang.ast.Expression consequentOpt = (io.nop.xlang.ast.Expression)optimize(node.getConsequent(),context);
                            if(consequentOpt != node.getConsequent()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { consequentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setConsequent(consequentOpt);
                            }
                        
                    }
                
                    if(node.getAlternate() != null){
                    
                            io.nop.xlang.ast.Expression alternateOpt = (io.nop.xlang.ast.Expression)optimize(node.getAlternate(),context);
                            if(alternateOpt != node.getAlternate()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { alternateOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setAlternate(alternateOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeSwitchStatement(SwitchStatement node, C context){
        SwitchStatement ret = node;

        
                    if(node.getDiscriminant() != null){
                    
                            io.nop.xlang.ast.Expression discriminantOpt = (io.nop.xlang.ast.Expression)optimize(node.getDiscriminant(),context);
                            if(discriminantOpt != node.getDiscriminant()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { discriminantOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDiscriminant(discriminantOpt);
                            }
                        
                    }
                
                    if(node.getCases() != null){
                    
                            java.util.List<io.nop.xlang.ast.SwitchCase> casesOpt = optimizeList(node.getCases(),true, context);
                            if(casesOpt != node.getCases()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(casesOpt); ret = node.deepClone();}
                                ret.setCases(casesOpt);
                            }
                        
                    }
                
                    if(node.getDefaultCase() != null){
                    
                            io.nop.xlang.ast.Expression defaultCaseOpt = (io.nop.xlang.ast.Expression)optimize(node.getDefaultCase(),context);
                            if(defaultCaseOpt != node.getDefaultCase()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { defaultCaseOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDefaultCase(defaultCaseOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeSwitchCase(SwitchCase node, C context){
        SwitchCase ret = node;

        
                    if(node.getTest() != null){
                    
                            io.nop.xlang.ast.Expression testOpt = (io.nop.xlang.ast.Expression)optimize(node.getTest(),context);
                            if(testOpt != node.getTest()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { testOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTest(testOpt);
                            }
                        
                    }
                
                    if(node.getConsequent() != null){
                    
                            io.nop.xlang.ast.Expression consequentOpt = (io.nop.xlang.ast.Expression)optimize(node.getConsequent(),context);
                            if(consequentOpt != node.getConsequent()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { consequentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setConsequent(consequentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeThrowStatement(ThrowStatement node, C context){
        ThrowStatement ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTryStatement(TryStatement node, C context){
        TryStatement ret = node;

        
                    if(node.getBlock() != null){
                    
                            io.nop.xlang.ast.Expression blockOpt = (io.nop.xlang.ast.Expression)optimize(node.getBlock(),context);
                            if(blockOpt != node.getBlock()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { blockOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBlock(blockOpt);
                            }
                        
                    }
                
                    if(node.getCatchHandler() != null){
                    
                            io.nop.xlang.ast.CatchClause catchHandlerOpt = (io.nop.xlang.ast.CatchClause)optimize(node.getCatchHandler(),context);
                            if(catchHandlerOpt != node.getCatchHandler()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { catchHandlerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setCatchHandler(catchHandlerOpt);
                            }
                        
                    }
                
                    if(node.getFinalizer() != null){
                    
                            io.nop.xlang.ast.Expression finalizerOpt = (io.nop.xlang.ast.Expression)optimize(node.getFinalizer(),context);
                            if(finalizerOpt != node.getFinalizer()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { finalizerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setFinalizer(finalizerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeCatchClause(CatchClause node, C context){
        CatchClause ret = node;

        
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getVarType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode varTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getVarType(),context);
                            if(varTypeOpt != node.getVarType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { varTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setVarType(varTypeOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeWhileStatement(WhileStatement node, C context){
        WhileStatement ret = node;

        
                    if(node.getTest() != null){
                    
                            io.nop.xlang.ast.Expression testOpt = (io.nop.xlang.ast.Expression)optimize(node.getTest(),context);
                            if(testOpt != node.getTest()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { testOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTest(testOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeDoWhileStatement(DoWhileStatement node, C context){
        DoWhileStatement ret = node;

        
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
                    if(node.getTest() != null){
                    
                            io.nop.xlang.ast.Expression testOpt = (io.nop.xlang.ast.Expression)optimize(node.getTest(),context);
                            if(testOpt != node.getTest()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { testOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTest(testOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeVariableDeclarator(VariableDeclarator node, C context){
        VariableDeclarator ret = node;

        
                    if(node.getId() != null){
                    
                            io.nop.xlang.ast.XLangASTNode idOpt = (io.nop.xlang.ast.XLangASTNode)optimize(node.getId(),context);
                            if(idOpt != node.getId()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { idOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setId(idOpt);
                            }
                        
                    }
                
                    if(node.getVarType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode varTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getVarType(),context);
                            if(varTypeOpt != node.getVarType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { varTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setVarType(varTypeOpt);
                            }
                        
                    }
                
                    if(node.getInit() != null){
                    
                            io.nop.xlang.ast.Expression initOpt = (io.nop.xlang.ast.Expression)optimize(node.getInit(),context);
                            if(initOpt != node.getInit()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { initOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setInit(initOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeVariableDeclaration(VariableDeclaration node, C context){
        VariableDeclaration ret = node;

        
                    if(node.getDeclarators() != null){
                    
                            java.util.List<io.nop.xlang.ast.VariableDeclarator> declaratorsOpt = optimizeList(node.getDeclarators(),true, context);
                            if(declaratorsOpt != node.getDeclarators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(declaratorsOpt); ret = node.deepClone();}
                                ret.setDeclarators(declaratorsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeForStatement(ForStatement node, C context){
        ForStatement ret = node;

        
                    if(node.getInit() != null){
                    
                            io.nop.xlang.ast.Expression initOpt = (io.nop.xlang.ast.Expression)optimize(node.getInit(),context);
                            if(initOpt != node.getInit()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { initOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setInit(initOpt);
                            }
                        
                    }
                
                    if(node.getTest() != null){
                    
                            io.nop.xlang.ast.Expression testOpt = (io.nop.xlang.ast.Expression)optimize(node.getTest(),context);
                            if(testOpt != node.getTest()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { testOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTest(testOpt);
                            }
                        
                    }
                
                    if(node.getUpdate() != null){
                    
                            io.nop.xlang.ast.Expression updateOpt = (io.nop.xlang.ast.Expression)optimize(node.getUpdate(),context);
                            if(updateOpt != node.getUpdate()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { updateOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setUpdate(updateOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeForOfStatement(ForOfStatement node, C context){
        ForOfStatement ret = node;

        
                    if(node.getIndex() != null){
                    
                            io.nop.xlang.ast.Identifier indexOpt = (io.nop.xlang.ast.Identifier)optimize(node.getIndex(),context);
                            if(indexOpt != node.getIndex()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { indexOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setIndex(indexOpt);
                            }
                        
                    }
                
                    if(node.getLeft() != null){
                    
                            io.nop.xlang.ast.Expression leftOpt = (io.nop.xlang.ast.Expression)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.xlang.ast.Expression rightOpt = (io.nop.xlang.ast.Expression)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeForRangeStatement(ForRangeStatement node, C context){
        ForRangeStatement ret = node;

        
                    if(node.getVar() != null){
                    
                            io.nop.xlang.ast.Identifier varOpt = (io.nop.xlang.ast.Identifier)optimize(node.getVar(),context);
                            if(varOpt != node.getVar()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { varOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setVar(varOpt);
                            }
                        
                    }
                
                    if(node.getIndex() != null){
                    
                            io.nop.xlang.ast.Identifier indexOpt = (io.nop.xlang.ast.Identifier)optimize(node.getIndex(),context);
                            if(indexOpt != node.getIndex()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { indexOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setIndex(indexOpt);
                            }
                        
                    }
                
                    if(node.getBegin() != null){
                    
                            io.nop.xlang.ast.Expression beginOpt = (io.nop.xlang.ast.Expression)optimize(node.getBegin(),context);
                            if(beginOpt != node.getBegin()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { beginOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBegin(beginOpt);
                            }
                        
                    }
                
                    if(node.getEnd() != null){
                    
                            io.nop.xlang.ast.Expression endOpt = (io.nop.xlang.ast.Expression)optimize(node.getEnd(),context);
                            if(endOpt != node.getEnd()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { endOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setEnd(endOpt);
                            }
                        
                    }
                
                    if(node.getStep() != null){
                    
                            io.nop.xlang.ast.Expression stepOpt = (io.nop.xlang.ast.Expression)optimize(node.getStep(),context);
                            if(stepOpt != node.getStep()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { stepOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setStep(stepOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeForInStatement(ForInStatement node, C context){
        ForInStatement ret = node;

        
                    if(node.getIndex() != null){
                    
                            io.nop.xlang.ast.Identifier indexOpt = (io.nop.xlang.ast.Identifier)optimize(node.getIndex(),context);
                            if(indexOpt != node.getIndex()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { indexOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setIndex(indexOpt);
                            }
                        
                    }
                
                    if(node.getLeft() != null){
                    
                            io.nop.xlang.ast.Expression leftOpt = (io.nop.xlang.ast.Expression)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.xlang.ast.Expression rightOpt = (io.nop.xlang.ast.Expression)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeDeleteStatement(DeleteStatement node, C context){
        DeleteStatement ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeChainExpression(ChainExpression node, C context){
        ChainExpression ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.xlang.ast.Expression exprOpt = (io.nop.xlang.ast.Expression)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeThisExpression(ThisExpression node, C context){
        ThisExpression ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeSuperExpression(SuperExpression node, C context){
        SuperExpression ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeTemplateStringExpression(TemplateStringExpression node, C context){
        TemplateStringExpression ret = node;

        
                    if(node.getId() != null){
                    
                            io.nop.xlang.ast.Identifier idOpt = (io.nop.xlang.ast.Identifier)optimize(node.getId(),context);
                            if(idOpt != node.getId()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { idOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setId(idOpt);
                            }
                        
                    }
                
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.TemplateStringLiteral valueOpt = (io.nop.xlang.ast.TemplateStringLiteral)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeArrayExpression(ArrayExpression node, C context){
        ArrayExpression ret = node;

        
                    if(node.getElements() != null){
                    
                            java.util.List<io.nop.xlang.ast.XLangASTNode> elementsOpt = optimizeList(node.getElements(),true, context);
                            if(elementsOpt != node.getElements()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(elementsOpt); ret = node.deepClone();}
                                ret.setElements(elementsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeObjectExpression(ObjectExpression node, C context){
        ObjectExpression ret = node;

        
                    if(node.getProperties() != null){
                    
                            java.util.List<io.nop.xlang.ast.XLangASTNode> propertiesOpt = optimizeList(node.getProperties(),true, context);
                            if(propertiesOpt != node.getProperties()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(propertiesOpt); ret = node.deepClone();}
                                ret.setProperties(propertiesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizePropertyAssignment(PropertyAssignment node, C context){
        PropertyAssignment ret = node;

        
                    if(node.getKey() != null){
                    
                            io.nop.xlang.ast.Expression keyOpt = (io.nop.xlang.ast.Expression)optimize(node.getKey(),context);
                            if(keyOpt != node.getKey()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { keyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setKey(keyOpt);
                            }
                        
                    }
                
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Expression valueOpt = (io.nop.xlang.ast.Expression)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeParameterDeclaration(ParameterDeclaration node, C context){
        ParameterDeclaration ret = node;

        
                    if(node.getDecorators() != null){
                    
                            io.nop.xlang.ast.Decorators decoratorsOpt = (io.nop.xlang.ast.Decorators)optimize(node.getDecorators(),context);
                            if(decoratorsOpt != node.getDecorators()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { decoratorsOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.XLangASTNode nameOpt = (io.nop.xlang.ast.XLangASTNode)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode typeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { typeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setType(typeOpt);
                            }
                        
                    }
                
                    if(node.getInitializer() != null){
                    
                            io.nop.xlang.ast.Expression initializerOpt = (io.nop.xlang.ast.Expression)optimize(node.getInitializer(),context);
                            if(initializerOpt != node.getInitializer()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { initializerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setInitializer(initializerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeFunctionDeclaration(FunctionDeclaration node, C context){
        FunctionDeclaration ret = node;

        
                    if(node.getDecorators() != null){
                    
                            io.nop.xlang.ast.Decorators decoratorsOpt = (io.nop.xlang.ast.Decorators)optimize(node.getDecorators(),context);
                            if(decoratorsOpt != node.getDecorators()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { decoratorsOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getParams() != null){
                    
                            java.util.List<io.nop.xlang.ast.ParameterDeclaration> paramsOpt = optimizeList(node.getParams(),true, context);
                            if(paramsOpt != node.getParams()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(paramsOpt); ret = node.deepClone();}
                                ret.setParams(paramsOpt);
                            }
                        
                    }
                
                    if(node.getReturnType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode returnTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getReturnType(),context);
                            if(returnTypeOpt != node.getReturnType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { returnTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setReturnType(returnTypeOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeArrowFunctionExpression(ArrowFunctionExpression node, C context){
        ArrowFunctionExpression ret = node;

        
                    if(node.getParams() != null){
                    
                            java.util.List<io.nop.xlang.ast.ParameterDeclaration> paramsOpt = optimizeList(node.getParams(),true, context);
                            if(paramsOpt != node.getParams()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(paramsOpt); ret = node.deepClone();}
                                ret.setParams(paramsOpt);
                            }
                        
                    }
                
                    if(node.getReturnType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode returnTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getReturnType(),context);
                            if(returnTypeOpt != node.getReturnType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { returnTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setReturnType(returnTypeOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeUnaryExpression(UnaryExpression node, C context){
        UnaryExpression ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeUpdateExpression(UpdateExpression node, C context){
        UpdateExpression ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeBinaryExpression(BinaryExpression node, C context){
        BinaryExpression ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.xlang.ast.Expression leftOpt = (io.nop.xlang.ast.Expression)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.xlang.ast.Expression rightOpt = (io.nop.xlang.ast.Expression)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeInExpression(InExpression node, C context){
        InExpression ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.xlang.ast.Expression leftOpt = (io.nop.xlang.ast.Expression)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.xlang.ast.Expression rightOpt = (io.nop.xlang.ast.Expression)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeExpressionStatement(ExpressionStatement node, C context){
        ExpressionStatement ret = node;

        
                    if(node.getExpression() != null){
                    
                            io.nop.xlang.ast.Expression expressionOpt = (io.nop.xlang.ast.Expression)optimize(node.getExpression(),context);
                            if(expressionOpt != node.getExpression()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { expressionOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpression(expressionOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeAssignmentExpression(AssignmentExpression node, C context){
        AssignmentExpression ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.xlang.ast.Expression leftOpt = (io.nop.xlang.ast.Expression)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.xlang.ast.Expression rightOpt = (io.nop.xlang.ast.Expression)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeLogicalExpression(LogicalExpression node, C context){
        LogicalExpression ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.xlang.ast.Expression leftOpt = (io.nop.xlang.ast.Expression)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.xlang.ast.Expression rightOpt = (io.nop.xlang.ast.Expression)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeMemberExpression(MemberExpression node, C context){
        MemberExpression ret = node;

        
                    if(node.getObject() != null){
                    
                            io.nop.xlang.ast.Expression objectOpt = (io.nop.xlang.ast.Expression)optimize(node.getObject(),context);
                            if(objectOpt != node.getObject()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { objectOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setObject(objectOpt);
                            }
                        
                    }
                
                    if(node.getProperty() != null){
                    
                            io.nop.xlang.ast.Expression propertyOpt = (io.nop.xlang.ast.Expression)optimize(node.getProperty(),context);
                            if(propertyOpt != node.getProperty()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { propertyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setProperty(propertyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeEvalExpression(EvalExpression node, C context){
        EvalExpression ret = node;

        
                    if(node.getSource() != null){
                    
                            io.nop.xlang.ast.Literal sourceOpt = (io.nop.xlang.ast.Literal)optimize(node.getSource(),context);
                            if(sourceOpt != node.getSource()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { sourceOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSource(sourceOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeCallExpression(CallExpression node, C context){
        CallExpression ret = node;

        
                    if(node.getCallee() != null){
                    
                            io.nop.xlang.ast.Expression calleeOpt = (io.nop.xlang.ast.Expression)optimize(node.getCallee(),context);
                            if(calleeOpt != node.getCallee()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { calleeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setCallee(calleeOpt);
                            }
                        
                    }
                
                    if(node.getArguments() != null){
                    
                            java.util.List<io.nop.xlang.ast.Expression> argumentsOpt = optimizeList(node.getArguments(),true, context);
                            if(argumentsOpt != node.getArguments()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(argumentsOpt); ret = node.deepClone();}
                                ret.setArguments(argumentsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeNewExpression(NewExpression node, C context){
        NewExpression ret = node;

        
                    if(node.getCallee() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode calleeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getCallee(),context);
                            if(calleeOpt != node.getCallee()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { calleeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setCallee(calleeOpt);
                            }
                        
                    }
                
                    if(node.getArguments() != null){
                    
                            java.util.List<io.nop.xlang.ast.Expression> argumentsOpt = optimizeList(node.getArguments(),true, context);
                            if(argumentsOpt != node.getArguments()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(argumentsOpt); ret = node.deepClone();}
                                ret.setArguments(argumentsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeSpreadElement(SpreadElement node, C context){
        SpreadElement ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeSequenceExpression(SequenceExpression node, C context){
        SequenceExpression ret = node;

        
                    if(node.getExpressions() != null){
                    
                            java.util.List<io.nop.xlang.ast.Expression> expressionsOpt = optimizeList(node.getExpressions(),true, context);
                            if(expressionsOpt != node.getExpressions()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(expressionsOpt); ret = node.deepClone();}
                                ret.setExpressions(expressionsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeConcatExpression(ConcatExpression node, C context){
        ConcatExpression ret = node;

        
                    if(node.getExpressions() != null){
                    
                            java.util.List<io.nop.xlang.ast.Expression> expressionsOpt = optimizeList(node.getExpressions(),true, context);
                            if(expressionsOpt != node.getExpressions()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(expressionsOpt); ret = node.deepClone();}
                                ret.setExpressions(expressionsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTemplateExpression(TemplateExpression node, C context){
        TemplateExpression ret = node;

        
                    if(node.getExpressions() != null){
                    
                            java.util.List<io.nop.xlang.ast.Expression> expressionsOpt = optimizeList(node.getExpressions(),true, context);
                            if(expressionsOpt != node.getExpressions()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(expressionsOpt); ret = node.deepClone();}
                                ret.setExpressions(expressionsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeBraceExpression(BraceExpression node, C context){
        BraceExpression ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.xlang.ast.Expression exprOpt = (io.nop.xlang.ast.Expression)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeObjectBinding(ObjectBinding node, C context){
        ObjectBinding ret = node;

        
                    if(node.getProperties() != null){
                    
                            java.util.List<io.nop.xlang.ast.PropertyBinding> propertiesOpt = optimizeList(node.getProperties(),true, context);
                            if(propertiesOpt != node.getProperties()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(propertiesOpt); ret = node.deepClone();}
                                ret.setProperties(propertiesOpt);
                            }
                        
                    }
                
                    if(node.getRestBinding() != null){
                    
                            io.nop.xlang.ast.RestBinding restBindingOpt = (io.nop.xlang.ast.RestBinding)optimize(node.getRestBinding(),context);
                            if(restBindingOpt != node.getRestBinding()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { restBindingOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRestBinding(restBindingOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizePropertyBinding(PropertyBinding node, C context){
        PropertyBinding ret = node;

        
                    if(node.getIdentifier() != null){
                    
                            io.nop.xlang.ast.Identifier identifierOpt = (io.nop.xlang.ast.Identifier)optimize(node.getIdentifier(),context);
                            if(identifierOpt != node.getIdentifier()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { identifierOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setIdentifier(identifierOpt);
                            }
                        
                    }
                
                    if(node.getInitializer() != null){
                    
                            io.nop.xlang.ast.Expression initializerOpt = (io.nop.xlang.ast.Expression)optimize(node.getInitializer(),context);
                            if(initializerOpt != node.getInitializer()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { initializerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setInitializer(initializerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeRestBinding(RestBinding node, C context){
        RestBinding ret = node;

        
                    if(node.getIdentifier() != null){
                    
                            io.nop.xlang.ast.Identifier identifierOpt = (io.nop.xlang.ast.Identifier)optimize(node.getIdentifier(),context);
                            if(identifierOpt != node.getIdentifier()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { identifierOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setIdentifier(identifierOpt);
                            }
                        
                    }
                
                    if(node.getInitializer() != null){
                    
                            io.nop.xlang.ast.Expression initializerOpt = (io.nop.xlang.ast.Expression)optimize(node.getInitializer(),context);
                            if(initializerOpt != node.getInitializer()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { initializerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setInitializer(initializerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeArrayBinding(ArrayBinding node, C context){
        ArrayBinding ret = node;

        
                    if(node.getElements() != null){
                    
                            java.util.List<io.nop.xlang.ast.ArrayElementBinding> elementsOpt = optimizeList(node.getElements(),true, context);
                            if(elementsOpt != node.getElements()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(elementsOpt); ret = node.deepClone();}
                                ret.setElements(elementsOpt);
                            }
                        
                    }
                
                    if(node.getRestBinding() != null){
                    
                            io.nop.xlang.ast.RestBinding restBindingOpt = (io.nop.xlang.ast.RestBinding)optimize(node.getRestBinding(),context);
                            if(restBindingOpt != node.getRestBinding()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { restBindingOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRestBinding(restBindingOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeArrayElementBinding(ArrayElementBinding node, C context){
        ArrayElementBinding ret = node;

        
                    if(node.getIdentifier() != null){
                    
                            io.nop.xlang.ast.Identifier identifierOpt = (io.nop.xlang.ast.Identifier)optimize(node.getIdentifier(),context);
                            if(identifierOpt != node.getIdentifier()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { identifierOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setIdentifier(identifierOpt);
                            }
                        
                    }
                
                    if(node.getInitializer() != null){
                    
                            io.nop.xlang.ast.Expression initializerOpt = (io.nop.xlang.ast.Expression)optimize(node.getInitializer(),context);
                            if(initializerOpt != node.getInitializer()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { initializerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setInitializer(initializerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeExportDeclaration(ExportDeclaration node, C context){
        ExportDeclaration ret = node;

        
                    if(node.getDeclaration() != null){
                    
                            io.nop.xlang.ast.Declaration declarationOpt = (io.nop.xlang.ast.Declaration)optimize(node.getDeclaration(),context);
                            if(declarationOpt != node.getDeclaration()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { declarationOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDeclaration(declarationOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeExportNamedDeclaration(ExportNamedDeclaration node, C context){
        ExportNamedDeclaration ret = node;

        
                    if(node.getSpecifiers() != null){
                    
                            java.util.List<io.nop.xlang.ast.ExportSpecifier> specifiersOpt = optimizeList(node.getSpecifiers(),true, context);
                            if(specifiersOpt != node.getSpecifiers()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(specifiersOpt); ret = node.deepClone();}
                                ret.setSpecifiers(specifiersOpt);
                            }
                        
                    }
                
                    if(node.getSource() != null){
                    
                            io.nop.xlang.ast.Literal sourceOpt = (io.nop.xlang.ast.Literal)optimize(node.getSource(),context);
                            if(sourceOpt != node.getSource()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { sourceOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSource(sourceOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeExportAllDeclaration(ExportAllDeclaration node, C context){
        ExportAllDeclaration ret = node;

        
                    if(node.getSource() != null){
                    
                            io.nop.xlang.ast.Literal sourceOpt = (io.nop.xlang.ast.Literal)optimize(node.getSource(),context);
                            if(sourceOpt != node.getSource()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { sourceOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSource(sourceOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeExportSpecifier(ExportSpecifier node, C context){
        ExportSpecifier ret = node;

        
                    if(node.getLocal() != null){
                    
                            io.nop.xlang.ast.Identifier localOpt = (io.nop.xlang.ast.Identifier)optimize(node.getLocal(),context);
                            if(localOpt != node.getLocal()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { localOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLocal(localOpt);
                            }
                        
                    }
                
                    if(node.getExported() != null){
                    
                            io.nop.xlang.ast.Identifier exportedOpt = (io.nop.xlang.ast.Identifier)optimize(node.getExported(),context);
                            if(exportedOpt != node.getExported()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exportedOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExported(exportedOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeImportDeclaration(ImportDeclaration node, C context){
        ImportDeclaration ret = node;

        
                    if(node.getSpecifiers() != null){
                    
                            java.util.List<io.nop.xlang.ast.ModuleSpecifier> specifiersOpt = optimizeList(node.getSpecifiers(),true, context);
                            if(specifiersOpt != node.getSpecifiers()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(specifiersOpt); ret = node.deepClone();}
                                ret.setSpecifiers(specifiersOpt);
                            }
                        
                    }
                
                    if(node.getSource() != null){
                    
                            io.nop.xlang.ast.Literal sourceOpt = (io.nop.xlang.ast.Literal)optimize(node.getSource(),context);
                            if(sourceOpt != node.getSource()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { sourceOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSource(sourceOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeImportAsDeclaration(ImportAsDeclaration node, C context){
        ImportAsDeclaration ret = node;

        
                    if(node.getSource() != null){
                    
                            io.nop.xlang.ast.XLangASTNode sourceOpt = (io.nop.xlang.ast.XLangASTNode)optimize(node.getSource(),context);
                            if(sourceOpt != node.getSource()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { sourceOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setSource(sourceOpt);
                            }
                        
                    }
                
                    if(node.getLocal() != null){
                    
                            io.nop.xlang.ast.Identifier localOpt = (io.nop.xlang.ast.Identifier)optimize(node.getLocal(),context);
                            if(localOpt != node.getLocal()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { localOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLocal(localOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeImportSpecifier(ImportSpecifier node, C context){
        ImportSpecifier ret = node;

        
                    if(node.getLocal() != null){
                    
                            io.nop.xlang.ast.Identifier localOpt = (io.nop.xlang.ast.Identifier)optimize(node.getLocal(),context);
                            if(localOpt != node.getLocal()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { localOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLocal(localOpt);
                            }
                        
                    }
                
                    if(node.getImported() != null){
                    
                            io.nop.xlang.ast.Identifier importedOpt = (io.nop.xlang.ast.Identifier)optimize(node.getImported(),context);
                            if(importedOpt != node.getImported()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { importedOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setImported(importedOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeImportDefaultSpecifier(ImportDefaultSpecifier node, C context){
        ImportDefaultSpecifier ret = node;

        
                    if(node.getLocal() != null){
                    
                            io.nop.xlang.ast.Identifier localOpt = (io.nop.xlang.ast.Identifier)optimize(node.getLocal(),context);
                            if(localOpt != node.getLocal()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { localOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLocal(localOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeImportNamespaceSpecifier(ImportNamespaceSpecifier node, C context){
        ImportNamespaceSpecifier ret = node;

        
                    if(node.getLocal() != null){
                    
                            io.nop.xlang.ast.Identifier localOpt = (io.nop.xlang.ast.Identifier)optimize(node.getLocal(),context);
                            if(localOpt != node.getLocal()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { localOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLocal(localOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeAwaitExpression(AwaitExpression node, C context){
        AwaitExpression ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeDecorators(Decorators node, C context){
        Decorators ret = node;

        
                    if(node.getDecorators() != null){
                    
                            java.util.List<io.nop.xlang.ast.Decorator> decoratorsOpt = optimizeList(node.getDecorators(),true, context);
                            if(decoratorsOpt != node.getDecorators()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(decoratorsOpt); ret = node.deepClone();}
                                ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeQualifiedName(QualifiedName node, C context){
        QualifiedName ret = node;

        
                    if(node.getNext() != null){
                    
                            io.nop.xlang.ast.QualifiedName nextOpt = (io.nop.xlang.ast.QualifiedName)optimize(node.getNext(),context);
                            if(nextOpt != node.getNext()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nextOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setNext(nextOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeDecorator(Decorator node, C context){
        Decorator ret = node;

        
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.QualifiedName nameOpt = (io.nop.xlang.ast.QualifiedName)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.MetaObject valueOpt = (io.nop.xlang.ast.MetaObject)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeMetaObject(MetaObject node, C context){
        MetaObject ret = node;

        
                    if(node.getProperties() != null){
                    
                            java.util.List<io.nop.xlang.ast.MetaProperty> propertiesOpt = optimizeList(node.getProperties(),true, context);
                            if(propertiesOpt != node.getProperties()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(propertiesOpt); ret = node.deepClone();}
                                ret.setProperties(propertiesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeMetaProperty(MetaProperty node, C context){
        MetaProperty ret = node;

        
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.XLangASTNode valueOpt = (io.nop.xlang.ast.XLangASTNode)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeMetaArray(MetaArray node, C context){
        MetaArray ret = node;

        
                    if(node.getElements() != null){
                    
                            java.util.List<io.nop.xlang.ast.XLangASTNode> elementsOpt = optimizeList(node.getElements(),true, context);
                            if(elementsOpt != node.getElements()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(elementsOpt); ret = node.deepClone();}
                                ret.setElements(elementsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeUsingStatement(UsingStatement node, C context){
        UsingStatement ret = node;

        
                    if(node.getVars() != null){
                    
                            io.nop.xlang.ast.VariableDeclaration varsOpt = (io.nop.xlang.ast.VariableDeclaration)optimize(node.getVars(),context);
                            if(varsOpt != node.getVars()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { varsOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setVars(varsOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeMacroExpression(MacroExpression node, C context){
        MacroExpression ret = node;

        
                    if(node.getExpr() != null){
                    
                            io.nop.xlang.ast.Expression exprOpt = (io.nop.xlang.ast.Expression)optimize(node.getExpr(),context);
                            if(exprOpt != node.getExpr()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { exprOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExpr(exprOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTextOutputExpression(TextOutputExpression node, C context){
        TextOutputExpression ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeEscapeOutputExpression(EscapeOutputExpression node, C context){
        EscapeOutputExpression ret = node;

        
                    if(node.getText() != null){
                    
                            io.nop.xlang.ast.Expression textOpt = (io.nop.xlang.ast.Expression)optimize(node.getText(),context);
                            if(textOpt != node.getText()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { textOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setText(textOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeCollectOutputExpression(CollectOutputExpression node, C context){
        CollectOutputExpression ret = node;

        
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeCompareOpExpression(CompareOpExpression node, C context){
        CompareOpExpression ret = node;

        
                    if(node.getLeft() != null){
                    
                            io.nop.xlang.ast.Expression leftOpt = (io.nop.xlang.ast.Expression)optimize(node.getLeft(),context);
                            if(leftOpt != node.getLeft()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { leftOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLeft(leftOpt);
                            }
                        
                    }
                
                    if(node.getRight() != null){
                    
                            io.nop.xlang.ast.Expression rightOpt = (io.nop.xlang.ast.Expression)optimize(node.getRight(),context);
                            if(rightOpt != node.getRight()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { rightOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRight(rightOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeAssertOpExpression(AssertOpExpression node, C context){
        AssertOpExpression ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Expression valueOpt = (io.nop.xlang.ast.Expression)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeBetweenOpExpression(BetweenOpExpression node, C context){
        BetweenOpExpression ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Expression valueOpt = (io.nop.xlang.ast.Expression)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
                    if(node.getMin() != null){
                    
                            io.nop.xlang.ast.Expression minOpt = (io.nop.xlang.ast.Expression)optimize(node.getMin(),context);
                            if(minOpt != node.getMin()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { minOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setMin(minOpt);
                            }
                        
                    }
                
                    if(node.getMax() != null){
                    
                            io.nop.xlang.ast.Expression maxOpt = (io.nop.xlang.ast.Expression)optimize(node.getMax(),context);
                            if(maxOpt != node.getMax()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { maxOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setMax(maxOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeGenNodeExpression(GenNodeExpression node, C context){
        GenNodeExpression ret = node;

        
                    if(node.getTagName() != null){
                    
                            io.nop.xlang.ast.Expression tagNameOpt = (io.nop.xlang.ast.Expression)optimize(node.getTagName(),context);
                            if(tagNameOpt != node.getTagName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { tagNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTagName(tagNameOpt);
                            }
                        
                    }
                
                    if(node.getExtAttrs() != null){
                    
                            io.nop.xlang.ast.Expression extAttrsOpt = (io.nop.xlang.ast.Expression)optimize(node.getExtAttrs(),context);
                            if(extAttrsOpt != node.getExtAttrs()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { extAttrsOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExtAttrs(extAttrsOpt);
                            }
                        
                    }
                
                    if(node.getAttrs() != null){
                    
                            java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> attrsOpt = optimizeList(node.getAttrs(),true, context);
                            if(attrsOpt != node.getAttrs()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(attrsOpt); ret = node.deepClone();}
                                ret.setAttrs(attrsOpt);
                            }
                        
                    }
                
                    if(node.getBody() != null){
                    
                            io.nop.xlang.ast.Expression bodyOpt = (io.nop.xlang.ast.Expression)optimize(node.getBody(),context);
                            if(bodyOpt != node.getBody()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { bodyOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setBody(bodyOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeGenNodeAttrExpression(GenNodeAttrExpression node, C context){
        GenNodeAttrExpression ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Expression valueOpt = (io.nop.xlang.ast.Expression)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeOutputXmlAttrExpression(OutputXmlAttrExpression node, C context){
        OutputXmlAttrExpression ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Expression valueOpt = (io.nop.xlang.ast.Expression)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeOutputXmlExtAttrsExpression(OutputXmlExtAttrsExpression node, C context){
        OutputXmlExtAttrsExpression ret = node;

        
                    if(node.getExtAttrs() != null){
                    
                            io.nop.xlang.ast.Expression extAttrsOpt = (io.nop.xlang.ast.Expression)optimize(node.getExtAttrs(),context);
                            if(extAttrsOpt != node.getExtAttrs()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { extAttrsOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExtAttrs(extAttrsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTypeOfExpression(TypeOfExpression node, C context){
        TypeOfExpression ret = node;

        
                    if(node.getArgument() != null){
                    
                            io.nop.xlang.ast.Expression argumentOpt = (io.nop.xlang.ast.Expression)optimize(node.getArgument(),context);
                            if(argumentOpt != node.getArgument()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argumentOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgument(argumentOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeInstanceOfExpression(InstanceOfExpression node, C context){
        InstanceOfExpression ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Expression valueOpt = (io.nop.xlang.ast.Expression)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
                    if(node.getRefType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode refTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getRefType(),context);
                            if(refTypeOpt != node.getRefType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { refTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setRefType(refTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeCastExpression(CastExpression node, C context){
        CastExpression ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Expression valueOpt = (io.nop.xlang.ast.Expression)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
                    if(node.getAsType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode asTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getAsType(),context);
                            if(asTypeOpt != node.getAsType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { asTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setAsType(asTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeArrayTypeNode(ArrayTypeNode node, C context){
        ArrayTypeNode ret = node;

        
                    if(node.getComponentType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode componentTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getComponentType(),context);
                            if(componentTypeOpt != node.getComponentType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { componentTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setComponentType(componentTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeParameterizedTypeNode(ParameterizedTypeNode node, C context){
        ParameterizedTypeNode ret = node;

        
                    if(node.getTypeArgs() != null){
                    
                            java.util.List<io.nop.xlang.ast.NamedTypeNode> typeArgsOpt = optimizeList(node.getTypeArgs(),true, context);
                            if(typeArgsOpt != node.getTypeArgs()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typeArgsOpt); ret = node.deepClone();}
                                ret.setTypeArgs(typeArgsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTypeNameNode(TypeNameNode node, C context){
        TypeNameNode ret = node;

        
		return ret;
	}
    
	public XLangASTNode optimizeUnionTypeDef(UnionTypeDef node, C context){
        UnionTypeDef ret = node;

        
                    if(node.getTypes() != null){
                    
                            java.util.List<io.nop.xlang.ast.NamedTypeNode> typesOpt = optimizeList(node.getTypes(),true, context);
                            if(typesOpt != node.getTypes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typesOpt); ret = node.deepClone();}
                                ret.setTypes(typesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeIntersectionTypeDef(IntersectionTypeDef node, C context){
        IntersectionTypeDef ret = node;

        
                    if(node.getTypes() != null){
                    
                            java.util.List<io.nop.xlang.ast.NamedTypeNode> typesOpt = optimizeList(node.getTypes(),true, context);
                            if(typesOpt != node.getTypes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typesOpt); ret = node.deepClone();}
                                ret.setTypes(typesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeObjectTypeDef(ObjectTypeDef node, C context){
        ObjectTypeDef ret = node;

        
                    if(node.getTypes() != null){
                    
                            java.util.List<io.nop.xlang.ast.PropertyTypeDef> typesOpt = optimizeList(node.getTypes(),true, context);
                            if(typesOpt != node.getTypes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typesOpt); ret = node.deepClone();}
                                ret.setTypes(typesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizePropertyTypeDef(PropertyTypeDef node, C context){
        PropertyTypeDef ret = node;

        
                    if(node.getValueType() != null){
                    
                            io.nop.xlang.ast.TypeNode valueTypeOpt = (io.nop.xlang.ast.TypeNode)optimize(node.getValueType(),context);
                            if(valueTypeOpt != node.getValueType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValueType(valueTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTupleTypeDef(TupleTypeDef node, C context){
        TupleTypeDef ret = node;

        
                    if(node.getTypes() != null){
                    
                            java.util.List<io.nop.xlang.ast.TypeNode> typesOpt = optimizeList(node.getTypes(),true, context);
                            if(typesOpt != node.getTypes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typesOpt); ret = node.deepClone();}
                                ret.setTypes(typesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTypeParameterNode(TypeParameterNode node, C context){
        TypeParameterNode ret = node;

        
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getUpperBound() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode upperBoundOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getUpperBound(),context);
                            if(upperBoundOpt != node.getUpperBound()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { upperBoundOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setUpperBound(upperBoundOpt);
                            }
                        
                    }
                
                    if(node.getLowerBound() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode lowerBoundOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getLowerBound(),context);
                            if(lowerBoundOpt != node.getLowerBound()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { lowerBoundOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setLowerBound(lowerBoundOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeTypeAliasDeclaration(TypeAliasDeclaration node, C context){
        TypeAliasDeclaration ret = node;

        
                    if(node.getTypeName() != null){
                    
                            io.nop.xlang.ast.Identifier typeNameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getTypeName(),context);
                            if(typeNameOpt != node.getTypeName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { typeNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setTypeName(typeNameOpt);
                            }
                        
                    }
                
                    if(node.getTypeParams() != null){
                    
                            java.util.List<io.nop.xlang.ast.TypeParameterNode> typeParamsOpt = optimizeList(node.getTypeParams(),true, context);
                            if(typeParamsOpt != node.getTypeParams()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typeParamsOpt); ret = node.deepClone();}
                                ret.setTypeParams(typeParamsOpt);
                            }
                        
                    }
                
                    if(node.getDefType() != null){
                    
                            io.nop.xlang.ast.TypeNode defTypeOpt = (io.nop.xlang.ast.TypeNode)optimize(node.getDefType(),context);
                            if(defTypeOpt != node.getDefType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { defTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDefType(defTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeFunctionTypeDef(FunctionTypeDef node, C context){
        FunctionTypeDef ret = node;

        
                    if(node.getTypeParams() != null){
                    
                            java.util.List<io.nop.xlang.ast.TypeParameterNode> typeParamsOpt = optimizeList(node.getTypeParams(),true, context);
                            if(typeParamsOpt != node.getTypeParams()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typeParamsOpt); ret = node.deepClone();}
                                ret.setTypeParams(typeParamsOpt);
                            }
                        
                    }
                
                    if(node.getArgs() != null){
                    
                            java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> argsOpt = optimizeList(node.getArgs(),true, context);
                            if(argsOpt != node.getArgs()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(argsOpt); ret = node.deepClone();}
                                ret.setArgs(argsOpt);
                            }
                        
                    }
                
                    if(node.getReturnType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode returnTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getReturnType(),context);
                            if(returnTypeOpt != node.getReturnType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { returnTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setReturnType(returnTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeFunctionArgTypeDef(FunctionArgTypeDef node, C context){
        FunctionArgTypeDef ret = node;

        
                    if(node.getArgName() != null){
                    
                            io.nop.xlang.ast.Identifier argNameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getArgName(),context);
                            if(argNameOpt != node.getArgName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argNameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgName(argNameOpt);
                            }
                        
                    }
                
                    if(node.getArgType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode argTypeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getArgType(),context);
                            if(argTypeOpt != node.getArgType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { argTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setArgType(argTypeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeEnumDeclaration(EnumDeclaration node, C context){
        EnumDeclaration ret = node;

        
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getMembers() != null){
                    
                            java.util.List<io.nop.xlang.ast.EnumMember> membersOpt = optimizeList(node.getMembers(),true, context);
                            if(membersOpt != node.getMembers()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(membersOpt); ret = node.deepClone();}
                                ret.setMembers(membersOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeEnumMember(EnumMember node, C context){
        EnumMember ret = node;

        
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getValue() != null){
                    
                            io.nop.xlang.ast.Literal valueOpt = (io.nop.xlang.ast.Literal)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { valueOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeClassDefinition(ClassDefinition node, C context){
        ClassDefinition ret = node;

        
                    if(node.getDecorators() != null){
                    
                            io.nop.xlang.ast.Decorators decoratorsOpt = (io.nop.xlang.ast.Decorators)optimize(node.getDecorators(),context);
                            if(decoratorsOpt != node.getDecorators()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { decoratorsOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getTypeParams() != null){
                    
                            java.util.List<io.nop.xlang.ast.TypeParameterNode> typeParamsOpt = optimizeList(node.getTypeParams(),true, context);
                            if(typeParamsOpt != node.getTypeParams()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(typeParamsOpt); ret = node.deepClone();}
                                ret.setTypeParams(typeParamsOpt);
                            }
                        
                    }
                
                    if(node.getExtendsType() != null){
                    
                            io.nop.xlang.ast.ParameterizedTypeNode extendsTypeOpt = (io.nop.xlang.ast.ParameterizedTypeNode)optimize(node.getExtendsType(),context);
                            if(extendsTypeOpt != node.getExtendsType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { extendsTypeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setExtendsType(extendsTypeOpt);
                            }
                        
                    }
                
                    if(node.getImplementTypes() != null){
                    
                            java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> implementTypesOpt = optimizeList(node.getImplementTypes(),true, context);
                            if(implementTypesOpt != node.getImplementTypes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(implementTypesOpt); ret = node.deepClone();}
                                ret.setImplementTypes(implementTypesOpt);
                            }
                        
                    }
                
                    if(node.getFields() != null){
                    
                            java.util.List<io.nop.xlang.ast.FieldDeclaration> fieldsOpt = optimizeList(node.getFields(),true, context);
                            if(fieldsOpt != node.getFields()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(fieldsOpt); ret = node.deepClone();}
                                ret.setFields(fieldsOpt);
                            }
                        
                    }
                
                    if(node.getMethods() != null){
                    
                            java.util.List<io.nop.xlang.ast.FunctionDeclaration> methodsOpt = optimizeList(node.getMethods(),true, context);
                            if(methodsOpt != node.getMethods()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(methodsOpt); ret = node.deepClone();}
                                ret.setMethods(methodsOpt);
                            }
                        
                    }
                
                    if(node.getClassDefinitions() != null){
                    
                            java.util.List<io.nop.xlang.ast.ClassDefinition> classDefinitionsOpt = optimizeList(node.getClassDefinitions(),true, context);
                            if(classDefinitionsOpt != node.getClassDefinitions()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(classDefinitionsOpt); ret = node.deepClone();}
                                ret.setClassDefinitions(classDefinitionsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeFieldDeclaration(FieldDeclaration node, C context){
        FieldDeclaration ret = node;

        
                    if(node.getDecorators() != null){
                    
                            io.nop.xlang.ast.Decorators decoratorsOpt = (io.nop.xlang.ast.Decorators)optimize(node.getDecorators(),context);
                            if(decoratorsOpt != node.getDecorators()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { decoratorsOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setDecorators(decoratorsOpt);
                            }
                        
                    }
                
                    if(node.getName() != null){
                    
                            io.nop.xlang.ast.Identifier nameOpt = (io.nop.xlang.ast.Identifier)optimize(node.getName(),context);
                            if(nameOpt != node.getName()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { nameOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setName(nameOpt);
                            }
                        
                    }
                
                    if(node.getType() != null){
                    
                            io.nop.xlang.ast.NamedTypeNode typeOpt = (io.nop.xlang.ast.NamedTypeNode)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { typeOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setType(typeOpt);
                            }
                        
                    }
                
                    if(node.getInitializer() != null){
                    
                            io.nop.xlang.ast.Expression initializerOpt = (io.nop.xlang.ast.Expression)optimize(node.getInitializer(),context);
                            if(initializerOpt != node.getInitializer()){
                               incChangeCount();
                               if(shouldClone(ret,node)) { initializerOpt.setASTParent(null); ret = node.deepClone();}
                               ret.setInitializer(initializerOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public XLangASTNode optimizeCustomExpression(CustomExpression node, C context){
        CustomExpression ret = node;

        
		return ret;
	}
    
}
// resume CPD analysis - CPD-ON
