/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.api.qrcode;

import java.io.File;

public interface IQrcodeService {
    /**
     * 生成二维码图片
     *
     * @param type  二维码类型
     * @param page  页面路径
     * @param width 宽度
     * @return 图片的二进制数据
     */
    byte[] createQrcodeBytes(String type, String scene, String page, int width);

    File createQrcodeFile(String type, String scene, String page, int width, File dir);
}