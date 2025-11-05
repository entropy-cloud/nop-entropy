/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.common.constants.ContentTypes;
import io.nop.ooxml.common.impl.ResourceOfficePackagePart;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxConstants;

import java.io.File;
import java.util.function.Consumer;

import static io.nop.ooxml.common.OfficeErrors.ARG_FILE_EXT;
import static io.nop.ooxml.common.OfficeErrors.ARG_PATH;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_UNSUPPORTED_CONTENT_TYPE;
import static io.nop.ooxml.docx.DocxConstants.PART_NAME_COMMENTS;
import static io.nop.ooxml.docx.DocxConstants.PART_NAME_COMMENTS_EXTENDED;
import static io.nop.ooxml.docx.DocxConstants.PART_NAME_COMMENTS_EXTENSIBLE;
import static io.nop.ooxml.docx.DocxConstants.PART_NAME_COMMENTS_IDS;

public class WordOfficePackage extends OfficePackage {
    @Override
    public WordOfficePackage copy() {
        WordOfficePackage pkg = new WordOfficePackage();
        copyTo(pkg);
        return pkg;
    }

    public void saveImagesToDir(File dir) {
        forEachImage(resource -> {
            resource.saveToFile(new File(dir, resource.getName()));
        });
    }

    public void forEachImage(Consumer<IResource> action) {
        this.getFiles("word/media/image").forEach(part -> {
            action.accept(part.getResource());
        });
    }

    public WordStylesPart getStyles() {
        String path = "word/styles.xml";
        IOfficePackagePart file = getFile(path);
        if (file == null)
            return null;

        if (file instanceof WordStylesPart)
            return (WordStylesPart) file;

        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
        WordStylesPart part = new WordStylesPart(file.getPath(), res.loadXml());
        addFile(part);
        return part;
    }

    public WordCommentsPart getComments() {
        String path = "word/comments.xml";
        IOfficePackagePart file = getFile(path);
        if (file == null)
            return null;

        if (file instanceof WordCommentsPart)
            return (WordCommentsPart) file;

        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
        WordCommentsPart part = new WordCommentsPart(file.getPath(), res.loadXml());
        addFile(part);
        return part;
    }

    public OfficeRelationship addImage(IResource resource) {
        return addImage(StringHelper.fileExt(resource.getPath()), resource);
    }

    public OfficeRelationship addImage(String fileExt, IResource resource) {
        ContentTypesPart contentTypes = getContentTypes();
        String contentType = ContentTypes.getContentTypeFromFileExtension(fileExt);
        if (contentType == null)
            throw new NopException(ERR_OOXML_UNSUPPORTED_CONTENT_TYPE).param(ARG_PATH, resource.getPath())
                    .param(ARG_FILE_EXT, fileExt);

        contentTypes.addDefaultContentType(fileExt.toLowerCase(), contentType);

        String target = addNewFile("word/media/image1." + fileExt, resource);
        OfficeRelsPart part = getRels(DocxConstants.PATH_WORD_RELS);
        if (target.startsWith("word/"))
            target = target.substring("word/".length());
        return part.addImage(target);
    }

    public XNode getWordXml() {
        return getFile(DocxConstants.PATH_WORD_DOCUMENT).buildXml(null);
    }

    public void removeCommentsFile() {
        this.removeFile("word/comments.xml");
        this.removeFile("word/commentsExtended.xml");
        this.removeFile("word/commentsExtensible.xml");
        this.removeFile("word/commentsIds.xml");

        ContentTypesPart part = getContentTypes();
        part.removeContentType(PART_NAME_COMMENTS);
        part.removeContentType(PART_NAME_COMMENTS_EXTENDED);
        part.removeContentType(PART_NAME_COMMENTS_EXTENSIBLE);
        part.removeContentType(PART_NAME_COMMENTS_IDS);
    }
}