/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.utils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.markdown.ext.math.MathExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-09
 */
public class MarkdownHelper {
    private static final Parser parser = Parser.builder()
                                               .extensions(Arrays.asList(TablesExtension.create(),
                                                                         MathExtension.create()))
                                               .build();
    private static final Renderer htmlRenderer = HtmlRenderer.builder()
                                                             .escapeHtml(true)
                                                             .sanitizeUrls(true)
                                                             .extensions(Arrays.asList(TablesExtension.create(),
                                                                                       MathExtension.create(),
                                                                                       //
                                                                                       (HtmlRenderer.HtmlRendererExtension) rendererBuilder -> {
                                                                                           rendererBuilder.nodeRendererFactory(
                                                                                                   SelectiveLinkRenderer::new);
                                                                                       }
                                                                                       //
                                                             ))
                                                             .build();

    /** 将 markdown 文本渲染为 html 代码：在 markdown 中的 html 将被转义 */
    public static String renderHtml(String text) {
        Node document = parser.parse(text);
        return htmlRenderer.render(document);
    }

    /** 将链接展示出来，且图片也不直接显示，避免恶意攻击 */
    static class SelectiveLinkRenderer implements NodeRenderer {
        private final HtmlWriter html;
        private final HtmlNodeRendererContext context;

        SelectiveLinkRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
            this.context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Link.class, Image.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof Link) {
                renderExplicitLink((Link) node);
            } else if (node instanceof Image) {
                renderExplicitImage((Image) node);
            }
        }

        private void renderExplicitLink(Link node) {
            String href = this.context.encodeUrl(node.getDestination());
            String title = null;

            if (node.getFirstChild() instanceof Text) {
                title = ((Text) node.getFirstChild()).getLiteral();
            }
            if (title == null) {
                title = node.getTitle();
            }
            title = NopPluginBundle.message("xlang.doc.markdown.link-title", title != null ? " " + title : "");

            this.html.tag("a", Map.of("href", href), this.context.shouldSanitizeUrls());
            this.html.text(title + " ");
            this.html.tag("code");
            this.html.text(node.getDestination());
            this.html.tag("/code");
            this.html.tag("/a");
        }

        private void renderExplicitImage(Image node) {
            String href = this.context.encodeUrl(node.getDestination());
            String title = null;

            if (node.getFirstChild() instanceof Text) {
                title = ((Text) node.getFirstChild()).getLiteral();
            }
            if (title == null) {
                title = node.getTitle();
            }
            title = NopPluginBundle.message("xlang.doc.markdown.image-title", title != null ? " " + title : "");

            this.html.tag("a", Map.of("href", href), this.context.shouldSanitizeUrls());
            this.html.text(title + " ");
            this.html.tag("code");
            this.html.text(node.getDestination());
            this.html.tag("/code");
            this.html.tag("/a");
        }
    }
}
