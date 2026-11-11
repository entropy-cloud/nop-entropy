/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NacosNamingService implements INamingService {
    static final Logger LOG = LoggerFactory.getLogger(NacosNamingService.class);

    /**
     * 4.2-MA-03（plan 2559 裁定 A③）: pagination bounds for getServices — page size is
     * kept at the historical 1000, but the result is now paged to completeness (bounded
     * by MAX_PAGES to guarantee termination against misbehaving servers).
     */
    static final int SERVICE_LIST_PAGE_SIZE = 1000;
    static final int SERVICE_LIST_MAX_PAGES = 100;

    /**
     * 4.2-MA-03（plan 2559 裁定 A②）: default capacity bound of the instance cache
     * (LRU eviction; configurable via nop.cluster.nacos.instance-cache-max-size).
     */
    static final int DEFAULT_INSTANCE_CACHE_MAX_SIZE = 1000;

    private Properties properties = new Properties();
    private String groupName;
    private volatile NamingService namingService; // NOSONAR
    private volatile NamingMaintainService namingMaintainService; // NOSONAR

    /**
     * 4.2-C1-005（plan 2559 裁定 E）: destroy 终态标志 — destroy() 之后 getNamingService /
     * getNamingMaintainService 的懒初始化（含双检内层）必须类型化拒绝，不得静默复活客户端。
     */
    private volatile boolean destroyed;

    /**
     * 4.2-MA-03（plan 2559 裁定 A②）: bounded LRU instance cache. Field NAME is pinned by
     * the R2-18 zero-regression reflection guard (TestNacosInstanceRebuildAndSnapshotGuard
     * reads "instanceCache") — do not rename. destroy-on-remove tears down the remote
     * subscription of evicted entries (see ServiceInfo.destroy).
     */
    private volatile LocalCache<String, ServiceInfo> instanceCache;

    /**
     * 4.2-C2-002（plan 2559 裁定 F）: per-key load mutexes — remote I/O during first install
     * happens under this lock (one loader per service name) instead of inside the cache's
     * bin lock, so a slow remote load never blocks lookups of other service names.
     */
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    private int instanceCacheMaxSize = DEFAULT_INSTANCE_CACHE_MAX_SIZE;

    private static LocalCache<String, ServiceInfo> newInstanceCache(int maxSize) {
        CacheConfig config = new CacheConfig();
        config.setMaximumSize(maxSize);
        config.setDestroyOnRemove(true);
        return new LocalCache<>("nacos-instance-cache", config);
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Properties getProperties() {
        return properties;
    }

    public String getGroupName() {
        return groupName;
    }

    @InjectValue("@cfg:nop.application.group|DEFAULT")
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /**
     * 4.2-MA-03（plan 2559 裁定 A②）: capacity bound of the instance cache. Must be set
     * before the cache is first used (start()/getInstances); resizing an already-built
     * cache would orphan live subscriptions, so it is not supported after first use.
     */
    @InjectValue("@cfg:nop.cluster.nacos.instance-cache-max-size|1000")
    public void setInstanceCacheMaxSize(int instanceCacheMaxSize) {
        Guard.positiveInt(instanceCacheMaxSize, "instanceCacheMaxSize");
        this.instanceCacheMaxSize = instanceCacheMaxSize;
    }

    private LocalCache<String, ServiceInfo> instanceCache() {
        // 4.2-C1-005（plan 2559 裁定 E）: destroy() tears the cache down — post-destroy
        // access must not lazily rebuild it
        if (destroyed)
            throw new NopException(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED);
        LocalCache<String, ServiceInfo> cache = this.instanceCache;
        if (cache == null) {
            synchronized (this) {
                if (destroyed)
                    throw new NopException(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED);
                if (this.instanceCache == null) {
                    // lazy build honoring the (possibly injected) capacity bound; the cache
                    // must be built lazily because setInstanceCacheMaxSize is an IoC
                    // injection that may land after field initialization
                    this.instanceCache = newInstanceCache(this.instanceCacheMaxSize);
                }
                cache = this.instanceCache;
            }
        }
        return cache;
    }

    public NamingService getNamingService() {
        if (destroyed) {
            throw new NopException(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED);
        }
        if (namingService == null) {
            synchronized (this) {
                // 4.2-C1-005（plan 2559 裁定 E）: destroyed check inside the double-check
                // lock as well — a destroy() racing the lazy init must not revive the client
                if (destroyed) {
                    throw new NopException(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED);
                }
                if (namingService == null) {
                    try {
                        namingService = NacosFactory.createNamingService(properties);
                    } catch (NacosException e) {
                        throw newError(e);
                    }
                }
            }
        }
        return namingService;
    }

    public NamingMaintainService getNamingMaintainService() {
        if (destroyed) {
            throw new NopException(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED);
        }
        if (namingMaintainService == null) {
            synchronized (this) {
                // 4.2-C1-005（plan 2559 裁定 E）: same destroyed double-check as above
                if (destroyed) {
                    throw new NopException(NacosErrors.ERR_CLUSTER_NACOS_NAMING_SERVICE_DESTROYED);
                }
                if (namingMaintainService == null) {
                    try {
                        namingMaintainService = NacosFactory.createMaintainService(properties);
                    } catch (NacosException e) {
                        throw newError(e);
                    }
                }
            }
        }
        return namingMaintainService;
    }

    @PostConstruct
    public void start() {
        getNamingService();
        getNamingMaintainService();
    }

    @PreDestroy
    public void destroy() {
        // 4.2-MA-03（plan 2559 裁定 A②）: tear down cached subscriptions while the naming
        // service is still alive, so the listeners do not dangle on a shut-down client
        LocalCache<String, ServiceInfo> cache;
        synchronized (this) {
            cache = this.instanceCache;
            this.instanceCache = null;
        }
        if (cache != null) {
            cache.forEachEntry((serviceName, info) -> {
                try {
                    NacosNamingService.ServiceInfoListener listener = info.getListener();
                    if (listener != null) {
                        listener.unsubscribe();
                    }
                } catch (Exception e) {
                    LOG.warn("nop.err.cluster.nacos.unsubscribe-fail", e);
                }
            });
            cache.clear();
        }

        destroyed = true;

        if (namingService != null) {
            try {
                namingService.shutDown();
            } catch (Exception e) {
                LOG.error("nop.err.cluster.nacos.shutdown-naming-service-fail", e);
            }
            namingService = null;
        }

        if (namingMaintainService != null) {
            try {
                namingMaintainService.shutDown();
            } catch (Exception e) {
                LOG.error("nop.err.cluster.nacos.shutdown-naming-service-fail", e);
            }
            namingMaintainService = null;
        }
    }

    @Override
    public void registerInstance(ServiceInstance instance) {
        Guard.checkEquals(instance.getGroupName(), groupName);

        Instance inst = toInstance(instance);
        try {
            // nacos内部会通过心跳机制不断的更新注册信息
            getNamingService().registerInstance(instance.getServiceName(), groupName, inst);
        } catch (NacosException e) {
            throw newError(e);
        }
    }

    protected NopException newError(NacosException e) {
        throw NopException.adapt(e);
    }

    @Override
    public void unregisterInstance(ServiceInstance instance) {
        Guard.checkEquals(instance.getGroupName(), groupName);

        try {
            Instance inst = toInstance(instance);
            getNamingService().deregisterInstance(instance.getServiceName(), groupName, inst);
        } catch (NacosException e) {
            throw newError(e);
        }
    }

    private Instance toInstance(ServiceInstance serviceInstance) {
        Instance inst = new Instance();
        inst.setIp(serviceInstance.getAddr());
        inst.setPort(serviceInstance.getPort());
        inst.setServiceName(serviceInstance.getServiceName());
        inst.setInstanceId(serviceInstance.getInstanceId());
        inst.setEphemeral(serviceInstance.isEphemeral());
        inst.setEnabled(serviceInstance.isEnabled());
        inst.setHealthy(serviceInstance.isHealthy());
        inst.setMetadata(serviceInstance.getMetadata());
        return inst;
    }

    @Override
    public List<String> getServices() {
        try {
            // 4.2-MA-03（plan 2559 裁定 A③）: previously returned page 1 only (an undeclared
            // subset when the server hosts > 1000 services) — now paged to completeness,
            // bounded by SERVICE_LIST_MAX_PAGES for termination safety
            List<String> result = new ArrayList<>();
            for (int pageNo = 1; pageNo <= SERVICE_LIST_MAX_PAGES; pageNo++) {
                ListView<String> page = getNamingService().getServicesOfServer(pageNo, SERVICE_LIST_PAGE_SIZE);
                List<String> data = page == null ? null : page.getData();
                if (data == null || data.isEmpty())
                    break;
                result.addAll(data);
                if (data.size() < SERVICE_LIST_PAGE_SIZE)
                    break;
            }
            return result;
        } catch (NacosException e) {
            throw newError(e);
        }
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        LocalCache<String, ServiceInfo> cache = instanceCache();

        // 4.2-C2-002（plan 2559 裁定 F）three-phase: check → load outside the map bin lock
        // (per-key load mutex only) → putIfAbsent write-back. Replaces the old
        // computeIfAbsent(serviceName, this::getServiceInfo) whose mapping function ran
        // two remote calls while holding the cache bin lock, blocking all other keys.
        ServiceInfo info = cache.getIfPresent(serviceName);
        if (info != null)
            return info.getServiceInstances();

        Object loadLock = loadLocks.computeIfAbsent(serviceName, k -> new Object());
        try {
            synchronized (loadLock) {
                info = cache.getIfPresent(serviceName); // second check under the load mutex
                if (info != null)
                    return info.getServiceInstances();

                ServiceInfo loaded = getServiceInfo(serviceName);
                return writeBack(cache, serviceName, loaded).getServiceInstances();
            }
        } finally {
            // release the per-key mutex entry; waiters blocked on the old object still
            // re-check the cache and observe the installed entry
            loadLocks.remove(serviceName);
        }
    }

    /**
     * 4.2-C2-002（plan 2559 裁定 F）: putIfAbsent write-back — the loser of the atomic
     * write-back must unsubscribe the listener its own load registered, otherwise the
     * loser's subscription leaks (败者泄漏新变体预防). Extracted package-private so the
     * loser path is deterministically testable (TestNacosLifecycleConcurrency).
     */
    ServiceInfo writeBack(LocalCache<String, ServiceInfo> cache, String serviceName, ServiceInfo loaded) {
        cache.putIfAbsent(serviceName, loaded);
        ServiceInfo winner = cache.getIfPresent(serviceName);
        if (winner == null) {
            // evicted between write-back and read: destroy-on-remove already
            // unsubscribed its listener; serve the local snapshot
            return loaded;
        }
        if (winner != loaded) {
            // putIfAbsent loser: our self-registered listener must be unsubscribed
            // or it leaks (裁定 F)
            loaded.destroy();
        }
        return winner;
    }

    private ServiceInfo getServiceInfo(String serviceName) {
        NamingService namingService = getNamingService();

        ServiceInfo serviceInfo = new ServiceInfo();
        ServiceInfoListener listener = new ServiceInfoListener(serviceName, serviceInfo);
        serviceInfo.setListener(listener);

        // 注册监听器，当instances集合发生变化时更新ServiceInfo
        boolean subscribed = false;
        try {
            namingService.subscribe(serviceName, groupName, listener);
            subscribed = true;

            List<Instance> instances = namingService.getAllInstances(serviceName);
            listener.initInstances(instances);
            return serviceInfo;
        } catch (Exception e) {
            // 4.2-MA-03（plan 2559 裁定 A①）: catch widened from NacosException to Exception —
            // any failure after subscribe(...) must unsubscribe the listener we registered,
            // otherwise the subscription leaks (C3-005)
            if (subscribed) {
                try {
                    listener.unsubscribe();
                } catch (Exception ex) {
                    LOG.warn("nop.err.cluster.nacos.unsubscribe-fail", ex);
                }
            }
            if (e instanceof NacosException)
                throw newError((NacosException) e);
            throw NopException.adapt(e);
        }
    }

    class ServiceInfoListener implements EventListener {
        private final String serviceName;
        private final ServiceInfo serviceInfo;

        // instanceId ==> Pair<instanceText,ServiceInstance>
        private Map<String, Pair<String, ServiceInstance>> instanceMap = Collections.emptyMap();

        /**
         * C1-003 (plan 2521): event-state guard — set when an event-driven
         * updateInstances has landed; a subsequent initInstances with a snapshot
         * pulled BEFORE that event must not overwrite the newer event state.
         */
        private boolean eventReceived;

        public ServiceInfoListener(String serviceName, ServiceInfo serviceInfo) {
            this.serviceName = serviceName;
            this.serviceInfo = serviceInfo;
        }

        @Override
        public void onEvent(Event event) {
            if (event instanceof InstancesChangeEvent) {
                updateInstances(((InstancesChangeEvent) event).getHosts());
            } else if (event instanceof NamingEvent) {
                updateInstances(((NamingEvent) event).getInstances());
            }
        }

        public void unsubscribe() {
            try {
                getNamingService().unsubscribe(serviceName, groupName, this);
            } catch (NacosException e) {
                throw newError(e);
            }
        }

        /**
         * 4.2-MA-03（plan 2559 裁定 A①/F）: unsubscribe used on cleanup paths (load failure,
         * putIfAbsent loser, cache eviction, destroy) — must never mask the primary outcome
         * with a secondary unsubscribe failure.
         */
        public void unsubscribeQuietly() {
            try {
                unsubscribe();
            } catch (Exception e) {
                LOG.warn("nop.err.cluster.nacos.unsubscribe-fail", e);
            }
        }

        synchronized void initInstances(List<Instance> instances) {
            // C1-003: an event already landed while this snapshot was being pulled —
            // the snapshot is stale by construction, do not overwrite the newer state
            if (eventReceived)
                return;
            updateInstances(instances);
        }

        // 更新ServiceInfo中的instances信息
        void updateInstances(List<Instance> instances) {
            Map<String, Pair<String, ServiceInstance>> map = new LinkedHashMap<>();
            List<ServiceInstance> removed = Collections.emptyList();
            synchronized (this) {
                // C1-003: the flag is set inside the same monitor, BEFORE the map is
                // published — initInstances cannot observe a written map with the
                // flag still unset
                eventReceived = true;
                for (Instance instance : instances) {
                    String key = instance.getInstanceId();

                    ServiceInstance serviceInstance;
                    Pair<String, ServiceInstance> old = instanceMap.get(key);
                    if (old == null) {
                        serviceInstance = new ServiceInstance();
                        copyFromInstance(instance, serviceInstance);
                        map.put(instance.getInstanceId(), Pair.of(instance.toString(), serviceInstance));
                    } else {
                        // 如果instance的内容改变了
                        if (!old.getLeft().equals(instance.toString())) {
                            // C1-001 (plan 2521): REBUILD the instance instead of mutating
                            // the shared object in place — old references keep a stable
                            // view (no torn reads) and the `_host` lazy cache of the new
                            // object derives from the new endpoint
                            ServiceInstance rebuilt = new ServiceInstance();
                            copyFromInstance(instance, rebuilt);
                            rebuilt.setModifyIndex(old.getRight().getModifyIndex() + 1);
                            // locally maintained state (not part of the nacos snapshot)
                            // must survive the rebuild
                            rebuilt.getAttrs().putAll(old.getRight().getAttrs());
                            List<Runnable> tasks = old.getRight().getCleanupTasks();
                            for (Runnable task : tasks) {
                                rebuilt.addCleanupTask(task);
                            }
                            serviceInstance = rebuilt;
                            map.put(key, Pair.of(instance.toString(), rebuilt));
                        } else {
                            serviceInstance = old.getRight();
                            map.put(key, old);
                        }
                    }
                }
                if (!instanceMap.isEmpty()) {
                    instanceMap.keySet().removeAll(map.keySet());
                    removed = new ArrayList<>(
                            instanceMap.values().stream().map(Pair::getRight).collect(Collectors.toList()));
                }
                this.instanceMap = map;
                serviceInfo.setServiceInstances(CollectionHelper
                        .immutableList(map.values().stream().map(Pair::getRight).sorted().collect(Collectors.toList())));
            }

            for (ServiceInstance serviceInstance : removed) {
                for (Runnable task : serviceInstance.getCleanupTasks()) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOG.warn("nop.err.cluster.nacos.clean-up-fail", e);
                    }
                }
            }
        }
    }

    static void copyFromInstance(Instance inst, ServiceInstance ret) {
        ret.setAddr(inst.getIp());
        ret.setPort(inst.getPort());
        ret.setServiceName(inst.getServiceName());
        ret.setEphemeral(inst.isEphemeral());
        ret.setClusterName(inst.getClusterName());
        ret.setEnabled(inst.isEnabled());
        ret.setInstanceId(inst.getInstanceId());
        ret.setHealthy(inst.isHealthy());
        // 4.2-MA-08（plan 2559 裁定 C）: the raw `(int)` cast silently truncates/miswraps
        // out-of-range weights (long→int narrowing at the nacos→ServiceInstance seam) —
        // saturate explicitly and WARN to leave the original value traceable
        double weight = inst.getWeight();
        if (weight > Integer.MAX_VALUE) {
            LOG.warn("nop.warn.cluster.nacos.weight-saturate:instanceId={},weight={},saturatedTo={}",
                    inst.getInstanceId(), weight, Integer.MAX_VALUE);
            ret.setWeight(Integer.MAX_VALUE);
        } else if (weight < Integer.MIN_VALUE) {
            LOG.warn("nop.warn.cluster.nacos.weight-saturate:instanceId={},weight={},saturatedTo={}",
                    inst.getInstanceId(), weight, Integer.MIN_VALUE);
            ret.setWeight(Integer.MIN_VALUE);
        } else {
            ret.setWeight((int) weight);
        }
        // P1-4.4-C4（plan 2395）：pos 守卫对齐 ServiceInstance.getNormalizedServiceName——
        // 无组名的 serviceName（不含 "@@"）原样保留，此前直接 substring 抛 StringIndexOutOfBounds
        int pos = inst.getServiceName().indexOf("@@");
        if (pos > 0) {
            ret.setGroupName(inst.getServiceName().substring(0, pos));
            ret.setServiceName(inst.getServiceName().substring(pos + 2));
        }

        if (inst.getMetadata() != null) {
            ret.setMetadata(new TreeMap<>(inst.getMetadata()));

            Set<String> tags = ConvertHelper.toCsvSet(inst.getMetadata().get(ApiConstants.META_KEY_TAGS));
            ret.setTags(tags);
        }
    }
}
