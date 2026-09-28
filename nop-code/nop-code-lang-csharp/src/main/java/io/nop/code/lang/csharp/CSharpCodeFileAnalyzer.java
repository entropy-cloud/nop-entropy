package io.nop.code.lang.csharp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import io.nop.treesitter.compat.TSNode;
import io.nop.treesitter.compat.TSParser;
import io.nop.treesitter.compat.TSTree;
import io.nop.treesitter.compat.TreeSitterCSharp;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeRelationType;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.model.EdgeProvenance;
import io.nop.code.core.semantic.EdgeConfidence;

/**
 * C# 文件分析器。
 * 使用 nop-treesitter 纯 Java 运行时解析 C# 源代码，提取符号（类/接口/结构体/
 * record/枚举/方法/属性→FIELD/字段/常量）、继承边（base_list 按容器分派：class
 * 首项 EXTENDS 其余 IMPLEMENTS、interface 全 EXTENDS、struct 全 IMPLEMENTS）、
 * 调用与 using 导入。
 *
 * <p>qn 前缀 = namespace 路径（块式嵌套拼接、file_scoped 变体、qualified_name
 * 拆段均覆盖）。property 取名 = 类型之后的 identifier（自定义类型属性的类型
 * 也是 identifier，不得取首 identifier）。
 */
public class CSharpCodeFileAnalyzer implements ICodeFileAnalyzer {

    private static final TreeSitterCSharp TS_LANGUAGE = new TreeSitterCSharp();

