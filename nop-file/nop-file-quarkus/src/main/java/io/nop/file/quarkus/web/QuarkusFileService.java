/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.quarkus.web;

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
import io.nop.graphql.core.web.JaxrsHelper;
import io.quarkus.vertx.http.runtime.RouteConstants;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
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
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.quarkus.web.utils.QuarkusExecutorHelper.withRoutingContext;

@Path("")
@ApplicationScoped
public class QuarkusFileService extends AbstractGraphQLFileService {
    @Path(FileConstants.PATH_UPLOAD)
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA + ";charset=UTF-8")
    public CompletionStage<Response> uploadFileAsync(@Context RoutingContext routingContext,
                                                     MultipartFormDataInput input,
                                                     @Context HttpServerRequest request) {
        return withRoutingContext(routingContext, () -> {
            Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
            List<InputPart> inputParts = uploadForm.get("file");

            String locale = ContextProvider.currentLocale();
            CompletionStage<ApiResponse<?>> res = null;
            try {
                for (InputPart inputPart : inputParts) {

                    MultivaluedMap<String, String> headers = inputPart.getHeaders();
                    String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);

                    // 获取文件名
                    String fileName = getFileName(headers);
                    if (StringHelper.isEmpty(fileName))
                        continue;

                    // 修复文件名乱码
                    fileName = fixFileName(fileName);
                    // 处理上传文件
                    InputStream inputStream = inputPart.getBody(InputStream.class, null);

                    UploadRequestBean fileInput = buildUploadRequestBean(inputStream,
                                                                         fileName,
                                                                         -1,
                                                                         contentType,
                                                                         (name) -> getParamFrom(request, input, name));
                    res = uploadAsync(buildApiRequest(request, fileInput));

                    return res.thenApply(JaxrsHelper::buildJaxrsResponse);
                }
                throw new IllegalArgumentException("No Upload File");
            } catch (Exception e) {
                res = FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
                return res.thenApply(JaxrsHelper::buildJaxrsResponse);
            }
        });
    }

    private String getParamFrom(HttpServerRequest request, MultipartFormDataInput input, String name) {
        String value = request.getParam(name);
        if (value == null) {
            try {
                value = input.getFormDataPart(name, String.class, null);
            } catch (IOException ignore) {
            }
        }
        return value;
    }

    /**
     * resteasy内部强制使用了固定编码方式解析content-disposition来得到文件名
     */
    private String fixFileName(String fileName) {
        return new String(fileName.getBytes(StringHelper.CHARSET_ISO_8859_1), StringHelper.CHARSET_UTF8);
    }

    protected <T> ApiRequest<T> buildApiRequest(HttpServerRequest req, T data) {
        return buildApiRequest(data, (header) -> {
            req.headers().forEach(header::accept);
        });
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

                return name[1].trim().replaceAll("\"", "");
            }
        }
        return null;
    }

    @Path(FileConstants.PATH_DOWNLOAD + "/{fileId}")
    @GET
    public CompletionStage<Response> download(@Context RoutingContext routingContext,
                                              @PathParam("fileId") String fileId,
                                              @DefaultValue("") @QueryParam("contentType") String contentType,
                                              @Context HttpServerRequest req) {
        return doDownload(routingContext,fileId,contentType,req);
    }

    @Path(FileConstants.PATH_DOWNLOAD + "/{fileId}")
    @POST
    public CompletionStage<Response> downloadPost(@Context RoutingContext routingContext,
                                              @PathParam("fileId") String fileId,
                                              @DefaultValue("") @QueryParam("contentType") String contentType,
                                              @Context HttpServerRequest req) {
        return doDownload(routingContext,fileId,contentType,req);
    }

    public CompletionStage<Response> doDownload(@Context RoutingContext routingContext,
                                                @PathParam("fileId") String fileId,
                                                @DefaultValue("") @QueryParam("contentType") String contentType,
                                                @Context HttpServerRequest req) {
        return withRoutingContext(routingContext, () -> {
            DownloadRequestBean request = buildDownloadRequestBean(fileId, contentType);

            return downloadAsync(buildApiRequest(req, request)).thenApply(res -> {
                if (!res.isOk()) {
                    return JaxrsHelper.buildJaxrsResponse(res);
                } else {
                    return QuarkusFileHelper.buildFileResponse(res);
                }
            });
        });
    }
}
