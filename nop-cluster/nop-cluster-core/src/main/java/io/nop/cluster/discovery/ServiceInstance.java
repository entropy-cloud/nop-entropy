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
public class ServiceInstance implements Serializable {
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
     * 可用于区分不同数据中心的节点
     */
    private String groupName;

    /**
     * cluster information of instance
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
