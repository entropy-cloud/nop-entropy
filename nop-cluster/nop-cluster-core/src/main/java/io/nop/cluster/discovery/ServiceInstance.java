/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.cluster.discovery;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// modify from Nacos Instance
@DataBean
public class ServiceInstance implements Serializable, Comparable<ServiceInstance> {
    public static final String META_ZONE = "zone";
    public static final String META_VERSION = "version";
    public static final String META_GROUP = "group";

    private String instanceId;

    private String addr;

    /**
     * instance port
     */
    private int port;

    /**
     * instance weight
     */
    private int weight = 100;

    /**
     * instance health status
     */
    private boolean healthy = true;

    /**
     * If instance is enabled to accept request
     */
    private boolean enabled = true;

    /**
     * If instance is ephemeral
     *
     * @since 1.0.0
     */
    private boolean ephemeral = true;

    /**
     * 服务分组名称（Group Name）
     *
     * <p>用于逻辑分组，区别于 Namespace（环境隔离）和 Cluster（物理分组）
     *
     * <h3>层级关系：</h3>
     * <pre>
     * Namespace (命名空间 - 环境隔离，如 dev/test/prod)
     *   └── Group (业务分组 - 逻辑隔离，如 order-group/payment-group)
     *       └── Service (服务)
     *           └── Cluster (机房分组 - 物理隔离，如 BJ-IDC/SH-IDC)
     *               └── Instance (实例)
     * </pre>
     *
     * <h3>与 ClusterName 的区别：</h3>
     * <ul>
     *   <li><b>GroupName</b>: 跨服务的逻辑分组，用于业务线隔离</li>
     *   <li><b>ClusterName</b>: 同一服务内的物理分组，用于机房/地域隔离</li>
     * </ul>
     *
     * <h3>典型使用场景：</h3>
     * <ul>
     *   <li>多项目共用 Nacos，用 Group 区分不同项目</li>
     *   <li>灰度发布，用 Group 区分 stable/canary 版本</li>
     *   <li>业务线隔离，如 order-group、payment-group、user-group</li>
     * </ul>
     *
     * <h3>服务调用规则：</h3>
     * <ul>
     *   <li>服务发现时，<b>必须</b>在同一个 Group 内才能互相发现</li>
     *   <li>跨 Group 调用需要显式指定目标 Group</li>
     *   <li>NacosNamingService 会校验 instance.groupName == 配置的 groupName</li>
     * </ul>
     */
    private String groupName;

    /**
     * 集群名称（Cluster Name）
     *
     * <p>同一服务下的实例分组，用于物理/地域隔离
     *
     * <h3>主要用途：</h3>
     * <ul>
     *   <li><b>机房隔离</b>: 将同一服务的实例按部署机房分组（如 BJ-IDC、SH-IDC）</li>
     *   <li><b>地域就近访问</b>: 优先调用同机房的实例，降低网络延迟</li>
     *   <li><b>容灾部署</b>: 主备机房、多活机房的场景</li>
     * </ul>
     *
     * <h3>与 GroupName 的区别：</h3>
     * <ul>
     *   <li><b>GroupName</b>: 跨服务的逻辑分组（业务维度）</li>
     *   <li><b>ClusterName</b>: 同一服务内的实例分组（地理维度）</li>
     * </ul>
     *
     * <h3>服务调用规则：</h3>
     * <ul>
     *   <li>服务发现时，<b>可以</b>发现同一 Group 内所有 Cluster 的实例</li>
     *   <li>负载均衡时，通过 {@link io.nop.cluster.chooser.filter.ZoneServiceInstanceFilter}
     *       优先选择同 zone/cluster 的实例</li>
     *   <li>不会强制隔离，只是优先级不同</li>
     * </ul>
     *
     * <h3>典型配置示例：</h3>
     * <pre>
     * # 北京机房实例
     * nop.cluster.name=BJ-IDC
     * nop.application.zone=beijing
     *
     * # 上海机房实例
     * nop.cluster.name=SH-IDC
     * nop.application.zone=shanghai
     * </pre>
     */
    private String clusterName;

    /**
     * Service information of instance。通常对应于application name
     */
    private String serviceName;

    private Set<String> tags;

    private Map<String, String> metadata;

    private List<Runnable> cleanupTasks;

    private String _host;

    /**
     * 本地维护的临时信息。从NamingService获取的信息中不包含这些属性
     */
    private Map<String, Object> attrs = new ConcurrentHashMap<>();

    private long modifyIndex;

    @Override
    public int compareTo(ServiceInstance o) {
        return instanceId.compareTo(o.getInstanceId());
    }

    public String toString() {
        return "ServiceInstance(instanceId=" + instanceId + ",service=" + serviceName + ",host=" + getHost()
                + ",cluster=" + clusterName + ",group=" + getGroupName() + ")";
    }

    @JsonIgnore
    public String getHost() {
        if (_host == null) {
            _host = getAddr() + ':' + getPort();
        }
        return _host;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * 除去group部分
     */
    public String getNormalizedServiceName() {
        int pos = serviceName.indexOf("@@");
        if (pos > 0)
            return serviceName.substring(pos + 2);
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getMetadata(String name) {
        if (metadata == null)
            return null;
        return metadata.get(name);
    }

    @JsonIgnore
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public Object getAttribute(String name) {
        return attrs.get(name);
    }

    public void setAttribute(String name, Object value) {
        attrs.put(name, value);
    }

    public synchronized void addCleanupTask(Runnable task) {
        if (cleanupTasks == null)
            cleanupTasks = new ArrayList<>();
        cleanupTasks.add(task);
    }

    @JsonIgnore
    public List<Runnable> getCleanupTasks() {
        if (cleanupTasks == null)
            return Collections.emptyList();
        return new ArrayList<>(cleanupTasks);
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public long getModifyIndex() {
        return modifyIndex;
    }

    public void setModifyIndex(long modifyIndex) {
        this.modifyIndex = modifyIndex;
    }
}
