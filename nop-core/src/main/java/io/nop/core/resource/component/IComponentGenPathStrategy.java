/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import java.util.Set;

/**
 * 根据模型对象生成组件时，每一个被生成的组件需要有一个唯一的资源文件路径，用于保存生成结果，也用于远程访问时指定对应的url。
 */
public interface IComponentGenPathStrategy {
    Set<String> getGenFileTypes();

    /**
     * 不同的genFormat可能对应同一个fileType
     *
     * @param genFormat 生成的组件文件格式。比如同样的模型可能生成手机小程序和网页组件
     */
    String buildComponentPath(String modelPath, String genFormat);

    /**
     * 判断组件路径是否由本策略生成
     *
     * @param path 生成的组件路径
     */
    boolean supportComponentPath(String path);

    /**
     * 如果不是由本模型生成，则抛出异常
     */
    ComponentGenPath parseComponentPath(String path);
}