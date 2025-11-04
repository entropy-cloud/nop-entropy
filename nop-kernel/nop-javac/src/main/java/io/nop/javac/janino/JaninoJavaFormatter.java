package io.nop.javac.janino;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.formatter.ITextFormatter;
import io.nop.javac.JavaCompileTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaninoJavaFormatter implements ITextFormatter {
    public static final JaninoJavaFormatter INSTANCE = new JaninoJavaFormatter();

    static final Logger LOG = LoggerFactory.getLogger(JaninoJavaFormatter.class);

    @Override
    public String format(SourceLocation loc, String source, boolean ignoreErrors) {
        try {
            return new JavaCompileTool().parseJavaSource(loc, source).getFormattedSource();
        } catch (RuntimeException e) {
            LOG.debug("nop.javac.format-java-failed", e);
            if (ignoreErrors)
                return source;
            else
                throw e;
        }
    }
}