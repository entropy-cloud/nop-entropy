package io.nop.converter.impl;

import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObjectBuilder;

public class ExcelAdapter {
    public static IDocumentObjectBuilder newXlsxDslDocumentObjectBuilder() {
        return new XlsxDslDocumentObjectBuilder();
    }

    public static IDocumentObjectBuilder newXlsxDocumentObjectBuilder() {
        return new XlsxDocumentObjectBuilder();
    }

    public static IDocumentConverter newDslToExcelDocumentConverter() {
        return new DslToExcelDocumentConverter();
    }
}