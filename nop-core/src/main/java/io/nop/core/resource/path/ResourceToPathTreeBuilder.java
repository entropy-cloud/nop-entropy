package io.nop.core.resource.path;

import io.nop.core.model.tree.ITreeChildrenAdapter;
import io.nop.core.model.tree.ITreeVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.model.tree.TreeVisitors;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

public class ResourceToPathTreeBuilder implements ITreeVisitor<IResource> {

    private PathTreeNode rootNode;
    private PathTreeNode currentNode;

    @Override
    public TreeVisitResult beginNode(IResource resource) {
        // 创建对应的PathTreeNode
        PathTreeNode node = createPathTreeNode(resource);

        // 设置根节点（第一次调用时）
        if (rootNode == null) {
            rootNode = node;
        } else {
            // 将当前节点添加到父节点的children中
            if (currentNode != null) {
                currentNode.makeChildren().add(node);
            }
        }

        // 更新当前节点引用
        currentNode = node;

        return TreeVisitResult.CONTINUE;
    }

    @Override
    public TreeVisitResult endNode(IResource resource) {
        // 返回到父节点
        if (currentNode != null) {
            currentNode = currentNode.getParent();
        }
        return TreeVisitResult.CONTINUE;
    }

    private PathTreeNode createPathTreeNode(IResource resource) {
        String name = ResourceHelper.getName(resource.getPath());
        boolean isDirectory = resource.isDirectory();

        // 计算当前层级 - 这里简化处理，可能需要根据实际情况调整
        int level = currentNode != null ? currentNode.getLevel() + 1 : 0;

        return new PathTreeNode(name, level, currentNode, isDirectory);
    }

    public PathTreeNode getResult() {
        return rootNode;
    }

    // 辅助方法：从IResource构建PathTreeNode
    public static PathTreeNode buildFromResource(ITreeChildrenAdapter<IResource> loader, IResource rootResource) {
        ResourceToPathTreeBuilder builder = new ResourceToPathTreeBuilder();
        TreeVisitors.visitTree(loader, rootResource, builder);
        return builder.getResult();
    }
}