package io.nop.core.lang.xml.split;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XPathProvider;
import io.nop.core.lang.xml.handler.CollectXNodeHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个用于将大型 XML 节点分割成多个较小节点的工具类。
 *
 * <p>该类通过在指定的 XPath 路径下进行分割，并估算每个分片的大小， 来确保生成的分片节点不会超过预设的大小限制。这对于处理、传输或 存储大型 XML 数据非常有用。
 */
public class XNodeSplitter {

    /**
     * 将一个大的 XML 节点在指定的 XPath 路径下分割成多个较小的节点列表。
     *
     * <p>该方法会遍历节点，并尝试在匹配 {@code splitPath} 的位置进行分割，确保每个分片节点
     * （包括其标签和所有子节点）的估算 XML 字符串大小不超过给定的 {@code maxSize}。
     *
     * <p><b>注意：</b>如果单个子节点（或其子树）的大小本身就超过了 {@code maxSize}，
     * 则它将独立构成一个分片，导致该分片的大小超出限制。原始节点本身不会被分割，
     * 其标签和属性会作为每个分片的外壳。
     *
     * @param node      待分割的 XML 节点，不能为 null。
     * @param maxSize   每个分片的最大估算大小（单位：字符），必须为正数。
     * @param splitPath 用于定位分割点的 XPath 表达式。如果为空或无效，则不进行分割。
     * @return 包含分割后节点的列表。如果输入节点没有子节点，则返回一个仅包含原始节点副本的列表。
     * @throws IllegalArgumentException 如果 {@code node} 为 null 或 {@code maxSize} 不是正数。
     */
    public static List<XNode> split(XNode node, int maxSize, String splitPath) {
        return new SplitTool().split(node, maxSize, splitPath);
    }

    static class SplitTool {
        private List<XNode> resultNodes;
        private CollectXNodeHandler handler;
        private int accumulateSize;

        private Set<XNode> splittableParents;

        public List<XNode> split(XNode node, int maxSize, String splitPath) {
            Guard.notNull(node, "node");
            Guard.positiveInt(maxSize, "maxSize");

            this.resultNodes = new ArrayList<>();
            this.handler = new CollectXNodeHandler();
            this.accumulateSize = 0;
            this.splittableParents = null;

            parseSplitPath(node, splitPath);

            processNode(node, maxSize);
            XNode root = handler.root();
            if (root != null && !resultNodes.contains(root)) {
                resultNodes.add(root);
            }
            return resultNodes;
        }

        void parseSplitPath(XNode node, String splitPath) {
            if (splitPath == null || splitPath.isEmpty())
                return;

            if (this.splittableParents == null)
                this.splittableParents = CollectionHelper.newIdentityHashSet();

            List<String> paths = StringHelper.split(splitPath, '|');
            for (String path : paths) {
                path = path.trim();
                if (path.isEmpty())
                    continue;
                IXSelector<XNode> selector = XPathProvider.instance().compile(path);
                Collection<?> selected = node.selectMany(selector);
                if (selected != null) {
                    for (Object item : selected) {
                        if (item instanceof XNode)
                            splittableParents.add((XNode) item);
                    }
                }
            }
        }

        void processNode(XNode node, int maxSize) {
            if (node.getComment() != null) {
                handler.comment(node.getComment());
                accumulateSize += calcCommentSize(node.getComment());
            }
            if (!node.hasBody()) {
                handler.simpleNode(node.getLocation(), node.getTagName(), node.attrValueLocs());
                accumulateSize += calcNodeSize(node);
                return;
            }
            this.handler.beginNode(node.getLocation(), node.getTagName(), node.attrValueLocs());
            accumulateSize += calcNodeSize(node);
            if (node.hasChild()) {
                if (isSplittableParent(node)) {
                    for (XNode child : node.getChildren()) {
                        if (accumulateSize > maxSize) {
                            flushSplit(child);
                        }
                        processNode(child, maxSize);
                    }
                } else {
                    for (XNode child : node.getChildren()) {
                        processNode(child, maxSize);
                    }
                }
            } else {
                this.handler.value(node.content().getLocation(), node.content());
                accumulateSize += calcContentSize(node.content());
            }
            this.handler.endNode(node.getTagName());
        }

        private boolean isSplittableParent(XNode node) {
            return splittableParents == null || splittableParents.contains(node);
        }

        /**
         * 完成当前分片的构建，将其存入结果列表，并重置构建器以开始下一个分片。
         * <p>
         * 此方法会向上回溯并关闭所有父节点，生成一个完整的 XML 树。然后，
         * 它会重新打开这些父节点，以便继续构建下一个分片。
         */
        private void flushSplit(XNode node) {
            List<XNode> parents = new ArrayList<>();

            XNode parent = node.getParent();
            while (parent != null) {
                parents.add(parent);
                handler.endNode(parent.getTagName());
                parent = parent.getParent();
            }

            resultNodes.add(handler.root());
            handler.reset();
            accumulateSize = 0;

            for (int i = 0, n = parents.size(); i < n; i++) {
                XNode p = parents.get(n - i - 1);
                handler.beginNode(p.getLocation(), p.getTagName(), p.attrValueLocs());
                accumulateSize += calcNodeSize(p);
            }
        }
    }

    static int calcNodeSize(XNode node) {
        int size = 0;
        size += calcTagSize(node.getTagName(), !node.hasBody());
        size += calcAttrsSize(node.attrValueLocs());
        size += calcContentSize(node.content());
        return size;
    }

    static int calcFullNodeSize(XNode node) {
        return calcNodeSize(node) + calcDescendantsSize(node);
    }

    static int calcCommentSize(String comment) {
        if (comment == null || comment.isEmpty())
            return 0;
        return comment.length() + "<!-- -->".length();
    }

    static int calcTagSize(String tagName, boolean simple) {
        int size = tagName.length();
        if (simple) {
            size += "</>".length();
        } else {
            size += tagName.length() + "<></>".length();
        }
        return size;
    }

    static int calcAttrsSize(Map<String, ValueWithLocation> attrs) {
        int size = 0;
        for (Map.Entry<String, ValueWithLocation> entry : attrs.entrySet()) {
            String name = entry.getKey();
            String value = StringHelper.escapeXmlAttr(entry.getValue().asString(""));
            size += 1 + name.length() + 1 + value.length() + 2;
        }
        return size;
    }

    static int calcContentSize(ValueWithLocation content) {
        if (content.isEmpty())
            return 0;

        String str = content.asString("");
        if (content.isCDataText()) {
            return "<![CDATA[]]>".length() + str.length();
        } else {
            return StringHelper.escapeXmlValue(str).length();
        }
    }

    static int calcDescendantsSize(XNode node) {
        int size = 0;
        for (XNode child : node.getChildren()) {
            size += calcNodeSize(child) + calcDescendantsSize(child);
        }
        return size;
    }
}
