package io.nop.spring.web.service;

import io.nop.commons.util.CollectionHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SpringMvcHelper {
    static final Set<String> IGNORE_HEADERS = CollectionHelper.buildImmutableSet("connection",
            "accept", "accept-encoding", "content-length");

    public static boolean shouldIgnoreHeader(String name) {
        return IGNORE_HEADERS.contains(name);
    }

    public static Map<String, Object> getHeaders(HttpServletRequest request) {
        Map<String, Object> ret = new TreeMap<>();
        Enumeration<String> it = request.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement().toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name))
                continue;
            ret.put(name, request.getHeader(name));
        }
        return ret;
    }

    public static <T> ResponseEntity<T> buildResponseEntity(Map<String, Object> headers,
                                                            T body, int httpStatus) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach((name, value) -> {
            List<String> list = Collections.singletonList(String.valueOf(value));
            httpHeaders.put(name, list);
        });

        return new ResponseEntity<>(body, httpHeaders, httpStatus);
    }
}