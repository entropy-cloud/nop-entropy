/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestNetHelper {

    @Test
    public void testFindLocalIp() {
        // 无论网卡环境如何，findLocalIp都不应抛出NPE，且应返回可用的本机IP。
        // 当所有网卡被过滤时必须通过LOCALHOST4()触发懒初始化，而不是直接读尚未初始化的静态字段
        String ip = NetHelper.findLocalIp();
        assertNotNull(ip);
        assertFalse(ip.isEmpty());
        assertNotNull(NetHelper.LOCALHOST4().getHostAddress());
    }
}
