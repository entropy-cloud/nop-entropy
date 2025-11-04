package io.nop.commons.text.formatter;

import io.nop.api.core.util.SourceLocation;

public interface ITextFormatter {

    String format(SourceLocation loc, String text, boolean ignoreErrors);
}
