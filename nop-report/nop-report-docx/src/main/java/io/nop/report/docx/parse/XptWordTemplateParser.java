package io.nop.report.docx.parse;

import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.gen.XplGenConfig;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.docx.parse.WordTemplateParser;
import io.nop.xlang.api.XLangCompileTool;

public class XptWordTemplateParser extends WordTemplateParser {

    @Override
    protected void postProcessDocNode(WordOfficePackage pkg, XplGenConfig config, XLangCompileTool cp, XNode doc) {

    }
}
