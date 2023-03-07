/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.lock;

/**
 * 用户可以锁定一个资源一段时间
 */
public interface IResourceLockState {
    String getResourceId();

    String getLockerId();

    long getLockTime();

    long getExpireTime();

    long getCreateTime();
}