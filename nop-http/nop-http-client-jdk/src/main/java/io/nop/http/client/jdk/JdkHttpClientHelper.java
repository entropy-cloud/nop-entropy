package io.nop.http.client.jdk;

import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;

import java.net.ConnectException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static io.nop.http.api.HttpApiErrors.ERR_HTTP_CONNECT_FAIL;

public class JdkHttpClientHelper {
    public static Map<String, String> getHeaders(HttpHeaders headers) {
        Map<String, String> ret = new HashMap<>();
        for (String name : headers.map().keySet()) {
            Optional<String> value = headers.firstValue(name);
            value.ifPresent(s -> ret.put(name, s));
        }
        return ret;
    }

    public static boolean isSuccessful(HttpResponse<?> response) {
        int statusCode = response.statusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    public static RuntimeException wrapException(Throwable e) {
        if (e instanceof CompletionException) {
            e = e.getCause();
        }
        if (e instanceof ConnectException)
            return new NopConnectException(ERR_HTTP_CONNECT_FAIL);
        return NopException.adapt(e);
    }
}
