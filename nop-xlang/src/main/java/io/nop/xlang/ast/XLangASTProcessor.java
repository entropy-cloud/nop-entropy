//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast;

// tell cpd to start ignoring code - CPD-OFF
public class XLangASTProcessor<T,C>{

    public T processAST(XLangASTNode node, C context){
        if(node == null)
            return null;
       switch(node.getASTKind()){
    
            case CompilationUnit:
                return processCompilationUnit((CompilationUnit)node,context);
        
            case Program:
                return processProgram((Program)node,context);
        
            case Identifier:
                return processIdentifier((Identifier)node,context);
        
            case Literal:
                return processLiteral((Literal)node,context);
        
            case TemplateStringLiteral:
                return processTemplateStringLiteral((TemplateStringLiteral)node,context);
        
            case RegExpLiteral:
                return processRegExpLiteral((RegExpLiteral)node,context);
        
            case BlockStatement:
                return processBlockStatement((BlockStatement)node,context);
        
            case EmptyStatement:
                return processEmptyStatement((EmptyStatement)node,context);
        
            case ReturnStatement:
                return processReturnStatement((ReturnStatement)node,context);
        
            case BreakStatement:
                return processBreakStatement((BreakStatement)node,context);
        
            case ContinueStatement:
                return processContinueStatement((ContinueStatement)node,context);
        
            case IfStatement:
                return processIfStatement((IfStatement)node,context);
        
            case SwitchStatement:
                return processSwitchStatement((SwitchStatement)node,context);
        
            case SwitchCase:
                return processSwitchCase((SwitchCase)node,context);
        
            case ThrowStatement:
                return processThrowStatement((ThrowStatement)node,context);
        
            case TryStatement:
                return processTryStatement((TryStatement)node,context);
        
            case CatchClause:
                return processCatchClause((CatchClause)node,context);
        
            case WhileStatement:
                return processWhileStatement((WhileStatement)node,context);
        
            case DoWhileStatement:
                return processDoWhileStatement((DoWhileStatement)node,context);
        
            case VariableDeclarator:
                return processVariableDeclarator((VariableDeclarator)node,context);
        
            case VariableDeclaration:
                return processVariableDeclaration((VariableDeclaration)node,context);
        
            case ForStatement:
                return processForStatement((ForStatement)node,context);
        
            case ForOfStatement:
                return processForOfStatement((ForOfStatement)node,context);
        
            case ForRangeStatement:
                return processForRangeStatement((ForRangeStatement)node,context);
        
            case ForInStatement:
                return processForInStatement((ForInStatement)node,context);
        
            case DeleteStatement:
                return processDeleteStatement((DeleteStatement)node,context);
        
            case ChainExpression:
                return processChainExpression((ChainExpression)node,context);
        
            case ThisExpression:
                return processThisExpression((ThisExpression)node,context);
        
            case SuperExpression:
                return processSuperExpression((SuperExpression)node,context);
        
            case TemplateStringExpression:
                return processTemplateStringExpression((TemplateStringExpression)node,context);
        
            case ArrayExpression:
                return processArrayExpression((ArrayExpression)node,context);
        
            case ObjectExpression:
                return processObjectExpression((ObjectExpression)node,context);
        
            case PropertyAssignment:
                return processPropertyAssignment((PropertyAssignment)node,context);
        
            case ParameterDeclaration:
                return processParameterDeclaration((ParameterDeclaration)node,context);
        
            case FunctionDeclaration:
                return processFunctionDeclaration((FunctionDeclaration)node,context);
        
            case ArrowFunctionExpression:
                return processArrowFunctionExpression((ArrowFunctionExpression)node,context);
        
            case UnaryExpression:
                return processUnaryExpression((UnaryExpression)node,context);
        
            case UpdateExpression:
                return processUpdateExpression((UpdateExpression)node,context);
        
            case BinaryExpression:
                return processBinaryExpression((BinaryExpression)node,context);
        
            case InExpression:
                return processInExpression((InExpression)node,context);
        
            case ExpressionStatement:
                return processExpressionStatement((ExpressionStatement)node,context);
        
            case AssignmentExpression:
                return processAssignmentExpression((AssignmentExpression)node,context);
        
            case LogicalExpression:
                return processLogicalExpression((LogicalExpression)node,context);
        
            case MemberExpression:
                return processMemberExpression((MemberExpression)node,context);
        
            case EvalExpression:
                return processEvalExpression((EvalExpression)node,context);
        
            case CallExpression:
                return processCallExpression((CallExpression)node,context);
        
            case NewExpression:
                return processNewExpression((NewExpression)node,context);
        
            case SpreadElement:
                return processSpreadElement((SpreadElement)node,context);
        
            case SequenceExpression:
                return processSequenceExpression((SequenceExpression)node,context);
        
            case ConcatExpression:
                return processConcatExpression((ConcatExpression)node,context);
        
            case TemplateExpression:
                return processTemplateExpression((TemplateExpression)node,context);
        
            case BraceExpression:
                return processBraceExpression((BraceExpression)node,context);
        
            case ObjectBinding:
                return processObjectBinding((ObjectBinding)node,context);
        
            case PropertyBinding:
                return processPropertyBinding((PropertyBinding)node,context);
        
            case RestBinding:
                return processRestBinding((RestBinding)node,context);
        
            case ArrayBinding:
                return processArrayBinding((ArrayBinding)node,context);
        
            case ArrayElementBinding:
                return processArrayElementBinding((ArrayElementBinding)node,context);
        
            case ExportDeclaration:
                return processExportDeclaration((ExportDeclaration)node,context);
        
            case ExportNamedDeclaration:
                return processExportNamedDeclaration((ExportNamedDeclaration)node,context);
        
            case ExportAllDeclaration:
                return processExportAllDeclaration((ExportAllDeclaration)node,context);
        
            case ExportSpecifier:
                return processExportSpecifier((ExportSpecifier)node,context);
        
            case ImportDeclaration:
                return processImportDeclaration((ImportDeclaration)node,context);
        
            case ImportAsDeclaration:
                return processImportAsDeclaration((ImportAsDeclaration)node,context);
        
            case ImportSpecifier:
                return processImportSpecifier((ImportSpecifier)node,context);
        
            case ImportDefaultSpecifier:
                return processImportDefaultSpecifier((ImportDefaultSpecifier)node,context);
        
            case ImportNamespaceSpecifier:
                return processImportNamespaceSpecifier((ImportNamespaceSpecifier)node,context);
        
            case AwaitExpression:
                return processAwaitExpression((AwaitExpression)node,context);
        
            case Decorators:
                return processDecorators((Decorators)node,context);
        
            case QualifiedName:
                return processQualifiedName((QualifiedName)node,context);
        
            case Decorator:
                return processDecorator((Decorator)node,context);
        
            case MetaObject:
                return processMetaObject((MetaObject)node,context);
        
            case MetaProperty:
                return processMetaProperty((MetaProperty)node,context);
        
            case MetaArray:
                return processMetaArray((MetaArray)node,context);
        
            case UsingStatement:
                return processUsingStatement((UsingStatement)node,context);
        
            case MacroExpression:
                return processMacroExpression((MacroExpression)node,context);
        
            case TextOutputExpression:
                return processTextOutputExpression((TextOutputExpression)node,context);
        
            case EscapeOutputExpression:
                return processEscapeOutputExpression((EscapeOutputExpression)node,context);
        
            case CollectOutputExpression:
                return processCollectOutputExpression((CollectOutputExpression)node,context);
        
            case CompareOpExpression:
                return processCompareOpExpression((CompareOpExpression)node,context);
        
            case AssertOpExpression:
                return processAssertOpExpression((AssertOpExpression)node,context);
        
            case BetweenOpExpression:
                return processBetweenOpExpression((BetweenOpExpression)node,context);
        
            case GenNodeExpression:
                return processGenNodeExpression((GenNodeExpression)node,context);
        
            case GenNodeAttrExpression:
                return processGenNodeAttrExpression((GenNodeAttrExpression)node,context);
        
            case OutputXmlAttrExpression:
                return processOutputXmlAttrExpression((OutputXmlAttrExpression)node,context);
        
            case OutputXmlExtAttrsExpression:
                return processOutputXmlExtAttrsExpression((OutputXmlExtAttrsExpression)node,context);
        
            case TypeOfExpression:
                return processTypeOfExpression((TypeOfExpression)node,context);
        
            case InstanceOfExpression:
                return processInstanceOfExpression((InstanceOfExpression)node,context);
        
            case CastExpression:
                return processCastExpression((CastExpression)node,context);
        
            case ArrayTypeNode:
                return processArrayTypeNode((ArrayTypeNode)node,context);
        
            case ParameterizedTypeNode:
                return processParameterizedTypeNode((ParameterizedTypeNode)node,context);
        
            case TypeNameNode:
                return processTypeNameNode((TypeNameNode)node,context);
        
            case UnionTypeDef:
                return processUnionTypeDef((UnionTypeDef)node,context);
        
            case IntersectionTypeDef:
                return processIntersectionTypeDef((IntersectionTypeDef)node,context);
        
            case ObjectTypeDef:
                return processObjectTypeDef((ObjectTypeDef)node,context);
        
            case PropertyTypeDef:
                return processPropertyTypeDef((PropertyTypeDef)node,context);
        
            case TupleTypeDef:
                return processTupleTypeDef((TupleTypeDef)node,context);
        
            case TypeParameterNode:
                return processTypeParameterNode((TypeParameterNode)node,context);
        
            case TypeAliasDeclaration:
                return processTypeAliasDeclaration((TypeAliasDeclaration)node,context);
        
            case FunctionTypeDef:
                return processFunctionTypeDef((FunctionTypeDef)node,context);
        
            case FunctionArgTypeDef:
                return processFunctionArgTypeDef((FunctionArgTypeDef)node,context);
        
            case EnumDeclaration:
                return processEnumDeclaration((EnumDeclaration)node,context);
        
            case EnumMember:
                return processEnumMember((EnumMember)node,context);
        
            case ClassDefinition:
                return processClassDefinition((ClassDefinition)node,context);
        
            case FieldDeclaration:
                return processFieldDeclaration((FieldDeclaration)node,context);
        
            case CustomExpression:
                return processCustomExpression((CustomExpression)node,context);
        
          default:
             throw new IllegalArgumentException("invalid ast kind");
       }
    }

    
	public T processCompilationUnit(CompilationUnit node, C context){
        return defaultProcess(node, context);
	}
    
