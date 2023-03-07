/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.docx.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.common.constants.ContentTypes;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxConstants;

import static io.nop.core.CoreErrors.ARG_FILE_NAME;
import static io.nop.ooxml.common.OfficeErrors.ARG_PATH;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_UNSUPPORTED_CONTENT_TYPE;

public class WordOfficePackage extends OfficePackage {
    @Override
    public WordOfficePackage copy() {
        WordOfficePackage pkg = new WordOfficePackage();
        copyTo(pkg);
        return pkg;
    }

    public OfficeRelationship addImage(IResource resource) {
        String fileExt = StringHelper.fileExt(resource.getPath());
        ContentTypesPart contentTypes = getContentTypes();
        String contentType = ContentTypes.getContentTypeFromFileExtension(resource.getName());
        if (contentType == null)
            throw new NopException(ERR_OOXML_UNSUPPORTED_CONTENT_TYPE).param(ARG_PATH, resource.getPath())
                    .param(ARG_FILE_NAME, resource.getName());

        contentTypes.addDefaultContentType(fileExt.toLowerCase(), contentType);

        String target = addNewFile("word/media/image1." + fileExt, resource);
        OfficeRelsPart part = getRels(DocxConstants.PATH_WORD_RELS);
        if (target.startsWith("word/"))
            target = target.substring("word/".length());
        return part.addImage(target);
    }
}
