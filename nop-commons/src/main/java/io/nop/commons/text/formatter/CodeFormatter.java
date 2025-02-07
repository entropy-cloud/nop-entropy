package io.nop.commons.text.formatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 来自于文章 <a href="https://yorickpeterse.com/articles/how-to-write-a-code-formatter/">How to write a code formatter</a>
 */
public class CodeFormatter {
    // The character(s) to use for indenting code
    private static final String INDENT = "  ";

    // The "Node" type is the foundation of our formatter
    public enum NodeType {
        GROUP, NODES, IF_WRAP, TEXT, UNICODE, SPACE_OR_LINE, LINE, INDENT
    }

    public static class Node {
        public final NodeType type;
        public Integer groupId;
        public List<Node> children;
        public String text;
        public int width;
        public Node ifWrapNode;
        public Node elseWrapNode;

        public Node(NodeType type) {
            this.type = type;
        }

        public static Node group(int id, List<Node> nodes) {
            Node node = new Node(NodeType.GROUP);
            node.groupId = id;
            node.children = nodes;
            return node;
        }

        public static Node nodes(List<Node> nodes) {
            Node node = new Node(NodeType.NODES);
            node.children = nodes;
            return node;
        }

        public static Node ifWrap(int id, Node node, Node elseNode) {
            Node ifWrap = new Node(NodeType.IF_WRAP);
            ifWrap.groupId = id;
            ifWrap.ifWrapNode = node;
            ifWrap.elseWrapNode = elseNode;
            return ifWrap;
        }

        public static Node text(String text) {
            Node node = new Node(NodeType.TEXT);
            node.text = text;
            return node;
        }

        public static Node unicode(String value) {
            Node node = new Node(NodeType.UNICODE);
            node.text = value;
            node.width = value.codePointCount(0, value.length());
            return node;
        }

        public static Node spaceOrLine() {
            return new Node(NodeType.SPACE_OR_LINE);
        }

        public static Node line() {
            return new Node(NodeType.LINE);
        }

        public static Node indent(List<Node> nodes) {
            Node node = new Node(NodeType.INDENT);
            node.children = nodes;
            return node;
        }

        public int width(Set<Integer> wrapped) {
            if (type == NodeType.GROUP || type == NodeType.NODES || type == NodeType.INDENT) {
                return children.stream().mapToInt(child -> child.width(wrapped)).sum();
            } else if (type == NodeType.IF_WRAP) {
                return wrapped.contains(groupId) ? ifWrapNode.width(wrapped) : elseWrapNode.width(wrapped);
            } else if (type == NodeType.TEXT) {
                return text.length();
            } else if (type == NodeType.UNICODE) {
                return width;
            } else if (type == NodeType.SPACE_OR_LINE) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public enum Wrap {
        ENABLE, DETECT;

        public boolean isEnable() {
            return this == ENABLE;
        }
    }

    public static class Generator {
        private final StringBuilder buffer = new StringBuilder();
        private int indent = 0;
        private int size = 0;
        private final int max;
        private final Set<Integer> wrapped = new HashSet<>();

        public Generator(int max) {
            this.max = max;
        }

        public void generate(Node node) {
            generateNode(node, Wrap.DETECT);
        }

        private void generateNode(Node node, Wrap wrap) {
            switch (node.type) {
                case GROUP:
                case NODES:
                case INDENT:
                    for (Node child : node.children) {
                        generateNode(child, (node.type == NodeType.GROUP) ? calculateWrap(node, wrap) : wrap);
                    }
                    break;
                case IF_WRAP:
                    if (wrapped.contains(node.groupId)) {
                        generateNode(node.ifWrapNode, Wrap.ENABLE);
                    } else {
                        generateNode(node.elseWrapNode, wrap);
                    }
                    break;
                case TEXT:
                    appendText(node.text, node.text.length());
                    break;
                case UNICODE:
                    appendText(node.text, node.width);
                    break;
                case LINE:
                    if (wrap.isEnable()) {
                        newLine();
                    }
                    break;
                case SPACE_OR_LINE:
                    if (wrap.isEnable()) {
                        newLine();
                    } else {
                        appendText(" ", 1);
                    }
                    break;
            }
        }

        private Wrap calculateWrap(Node node, Wrap parentWrap) {
            int width = node.children.stream().mapToInt(child -> child.width(wrapped)).sum();
            if (size + width > max) {
                wrapped.add(node.groupId);
                return Wrap.ENABLE;
            } else {
                return parentWrap;
            }
        }

        private void appendText(String value, int length) {
            size += length;
            buffer.append(value);
        }

        private void newLine() {
            buffer.append("\n");
            for (int i = 0; i < indent; i++) {
                buffer.append(INDENT);
            }
            size = INDENT.length() * indent;
        }

        @Override
        public String toString() {
            return buffer.toString();
        }
    }

    public static class Builder {
        private int id = 0;

        public Node call(String name, List<Node> arguments) {
            id++;
            if (arguments.isEmpty()) {
                return Node.group(id, List.of(Node.text(name), Node.text("()")));
            }

            int max = arguments.size() - 1;
            List<Node> argumentNodes = new ArrayList<>();
            for (int i = 0; i < arguments.size(); i++) {
                Node argument = arguments.get(i);
                if (i < max) {
                    argumentNodes.add(Node.nodes(List.of(argument, Node.text(","), Node.spaceOrLine())));
                } else {
                    argumentNodes.add(Node.nodes(List.of(argument, Node.ifWrap(id, Node.text(","), Node.text("")))));
                }
            }

            List<Node> groupNodes = new ArrayList<>();
            groupNodes.add(Node.text(name));
            groupNodes.add(Node.group(id, List.of(
                    Node.text("("),
                    Node.line(),
                    Node.indent(argumentNodes),
                    Node.line(),
                    Node.text(")")
            )));

            return Node.group(id, groupNodes);
        }

        public Node string(String value) {
            id++;
            return Node.group(id, List.of(Node.text("\""), Node.unicode(value), Node.text("\"")));
        }
    }

    public static void main(String[] args) {
        int maxLineLength = args.length > 0 ? Integer.parseInt(args[0]) : 80;
        if (maxLineLength < 0) {
            maxLineLength = 80;
        }

        Generator generator = new Generator(maxLineLength);
        Builder builder = new Builder();

        Node root = builder.call(
                "foo",
                List.of(
                        Node.text("1000000000000000000000000000000"),
                        builder.call(
                                "bar",
                                List.of(
                                        Node.text("2000000000000000000000000000000"),
                                        builder.string("this is a string"),
                                        builder.call("without_arguments", new ArrayList<>())
                                )
                        )
                )
        );

        generator.generate(root);
        System.out.println(generator);
    }
}