/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.output;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.model.table.CellPosition;
import io.nop.core.resource.tpl.AbstractXmlTemplate;
import io.nop.excel.model.IExcelSheet;

import java.util.Collections;
import java.util.UUID;

public class ExcelCommentsWriter extends AbstractXmlTemplate {
    private final IExcelSheet sheet;

    public ExcelCommentsWriter(IExcelSheet sheet) {
        this.sheet = sheet;
    }

    @Override
    public void generateXml(IXNodeHandler out, IEvalContext context) {
        out.beginDoc("UTF-8", null, null);

        out.beginNode(null, "comments", attrs(
                "xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
                "xmlns:mc", "http://schemas.openxmlformats.org/markup-compatibility/2006",
                "mc:Ignorable", "xr",
                "xmlns:xr", "http://schemas.microsoft.com/office/spreadsheetml/2014/revision"
        ));

        out.beginNode("authors");
        out.beginNode("author");
        out.value(null, "nop");
        out.endNode("author");
        out.endNode("authors");

        out.beginNode(null, "commentList", Collections.emptyMap());
        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            String comment = cell.getComment();
            if (!StringHelper.isEmpty(comment)) {
                String ref = CellPosition.toABString(rowIndex, colIndex);
                out.beginNode(null, "comment", attrs(
                        "ref", ref,
                        "authorId", "0",
                        "shapeId", "0",
                        "xr:uid", "{" + UUID.randomUUID() + "}"
                ));

                // <text><r><rPr><b/><sz val="9"/><color indexed="81"/><rFont val="宋体"/><family val="3"/><charset val="134"/></rPr><t>a=1
                //b=2</t></r></text>
                out.beginNode(null, "text", Collections.emptyMap());
                out.beginNode("r");
                out.beginNode("rPr");
                out.simpleNode("b");
                out.simpleNode(null, "sz", attrs("val", "9"));
                out.simpleNode(null, "color", attrs("indexed", "81"));
                out.simpleNode(null, "rFont", attrs("val", "SimSun")); // 宋体
                out.simpleNode(null, "family", attrs("val", 3));
                out.simpleNode(null, "charset", attrs("val", "134"));
                out.endNode("rPr");
                out.beginNode("t");
                out.value(null, comment);
                out.endNode("t");
                out.endNode("r");
                out.endNode("text");

                out.endNode("comment");
            }
            return ProcessResult.CONTINUE;
        });
        out.endNode("commentList");
        out.endNode("comments");

        out.endDoc();
    }
}
