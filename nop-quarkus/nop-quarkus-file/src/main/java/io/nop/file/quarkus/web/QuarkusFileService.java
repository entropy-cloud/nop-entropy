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

            String locale = ContextProvider.currentLocale();
            CompletionStage<ApiResponse<?>> res = null;
            // C6-001（plan 2524）：inputStream 提升为外层局部（R2 Major-1 ①）——外层 catch
            // 作用域内可见方可兜底回收；「本迭代已提交否」判据防多 part 陈旧 res 边漏关
            InputStream opened = null;
            try {
                for (List<InputPart> inputParts : uploadForm.values()) {
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
                        opened = inputStream;

                        UploadRequestBean fileInput = buildUploadRequestBean(inputStream,
                                fileName,
                                -1,
                                contentType,
                                (name) -> getParamFrom(request, input, name));
                        CompletionStage<ApiResponse<?>> stage = uploadAsync(buildApiRequest(request, fileInput));
                        // C6-001：提交成功即注册配对 close（校验链失败此前直接抛出泄漏 fd）
                        stage.whenComplete((r, e) -> io.nop.commons.util.IoHelper.safeCloseObject(inputStream));
                        opened = null; // 关闭责任已移交 whenComplete
                        res = stage;
                        break; // NOPMD - intentional early exit on first valid file
                    }
                }
            if (res != null) {
                return res.thenApply(JaxrsHelper::buildJaxrsResponse);
            }
            throw new IllegalArgumentException("No Upload File");
            } catch (Throwable e) {
                // C6-001 前段兜底（R2 Major-1 ②③）：按「本迭代已提交否」关流（不依赖 res 判空
                // ——part₁ 成功 res₁ 非 null 而 part₂ 抛出时字面读法漏关）；Error 通道同关
                if (opened != null && res == null) {
                    io.nop.commons.util.IoHelper.safeCloseObject(opened);
                }
                // R21 族纪律：completeExceptionally 终结（NopException.adapt 包装——避免
                // ErrorMessageManager.buildResponseForException 的 checked IOException 逃逸）
                return java.util.concurrent.CompletableFuture.failedFuture(
                        io.nop.api.core.exceptions.NopException.adapt(e));
            }
        });
    }

    private String getParamFrom(HttpServerRequest request, MultipartFormDataInput input, String name) {
        String value = request.getParam(name);
        if (value == null) {
            try {
                value = input.getFormDataPart(name, String.class, null);
            } catch (IOException expected) {
                // NOPMD - 字段为空时走 getFormDataPart 回退；IOException 表示该 part 无此字段（非错误）
            }
        }
        return value;
    }

    /**
     * resteasy内部强制使用了固定编码方式解析content-disposition来得到文件名
     * 2025-01-10, 根据反馈，目前quarkus已经不需要Fix上传文件名
     */
    protected String fixFileName(String fileName) {
        return fileName;
        //return new String(fileName.getBytes(StringHelper.CHARSET_ISO_8859_1), StringHelper.CHARSET_UTF8);
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
        return doDownload(routingContext, fileId, contentType, req);
    }

    @Path(FileConstants.PATH_DOWNLOAD + "/{fileId}")
    @POST
    public CompletionStage<Response> downloadPost(@Context RoutingContext routingContext,
                                                  @PathParam("fileId") String fileId,
                                                  @DefaultValue("") @QueryParam("contentType") String contentType,
                                                  @Context HttpServerRequest req) {
        return doDownload(routingContext, fileId, contentType, req);
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
