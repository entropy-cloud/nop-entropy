/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CharSequenceHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.commons.CommonErrors.ARG_KEY;
import static io.nop.commons.CommonErrors.ERR_TEXT_TRIE_KEY_ALREADY_EXISTS;

public class StringTrie<T> {
    private enum AddType {
        // 一个key仅允许一个value, 如果key出现重复, 则抛出异常
        UNIQUE,

        // 保留key对应的第一个value, 再加入的value被忽略
        KEEP_FIRST,

        // 保留key对应的最后一个value, 后加入的value会取代此前加入的value
        KEEP_LAST
    }

    final List<TrieNode<T>> roots = new ArrayList<>();

    public static class TrieNode<T> {
        char[] data;
        List<TrieNode<T>> children;
        T object;
        final int length;

        public TrieNode(char[] data, int length, T object) {
            this.data = data;
            this.length = length;
            this.object = object;
        }

        /**
         * 匹配本节点时对应的总的前缀长度，可以用于和key来比较来确定是前缀匹配还是整体匹配
         *
         * @return
         */
        public int getKeyLength() {
            return length;
        }

        public T getObject() {
            return object;
        }

        void addChild(TrieNode<T> child) {
            if (children == null)
                children = new ArrayList<TrieNode<T>>();
            children.add(child);
        }

        void dump(Appendable out, int indent) throws IOException {
            for (int i = 0; i < indent; i++) {
                out.append("  ");
            }
            for (int i = 0, n = data.length; i < n; i++) {
                out.append(data[i]);
            }
            T obj = object;
            if (obj != null) {
                out.append(':');
                out.append(obj.toString());
            }
            out.append("\n");
            if (children != null) {
                for (TrieNode<T> child : children) {
                    child.dump(out, indent + 1);
                }
            }
        }
    }

    public void add(CharSequence str, T value) {
        add(str, value, AddType.UNIQUE);
    }

    public void put(CharSequence str, T value) {
        add(str, value, AddType.KEEP_LAST);
    }

    public void putIfAbsent(CharSequence str, T value) {
        add(str, value, AddType.KEEP_FIRST);
    }

    void add(CharSequence str, T value, AddType addType) {
        addToList(null, roots, str, 0, value, addType);
    }

    public T find(String str) {
        TrieNode<T> node = findNode(str, false);
        return node == null ? null : node.getObject();
    }

    public T findWithPrefix(String str) {
        TrieNode<T> node = findNode(str, true);
        return node == null ? null : node.getObject();
    }

    protected TrieNode<T> findNode(String str, boolean onlyPrefix) {
        return findInList(roots, str, 0, onlyPrefix);
    }

    public boolean isEmpty() {
        return roots.isEmpty();
    }

    TrieNode<T> findInList(List<TrieNode<T>> list, String str, int startPos, boolean onlyPrefix) {
        char c = str.charAt(startPos);
        if (list == null)
            return null;
        int i, n = list.size();
        for (i = 0; i < n; i++) {
            TrieNode<T> node = list.get(i);
            char[] data = node.data;
            if (data[0] == c) {
                int keyLen = node.getKeyLength();
                if (keyLen > str.length()) {
                    return null;
                }

                if (!CharSequenceHelper.matchChars(str, startPos + 1, keyLen, data, 1))
                    return null;

                if (keyLen == str.length()) {
                    return node;
                } else {
                    TrieNode<T> find = findInList(node.children, str, keyLen, onlyPrefix);
                    if (onlyPrefix && find == null)
                        find = node;
                    return find;
                }
            } else if (data[0] > c) {
                break;
            }
        }
        return null;
    }

    public void dump(Appendable out) {
        try {
            out.append("trie=\n");
            for (TrieNode<T> root : roots) {
                root.dump(out, 0);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    void addToList(TrieNode<T> parent, List<TrieNode<T>> list, CharSequence str, int startPos, T value,
                   AddType addType) {
        int i, n = list.size();
        char firstChar = str.charAt(startPos);
        int strLen = str.length();
        for (i = 0; i < n; i++) {
            TrieNode<T> node = list.get(i);
            if (node.data[0] == firstChar) {
                int keyLen = node.length;
                int m = Math.min(keyLen, strLen) - startPos;
                int j;
                for (j = 1; j < m; j++) {
                    if (node.data[j] != str.charAt(j + startPos)) {
                        break;
                    }
                }
                if (j < m || keyLen > strLen) {
                    // 这里需要把节点拆分
                    char[] pre = new char[j];
                    System.arraycopy(node.data, 0, pre, 0, pre.length);
                    char[] post = new char[node.data.length - j];
                    System.arraycopy(node.data, j, post, 0, post.length);
                    node.data = post;
                    TrieNode<T> preNode;

                    int pos = startPos + j;

                    if (j < m) {
                        preNode = new TrieNode<T>(pre, pos, null);
                        char[] postStr = new char[strLen - pos];
                        CharSequenceHelper.getChars(str, pos, strLen, postStr, 0);

                        // 如果是在中间不匹配，则需要插入一个前缀节点，并产生一个新节点
                        TrieNode<T> newNode = new TrieNode<T>(postStr, strLen, value);

                        if (newNode.data[0] > node.data[0]) {
                            preNode.addChild(node);
                            preNode.addChild(newNode);
                        } else {
                            preNode.addChild(newNode);
                            preNode.addChild(node);
                        }
                    } else {
                        // 如果整个str都已经匹配，则直接拆分出一个前缀节点，把值插入到前缀节点中即可
                        preNode = new TrieNode<T>(pre, pos, value);
                        preNode.addChild(node);
                    }

                    list.set(i, preNode);
                    return;
                }
                if (keyLen < strLen) {
                    if (node.children == null)
                        node.children = new ArrayList<>();
                    this.addToList(node, node.children, str, keyLen, value, addType);
                    return;
                } else if (keyLen == strLen) {
                    switch (addType) {
                        case KEEP_FIRST:
                            break;
                        case KEEP_LAST:
                            node.object = value;
                            break;
                        case UNIQUE:
                            if (node.object == null) {
                                node.object = value;
                                break;
                            }
                        default:
                            throw new NopException(ERR_TEXT_TRIE_KEY_ALREADY_EXISTS).param(ARG_KEY, str).param(ARG_VALUE,
                                    value);
                    }
                    return;
                }
            } else if (node.data[0] > firstChar) {
                // child 是按照data[0]的顺序排列的
                char[] data = CharSequenceHelper.getChars(str, startPos, strLen);
                TrieNode<T> newNode = new TrieNode<T>(data, strLen, value);
                list.add(i, newNode);
                return;
            }
        }
        char[] data = CharSequenceHelper.getChars(str, startPos, strLen);
        TrieNode<T> newNode = new TrieNode<T>(data, strLen, value);
        list.add(newNode);
    }
}