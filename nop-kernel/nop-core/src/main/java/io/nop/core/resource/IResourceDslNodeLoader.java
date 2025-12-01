package io.nop.core.resource;

import io.nop.core.lang.xml.XNode;

public interface IResourceDslNodeLoader {
    enum ResolvePhase {
        /**
         * 原始内容
         */
        raw,

        /**
         * 执行feature表达式过滤
         */
        filtered,

        /**
         * 加载基础节点，并和当前节点合并
         */
        merged,

        /**
         * 合并post-extends的结果
         */
        completed
    }

    XNode loadDslNodeFromResource(IResource resource, ResolvePhase resolvePhase);
}
