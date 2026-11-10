/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.output;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.constants.ContentTypes;
import io.nop.ooxml.common.impl.ResourceOfficePackagePart;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.docx.output.OfficeDocXmlBuilder.PartContext;
import io.nop.xlang.api.XLang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.ooxml.common.OfficeErrors.ARG_FILE_EXT;
import static io.nop.ooxml.common.OfficeErrors.ARG_PATH;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_UNSUPPORTED_CONTENT_TYPE;
import static io.nop.ooxml.docx.DocxErrors.ARG_REASON;
import static io.nop.ooxml.docx.DocxErrors.ARG_TARGET_ID;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_GEN_IMAGE_INVALID;

/**
 * Orchestrator that converts an OfficeDocModel into a complete DOCX file (WI5):
 * multi-section document.xml (per-page sectPr placement), per-section headerN/
 * footerN parts, media parts from doc.assets, named styles.xml, settings part for
 * the document background, and the full [Content_Types].xml override set.
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
        xmlBuilder.setDoc(doc);

        ContentTypesPart contentTypes = pkg.getContentTypes();
        contentTypes.addDefaultContentType("rels",
                "application/vnd.openxmlformats-package.relationships+xml");
        contentTypes.addDefaultContentType("xml", "text/xml");

        // ---- media parts from doc.assets --------------------------------------
        // assetRef -> package-internal part path (parsed assets already carry
        // "word/media/..." names; those are kept verbatim so image assetRefs
        // round-trip unchanged, unlike WordOfficePackage.addImage which renumbers)
        Map<String, String> mediaTargets = new LinkedHashMap<>();
        if (doc.getAssets() != null) {
            for (OfficeImageAsset asset : doc.getAssets()) {
                String partPath = resolveMediaPartPath(asset);
                String ext = StringHelper.isEmpty(asset.getExt())
                        ? StringHelper.fileExt(partPath) : asset.getExt();
                String contentType = ContentTypes.getContentTypeFromFileExtension(ext);
                if (contentType == null) {
                    throw new NopException(ERR_OOXML_UNSUPPORTED_CONTENT_TYPE)
                            .param(ARG_PATH, partPath).param(ARG_FILE_EXT, ext);
                }
                contentTypes.addDefaultContentType(ext.toLowerCase(), contentType);
                pkg.addFile(new ResourceOfficePackagePart(partPath,
                        new ByteArrayResource("/" + partPath, asset.getData().toByteArray(), 0L)));
                mediaTargets.put(asset.getName(), partPath.substring("word/".length()));
            }
        }

        Function<String, String> imageResolver = assetRef -> {
            String target = mediaTargets.get(assetRef);
            if (target == null) {
                throw new NopException(ERR_DOCX_GEN_IMAGE_INVALID)
                        .param(ARG_REASON, "assetRef is not present in doc.assets")
                        .param(ARG_TARGET_ID, assetRef);
            }
            return target;
        };

        // ---- document.xml (multi-section) --------------------------------------
        OfficeRelsPart rels = new OfficeRelsPart(DocxConstants.PATH_WORD_RELS);
        xmlBuilder.setPartContext(new PartContext(rels, imageResolver));
        XNode documentNode = xmlBuilder.buildDocumentXml(doc);
        pkg.addFile(new XmlOfficePackagePart(DocxConstants.PATH_WORD_DOCUMENT, documentNode));
        contentTypes.addOverrideContentType("/" + DocxConstants.PATH_WORD_DOCUMENT,
                DocxConstants.CT_WML_DOCUMENT);

        // ---- per-section header/footer parts -----------------------------------
        List<OfficeDocPageModel> pages = doc.getPages();
        for (int i = 0; i < pages.size(); i++) {
            OfficeDocPageModel page = pages.get(i);
            int sectionIndex = i + 1;

            if (!page.getHeader().isEmpty()) {
                addHeaderFooterPart(pkg, contentTypes, xmlBuilder, imageResolver,
                        "word/header" + sectionIndex + ".xml", page.getHeader(),
                        DocxConstants.CT_WML_HEADER);
                rels.addRelationship("rIdHdr" + sectionIndex, OfficeConstants.NS_HEADER,
                        "header" + sectionIndex + ".xml", null);
            }

            if (!page.getFooter().isEmpty()) {
                addHeaderFooterPart(pkg, contentTypes, xmlBuilder, imageResolver,
                        "word/footer" + sectionIndex + ".xml", page.getFooter(),
                        DocxConstants.CT_WML_FOOTER);
                rels.addRelationship("rIdFtr" + sectionIndex, OfficeConstants.NS_FOOTER,
                        "footer" + sectionIndex + ".xml", null);
            }
        }

        // ---- settings part (document background, G4) ---------------------------
        if (!StringHelper.isEmpty(doc.getBackground())) {
            XNode settingsNode = xmlBuilder.buildSettingsXml();
            pkg.addFile(new XmlOfficePackagePart(DocxConstants.PATH_WORD_SETTINGS, settingsNode));
            contentTypes.addOverrideContentType("/" + DocxConstants.PATH_WORD_SETTINGS,
                    DocxConstants.CT_WML_SETTINGS);
            rels.addRelationship("rIdSettings", DocxConstants.NS_SETTINGS, "settings.xml", null);
        }

        // every asset gets a document relationship so no media part is orphaned;
        // images already registered their own relationship while building drawings
        for (Map.Entry<String, String> entry : mediaTargets.entrySet()) {
            if (rels.getRelationshipByTarget(entry.getValue()) == null) {
                rels.addImage(entry.getValue());
            }
        }

        pkg.addFile(rels);

        // ---- styles.xml ---------------------------------------------------------
        XNode stylesNode = xmlBuilder.buildStylesXml(doc);
        pkg.addFile(new XmlOfficePackagePart(DocxConstants.PATH_WORD_STYLES, stylesNode));
        contentTypes.addOverrideContentType("/" + DocxConstants.PATH_WORD_STYLES,
                DocxConstants.CT_WML_STYLES);

        // ---- _rels/.rels ---------------------------------------------------------
        OfficeRelsPart rootRels = new OfficeRelsPart("_rels/.rels");
        rootRels.addRelationship(new OfficeRelationship(null, "rId1",
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument",
                "word/document.xml", null));
        pkg.addFile(rootRels);

        xmlBuilder.setPartContext(null);
        xmlBuilder.setDoc(null);
        return pkg;
    }

    /**
     * Media part path for an asset. Parsed assets keep their original package path
     * (word/media/...), which preserves image assetRef values across a round trip.
     * Programmatic asset names are placed under word/media/ with their own file name.
     */
    private String resolveMediaPartPath(OfficeImageAsset asset) {
        String name = asset.getName();
        if (StringHelper.isEmpty(name)) {
            throw new NopException(ERR_DOCX_GEN_IMAGE_INVALID)
                    .param(ARG_REASON, "image asset has no name")
                    .param(ARG_TARGET_ID, null);
        }
        if (name.startsWith("word/")) {
            return name;
        }
        String fileName = StringHelper.fileName(name);
        if (fileName.indexOf('.') < 0) {
            String ext = StringHelper.isEmpty(asset.getExt()) ? "png" : asset.getExt();
            fileName = fileName + "." + ext;
        }
        return "word/media/" + fileName;
    }

    private void addHeaderFooterPart(WordOfficePackage pkg, ContentTypesPart contentTypes,
                                     OfficeDocXmlBuilder xmlBuilder,
                                     Function<String, String> imageResolver,
                                     String partPath, List<OfficeBlock> blocks, String contentType) {
        // a header/footer may itself carry hyperlinks/images: give it its own rels part
        String relsPath = relsPathForPart(partPath);
        OfficeRelsPart rels = new OfficeRelsPart(relsPath);
        xmlBuilder.setPartContext(new OfficeDocXmlBuilder.PartContext(rels, imageResolver));
        try {
            XNode node = xmlBuilder.buildHeaderFooterXml(blocks);
            pkg.addFile(new XmlOfficePackagePart(partPath, node));
        } finally {
            xmlBuilder.setPartContext(null);
        }
        if (rels.getRelationshipsByType(OfficeConstants.NS_LINK) != null
                || rels.getRelationshipsByType(OfficeConstants.NS_IMAGE) != null) {
            pkg.addFile(rels);
        }
        contentTypes.addOverrideContentType("/" + partPath, contentType);
    }

    private String relsPathForPart(String partPath) {
        int pos = partPath.lastIndexOf('/');
        return partPath.substring(0, pos + 1) + "_rels/" + partPath.substring(pos + 1) + ".rels";
    }
}
