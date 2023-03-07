/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.commons.util.URLHelper;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * @author canonical_entropy@163.com
 */
public class URLResource extends AbstractResource {
    private static final long serialVersionUID = 8575587688389838873L;

    private final URL url;

    public URLResource(String path, URL url) {
        super(path);
        this.url = url;
    }

    public String toString() {
        return "URLResource[" + getPath() + ",url=" + toURL() + "]";
    }

    @Override
    protected Object internalObj() {
        return url;
    }

    @Override
    public String getExternalPath() {
        return URLHelper.getCanonicalUrl(url);
    }

    @Override
    public URL toURL() {
        return url;
    }

    @Override
    public File toFile() {
        if (URLHelper.isFileURL(url))
            return URLHelper.getFile(url);
        return null;
    }

    @Override
    public boolean exists() {
        return URLHelper.exists(url);
    }

    @Override
    public InputStream getInputStream() {
        InputStream is = URLHelper.getInputStream(url);
        return is;
    }
}