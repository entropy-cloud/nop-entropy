/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.xml;

/**
 * 提供json映射到xml所需的信息
 */
public interface IJsonToXNodeAdapter {
    /**
     * 从json对象上解析得到构造XNode所需的信息。
     *
     * @param key obj在父对象中的key, 可能为null
     * @param obj 待解析的json对象
     */
    NodeData getNodeData(String key, Object obj);

    default void enterBody(NodeData node) {

    }

    default void leaveBody(NodeData node) {

    }
}