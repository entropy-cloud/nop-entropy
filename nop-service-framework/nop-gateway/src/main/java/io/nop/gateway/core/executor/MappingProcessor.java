/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayMessageHeaderModel;
import io.nop.gateway.model.GatewayMessageMappingModel;
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.RecordMappingContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 处理请求和响应的消息映射
 *
 * <p>功能包括：</p>
 * <ul>
 *   <li>Header过滤（allowHeaders/disallowHeaders）</li>
 *   <li>动态Header设置</li>
 *   <li>Body数据映射（bodyMapping）</li>
 * </ul>
 */
public class MappingProcessor {

    private final IRecordMappingManager mappingManager;

    public MappingProcessor(IRecordMappingManager mappingManager) {
        this.mappingManager = mappingManager;
    }

    public Object mapElement(String mappingName, Object element, IGatewayContext context) {
        if (mappingName == null)
            return element;

        IRecordMapping recordMapping = mappingManager.getRecordMapping(mappingName);

        RecordMappingContext mappingCtx = createMappingContext(context);
        mappingCtx.setSourceRoot(element);
        Object mappedData = recordMapping.map(element, mappingCtx);
        return mappedData;

    }

    @SuppressWarnings("unchecked")
    public ApiRequest<?> mapRequest(GatewayMessageMappingModel mapping, ApiRequest<?> request,
                                    IGatewayContext context) {
        if (mapping == null) {
            return request;
        }

        // 创建新的请求对象以避免修改原始请求
        ApiRequest<Object> mappedRequest = new ApiRequest<>();
        mappedRequest.setSelection(request.getSelection());
        mappedRequest.setHeaders(new TreeMap<>(request.getHeaders()));
        mappedRequest.setData(request.getData());

        // 1. 过滤headers
        filterHeaders(mappedRequest.getHeaders(), mapping);

        // 2. 应用bodyMapping
        if (mapping.getBodyMapping() != null) {
            IRecordMapping recordMapping = mappingManager.getRecordMapping(mapping.getBodyMapping());
            RecordMappingContext mappingCtx = createMappingContext(context);
            mappingCtx.setSourceRoot(request.getData());
            Object mappedData = recordMapping.map(request.getData(), mappingCtx);
            mappedRequest.setData(mappedData);
        }

        // 3. 添加动态headers
        addDynamicHeaders(mappedRequest.getHeaders(), mapping, context);

        return mappedRequest;
    }

    /**
     * 映射响应消息
     *
     * @param mapping  响应映射配置
     * @param response 原始响应
     * @param context  网关上下文
     * @return 映射后的响应
     */
    @SuppressWarnings("unchecked")
    public ApiResponse<?> mapResponse(GatewayMessageMappingModel mapping, ApiResponse<?> response,
                                      IGatewayContext context) {
        if (mapping == null) {
            return response;
        }

        // 创建新的响应对象以避免修改原始响应
        ApiResponse<Object> mappedResponse = new ApiResponse<>();
        mappedResponse.setHttpStatus(response.getHttpStatus());
        mappedResponse.setCode(response.getCode());
        mappedResponse.setMsg(response.getMsg());
        mappedResponse.setHeaders(new TreeMap<>(response.getHeaders()));
        mappedResponse.setData(response.getData());
        mappedResponse.setWrapper(response.isWrapper());

        // 1. 过滤headers
        filterHeaders(mappedResponse.getHeaders(), mapping);

        // 2. 应用bodyMapping
        if (mapping.getBodyMapping() != null) {
            IRecordMapping recordMapping = mappingManager.getRecordMapping(mapping.getBodyMapping());

            RecordMappingContext mappingCtx = createMappingContext(context);
            mappingCtx.setSourceRoot(response.getData());
            Object mappedData = recordMapping.map(response.getData(), mappingCtx);
            mappedResponse.setData(mappedData);
        }

        // 3. 添加动态headers
        addDynamicHeaders(mappedResponse.getHeaders(), mapping, context);

        return mappedResponse;
    }

    /**
     * 过滤headers，根据allowHeaders和disallowHeaders配置。
     * <p>配置名与运行时 header key 的大小写可能不一致（运行时 key 已被 HTTP 层小写化，
     * 配置按原样字符串读取），比较统一按小写进行，避免白名单把头全部清掉/黑名单漏删。
     */
    private void filterHeaders(Map<String, Object> headers, GatewayMessageMappingModel mapping) {
        Set<String> allowHeaders = lowercaseNames(mapping.getAllowHeaders());
        Set<String> disallowHeaders = lowercaseNames(mapping.getDisallowHeaders());

        if (allowHeaders != null && !allowHeaders.isEmpty()) {
            // 白名单模式：只保留允许的headers
            headers.keySet().removeIf(k -> !allowHeaders.contains(lowercase(k)));
        } else if (disallowHeaders != null && !disallowHeaders.isEmpty()) {
            // 黑名单模式：移除禁止的headers
            headers.keySet().removeIf(k -> disallowHeaders.contains(lowercase(k)));
        }
    }

    private static Set<String> lowercaseNames(Set<String> names) {
        if (names == null) {
            return null;
        }
        Set<String> lowercased = new java.util.HashSet<>(names.size());
        for (String name : names) {
            if (name != null) {
                lowercased.add(lowercase(name));
            }
        }
        return lowercased;
    }

    private static String lowercase(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 添加动态headers
     */
    private void addDynamicHeaders(Map<String, Object> headers, GatewayMessageMappingModel mapping,
                                   IGatewayContext context) {
        List<GatewayMessageHeaderModel> headerModels = mapping.getBody();
        if (headerModels == null || headerModels.isEmpty()) {
            return;
        }

        IEvalScope scope = context.getEvalScope();
        for (GatewayMessageHeaderModel headerModel : headerModels) {
            String name = headerModel.getName();
            if (headerModel.getValue() != null) {
                Object value = headerModel.getValue().invoke(scope);
                if (value != null) {
                    headers.put(name, value);
                }
            }
        }
    }

    /**
     * 创建映射上下文
     */
    private RecordMappingContext createMappingContext(IGatewayContext context) {
        RecordMappingContext mappingCtx = new RecordMappingContext(context.getEvalScope());
        return mappingCtx;
    }
}
