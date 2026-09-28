package io.nop.code.lang.rust;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.nop.treesitter.compat.TSNode;
import io.nop.treesitter.compat.TSParser;
import io.nop.treesitter.compat.TSTree;
import io.nop.treesitter.compat.TreeSitterRust;

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
 * Rust 文件分析器。
 * 使用 nop-treesitter 纯 Java 运行时解析 Rust 源代码，提取符号（函数/方法/结构体/
 * 枚举/trait/类型别名/常量）、继承边（impl trait→self-type = IMPLEMENTS、trait
 * supertrait = EXTENDS）、调用与 use 导入。
 *
 * <p>语法裁定（RustTreeProbe 实测 + 审查 M1-M3）：pub 项首命名子是
 * visibility_modifier（不得当名称）；impl 的 trait = `for` 匿名节点之前最后一个
 * 命名子，self type = `for` 之后第一个命名子（generic impl 覆盖）；顶层
 * function_item → FUNCTION，declaration_list 内 function_item /
 * function_signature_item → METHOD；EXTENDS 仅取 trait_item 的直接 trait_bounds
 * 子（struct 泛型 bounds 语义上非继承，排除）。
 */
public class RustCodeFileAnalyzer implements ICodeFileAnalyzer {

    private static final TreeSitterRust TS_LANGUAGE = new TreeSitterRust();

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.RUST;
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
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(filePath);
        result.setSourceCode(sourceCode);
        result.setLanguage(CodeLanguage.RUST);

        walkNode(root, null, null, result);

