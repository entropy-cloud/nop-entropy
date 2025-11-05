package io.nop.http.api.server;

public class DefaultClientIpFetcher implements IClientIpFetcher {
    // 只信任这两个最标准的头，避免潜在的安全问题
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String FORWARDED = "Forwarded";

    @Override
    public String getClientRealIp(IHttpServerContext context) {
        // 1. 优先检查 X-Forwarded-For（最常用）
        String clientIp = getClientIpFromHeader(context, X_FORWARDED_FOR);
        if (isValidIp(clientIp)) {
            return clientIp;
        }

        // 2. 检查标准 Forwarded 头（RFC 7239）
        clientIp = getClientIpFromForwardedHeader(context);
        if (isValidIp(clientIp)) {
            return clientIp;
        }

        // 3. 如果代理头无效，回退到直接连接的 IP
        return context.getRemoteAddr();
    }

    @Override
    public String getClientRealAddr(IHttpServerContext context) {
        return getClientRealIp(context) + ':' + context.getRemotePort();
    }

    private String getClientIpFromHeader(IHttpServerContext context, String headerName) {
        String headerValue = context.getRequestStringHeader(headerName);
        if (headerValue == null || headerValue.isEmpty()) {
            return null;
        }

        // X-Forwarded-For: client, proxy1, proxy2 → 取第一个 IP
        String[] ips = headerValue.split(",");
        for (String ip : ips) {
            String trimmedIp = ip.trim();
            if (isValidIp(trimmedIp)) {
                return trimmedIp;
            }
        }
        return null;
    }

    private String getClientIpFromForwardedHeader(IHttpServerContext context) {
        // Forwarded: for=192.0.2.60;proto=http;by=203.0.113.43
        String headerValue = context.getRequestStringHeader(FORWARDED);
        if (headerValue == null || headerValue.isEmpty()) {
            return null;
        }

        // 解析 Forwarded 头，提取第一个 "for=" 的 IP
        String[] parts = headerValue.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("for=")) {
                String ip = part.substring(4).trim();
                if (ip.startsWith("\"") && ip.endsWith("\"")) {
                    ip = ip.substring(1, ip.length() - 1); // 去掉引号
                }
                if (isValidIp(ip)) {
                    return ip;
                }
            }
        }
        return null;
    }

    // 简单校验 IP 是否有效（可扩展为更严格的检查）
    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}