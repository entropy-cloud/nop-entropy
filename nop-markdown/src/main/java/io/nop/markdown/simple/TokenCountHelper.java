package io.nop.markdown.simple;

import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.model.MarkdownTextOptions;

import java.util.function.Function;

public class TokenCountHelper {
    public static int initTokenCount(MarkdownSection section,
                                     Function<String, Integer> tokenCounter, MarkdownTextOptions options) {
        if (section.getTokenCount() <= 0) {
            int count = getMainTokenCount(section, tokenCounter, options);
            if (section.getChildren() != null) {
                for (MarkdownSection child : section.getChildren()) {
                    count += initTokenCount(child, tokenCounter, options);
                }
            }
            section.setTokenCount(count);
        }
        return section.getTokenCount();
    }

    static int getMainTokenCount(MarkdownSection section, Function<String, Integer> tokenCounter, MarkdownTextOptions options) {
        StringBuilder sb = new StringBuilder();
        section.buildMainText(sb, options);
        return tokenCounter.apply(sb.toString());
    }
}