	public T processProgram(Program node, C context){
        return defaultProcess(node, context);
	}
    
	public T processIdentifier(Identifier node, C context){
        return defaultProcess(node, context);
	}
    
	public T processLiteral(Literal node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTemplateStringLiteral(TemplateStringLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processRegExpLiteral(RegExpLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processBlockStatement(BlockStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processEmptyStatement(EmptyStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processReturnStatement(ReturnStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processBreakStatement(BreakStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processContinueStatement(ContinueStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processIfStatement(IfStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSwitchStatement(SwitchStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSwitchCase(SwitchCase node, C context){
        return defaultProcess(node, context);
	}
    
	public T processThrowStatement(ThrowStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTryStatement(TryStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processCatchClause(CatchClause node, C context){
        return defaultProcess(node, context);
	}
    
	public T processWhileStatement(WhileStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processDoWhileStatement(DoWhileStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processVariableDeclarator(VariableDeclarator node, C context){
        return defaultProcess(node, context);
	}
    
	public T processVariableDeclaration(VariableDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processForStatement(ForStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processForOfStatement(ForOfStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processForRangeStatement(ForRangeStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processForInStatement(ForInStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processDeleteStatement(DeleteStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processChainExpression(ChainExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processThisExpression(ThisExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSuperExpression(SuperExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTemplateStringExpression(TemplateStringExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processArrayExpression(ArrayExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processObjectExpression(ObjectExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processPropertyAssignment(PropertyAssignment node, C context){
        return defaultProcess(node, context);
	}
    
	public T processParameterDeclaration(ParameterDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processFunctionDeclaration(FunctionDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processArrowFunctionExpression(ArrowFunctionExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processUnaryExpression(UnaryExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processUpdateExpression(UpdateExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processBinaryExpression(BinaryExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processInExpression(InExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processExpressionStatement(ExpressionStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processAssignmentExpression(AssignmentExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processLogicalExpression(LogicalExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMemberExpression(MemberExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processEvalExpression(EvalExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processCallExpression(CallExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processNewExpression(NewExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSpreadElement(SpreadElement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processSequenceExpression(SequenceExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processConcatExpression(ConcatExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTemplateExpression(TemplateExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processBraceExpression(BraceExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processObjectBinding(ObjectBinding node, C context){
        return defaultProcess(node, context);
	}
    
	public T processPropertyBinding(PropertyBinding node, C context){
        return defaultProcess(node, context);
	}
    
	public T processRestBinding(RestBinding node, C context){
        return defaultProcess(node, context);
	}
    
	public T processArrayBinding(ArrayBinding node, C context){
        return defaultProcess(node, context);
	}
    
	public T processArrayElementBinding(ArrayElementBinding node, C context){
        return defaultProcess(node, context);
	}
    
	public T processExportDeclaration(ExportDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processExportNamedDeclaration(ExportNamedDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processExportAllDeclaration(ExportAllDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processExportSpecifier(ExportSpecifier node, C context){
        return defaultProcess(node, context);
	}
    
	public T processImportDeclaration(ImportDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processImportAsDeclaration(ImportAsDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processImportSpecifier(ImportSpecifier node, C context){
        return defaultProcess(node, context);
	}
    
	public T processImportDefaultSpecifier(ImportDefaultSpecifier node, C context){
        return defaultProcess(node, context);
	}
    
	public T processImportNamespaceSpecifier(ImportNamespaceSpecifier node, C context){
        return defaultProcess(node, context);
	}
    
	public T processAwaitExpression(AwaitExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processDecorators(Decorators node, C context){
        return defaultProcess(node, context);
	}
    
	public T processQualifiedName(QualifiedName node, C context){
        return defaultProcess(node, context);
	}
    
	public T processDecorator(Decorator node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMetaObject(MetaObject node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMetaProperty(MetaProperty node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMetaArray(MetaArray node, C context){
        return defaultProcess(node, context);
	}
    
	public T processUsingStatement(UsingStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMacroExpression(MacroExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTextOutputExpression(TextOutputExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processEscapeOutputExpression(EscapeOutputExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processCollectOutputExpression(CollectOutputExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processCompareOpExpression(CompareOpExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processAssertOpExpression(AssertOpExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processBetweenOpExpression(BetweenOpExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGenNodeExpression(GenNodeExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGenNodeAttrExpression(GenNodeAttrExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processOutputXmlAttrExpression(OutputXmlAttrExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processOutputXmlExtAttrsExpression(OutputXmlExtAttrsExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTypeOfExpression(TypeOfExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processInstanceOfExpression(InstanceOfExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processCastExpression(CastExpression node, C context){
        return defaultProcess(node, context);
	}
    
	public T processArrayTypeNode(ArrayTypeNode node, C context){
        return defaultProcess(node, context);
	}
    
	public T processParameterizedTypeNode(ParameterizedTypeNode node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTypeNameNode(TypeNameNode node, C context){
        return defaultProcess(node, context);
	}
    
	public T processUnionTypeDef(UnionTypeDef node, C context){
        return defaultProcess(node, context);
	}
    
	public T processIntersectionTypeDef(IntersectionTypeDef node, C context){
        return defaultProcess(node, context);
	}
    
	public T processObjectTypeDef(ObjectTypeDef node, C context){
        return defaultProcess(node, context);
	}
    
	public T processPropertyTypeDef(PropertyTypeDef node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTupleTypeDef(TupleTypeDef node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTypeParameterNode(TypeParameterNode node, C context){
        return defaultProcess(node, context);
	}
    
	public T processTypeAliasDeclaration(TypeAliasDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processFunctionTypeDef(FunctionTypeDef node, C context){
        return defaultProcess(node, context);
	}
    
	public T processFunctionArgTypeDef(FunctionArgTypeDef node, C context){
        return defaultProcess(node, context);
	}
    
	public T processEnumDeclaration(EnumDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processEnumMember(EnumMember node, C context){
        return defaultProcess(node, context);
	}
    
	public T processClassDefinition(ClassDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processFieldDeclaration(FieldDeclaration node, C context){
        return defaultProcess(node, context);
	}
    
	public T processCustomExpression(CustomExpression node, C context){
        return defaultProcess(node, context);
	}
    

    public T defaultProcess(XLangASTNode node, C context){
        return null;
    }
}
// resume CPD analysis - CPD-ON
