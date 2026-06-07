package io.nop.code.lang.typescript.analyzer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterTypescript;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeAnnotationUsage;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeRelationType;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.model.EdgeProvenance;
/**
 * TypeScript/TSX 文件分析器
 * 使用 bonede tree-sitter-typescript 解析源代码，提取符号信息、继承关系和装饰器。
 * <p>
 * TreeSitterTypescript 同时处理 .ts 和 .tsx 文件。
 */
public class TypeScriptCodeFileAnalyzer implements ICodeFileAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(TypeScriptCodeFileAnalyzer.class);

    private static final TSLanguage TS_LANGUAGE = new TreeSitterTypescript();

    private static final List<String> EXTENSIONS = Arrays.asList(".ts", ".tsx");

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.TYPESCRIPT;
    }

    @Override
    public List<String> getFileExtensions() {
        return EXTENSIONS;
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
        if (root.isNull()) {
            return null;
        }

        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(filePath);
        result.setSourceCode(sourceCode);
        result.setLineCount(countLines(sourceCode));
        result.setLanguage(CodeLanguage.TYPESCRIPT);

        byte[] sourceBytes = sourceCode.getBytes(StandardCharsets.UTF_8);
        String qualifiedPrefix = buildQualifiedPrefix(filePath);

        walkNode(root, sourceCode, sourceBytes, result, qualifiedPrefix, null);

        tree = null;
        return result;
    }

    private void walkNode(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                          String qualifiedPrefix, CodeSymbol parentSymbol) {
        if (node.isNull()) {
            return;
        }

        String type = node.getType();
        int childCount = node.getChildCount();

        switch (type) {
            case "class_declaration":
                handleClassDeclaration(node, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
                return;
            case "interface_declaration":
                handleInterfaceDeclaration(node, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
                return;
            case "enum_declaration":
                handleEnumDeclaration(node, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
                return;
            case "function_declaration":
                handleFunctionDeclaration(node, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
                return;
            case "method_definition":
                handleMethodDefinition(node, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
                return;
            case "method_signature":
                handleMethodSignature(node, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
                return;
            case "public_field_definition":
            case "property_signature":
            case "property_declaration":
                handleProperty(node, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
                return;
            default:
                break;
        }

        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            walkNode(child, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
        }
    }

    private void handleClassDeclaration(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                        String qualifiedPrefix, CodeSymbol parentSymbol) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(CodeSymbolKind.CLASS);
        symbol.setName(getName(node, source, sourceBytes));
        symbol.setAccessModifier(getAccessModifier(node, source, sourceBytes));
        symbol.setAbstractFlag(hasModifier(node, "abstract"));

        setLineInfo(symbol, node);

        String qualifiedName = qualifiedPrefix + "." + symbol.getName();
        symbol.setQualifiedName(qualifiedName);

        if (parentSymbol != null) {
            symbol.setParentId(parentSymbol.getId());
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        }

        result.getSymbols().add(symbol);

        processHeritageClauses(node, source, sourceBytes, result, symbol);
        processDecorators(node, source, sourceBytes, result, symbol);

        TSNode body = node.getChildByFieldName("body");
        if (!body.isNull()) {
            walkChildren(body, source, sourceBytes, result, qualifiedPrefix, symbol);
        }
    }

    private void handleInterfaceDeclaration(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                            String qualifiedPrefix, CodeSymbol parentSymbol) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(CodeSymbolKind.INTERFACE);
        symbol.setName(getName(node, source, sourceBytes));
        symbol.setAccessModifier(getAccessModifier(node, source, sourceBytes));

        setLineInfo(symbol, node);

        String qualifiedName = qualifiedPrefix + "." + symbol.getName();
        symbol.setQualifiedName(qualifiedName);

        if (parentSymbol != null) {
            symbol.setParentId(parentSymbol.getId());
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        }

        result.getSymbols().add(symbol);

        processHeritageClauses(node, source, sourceBytes, result, symbol);
        processDecorators(node, source, sourceBytes, result, symbol);

        TSNode body = node.getChildByFieldName("body");
        if (!body.isNull()) {
            walkChildren(body, source, sourceBytes, result, qualifiedPrefix, symbol);
        }
    }

    private void handleEnumDeclaration(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                       String qualifiedPrefix, CodeSymbol parentSymbol) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(CodeSymbolKind.ENUM);
        symbol.setName(getName(node, source, sourceBytes));
        symbol.setAccessModifier(getAccessModifier(node, source, sourceBytes));

        setLineInfo(symbol, node);

        String qualifiedName = qualifiedPrefix + "." + symbol.getName();
        symbol.setQualifiedName(qualifiedName);

        if (parentSymbol != null) {
            symbol.setParentId(parentSymbol.getId());
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        }

        result.getSymbols().add(symbol);

        processDecorators(node, source, sourceBytes, result, symbol);
    }

    private void handleFunctionDeclaration(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                           String qualifiedPrefix, CodeSymbol parentSymbol) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(parentSymbol != null && isTypeSymbol(parentSymbol) ? CodeSymbolKind.METHOD : CodeSymbolKind.FUNCTION);
        symbol.setName(getName(node, source, sourceBytes));
        symbol.setAccessModifier(getAccessModifier(node, source, sourceBytes));
        symbol.setAsyncFlag(hasModifier(node, "async"));

        setLineInfo(symbol, node);

        String qualifiedName;
        if (parentSymbol != null) {
            qualifiedName = parentSymbol.getQualifiedName() + "." + symbol.getName();
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        } else {
            qualifiedName = qualifiedPrefix + "." + symbol.getName();
        }
        symbol.setQualifiedName(qualifiedName);

        result.getSymbols().add(symbol);

        processDecorators(node, source, sourceBytes, result, symbol);
        walkNodeForCalls(node, source, sourceBytes, symbol, result);
    }

    private void handleMethodDefinition(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                        String qualifiedPrefix, CodeSymbol parentSymbol) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(CodeSymbolKind.METHOD);
        symbol.setName(getName(node, source, sourceBytes));
        symbol.setAccessModifier(getAccessModifier(node, source, sourceBytes));
        symbol.setAsyncFlag(hasModifier(node, "async"));
        symbol.setStaticFlag(hasModifier(node, "static"));
        symbol.setAbstractFlag(hasModifier(node, "abstract"));

        setLineInfo(symbol, node);

        String qualifiedName;
        if (parentSymbol != null) {
            qualifiedName = parentSymbol.getQualifiedName() + "." + symbol.getName();
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        } else {
            qualifiedName = qualifiedPrefix + "." + symbol.getName();
        }
        symbol.setQualifiedName(qualifiedName);

        result.getSymbols().add(symbol);

        processDecorators(node, source, sourceBytes, result, symbol);
        walkNodeForCalls(node, source, sourceBytes, symbol, result);
    }

    private void handleMethodSignature(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                        String qualifiedPrefix, CodeSymbol parentSymbol) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(CodeSymbolKind.METHOD);
        symbol.setName(getName(node, source, sourceBytes));
        symbol.setAccessModifier(getAccessModifier(node, source, sourceBytes));

        setLineInfo(symbol, node);

        String qualifiedName;
        if (parentSymbol != null) {
            qualifiedName = parentSymbol.getQualifiedName() + "." + symbol.getName();
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        } else {
            qualifiedName = qualifiedPrefix + "." + symbol.getName();
        }
        symbol.setQualifiedName(qualifiedName);

        result.getSymbols().add(symbol);
    }

    private void handleProperty(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                String qualifiedPrefix, CodeSymbol parentSymbol) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(CodeSymbolKind.FIELD);
        symbol.setName(getName(node, source, sourceBytes));
        symbol.setAccessModifier(getAccessModifier(node, source, sourceBytes));
        symbol.setStaticFlag(hasModifier(node, "static"));
        symbol.setReadonlyFlag(hasModifier(node, "readonly"));

        setLineInfo(symbol, node);

        TSNode typeNode = node.getChildByFieldName("type");
        if (!typeNode.isNull()) {
            String fieldTypeName = getNodeText(typeNode, sourceBytes);
            symbol.setFieldType(fieldTypeName);
            symbol.setRawFieldType(extractRawType(fieldTypeName));
        }

        String qualifiedName;
        if (parentSymbol != null) {
            qualifiedName = parentSymbol.getQualifiedName() + "." + symbol.getName();
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        } else {
            qualifiedName = qualifiedPrefix + "." + symbol.getName();
        }
        symbol.setQualifiedName(qualifiedName);

        result.getSymbols().add(symbol);
    }

    private void processHeritageClauses(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                        CodeSymbol ownerSymbol) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            String childType = child.getType();

            if ("class_heritage".equals(childType)) {
                processClassHeritage(child, source, sourceBytes, result, ownerSymbol);
            } else if ("extends_clause".equals(childType)) {
                addHeritage(child, source, sourceBytes, result, ownerSymbol, CodeRelationType.EXTENDS);
            } else if ("implements_clause".equals(childType)) {
                addHeritage(child, source, sourceBytes, result, ownerSymbol, CodeRelationType.IMPLEMENTS);
            }
        }
    }

    private void processClassHeritage(TSNode heritageNode, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                      CodeSymbol ownerSymbol) {
        int count = heritageNode.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = heritageNode.getChild(i);
            String childType = child.getType();

            if ("extends_clause".equals(childType)) {
                addHeritage(child, source, sourceBytes, result, ownerSymbol, CodeRelationType.EXTENDS);
            } else if ("implements_clause".equals(childType)) {
                addHeritage(child, source, sourceBytes, result, ownerSymbol, CodeRelationType.IMPLEMENTS);
            }
        }
    }

    private void addHeritage(TSNode clauseNode, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                             CodeSymbol ownerSymbol, CodeRelationType relationType) {
        int count = clauseNode.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = clauseNode.getChild(i);
            if (!child.isNamed()) continue;

            String superTypeName = extractTypeName(child, sourceBytes);
            if (superTypeName != null && !superTypeName.isEmpty()) {
                CodeInheritance inheritance = new CodeInheritance();
                inheritance.setId(UUID.randomUUID().toString());
                inheritance.setSubTypeId(ownerSymbol.getId());
                inheritance.setSuperTypeQualifiedName(superTypeName);
                inheritance.setRelationType(relationType);
                inheritance.setProvenance(EdgeProvenance.AST_EXTRACTION);
                result.getInheritances().add(inheritance);
            }
        }
    }

    private String extractTypeName(TSNode typeNode, byte[] sourceBytes) {
        String nodeType = typeNode.getType();
        switch (nodeType) {
            case "type_identifier":
                return getNodeText(typeNode, sourceBytes);
            case "generic_type":
            case "nested_type_identifier": {
                TSNode nameNode = typeNode.getChildByFieldName("name");
                if (!nameNode.isNull()) {
                    return getNodeText(nameNode, sourceBytes);
                }
                int count = typeNode.getChildCount();
                for (int i = 0; i < count; i++) {
                    TSNode child = typeNode.getChild(i);
                    if (child.isNamed()) {
                        return getNodeText(child, sourceBytes);
                    }
                }
                return getNodeText(typeNode, sourceBytes);
            }
            default:
                return getNodeText(typeNode, sourceBytes);
        }
    }

    private void processDecorators(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                                   CodeSymbol ownerSymbol) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if ("decorator".equals(child.getType())) {
                addDecoratorUsage(child, sourceBytes, result, ownerSymbol);
            }
        }

        TSNode parent = node.getParent();
        if (!parent.isNull()) {
            int nodeStartByte = node.getStartByte();
            int parentCount = parent.getChildCount();
            for (int i = 0; i < parentCount; i++) {
                TSNode sibling = parent.getChild(i);
                if ("decorator".equals(sibling.getType()) && sibling.getEndByte() <= nodeStartByte) {
                    addDecoratorUsage(sibling, sourceBytes, result, ownerSymbol);
                }
            }
        }
    }

    private void addDecoratorUsage(TSNode decoratorNode, byte[] sourceBytes, CodeFileAnalysisResult result,
                                    CodeSymbol ownerSymbol) {
        CodeAnnotationUsage usage = new CodeAnnotationUsage();
        usage.setId(UUID.randomUUID().toString());
        usage.setAnnotationTypeQualifiedName(extractDecoratorName(decoratorNode, sourceBytes));
        usage.setAnnotatedSymbolId(ownerSymbol.getId());
        usage.setProvenance(EdgeProvenance.AST_EXTRACTION);
        usage.setLine(decoratorNode.getStartPoint().getRow() + 1);
        usage.setColumn(decoratorNode.getStartPoint().getColumn());
        result.getAnnotationUsages().add(usage);
    }

    private void walkNodeForCalls(TSNode node, String source, byte[] sourceBytes,
                                  CodeSymbol callerSymbol, CodeFileAnalysisResult result) {
        String type = node.getType();

        if ("call_expression".equals(type)) {
            CodeMethodCall call = new CodeMethodCall();
            call.setId(UUID.randomUUID().toString());
            call.setCallerId(callerSymbol.getId());
            call.setProvenance(EdgeProvenance.AST_EXTRACTION);
            call.setLine(node.getStartPoint().getRow() + 1);
            call.setColumn(node.getStartPoint().getColumn());

            TSNode funcNode = node.getChildByFieldName("function");
            if (!funcNode.isNull()) {
                String funcType = funcNode.getType();
                if ("member_expression".equals(funcType)) {
                    TSNode propNode = funcNode.getChildByFieldName("property");
                    if (!propNode.isNull()) {
                        call.setMethodName(getNodeText(propNode, sourceBytes));
                    }
                    TSNode objNode = funcNode.getChildByFieldName("object");
                    if (!objNode.isNull()) {
                        call.setContext(getNodeText(objNode, sourceBytes));
                    }
                } else {
                    call.setMethodName(getNodeText(funcNode, sourceBytes));
                }
            }

            if (call.getMethodName() != null) {
                result.getCalls().add(call);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (child != null && child.isNamed()) {
                walkNodeForCalls(child, source, sourceBytes, callerSymbol, result);
            }
        }
    }

    private String extractDecoratorName(TSNode decoratorNode, byte[] sourceBytes) {
        int count = decoratorNode.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = decoratorNode.getChild(i);
            if (child.isNamed()) {
                if ("identifier".equals(child.getType())) {
                    return getNodeText(child, sourceBytes);
                } else if ("call_expression".equals(child.getType())) {
                    TSNode funcNode = child.getChildByFieldName("function");
                    if (!funcNode.isNull()) {
                        return getNodeText(funcNode, sourceBytes);
                    }
                    int callCount = child.getChildCount();
                    for (int j = 0; j < callCount; j++) {
                        TSNode callChild = child.getChild(j);
                        if (callChild.isNamed()) {
                            return getNodeText(callChild, sourceBytes);
                        }
                    }
                }
                return getNodeText(child, sourceBytes);
            }
        }
        return getNodeText(decoratorNode, sourceBytes);
    }

    /**
     * Tree-sitter does NOT provide node.getText(). Use byte offsets instead.
     */
    private String getNodeText(TSNode node, byte[] sourceBytes) {
        int startByte = node.getStartByte();
        int endByte = node.getEndByte();
        if (startByte >= endByte) {
            return "";
        }
        if (endByte > sourceBytes.length) {
            endByte = sourceBytes.length;
        }
        return new String(sourceBytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
    }

    private String getName(TSNode node, String source, byte[] sourceBytes) {
        TSNode nameNode = node.getChildByFieldName("name");
        if (!nameNode.isNull()) {
            String text = getNodeText(nameNode, sourceBytes);
            if (text != null && !text.isEmpty()) {
                return text;
            }
        }
        return "<anonymous>";
    }

    /**
     * Set line info converting from tree-sitter 0-based to CodeSymbol 1-based rows.
     */
    private void setLineInfo(CodeSymbol symbol, TSNode node) {
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn(node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setEndColumn(node.getEndPoint().getColumn());
    }

    private CodeAccessModifier getAccessModifier(TSNode node, String source, byte[] sourceBytes) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if ("accessibility_modifier".equals(child.getType())) {
                String text = getNodeText(child, sourceBytes);
                if ("public".equals(text)) {
                    return CodeAccessModifier.PUBLIC;
                } else if ("private".equals(text)) {
                    return CodeAccessModifier.PRIVATE;
                } else if ("protected".equals(text)) {
                    return CodeAccessModifier.PROTECTED;
                }
            }
        }
        return CodeAccessModifier.NO_MODIFIER;
    }

    private boolean hasModifier(TSNode node, String modifier) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (modifier.equals(child.getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMethodLike(CodeSymbol symbol) {
        return symbol.getKind() == CodeSymbolKind.METHOD
                || symbol.getKind() == CodeSymbolKind.CONSTRUCTOR
                || symbol.getKind() == CodeSymbolKind.FUNCTION;
    }

    private boolean isTypeSymbol(CodeSymbol symbol) {
        return symbol.getKind() == CodeSymbolKind.CLASS
                || symbol.getKind() == CodeSymbolKind.INTERFACE
                || symbol.getKind() == CodeSymbolKind.ENUM;
    }

    private void walkChildren(TSNode node, String source, byte[] sourceBytes, CodeFileAnalysisResult result,
                              String qualifiedPrefix, CodeSymbol parentSymbol) {
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = node.getChild(i);
            walkNode(child, source, sourceBytes, result, qualifiedPrefix, parentSymbol);
        }
    }

    /**
     * Build a qualified-name prefix from the file path.
     * <p>
     * <b>Design limitation:</b> This implementation uses the raw file path, so the resulting
     * qualified names include the {@code src/} prefix (e.g. {@code src.utils.helper} instead of
     * {@code utils.helper}). A complete fix requires parsing {@code tsconfig.json} to determine
     * the actual module root, which is not yet available.
     */
    private String buildQualifiedPrefix(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        String normalized = filePath.replace('\\', '/');
        int srcIdx = normalized.indexOf("src/");
        if (srcIdx >= 0) {
            normalized = normalized.substring(srcIdx + 4);
        }
        int dotIdx = normalized.lastIndexOf('.');
        if (dotIdx > 0) {
            normalized = normalized.substring(0, dotIdx);
        }
        return normalized.replace('/', '.');
    }

    private static String extractRawType(String typeText) {
        if (typeText == null) return null;
        int idx = typeText.indexOf('<');
        return idx >= 0 ? typeText.substring(0, idx) : typeText;
    }

}
