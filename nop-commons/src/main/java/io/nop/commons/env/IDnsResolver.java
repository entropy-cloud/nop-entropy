/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.env;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 对DNS命名解析过程的封装
 */
public interface IDnsResolver {
    InetAddress resolve(String host) throws UnknownHostException;
}