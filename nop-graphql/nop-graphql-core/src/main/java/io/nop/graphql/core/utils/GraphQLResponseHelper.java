/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.utils;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.json.JSON;
import io.nop.commons.functional.ITriFunction;
import io.nop.commons.util.StringHelper;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-06-28
 */
public class GraphQLResponseHelper {

    /**
     * 对 {@link ApiResponse} 做 JSON 序列化后的处理函数
     * <p/>
     * 注：响应头 `Content-Type` 将被设置为 `application/json`
     *
     * @param responseConsumer
     *         对 JSON 序列化后的 {@link ApiResponse} 的处理函数，其有如下参数：<ul>
     *         <li>invokeHeaderSet: 调用响应头设置函数，其为匿名函数，接受并调用函数 `(name, value) -> {}`，用于消费涉及的响应头；</li>
     *         <li>body: 响应体数据，其为调用 `JSON.stringify(response.cloneInstance(false))` 的序列化结果；</li>
     *         <li>status: 响应状态，来自于 {@link ApiResponse#getHttpStatus()}，
     *         在原值为 `0` 时，该入参实际传入 `200` 或 `500`（{@link ApiResponse#isOk()} 为 `false` 时）；</li>
     *         </ul>
     */
    public static <T> T consumeJsonResponse(
            ApiResponse<?> response,
            ITriFunction<Consumer<BiConsumer<String, Object>>, Object, Integer, T> responseConsumer
    ) {
        int status = response.getHttpStatus();
        if (status == 0) {
            status = response.isOk() ? 200 : 500;
        }

        // Note: ApiResponse#headers 仅用于设置响应头，不需要包含在响应体中，
        // 故而，需要在序列化时将其排除掉
        Object body = JSON.stringify(response.cloneInstance(false));

        return responseConsumer.apply( //
                                       (headerSet) -> {
                                           if (response.getHeaders() != null) {
                                               response.getHeaders().forEach(headerSet);
                                           }

                                           headerSet.accept(ApiConstants.HEADER_CONTENT_TYPE,
                                                            WebContentBean.CONTENT_TYPE_JSON);
                                       }, //
                                       body, //
                                       status //
        );
    }

    /**
     * 对 {@link WebContentBean} 的响应处理，其将统一设置 `Content-Type` 响应头，
     * 并为文件下载（{@link WebContentBean#getFileName()} 不为空时）等设置相关的响应头
     *
     * @param contentConsumer
     *         对 {@link WebContentBean#getContent()} 的处理函数，其有如下参数：<ul>
     *         <li>invokeHeaderSet: 调用响应头设置函数，其为匿名函数，接受并调用函数 `(name, value) -> {}`，用于消费涉及的响应头；</li>
     *         <li>content: 响应体数据，来自于 `response.getData().getContent()`；</li>
     *         <li>status: 响应状态，来自于 {@link ApiResponse#getHttpStatus()}，在原值为 `0` 时，该入参实际传入 `200`；</li>
     *         </ul>
     */
    public static <T> T consumeWebContent(
            ApiResponse<WebContentBean> response,
            ITriFunction<Consumer<BiConsumer<String, Object>>, Object, Integer, T> contentConsumer
    ) {
        return consumeWebContent(response, response.getData(), contentConsumer);
    }

    /**
     * 对 {@link WebContentBean} 的响应处理，统一设置 `Content-Type` 响应头，
     * 并为文件下载（{@link WebContentBean#getFileName()} 不为空时）等设置相关的响应头
     *
     * @param contentConsumer
     *         对 {@link WebContentBean#getContent()} 的处理函数，其有如下参数：<ul>
     *         <li>invokeHeaderSet: 调用响应头设置函数，其为匿名函数，接受并调用函数 `(name, value) -> {}`，用于消费涉及的响应头；</li>
     *         <li>content: 响应体数据，来自于 `response.getData().getContent()`；</li>
     *         <li>status: 响应状态，来自于 {@link ApiResponse#getHttpStatus()}，在原值为 `0` 时，该入参实际传入 `200`；</li>
     *         </ul>
     */
    public static <T> T consumeWebContent(
            ApiResponse<?> response, WebContentBean contentBean,
            ITriFunction<Consumer<BiConsumer<String, Object>>, Object, Integer, T> contentConsumer
    ) {
        int status = response.getHttpStatus();
        if (status == 0) {
            status = 200;
        }

        String contentType = contentBean.getContentType();
        String fileName = contentBean.getFileName();

        return contentConsumer.apply( //
                                      (headerSet) -> {
                                          if (response.getHeaders() != null) {
                                              response.getHeaders().forEach(headerSet);
                                          }

                                          headerSet.accept(ApiConstants.HEADER_CONTENT_TYPE, contentType);

                                          if (StringHelper.isNotBlank(fileName)) {
                                              String encoded = StringHelper.encodeURL(fileName);

                                              headerSet.accept("content-disposition",
                                                               "attachment; filename=" + encoded);
                                              headerSet.accept("Access-Control-Expose-Headers", "content-disposition");
                                          }
                                      }, //
                                      contentBean.getContent(), //
                                      status //
        );
    }
}
