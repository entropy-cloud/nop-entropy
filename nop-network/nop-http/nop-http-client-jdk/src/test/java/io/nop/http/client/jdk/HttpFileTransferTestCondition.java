/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.jdk;

/**
 * Test gating helper for HTTP client file transfer tests on Windows.
 *
 * <p>These tests spin up a loopback HttpServer, kick off ranged downloads
 * and inspect checksum behaviour. On Windows the loopback server side of
 * {@code .part} files briefly keeps the file handle open after the server
 * has returned, racing with the next test's file create. Linux/macOS
 * handle releases fast enough that the race never triggers, so the tests
 * are reliable on the GitHub Actions default runners and we skip on
 * Windows rather than try to chase a transient platform race.
 */
final class HttpFileTransferTestCondition {
    private HttpFileTransferTestCondition() {
    }

    static boolean isFileTransferReliableOnThisOS() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return !osName.contains("win");
    }
}