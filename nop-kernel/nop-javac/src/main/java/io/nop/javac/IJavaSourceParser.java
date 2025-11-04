package io.nop.javac;

import io.nop.api.core.util.SourceLocation;

public interface IJavaSourceParser {

    IJavaParseResult parseJavaSource(SourceLocation loc, String source);
}
