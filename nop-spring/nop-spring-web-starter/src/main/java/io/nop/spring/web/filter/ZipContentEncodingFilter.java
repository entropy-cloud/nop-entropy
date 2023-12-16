/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.web.filter;

import io.nop.commons.util.StringHelper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;


/**
 * 对于js请求，识别是否已经存在js.gz文件，如果有则直接返回压缩后版本
 */
public class ZipContentEncodingFilter implements Filter {
    static final Logger LOG = LoggerFactory.getLogger(ZipContentEncodingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        doFilterInternal((HttpServletRequest) request, (HttpServletResponse) response, chain);
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/")) {
            Resource resource = getResource("META-INF/resources/index.html.gz");
            if (resource.exists()) {
                setZipHeader("/index.html", response);
                request.getRequestDispatcher("/index.html.gz").forward(request, newResponseWrapper(response));
                return;
            } else {
                resource = getResource("META-INF/resources/index.html");
                if (resource.exists()) {
                    request.getRequestDispatcher("/index.html").forward(request, newResponseWrapper(response));
                    return;
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".json")) {
            String gzPath = path + ".gz";
            Resource resource = getResource("META-INF/resources" + gzPath);
            if (resource.exists()) {
                setZipHeader(path, response);
                request.getRequestDispatcher(gzPath).forward(request, newResponseWrapper(response));
                return;
            }
        }
        if (path.endsWith(".gz")) {
            setZipHeader(path, response);
            filterChain.doFilter(request, newResponseWrapper(response));
        } else {
            filterChain.doFilter(request, response);
        }
    }

    ClassPathResource getResource(String path) {
        return new ClassPathResource(path, ZipContentEncodingFilter.class.getClassLoader());
    }

    HttpServletResponse newResponseWrapper(HttpServletResponse response) {
        return new HttpServletResponseWrapper(response) {
            @Override
            public void setContentType(String type) {
                // 忽略ResourceHandler中设置的contentType
                //super.setContentType(type);
                LOG.trace("ignore-content-type:{}", type);
            }

            @Override
            public void setHeader(String name, String value) {
                if (name.equalsIgnoreCase("content-type"))
                    return;
                super.setHeader(name, value);
            }

            @Override
            public void addHeader(String name, String value) {
                if (name.equalsIgnoreCase("content-type"))
                    return;
                super.addHeader(name, value);
            }
        };
    }

    void setZipHeader(String path, HttpServletResponse response) {
        String fileName = StringHelper.removeTail(StringHelper.fileFullName(path), ".gz");
        response.setHeader("content-encoding", "gzip");
        // guess content type
        String contentType = MimeMappings.DEFAULT.get(StringHelper.fileExt(fileName));
        if (contentType != null) {
            response.setContentType(contentType);
        }
    }
}