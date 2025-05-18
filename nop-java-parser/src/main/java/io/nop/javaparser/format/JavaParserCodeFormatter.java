package io.nop.javaparser.format;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.formatter.ITextFormatter;
import io.nop.commons.util.StringHelper;
import io.nop.javaparser.utils.JavaParserHelper;

import static io.nop.javaparser.JavaParserErrors.ARG_PARSE_RESULT;
import static io.nop.javaparser.JavaParserErrors.ERR_JAVA_PARSER_PARSE_FAILED;

public class JavaParserCodeFormatter implements ITextFormatter {
    public static JavaParserCodeFormatter INSTANCE = new JavaParserCodeFormatter();

    // 默认配置
    private static final PrinterConfiguration DEFAULT_CONFIG =
            new DefaultPrinterConfiguration()
                    .addOption(new DefaultConfigurationOption(
                            DefaultPrinterConfiguration.ConfigOption.END_OF_LINE_CHARACTER, "\n"));

    public String formatCompilationUnit(CompilationUnit cu) {
        return new DefaultPrettyPrinter(DEFAULT_CONFIG).print(cu);
    }

    @Override
    public String format(SourceLocation loc, String sourceCode, boolean ignoreErrors) {
        if (StringHelper.isBlank(sourceCode)) {
            if (ignoreErrors)
                return sourceCode;
            else
                throw new IllegalArgumentException("sourceCode is empty");
        }

        // 解析源代码
        ParseResult<CompilationUnit> parseResult = new JavaParser().parse(sourceCode);

        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            // 格式化代码
            return formatCompilationUnit(parseResult.getResult().get());
        } else {
            // 解析失败时返回原始代码（或可以抛出异常）
            if (ignoreErrors)
                return sourceCode;
            SourceLocation problemLoc = JavaParserHelper.getProblemLocation(loc, parseResult.getProblems().get(0));
            throw new NopException(ERR_JAVA_PARSER_PARSE_FAILED).loc(problemLoc).param(ARG_PARSE_RESULT, parseResult.toString());
        }
    }
}
