/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.jpath;

import java.util.List;

/**
 * @deprecated Use {@code io.nop.jq.jsonpath.NopJsonPath} from nop-jq module instead.
 * This class is kept for backward compatibility only and will be removed in a future version.
 */
@Deprecated
public class JPath {

    private final String pathString;

    public JPath(String pathString) {
        this.pathString = pathString;
    }

    public static JPath compile(String path) {
        return new JPath(path);
    }

    public static JPath jpath(String path) {
        return compile(path);
    }

    public static JPath compileWithCache(String path) {
        return compile(path);
    }

    public String getPathString() {
        return pathString;
    }

    /**
     * @deprecated Migrate to NopJsonPath.eval()
     */
    @Deprecated
    public Object get(Object bean) {
        throw new UnsupportedOperationException(
                "JPath.get() is deprecated. Migrate to io.nop.jq.jsonpath.NopJsonPath.eval(bean, path)");
    }

    /**
     * @deprecated Migrate to NopJsonPath.evalOne()
     */
    @Deprecated
    public Object getOne(Object bean) {
        throw new UnsupportedOperationException(
                "JPath.getOne() is deprecated. Migrate to io.nop.jq.jsonpath.NopJsonPath.evalOne(bean, path)");
    }

    /**
     * @deprecated Migrate to NopJsonPath.set()
     */
    @Deprecated
    public void get(Object bean, Object value) {
        throw new UnsupportedOperationException(
                "JPath.set() is deprecated. Migrate to io.nop.jq.jsonpath.NopJsonPath.set(bean, path, value)");
    }

    /**
     * @deprecated Migrate to NopJsonPath.remove()
     */
    @Deprecated
    public void delete(Object bean) {
        throw new UnsupportedOperationException(
                "JPath.delete() is deprecated. Migrate to io.nop.jq.jsonpath.NopJsonPath.remove(bean, path)");
    }

    public static Object get(Object bean, String path) {
        throw new UnsupportedOperationException(
                "JPath.get() is deprecated. Migrate to io.nop.jq.jsonpath.NopJsonPath.eval(bean, path)");
    }

    public static void get(Object bean, String path, Object value) {
        throw new UnsupportedOperationException(
                "JPath.set() is deprecated. Migrate to io.nop.jq.jsonpath.NopJsonPath.set(bean, path, value)");
    }

    public static void delete(Object bean, String path) {
        throw new UnsupportedOperationException(
                "JPath.delete() is deprecated. Migrate to io.nop.jq.jsonpath.NopJsonPath.remove(bean, path)");
    }
}
