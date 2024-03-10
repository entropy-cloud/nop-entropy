/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.IoHelper;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;

/**
 * IResource接口封装了根据path可以唯一确定的资源对象。 引入标准路径概念，从而支持资源定制。 资源接口不包含父子关系定义，因此更容易封装分布式存储等大数据资源。
 */
public interface IResource extends IResourceReference {
    /**
     * 资源对象相等的条件是类型和path都相等
     */
    boolean equals(Object o);

    /**
     * 资源的存储路径，可能是在定制目录下
     */
    String getPath();

    /**
     * 标准路径
     */
    String getStdPath();

    /**
     * 得到虚拟路径对应的实际文件的真实路径, 一般情况下为toURL().toExternalForm()，例如由/a/b.txt得到file://xx/_vfs/a/b.txt
     */
    String getExternalPath();

    /**
     * 资源名
     */
    String getName();

    /**
     * 资源的长度，如果资源不存在或者长度未知，则返回-1
     */
    long length();

    /**
     * 资源的最后修改时间
     */
    long lastModified();

    void setLastModified(long time);

    /**
     * 判断资源是否存在
     */
    boolean exists();

    /**
     * 试图删除资源
     *
     * @return true表示成功删除
     */
    boolean delete();

    /**
     * 只读目录不允许新建子节点，而只读文件不允许写入
     */
    boolean isReadOnly();

    boolean isDirectory();

    /**
     * 打开输入流，读取资源内容
     */
    InputStream getInputStream();

    /**
     * 打开输出流，会自动创建目录和文件
     */
    default OutputStream getOutputStream() {
        return getOutputStream(false);
    }

    OutputStream getOutputStream(boolean append);

    default Reader getReader(String encoding) {
        try {
            return IoHelper.toReader(getInputStream(), encoding);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    default Writer getWriter(String encoding, boolean append) {
        try {
            return IoHelper.toWriter(getOutputStream(append), encoding);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    default Writer getWriter(String encoding) {
        return getWriter(encoding, false);
    }

    default String readText() {
        return readText(null);
    }

    default String readText(String encoding) {
        Reader rd = null;
        try {
            rd = getReader(encoding);
            return IoHelper.readText(rd);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(rd);
        }
    }

    default void writeText(String text, String encoding) {
        Writer out = null;
        try {
            out = getWriter(encoding);
            out.write(text);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(out);
        }
    }

    /**
     * 如果资源对应于一个本地文件，这里返回本地文件对象
     */
    File toFile();

    /**
     * 如果资源不能采用URL表达，则返回null
     */
    URL toURL();

    default URI toURI() {
        URL url = toURL();
        if (url == null)
            return null;
        try {
            return url.toURI();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 将资源文件内容转存到本地文件中
     */
    void saveToFile(@Nonnull File file);

    /**
     * 特殊的资源文件可能具有内部优化，避免流拷贝。
     */
    default void saveToResource(IResource resource) {
        saveToResource(resource, null);
    }

    void saveToResource(IResource resource, IStepProgressListener listener);

    default void writeToStream(OutputStream os) {
        writeToStream(os, null);
    }

    void writeToStream(OutputStream os, IStepProgressListener listener);

    IResourceRegion getResourceRegion(LongRangeBean range);

}