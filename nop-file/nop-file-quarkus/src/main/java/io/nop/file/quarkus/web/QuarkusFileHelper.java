/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.file.quarkus.web;

import io.nop.api.core.ApiConstants;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;

public class QuarkusFileHelper {
    public static Response buildFileResponse(IResource resource, String contentType, String fileName) {
        Response.ResponseBuilder builder = Response.ok();
        builder.header(ApiConstants.HEADER_CONTENT_TYPE, contentType);

        if (!StringHelper.isEmpty(fileName)) {
            String encoded = StringHelper.encodeURL(fileName);
            builder.header("Content-Disposition", "attachment;filename=" + encoded);
        }
        File file = resource.toFile();
        if (file != null) {
            builder.entity(file);
        } else {
            builder.entity((StreamingOutput) resource::writeToStream);
        }

        return builder.build();
    }
}
