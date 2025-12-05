/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.quarkus.web;

import java.io.File;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.utils.GraphQLResponseHelper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

public class QuarkusFileHelper {

    public static Response buildFileResponse(ApiResponse<WebContentBean> response) {
        return GraphQLResponseHelper.consumeWebContent(response, (invokeHeaderSet, content, status) -> {
            Response.ResponseBuilder builder = Response.status(status);
            invokeHeaderSet.accept(builder::header);

            if (content instanceof IResource) {
                IResource resource = (IResource) content;
                File file = resource.toFile();

                if (file != null) {
                    builder.entity(file);
                } else {
                    builder.entity((StreamingOutput) resource::writeToStream);
                }
            } else if (content instanceof byte[]
                       || content instanceof String
                       || content instanceof File
                       || content instanceof StreamingOutput) {
                builder.entity(content);
            } else if (content != null) {
                builder.entity("INVALID CONTENT TYPE");
            }

            return builder.build();
        });
    }
}
