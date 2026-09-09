package io.nop.treesitter.parser;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;
import io.nop.treesitter.subtree.SubtreeArena;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: the LR(1) parser, TSTree wrapper and toSExpression — plus the
 * Language → Lexer → Parser → TSTree wiring check and the no-silent-skip
 * guarantees (unimplemented action types raise, parse errors propagate).
 */
class ParserTest {

    private static final Language LANGUAGE = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");

    private static String parseSexp(String source) {
        return TSParser.parse(LANGUAGE, source).toSExpression();
    }

    @Test
    void emptyArrayParses() {
        assertEquals("(document\n  (array))", parseSexp("[]"));
    }

    @Test
    void emptyAndSingleStringsParse() {
        assertEquals("(document\n  (string))", parseSexp("\"\""));
        assertEquals("(document\n  (string\n    (string_content)))", parseSexp("\"abc\""));
    }

    @Test
    void stringWithEscapeSequenceParses() {
        assertEquals("(document\n  (string\n    (string_content)\n    (escape_sequence)))",
                parseSexp("\"def\\n\""));
    }

    @Test
    void nestedArraysAndNumbersParse() {
        assertEquals("(document\n  (array\n    (number)\n    (number)))", parseSexp("[1, 2]"));
        assertEquals("(document\n  (array\n    (number)\n    (array\n      (number))))",
                parseSexp("[1, [2]]"));
        assertEquals("(document\n  (array\n    (number)\n    (number)\n    (number)\n    (number)))",
                parseSexp("[1e10, 1e+10, 1E+10, 1e-10]"));
    }

    @Test
    void objectWithPairsParses() {
        assertEquals("(document\n  (object\n    (pair\n      (string\n        (string_content))\n"
                        + "      (number))))",
                parseSexp("{\"a\": 1}"));
        assertEquals("(document\n  (object\n    (pair\n      (string\n        (string_content))\n"
                        + "      (number))\n    (pair\n      (string\n        (string_content))\n"
                        + "      (string\n        (string_content)))))",
                parseSexp("{\"a\": 1, \"b\": \"2\"}"));
    }

    @Test
    void commentsBecomeChildrenOfEnclosingNode() {
        assertEquals("(document\n  (object\n    (pair\n      (string\n        (string_content))\n"
                        + "      (number))\n    (comment)\n    (pair\n      (string\n        (string_content))\n"
                        + "      (string\n        (string_content)))))",
                parseSexp("{ \"a\": 1, /*c*/ \"b\": \"2\" }"));
    }

    @Test
    void topLevelScalarsAndMultipleObjectsParse() {
        assertEquals("(document\n  (number))", parseSexp("-1"));
        assertEquals("(document\n  (null))", parseSexp("null"));
        assertEquals("(document\n  (true))", parseSexp("true"));
        assertEquals("(document\n  (object)\n  (object))", parseSexp("{}\n{}"));
    }

    @Test
    void wiredEndToEndPathProducesNavigableTree() {
        TSTree tree = TSParser.parse(LANGUAGE, "[1, {\"k\": null}]");
        assertEquals("(document\n  (array\n    (number)\n    (object\n      (pair\n"
                        + "        (string\n          (string_content))\n        (null)))))",
                tree.toSExpression());

        SubtreeArena arena = tree.arena();
        Subtree root = arena.get(tree.root());
        assertEquals(15, root.symbol(), "root is the document symbol");
        assertEquals("document", tree.language().symbolName(root.symbol()));

        Subtree value = arena.get(root.child(0));
        assertEquals(16, value.symbol(), "document child is the hidden _value supertype");
        Subtree array = arena.get(value.child(0));
        assertEquals(19, array.symbol(), "array symbol");
        assertEquals(4, array.childCount(), "array holds '[' value repeat1 ']'");
        Subtree numberWrap = arena.get(array.child(1));
        assertEquals(16, numberWrap.symbol(), "hidden _value wrapper around the first element");
        assertEquals(10, arena.get(numberWrap.child(0)).symbol(), "first element is a number");
        Subtree repeat1 = arena.get(array.child(2));
        assertEquals(24, repeat1.symbol(), "hidden aux_sym_array_repeat1");
        Subtree valueWrap = arena.get(repeat1.child(1));
        assertEquals(16, valueWrap.symbol(), "hidden _value wrapper in the repeat");
        Subtree object = arena.get(valueWrap.child(0));
        assertEquals(17, object.symbol(), "object symbol");
        Subtree pair = arena.get(object.child(1));
        assertEquals(18, pair.symbol(), "pair symbol");
        assertEquals(3, pair.childCount());
        Subtree valueWrap2 = arena.get(pair.child(2));
        assertEquals(16, valueWrap2.symbol(), "pair value is a hidden _value wrapper");
        assertEquals(13, arena.get(valueWrap2.child(0)).symbol(), "null symbol");
    }

