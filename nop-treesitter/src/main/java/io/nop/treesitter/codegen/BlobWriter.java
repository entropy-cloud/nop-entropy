package io.nop.treesitter.codegen;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Serializes an {@link ExtractedGrammar} into the compact binary blob format
 * documented in {@code src/main/resources/blob-format.md}.
 *
 * <p>All multi-byte integers are big-endian. The writer throws
 * {@link IllegalStateException} when a value does not fit the declared width
 * of its section instead of truncating silently.</p>
 */
public final class BlobWriter {

    private static final byte[] MAGIC = {'T', 'S', 'J', 'B'};
    private static final int FORMAT_VERSION = 1;

    private BlobWriter() {
    }

    public static byte[] write(ExtractedGrammar g) {
        int size = headerSize() + namesSize(g) + metadataSize(g) + actionsSize(g)
                + parseTableSize(g) + smallTableSize(g) + smallTableMapSize(g)
                + primaryStateIdsSize(g) + lexModesSize(g) + keywordLexModesSize(g);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.order(java.nio.ByteOrder.BIG_ENDIAN);

        writeHeader(buf, g);
        writeSymbolNames(buf, g);
        writeSymbolMetadata(buf, g);
        writeParseActions(buf, g);
        writeParseTable(buf, g);
        writeSmallParseTable(buf, g);
        writeSmallParseTableMap(buf, g);
        writePrimaryStateIds(buf, g);
        writeLexModes(buf, g);
        writeKeywordLexModes(buf, g);

        if (buf.position() != size) {
            throw new IllegalStateException("blob size mismatch: wrote " + buf.position() + " of " + size);
        }
        return buf.array();
    }

    private static int headerSize() {
        return 64;
    }

    private static void writeHeader(ByteBuffer buf, ExtractedGrammar g) {
        checkRange(g.symbolCount, 0xFFFF, "symbol_count");
        checkRange(g.stateCount, 0xFFFF, "state_count");
        checkRange(g.largeStateCount, 0xFFFF, "large_state_count");
        checkRange(g.tokenCount, 0xFFFF, "token_count");
        checkRange(g.productionIdCount, 0xFFFF, "production_id_count");
        checkRange(g.fieldCount, 0xFFFF, "field_count");
        checkRange(g.parseActions.length, 0xFFFF, "parse_action_group_count");
        checkRange(g.smallParseTable.length, 0xFFFF, "small_parse_table_word_count");
        checkRange(g.smallParseTableMap.length, 0xFFFF, "small_parse_table_map_count");
        checkRange(g.lexModes.length, 0xFFFF, "lex_mode_count");
        checkRange(g.keywordLexModes.length, 0xFFFF, "keyword_lex_mode_count");
        checkRange(g.primaryStateIds.length, 0xFFFF, "primary_state_id_count");

        buf.put(MAGIC);
        buf.put((byte) FORMAT_VERSION);
        buf.put((byte) g.languageVersion);
        buf.putShort((short) 0);
        buf.putShort((short) g.symbolCount);
        buf.putShort((short) g.stateCount);
        buf.putShort((short) g.largeStateCount);
        buf.putShort((short) g.tokenCount);
        buf.putShort((short) g.productionIdCount);
        buf.putShort((short) g.fieldCount);
        buf.putShort((short) g.parseActions.length);
        buf.putShort((short) g.smallParseTable.length);
        buf.putShort((short) g.smallParseTableMap.length);
        buf.putShort((short) g.lexModes.length);
        buf.putShort((short) g.keywordLexModes.length);
        buf.putShort((short) g.primaryStateIds.length);
        for (int i = 0; i < 32; i++) {
            buf.put((byte) 0);
        }
    }

    private static int namesSize(ExtractedGrammar g) {
        int n = 0;
        for (String name : g.symbolNames) {
            byte[] b = name.getBytes(StandardCharsets.UTF_8);
            checkRange(b.length, 0xFF, "symbol name length");
            n += 1 + b.length;
        }
        return n;
    }

    private static void writeSymbolNames(ByteBuffer buf, ExtractedGrammar g) {
        for (String name : g.symbolNames) {
            byte[] b = name.getBytes(StandardCharsets.UTF_8);
            buf.put((byte) b.length);
            buf.put(b);
        }
    }

    private static int metadataSize(ExtractedGrammar g) {
        return g.symbolMetadata.length;
    }