    private String source;
    private String lastNamespace;

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.CSHARP;
    }

    @Override
    public List<String> getFileExtensions() {
        return java.util.Collections.singletonList(".cs");
    }

    @Override
    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }

        TSParser parser = new TSParser();
        parser.setLanguage(TS_LANGUAGE);

        TSTree tree = parser.parseString(null, sourceCode);
        if (tree == null) {
            return null;
        }

        TSNode root = tree.getRootNode();
        this.source = sourceCode;
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(filePath);
        result.setSourceCode(sourceCode);
        result.setLanguage(CodeLanguage.CSHARP);

        Deque<String> namespaceStack = new ArrayDeque<>();
        lastNamespace = null;
        walkNode(root, namespaceStack, null, result);
        result.setPackageName(lastNamespace != null && !lastNamespace.isEmpty() ? lastNamespace : null);

        tree = null;
        return result;
    }

    // ---- walking ----

    private void walkNode(TSNode node, Deque<String> namespaceStack, CodeSymbol ownerType,
                          CodeFileAnalysisResult result) {
        String type = node.getType();

        switch (type) {
            case "namespace_declaration":
                visitNamespaceDeclaration(node, namespaceStack, result);
                return;
            case "file_scoped_namespace_declaration":
                return; // handled at the sibling level (see walkNode sibling loop)
            case "using_directive":
                visitUsingDirective(node, namespaceStack, result);
                return;
            case "class_declaration":
            case "interface_declaration":
            case "struct_declaration":
            case "record_declaration":
                visitTypeDeclaration(node, type, namespaceStack, result);
                return;
            case "enum_declaration":
                visitEnumDeclaration(node, namespaceStack, result);
                return;
            case "method_declaration":
                visitMethodDeclaration(node, namespaceStack, ownerType, result);
                return;
            case "property_declaration":
                visitPropertyDeclaration(node, ownerType, result);
                return;
            case "field_declaration":
                visitFieldDeclaration(node, ownerType, result);
                return;
            case "invocation_expression":
                visitInvocationExpression(node, namespaceStack, ownerType, result);
                break;
            default:
                break;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null) {
                continue;
            }
            if ("file_scoped_namespace_declaration".equals(child.getType())) {
                // members are subsequent siblings; the namespace stays effective
                walkFileScopedSiblings(child, i, node, namespaceStack, ownerType, result);
                return;
            }
            walkNode(child, namespaceStack, ownerType, result);
        }
    }

    private void visitNamespaceDeclaration(TSNode node, Deque<String> namespaceStack,
                                           CodeFileAnalysisResult result) {
        String namespacePath = namespaceOf(node);
        namespaceStack.push(namespacePath);
        lastNamespace = namespacePath;
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && "declaration_list".equals(child.getType())) {
                walkChildren(child, namespaceStack, result);
            }
        }
        namespaceStack.pop();
    }

    /**
     * 文件级 namespace（C# 10+）：成员是本节点的兄弟节点。推送其 namespace 后
     * 遍历全部后续兄弟，返回后弹出。
     */
    private void walkFileScopedSiblings(TSNode node, int siblingIndex, TSNode parent,
                                        Deque<String> namespaceStack, CodeSymbol ownerType,
                                        CodeFileAnalysisResult result) {
        String namespacePath = namespaceOf(node);
        namespaceStack.push(namespacePath);
        lastNamespace = namespacePath;
        int childCount = parent.getChildCount();
        for (int i = siblingIndex + 1; i < childCount; i++) {
            TSNode child = parent.getChild(i);
            if (child != null) {
                walkNode(child, namespaceStack, ownerType, result);
            }
        }
        namespaceStack.pop();
    }

    /**
     * namespace 名 = identifier 或 qualified_name（A.B）子节点，逐段拼接。
     */
    private String namespaceOf(TSNode node) {
        int childCount = node.getChildCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null || !child.isNamed()) {
                continue;
            }
            String childType = child.getType();
            if ("identifier".equals(childType) || "qualified_name".equals(childType)) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(nodeText(child));
            }
        }
        return sb.toString();
    }

    private void walkChildren(TSNode node, Deque<String> namespaceStack, CodeFileAnalysisResult result) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                walkNode(child, namespaceStack, null, result);
            }
        }
    }

    private void visitUsingDirective(TSNode node, Deque<String> namespaceStack,
                                     CodeFileAnalysisResult result) {
        String path = dottedName(node);
        if (path == null || path.isEmpty()) {
            return;
        }

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.IMPORT);
        symbol.setName(path);
        symbol.setQualifiedName(prefix(path, namespaceStack));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        result.getSymbols().add(symbol);
        if (!result.getImports().contains(path)) {
            result.getImports().add(path);
        }
    }

    private void visitTypeDeclaration(TSNode node, String type, Deque<String> namespaceStack,
                                      CodeFileAnalysisResult result) {
        String name = typeName(node);
        if (name == null) {
            return;
        }
        boolean isInterface = "interface_declaration".equals(type);
        boolean isStructLike = "struct_declaration".equals(type)
                || ("record_declaration".equals(type) && hasStructKeyword(node));

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(isInterface ? CodeSymbolKind.INTERFACE : CodeSymbolKind.CLASS);
        symbol.setName(name);
        symbol.setQualifiedName(prefix(name, namespaceStack));
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node));
        result.getSymbols().add(symbol);

        // base_list edges by container kind (audit M1)
        TSNode baseList = firstChildOfType(node, "base_list");
        if (baseList != null) {
            addBaseListEdges(baseList, symbol, isInterface, isStructLike, namespaceStack, result);
        }

        TSNode body = firstChildOfType(node, "declaration_list");
        if (body != null) {
            int childCount = body.getChildCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = body.getChild(i);
                if (child != null) {
                    walkNode(child, namespaceStack, symbol, result);
                }
            }
        }
    }

    private void addBaseListEdges(TSNode baseList, CodeSymbol symbol, boolean isInterface,
                                  boolean isStructLike, Deque<String> namespaceStack,
                                  CodeFileAnalysisResult result) {
        boolean first = true;
        int childCount = baseList.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = baseList.getChild(i);
            if (child == null || !child.isNamed() || ":".equals(child.getType())) {
                continue;
            }
            String baseName = nodeText(child).trim();
            if (baseName.isEmpty()) {
                continue;
            }
            String baseQn = baseName.contains(".") ? baseName : prefix(baseName, namespaceStack);
            CodeRelationType relation;
            if (isInterface) {
                relation = CodeRelationType.EXTENDS;
            } else if (isStructLike) {
                relation = CodeRelationType.IMPLEMENTS;
            } else {
                // class: resolve by known-symbol kind — an interface base must be
                // IMPLEMENTS even when it is the first entry; fallback heuristic
                // (first EXTENDS) applies only to unknown bases
                CodeSymbol known = findSymbolByName(baseName, result);
                if (known != null && known.getKind() == CodeSymbolKind.INTERFACE) {
                    relation = CodeRelationType.IMPLEMENTS;
                } else {
                    relation = first ? CodeRelationType.EXTENDS : CodeRelationType.IMPLEMENTS;
                }
            }
            first = false;
            addInheritance(result, symbol.getId(), baseQn, relation);
        }
    }

    private CodeSymbol findSymbolByName(String name, CodeFileAnalysisResult result) {
        for (CodeSymbol symbol : result.getSymbols()) {
            if (name.equals(symbol.getName())) {
                return symbol;
            }
        }
        return null;
    }

    private void visitEnumDeclaration(TSNode node, Deque<String> namespaceStack,
                                      CodeFileAnalysisResult result) {
        String name = typeName(node);
        if (name == null) {
            return;
        }
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.ENUM);
        symbol.setName(name);
        symbol.setQualifiedName(prefix(name, namespaceStack));
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        result.getSymbols().add(symbol);
    }

    private void visitMethodDeclaration(TSNode node, Deque<String> namespaceStack,
                                        CodeSymbol ownerType, CodeFileAnalysisResult result) {
        String name = nameBeforeStop(node);
        if (name == null) {
            return;
        }
        boolean isMethod = ownerType != null;
        String qn = isMethod ? ownerType.getQualifiedName() + "." + name
                : prefix(name, namespaceStack);

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(isMethod ? CodeSymbolKind.METHOD : CodeSymbolKind.FUNCTION);
        symbol.setName(name);
        symbol.setQualifiedName(qn);
        symbol.setParentId(isMethod ? ownerType.getId() : null);
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node));
        result.getSymbols().add(symbol);

        // method body: calls inside
        TSNode block = firstChildOfType(node, "block");
        if (block != null) {
            walkChildren(block, namespaceStack, result);
        }
    }

    private void visitPropertyDeclaration(TSNode node, CodeSymbol ownerType,
                                          CodeFileAnalysisResult result) {
        // name = last identifier before accessor_list (the preceding identifier
        // is the property type for custom-type properties)
        String name = nameBeforeStop(node);
        if (name == null) {
            return;
        }
        String qn = ownerType != null ? ownerType.getQualifiedName() + "." + name
                : prefix(name, new ArrayDeque<>());

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.FIELD);
        symbol.setName(name);
        symbol.setQualifiedName(qn);
        symbol.setParentId(ownerType != null ? ownerType.getId() : null);
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        result.getSymbols().add(symbol);
    }

    private void visitFieldDeclaration(TSNode node, CodeSymbol ownerType,
                                       CodeFileAnalysisResult result) {
        boolean isConst = hasConstModifier(node);
        TSNode varDecl = firstChildOfType(node, "variable_declaration");
        if (varDecl == null) {
            return;
        }
        int childCount = varDecl.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode declarator = varDecl.getChild(i);
            if (declarator == null || !"variable_declarator".equals(declarator.getType())) {
                continue;
            }
            TSNode nameNode = firstChildOfType(declarator, "identifier");
            if (nameNode == null) {
                continue;
            }
            String name = nodeText(nameNode);
            String qn = ownerType != null ? ownerType.getQualifiedName() + "." + name
                    : prefix(name, new ArrayDeque<>());

            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(newId());
            symbol.setKind(isConst ? CodeSymbolKind.CONSTANT : CodeSymbolKind.FIELD);
            symbol.setName(name);
            symbol.setQualifiedName(qn);
            symbol.setParentId(ownerType != null ? ownerType.getId() : null);
            symbol.setAccessModifier(accessModifier(node));
            symbol.setLine(node.getStartPoint().getRow() + 1);
            result.getSymbols().add(symbol);
        }
    }

    private void visitInvocationExpression(TSNode node, Deque<String> namespaceStack,
                                           CodeSymbol ownerType, CodeFileAnalysisResult result) {
        TSNode fnNode = firstChildOfType(node, "member_access_expression");
        if (fnNode == null) {
            fnNode = lastIdentifierFunction(node);
        }
        if (fnNode == null) {
            return;
        }
        String fnText = nodeText(fnNode).trim();
        if (fnText.isEmpty()) {
            return;
        }

        CodeMethodCall call = new CodeMethodCall();
        call.setId(newId());
        call.setMethodName(fnText.substring(fnText.lastIndexOf('.') + 1));
        call.setCalleeQualifiedName(resolveCallQualifiedName(fnText, result));
        call.setCallType("FUNCTION_CALL");
        call.setLine(node.getStartPoint().getRow() + 1);
        call.setColumn((int) node.getStartPoint().getColumn());
        call.setConfidence(EdgeConfidence.EXTRACTED);
        call.setProvenance(EdgeProvenance.AST_EXTRACTION);
        if (ownerType != null) {
            call.setCallerId(ownerType.getId());
        }
        result.getCalls().add(call);
    }

    private String resolveCallQualifiedName(String fnText, CodeFileAnalysisResult result) {
        String[] parts = fnText.split("\\.");
        String last = parts[parts.length - 1];
        if (parts.length == 1) {
            for (CodeSymbol symbol : result.getSymbols()) {
                if (symbol.getName().equals(last)
                        && (symbol.getKind() == CodeSymbolKind.METHOD
                                || symbol.getKind() == CodeSymbolKind.FUNCTION)) {
                    return symbol.getQualifiedName();
                }
            }
            return fnText;
        }
        String head = String.join(".", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
        for (CodeSymbol symbol : result.getSymbols()) {
            if ((symbol.getKind() == CodeSymbolKind.CLASS
                    || symbol.getKind() == CodeSymbolKind.INTERFACE)
                    && (symbol.getName().equals(head)
                            || symbol.getQualifiedName().endsWith("." + head))) {
                return symbol.getQualifiedName() + "." + last;
            }
        }
        return fnText;
    }

    /**
     * invocation_expression 无 member_access 时（直呼），取最后 identifier 子。
     */
    private TSNode lastIdentifierFunction(TSNode invocation) {
        TSNode last = null;
        int childCount = invocation.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = invocation.getChild(i);
            if (child != null && "identifier".equals(child.getType())) {
                last = child;
            }
        }
        return last;
    }

    // ---- helpers ----

    /**
     * 声明名 = 停止节点（parameter_list/accessor_list/block/declaration_list/;）
     * 之前的最后一个 identifier 子节点——modifier/类型标识符都在它之前。
     */
    private String nameBeforeStop(TSNode node) {
        int childCount = node.getChildCount();
        String lastIdentifier = null;
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null) {
                continue;
            }
            String childType = child.getType();
            if ("parameter_list".equals(childType) || "accessor_list".equals(childType)
                    || "block".equals(childType) || "declaration_list".equals(childType)
                    || ";".equals(childType)) {
                break;
            }
            if ("identifier".equals(childType)) {
                lastIdentifier = nodeText(child);
            }
        }
        return lastIdentifier;
    }

    private String typeName(TSNode node) {
        return nameBeforeStop(node);
    }

    private boolean hasConstModifier(TSNode node) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && "modifier".equals(child.getType())
                    && nodeText(child).contains("const")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStructKeyword(TSNode node) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && "struct".equals(child.getType())) {
                return true;
            }
        }
        return false;
    }

    private String dottedName(TSNode node) {
        int childCount = node.getChildCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null || !child.isNamed()) {
                continue;
            }
            String childType = child.getType();
            if ("identifier".equals(childType) || "qualified_name".equals(childType)) {
                sb.append(nodeText(child));
            }
        }
        return sb.toString();
    }

    private CodeAccessModifier accessModifier(TSNode node) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && "modifier".equals(child.getType())) {
                String text = nodeText(child);
                if (text.contains("public")) {
                    return CodeAccessModifier.PUBLIC;
                }
                if (text.contains("private")) {
                    return CodeAccessModifier.PRIVATE;
                }
                if (text.contains("protected")) {
                    return CodeAccessModifier.PROTECTED;
                }
                if (text.contains("internal")) {
                    return CodeAccessModifier.PACKAGE_PRIVATE;
                }
            }
        }
        return CodeAccessModifier.PACKAGE_PRIVATE;
    }

    private static String prefix(String name, Deque<String> namespaceStack) {
        if (namespaceStack == null || namespaceStack.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : namespaceStack) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(part);
        }
        return sb.length() > 0 ? sb + "." + name : name;
    }

    private static TSNode firstChildOfType(TSNode node, String type) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && !child.isNull() && type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private String nodeText(TSNode node) {
        int startByte = node.getStartByte();
        int endByte = node.getEndByte();
        if (startByte >= endByte) {
            return "";
        }
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (endByte > bytes.length) {
            endByte = bytes.length;
        }
        return new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
    }

    private void addInheritance(CodeFileAnalysisResult result, String subTypeId,
                                String superTypeQn, CodeRelationType relationType) {
        CodeInheritance inheritance = new CodeInheritance();
        inheritance.setId(newId());
        inheritance.setSubTypeId(subTypeId);
        inheritance.setSuperTypeQualifiedName(superTypeQn);
        inheritance.setRelationType(relationType);
        inheritance.setProvenance(EdgeProvenance.AST_EXTRACTION);
        result.getInheritances().add(inheritance);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
