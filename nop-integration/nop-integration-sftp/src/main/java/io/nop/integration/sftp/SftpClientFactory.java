/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.sftp;

import io.nop.integration.api.file.IFileServiceClientFactory;

public class SftpClientFactory implements IFileServiceClientFactory {
    private SftpConfig config;

    public SftpConfig getConfig() {
        return config;
    }

    public void setConfig(SftpConfig config) {
        this.config = config;
    }

    @Override
    public SftpClient newClient() {
        return new SftpClient(config);
    }
}
