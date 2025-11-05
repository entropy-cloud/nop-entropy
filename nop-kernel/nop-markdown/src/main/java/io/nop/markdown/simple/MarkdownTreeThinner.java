package io.nop.markdown.simple;

import io.nop.commons.util.CharSequenceHelper;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.model.MarkdownTextOptions;

import java.util.function.Function;

public class MarkdownTreeThinner {
    private final int minTokenThreshold;

    private final Function<String, Integer> tokenCounter;
    private MarkdownTextOptions options = new MarkdownTextOptions();

    public MarkdownTreeThinner(int minTokenThreshold, Function<String, Integer> tokenCounter) {
        this.minTokenThreshold = minTokenThreshold;
        this.tokenCounter = tokenCounter;
    }

    public MarkdownTreeThinner(int minTokenThreshold) {
        this(minTokenThreshold, String::length);
    }

    public void process(MarkdownSection section) {
        TokenCountHelper.initTokenCount(section, tokenCounter, options);
        doProcess(section);
    }

    void doProcess(MarkdownSection section) {
        if (!section.hasChild())
            return;

        if (section.getTokenCount() <= minTokenThreshold) {
            StringBuilder sb = new StringBuilder();
            if (section.getText() != null) {
                sb.append(section.getText());
            }
            if (!CharSequenceHelper.endsWith(sb, "\n\n"))
                sb.append("\n\n");

            section.getChildren().forEach(child -> {
                child.buildText(sb, options);
            });

            section.setText(sb.toString());
            section.setChildren(null);
        } else {
            section.getChildren().forEach(this::doProcess);
        }
    }
}
