package io.nop.report.core.engine.renderer;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.tree.ITreeVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.resource.IResource;

import java.io.File;

public class SimpleHtmlSplitter {
    private final boolean indent;

    public SimpleHtmlSplitter(boolean indent) {
        this.indent = indent;
    }

    public void split(IResource inputFile, File targetDir) {
        XNode root = XNodeParser.instance().forFragments(true).parseFromResource(inputFile);
        split(root, targetDir);
    }

    public void split(XNode node, File targetDir) {
        node.visit(new ITreeVisitor<>() {
            @Override
            public TreeVisitResult beginNode(XNode node) {
                String sheetName = node.attrText("data-sheet-name");
                if (!StringHelper.isBlank(sheetName)) {
                    sheetName = StringHelper.safeFileName(sheetName);
                    File targetFile = new File(targetDir, sheetName + ".shtml");
                    FileHelper.writeText(targetFile, node.outerXml(indent,true), null);
                    return TreeVisitResult.SKIP_CHILD;
                }
                return ITreeVisitor.super.beginNode(node);
            }
        });
    }
}