    private static void writeSymbolMetadata(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.SymbolMeta m : g.symbolMetadata) {
            int flags = 0;
            if (m.visible()) {
                flags |= 0x01;
            }
            if (m.named()) {
                flags |= 0x02;
            }
            if (m.supertype()) {
                flags |= 0x04;
            }
            buf.put((byte) flags);
        }
    }

    private static int actionsSize(ExtractedGrammar g) {
        int n = 0;
        for (ExtractedGrammar.ParseActionGroup group : g.parseActions) {
            checkRange(group.count(), 0xFF, "parse action group count");
            n += 4 + group.actions().length * 10;
        }
        return n;
    }

    private static void writeParseActions(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.ParseActionGroup group : g.parseActions) {
            checkRange(group.index(), 0xFFFF, "parse action group index");
            checkRange(group.count(), 0xFF, "parse action group count");
            if (group.actions().length != group.count()) {
                throw new IllegalStateException("parse action group " + group.index() + ": declared "
                        + group.count() + " actions but " + group.actions().length + " present");
            }
            buf.putShort((short) group.index());
            buf.put((byte) group.count());
            buf.put((byte) (group.reusable() ? 1 : 0));
            for (ExtractedGrammar.ParseAction a : group.actions()) {
                writeAction(buf, a);
            }
        }
    }

    private static void writeAction(ByteBuffer buf, ExtractedGrammar.ParseAction a) {
        int flags = (a.extra() ? 0x01 : 0x00) | (a.repetition() ? 0x02 : 0x00);
        buf.put((byte) a.type());
        buf.put((byte) flags);
        switch (a.type()) {
            case ExtractedGrammar.ParseAction.SHIFT -> {
                checkRange(a.state(), 0xFFFF, "shift state");
                buf.putShort((short) a.state());
                buf.putShort((short) 0);
                buf.putShort((short) 0);
                buf.putShort((short) 0);
            }
            case ExtractedGrammar.ParseAction.REDUCE -> {
                checkRange(a.symbol(), 0xFFFF, "reduce symbol");
                checkRange(a.childCount(), 0xFFFF, "reduce child count");
                checkRange(a.productionId(), 0xFFFF, "reduce production id");
                buf.putShort((short) a.symbol());
                buf.putShort((short) a.childCount());
                buf.putShort((short) a.dynamicPrecedence());
                buf.putShort((short) a.productionId());
            }
            case ExtractedGrammar.ParseAction.ACCEPT, ExtractedGrammar.ParseAction.RECOVER -> {
                buf.putShort((short) 0);
                buf.putShort((short) 0);
                buf.putShort((short) 0);
                buf.putShort((short) 0);
            }
            default -> throw new IllegalStateException("unknown parse action type: " + a.type());
        }
    }

    private static int parseTableSize(ExtractedGrammar g) {
        return g.largeStateCount * g.symbolCount * 2;
    }

    private static void writeParseTable(ByteBuffer buf, ExtractedGrammar g) {
        for (int state = 0; state < g.largeStateCount; state++) {
            for (int sym = 0; sym < g.symbolCount; sym++) {
                int v = g.parseTable[state][sym];
                checkRange(v, 0xFFFF, "parse table cell");
                buf.putShort((short) v);
            }
        }
    }

    private static int smallTableSize(ExtractedGrammar g) {
        return g.smallParseTable.length * 2;
    }

    private static void writeSmallParseTable(ByteBuffer buf, ExtractedGrammar g) {
        for (int w : g.smallParseTable) {
            checkRange(w, 0xFFFF, "small parse table word");
            buf.putShort((short) w);
        }
    }

    private static int smallTableMapSize(ExtractedGrammar g) {
        return g.smallParseTableMap.length * 4;
    }

    private static void writeSmallParseTableMap(ByteBuffer buf, ExtractedGrammar g) {
        for (int off : g.smallParseTableMap) {
            buf.putInt(off);
        }
    }

    private static int primaryStateIdsSize(ExtractedGrammar g) {
        return g.primaryStateIds.length * 2;
    }

    private static void writePrimaryStateIds(ByteBuffer buf, ExtractedGrammar g) {
        for (int id : g.primaryStateIds) {
            checkRange(id, 0xFFFF, "primary state id");
            buf.putShort((short) id);
        }
    }

    private static int lexModesSize(ExtractedGrammar g) {
        return g.lexModes.length * 2;
    }

    private static void writeLexModes(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.LexMode m : g.lexModes) {
            checkRange(m.lexState(), 0xFFFF, "lex state");
            buf.putShort((short) m.lexState());
        }
    }

    private static int keywordLexModesSize(ExtractedGrammar g) {
        return g.keywordLexModes.length * 2;
    }

    private static void writeKeywordLexModes(ByteBuffer buf, ExtractedGrammar g) {
        for (ExtractedGrammar.LexMode m : g.keywordLexModes) {
            checkRange(m.lexState(), 0xFFFF, "keyword lex state");
            buf.putShort((short) m.lexState());
        }
    }

    private static void checkRange(int value, int max, String what) {
        if (value < 0 || value > max) {
            throw new IllegalStateException(what + " out of declared width range: " + value
                    + " (max " + max + ")");
        }
    }
}