    /**
     * Pre-recovery these inputs threw TreeSitterException; with item 11
     * recovery they parse to the upstream C-runtime trees (oracle-verified,
     * see JsonErrorRecoveryTest).
     */
    @Test
    void brokenInputsRecoverToErrorTreesInsteadOfThrowing() {
        assertEquals("(document\n  (array\n    (number)\n    (number)\n    (MISSING \"]\")))",
                parseSexp("[1, 2"));
        assertEquals("(document\n  (ERROR))", parseSexp("{"));
        assertEquals("(document\n  (array\n    (true)\n    (ERROR\n      (UNEXPECTED 'x'))))",
                parseSexp("[truex]"));
    }

    @Test
    void unimplementedActionTypeRaisesUnsupportedOperationNamingTheAction() {
        Language synthetic = Language.fromBytes(syntheticBlob(9));
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> TSParser.parse(synthetic, "{"));
        assertTrue(ex.getMessage().contains("9"), ex.getMessage());
    }

    /**
     * Pre-recovery the synthetic RECOVER group raised
     * UnsupportedOperationException; item 11 dispatches it into the recovery
     * engine, and the parse terminates with an ERROR-carrying tree.
     */
    @Test
    void recoverActionDispatchesIntoErrorRecoveryProducingAnErrorTree() {
        Language synthetic = Language.fromBytes(syntheticBlob(3));
        TSTree tree = TSParser.parse(synthetic, "{");
        String sexp = tree.toSexpString(false);
        assertTrue(sexp.contains("(ERROR"), sexp);
    }

    /**
     * A minimal hand-built v4 blob (2 symbols: end + '{'; 2 states; one action
     * group with the given action type; a two-state lexer automaton accepting
     * '{' as symbol 1) whose parse table sends '{' from state 1 to that group,
     * so a parse of "{" dispatches the synthetic action.
     */
    private static byte[] syntheticBlob(int actionType) {
        ByteBuffer buf = ByteBuffer.allocate(182);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(new byte[]{'T', 'S', 'J', 'B'});
        buf.put((byte) 4);  // format version
        buf.put((byte) 14); // abi version
        buf.putShort((short) 0);
        buf.putShort((short) 2); // symbol count
        buf.putShort((short) 2); // state count
        buf.putShort((short) 2); // large state count
        buf.putShort((short) 2); // token count
        buf.putShort((short) 0); // production id count
        buf.putShort((short) 0); // field count
        buf.putShort((short) 1); // parse action group count
        buf.putInt(0);           // small parse table words (u32 since v4)
        buf.putShort((short) 0); // small parse table map count
        buf.putShort((short) 2); // lex mode count
        buf.putShort((short) 0); // keyword lex mode count
        buf.putShort((short) 2); // primary state id count
        buf.putShort((short) 0); // alias count
        buf.putShort((short) 0); // max alias sequence length
        buf.putShort((short) 0); // field name count
        buf.putShort((short) 0); // field map slice count
        buf.putInt(0);           // field map entry count
        buf.putInt(0);           // alias sequence element count
        buf.putInt(0);           // non-terminal alias map count
        buf.putShort((short) 0); // keyword capture token
        buf.putShort((short) 0); // external token count
        buf.putShort((short) 0); // external lex state count
        buf.putShort((short) 0); // reserved word set count
        buf.putShort((short) 0); // max reserved word set size
        buf.putInt(0);           // scanner program length
        buf.put((byte) 1);       // lexer fn count
        for (int i = 0; i < 27; i++) {
            buf.put((byte) 0);
        }
        buf.put((byte) 3);
        buf.put("end".getBytes(StandardCharsets.UTF_8));
        buf.put((byte) 1);
        buf.put((byte) '{');
        buf.put((byte) 0x02); // end: named, invisible
        buf.put((byte) 0x03); // '{': named, visible
        buf.putShort((short) 7); // group index
        buf.put((byte) 1);       // action count
        buf.put((byte) 1);       // reusable
        buf.put((byte) actionType);
        buf.put((byte) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        // large parse table: [1][1] = group 7
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 7);
        // primary state ids
        buf.putShort((short) 0);
        buf.putShort((short) 1);
        // lex modes (v3: lex_state, external_lex_state, reserved_word_set_id)
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        // lexer fn count byte
        buf.put((byte) 1);
        // lexer automaton: 2 states; state 0 '{' -> state 1; state 1 accepts symbol 1
        buf.putShort((short) 2); // state count
        buf.putShort((short) 1); // accept count
        buf.putInt(1);           // transition count
        buf.putShort((short) 0); // set count
        buf.putInt(0);           // set range count
        buf.putShort((short) 1); // accept state
        buf.putShort((short) 1); // accept symbol
        buf.put((byte) 0);       // at exit
        buf.put((byte) 1);       // state 0 transition count
        buf.put((byte) 0);       // state 1 transition count
        buf.putShort((short) 1); // target state
        buf.put((byte) 0);       // not a skip
        buf.put((byte) 1);       // one clause
        buf.put((byte) 1);       // one literal
        buf.put((byte) 1);       // CHAR_EQ
        buf.putInt('{');
        buf.putInt(0);
        // v3 sections: empty external symbol map / states / reserved words / scanner program
        buf.putInt(0);           // scanner program length
        return buf.array();
    }
}