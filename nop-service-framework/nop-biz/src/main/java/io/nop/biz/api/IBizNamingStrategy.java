/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.api;

/**
 * 根据bizObjName来确定biz模型文件的路径
 */
public interface IBizNamingStrategy {
    String getBizModelPath(String bizObjName);

    String getBizObjName(String bizModelPath);
}
