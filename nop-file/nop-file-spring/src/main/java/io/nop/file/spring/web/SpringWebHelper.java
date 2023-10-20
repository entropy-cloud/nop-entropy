/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.file.spring.web;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.WebContentBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.spring.core.resource.SpringResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;

public class SpringWebHelper {
    public static ResponseEntity<Object> buildResponse(int status, Object data) {
        if (status == 0)
            status = 200;

        HttpHeaders headers = new HttpHeaders();

        Object body;
        if (data instanceof WebContentBean) {
            WebContentBean contentBean = (WebContentBean) data;
            body = buildContent(headers, contentBean.getContentType(), (IResource) contentBean.getContent(), contentBean.getFileName());
        } else {
            body = JsonTool.stringify(data);
        }
        return new ResponseEntity<>(body, headers, HttpStatus.valueOf(status));
    }

    private static Object buildContent(HttpHeaders headers, String contentType, IResource content, String fileName) {
        headers.set(ApiConstants.HEADER_CONTENT_TYPE, contentType);

        if (!StringHelper.isEmpty(fileName)) {
            String encoded = StringHelper.encodeURL(fileName);
            headers.set("Content-Disposition", "attachment; filename=" + encoded);
        }
        File file = content.toFile();
        if (file != null)
            return new FileSystemResource(file);
        return new SpringResource(content);
    }
}
