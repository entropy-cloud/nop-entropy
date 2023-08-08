package io.nop.file.spring.web;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.file.core.AbstractGraphQLFileService;
import io.nop.file.core.DownloadRequestBean;
import io.nop.file.core.FileConstants;
import io.nop.file.core.MediaTypeHelper;
import io.nop.file.core.UploadRequestBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

@RestController
public class SpringFileService extends AbstractGraphQLFileService {
    @PostMapping(FileConstants.PATH_UPLOAD)
    public CompletionStage<ResponseEntity<Object>> upload(MultipartFile file, HttpServletRequest request) {
        String locale = ContextProvider.currentLocale();
        CompletionStage<ApiResponse<?>> res;
        try {
            InputStream is = file.getInputStream();
            String fileName = StringHelper.fileFullName(file.getOriginalFilename());
            String mimeType = MediaTypeHelper.getMimeType(file.getContentType(), StringHelper.fileExt(fileName));
            UploadRequestBean input = new UploadRequestBean(is, fileName, file.getSize(), mimeType);
            input.setBizObjName(request.getParameter(FileConstants.PARAM_BIZ_OBJ_NAME));
            res = uploadAsync(buildRequest(request, input));
        } catch (IOException e) {
            res = FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
        }
        return res.thenApply(response -> SpringWebHelper.buildResponse(response.getStatus(), response));
    }

    protected <T> ApiRequest<T> buildRequest(HttpServletRequest req, T data) {
        ApiRequest<T> ret = new ApiRequest<>();
        Enumeration<String> it = req.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            name = name.toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name))
                continue;
            ret.setHeader(name, req.getHeader(name));
        }
        ret.setData(data);
        return ret;
    }

    @GetMapping(FileConstants.PATH_DOWNLOAD + "/{fileId}")
    public CompletionStage<ResponseEntity<Object>> download(@PathVariable("fileId") String fileId,
                                                            @RequestParam(value = "contentType", required = false) String contentType,
                                                            HttpServletRequest request) {
        DownloadRequestBean req = new DownloadRequestBean();
        req.setFileId(fileId);
        req.setContentType(contentType);
        CompletionStage<ApiResponse<WebContentBean>> future = downloadAsync(buildRequest(request, req));
        return future.thenApply(res -> {
            if (!res.isOk()) {
                int status = res.getHttpStatus();
                if (status == 0)
                    status = 500;
                return SpringWebHelper.buildResponse(status, res);
            } else {
                return SpringWebHelper.buildResponse(res.getHttpStatus(), res.getData());
            }
        });
    }
}
