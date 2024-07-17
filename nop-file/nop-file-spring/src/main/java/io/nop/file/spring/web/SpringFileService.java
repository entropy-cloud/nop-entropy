/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.spring.web;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.file.core.AbstractGraphQLFileService;
import io.nop.file.core.DownloadRequestBean;
import io.nop.file.core.FileConstants;
import io.nop.file.core.UploadRequestBean;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.concurrent.CompletionStage;

@RestController
public class SpringFileService extends AbstractGraphQLFileService {
    public SpringFileService() {

    }

    @PostMapping(FileConstants.PATH_UPLOAD)
    public CompletionStage<ResponseEntity<Object>> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String locale = ContextProvider.currentLocale();

        CompletionStage<ApiResponse<?>> res;
        try {
            InputStream is = file.getInputStream();
            String fileName = StringHelper.fileFullName(file.getOriginalFilename());

            UploadRequestBean input = buildUploadRequestBean(is,
                    fileName,
                    file.getSize(),
                    file.getContentType(),
                    request::getParameter);

            res = uploadAsync(buildApiRequest(request, input));
        } catch (IOException e) {
            res = FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
        }

        return res.thenApply(SpringWebHelper::buildJsonResponse);
    }

    protected <T> ApiRequest<T> buildApiRequest(HttpServletRequest req, T data) {
        return buildApiRequest(data, (header) -> {
            Enumeration<String> it = req.getHeaderNames();

            while (it.hasMoreElements()) {
                String name = it.nextElement();
                header.accept(name, req.getHeader(name));
            }
        });
    }

    @GetMapping(FileConstants.PATH_DOWNLOAD + "/{fileId}")
    public CompletionStage<ResponseEntity<Object>> download(@PathVariable("fileId") String fileId,
                                                            @RequestParam(value = "contentType", required = false) String contentType,
                                                            HttpServletRequest request) {
        return doDownload(fileId, contentType, request);
    }

    @PostMapping(FileConstants.PATH_DOWNLOAD + "/{fileId}")
    public CompletionStage<ResponseEntity<Object>> downloadPost(@PathVariable("fileId") String fileId,
                                                                @RequestParam(value = "contentType", required = false) String contentType,
                                                                HttpServletRequest request) {
        return doDownload(fileId, contentType, request);
    }

    public CompletionStage<ResponseEntity<Object>> doDownload(@PathVariable("fileId") String fileId,
                                                              @RequestParam(value = "contentType", required = false) String contentType,
                                                              HttpServletRequest request) {
        DownloadRequestBean req = buildDownloadRequestBean(fileId, contentType);

        return downloadAsync(buildApiRequest(request, req)).thenApply(res -> {
            if (!res.isOk()) {
                return SpringWebHelper.buildJsonResponse(res);
            } else {
                return SpringWebHelper.buildFileResponse(res);
            }
        });
    }
}
