/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.backend;

import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.backend.EvalBackendCapability;
import io.nop.xlang.backend.IEvalStaticBackend;
import io.nop.xlang.backend.IEvalStaticBinding;
import io.nop.xlang.java.gen.GeneratedClassManifest;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * java 执行后端（静态生成物形态）：扫描清单成员资格判定 + 生成类绑定查找。
 *
 * <p>清单与绑定的生产供给（I10 落地）：扫描清单为构建期扫描任务产物（构建集成阶段接入
 * {@link #setStaticScanList}）；生产 binder = {@link GeneratedClassBindingBinder}（生成类清单内存
 * 契约消费 + classpath 常规加载 + 树指纹一致性校验 + D5 分级降级观测），经
 * {@link #setGeneratedClassManifest} 接入（内部走 I9 移交的 {@link #setBinder} 缝——同一接入缝、
 * 同一降级语义，仅供给方替换）。测试域仍可经 {@link #setBinder} 直接注入合成绑定。
 * 缺省（未设置清单与 binder）为惰性空态：无静态成员，全部资源走动态路径，行为与现状一致。
 */
public class JavaEvalExecutionBackend implements IEvalStaticBackend {

    public static final String BACKEND_ID = "java";

    private static final JavaEvalExecutionBackend INSTANCE = new JavaEvalExecutionBackend();

    public static JavaEvalExecutionBackend instance() {
        return INSTANCE;
    }

    private final Set<String> staticScanList = ConcurrentHashMap.newKeySet();

    private volatile IEvalStaticBindingBinder binder;

    private volatile String unavailableReason;

    @Override
    public String getBackendId() {
        return BACKEND_ID;
    }

    @Override
    public Set<EvalBackendCapability> getCapabilities() {
        return Collections.unmodifiableSet(
                EnumSet.of(EvalBackendCapability.STATIC_GENERATED));
    }

    @Override
    public boolean isAvailable() {
        return unavailableReason == null;
    }

    @Override
    public String getUnavailableReason() {
        return unavailableReason;
    }

    /**
     * 标记不可用（保留原因）。生产接入缝：构建管线漏跑/生成产物完整性失败时由集成方标记；
     * 不可用条目查询经注册表诊断接口。
     */
    public void markUnavailable(String reason) {
        this.unavailableReason = reason;
    }

    public void clearUnavailable() {
        this.unavailableReason = null;
    }

    /** 生成类绑定供给方接入（生产 = java 生成类加载集成；测试 = 合成绑定） */
    public void setBinder(IEvalStaticBindingBinder binder) {
        this.binder = binder;
    }

    /**
     * 生产供给缝（I10）：设置生成类清单内存契约——内部构造生产 binder
     * {@link GeneratedClassBindingBinder} 经 {@link #setBinder} 缝接入。传 null 清除（回空态）。
     */
    public void setGeneratedClassManifest(GeneratedClassManifest manifest) {
        setBinder(manifest == null ? null : new GeneratedClassBindingBinder(manifest));
    }

    /** 设置扫描清单成员（构建期扫描任务产物；测试合成清单） */
    public void setStaticScanList(Collection<String> resourcePaths) {
        staticScanList.clear();
        if (resourcePaths != null)
            staticScanList.addAll(resourcePaths);
    }

    public Set<String> getStaticScanList() {
        return Collections.unmodifiableSet(staticScanList);
    }

    @Override
    public boolean isStaticCandidate(String resourcePath) {
        return resourcePath != null && staticScanList.contains(resourcePath);
    }

    @Override
    public IEvalStaticBinding findStaticBinding(String resourcePath, IExecutableExpression tree) {
        IEvalStaticBindingBinder b = binder;
        return b == null ? null : b.findStaticBinding(resourcePath, tree);
    }
}
