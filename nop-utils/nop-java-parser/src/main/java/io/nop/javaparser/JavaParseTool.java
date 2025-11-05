package io.nop.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.javac.IJavaSourceParser;
import io.nop.javaparser.parse.JavaParserParseResult;
import io.nop.javaparser.utils.JavaParserHelper;

import static io.nop.javaparser.JavaParserErrors.ARG_PARSE_RESULT;
import static io.nop.javaparser.JavaParserErrors.ERR_JAVA_PARSER_PARSE_FAILED;

public class JavaParseTool implements IJavaSourceParser {
    public static JavaParseTool instance() {
        return new JavaParseTool();
    }

    protected boolean resolveSymbol = true;

    public JavaParseTool resolveSymbol(boolean resolveSymbol) {
        this.resolveSymbol = resolveSymbol;
        return this;
    }

    @Override
    public JavaParserParseResult parseJavaSource(SourceLocation loc, String source) {
        JavaParser parser = getJavaParser();
        ParseResult<CompilationUnit> parseResult = parser.parse(source);

        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            // 格式化代码
            return new JavaParserParseResult(loc, parseResult.getResult().get());
        } else {
            SourceLocation problemLoc = JavaParserHelper.getProblemLocation(loc, parseResult.getProblems().get(0));
            throw new NopException(ERR_JAVA_PARSER_PARSE_FAILED).loc(problemLoc).param(ARG_PARSE_RESULT, parseResult.toString());
        }
    }

    protected JavaParser getJavaParser() {
        // 配置Symbol Solver
        JavaParser parser = new JavaParser();
        if (resolveSymbol) {
            TypeSolver typeSolver = newTypeResolve();

            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            parser.getParserConfiguration().setSymbolResolver(symbolSolver);
        }
        return parser;
    }

    protected TypeSolver newTypeResolve() {
        return new NopTypeResolve();
    }

    static class NopTypeResolve extends ReflectionTypeSolver {
        @Override
        protected boolean filterName(String name) {
            return name.startsWith("io.nop.") || name.startsWith("java.") || name.startsWith("jakarta.");
        }
    }
}