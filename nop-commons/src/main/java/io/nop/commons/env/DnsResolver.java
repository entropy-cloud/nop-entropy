/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.env;

import io.nop.api.core.util.Guard;

import java.net.InetAddress;

public class DnsResolver {
    public static IDnsResolver DEFAULT = InetAddress::getByName;

    private static IDnsResolver _instance = DEFAULT;

    public static IDnsResolver instance() {
        return Guard.notNull(_instance, "DnsResolver not initialized");
    }

    public static void registerInstance(IDnsResolver dnsResolver) {
        _instance = dnsResolver;
    }
}