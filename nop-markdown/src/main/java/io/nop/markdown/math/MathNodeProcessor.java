package io.nop.markdown.math;

import org.commonmark.node.Node;
import org.commonmark.node.Nodes;
import org.commonmark.node.SourceSpans;
import org.commonmark.node.Text;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;

public class MathNodeProcessor implements DelimiterProcessor {
    @Override
    public char getOpeningCharacter() {
        return '$';
    }

    @Override
    public char getClosingCharacter() {
        return '$';
    }

    @Override
    public int getMinLength() {
        return 1;
    }

    @Override
    public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
        int usedDelimiters = 1;
        Node mathNode = new MathNode();
        SourceSpans sourceSpans = SourceSpans.empty();
        sourceSpans.addAllFrom(openingRun.getOpeners(usedDelimiters));

        Text opener = openingRun.getOpener();
        for (Node node : Nodes.between(opener, closingRun.getCloser())) {
            mathNode.appendChild(node);
            sourceSpans.addAll(node.getSourceSpans());
        }

        sourceSpans.addAllFrom(closingRun.getClosers(usedDelimiters));

        mathNode.setSourceSpans(sourceSpans.getSourceSpans());
        opener.insertAfter(mathNode);

        return usedDelimiters;
    }
}
