/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NacosNamingService implements INamingService {
    static final Logger LOG = LoggerFactory.getLogger(NacosNamingService.class);

    private Properties properties = new Properties();
    private String groupName;
    private volatile NamingService namingService; // NOSONAR
    private volatile NamingMaintainService namingMaintainService; // NOSONAR

    private Map<String, ServiceInfo> instanceCache = new ConcurrentHashMap<>();

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Properties getProperties() {
        return properties;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public NamingService getNamingService() {
        if (namingService == null) {
            synchronized (this) {
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
        if (namingMaintainService == null) {
            synchronized (this) {
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
            return getNamingService().getServicesOfServer(1, 1000).getData();
        } catch (NacosException e) {
            throw newError(e);
        }
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        return instanceCache.computeIfAbsent(serviceName, this::getServiceInfo).getServiceInstances();
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

            List<Instance> instances = getNamingService().getAllInstances(serviceName);
            listener.initInstances(instances);
            return serviceInfo;
        } catch (NacosException e) {
            if (subscribed)
                listener.unsubscribe();
            throw newError(e);
        }
    }

    class ServiceInfoListener implements EventListener {
        private final String serviceName;
        private final ServiceInfo serviceInfo;

        // instanceId ==> Pair<instanceText,ServiceInstance>
        private Map<String, Pair<String, ServiceInstance>> instanceMap = Collections.emptyMap();

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

        synchronized void initInstances(List<Instance> instances) {
            updateInstances(instances);
        }

        // 更新ServiceInfo中的instances信息
        void updateInstances(List<Instance> instances) {
            Map<String, Pair<String, ServiceInstance>> map = new LinkedHashMap<>();
            List<ServiceInstance> removed = Collections.emptyList();
            synchronized (this) {
                for (Instance instance : instances) {
                    String key = instance.getInstanceId();

                    ServiceInstance serviceInstance;
                    Pair<String, ServiceInstance> old = instanceMap.get(key);
                    if (old == null) {
                        serviceInstance = new ServiceInstance();
                        copyFromInstance(instance, serviceInstance);
                        map.put(instance.getInstanceId(), Pair.of(instance.toString(), serviceInstance));
                    } else {
                        serviceInstance = old.getRight();
                        // 如果instance的内容改变了
                        if (!old.getLeft().equals(instance.toString())) {
                            serviceInstance.setModifyIndex(serviceInstance.getModifyIndex() + 1);
                            copyFromInstance(instance, serviceInstance);
                        }
                        map.put(key, old);
                    }
                }
                if (!instanceMap.isEmpty()) {
                    instanceMap.keySet().removeAll(map.keySet());
                    removed = new ArrayList<>(
                            instanceMap.values().stream().map(Pair::getRight).collect(Collectors.toList()));
                }
                this.instanceMap = map;
                serviceInfo.setServiceInstances(CollectionHelper
                        .immutableList(map.values().stream().map(Pair::getRight).collect(Collectors.toList())));
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

    private void copyFromInstance(Instance inst, ServiceInstance ret) {
        ret.setAddr(inst.getIp());
        ret.setPort(inst.getPort());
        ret.setServiceName(inst.getServiceName());
        ret.setEphemeral(inst.isEphemeral());
        ret.setClusterName(inst.getClusterName());
        ret.setEnabled(inst.isEnabled());
        ret.setInstanceId(inst.getInstanceId());
        ret.setHealthy(inst.isHealthy());
        ret.setWeight((int) inst.getWeight());
        int pos = inst.getServiceName().indexOf("@@");
        ret.setGroupName(inst.getServiceName().substring(0, pos));
        ret.setServiceName(inst.getServiceName().substring(pos + 2));

        if (inst.getMetadata() != null) {
            ret.setMetadata(new HashMap<>(inst.getMetadata()));

            Set<String> tags = ConvertHelper.toCsvSet(inst.getMetadata().get(ApiConstants.META_KEY_TAGS));
            ret.setTags(tags);
        }
    }
}
