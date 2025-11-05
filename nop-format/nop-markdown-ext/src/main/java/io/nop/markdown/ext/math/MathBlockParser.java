package io.nop.markdown.ext.math;

import org.commonmark.internal.util.Parsing;
import org.commonmark.node.Block;
import org.commonmark.parser.SourceLine;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;
import org.commonmark.text.Characters;

public class MathBlockParser extends AbstractBlockParser {

    private final MathBlock block = new MathBlock();

    private boolean firstLine = true;
    private StringBuilder otherLines = new StringBuilder();

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        int nextNonSpace = state.getNextNonSpaceIndex();
        int newIndex = state.getIndex();
        CharSequence line = state.getLine().getContent();
        if (state.getIndent() < Parsing.CODE_BLOCK_INDENT && nextNonSpace < line.length() && tryClosing(line, nextNonSpace)) {
            // closing fence - we're at end of line, so we can finalize now
            return BlockContinue.finished();
        }
        return BlockContinue.atIndex(newIndex);
    }

    @Override
    public void addLine(SourceLine line) {
        if (firstLine) {
            firstLine = false;
            return;
        }
        otherLines.append(line.getContent());
        otherLines.append('\n');
    }

    @Override
    public void closeBlock() {
        block.setLiteral(otherLines.toString());
    }

    public static class Factory extends AbstractBlockParserFactory {

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            int indent = state.getIndent();
            if (indent >= Parsing.CODE_BLOCK_INDENT) {
                return BlockStart.none();
            }

            int nextNonSpace = state.getNextNonSpaceIndex();
            MathBlockParser blockParser = checkOpener(state.getLine().getContent(), nextNonSpace);
            if (blockParser != null) {
                return BlockStart.of(blockParser).atIndex(nextNonSpace);
            } else {
                return BlockStart.none();
            }
        }
    }

    private static MathBlockParser checkOpener(CharSequence line, int index) {
        int count = Characters.skip('$', line, index, line.length()) - index;
        if (count == 2 && line.toString().trim().equals("$$")) {
            return new MathBlockParser();
        }
        return null;
    }

    // spec: The content of the code block consists of all subsequent lines, until a closing code fence of the same type
    // as the code block began with (backticks or tildes), and with at least as many backticks or tildes as the opening
    // code fence.
    private boolean tryClosing(CharSequence line, int index) {
        int count = Characters.skip('$', line, index, line.length()) - index;
        return count == 2 && line.toString().trim().equals("$$");
    }
}
