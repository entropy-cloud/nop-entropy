/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.net;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.util.NetHelper;
import io.nop.commons.util.StringHelper;

public class DefaultServerAddrFinder implements IServerAddrFinder {
    private String addr;

    @InjectValue("@cfg:nop.server.addr|")
    public void setAddr(String addr) {
        this.addr = addr;
    }

    @Override
    public String findAddr() {
        if (StringHelper.isEmpty(addr))
            addr = NetHelper.findLocalIp();
        return addr;
    }
}
