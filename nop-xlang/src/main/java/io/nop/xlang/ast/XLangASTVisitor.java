//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast;

import io.nop.commons.functional.visit.AbstractVisitor;

// tell cpd to start ignoring code - CPD-OFF
public class XLangASTVisitor extends AbstractVisitor<XLangASTNode>{

    @Override
    public void visit(XLangASTNode node){
        switch(node.getASTKind()){
        
                case Program:
                    visitProgram((Program)node);
                    return;
            
                case Identifier:
                    visitIdentifier((Identifier)node);
                    return;
            
                case Literal:
                    visitLiteral((Literal)node);
                    return;
            
                case TemplateStringLiteral:
                    visitTemplateStringLiteral((TemplateStringLiteral)node);
                    return;
            
                case RegExpLiteral:
                    visitRegExpLiteral((RegExpLiteral)node);
                    return;
            
                case BlockStatement:
                    visitBlockStatement((BlockStatement)node);
                    return;
            
                case EmptyStatement:
                    visitEmptyStatement((EmptyStatement)node);
                    return;
            
                case ReturnStatement:
                    visitReturnStatement((ReturnStatement)node);
                    return;
            
                case BreakStatement:
                    visitBreakStatement((BreakStatement)node);
                    return;
            
                case ContinueStatement:
                    visitContinueStatement((ContinueStatement)node);
                    return;
            
                case IfStatement:
                    visitIfStatement((IfStatement)node);
                    return;
            
                case SwitchStatement:
                    visitSwitchStatement((SwitchStatement)node);
                    return;
            
                case SwitchCase:
                    visitSwitchCase((SwitchCase)node);
                    return;
            
                case ThrowStatement:
                    visitThrowStatement((ThrowStatement)node);
                    return;
            
                case TryStatement:
                    visitTryStatement((TryStatement)node);
                    return;
            
                case CatchClause:
                    visitCatchClause((CatchClause)node);
                    return;
            
                case WhileStatement:
                    visitWhileStatement((WhileStatement)node);
                    return;
            
                case DoWhileStatement:
                    visitDoWhileStatement((DoWhileStatement)node);
                    return;
            
                case VariableDeclarator:
                    visitVariableDeclarator((VariableDeclarator)node);
                    return;
            
                case VariableDeclaration:
                    visitVariableDeclaration((VariableDeclaration)node);
                    return;
            
                case ForStatement:
                    visitForStatement((ForStatement)node);
                    return;
            
                case ForOfStatement:
                    visitForOfStatement((ForOfStatement)node);
                    return;
            
                case ForRangeStatement:
                    visitForRangeStatement((ForRangeStatement)node);
                    return;
            
                case ForInStatement:
                    visitForInStatement((ForInStatement)node);
                    return;
            
                case DeleteStatement:
                    visitDeleteStatement((DeleteStatement)node);
                    return;
            
                case ChainExpression:
                    visitChainExpression((ChainExpression)node);
                    return;
            
                case ThisExpression:
                    visitThisExpression((ThisExpression)node);
                    return;
            
                case SuperExpression:
                    visitSuperExpression((SuperExpression)node);
                    return;
            
                case TemplateStringExpression:
                    visitTemplateStringExpression((TemplateStringExpression)node);
                    return;
            
                case ArrayExpression:
                    visitArrayExpression((ArrayExpression)node);
                    return;
            
                case ObjectExpression:
                    visitObjectExpression((ObjectExpression)node);
                    return;
            
                case PropertyAssignment:
                    visitPropertyAssignment((PropertyAssignment)node);
                    return;
            
                case ParameterDeclaration:
                    visitParameterDeclaration((ParameterDeclaration)node);
                    return;
            
                case FunctionDeclaration:
                    visitFunctionDeclaration((FunctionDeclaration)node);
                    return;
            
                case ArrowFunctionExpression:
                    visitArrowFunctionExpression((ArrowFunctionExpression)node);
                    return;
            
                case UnaryExpression:
                    visitUnaryExpression((UnaryExpression)node);
                    return;
            
                case UpdateExpression:
                    visitUpdateExpression((UpdateExpression)node);
                    return;
            
                case BinaryExpression:
                    visitBinaryExpression((BinaryExpression)node);
                    return;
            
                case InExpression:
                    visitInExpression((InExpression)node);
                    return;
            
                case ExpressionStatement:
                    visitExpressionStatement((ExpressionStatement)node);
                    return;
            
                case AssignmentExpression:
                    visitAssignmentExpression((AssignmentExpression)node);
                    return;
            
                case LogicalExpression:
                    visitLogicalExpression((LogicalExpression)node);
                    return;
            
                case MemberExpression:
                    visitMemberExpression((MemberExpression)node);
                    return;
            
                case EvalExpression:
                    visitEvalExpression((EvalExpression)node);
                    return;
            
                case CallExpression:
                    visitCallExpression((CallExpression)node);
                    return;
            
                case NewExpression:
                    visitNewExpression((NewExpression)node);
                    return;
            
                case SpreadElement:
                    visitSpreadElement((SpreadElement)node);
                    return;
            
                case SequenceExpression:
                    visitSequenceExpression((SequenceExpression)node);
                    return;
            
                case ConcatExpression:
                    visitConcatExpression((ConcatExpression)node);
                    return;
            
                case TemplateExpression:
                    visitTemplateExpression((TemplateExpression)node);
                    return;
            
                case BraceExpression:
                    visitBraceExpression((BraceExpression)node);
                    return;
            
                case ObjectBinding:
                    visitObjectBinding((ObjectBinding)node);
                    return;
            
                case PropertyBinding:
                    visitPropertyBinding((PropertyBinding)node);
                    return;
            
                case RestBinding:
                    visitRestBinding((RestBinding)node);
                    return;
            
                case ArrayBinding:
                    visitArrayBinding((ArrayBinding)node);
                    return;
            
                case ArrayElementBinding:
                    visitArrayElementBinding((ArrayElementBinding)node);
                    return;
            
                case ExportDeclaration:
                    visitExportDeclaration((ExportDeclaration)node);
                    return;
            
                case ExportNamedDeclaration:
                    visitExportNamedDeclaration((ExportNamedDeclaration)node);
                    return;
            
                case ExportAllDeclaration:
                    visitExportAllDeclaration((ExportAllDeclaration)node);
                    return;
            
                case ExportSpecifier:
                    visitExportSpecifier((ExportSpecifier)node);
                    return;
            
                case ImportDeclaration:
                    visitImportDeclaration((ImportDeclaration)node);
                    return;
            
                case ImportAsDeclaration:
                    visitImportAsDeclaration((ImportAsDeclaration)node);
                    return;
            
                case ImportSpecifier:
                    visitImportSpecifier((ImportSpecifier)node);
                    return;
            
                case ImportDefaultSpecifier:
                    visitImportDefaultSpecifier((ImportDefaultSpecifier)node);
                    return;
            
                case ImportNamespaceSpecifier:
                    visitImportNamespaceSpecifier((ImportNamespaceSpecifier)node);
                    return;
            
                case AwaitExpression:
                    visitAwaitExpression((AwaitExpression)node);
                    return;
            
                case Decorators:
                    visitDecorators((Decorators)node);
                    return;
            
                case QualifiedName:
                    visitQualifiedName((QualifiedName)node);
                    return;
            
                case Decorator:
                    visitDecorator((Decorator)node);
                    return;
            
                case MetaObject:
                    visitMetaObject((MetaObject)node);
                    return;
            
                case MetaProperty:
                    visitMetaProperty((MetaProperty)node);
                    return;
            
                case MetaArray:
                    visitMetaArray((MetaArray)node);
                    return;
            
                case UsingStatement:
                    visitUsingStatement((UsingStatement)node);
                    return;
            
                case MacroExpression:
                    visitMacroExpression((MacroExpression)node);
                    return;
            
                case TextOutputExpression:
                    visitTextOutputExpression((TextOutputExpression)node);
                    return;
            
                case EscapeOutputExpression:
                    visitEscapeOutputExpression((EscapeOutputExpression)node);
                    return;
            
                case CollectOutputExpression:
                    visitCollectOutputExpression((CollectOutputExpression)node);
                    return;
            
                case CompareOpExpression:
                    visitCompareOpExpression((CompareOpExpression)node);
                    return;
            
                case AssertOpExpression:
                    visitAssertOpExpression((AssertOpExpression)node);
                    return;
            
                case BetweenOpExpression:
                    visitBetweenOpExpression((BetweenOpExpression)node);
                    return;
            
                case GenNodeExpression:
                    visitGenNodeExpression((GenNodeExpression)node);
                    return;
            
                case GenNodeAttrExpression:
                    visitGenNodeAttrExpression((GenNodeAttrExpression)node);
                    return;
            
                case OutputXmlAttrExpression:
                    visitOutputXmlAttrExpression((OutputXmlAttrExpression)node);
                    return;
            
                case OutputXmlExtAttrsExpression:
                    visitOutputXmlExtAttrsExpression((OutputXmlExtAttrsExpression)node);
                    return;
            
                case TypeOfExpression:
                    visitTypeOfExpression((TypeOfExpression)node);
                    return;
            
                case InstanceOfExpression:
                    visitInstanceOfExpression((InstanceOfExpression)node);
                    return;
            
                case CastExpression:
                    visitCastExpression((CastExpression)node);
                    return;
            
                case ArrayTypeNode:
                    visitArrayTypeNode((ArrayTypeNode)node);
                    return;
            
                case ParameterizedTypeNode:
                    visitParameterizedTypeNode((ParameterizedTypeNode)node);
                    return;
            
                case TypeNameNode:
                    visitTypeNameNode((TypeNameNode)node);
                    return;
            
                case UnionTypeDef:
                    visitUnionTypeDef((UnionTypeDef)node);
                    return;
            
                case IntersectionTypeDef:
                    visitIntersectionTypeDef((IntersectionTypeDef)node);
                    return;
            
                case ObjectTypeDef:
                    visitObjectTypeDef((ObjectTypeDef)node);
                    return;
            
                case PropertyTypeDef:
                    visitPropertyTypeDef((PropertyTypeDef)node);
                    return;
            
                case TupleTypeDef:
                    visitTupleTypeDef((TupleTypeDef)node);
                    return;
            
                case TypeParameterNode:
                    visitTypeParameterNode((TypeParameterNode)node);
                    return;
            
                case TypeAliasDeclaration:
                    visitTypeAliasDeclaration((TypeAliasDeclaration)node);
                    return;
            
                case FunctionTypeDef:
                    visitFunctionTypeDef((FunctionTypeDef)node);
                    return;
            
                case FunctionArgTypeDef:
                    visitFunctionArgTypeDef((FunctionArgTypeDef)node);
                    return;
            
                case EnumDeclaration:
                    visitEnumDeclaration((EnumDeclaration)node);
                    return;
            
                case EnumMember:
                    visitEnumMember((EnumMember)node);
                    return;
            
                case ClassDefinition:
                    visitClassDefinition((ClassDefinition)node);
                    return;
            
                case FieldDeclaration:
                    visitFieldDeclaration((FieldDeclaration)node);
                    return;
            
                case CustomExpression:
                    visitCustomExpression((CustomExpression)node);
                    return;
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
            public void visitProgram(Program node){
            
                    this.visitChildren(node.getBody());         
            }
        
            public void visitIdentifier(Identifier node){
            
            }
        
            public void visitLiteral(Literal node){
            
            }
        
            public void visitTemplateStringLiteral(TemplateStringLiteral node){
            
            }
        
            public void visitRegExpLiteral(RegExpLiteral node){
            
            }
        
            public void visitBlockStatement(BlockStatement node){
            
                    this.visitChildren(node.getBody());         
            }
        
            public void visitEmptyStatement(EmptyStatement node){
            
            }
        
            public void visitReturnStatement(ReturnStatement node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitBreakStatement(BreakStatement node){
            
            }
        
            public void visitContinueStatement(ContinueStatement node){
            
            }
        
            public void visitIfStatement(IfStatement node){
            
                    this.visitChild(node.getTest());
                    this.visitChild(node.getConsequent());
                    this.visitChild(node.getAlternate());
            }
        
            public void visitSwitchStatement(SwitchStatement node){
            
                    this.visitChild(node.getDiscriminant());
                    this.visitChildren(node.getCases());         
                    this.visitChild(node.getDefaultCase());
            }
        
            public void visitSwitchCase(SwitchCase node){
            
                    this.visitChild(node.getTest());
                    this.visitChild(node.getConsequent());
            }
        
            public void visitThrowStatement(ThrowStatement node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitTryStatement(TryStatement node){
            
                    this.visitChild(node.getBlock());
                    this.visitChild(node.getCatchHandler());
                    this.visitChild(node.getFinalizer());
            }
        
            public void visitCatchClause(CatchClause node){
            
                    this.visitChild(node.getName());
                    this.visitChild(node.getVarType());
                    this.visitChild(node.getBody());
            }
        
            public void visitWhileStatement(WhileStatement node){
            
                    this.visitChild(node.getTest());
                    this.visitChild(node.getBody());
            }
        
            public void visitDoWhileStatement(DoWhileStatement node){
            
                    this.visitChild(node.getBody());
                    this.visitChild(node.getTest());
            }
        
            public void visitVariableDeclarator(VariableDeclarator node){
            
                    this.visitChild(node.getId());
                    this.visitChild(node.getVarType());
                    this.visitChild(node.getInit());
            }
        
            public void visitVariableDeclaration(VariableDeclaration node){
            
                    this.visitChildren(node.getDeclarators());         
            }
        
            public void visitForStatement(ForStatement node){
            
                    this.visitChild(node.getInit());
                    this.visitChild(node.getTest());
                    this.visitChild(node.getUpdate());
                    this.visitChild(node.getBody());
            }
        
            public void visitForOfStatement(ForOfStatement node){
            
                    this.visitChild(node.getIndex());
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
                    this.visitChild(node.getBody());
            }
        
            public void visitForRangeStatement(ForRangeStatement node){
            
                    this.visitChild(node.getVar());
                    this.visitChild(node.getIndex());
                    this.visitChild(node.getBegin());
                    this.visitChild(node.getEnd());
                    this.visitChild(node.getStep());
                    this.visitChild(node.getBody());
            }
        
            public void visitForInStatement(ForInStatement node){
            
                    this.visitChild(node.getIndex());
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
                    this.visitChild(node.getBody());
            }
        
            public void visitDeleteStatement(DeleteStatement node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitChainExpression(ChainExpression node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitThisExpression(ThisExpression node){
            
            }
        
            public void visitSuperExpression(SuperExpression node){
            
            }
        
            public void visitTemplateStringExpression(TemplateStringExpression node){
            
                    this.visitChild(node.getId());
                    this.visitChild(node.getValue());
            }
        
            public void visitArrayExpression(ArrayExpression node){
            
                    this.visitChildren(node.getElements());         
            }
        
            public void visitObjectExpression(ObjectExpression node){
            
                    this.visitChildren(node.getProperties());         
            }
        
            public void visitPropertyAssignment(PropertyAssignment node){
            
                    this.visitChild(node.getKey());
                    this.visitChild(node.getValue());
            }
        
            public void visitParameterDeclaration(ParameterDeclaration node){
            
                    this.visitChild(node.getDecorators());
                    this.visitChild(node.getName());
                    this.visitChild(node.getType());
                    this.visitChild(node.getInitializer());
            }
        
            public void visitFunctionDeclaration(FunctionDeclaration node){
            
                    this.visitChild(node.getDecorators());
                    this.visitChild(node.getName());
                    this.visitChildren(node.getParams());         
                    this.visitChild(node.getReturnType());
                    this.visitChild(node.getBody());
            }
        
            public void visitArrowFunctionExpression(ArrowFunctionExpression node){
            
                    this.visitChildren(node.getParams());         
                    this.visitChild(node.getReturnType());
                    this.visitChild(node.getBody());
            }
        
            public void visitUnaryExpression(UnaryExpression node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitUpdateExpression(UpdateExpression node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitBinaryExpression(BinaryExpression node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitInExpression(InExpression node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitExpressionStatement(ExpressionStatement node){
            
                    this.visitChild(node.getExpression());
            }
        
            public void visitAssignmentExpression(AssignmentExpression node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitLogicalExpression(LogicalExpression node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitMemberExpression(MemberExpression node){
            
                    this.visitChild(node.getObject());
                    this.visitChild(node.getProperty());
            }
        
            public void visitEvalExpression(EvalExpression node){
            
                    this.visitChild(node.getSource());
            }
        
            public void visitCallExpression(CallExpression node){
            
                    this.visitChild(node.getCallee());
                    this.visitChildren(node.getArguments());         
            }
        
            public void visitNewExpression(NewExpression node){
            
                    this.visitChild(node.getCallee());
                    this.visitChildren(node.getArguments());         
            }
        
            public void visitSpreadElement(SpreadElement node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitSequenceExpression(SequenceExpression node){
            
                    this.visitChildren(node.getExpressions());         
            }
        
            public void visitConcatExpression(ConcatExpression node){
            
                    this.visitChildren(node.getExpressions());         
            }
        
            public void visitTemplateExpression(TemplateExpression node){
            
                    this.visitChildren(node.getExpressions());         
            }
        
            public void visitBraceExpression(BraceExpression node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitObjectBinding(ObjectBinding node){
            
                    this.visitChildren(node.getProperties());         
                    this.visitChild(node.getRestBinding());
            }
        
            public void visitPropertyBinding(PropertyBinding node){
            
                    this.visitChild(node.getIdentifier());
                    this.visitChild(node.getInitializer());
            }
        
            public void visitRestBinding(RestBinding node){
            
                    this.visitChild(node.getIdentifier());
                    this.visitChild(node.getInitializer());
            }
        
            public void visitArrayBinding(ArrayBinding node){
            
                    this.visitChildren(node.getElements());         
                    this.visitChild(node.getRestBinding());
            }
        
            public void visitArrayElementBinding(ArrayElementBinding node){
            
                    this.visitChild(node.getIdentifier());
                    this.visitChild(node.getInitializer());
            }
        
            public void visitExportDeclaration(ExportDeclaration node){
            
                    this.visitChild(node.getDeclaration());
            }
        
            public void visitExportNamedDeclaration(ExportNamedDeclaration node){
            
                    this.visitChildren(node.getSpecifiers());         
                    this.visitChild(node.getSource());
            }
        
            public void visitExportAllDeclaration(ExportAllDeclaration node){
            
                    this.visitChild(node.getSource());
            }
        
            public void visitExportSpecifier(ExportSpecifier node){
            
                    this.visitChild(node.getLocal());
                    this.visitChild(node.getExported());
            }
        
            public void visitImportDeclaration(ImportDeclaration node){
            
                    this.visitChildren(node.getSpecifiers());         
                    this.visitChild(node.getSource());
            }
        
            public void visitImportAsDeclaration(ImportAsDeclaration node){
            
                    this.visitChild(node.getSource());
                    this.visitChild(node.getLocal());
            }
        
            public void visitImportSpecifier(ImportSpecifier node){
            
                    this.visitChild(node.getLocal());
                    this.visitChild(node.getImported());
            }
        
            public void visitImportDefaultSpecifier(ImportDefaultSpecifier node){
            
                    this.visitChild(node.getLocal());
            }
        
            public void visitImportNamespaceSpecifier(ImportNamespaceSpecifier node){
            
                    this.visitChild(node.getLocal());
            }
        
            public void visitAwaitExpression(AwaitExpression node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitDecorators(Decorators node){
            
                    this.visitChildren(node.getDecorators());         
            }
        
            public void visitQualifiedName(QualifiedName node){
            
                    this.visitChild(node.getNext());
            }
        
            public void visitDecorator(Decorator node){
            
                    this.visitChild(node.getName());
                    this.visitChild(node.getValue());
            }
        
            public void visitMetaObject(MetaObject node){
            
                    this.visitChildren(node.getProperties());         
            }
        
            public void visitMetaProperty(MetaProperty node){
            
                    this.visitChild(node.getName());
                    this.visitChild(node.getValue());
            }
        
            public void visitMetaArray(MetaArray node){
            
                    this.visitChildren(node.getElements());         
            }
        
            public void visitUsingStatement(UsingStatement node){
            
                    this.visitChild(node.getVars());
                    this.visitChild(node.getBody());
            }
        
            public void visitMacroExpression(MacroExpression node){
            
                    this.visitChild(node.getExpr());
            }
        
            public void visitTextOutputExpression(TextOutputExpression node){
            
            }
        
            public void visitEscapeOutputExpression(EscapeOutputExpression node){
            
                    this.visitChild(node.getText());
            }
        
            public void visitCollectOutputExpression(CollectOutputExpression node){
            
                    this.visitChild(node.getBody());
            }
        
            public void visitCompareOpExpression(CompareOpExpression node){
            
                    this.visitChild(node.getLeft());
                    this.visitChild(node.getRight());
            }
        
            public void visitAssertOpExpression(AssertOpExpression node){
            
                    this.visitChild(node.getValue());
            }
        
            public void visitBetweenOpExpression(BetweenOpExpression node){
            
                    this.visitChild(node.getValue());
                    this.visitChild(node.getMin());
                    this.visitChild(node.getMax());
            }
        
            public void visitGenNodeExpression(GenNodeExpression node){
            
                    this.visitChild(node.getTagName());
                    this.visitChild(node.getExtAttrs());
                    this.visitChildren(node.getAttrs());         
                    this.visitChild(node.getBody());
            }
        
            public void visitGenNodeAttrExpression(GenNodeAttrExpression node){
            
                    this.visitChild(node.getValue());
            }
        
            public void visitOutputXmlAttrExpression(OutputXmlAttrExpression node){
            
                    this.visitChild(node.getValue());
            }
        
            public void visitOutputXmlExtAttrsExpression(OutputXmlExtAttrsExpression node){
            
                    this.visitChild(node.getExtAttrs());
            }
        
            public void visitTypeOfExpression(TypeOfExpression node){
            
                    this.visitChild(node.getArgument());
            }
        
            public void visitInstanceOfExpression(InstanceOfExpression node){
            
                    this.visitChild(node.getValue());
                    this.visitChild(node.getRefType());
            }
        
            public void visitCastExpression(CastExpression node){
            
                    this.visitChild(node.getValue());
                    this.visitChild(node.getAsType());
            }
        
            public void visitArrayTypeNode(ArrayTypeNode node){
            
                    this.visitChild(node.getComponentType());
            }
        
            public void visitParameterizedTypeNode(ParameterizedTypeNode node){
            
                    this.visitChildren(node.getTypeArgs());         
            }
        
            public void visitTypeNameNode(TypeNameNode node){
            
            }
        
            public void visitUnionTypeDef(UnionTypeDef node){
            
                    this.visitChildren(node.getTypes());         
            }
        
            public void visitIntersectionTypeDef(IntersectionTypeDef node){
            
                    this.visitChildren(node.getTypes());         
            }
        
            public void visitObjectTypeDef(ObjectTypeDef node){
            
                    this.visitChildren(node.getTypes());         
            }
        
            public void visitPropertyTypeDef(PropertyTypeDef node){
            
                    this.visitChild(node.getValueType());
            }
        
            public void visitTupleTypeDef(TupleTypeDef node){
            
                    this.visitChildren(node.getTypes());         
            }
        
            public void visitTypeParameterNode(TypeParameterNode node){
            
                    this.visitChild(node.getName());
                    this.visitChild(node.getUpperBound());
                    this.visitChild(node.getLowerBound());
            }
        
            public void visitTypeAliasDeclaration(TypeAliasDeclaration node){
            
                    this.visitChild(node.getTypeName());
                    this.visitChildren(node.getTypeParams());         
                    this.visitChild(node.getDefType());
            }
        
            public void visitFunctionTypeDef(FunctionTypeDef node){
            
                    this.visitChildren(node.getTypeParams());         
                    this.visitChildren(node.getArgs());         
                    this.visitChild(node.getReturnType());
            }
        
            public void visitFunctionArgTypeDef(FunctionArgTypeDef node){
            
                    this.visitChild(node.getArgName());
                    this.visitChild(node.getArgType());
            }
        
            public void visitEnumDeclaration(EnumDeclaration node){
            
                    this.visitChild(node.getName());
                    this.visitChildren(node.getMembers());         
            }
        
            public void visitEnumMember(EnumMember node){
            
                    this.visitChild(node.getName());
                    this.visitChild(node.getValue());
            }
        
            public void visitClassDefinition(ClassDefinition node){
            
                    this.visitChild(node.getDecorators());
                    this.visitChild(node.getName());
                    this.visitChildren(node.getTypeParams());         
                    this.visitChild(node.getExtendsType());
                    this.visitChildren(node.getImplementTypes());         
                    this.visitChildren(node.getFields());         
                    this.visitChildren(node.getMethods());         
                    this.visitChildren(node.getClassDefinitions());         
            }
        
            public void visitFieldDeclaration(FieldDeclaration node){
            
                    this.visitChild(node.getDecorators());
                    this.visitChild(node.getName());
                    this.visitChild(node.getType());
                    this.visitChild(node.getInitializer());
            }
        
            public void visitCustomExpression(CustomExpression node){
            
            }
        
}
// resume CPD analysis - CPD-ON
