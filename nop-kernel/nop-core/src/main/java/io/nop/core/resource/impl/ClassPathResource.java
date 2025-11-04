/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.URLHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_PATH_FOR_CLASSPATH_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_OPEN_INPUT_STREAM_FAIL;
import static io.nop.core.CoreErrors.ERR_RESOURCE_OPEN_OUTPUT_STREAM_FAIL;
import static io.nop.core.resource.ResourceConstants.RESOURCE_NS_CLASSPATH;

public class ClassPathResource extends AbstractResource {

    private static final long serialVersionUID = 580938702502424559L;
    private transient URL url;
    private transient ClassLoader classLoader;

    private long length = -1;

    public ClassPathResource(String path, ClassLoader classLoader) {
        super(normalizeClassPath(path));
        this.classLoader = classLoader;
    }

    public ClassPathResource(String path) {
        this(path, null);
    }

    static String normalizeClassPath(String path) {
        if (!ResourceHelper.startsWithNamespace(path, RESOURCE_NS_CLASSPATH))
            throw new NopException(ERR_RESOURCE_INVALID_PATH_FOR_CLASSPATH_RESOURCE).param(ARG_RESOURCE_PATH, path);
        if (path.length() < RESOURCE_NS_CLASSPATH.length() + 2)
            throw new NopException(ERR_RESOURCE_INVALID_PATH_FOR_CLASSPATH_RESOURCE).param(ARG_RESOURCE_PATH, path);

        // 不能是classpath:/a/b/c这种形式，而只能是classpath:a/b/c
        if (path.charAt(RESOURCE_NS_CLASSPATH.length() + 1) == '/') {
            return RESOURCE_NS_CLASSPATH + ':' + path.substring(RESOURCE_NS_CLASSPATH.length() + 2);
        }
        return path;
    }

    String getResPath() {
        return getPath().substring(RESOURCE_NS_CLASSPATH.length() + 1);
    }

    public String toString() {
        return getClass().getSimpleName() + "[path=" + getPath() + ",url=" + toURL() + "]";
    }

    @Override
    public File toFile() {
        URL url = toURL();
        if (URLHelper.isFileURL(url))
            return URLHelper.getFile(url);
        return null;
    }

    @Override
    protected Object internalObj() {
        return toURL();
    }

    @Override
    public String getExternalPath() {
        URL url = toURL();
        return url == null ? null : url.toExternalForm();
    }

    @Override
    public long lastModified() {
        File file = toFile();
        if (file != null)
            return file.lastModified();

        // 通过URL获取length会打开流，比较浪费资源，也没有多大用处
        return -1;
    }

    @Override
    public long length() {
        File file = toFile();
        if (file != null)
            return file.length();
        initLength();
        return length;
    }

    @Override
    public URL toURL() {
        if (url == null)
            url = _toURL();
        return url;
    }

    private void initLength() {
        if (length >= 0)
            return;

        URLConnection conn = null;
        try {
            conn = url.openConnection();
            this.length = conn.getContentLengthLong();
        } catch (Exception e) {
            // ignore
        }
    }

    URL _toURL() {
        String resPath = getResPath();
        ClassLoader cl = classLoader;
        if (cl == null)
            cl = ClassHelper.getDefaultClassLoader();
        URL url = cl.getResource(resPath);
        if (url == null && cl != IResource.class.getClassLoader()) {
            url = IResource.class.getClassLoader().getResource(resPath);
        }
        return url;
    }

    @Override
    public InputStream getInputStream() {
        URL url = toURL();
        if (url == null)
            throw new NopException(ERR_RESOURCE_OPEN_INPUT_STREAM_FAIL).param(ARG_RESOURCE, this);
        try {
            return URLHelper.getInputStream(url);
        } catch (Exception e) {
            throw new NopException(ERR_RESOURCE_OPEN_INPUT_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        File file = toFile();
        if (file == null)
            return super.getOutputStream(append);

        try {
            return new FileOutputStream(file, append);
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_OPEN_OUTPUT_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }

    @Override
    public boolean exists() {
        return toURL() != null;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}