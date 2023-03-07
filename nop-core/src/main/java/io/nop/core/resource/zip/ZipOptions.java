/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.zip;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.progress.IProgressListener;

import java.io.Serializable;

@DataBean
public class ZipOptions implements Serializable {
    private static final long serialVersionUID = -2985743199754333442L;
    private String encoding;
    private String userName;
    private String password;
    private boolean jarFile;
    private IProgressListener progressListener;

    public boolean isJarFile() {
        return jarFile;
    }

    public void setJarFile(boolean jarFile) {
        this.jarFile = jarFile;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public IProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(IProgressListener progressListener) {
        this.progressListener = progressListener;
    }
}