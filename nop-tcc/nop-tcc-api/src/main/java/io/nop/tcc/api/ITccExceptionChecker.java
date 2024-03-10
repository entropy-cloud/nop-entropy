/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

public interface ITccExceptionChecker {
    /**
     * 是否未发送到远程服务器就失败了。例如没有找到合法的服务器，或者连接服务器失败
     */
    boolean isClientException(Throwable ex);
}