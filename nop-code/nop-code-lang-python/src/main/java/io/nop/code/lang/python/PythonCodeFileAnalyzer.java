package io.nop.code.lang.python;

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
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterPython;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Python文件分析器
 * 使用bonede tree-sitter解析Python源代码，提取符号信息、继承关系等
 */
public class PythonCodeFileAnalyzer implements ICodeFileAnalyzer {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.PYTHON;
    }

    @Override
    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }

        String moduleName = pathToModuleName(filePath);

        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterPython());

        TSTree tree = parser.parseString(null, sourceCode);
        if (tree == null) {
            return null;
        }

        TSNode root = tree.getRootNode();
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(filePath);
        result.setSourceCode(sourceCode);
        result.setLineCount(countLines(sourceCode));
        result.setLanguage(CodeLanguage.PYTHON);

        walkNode(root, sourceCode, moduleName, null, result);

        return result;
    }

    @Override
    public List<String> getFileExtensions() {
        return Collections.singletonList(".py");
    }

    // ---- node text helper ----

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

    // ---- tree walking ----

    private void walkNode(TSNode node, String source, String modulePrefix,
                          CodeSymbol parentSymbol, CodeFileAnalysisResult result) {
        String type = node.getType();
        int childCount = node.getChildCount();

        if ("class_definition".equals(type)) {
            visitClassDefinition(node, source, modulePrefix, parentSymbol, result);
            return;
        }

        if ("function_definition".equals(type)) {
            visitFunctionDefinition(node, source, modulePrefix, parentSymbol, result);
            return;
        }

        if ("decorator".equals(type)) {
            visitDecorator(node, source, parentSymbol, result);
        }

        if ("import_statement".equals(type) || "import_from_statement".equals(type)) {
            visitImport(node, source, result);
        }

        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                walkNode(child, source, modulePrefix, parentSymbol, result);
            }
        }
    }

    private void visitClassDefinition(TSNode node, String source, String modulePrefix,
                                      CodeSymbol parentSymbol, CodeFileAnalysisResult result) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());
        symbol.setKind(CodeSymbolKind.CLASS);

        String className = getFirstChildByType(node, "identifier")
                .map(id -> nodeText(id, source)).orElse("Unknown");
        symbol.setName(className);

        String qualifiedName = buildQualifiedName(modulePrefix, parentSymbol, className);
        symbol.setQualifiedName(qualifiedName);

        symbol.setAccessModifier(inferAccessModifier(className));

        // tree-sitter rows are 0-based, convert to 1-based
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setColumn(node.getStartPoint().getColumn());
        symbol.setEndColumn(node.getEndPoint().getColumn());

        if (parentSymbol != null) {
            symbol.setParentId(parentSymbol.getId());
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        }

        // Handle argument_list for base classes: class Foo(Base1, Base2)
        TSNode argList = getFirstChildByType(node, "argument_list").orElse(null);
        if (argList != null) {
            extractInheritances(argList, source, symbol, result);
        }

        processDecoratorsOnDefinition(node, source, symbol, result);

        TSNode classBlock = getFirstChildByType(node, "block").orElse(null);
        if (classBlock != null) {
            String doc = extractDocstring(classBlock, source);
            if (doc != null) {
                symbol.setDocumentation(doc);
            }
        }

        result.getSymbols().add(symbol);

        TSNode block = getFirstChildByType(node, "block").orElse(null);
        if (block != null) {
            walkBlockChildren(block, source, modulePrefix, symbol, result);
        }
    }

    private void visitFunctionDefinition(TSNode node, String source, String modulePrefix,
                                         CodeSymbol parentSymbol, CodeFileAnalysisResult result) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(UUID.randomUUID().toString());

        boolean isMethod = parentSymbol != null && parentSymbol.getKind() == CodeSymbolKind.CLASS;
        symbol.setKind(isMethod ? CodeSymbolKind.METHOD : CodeSymbolKind.FUNCTION);

        String funcName = getFirstChildByType(node, "identifier")
                .map(id -> nodeText(id, source)).orElse("Unknown");
        symbol.setName(funcName);

        String qualifiedName = buildQualifiedName(modulePrefix, parentSymbol, funcName);
        symbol.setQualifiedName(qualifiedName);

        symbol.setAccessModifier(inferAccessModifier(funcName));

        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setColumn(node.getStartPoint().getColumn());
        symbol.setEndColumn(node.getEndPoint().getColumn());

        if (parentSymbol != null) {
            symbol.setParentId(parentSymbol.getId());
            symbol.setDeclaringSymbolId(parentSymbol.getId());
        }

        TSNode params = getFirstChildByType(node, "parameters").orElse(null);
        if (params != null) {
            symbol.setSignature(funcName + nodeText(params, source));
        } else {
            symbol.setSignature(funcName + "()");
        }

        // Check for async
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (child != null && "async".equals(child.getType())) {
                symbol.setAsyncFlag(true);
                break;
            }
        }

        processDecoratorsOnDefinition(node, source, symbol, result);

        TSNode funcBlock = getFirstChildByType(node, "block").orElse(null);
        if (funcBlock != null) {
            String doc = extractDocstring(funcBlock, source);
            if (doc != null) {
                symbol.setDocumentation(doc);
            }
        }

        result.getSymbols().add(symbol);

        TSNode block = getFirstChildByType(node, "block").orElse(null);
        if (block != null) {
            walkBlockForCalls(block, source, symbol, result);
        }
    }

    private void visitDecorator(TSNode node, String source, CodeSymbol parentSymbol,
                                CodeFileAnalysisResult result) {
        // Decorators are processed in processDecoratorsOnDefinition instead
    }

    private void visitImport(TSNode node, String source, CodeFileAnalysisResult result) {
        String importText = nodeText(node, source);
        result.getImports().add(importText);
    }

    private void processDecoratorsOnDefinition(TSNode defNode, String source,
                                               CodeSymbol symbol, CodeFileAnalysisResult result) {
        TSNode parent = defNode.getParent();
        if (parent == null) return;

        // Find this node's index in parent
        int defIndex = -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            TSNode child = parent.getChild(i);
            if (child != null && child.equals(defNode)) {
                defIndex = i;
                break;
            }
        }

        // Walk backwards to find decorators
        for (int i = defIndex - 1; i >= 0; i--) {
            TSNode sibling = parent.getChild(i);
            if (sibling == null) break;
            if ("decorator".equals(sibling.getType())) {
                CodeAnnotationUsage usage = new CodeAnnotationUsage();
                usage.setId(UUID.randomUUID().toString());
                usage.setAnnotatedSymbolId(symbol.getId());
                usage.setLine(sibling.getStartPoint().getRow() + 1);
                usage.setColumn(sibling.getStartPoint().getColumn());

                String decoratorText = nodeText(sibling, source);
                if (decoratorText.startsWith("@")) {
                    decoratorText = decoratorText.substring(1);
                }
                int parenIdx = decoratorText.indexOf('(');
                if (parenIdx > 0) {
                    String args = decoratorText.substring(parenIdx);
                    decoratorText = decoratorText.substring(0, parenIdx);
                    usage.setAttributes(args);
                }
                usage.setAnnotationTypeQualifiedName(decoratorText);

                result.getAnnotationUsages().add(usage);
            } else {
                break;
            }
        }
    }

    private void extractInheritances(TSNode argList, String source,
                                     CodeSymbol classSymbol, CodeFileAnalysisResult result) {
        for (int i = 0; i < argList.getChildCount(); i++) {
            TSNode child = argList.getChild(i);
            if (child == null) continue;
            if (child.isNamed()) {
                CodeInheritance inheritance = new CodeInheritance();
                inheritance.setId(UUID.randomUUID().toString());
                inheritance.setSubTypeId(classSymbol.getId());
                inheritance.setSuperTypeQualifiedName(nodeText(child, source));
                inheritance.setRelationType(CodeRelationType.EXTENDS);
                result.getInheritances().add(inheritance);
            }
        }
    }


    private boolean isTripleQuoted(String text) {
        if (text == null || text.length() < 6) return false;
        char c = text.charAt(0);
        if (c != '"' && c != '\'') return false;
        return text.charAt(1) == c && text.charAt(2) == c
                && text.charAt(text.length() - 1) == c && text.charAt(text.length() - 2) == c
                && text.charAt(text.length() - 3) == c;
    }

    private String extractDocstring(TSNode block, String source) {
        if (block == null) return null;
        for (int i = 0; i < block.getChildCount(); i++) {
            TSNode child = block.getChild(i);
            if (!child.isNamed()) continue;
            if ("expression_statement".equals(child.getType())) {
                for (int j = 0; j < child.getChildCount(); j++) {
                    TSNode stmtChild = child.getChild(j);
                    String nodeType = stmtChild.getType();
                    if (stmtChild.isNamed() && ("string".equals(nodeType) || "string_literal".equals(nodeType))) {
                        String text = nodeText(stmtChild, source);
                        if (isTripleQuoted(text)) {
                            return text.substring(3, text.length() - 3).trim();
                        } else if (text.length() >= 2) {
                            return text.substring(1, text.length() - 1).trim();
                        }
                        return text;
                    }
                }
            }
            break;
        }
        return null;
    }

    private void walkBlockChildren(TSNode block, String source, String modulePrefix,
                                   CodeSymbol parentSymbol, CodeFileAnalysisResult result) {
        for (int i = 0; i < block.getChildCount(); i++) {
            TSNode child = block.getChild(i);
            if (child == null) continue;
            String type = child.getType();

            if ("class_definition".equals(type)) {
                visitClassDefinition(child, source, modulePrefix, parentSymbol, result);
            } else if ("function_definition".equals(type)) {
                visitFunctionDefinition(child, source, modulePrefix, parentSymbol, result);
            } else if ("decorator".equals(type)) {
                // Skip standalone decorators; they're handled by processDecoratorsOnDefinition
            } else if ("expression_statement".equals(type)) {
                walkExpressionStatement(child, source, modulePrefix, parentSymbol, result);
            }
        }
    }

    private void walkExpressionStatement(TSNode exprStmt, String source, String modulePrefix,
                                         CodeSymbol parentSymbol, CodeFileAnalysisResult result) {
        for (int i = 0; i < exprStmt.getChildCount(); i++) {
            TSNode child = exprStmt.getChild(i);
            if (child == null) continue;
            // Look for assignment or other relevant constructs
        }
    }

    private void walkBlockForCalls(TSNode block, String source,
                                   CodeSymbol callerSymbol, CodeFileAnalysisResult result) {
        walkNodeForCalls(block, source, callerSymbol, result);
    }

    private void walkNodeForCalls(TSNode node, String source,
                                  CodeSymbol callerSymbol, CodeFileAnalysisResult result) {
        String type = node.getType();

        if ("call".equals(type)) {
            CodeMethodCall call = new CodeMethodCall();
            call.setId(UUID.randomUUID().toString());
            call.setCallerId(callerSymbol.getId());
            call.setLine(node.getStartPoint().getRow() + 1);
            call.setColumn(node.getStartPoint().getColumn());

            for (int i = 0; i < node.getChildCount(); i++) {
                TSNode child = node.getChild(i);
                if (child == null) continue;
                if (child.isNamed()) {
                    call.setMethodName(nodeText(child, source));
                    if ("attribute".equals(child.getType())) {
                        TSNode attr = child.getChildByFieldName("attribute");
                        if (attr != null) {
                            call.setMethodName(nodeText(attr, source));
                        }
                        TSNode obj = child.getChildByFieldName("object");
                        if (obj != null) {
                            call.setContext(nodeText(obj, source));
                        }
                    }
                    break;
                }
            }

            if (call.getMethodName() != null) {
                result.getCalls().add(call);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                walkNodeForCalls(child, source, callerSymbol, result);
            }
        }
    }

    // ---- utility helpers ----

    private java.util.Optional<TSNode> getFirstChildByType(TSNode node, String type) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (child != null && type.equals(child.getType())) {
                return java.util.Optional.of(child);
            }
        }
        return java.util.Optional.empty();
    }

    private String buildQualifiedName(String modulePrefix, CodeSymbol parent, String name) {
        if (parent != null) {
            return parent.getQualifiedName() + "." + name;
        }
        if (modulePrefix != null && !modulePrefix.isEmpty()) {
            return modulePrefix + "." + name;
        }
        return name;
    }

    private CodeAccessModifier inferAccessModifier(String name) {
        if (name == null) return CodeAccessModifier.NO_MODIFIER;
        if (name.startsWith("_")) {
            return CodeAccessModifier.PRIVATE;
        }
        return CodeAccessModifier.PUBLIC;
    }

    private String pathToModuleName(String filePath) {
        if (filePath == null) return "";
        String module = filePath.replace('\\', '/');
        if (module.endsWith(".py")) {
            module = module.substring(0, module.length() - 3);
        }
        module = module.replace('/', '.');
        while (module.startsWith(".")) {
            module = module.substring(1);
        }
        return module;
    }

}
