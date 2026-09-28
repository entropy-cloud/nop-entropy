package io.nop.code.service.semantic;

import java.nio.charset.StandardCharsets;
import io.nop.api.core.exceptions.NopException;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * N7.1 LLM 结果缓存：SHA256(prompt) 为键，ConcurrentHashMap 存储。
 * v1 纯内存（进程生命周期），缓存持久化 deferred。
 */
public class LlmEdgeCache {
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String getOrCompute(String promptKey, Supplier<String> compute) {
        String key = sha256(promptKey);
        return cache.computeIfAbsent(key, k -> compute.get());
    }

    public int size() {
        return cache.size();
    }

    static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