        tree = null;
        return result;
    }

    @Override
    public List<String> getFileExtensions() {
        return java.util.Collections.singletonList(".rs");
    }

    // ---- walking ----

    private void walkNode(TSNode node, CodeSymbol ownerType, String implTrait, CodeFileAnalysisResult result) {
        String type = node.getType();

        switch (type) {
            case "use_declaration":
                visitUseDeclaration(node, result);
                return;
            case "struct_item":
            case "enum_item":
            case "union_item":
                visitTypeLikeItem(node, type, result);
                return;
            case "trait_item":
                visitTraitItem(node, result);
                return;
            case "type_item":
                visitTypeAlias(node, result);
                return;
            case "const_item":
            case "static_item":
                visitConstLike(node, result);
                return;
            case "impl_item":
                visitImplItem(node, result);
                return;
            case "function_item":
                visitFunctionItem(node, ownerType, implTrait, result);
                return;
            case "function_signature_item":
                visitFunctionSignatureItem(node, ownerType, result);
                return;
            case "call_expression":
                visitCallExpression(node, result);
                break;
            default:
                break;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                walkNode(child, ownerType, implTrait, result);
            }
        }
    }

    private void visitUseDeclaration(TSNode node, CodeFileAnalysisResult result) {
        TSNode pathNode = firstChildOfType(node, "scoped_identifier");
        if (pathNode == null) {
            pathNode = firstChildOfType(node, "identifier");
        }
        if (pathNode == null) {
            return;
        }
        String path = nodeText(pathNode, result.getSourceCode());

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.IMPORT);
        symbol.setName(path);
        symbol.setQualifiedName(path);
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        result.getSymbols().add(symbol);
        if (!result.getImports().contains(path)) {
            result.getImports().add(path);
        }
    }

    private void visitTypeLikeItem(TSNode node, String type, CodeFileAnalysisResult result) {
        TSNode nameNode = firstChildOfType(node, "type_identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, result.getSourceCode());

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.CLASS);
        symbol.setName(name);
        symbol.setQualifiedName(name);
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node, result.getSourceCode()));
        result.getSymbols().add(symbol);

        // named fields: struct fields → FIELD symbols
        TSNode fieldList = firstChildOfType(node, "field_declaration_list");
        if (fieldList != null) {
            visitStructFields(fieldList, symbol, result);
        }
        TSNode variantList = firstChildOfType(node, "enum_variant_list");
        if (variantList != null) {
            visitEnumVariants(variantList, symbol, result);
        }
    }

    private void visitStructFields(TSNode fieldList, CodeSymbol owner, CodeFileAnalysisResult result) {
        int childCount = fieldList.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = fieldList.getChild(i);
            if (child == null || !"field_declaration".equals(child.getType())) {
                continue;
            }
            TSNode nameNode = firstChildOfType(child, "field_identifier");
            if (nameNode == null) {
                continue;
            }
            String name = nodeText(nameNode, result.getSourceCode());
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(newId());
            symbol.setKind(CodeSymbolKind.FIELD);
            symbol.setName(name);
            symbol.setQualifiedName(owner.getQualifiedName() + "." + name);
            symbol.setParentId(owner.getId());
            symbol.setAccessModifier(accessModifier(child));
            symbol.setLine(child.getStartPoint().getRow() + 1);
            symbol.setColumn((int) child.getStartPoint().getColumn());
            result.getSymbols().add(symbol);
        }
    }

    private void visitEnumVariants(TSNode variantList, CodeSymbol owner, CodeFileAnalysisResult result) {
        int childCount = variantList.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = variantList.getChild(i);
            if (child == null || !"enum_variant".equals(child.getType())) {
                continue;
            }
            TSNode nameNode = firstChildOfType(child, "identifier");
            if (nameNode == null) {
                continue;
            }
            String name = nodeText(nameNode, result.getSourceCode());
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(newId());
            symbol.setKind(CodeSymbolKind.CONSTANT);
            symbol.setName(name);
            symbol.setQualifiedName(owner.getQualifiedName() + "." + name);
            symbol.setParentId(owner.getId());
            symbol.setLine(child.getStartPoint().getRow() + 1);
            result.getSymbols().add(symbol);
        }
    }

    private void visitTraitItem(TSNode node, CodeFileAnalysisResult result) {
        TSNode nameNode = firstChildOfType(node, "type_identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, result.getSourceCode());

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.INTERFACE);
        symbol.setName(name);
        symbol.setQualifiedName(name);
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node, result.getSourceCode()));
        result.getSymbols().add(symbol);

        // supertrait: direct trait_bounds child → EXTENDS edge per bound type
        TSNode bounds = firstChildOfType(node, "trait_bounds");
        if (bounds != null) {
            int boundCount = bounds.getChildCount();
            for (int i = 0; i < boundCount; i++) {
                TSNode bound = bounds.getChild(i);
                if (bound == null || !bound.isNamed() || ":".equals(bound.getType())) {
                    continue;
                }
                String boundType = nodeText(bound, result.getSourceCode()).trim();
                if (boundType.isEmpty() || "+".equals(boundType)) {
                    continue;
                }
                addInheritance(result, symbol.getId(), boundType, CodeRelationType.EXTENDS);
            }
        }

        // trait methods: declaration_list > function_signature_item
        TSNode body = firstChildOfType(node, "declaration_list");
        if (body != null) {
            int itemCount = body.getChildCount();
            for (int i = 0; i < itemCount; i++) {
                TSNode item = body.getChild(i);
                if (item != null) {
                    walkNode(item, symbol, null, result);
                }
            }
        }
    }

    private void visitTypeAlias(TSNode node, CodeFileAnalysisResult result) {
        TSNode nameNode = firstChildOfType(node, "type_identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, result.getSourceCode());
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.TYPE_ALIAS);
        symbol.setName(name);
        symbol.setQualifiedName(name);
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        result.getSymbols().add(symbol);
    }

    private void visitConstLike(TSNode node, CodeFileAnalysisResult result) {
        TSNode nameNode = firstChildOfType(node, "identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, result.getSourceCode());
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.CONSTANT);
        symbol.setName(name);
        symbol.setQualifiedName(name);
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        result.getSymbols().add(symbol);
    }

    private void visitImplItem(TSNode node, CodeFileAnalysisResult result) {
        // trait = last named child before the anonymous `for`; self type = first
        // named child after `for` (generic impl `impl<T> Super for Pair<T>`:
        // first named child is type_parameters, so scan all children).
        String traitName = null;
        String selfType = null;
        boolean seenFor = false;
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null) {
                continue;
            }
            String childType = child.getType();
            if ("for".equals(childType)) {
                seenFor = true;
                continue;
            }
            if (!child.isNamed() || "where_clause".equals(childType)
                    || "declaration_list".equals(childType)) {
                continue;
            }
            if (!seenFor) {
                if ("type_parameters".equals(childType)) {
                    continue;
                }
                traitName = nodeText(child, result.getSourceCode()).trim();
            } else if (selfType == null) {
                selfType = nodeText(child, result.getSourceCode()).trim();
            }
        }

        if (!seenFor && selfType == null) {
            // inherent impl `impl Animal { ... }`: the single type is the self type
            selfType = traitName;
            traitName = null;
        }

        CodeSymbol owner = null;
        if (selfType != null) {
            String selfQn = stripGenericArgs(selfType);
            for (CodeSymbol symbol : result.getSymbols()) {
                if (selfQn.equals(symbol.getQualifiedName())
                        && (symbol.getKind() == CodeSymbolKind.CLASS
                                || symbol.getKind() == CodeSymbolKind.INTERFACE)) {
                    owner = symbol;
                    break;
                }
            }
        }

        if (traitName != null && selfType != null && !traitName.isEmpty() && !selfType.isEmpty()) {
            String traitQn = stripGenericArgs(traitName);
            String subTypeId = owner != null ? owner.getId() : stripGenericArgs(selfType);
            addInheritance(result, subTypeId, traitQn, CodeRelationType.IMPLEMENTS);
        }
        int declaredChildren = node.getChildCount();
        for (int i = 0; i < declaredChildren; i++) {
            TSNode child = node.getChild(i);
            if (child != null && "declaration_list".equals(child.getType())) {
                int listCount = child.getChildCount();
                for (int j = 0; j < listCount; j++) {
                    TSNode item = child.getChild(j);
                    if (item != null) {
                        walkNode(item, owner, traitName, result);
                    }
                }
            }
        }
    }

    private void visitFunctionItem(TSNode node, CodeSymbol ownerType, String implTrait,
                                   CodeFileAnalysisResult result) {
        TSNode nameNode = firstChildOfType(node, "identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, result.getSourceCode());
        boolean isMethod = ownerType != null;

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(isMethod ? CodeSymbolKind.METHOD : CodeSymbolKind.FUNCTION);
        symbol.setName(name);
        symbol.setQualifiedName(isMethod ? ownerType.getQualifiedName() + "." + name : name);
        symbol.setParentId(isMethod ? ownerType.getId() : null);
        symbol.setAccessModifier(accessModifier(node));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node, result.getSourceCode()));
        if (isMethod && implTrait != null) {
            symbol.setDocumentation("impl " + implTrait);
        }
        result.getSymbols().add(symbol);

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                walkNode(child, ownerType, implTrait, result);
            }
        }
    }

    private void visitFunctionSignatureItem(TSNode node, CodeSymbol ownerType,
                                            CodeFileAnalysisResult result) {
        TSNode nameNode = firstChildOfType(node, "identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, result.getSourceCode());

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.METHOD);
        symbol.setName(name);
        symbol.setQualifiedName(ownerType != null ? ownerType.getQualifiedName() + "." + name : name);
        symbol.setParentId(ownerType != null ? ownerType.getId() : null);
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        result.getSymbols().add(symbol);
    }

    private void visitCallExpression(TSNode node, CodeFileAnalysisResult result) {
        TSNode fnNode = node.getChildByFieldName("function");
        if (fnNode == null || fnNode.isNull()) {
            return;
        }
        String fnText = nodeText(fnNode, result.getSourceCode()).trim();
        if (fnText.isEmpty()) {
            return;
        }

        CodeMethodCall call = new CodeMethodCall();
        call.setId(newId());
        String methodName = fnText;
        if (methodName.contains("::")) {
            methodName = methodName.substring(methodName.lastIndexOf("::") + 2);
        } else if (methodName.contains(".")) {
            methodName = methodName.substring(methodName.lastIndexOf('.') + 1);
        }
        call.setMethodName(methodName);
        call.setCalleeQualifiedName(resolveCallQualifiedName(fnText, result));
        call.setCallType("FUNCTION_CALL");
        call.setLine(node.getStartPoint().getRow() + 1);
        call.setColumn((int) node.getStartPoint().getColumn());
        call.setConfidence(EdgeConfidence.EXTRACTED);
        call.setProvenance(EdgeProvenance.AST_EXTRACTION);
        result.getCalls().add(call);
    }

    private String resolveCallQualifiedName(String fnText, CodeFileAnalysisResult result) {
        // scoped path parts use `::`, method-style uses `.` — normalize for lookup
        String[] parts = fnText.contains("::") ? fnText.split("::") : new String[]{fnText};
        String last = parts[parts.length - 1];
        if (parts.length == 1) {
            for (CodeSymbol symbol : result.getSymbols()) {
                if (symbol.getName().equals(last)
                        && (symbol.getKind() == CodeSymbolKind.FUNCTION
                                || symbol.getKind() == CodeSymbolKind.METHOD)) {
                    return symbol.getQualifiedName();
                }
            }
            return fnText;
        }
        String head = String.join("::", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
        for (CodeSymbol symbol : result.getSymbols()) {
            if ((symbol.getKind() == CodeSymbolKind.CLASS
                    || symbol.getKind() == CodeSymbolKind.INTERFACE
                    || symbol.getKind() == CodeSymbolKind.TYPE_ALIAS)
                    && (symbol.getName().equals(head)
                            || symbol.getQualifiedName().endsWith("::" + head)
                            || symbol.getQualifiedName().equals(head))) {
                return symbol.getQualifiedName() + "." + last;
            }
        }
        return fnText;
    }

    private void addInheritance(CodeFileAnalysisResult result, String subTypeQn, String superTypeQn,
                                CodeRelationType relationType) {
        CodeInheritance inheritance = new CodeInheritance();
        inheritance.setId(newId());
        inheritance.setSubTypeId(subTypeQn);
        inheritance.setSuperTypeQualifiedName(superTypeQn);
        inheritance.setRelationType(relationType);
        inheritance.setProvenance(EdgeProvenance.AST_EXTRACTION);
        result.getInheritances().add(inheritance);
    }

    private static String stripGenericArgs(String typeText) {
        int angle = typeText.indexOf('<');
        return angle > 0 ? typeText.substring(0, angle).trim() : typeText.trim();
    }

    private static CodeAccessModifier accessModifier(TSNode node) {
        return firstChildOfType(node, "visibility_modifier") != null
                ? CodeAccessModifier.PUBLIC
                : CodeAccessModifier.PACKAGE_PRIVATE;
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

    private static String nodeText(TSNode node, String source) {
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

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
