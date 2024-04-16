/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.api.qrcode;

public interface IQrcodeService {
    /**
     * 生成二维码图片
     *
     * @return 图片的二进制数据
     */
    byte[] createQrcodeBytes(QrcodeOptions options);
}