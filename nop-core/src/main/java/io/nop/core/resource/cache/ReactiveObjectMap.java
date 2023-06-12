/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.cache;

import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.core.resource.deps.VirtualResourceDependencySet;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ReactiveObjectMap {
    private final Map<String, ReactiveObject> objMap = new ConcurrentHashMap<>();

    private static class ReactiveObject {
        final ResourceDependencySet deps;
        final CompletableFuture<Object> future = new CompletableFuture<>();

        public ReactiveObject(String path) {
            this.deps = new VirtualResourceDependencySet(path);
        }

        public boolean isChanged() {
            return ResourceComponentManager.instance().isAnyDependsChange(deps.getDepends());
        }
    }

    /**
     * 第一次运行时收集依赖。以后每次运行前先检查依赖是否已经发生变化，如果没有变化，则直接返回上次的值，否则 重新执行函数，并重新收集依赖。
     *
     * @param key   对象的唯一键
     * @param fn    待执行的函数
     * @param scope 上下文环境
     */
    public Object get(String key, IEvalFunction fn, IEvalScope scope) {
        ReactiveObject obj = objMap.get(key);
        if (obj == null || obj.isChanged()) {
            objMap.remove(key, obj);
            obj = new ReactiveObject(key);
            ReactiveObject oldObj = objMap.putIfAbsent(key, obj);
            if (oldObj != null) {
                obj = oldObj;
            } else {
                try {
                    obj.future.complete(ResourceComponentManager.instance().collectDependsTo(obj.deps,
                            () -> fn.call0(null, scope)));
                } catch (Exception e) {
                    obj.future.completeExceptionally(e);
                }
            }
        }
        return FutureHelper.getFromFuture(obj.future);
    }
}