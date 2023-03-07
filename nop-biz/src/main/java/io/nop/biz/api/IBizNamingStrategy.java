/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.api;

/**
 * 根据bizObjName来确定biz模型文件的路径
 */
public interface IBizNamingStrategy {
    String getBizModelPath(String bizObjName);

    String getBizObjName(String bizModelPath);
}
