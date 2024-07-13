/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.initialize;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.collections.SafeOrderedComparator;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.tenant.ResourceTenantManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import static io.nop.core.CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL;
import static io.nop.core.CoreConfigs.CFG_MODULE_DISABLED_MODULE_NAMES;

public class CoreInitialization {
    static final Logger LOG = LoggerFactory.getLogger(CoreInitialization.class);

    private static List<ICoreInitializer> initializers;
    private static int initializationLevel = -1;
    private static boolean initialized;
    private static long initializeBeginTime;

    private static volatile boolean initializerRunning = false;
    private static Map<String, Object> bootstrapConfig = null;

    private static final Object lock = new Object();
    private static List<Runnable> cleanups;

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isForAnalyze() {
        return CFG_CORE_MAX_INITIALIZE_LEVEL.get() == CoreConstants.INITIALIZER_PRIORITY_ANALYZE;
    }

    public static boolean isInitializerRunning() {
        return initializerRunning;
    }

    public static synchronized void initialize() {
        initializeTo(Integer.MAX_VALUE);
    }

    public static long getInitializeBeginTime() {
        return initializeBeginTime;
    }

    public static synchronized void initializeTo(int level) {
        if (initializationLevel >= level)
            return;

        try {
            initializerRunning = true;
            initializeBeginTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.core.begin-initialize:workDir={},initializationLevel={}",
                    FileHelper.currentDir(), initializationLevel);

            loadBootstrapConfig();

            if (initializers == null)
                initializers = loadInitializers();

            int maxLevel = Math.min(CFG_CORE_MAX_INITIALIZE_LEVEL.get(), level);

            int fromLevel = initializationLevel;

            for (ICoreInitializer initializer : initializers) {
                if (initializer.order() <= fromLevel)
                    continue;

                if (initializer.order() <= maxLevel) {
                    LOG.info("nop.core.run-initializer:class={}", initializer.getClass().getName());
                    initializer.initialize();
                    initializationLevel = initializer.order();
                } else {
                    LOG.info("nop.core.skip-initializer-for-high-level:class={},level={},maxLevel={}",
                            initializer.getClass(), initializer.order(), maxLevel);
                }
            }

            LOG.info("nop.core.end-initialize:usedTime={},initializationLevel={}",
                    CoreMetrics.currentTimeMillis() - initializeBeginTime, initializationLevel);

            initialized = true;
        } catch (Exception e) {
            // e.printStackTrace();
            LOG.error("nop.core.initialize-fail", e);
            destroy();
            throw NopException.adapt(e);
        } finally {
            initializerRunning = false;
        }
    }

    static List<ICoreInitializer> loadInitializers() {
        ServiceLoader<ICoreInitializer> loader = ServiceLoader.load(ICoreInitializer.class);

        Set<String> disabled = getDisabled();
        List<ICoreInitializer> list = CollectionHelper.iteratorToList(loader.iterator());
        List<ICoreInitializer> enabled = new ArrayList<>();
        for (ICoreInitializer initializer : list) {
            if (initializer.isEnabled() && !disabled.contains(initializer.getClass().getSimpleName())) {
                enabled.add(initializer);
            } else {
                LOG.info("nop.core.ignore-disabled-initializer:class={}", initializer.getClass());
            }
        }
        enabled.sort(SafeOrderedComparator.DEFAULT);
        return enabled;
    }

    public static int initializationLevel() {
        return initializationLevel;
    }

    public static void registerCleanup(Runnable task) {
        synchronized (lock) {
            if (cleanups == null)
                cleanups = new ArrayList<>();
            cleanups.add(task);
        }
    }

    public static synchronized void destroy() {
        bootstrapConfig = null;

        List<ICoreInitializer> list = initializers;
        if (list == null)
            return;

        initializerRunning = true;
        try {
            for (ICoreInitializer initializer : CollectionHelper.reverseList(list)) {
                if (initializationLevel < initializer.order())
                    continue;

                initializationLevel = initializer.order();
                LOG.info("nop.core.destroy-initializer:class={}", initializer.getClass().getName());
                try {
                    initializer.destroy();
                } catch (Throwable e) {
                    // e.printStackTrace();
                    LOG.error("nop.core.destroy-initializer-fail", e);
                }
            }
            List<Runnable> tasks;
            synchronized (lock) {
                tasks = cleanups;
                cleanups = null;
            }

            if (tasks != null) {
                for (Runnable task : tasks) {
                    try {
                        task.run();
                    } catch (Throwable e) {
                        LOG.error("nop.core.run-cleanup-fail", e);
                    }
                }
            }

            LOG.info("nop.core.destroy");
            initializers = null;
            initializationLevel = -1;
            initialized = false;
            ReflectionManager.instance().clearCache();
            GlobalCacheRegistry.instance().clearAllCache();
        } finally {
            ResourceTenantManager.instance().reset();
            initializerRunning = false;
        }
    }

    public static synchronized void reinitialize() {
        reinitialize(-1);
    }

    public static synchronized void reinitialize(int fromLevel) {
        try {
            initializerRunning = true;

            List<ICoreInitializer> list = initializers;
            if (list == null) {
                list = loadInitializers();
            }

            LOG.info("nop.core.reinitialize:level={}", fromLevel);

            int n = list.size();
            int i;
            for (i = n - 1; i >= 0; i--) {
                ICoreInitializer initializer = list.get(i);
                if (initializer.order() > initializationLevel)
                    continue;

                if (initializer.order() < fromLevel)
                    break;

                initializationLevel = initializer.order();
                LOG.info("nop.core.destroy-initializer:class={}", initializer.getClass().getName());
                try {
                    initializer.destroy();
                } catch (Throwable e) {
                    // e.printStackTrace();
                    LOG.error("nop.core.destroy-initializer-fail", e);
                }
            }

            ReflectionManager.instance().clearCache();
            GlobalCacheRegistry.instance().clearAllCache();

            int maxLevel = CFG_CORE_MAX_INITIALIZE_LEVEL.get();
            i++;
            for (; i < n; i++) {
                ICoreInitializer initializer = list.get(i);

                if (initializer.order() <= maxLevel) {
                    LOG.info("nop.core.run-initializer:class={}", initializer.getClass().getName());
                    initializer.initialize();
                    initializationLevel = initializer.order();
                }
            }
            initialized = true;
        } finally {
            initializerRunning = false;
        }
    }

    private static Set<String> getDisabled() {
        // 通过System.properties配置
        Object configValue = CFG_MODULE_DISABLED_MODULE_NAMES.get();
        // 如果未找到，则从bootstrap.yaml配置文件中查找配置项
        if (configValue == null)
            configValue = bootstrapConfig.get(CFG_MODULE_DISABLED_MODULE_NAMES.getName());

        Set<String> disabledNames = ConvertHelper.toCsvSet(configValue);
        if (disabledNames == null)
            disabledNames = Collections.emptySet();
        return disabledNames;
    }

    private static void loadBootstrapConfig() {
        if (bootstrapConfig != null)
            return;

        bootstrapConfig = Collections.emptyMap();
        IResource resource = new ClassPathResource("classpath:bootstrap.yaml");
        if (resource.exists()) {
            Map<String, Object> map = JsonTool.parseBeanFromResource(resource, Map.class);
            if (map != null) {
                bootstrapConfig = CollectionHelper.flattenMap(map);

                String profile = getProfile(bootstrapConfig);

                // 设置bootstrap.yaml文件中的参数到ConfigProvider中。这样ICoreInitializer插件执行的时候可以读取这些参数
                for (Map.Entry<String, Object> entry : bootstrapConfig.entrySet()) {
                    // 如果明确通过命令行参数指定，则以该参数为准
                    if (System.getProperty(entry.getKey()) != null)
                        continue;

                    String name = entry.getKey();
                    if (name.startsWith("%")) {
                        int pos = name.indexOf('.');
                        if (pos < 0)
                            continue;
                        String profileKey = name.substring(1, pos);
                        if (profileKey.equals(profile)) {
                            LOG.info("nop.config.use-bootstrap-config:name={},value={}", entry.getKey(),
                                    entry.getValue());
                            AppConfig.getConfigProvider().assignConfigValue(name.substring(pos + 1), entry.getValue());
                        }
                        continue;
                    }
                    AppConfig.getConfigProvider().assignConfigValue(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private static String getProfile(Map<String, Object> map) {
        String profile = ApiConfigs.CFG_PROFILE.get();
        if (StringHelper.isEmpty(profile)) {
            profile = (String) map.get(ApiConfigs.CFG_PROFILE.getName());
        }
        return profile;
    }
}