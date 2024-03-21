/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.spring.web;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.WebContentBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.spring.core.resource.SpringResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
            body = buildContent(headers, contentBean.getContentType(), contentBean.getContent(), contentBean.getFileName());
        } else {
            body = JsonTool.stringify(data);
        }
        return new ResponseEntity<>(body, headers, HttpStatus.valueOf(status));
    }

    private static Object buildContent(HttpHeaders headers, String contentType, Object content, String fileName) {
        headers.set(ApiConstants.HEADER_CONTENT_TYPE, contentType);

        if (!StringHelper.isEmpty(fileName)) {
            String encoded = StringHelper.encodeURL(fileName);
            headers.set("Content-Disposition", "attachment; filename=" + encoded);
        }

        if (content == null)
            return null;

        if (content instanceof IResource) {
            IResource resource = (IResource) content;
            File file = resource.toFile();
            if (file != null)
                return new FileSystemResource(file);
            return new SpringResource(resource);
        } else if (content instanceof File) {
            return new FileSystemResource((File) content);
        } else if (content instanceof byte[]) {
            return new ByteArrayResource((byte[]) content);
        } else if (content instanceof Resource) {
            return content;
        } else if (content instanceof String) {
            return content;
        } else {
            return "INVALID CONTENT TYPE";
        }
    }
}
