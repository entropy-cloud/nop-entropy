/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api;

import java.util.Arrays;
import java.util.List;

/**
 * http header大小写不敏感，全部转成小写
 */
public interface HttpApiConstants {
    String HEADER_RANGE = "range";

    String HEADER_IF_MODIFIED_SINCE = "if-modified-since";

    String HEADER_LAST_MODIFIED = "last-modified";

    String HEADER_CONTENT_RANGE = "content-range";

    String HEADER_CONTENT_LENGTH = "content-length";

    String HEADER_CONTENT_TYPE = "content-type";

    String HEADER_CONTENT_DISPOSITION = "content-disposition";

    String HEADER_USER_AGENT = "user-agent";

    String HEADER_AUTHORIZATION = "authorization";

    String HEADER_HOST = "host";

    String HEADER_PROXY_AUTHENTICATE = "proxy-authenticate";

    String HEADER_PROXY_AUTHORIZATION = "proxy-authorization";

    String HEADER_SET_COOKIE = "set-cookie";
    String HEADER_SET_COOKIE2 = "set-cookie2";

    String HEADER_REFERRER = "referrer";

    String CONTENT_TYPE_OCTET = "application/octet-stream";
    String CONTENT_TYPE_HTML = "text/html";
    String CONTENT_TYPE_JAVASCRIPT = "text/javascript";
    String CONTENT_TYPE_JSON = "application/json";
    String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";

    String METHOD_GET = "GET";
    String METHOD_POST = "POST";
    String METHOD_OPTIONS = "OPTIONS";
    String METHOD_PUT = "PUT";
    String METHOD_DELETE = "DELETE";
    String METHOD_HEAD = "HEAD";

    List<String> HTTP_METHODS = Arrays.asList(METHOD_GET, METHOD_POST, METHOD_OPTIONS, METHOD_PUT, METHOD_DELETE,
            METHOD_HEAD);

    String PROTOCOL_HTTP = "http";
    String PROTOCOL_HTTPS = "https";

    String PROTOCOL_WS = "ws";
    String PROTOCOL_WSS = "wss";

    String DATA_TYPE_FORM = "form";
    String DATA_TYPE_JSON = "json";
}
