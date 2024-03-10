/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.tree.ITreeChildrenStructure;
import io.nop.core.model.tree.TreeVisitors;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.InMemoryDirResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_DIR;

/**
 * 在内存中的资源树节点。每个节点对应一个IResource对象，同时它还负责维护资源对象之间的父子关系。 例如从Zip文件解析后会在内存中维护一个对应的资源树
 */
public class ResourceTreeNode implements ITreeChildrenStructure {
    static final Logger LOG = LoggerFactory.getLogger(ResourceTreeNode.class);

    private final String name;
    private Map<String, ResourceTreeNode> children = null;
    private final IResource resource;
    private ResourceTreeNode parent;

    public ResourceTreeNode(String name, IResource resource) {
        this.name = Guard.notNull(name, "resourceName");
        this.resource = Guard.notNull(resource, "resource");
        if (name.length() > 0)
            Guard.checkArgument(StringHelper.isValidFileName(name), "resourceName is not valid file name", name);
    }

    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public void merge(ResourceTreeNode node) {
        if (children == null) {
            children = new TreeMap<>();
        }

        if (node.children != null) {
            node.children.forEach((childName, child) -> {
                ResourceTreeNode c = children.putIfAbsent(childName, child);
                if (c != null) {
                    if (c.isLeaf() || child.isLeaf()) {
                        children.put(childName, child);
                    } else {
                        c.merge(child);
                    }
                }
            });
        }
    }

    public String getPath() {
        return resource.getPath();
    }

    public String getName() {
        return name;
    }

    public IResource getResource() {
        return resource;
    }

    public ResourceTreeNode getParent() {
        return parent;
    }

    public void setParent(ResourceTreeNode parent) {
        this.parent = parent;
    }

    public boolean hasChild(String name) {
        if (children == null)
            return false;
        return children.containsKey(name);
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    public void addChild(String name, IResource resource) {

        ResourceTreeNode child = new ResourceTreeNode(name, resource);
        addChild(child);
    }

    public void addChild(ResourceTreeNode child) {
        Guard.checkArgument(child.getParent() == null, "child node can not be added to multiple parent");

        if (children == null) {
            children = new TreeMap<>();
            children.put(child.getName(), child);
        } else {
            ResourceTreeNode old = children.put(child.getName(), child);
            if (old != null) {
                LOG.info("nop.core.resource.replace-child:newResource={},oldResource={}", child.getResource(),
                        old.getResource());
            }
        }
    }

    public List<IResource> getChildResources() {
        if (children == null)
            return null;

        List<IResource> ret = new ArrayList<>(children.size());
        for (ResourceTreeNode child : children.values()) {
            ret.add(child.getResource());
        }
        return ret;
    }

    public Collection<ResourceTreeNode> getChildren() {
        if (children == null)
            return null;
        return children.values();
    }

    public void addNode(String path, IResource resource) {
        if (path.equals("/"))
            return;

        String parentPath = ResourceHelper.getParentPath(path);
        String name = ResourceHelper.getName(path);
        ResourceTreeNode node = mkdirs(parentPath);
        if (!StringHelper.isEmpty(name))
            node.addChild(name, resource);
    }

    public boolean removeNode(String path) {
        if (path.equals("/"))
            return false;

        if (path.startsWith("/"))
            path = path.substring(1);

        int pos = path.indexOf('/');
        if (pos < 0) {
            if (children != null)
                return children.remove(path) != null;
        } else {
            String parentPath = path.substring(0, pos);
            ResourceTreeNode child = _getNode(parentPath, 0);
            if (child != null)
                return child.removeNode(path.substring(pos + 1));
        }
        return false;
    }

    public boolean addNodeIfAbsent(String path, IResource resource) {
        // 根节点已经存在
        if (path.equals("/"))
            return false;

        String parentPath = ResourceHelper.getParentPath(path);
        String name = ResourceHelper.getName(path);
        ResourceTreeNode node = mkdirs(parentPath);
        ResourceTreeNode child = node.getChild(name);
        if (child == null) {
            node.addChild(name, resource);
            return true;
        }

        if (child.getResource().equals(resource))
            return true;

        LOG.info("nop.core.resource.ignore-resource-since-path-already-exists:path={},resource={},prev={}", path,
                resource, child.getResource());
        return false;
    }

    public ResourceTreeNode getNode(String path) {
        if (path.equals("/"))
            return this;

        return _getNode(path, path.startsWith("/") ? 1 : 0);
    }

    public ResourceTreeNode getChild(String name) {
        if (children == null)
            return null;
        return children.get(name);
    }

    ResourceTreeNode _getNode(String path, int pos) {
        if (pos >= path.length())
            return this;

        int pos2 = path.indexOf('/', pos);
        if (pos2 < 0) {
            ResourceTreeNode child = getChild(path.substring(pos));
            if (child == null) {
                LOG.trace("nop.core.resource.get-node-null:path={}", path);
            }
            return child;
        }

        String name = path.substring(pos, pos2);

        ResourceTreeNode child = getChild(name);
        if (child == null) {
            LOG.trace("nop.core.resource.get-node-null:path={}", path.substring(0, pos2));
            return null;
        }

        return child._getNode(path, pos2 + 1);
    }

    public ResourceTreeNode mkdir(String name) {
        ResourceTreeNode child = getChild(name);
        if (child != null) {
            if (!child.getResource().isDirectory())
                throw new NopException(ERR_RESOURCE_NOT_DIR).param(ARG_RESOURCE, child.getResource());
            return child;
        }
        String path = StringHelper.appendPath(getPath(), name);
        child = new ResourceTreeNode(name, new InMemoryDirResource(path));
        addChild(child);
        return child;
    }

    public ResourceTreeNode mkdirs(String path) {
        if (path.equals("/") || path.equals(""))
            return this;
        return _mkdirs(path, path.startsWith("/") ? 1 : 0);
    }

    ResourceTreeNode _mkdirs(String path, int pos) {
        if (pos >= path.length())
            return this;

        int pos2 = path.indexOf('/', pos);
        if (pos2 < 0) {
            return mkdir(path.substring(pos));
        }

        String name = path.substring(pos, pos2);

        ResourceTreeNode child = mkdir(name);
        return child._mkdirs(path, pos2 + 1);
    }

    public List<IResource> getAllResourcesWithSuffix(String suffix) {
        List<IResource> ret = new ArrayList<>();

        for (ResourceTreeNode node : TreeVisitors.depthFirstIterator(this, true)) {
            if (!node.hasChild()) {
                if (suffix == null || node.getName().endsWith(suffix)) {
                    ret.add(node.getResource());
                }
            }
        }
        return ret;
    }
}