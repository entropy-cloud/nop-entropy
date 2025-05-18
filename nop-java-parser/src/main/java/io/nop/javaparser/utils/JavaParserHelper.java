package io.nop.javaparser.utils;

import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseException;
import com.github.javaparser.Problem;
import com.github.javaparser.Token;
import com.github.javaparser.TokenRange;
import io.nop.api.core.util.SourceLocation;

import java.util.Optional;

public class JavaParserHelper {
    public static SourceLocation getProblemLocation(SourceLocation baseLoc, Problem problem) {
        Optional<TokenRange> tokenRange = problem.getLocation();
        if (!tokenRange.isPresent()) {
            return getErrorLocation(baseLoc, problem.getCause().orElse(null));
        }

        return tokenRange.map(range -> {
            int line = getBeginLine(range.getBegin());
            int col = getBeginCol(range.getBegin());

            if (baseLoc == null) {
                return SourceLocation.fromLine("dynamic.java", line, col);
            }

            return baseLoc.offset(line - 1, col);
        }).orElse(null);
    }

    public static SourceLocation getErrorLocation(SourceLocation baseLoc, Throwable cause) {
        if (cause == null) {
            return null;
        }

        if (cause instanceof ParseException) {
            Token token = ((ParseException) cause).currentToken;
            if (token != null)
                return getTokenLocation(baseLoc, token);
        }

        return null;
    }

    public static SourceLocation getTokenLocation(SourceLocation baseLoc, Token token) {
        if (token == null) {
            return null;
        }

        int line = token.beginLine;
        int col = token.beginColumn;

        if (baseLoc == null) {
            return SourceLocation.fromLine("dynamic.java", line, col);
        }

        return baseLoc.offset(line - 1, col);
    }

    static int getBeginLine(JavaToken token) {
        return token.getRange().map(range -> range.begin.line).orElse(0);
    }

    static int getBeginCol(JavaToken token) {
        return token.getRange().map(range -> range.begin.column).orElse(0);
    }
}
