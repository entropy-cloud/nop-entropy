/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.client;

/**
 * 文件上传模式，协议契约见 ai-dev/design/nop-network/file-transfer-design.md
 */
public enum UploadMode {
    /**
     * 二进制流直传：PUT + application/octet-stream，元数据经 x-file-* 头标记
     */
    BINARY,

    /**
     * base64 表单上传：文件内容 base64 编码后作为普通 multipart 文本字段提交
     */
    BASE64_FORM
}
