/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.pool;

public interface IPooledObject {

    /**
     * 从缓冲池中获取时回调此函数，完成对象状态检查工作
     *
     * @return 如果返回false, 则表示对象内部状态不符合要求，必须要被关闭
     */
    default boolean onBorrowFromPool() {
        return true;
    }

    /**
     * 放回到缓冲池中时回调
     *
     * @return 如果返回false, 则表示对象内部状态无法被重置为可复用状态，必须要被关闭
     */
    default boolean onReturnToPool() {
        return true;
    }

    /**
     * 需要定期检查对象的有效性
     *
     * @return
     */
    boolean checkValid();

    /**
     * 销毁对象，释放资源
     */
    void destroy();
}