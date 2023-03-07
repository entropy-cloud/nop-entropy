/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.json;

import io.nop.api.core.annotations.data.DataBean;

import java.lang.reflect.Type;

@DataBean
public class JsonParseOptions {
    private boolean keepLocation;
    private boolean keepComment;
    private boolean intern;
    private boolean yaml;
    private String defaultEncoding;
    private boolean strictMode = true;
    private boolean traceDepends;

    private Type targetType;
    private boolean ignoreUnknownProp;

    public static JsonParseOptions create() {
        return new JsonParseOptions();
    }

    public JsonParseOptions withYaml(boolean yaml) {
        this.setYaml(yaml);
        return this;
    }

    public JsonParseOptions withStrictMode(boolean strictMode) {
        this.setStrictMode(strictMode);
        return this;
    }

    public JsonParseOptions withTargetType(Type targetType) {
        setTargetType(targetType);
        return this;
    }

    public JsonParseOptions withIgnoreUnknownProp(boolean ignoreUnknownProp) {
        this.setIgnoreUnknownProp(ignoreUnknownProp);
        return this;
    }

    public boolean isYaml() {
        return yaml;
    }

    public void setYaml(boolean yaml) {
        this.yaml = yaml;
    }

    /**
     * 是否解析为JObject, 以记录解析结果对应的源码位置
     *
     * @return
     */
    public boolean isKeepLocation() {
        return keepLocation;
    }

    /**
     * 是否解析为JObject, 以记录解析结果对应的源码位置
     *
     * @return
     */
    public void setKeepLocation(boolean keepLocation) {
        this.keepLocation = keepLocation;
    }

    /**
     * 是否解析为JObject, 以记录源码中的注释
     *
     * @return
     */
    public boolean isKeepComment() {
        return keepComment;
    }

    /**
     * 是否解析为JObject, 以记录源码中的注释
     *
     * @return
     */
    public void setKeepComment(boolean keepComment) {
        this.keepComment = keepComment;
    }

    /**
     * 是否对Map的key执行string.intern()调用以减少内存占用
     *
     * @return
     */
    public boolean isIntern() {
        return intern;
    }

    /**
     * 是否对Map的key执行string.intern()调用以减少内存占用
     *
     * @return
     */
    public void setIntern(boolean intern) {
        this.intern = intern;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    /**
     * 从二进制文件读取时的语言编码，缺省为UTF-8
     */
    public void setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    /**
     * 是否按照json语言规范进行解析。json语言规范规定key和value都要使用双引号包裹，且不支持注释
     *
     * @param strictMode
     */
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public boolean isTraceDepends() {
        return traceDepends;
    }

    /**
     * 是否记录资源文件的依赖关系
     */
    public void setTraceDepends(boolean traceDepends) {
        this.traceDepends = traceDepends;
    }

    public Type getTargetType() {
        return targetType;
    }

    public void setTargetType(Type targetType) {
        this.targetType = targetType;
    }

    public boolean isIgnoreUnknownProp() {
        return ignoreUnknownProp;
    }

    public void setIgnoreUnknownProp(boolean ignoreUnknownProp) {
        this.ignoreUnknownProp = ignoreUnknownProp;
    }
}
