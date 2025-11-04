/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

/**
 * 特定名字空间的资源由此接口负责装载。名字空间仅仅提供名称别名，父子关系由IResourceStore自己维护
 */
public interface IResourceNamespaceHandler {
    String getNamespace();

    /**
     * 名字空间可以是在原加载器基础上进行的一种逻辑上的再定义，因此它的内部实现可能是利用locator去作实际的资源加载工作。
     *
     * @param path    包含名字空间步骤的全路径
     * @param locator 解析名字空间时可以利用这里的locator来返回真正对应的资源对象
     * @return 返回的IResource对象的路径不一定是path
     */
    IResource getResource(String path, IResourceStore locator);
}