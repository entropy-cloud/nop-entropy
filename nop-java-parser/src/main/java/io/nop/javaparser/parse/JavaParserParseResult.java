package io.nop.javaparser.parse;

import com.github.javaparser.ast.CompilationUnit;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.javac.IJavaParseResult;
import io.nop.javaparser.format.JavaParserCodeFormatter;

import java.io.Writer;

public class JavaParserParseResult implements IJavaParseResult {
    private final SourceLocation loc;
    private final CompilationUnit compilationUnit;

    public JavaParserParseResult(SourceLocation loc, CompilationUnit compilationUnit) {
        this.loc = loc;
        this.compilationUnit = compilationUnit;
    }

    public SourceLocation getSourceLocation() {
        return loc;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    @Override
    public String getFormattedSource() {
        return JavaParserCodeFormatter.INSTANCE.formatCompilationUnit(compilationUnit);
    }

    @Override
    public void outputFormattedSource(Writer out) {
        try {
            out.write(getFormattedSource());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
