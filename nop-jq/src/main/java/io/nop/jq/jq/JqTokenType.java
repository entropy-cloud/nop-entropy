package io.nop.jq.jq;

/**
 * Token types for jq expression lexer.
 */
public enum JqTokenType {
    // Literals
    IDENT,       // variable names, property names
    INTEGER,     // integer literals
    FLOAT,       // float literals
    STRING,      // string literals ('...')

    // Operators
    PIPE,        // |
    DOT,         // .
    DOT_DOT,     // ..
    COMMA,       // ,
    COLON,       // :
    SEMICOLON,   // ;
    EQUAL,       // ==
    NOT_EQUAL,   // !=
    GREATER,     // >
    GREATER_EQ,  // >=
    LESS,        // <
    LESS_EQ,     // <=
    AND,         // and
    OR,          // or
    NOT,         // not
    PLUS,        // +
    MINUS,       // -
    MULTIPLY,    // *
    DIVIDE,      // /
    MODULO,      // %
    ASSIGN,      // =
    PIPE_ASSIGN, // |=
    PLUS_ASSIGN, // +=
    MINUS_ASSIGN,// -=
    MULTIPLY_ASSIGN, // *=
    DIVIDE_ASSIGN,   // /=
    MODULO_ASSIGN,   // %=
    ALTERNATIVE,     // //
    ALTERNATIVE_ASSIGN, // //=
    QUESTION_SLASH,  // ?//

    // Delimiters
    LPAREN,      // (
    RPAREN,      // )
    LBRACKET,    // [
    RBRACKET,    // ]
    LBRACE,      // {
    RBRACE,      // }
    QUESTION,    // ?
    AT,          // @

    // Keywords
    SELECT,      // select
    MAP,         // map
    REDUCE,      // reduce
    IF,          // if
    THEN,        // then
    ELSE,        // else
    ELIF,        // elif
    END,         // end
    AS,          // as
    TRY,         // try
    CATCH,       // catch
    RECURSE,     // recurse
    LABEL,       // label
    BREAK,       // break
    LENGTH,      // length
    KEYS,        // keys
    VALUES,      // values
    TYPE,        // type
    EMPTY,       // empty
    NULL,        // null
    TRUE,        // true
    FALSE,       // false
    INPUT,       // input
    INPUTS,      // inputs
    LIMIT,       // limit
    DEF,         // def
    IMPORT,      // import
    MODULE,      // module
    DEBUG,       // debug
    ENV,         // env
    FOREACH,     // foreach
    UNTIL,       // until
    WHILE,       // while

    // Special
    EOF,
    ERROR
}
