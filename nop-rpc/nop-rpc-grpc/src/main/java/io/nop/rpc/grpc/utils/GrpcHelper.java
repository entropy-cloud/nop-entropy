package io.nop.rpc.grpc.utils;

import io.grpc.Metadata;
import io.grpc.internal.GrpcUtil;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.type.BinaryScalarType;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.selection.FieldSelectionBeanParser;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GrpcHelper {
    public static Map<String, Object> parseHeaders(Metadata headers) {
        Map<String, Object> parsedHeaders = new HashMap<>();
        headers.keys().forEach(key -> {
            // 忽略pseudo属性
            if (key.startsWith(":"))
                return;

            if (key.endsWith("-bin")) {
                Metadata.Key<byte[]> metadataKey = Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER);
                byte[] value = headers.get(metadataKey);
                if (value != null)
                    parsedHeaders.put(key, new String(value, StringHelper.CHARSET_UTF8));
            } else if (key.equals(GrpcUtil.TIMEOUT)) {
                Long timeout = headers.get(GrpcUtil.TIMEOUT_KEY);
                if (timeout != null) {
                    parsedHeaders.put(ApiConstants.HEADER_TIMEOUT, TimeUnit.NANOSECONDS.toMillis(timeout));
                }
            } else {
                Metadata.Key<String> metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                String value = headers.get(metadataKey);
                if (value != null)
                    parsedHeaders.put(key, value);
            }
        });
        return parsedHeaders;
    }

    public static FieldSelectionBean getSelection(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty())
            return null;

        String selection = ApiHeaders.getStringHeader(headers, ApiConstants.HEADER_SELECTION);
        if (selection != null)
            return new FieldSelectionBeanParser().parseFromText(null, selection);
        return null;
    }

    public static Metadata buildHeaders(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty())
            return new Metadata();

        Metadata data = new Metadata();
        headers.forEach((name, value) -> {
            if (value == null)
                return;

            // 将nop-timeout变换为grpc-timeout
            if (name.equals(ApiConstants.HEADER_TIMEOUT)) {
                Long timeout = ConvertHelper.toLong(value);
                data.put(GrpcUtil.TIMEOUT_KEY, TimeUnit.MILLISECONDS.toNanos(timeout));
                return;
            }

            String str = value.toString();
            if (StringHelper.isUSASCII(str)) {
                Metadata.Key<String> key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
                data.put(key, str);
            } else {
                Metadata.Key<byte[]> key = Metadata.Key.of(name + "-bin", Metadata.BINARY_BYTE_MARSHALLER);
                byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
                data.put(key, bytes);
            }
        });
        return data;
    }


}
