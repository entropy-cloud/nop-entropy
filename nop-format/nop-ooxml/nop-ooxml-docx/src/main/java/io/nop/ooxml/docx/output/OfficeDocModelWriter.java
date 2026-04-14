/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.output;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.xlang.api.XLang;

import java.util.List;

/**
 * Orchestrator that converts an OfficeDocModel into a complete DOCX file. Builds
 * XNode trees via OfficeDocXmlBuilder, assembles a WordOfficePackage, and writes
 * it to an IResource.
 */
public class OfficeDocModelWriter {

    private final OfficeDocXmlBuilder builder = new OfficeDocXmlBuilder();

    public void writeToResource(OfficeDocModel doc, IResource resource) {
        WordOfficePackage pkg = buildPackage(doc);
        pkg.saveToResource(resource, XLang.newEvalScope());
    }

    public WordOfficePackage buildPackage(OfficeDocModel doc) {
        WordOfficePackage pkg = new WordOfficePackage();

        OfficeDocXmlBuilder xmlBuilder = this.builder;

        // Build document.xml
        XNode documentNode = xmlBuilder.buildDocumentXml(doc);
        pkg.addFile(new XmlOfficePackagePart(DocxConstants.PATH_WORD_DOCUMENT, documentNode));

        // Build header/footer parts if present
        boolean hasHeader = false;
        boolean hasFooter = false;
        for (OfficeDocPageModel page : doc.getPages()) {
            if (!page.getHeader().isEmpty()) {
                hasHeader = true;
            }
            if (!page.getFooter().isEmpty()) {
                hasFooter = true;
            }
        }

        // Add relationships for document.xml
        OfficeRelsPart rels = new OfficeRelsPart(DocxConstants.PATH_WORD_RELS);

        if (hasHeader) {
            String headerPath = "word/header1.xml";
            List<OfficeBlock> headerBlocks = doc.getPages().get(0).getHeader();
            XNode headerNode = xmlBuilder.buildHeaderFooterXml(headerBlocks);
            headerNode.setTagName("w:hdr");
            headerNode.setAttr("xmlns:w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main");
            headerNode.setAttr("xmlns:r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships");
            pkg.addFile(new XmlOfficePackagePart(headerPath, headerNode));
            rels.addRelationship(new OfficeRelationship(null, "rIdHdr1",
                    OfficeConstants.NS_HEADER, "header1.xml", null));
        }

        if (hasFooter) {
            String footerPath = "word/footer1.xml";
            List<OfficeBlock> footerBlocks = doc.getPages().get(0).getFooter();
            XNode footerNode = xmlBuilder.buildHeaderFooterXml(footerBlocks);
            footerNode.setTagName("w:ftr");
            footerNode.setAttr("xmlns:w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main");
            footerNode.setAttr("xmlns:r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships");
            pkg.addFile(new XmlOfficePackagePart(footerPath, footerNode));
            rels.addRelationship(new OfficeRelationship(null, "rIdFtr1",
                    OfficeConstants.NS_FOOTER, "footer1.xml", null));
        }

        pkg.addFile(rels);

        // Minimal styles.xml
        XNode stylesNode = XNode.make("w:styles");
        stylesNode.setAttr("xmlns:w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main");
        pkg.addFile(new XmlOfficePackagePart("word/styles.xml", stylesNode));

        // _rels/.rels
        OfficeRelsPart rootRels = new OfficeRelsPart("_rels/.rels");
        rootRels.addRelationship(new OfficeRelationship(null, "rId1",
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument",
                "word/document.xml", null));
        pkg.addFile(rootRels);

        // [Content_Types].xml
        ContentTypesPart contentTypes = pkg.getContentTypes();
        contentTypes.addDefaultContentType("rels",
                "application/vnd.openxmlformats-package.relationships+xml");
        contentTypes.addDefaultContentType("xml", "text/xml");

        return pkg;
    }
}
