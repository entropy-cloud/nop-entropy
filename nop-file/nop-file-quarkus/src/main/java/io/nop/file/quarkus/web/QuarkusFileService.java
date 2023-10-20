/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.file.quarkus.web;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.resource.IResource;
import io.nop.file.core.AbstractGraphQLFileService;
import io.nop.file.core.DownloadRequestBean;
import io.nop.file.core.FileConstants;
import io.nop.file.core.MediaTypeHelper;
import io.nop.file.core.UploadRequestBean;
import io.nop.graphql.core.web.JaxrsHelper;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Path("")
@ApplicationScoped
public class QuarkusFileService extends AbstractGraphQLFileService {
    @Path(FileConstants.PATH_UPLOAD)
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA + ";charset=UTF-8")
    public CompletionStage<Response> uploadFileAsync(MultipartFormDataInput input,
                                                     @Context HttpServerRequest request) {
        Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
        List<InputPart> inputParts = uploadForm.get("file");
        String bizObjName = request.getParam(FileConstants.PARAM_BIZ_OBJ_NAME);
        String fieldName = request.getParam(FileConstants.PARAM_FIELD_NAME);

        String locale = ContextProvider.currentLocale();
        CompletionStage<ApiResponse<?>> res = null;
        try {
            for (InputPart inputPart : inputParts) {

                MultivaluedMap<String, String> headers = inputPart.getHeaders();
                String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
                // 获取文件名
                String fileName = getFileName(headers);
                // 修复文件名乱码
                fileName = fixFileName(fileName);
                // 处理上传文件
                InputStream inputStream = inputPart.getBody(InputStream.class, null);

                String mimeType = MediaTypeHelper.getMimeType(contentType, StringHelper.fileExt(fileName));
                UploadRequestBean fileInput = new UploadRequestBean(inputStream, fileName, -1, mimeType);
                fileInput.setBizObjName(bizObjName);
                fileInput.setFieldName(fieldName);

                res = uploadAsync(buildRequest(request, fileInput));
                break;
            }
        } catch (Exception e) {
            res = FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
        }

        return res.thenApply(JaxrsHelper::buildJaxrsResponse);
    }

    /**
     * resteasy内部强制使用了固定编码方式解析content-disposition来得到文件名
     */
    private String fixFileName(String fileName) {
        return new String(fileName.getBytes(StringHelper.CHARSET_ISO_8859_1), StringHelper.CHARSET_UTF8);
    }

    protected <T> ApiRequest<T> buildRequest(HttpServerRequest req, T data) {
        ApiRequest<T> ret = new ApiRequest<>();
        req.headers().forEach((name, value) -> {
            name = name.toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name))
                return;
            ret.setHeader(name, value);
        });
        ret.setData(data);
        return ret;
    }

    /**
     * header sample
     * {
     * Content-Type=[image/png],
     * Content-Disposition=[form-data; name="file"; filename="filename.extension"]
     * }
     **/
    //get uploaded filename, is there a easy way in RESTEasy?
    private String getFileName(MultivaluedMap<String, String> header) {

        String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

        for (String filename : contentDisposition) {
            if ((filename.trim().startsWith("filename"))) {

                String[] name = filename.split("=");

                String finalFileName = name[1].trim().replaceAll("\"", "");
                return finalFileName;
            }
        }
        return "unknown";
    }

    @Path(FileConstants.PATH_DOWNLOAD + "/{fileId}")
    @GET
    public CompletionStage<Response> download(@PathParam("fileId") String fileId,
                                              @DefaultValue("") @QueryParam("contentType") String contentType,
                                              @Context HttpServerRequest req) {
        DownloadRequestBean request = new DownloadRequestBean();
        request.setFileId(fileId);
        request.setContentType(contentType);

        return downloadAsync(buildRequest(req, request)).thenApply(res -> {
            if (!res.isOk()) {
                return JaxrsHelper.buildJaxrsResponse(res);
            }
            WebContentBean content = res.getData();
            return QuarkusFileHelper.buildFileResponse((IResource) content.getContent(), content.getContentType(), content.getFileName());
        });
    }
}