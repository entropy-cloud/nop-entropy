/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.cache;

import io.nop.core.resource.IResource;

public interface IResourceContentCache {
    /**
     * 得到缓存的资源文件的文本内容。
     *
     * @param resource  资源文件对象
     * @param allowLoad 如果当前缓存中没有缓存该资源文件，则是否主动从资源文件读取主动更新缓存，并返回从文件读取的结果
     * @return 资源文件文本内容
     */
    String getCachedText(IResource resource, boolean allowLoad);

    void clearCachedText(IResource resource, boolean removeFile);

    /**
     * 更新缓存，并可以选择是否同步写入到文件中
     *
     * @param resource        资源文件对象
     * @param text            待缓存的文本内容
     * @param flushToFile     是否同步更新到文件中
     * @param removeEmptyFile 当flushToFile为true时，如果text为空字符串，是否自动删除对应的文件
     * @return 如果返回false, 则表示当前文本与此前缓存的文本内容一致
     */
    boolean updateCachedText(IResource resource, String text, boolean flushToFile, boolean removeEmptyFile);
}
