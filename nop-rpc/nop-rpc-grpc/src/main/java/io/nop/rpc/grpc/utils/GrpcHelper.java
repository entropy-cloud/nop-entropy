package io.nop.rpc.grpc.utils;

import io.grpc.Metadata;
import io.nop.commons.util.StringHelper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GrpcHelper {
    public static Map<String, Object> parseHeaders(Metadata headers) {
        Map<String, Object> parsedHeaders = new HashMap<>();
        headers.keys().forEach(key -> {
            Metadata.Key<byte[]> metadataKey = Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER);
            byte[] value = headers.get(metadataKey);
            if (value != null)
                parsedHeaders.put(key, new String(value, StringHelper.CHARSET_UTF8));
        });
        return parsedHeaders;
    }

    public static Metadata buildHeaders(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty())
            return new Metadata();

        Metadata data = new Metadata();
        headers.forEach((name, value) -> {
            if (value == null)
                return;

            Metadata.Key<byte[]> key = Metadata.Key.of(name, Metadata.BINARY_BYTE_MARSHALLER);
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            data.put(key, bytes);
        });
        return data;
    }
}
