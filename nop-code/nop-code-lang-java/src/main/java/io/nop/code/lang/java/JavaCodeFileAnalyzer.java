package io.nop.code.lang.java;

import io.nop.api.core.util.SourceLocation;
import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.model.*;
import io.nop.javaparser.analyzer.*;

import java.util.Collections;
import java.util.List;

/**
 * Java code file analyzer - wraps nop-java-parser and converts results to universal model
 */
public class JavaCodeFileAnalyzer implements ICodeFileAnalyzer {

    private final JavaFileAnalyzer delegate = new JavaFileAnalyzer();

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.JAVA;
    }

    @Override
    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }

        try {
            JavaFileAnalysisResult javaResult = delegate.analyze(
                    SourceLocation.fromPath(filePath), sourceCode);

            if (javaResult == null) {
                return null;
            }

            return convertResult(javaResult, sourceCode);
        } catch (Exception e) {
            // Invalid Java source
            return null;
        }
    }

    @Override
    public List<String> getFileExtensions() {
        return Collections.singletonList(".java");
    }

    private CodeFileAnalysisResult convertResult(JavaFileAnalysisResult javaResult, String sourceCode) {
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(javaResult.getFilePath());
        result.setSourceCode(sourceCode);
        result.setLineCount(countLines(sourceCode));
        result.setLanguage(CodeLanguage.JAVA);
        result.setPackageName(javaResult.getPackageName());
        result.setImports(javaResult.getImports());

        // Convert symbols
        for (SymbolInfo si : javaResult.getSymbols()) {
            result.getSymbols().add(convertSymbol(si));
        }

        // Convert calls
        for (MethodCall mc : javaResult.getCalls()) {
            result.getCalls().add(convertCall(mc));
        }

        // Convert inheritances
        for (InheritanceInfo ii : javaResult.getInheritances()) {
            result.getInheritances().add(convertInheritance(ii));
        }

        // Convert annotations
        for (AnnotationUsage au : javaResult.getAnnotationUsages()) {
            result.getAnnotationUsages().add(convertAnnotation(au));
        }

        return result;
    }

    private CodeSymbol convertSymbol(SymbolInfo si) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(si.getId());
        symbol.setKind(convertKind(si.getKind()));
        symbol.setName(si.getName());
        symbol.setQualifiedName(si.getQualifiedName());
        symbol.setAccessModifier(convertAccessModifier(si.getAccessModifier()));
        symbol.setDeprecated(si.isDeprecated());
        symbol.setDocumentation(si.getDocumentation());
        symbol.setLine(si.getLine());
        symbol.setColumn(si.getColumn());
        symbol.setEndLine(si.getEndLine());
        symbol.setEndColumn(si.getEndColumn());
        symbol.setParentId(si.getParentId());
        symbol.setDeclaringSymbolId(si.getDeclaringSymbolId());
        symbol.setSuperClassName(si.getSuperClassName());
        symbol.setAbstractFlag(si.isAbstractFlag());
        symbol.setFinalFlag(si.isFinalFlag());
        symbol.setSignature(si.getSignature());
        symbol.setReturnType(si.getReturnType());
        symbol.setStaticFlag(si.isStaticFlag());
        symbol.setFieldType(si.getFieldType());

        // Store Java-specific flags in extData as JSON
        if (si.isSynchronizedFlag() || si.isNativeFlag() || si.isVolatileFlag() || si.isTransientFlag()) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            if (si.isSynchronizedFlag()) {
                if (!first) json.append(",");
                json.append("\"synchronized\":true");
                first = false;
            }
            if (si.isNativeFlag()) {
                if (!first) json.append(",");
                json.append("\"native\":true");
                first = false;
            }
            if (si.isVolatileFlag()) {
                if (!first) json.append(",");
                json.append("\"volatile\":true");
                first = false;
            }
            if (si.isTransientFlag()) {
                if (!first) json.append(",");
                json.append("\"transient\":true");
                first = false;
            }
            json.append("}");
            symbol.setExtData(json.toString());
        }

        return symbol;
    }

    private CodeSymbolKind convertKind(SymbolKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case CLASS:
                return CodeSymbolKind.CLASS;
            case INTERFACE:
                return CodeSymbolKind.INTERFACE;
            case ENUM:
                return CodeSymbolKind.ENUM;
            case ENUM_CONSTANT:
                return CodeSymbolKind.CONSTANT;
            case ANNOTATION_TYPE:
                return CodeSymbolKind.ANNOTATION_TYPE;
            case METHOD:
                return CodeSymbolKind.METHOD;
            case CONSTRUCTOR:
                return CodeSymbolKind.CONSTRUCTOR;
            case FIELD:
                return CodeSymbolKind.FIELD;
            default:
                return null;
        }
    }

    private CodeAccessModifier convertAccessModifier(AccessModifier am) {
        if (am == null) return null;
        switch (am) {
            case PUBLIC:
                return CodeAccessModifier.PUBLIC;
            case PROTECTED:
                return CodeAccessModifier.PROTECTED;
            case PRIVATE:
                return CodeAccessModifier.PRIVATE;
            case PACKAGE_PRIVATE:
                return CodeAccessModifier.PACKAGE_PRIVATE;
            default:
                return null;
        }
    }

    private CodeMethodCall convertCall(MethodCall mc) {
        CodeMethodCall call = new CodeMethodCall();
        call.setId(mc.getId());
        call.setCallerId(mc.getCallerId());
        call.setCalleeId(mc.getCalleeId());
        call.setCalleeQualifiedName(mc.getCalleeQualifiedName());
        call.setMethodName(mc.getMethodName());
        call.setArgumentTypes(mc.getArgumentTypes());
        call.setCallType(mc.getCallType());
        call.setContext(mc.getContext());
        call.setLine(mc.getLine());
        call.setColumn(mc.getColumn());
        return call;
    }

    private CodeInheritance convertInheritance(InheritanceInfo ii) {
        CodeInheritance inh = new CodeInheritance();
        inh.setId(ii.getId());
        inh.setSubTypeId(ii.getSubTypeId());
        inh.setSuperTypeQualifiedName(ii.getSuperTypeQualifiedName());
        if (ii.getRelationType() != null) {
            inh.setRelationType(convertRelationType(ii.getRelationType()));
        }
        return inh;
    }

    private CodeRelationType convertRelationType(RelationType rt) {
        switch (rt) {
            case EXTENDS:
                return CodeRelationType.EXTENDS;
            case IMPLEMENTS:
                return CodeRelationType.IMPLEMENTS;
            default:
                return null;
        }
    }

    private CodeAnnotationUsage convertAnnotation(AnnotationUsage au) {
        CodeAnnotationUsage annotation = new CodeAnnotationUsage();
        annotation.setId(au.getId());
        annotation.setAnnotationTypeQualifiedName(au.getAnnotationTypeQualifiedName());
        annotation.setAnnotatedSymbolId(au.getAnnotatedSymbolId());
        annotation.setAttributes(au.getAttributes());
        annotation.setLine(au.getLine());
        annotation.setColumn(au.getColumn());
        return annotation;
    }

    private int countLines(String source) {
        if (source == null || source.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') count++;
        }
        return count;
    }
}
