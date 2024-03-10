/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.filter;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.quarkus.web.QuarkusWebConstants;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.impl.MimeMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;


/**
 * 对于js请求，识别是否已经存在js.gz文件，如果有则直接返回压缩后版本
 * 注意：如果应用多次，导致前台返回的header中存在多个content-encoding字段，则前台提示无法识别
 */
@ApplicationScoped
@IfBuildProperty(name = "nop.web.zip-content-encoding-filter.enabled", stringValue = "true", enableIfMissing = true)
public class ZipContentEncodingFilterRegistrar {

    public void setupFilter(@Observes Filters filters) {
        filters.register((rc) -> {
            String path = rc.normalizedPath();
            if (path.equals("/")) {
                IResource resource = new ClassPathResource("classpath:META-INF/resources/index.html.gz");
                if (resource.exists()) {
                    // reroute会清空headers
                    rc.reroute("/index.html.gz");
                    return;
                } else {
                    resource = new ClassPathResource("classpath:META-INF/resources/index.html");
                    if (resource.exists()) {
                        rc.reroute("/index.html");
                        return;
                    }
                }
                rc.next();
                return;
            }

            if (path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".json")) {
                String gzPath = path + ".gz";
                IResource resource = new ClassPathResource("classpath:META-INF/resources" + gzPath);
                if (resource.exists()) {
                    // reroute会清空headers
                    rc.reroute(gzPath);
                    return;
                }
            }
            if (path.endsWith(".gz")) {
                String fileName = StringHelper.removeTail(StringHelper.fileFullName(path), ".gz");
                rc.response().putHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
                // guess content type
                String contentType = MimeMapping.getMimeTypeForFilename(fileName);
                if (contentType != null) {
                    if (contentType.startsWith("text")) {
                        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, contentType + ";charset=UTF-8");
                    } else {
                        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, contentType);
                    }
                }
            }

            // if (path.endsWith(".br")) {
            // rc.response().headers().add("content-encoding", "br");
            // }
            //
            // if (path.endsWith(".wasm")) {
            // rc.response().headers().add("content-type", "application/wasm");
            // }

            rc.next();

        }, QuarkusWebConstants.PRIORITY_CONTENT_ENCODING_FILTER);
    }
}