/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.api.core.beans.FieldSelectionBean;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * 提供类似GraphQL的批量数据加载优化
 */
public interface IOrmBatchLoadQueue {

    IOrmBatchLoadQueue enqueueSelection(Collection<?> ormObjects, FieldSelectionBean selection);

    IOrmBatchLoadQueue enqueueEntity(IOrmEntity entity, FieldSelectionBean selection);

    /**
     * 记录需要加载的实体或者集合。会自动忽略没有识别的persistObject。
     *
     * @param ormObject 可能是IOrmEntity或者IOrmEntitySet
     * @return this
     */
    IOrmBatchLoadQueue enqueue(Object ormObject);

    /**
     * 记录多个需要加载的实体或者集合
     *
     * @param ormObjects l可能是IOrmEntity或者IOrmEntitySet
     * @return this
     */
    IOrmBatchLoadQueue enqueueMany(Collection<?> ormObjects);

    /**
     * 记录需要加载的实体属性。会自动忽略没有识别的属性。
     *
     * @param ormObject 可能是IOrmEntity或者IOrmEntitySet
     * @param propName  可能是复合属性，内部要执行多次加载任务。
     * @return this
     */
    IOrmBatchLoadQueue enqueueProp(Object ormObject, String propName);

    /**
     * 记录多个需要延迟加载的实体属性。
     *
     * @param ormObjects 可能是IOrmEntity或者IOrmEntitySet
     * @param propNames  可能是复合属性，内部要执行多次加载任务
     * @return this
     */
    IOrmBatchLoadQueue enqueueManyProps(Collection<?> ormObjects, @Nonnull Collection<String> propNames);

    boolean isEmpty();

    /**
     * 注册回调函数，当flush被调用之后触发。如果当前没有待flush的对象，则立刻触发
     */
    IOrmBatchLoadQueue afterFlush(Runnable task);

    /**
     * 批量执行队列中所有的延迟加载任务
     */
    void flush();
}