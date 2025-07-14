package io.nop.report.docx.parse;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.ooxml.common.gen.XplGenConfig;
import io.nop.ooxml.docx.WordTemplate;
import io.nop.ooxml.docx.model.CommentsPart;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.docx.parse.WordTemplateParser;
import io.nop.report.docx.ReportDocxConstants;
import io.nop.xlang.api.XLangCompileTool;

import java.util.Map;

public class XptWordTemplateParser extends WordTemplateParser {

    @Override
    protected WordTemplate buildWordTemplate(WordOfficePackage pkg, IEvalAction beforeGen, Map<String, ITextTemplateOutput> outputs, IEvalAction afterGen, XplGenConfig config) {
        WordTemplate tpl = super.buildWordTemplate(pkg, beforeGen, outputs, afterGen, config);
        tpl.setRemoveComments(true);
        return tpl;
    }

    @Override
    protected void postProcessDocNode(WordOfficePackage pkg, XplGenConfig config, XLangCompileTool cp, XNode doc) {
        CommentsPart comments = pkg.getComments();
        if (comments == null)
            return;

        doc.forEachNode(node -> {
            if (node.getTagName().equals("w:tbl")) {
                if (isXptTable(node, comments)) {
                    XNode genNode = XNode.make("docx-gen:GenXptTable");
                    genNode.setAttr("xpl:lib", "/nop/report/xlib/docx-gen.xlib");
                    genNode.setAttr("dump", config.isDump());
                    node.replaceBy(genNode);
                    node.detach();
                    genNode.appendChild(node);
                }
            }
        });

        doc.forEachNode(node -> {
            String tagName = node.getTagName();
            if (tagName.equals("w:commentRangeStart") || tagName.equals("w:commentRangeEnd")
                    || tagName.equals("w:proofErr")) {
                node.setAttr("xpl:is", "c:ignore");
            } else if (tagName.equals("w:commentReference")) {
                node.setAttr("xpl:is", "c:ignore");
                if (node.getParent().getTagName().equals("w:r")) {
                    node.getParent().setAttr("xpl:is", "c:ignore");
                }
            }
        });
    }

    boolean isXptTable(XNode node, CommentsPart comments) {
        XNode tr = node.childByTag("w:tr");
        if (tr != null) {
            XNode tc = tr.childByTag("w:tc");
            if (tc != null) {
                XNode ref = tc.findByTag("w:commentReference");
                if (ref != null) {
                    String id = ref.attrText("w:id");
                    if (id != null) {
                        String comment = comments.getComment(id);
                        if (comment != null) {
                            return comment.contains(ReportDocxConstants.VAR_XPT_TABLE);
                        }
                    }
                }
            }
        }
        return false;
    }
}
