/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.tpl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.CoreConstants;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_TPL;
import static io.nop.core.CoreErrors.ERR_TPL_OUTPUT_TEXT_FAIL;
import static io.nop.core.CoreErrors.ERR_TPL_OUTPUT_TO_RESOURCE_FAIL;

public interface ITextTemplateOutput extends ITemplateOutput {
    void generateToWriter(Writer out, IEvalContext context) throws IOException;

    default String generateText(IEvalContext context) {
        StringWriter out = new StringWriter();
        try {
            generateToWriter(out, context);
            return out.toString();
        } catch (IOException e) {
            throw new NopException(ERR_TPL_OUTPUT_TEXT_FAIL).param(ARG_TPL, this);
        }
    }

    default XNode generateToNode(IEvalContext context) {
        String text = generateText(context);
        return XNodeParser.instance().parseFromText(null, text);
    }

    default void generateToNode(IXNodeHandler handler, IEvalContext context) {
        String text = generateText(context);
        XNodeParser.instance().handler(handler).parseFromText(null, text);
    }

    default void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        generateToWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), context);
    }

    default void generateToResource(IResource resource, IEvalContext context) {
        Writer os = ResourceHelper.toWriter(resource, CoreConstants.ENCODING_UTF8, true);
        try {
            generateToWriter(os, context);
            os.flush();
        } catch (IOException e) {
            throw new NopException(ERR_TPL_OUTPUT_TO_RESOURCE_FAIL).param(ARG_TPL, this).param(ARG_RESOURCE, resource);
        } finally {
            IoHelper.safeClose(os);
        }
    }
}