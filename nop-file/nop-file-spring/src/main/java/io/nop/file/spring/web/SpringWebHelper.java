/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.spring.web;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.utils.GraphQLResponseHelper;
import io.nop.spring.core.resource.SpringResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class SpringWebHelper {

    /** 将参数 {@link ApiResponse} 序列化为 json 后，将其构造为响应体数据 */
    public static ResponseEntity<Object> buildJsonResponse(ApiResponse<?> res) {
        return GraphQLResponseHelper.consumeJsonResponse(res, (invokeHeaderSet, body, status) -> {
            HttpHeaders headers = createHttpHeaders(invokeHeaderSet);

            return new ResponseEntity<>(body, headers, HttpStatus.valueOf(status));
        });
    }

    /** 根据 {@link WebContentBean} 的类型，构造对应的资源响应体 */
    public static ResponseEntity<Object> buildFileResponse(ApiResponse<WebContentBean> response) {
        return GraphQLResponseHelper.consumeWebContent(response, (invokeHeaderSet, content, status) -> {
            HttpHeaders headers = createHttpHeaders(invokeHeaderSet);

            Object body = null;
            if (content instanceof IResource) {
                IResource resource = (IResource) content;
                File file = resource.toFile();

                if (file != null) {
                    body = new FileSystemResource(file);
                } else {
                    body = new SpringResource(resource);
                }
            } else if (content instanceof File) {
                body = new FileSystemResource((File) content);
            } else if (content instanceof byte[]) {
                body = new ByteArrayResource((byte[]) content);
            } else if (content instanceof Resource || content instanceof String) {
                body = content;
            } else if (content != null) {
                body = "INVALID CONTENT TYPE";
            }

            return new ResponseEntity<>(body, headers, HttpStatus.valueOf(status));
        });
    }

    private static HttpHeaders createHttpHeaders(Consumer<BiConsumer<String, Object>> invokeHeaderSet) {
        HttpHeaders headers = new HttpHeaders();

        invokeHeaderSet.accept((name, value) -> headers.set(name, value != null ? value.toString() : null));

        return headers;
    }
}
