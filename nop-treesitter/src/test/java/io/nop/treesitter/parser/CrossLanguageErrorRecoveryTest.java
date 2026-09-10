package io.nop.treesitter.parser;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ER-04 broken-input matrix across the shipped Java, JavaScript and
 * TypeScript grammars (roadmap item 11 phase 3). Every expected tree was
 * produced by the upstream C runtime oracle built from the reference
 * {@code lib/src/lib.c} plus the vendored grammar sources
 * ({@code _tmp/ts-oracle/}, run 2026-09-10), rendered in the
 * {@code ts_node_string} flat form with field-name prefixes stripped to match
 * the vendored corpus conventions.
 */
class CrossLanguageErrorRecoveryTest {

    private static final Language JAVA = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
    private static final Language JS = Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin");
    private static final Language TS = Language.fromClasspath("/grammars/typescript/tree-sitter-typescript-blob.bin");

    private static String parseFlat(Language language, String source) {
        return TSParser.parse(language, source).toSexpString(false);
    }

    @Test
    void javaUnclosedClassBodyInsertsMissingBrace() {
        assertEquals("(program (class_declaration (identifier) (class_body (method_declaration (void_type) "
                        + "(identifier) (formal_parameters) (block (local_variable_declaration (integral_type) "
                        + "(variable_declarator (identifier) (decimal_integer_literal))))) (MISSING \"}\"))))",
                parseFlat(JAVA, "class A { void m() { int x = 1; }"));
    }

    @Test
    void javaEmptyReturnWrapsInError() {
        assertEquals("(program (class_declaration (identifier) (class_body (field_declaration (integral_type) "
                        + "(variable_declarator (identifier))) (method_declaration (void_type) (identifier) "
                        + "(formal_parameters) (block (ERROR))))))",
                parseFlat(JAVA, "class A { int f; void m() { return } }"));
    }

    @Test
    void javaMissingInitializerProducesErrorAtDeclarationLevel() {
        assertEquals("(program (class_declaration (identifier) (class_body (method_declaration (void_type) "
                        + "(identifier) (formal_parameters) (block (local_variable_declaration (integral_type) "
                        + "(variable_declarator (identifier)) (ERROR)))))))",
                parseFlat(JAVA, "class A { void m() { int x = ; } }"));
    }

    @Test
    void javaMissingClassNameRecoversToErrorAndBlock() {
        assertEquals("(program (ERROR) (block))", parseFlat(JAVA, "class { }"));
    }

    @Test
    void javascriptUnclosedConditionInsertsMissingParen() {
        assertEquals("(program (if_statement (parenthesized_expression (identifier) (MISSING \")\")) "
                        + "(statement_block (expression_statement (call_expression (identifier) (arguments))))))",
                parseFlat(JS, "if (x { y(); }"));
    }

    @Test
    void typescriptMissingInitializerProducesErrorAfterAnnotation() {
        assertEquals("(program (lexical_declaration (variable_declarator (identifier) "
                        + "(type_annotation (predefined_type))) (ERROR)))",
                parseFlat(TS, "const x: number = ;"));
    }

    @Test
    void typescriptUnclosedInterfaceInsertsMissingBrace() {
        assertEquals("(program (interface_declaration (type_identifier) (interface_body "
                        + "(property_signature (property_identifier) (type_annotation (predefined_type))) "
                        + "(MISSING \"}\"))))",
                parseFlat(TS, "interface I { a: string"));
    }

    @Test
    void typescriptDanglingAsExpressionRecoversAtTopLevel() {
        assertEquals("(program (ERROR (identifier) (number)))", parseFlat(TS, "let y = 1 as"));
    }
}
