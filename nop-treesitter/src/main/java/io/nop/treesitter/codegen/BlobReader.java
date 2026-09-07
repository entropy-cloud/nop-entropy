package io.nop.treesitter.codegen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal independent reader for the blob format documented in
 * {@code src/main/resources/blob-format.md}. Deliberately separate from
 * {@link BlobWriter} so that writer/reader round-trip tests exercise two
 * independent implementations of the same documented layout.
 *
 * <p>Throws {@link IllegalStateException} on magic / version mismatch or
 * truncated data — no silent defaulting.</p>
 */
public final class BlobReader {

    public static final int HEADER_SIZE = 64;

    private BlobReader() {
    }

    public record Header(int formatVersion, int abiVersion, int symbolCount, int stateCount,
                         int largeStateCount, int tokenCount, int productionIdCount, int fieldCount,
                         int parseActionGroupCount, int smallParseTableWordCount, int smallParseTableMapCount,
                         int lexModeCount, int keywordLexModeCount, int primaryStateIdCount) {
    }

    public record Decoded(Header header, String[] symbolNames, int[] symbolFlags,
                          ExtractedGrammar.ParseActionGroup[] parseActions, int[] largeParseTable, int[] smallParseTable,
                          int[] smallParseTableMap, int[] primaryStateIds, int[] lexModes, int[] keywordLexModes) {
    }

    public static Decoded read(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new IllegalStateException("blob truncated: " + data.length + " bytes, header needs " + HEADER_SIZE);
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (magic[0] != 'T' || magic[1] != 'S' || magic[2] != 'J' || magic[3] != 'B') {
            throw new IllegalStateException("bad blob magic: "
                    + String.format("%02x %02x %02x %02x", magic[0], magic[1], magic[2], magic[3]));
        }
        int formatVersion = buf.get() & 0xFF;
        int abiVersion = buf.get() & 0xFF;
        buf.getShort(); // reserved
        Header header = new Header(formatVersion, abiVersion,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF,
                buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF);
        if (formatVersion != 1) {
            throw new IllegalStateException("unsupported blob format version: " + formatVersion);
        }
        for (int i = 0; i < 32; i++) {
            buf.get();
        }

        String[] symbolNames = new String[header.symbolCount()];
        for (int i = 0; i < header.symbolCount(); i++) {
            int len = buf.get() & 0xFF;
            byte[] b = new byte[len];
            buf.get(b);
            symbolNames[i] = new String(b, StandardCharsets.UTF_8);
        }

        int[] symbolFlags = new int[header.symbolCount()];
        for (int i = 0; i < header.symbolCount(); i++) {
            symbolFlags[i] = buf.get() & 0xFF;
        }

        ExtractedGrammar.ParseActionGroup[] groups = new ExtractedGrammar.ParseActionGroup[header.parseActionGroupCount()];
        for (int i = 0; i < groups.length; i++) {
            int index = buf.getShort() & 0xFFFF;
            int count = buf.get() & 0xFF;
            int reusable = buf.get() & 0xFF;
            ExtractedGrammar.ParseAction[] actions = new ExtractedGrammar.ParseAction[count];
            for (int j = 0; j < count; j++) {
                int type = buf.get() & 0xFF;
                int flags = buf.get() & 0xFF;
                int a = buf.getShort() & 0xFFFF;
                int b = buf.getShort() & 0xFFFF;
                int c = buf.getShort();
                int d = buf.getShort() & 0xFFFF;
                switch (type) {
                    case ExtractedGrammar.ParseAction.SHIFT ->
                            actions[j] = new ExtractedGrammar.ParseAction(type, a, 0, 0, 0, 0,
                                    (flags & 0x01) != 0, (flags & 0x02) != 0);
                    case ExtractedGrammar.ParseAction.REDUCE ->
                            actions[j] = new ExtractedGrammar.ParseAction(type, 0, a, b, c, d,
                                    false, false);
                    default ->
                            actions[j] = new ExtractedGrammar.ParseAction(type, 0, 0, 0, 0, 0,
                                    false, false);
                }
            }
            groups[i] = new ExtractedGrammar.ParseActionGroup(index, count, reusable == 1, actions);
        }

        int[] largeParseTable = new int[header.largeStateCount() * header.symbolCount()];
        for (int i = 0; i < largeParseTable.length; i++) {
            largeParseTable[i] = buf.getShort() & 0xFFFF;
        }

        int[] smallParseTable = new int[header.smallParseTableWordCount()];
        for (int i = 0; i < smallParseTable.length; i++) {
            smallParseTable[i] = buf.getShort() & 0xFFFF;
        }

        int[] smallParseTableMap = new int[header.smallParseTableMapCount()];
        for (int i = 0; i < smallParseTableMap.length; i++) {
            smallParseTableMap[i] = buf.getInt();
        }

        int[] primaryStateIds = new int[header.primaryStateIdCount()];
        for (int i = 0; i < primaryStateIds.length; i++) {
            primaryStateIds[i] = buf.getShort() & 0xFFFF;
        }

        int[] lexModes = new int[header.lexModeCount()];
        for (int i = 0; i < lexModes.length; i++) {
            lexModes[i] = buf.getShort() & 0xFFFF;
        }

        int[] keywordLexModes = new int[header.keywordLexModeCount()];
        for (int i = 0; i < keywordLexModes.length; i++) {
            keywordLexModes[i] = buf.getShort() & 0xFFFF;
        }

        if (buf.hasRemaining()) {
            throw new IllegalStateException("blob has trailing bytes: " + buf.remaining());
        }
        return new Decoded(header, symbolNames, symbolFlags, groups, largeParseTable,
                smallParseTable, smallParseTableMap, primaryStateIds, lexModes, keywordLexModes);
    }
}