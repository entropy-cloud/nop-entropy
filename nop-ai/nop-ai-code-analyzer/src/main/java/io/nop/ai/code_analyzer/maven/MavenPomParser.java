package io.nop.ai.code_analyzer.maven;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenPomParser {

    public MavenDependencyNode loadDependencyNodeFromPom(File file) {
        // 将File转换为Nop的IResource
        IResource resource = new FileResource(file);

        // 使用Nop的XDSL解析器解析pom.xml文件
        XNode pomNode = XNodeParser.instance().parseFromResource(resource);

        // 解析依赖项
        List<MavenDependency> dependencies = parseDependencies(pomNode);

        // 构建依赖树（这里简化处理，实际可能需要更复杂的依赖解析逻辑）
        return buildDependencyTree(dependencies, pomNode);
    }

    private List<MavenDependency> parseDependencies(XNode pomNode) {
        List<MavenDependency> dependencies = new ArrayList<>();

        // 查找dependencies节点
        XNode dependenciesNode = pomNode.childByTag("dependencies");
        if (dependenciesNode == null) {
            return dependencies;
        }

        // 遍历所有dependency节点
        for (XNode dependencyNode : dependenciesNode.getChildren()) {
            if ("dependency".equals(dependencyNode.getTagName())) {
                MavenDependency dependency = parseDependency(dependencyNode);
                if (dependency != null) {
                    dependencies.add(dependency);
                }
            }
        }

        return dependencies;
    }

    private MavenDependency parseDependency(XNode dependencyNode) {
        String groupId = getChildText(dependencyNode, "groupId");
        String artifactId = getChildText(dependencyNode, "artifactId");
        String version = getChildText(dependencyNode, "version");
        String type = getChildText(dependencyNode, "type");
        String scope = getChildText(dependencyNode, "scope");
        String classifier = getChildText(dependencyNode, "classifier");

        // 提供默认值
        if (type == null) {
            type = "jar"; // Maven默认类型
        }
        if (scope == null) {
            scope = "compile"; // Maven默认scope
        }

        return new MavenDependency(groupId, artifactId, type, version, scope, classifier);
    }

    private String getChildText(XNode parent, String childTagName) {
        return parent.childContentText(childTagName);
    }

    private MavenDependencyNode buildDependencyTree(List<MavenDependency> dependencies, XNode pomNode) {
        // 这里简化实现，实际项目中可能需要：
        // 1. 解析dependencyManagement
        // 2. 处理父子pom继承关系
        // 3. 解析properties中的变量
        // 4. 处理import scope的依赖管理

        // 创建根节点（代表当前项目）
        MavenDependency projectDependency = parseProjectInfo(pomNode);
        MavenDependencyNode root = new MavenDependencyNode(projectDependency);

        // 将所有依赖作为直接子节点添加（简化处理）
        for (MavenDependency dependency : dependencies) {
            // 跳过test和provided等非编译期依赖（可根据需要调整）
            if (!"test".equals(dependency.getScope()) && !"provided".equals(dependency.getScope())) {
                root.addChild(new MavenDependencyNode(dependency));
            }
        }

        return root;
    }

    private MavenDependency parseProjectInfo(XNode pomNode) {
        String groupId = getChildText(pomNode, "groupId");
        String artifactId = getChildText(pomNode, "artifactId");
        String version = getChildText(pomNode, "version");

        // 如果project节点下没有groupId，可能继承自parent
        if (groupId == null) {
            XNode parentNode = pomNode.childByTag("parent");
            if (parentNode != null) {
                groupId = getChildText(parentNode, "groupId");
            }
        }

        return new MavenDependency(
                groupId,
                artifactId,
                "pom", // 项目本身类型为pom
                version,
                "compile",
                null
        );
    }

    // 如果需要更复杂的依赖解析，可以添加以下方法
    private Map<String, String> parseProperties(XNode pomNode) {
        Map<String, String> properties = new HashMap<>();
        XNode propertiesNode = pomNode.childByTag("properties");
        if (propertiesNode != null) {
            for (XNode propNode : propertiesNode.getChildren()) {
                properties.put(propNode.getTagName(), propNode.contentText());
            }
        }
        return properties;
    }

    private List<MavenDependency> parseDependencyManagement(XNode pomNode) {
        List<MavenDependency> managedDependencies = new ArrayList<>();

        XNode dependencyManagementNode = pomNode.childByTag("dependencyManagement");
        if (dependencyManagementNode != null) {
            XNode dependenciesNode = dependencyManagementNode.childByTag("dependencies");
            if (dependenciesNode != null) {
                for (XNode dependencyNode : dependenciesNode.getChildren()) {
                    if ("dependency".equals(dependencyNode.getTagName())) {
                        MavenDependency dependency = parseDependency(dependencyNode);
                        if (dependency != null) {
                            managedDependencies.add(dependency);
                        }
                    }
                }
            }
        }

        return managedDependencies;
    }
}