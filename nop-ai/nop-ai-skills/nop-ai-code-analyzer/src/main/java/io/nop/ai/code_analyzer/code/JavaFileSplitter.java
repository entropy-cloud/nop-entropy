package io.nop.ai.code_analyzer.code;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.nop.ai.core.commons.splitter.IAiTextSplitter;
import io.nop.ai.core.commons.splitter.SimpleTextSplitter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.javaparser.utils.JavaParserHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JavaFileSplitter implements IAiTextSplitter {
    static final Logger LOG = LoggerFactory.getLogger(JavaFileSplitter.class);

    private static final String CHUNK_TYPE = "JAVA_METHODS";

    @Override
    public List<SplitChunk> split(SourceLocation loc, String javaCode, SplitOptions options) {
        if (javaCode == null || javaCode.trim().isEmpty()) {
            return List.of();
        }

        try {
            CompilationUnit cu = JavaParserHelper.parseJavaSource(loc, javaCode);
            List<MethodDeclaration> methods = collectMethods(cu);
            return splitIntoChunks(cu, methods, options);
        } catch (Exception e) {
            LOG.error("nop.ai.parse-java-file-fail", e);
            if (options.isIgnoreParseError()) {
                return new SimpleTextSplitter().split(loc, javaCode, options);
            }
            throw NopException.adapt(e);
        }
    }

    private List<MethodDeclaration> collectMethods(CompilationUnit cu) {
        List<MethodDeclaration> methods = new ArrayList<>();
        cu.accept(new MethodCollector(), methods);
        return methods;
    }

    private List<SplitChunk> splitIntoChunks(CompilationUnit cu,
                                             List<MethodDeclaration> methods,
                                             SplitOptions options) {
        List<SplitChunk> chunks = new ArrayList<>();
        List<List<MethodDeclaration>> methodGroups = groupMethods(methods, options);

        for (int i = 0; i < methodGroups.size(); i++) {
            CompilationUnit chunkCu = createChunkCompilationUnit(cu, methodGroups.get(i));
            chunks.add(createSplitChunk(chunkCu, i + 1));
        }

        return chunks;
    }

    private List<List<MethodDeclaration>> groupMethods(List<MethodDeclaration> methods,
                                                       SplitOptions options) {
        List<List<MethodDeclaration>> groups = new ArrayList<>();
        List<MethodDeclaration> currentGroup = new ArrayList<>();
        int currentSize = 0;

        for (MethodDeclaration method : methods) {
            int methodSize = method.toString().length();

            if (!currentGroup.isEmpty() &&
                    (exceedsElementLimit(currentGroup, options) ||
                            exceedsSizeLimit(currentSize, methodSize, options))) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentSize = 0;
            }

            currentGroup.add(method);
            currentSize += methodSize;
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    private boolean exceedsElementLimit(List<MethodDeclaration> group, SplitOptions options) {
        return options.getMaxElementsPerChunk() > 0 &&
                group.size() >= options.getMaxElementsPerChunk();
    }

    private boolean exceedsSizeLimit(int currentSize, int additionalSize, SplitOptions options) {
        return options.getMaxContentSize() > 0 &&
                currentSize + additionalSize > options.getMaxContentSize();
    }

    private CompilationUnit createChunkCompilationUnit(CompilationUnit originalCu,
                                                       List<MethodDeclaration> methodsToKeep) {
        CompilationUnit chunkCu = originalCu.clone();
        MethodFilter filter = new MethodFilter(methodsToKeep);
        filter.visit(chunkCu, methodsToKeep);
        filter.applyRemoval();
        return chunkCu;
    }

    private SplitChunk createSplitChunk(CompilationUnit chunkCu, int chunkNumber) {
        return new SplitChunk(CHUNK_TYPE, chunkCu.toString(), "part_" + chunkNumber);
    }

    private static class MethodCollector extends VoidVisitorAdapter<List<MethodDeclaration>> {
        @Override
        public void visit(MethodDeclaration md, List<MethodDeclaration> collector) {
            super.visit(md, collector);
            collector.add(md);
        }
    }

    private static class MethodFilter extends VoidVisitorAdapter<List<MethodDeclaration>> {
        private final List<MethodDeclaration> methodsToKeep;
        private final List<MethodDeclaration> methodsToRemove = new ArrayList<>();

        MethodFilter(List<MethodDeclaration> methodsToKeep) {
            this.methodsToKeep = methodsToKeep;
        }

        @Override
        public void visit(MethodDeclaration md, List<MethodDeclaration> arg) {
            super.visit(md, arg);
            if (!methodsToKeep.contains(md)) {
                methodsToRemove.add(md);
            }
        }

        public void applyRemoval() {
            methodsToRemove.forEach(MethodDeclaration::remove);
        }
    }
}