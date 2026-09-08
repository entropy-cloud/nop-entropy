/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.okhttp;

/**
 * Test gating helper for OkHttp-backed HTTP client file transfer tests on
 * Windows. See the JDK variant of this class for the full rationale; the
 * same loopback file-handle race makes these tests unreliable on Windows.
 */
final class HttpFileTransferTestCondition {
    private HttpFileTransferTestCondition() {
    }

    static boolean isFileTransferReliableOnThisOS() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return !osName.contains("win");
    }
}