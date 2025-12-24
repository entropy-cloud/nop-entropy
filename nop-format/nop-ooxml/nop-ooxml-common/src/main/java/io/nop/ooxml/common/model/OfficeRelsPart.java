/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common.model;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.constants.TargetMode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfficeRelsPart implements Serializable, IOfficePackagePart {
    private static final long serialVersionUID = 2052177043359756008L;

    private final String path;

    private final Map<String, OfficeRelationship> relationships = new HashMap<>();
    private final Map<String, List<OfficeRelationship>> relationshipsByType = new HashMap<>();

    private int nextId = 1;

    public OfficeRelsPart(String path) {
        this.path = path;
    }

    public OfficeRelationship getRelationship(String id) {
        return relationships.get(id);
    }

    public void addRelationship(OfficeRelationship relationship) {
        relationships.put(relationship.getId(), relationship);
        List<OfficeRelationship> list = relationshipsByType.computeIfAbsent(relationship.getType(),
                k -> new ArrayList<>());
        list.add(relationship);
    }

    public void addRelationship(String id, String type, String target, TargetMode targetMode) {
        this.addRelationship(new OfficeRelationship(null, id, type, target, targetMode));
    }

    public String addRelationship(String type, String target, TargetMode targetMode) {
        String id = newId();
        addRelationship(id, type, target, targetMode);
        return id;
    }

    public void removeRelationshipById(String id) {
        OfficeRelationship rel = relationships.remove(id);
        if (rel != null) {
            List<OfficeRelationship> list = relationshipsByType.get(rel.getType());
            if (list != null)
                list.remove(rel);
        }
    }

    public void removeRelationshipByType(String type) {
        List<OfficeRelationship> list = relationshipsByType.get(type);
        if (list != null) {
            for (OfficeRelationship rel : list) {
                relationships.remove(rel.getId());
            }
            list.clear();
        }
    }

    public void removeRelationshipByTarget(String target) {
        OfficeRelationship rel = getRelationshipByTarget(target);
        if (rel != null) {
            relationships.remove(rel.getId(), rel);
            List<OfficeRelationship> list = relationshipsByType.get(rel.getType());
            if (list != null) {
                list.remove(rel);
            }
        }
    }

    public OfficeRelationship getRelationshipByTarget(String target) {
        for (OfficeRelationship rel : relationships.values()) {
            if (target.equals(rel.getTarget())) {
                return rel;
            }
        }
        return null;
    }

    public List<OfficeRelationship> getRelationshipsByType(String type) {
        return relationshipsByType.get(type);
    }

    public OfficeRelationship getRelationshipByType(String type) {
        List<OfficeRelationship> list = relationshipsByType.get(type);
        if (list == null || list.isEmpty())
            return null;
        return list.get(0);
    }

    public List<OfficeRelationship> getImages() {
        return getRelationshipsByType(OfficeConstants.NS_IMAGE);
    }

    public OfficeRelationship addImage(String target) {
        String id = newId();
        OfficeRelationship rel = new OfficeRelationship(null, id, OfficeConstants.NS_IMAGE, target, null);
        addRelationship(rel);
        return rel;
    }

    public OfficeRelationship addChart(String target) {
        String id = newId();
        OfficeRelationship rel = new OfficeRelationship(null, id, OfficeConstants.NS_CHART, target, null);
        addRelationship(rel);
        return rel;
    }

    public String newId() {
        String id;
        do {
            id = "rId" + nextId++;
        } while (relationships.containsKey(id));
        return id;
    }

    public String getTarget(String rId) {
        OfficeRelationship rel = getRelationship(rId);
        if (rel == null)
            return null;
        return rel.getTarget(); // 返回如 "media/image1.png"
    }

    public static OfficeRelsPart parse(String path, IResource resource) {
        XNode node = XNodeParser.instance().parseFromResource(resource);
        OfficeRelsPart file = new OfficeRelsPart(path);
        for (XNode child : node.getChildren()) {
            OfficeRelationship rel = OfficeRelationship.parse(child);
            file.addRelationship(rel);
        }
        return file;
    }

    public OfficeRelsPart cloneInstance() {
        OfficeRelsPart ret = new OfficeRelsPart(path);
        ret.relationships.putAll(relationships);
        return ret;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public XNode loadXml() {
        XNode node = XNode.make("Relationships");
        node.setAttr("xmlns", OfficeConstants.NS_RELATIONSHIPS);

        for (OfficeRelationship rel : relationships.values()) {
            node.appendChild(rel.toNode());
        }
        return node;
    }

    @Override
    public XNode buildXml(IEvalContext context) {
        return loadXml();
    }
}