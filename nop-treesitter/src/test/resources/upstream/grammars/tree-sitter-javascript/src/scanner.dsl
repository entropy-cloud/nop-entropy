# Hand translation of tree-sitter-javascript/src/scanner.c (MIT) into the
# external-scanner DSL compiled by io.nop.treesitter.scanner.ScannerCompiler.
#
# Branch convention (matches the VM): "jmp_if_X <v> <label>" falls through when
# the condition X(v) holds and jumps to <label> otherwise, so "if (cond) {A}
# else {B}" is written as "jmp_if_X <B-start>; A" with B placed at the label.
# Subroutines return via RET with result = 1 (token accepted) or 0 (rejected);
# the dispatcher emits the token. Register usage mirrors scanner.c locals:
#   result register = ws WhitespaceResult / saw_block_newline / subroutine bool
#   flag register   = *scanned_comment (ws out-param) / has_content / at_newline
#   state register  = comment_condition for scan_automatic_semicolon
#
# Constructs from scanner.c the DSL cannot express raise at translation time
# (unknown mnemonic); the JS scanner translates with an empty inventory.

token automatic_semicolon
token template_chars
token ternary_qmark
token html_comment
token logical_or
token escape_sequence
token regex_pattern
token jsx_text

# ---------------------------------------------------------------
# tree_sitter_javascript_external_scanner_scan — the dispatcher.
# ---------------------------------------------------------------
dispatcher:
    # if (valid_symbols[TEMPLATE_CHARS]) {
    #   if (valid_symbols[AUTOMATIC_SEMICOLON]) return false;
    #   return scan_template_chars(lexer);
    # }
    jmp_if_valid template_chars d_templ_absent          # TEMPLATE_CHARS valid -> body
    jmp_if_valid automatic_semicolon d_templ_call       # ASI valid -> reject
    fail
d_templ_call:
    call scan_template_chars
    jmp_if_result_eq 1 d_templ_failed                   # result==1 -> emit
    emit template_chars
d_templ_failed:
    fail
d_templ_absent:
    # if (valid_symbols[JSX_TEXT] && scan_jsx_text(lexer)) return true;
    jmp_if_valid jsx_text d_jsx_absent                  # JSX_TEXT valid -> body
    call scan_jsx_text
    jmp_if_result_eq 1 d_jsx_failed                     # result==1 -> emit
    emit jsx_text
d_jsx_failed:
    fail
d_jsx_absent:
    # if (valid_symbols[AUTOMATIC_SEMICOLON]) {
    #   bool scanned_comment = false;
    #   bool ret = scan_automatic_semicolon(lexer, !valid_symbols[LOGICAL_OR], &scanned_comment);
    #   if (!ret && !scanned_comment && valid_symbols[TERNARY_QMARK] && lexer->lookahead == '?')
    #     return scan_ternary_qmark(lexer);
    #   return ret;
    # }
    jmp_if_valid automatic_semicolon d_asi_absent       # ASI valid -> body
    jmp_if_valid logical_or d_cc_true                   # LOGICAL_OR valid -> comment_condition=false
    set_state 0
    jmp d_asi_run
d_cc_true:
    set_state 1
d_asi_run:
    set_flag 0
    call scan_automatic_semicolon
    jmp_if_result_eq 1 d_asi_fallback_checks            # result==1 -> emit
    emit automatic_semicolon
d_asi_fallback_checks:
    # ret == false: if (!scanned_comment && valid TERNARY_QMARK && lookahead=='?') ternary
    jmp_if_flag_eq 1 d_asi_ternary_check                # scanned_comment -> no fallback
    fail
d_asi_ternary_check:
    jmp_if_valid ternary_qmark d_asi_no_qmark           # TERNARY_QMARK valid -> check '?'
    jmp_if_eq '?' d_asi_no_qmark                        # lookahead == '?' -> ternary
    call scan_ternary_qmark
    jmp_if_result_eq 1 d_asi_qmark_failed               # result==1 -> emit
    emit ternary_qmark
d_asi_qmark_failed:
    fail
d_asi_no_qmark:
    fail
d_asi_absent:
    # if (valid_symbols[TERNARY_QMARK]) return scan_ternary_qmark(lexer);
    jmp_if_valid ternary_qmark d_ternary_absent         # TERNARY_QMARK valid -> body
    call scan_ternary_qmark
    jmp_if_result_eq 1 d_ternary_failed                 # result==1 -> emit
    emit ternary_qmark
d_ternary_failed:
    fail
d_ternary_absent:
    # if (valid_symbols[HTML_COMMENT] && !valid_symbols[LOGICAL_OR]
    #     && !valid_symbols[ESCAPE_SEQUENCE] && !valid_symbols[REGEX_PATTERN])
    #   return scan_html_comment(lexer);
    jmp_if_valid html_comment d_html_failed             # HTML_COMMENT valid -> body
    jmp_if_valid logical_or d_html_gate2                # LOGICAL_OR valid -> fail
    fail
d_html_gate2:
    jmp_if_valid escape_sequence d_html_gate3           # ESCAPE_SEQUENCE valid -> fail
    fail
d_html_gate3:
    jmp_if_valid regex_pattern d_html_call              # REGEX_PATTERN valid -> fail
    fail
d_html_call:
    call scan_html_comment
    jmp_if_result_eq 1 d_html_failed                    # result==1 -> emit
    emit html_comment
d_html_failed:
    fail

# ---------------------------------------------------------------
# scan_template_chars: flag = has_content (loop-carried).
#   loop { mark_end; switch (lookahead) {
#     '`': return has_content;  '\0': return false;
#     '$': advance; if '{' return has_content; break;
#     '\\': return has_content; default: advance; } }
# ---------------------------------------------------------------
scan_template_chars:
    set_flag 0
    jmp templ_entry
templ_loop:
    set_flag 1
templ_entry:
    mark_end
    jmp_if_eq '`' templ_eof_check                       # backtick -> has_content check
    jmp templ_backtick
templ_backtick:
    jmp_if_flag_eq 1 templ_fail
    jmp templ_emit
templ_eof_check:
    jmp_if_eq 0 templ_dollar_check                      # NUL -> reject
    jmp templ_eof
templ_eof:
    fail
templ_dollar_check:
    jmp_if_eq '$' templ_backslash_check                 # '$' -> dollar handler
    jmp templ_dollar
templ_dollar:
    advance
    jmp_if_eq '{' templ_loop_cont                       # '{' -> has_content check
    jmp templ_brace
templ_brace:
    jmp_if_flag_eq 1 templ_fail
    jmp templ_emit
templ_loop_cont:
    jmp templ_loop
templ_backslash_check:
    jmp_if_eq '\\' templ_default                        # '\\' -> has_content check
    jmp templ_backslash
templ_backslash:
    jmp_if_flag_eq 1 templ_fail
    jmp templ_emit
templ_default:
    advance
    jmp templ_loop
templ_emit:
    set_result 1
    ret
templ_fail:
    set_result 0
    ret

# ---------------------------------------------------------------
# scan_jsx_text: flag = at_newline, result = saw_text.
#   while (lookahead not in {0,<,>,{,},&}) {
#     if ('\n') at_newline = true;
#     else { at_newline &= iswspace; if (!at_newline) saw_text = true; }
#     advance; }
# ---------------------------------------------------------------
scan_jsx_text:
    set_flag 0
    set_result 0
jsx_loop:
    jmp_if_eq 0 jsx_lt_check                            # terminator 0 -> done
    jmp jsx_done
jsx_lt_check:
    jmp_if_eq '<' jsx_gt_check                          # '<' -> done
    jmp jsx_done
jsx_gt_check:
    jmp_if_eq '>' jsx_lbrace_check                      # '>' -> done
    jmp jsx_done
jsx_lbrace_check:
    jmp_if_eq '{' jsx_rbrace_check                      # '{' -> done
    jmp jsx_done
jsx_rbrace_check:
    jmp_if_eq '}' jsx_amp_check                         # '}' -> done
    jmp jsx_done
jsx_amp_check:
    jmp_if_eq '&' jsx_nl_check                          # '&' -> done
    jmp jsx_done
jsx_nl_check:
    jmp_if_eq '\n' jsx_ws_check                         # newline -> at_newline = true
    jmp jsx_nl_handler
jsx_nl_handler:
    set_flag 1
    advance
    jmp jsx_loop
jsx_ws_check:
    jmp_if_ws jsx_content_path                          # whitespace -> at_newline &= is_wspace
    jmp jsx_ws_handler
jsx_ws_handler:
    jmp_if_flag_eq 1 jsx_ws_saw                         # at_newline -> no text
    jmp jsx_ws_at_nl
jsx_ws_at_nl:
    advance
    jmp jsx_loop
jsx_ws_saw:
    set_result 1
    advance
    jmp jsx_loop
jsx_content_path:
    set_flag 0
    set_result 1
    advance
    jmp jsx_loop
jsx_done:
    mark_end
    jmp_if_result_eq 1 jsx_fail                         # saw_text -> accept
    ret
jsx_fail:
    set_result 0
    ret

# ---------------------------------------------------------------
# scan_whitespace_and_comments with consume=false and consume=true
# (two copies, consume baked in). result = saw_block_newline /
# WhitespaceResult (REJECT=0, NO_NEWLINE=1, ACCEPT=2); flag = *scanned_comment.
# ---------------------------------------------------------------
ws_false:
    jmp ws_f_ws_loop
ws_f_ws_loop:
    jmp_if_ws ws_f_slash_check                          # whitespace -> skip
    skip
    jmp ws_f_ws_loop
ws_f_slash_check:
    jmp_if_eq '/' ws_f_accept                           # '/' -> comment; else ACCEPT
    jmp ws_f_comment
ws_f_comment:
    skip
    jmp_if_eq '/' ws_f_star                             # "//" -> line comment; else star check
    jmp ws_f_line
ws_f_star:
    jmp_if_eq '*' ws_f_reject                           # "/*" -> block comment; else REJECT
    jmp ws_f_block
ws_f_line:
    skip
ws_f_line_loop:
    jmp_if_eq 0 ws_f_line_nl                            # EOF -> line done
    jmp ws_f_line_done
ws_f_line_nl:
    jmp_if_eq '\n' ws_f_line_2028                       # '\n' -> line done
    jmp ws_f_line_done
ws_f_line_2028:
    jmp_if_eq 0x2028 ws_f_line_2029                     # U+2028 -> line done
    jmp ws_f_line_done
ws_f_line_2029:
    jmp_if_eq 0x2029 ws_f_line_other                    # U+2029 -> line done
    jmp ws_f_line_done
ws_f_line_other:
    skip
    jmp ws_f_line_loop
ws_f_line_done:
    set_flag 1
    jmp ws_f_ws_loop
ws_f_block:
    skip
ws_f_block_loop:
    jmp_if_eq 0 ws_f_block_star_ck                      # EOF -> outer loop
    jmp ws_f_block_eof
ws_f_block_eof:
    jmp ws_f_ws_loop
ws_f_block_star_ck:
    jmp_if_eq '*' ws_f_block_nl_ck                      # '*' -> maybe "*/"
    jmp ws_f_block_star
ws_f_block_star:
    skip
    jmp_if_eq '/' ws_f_block_loop                       # '/' -> consume it, comment done
    skip
    jmp ws_f_block_end
ws_f_block_nl_ck:
    jmp_if_eq '\n' ws_f_block_2028                      # newline in block -> saw_block_newline
    jmp ws_f_block_nl
ws_f_block_nl:
    set_result 1
    skip
    jmp ws_f_block_loop
ws_f_block_2028:
    jmp_if_eq 0x2028 ws_f_block_2029                    # U+2028 -> saw_block_newline
    jmp ws_f_block_nl
ws_f_block_2029:
    jmp_if_eq 0x2029 ws_f_block_other                   # U+2029 -> saw_block_newline
    jmp ws_f_block_nl
ws_f_block_other:
    skip
    jmp ws_f_block_loop
ws_f_block_end:
    set_flag 1
    jmp_if_eq '/' ws_f_block_return                     # lookahead '/' -> continue outer loop
    jmp ws_f_ws_loop
ws_f_block_return:
    jmp_if_result_eq 1 ws_f_no_nl                       # saw_block_newline -> ACCEPT
    jmp ws_f_accept
ws_f_accept:
    set_result 2
    ret
ws_f_no_nl:
    set_result 1
    ret
ws_f_reject:
    set_result 0
    ret

ws_true:
    jmp ws_t_ws_loop
ws_t_ws_loop:
    jmp_if_ws ws_t_slash_check                          # whitespace -> skip
    skip
    jmp ws_t_ws_loop
ws_t_slash_check:
    jmp_if_eq '/' ws_t_accept                           # '/' -> comment; else ACCEPT
    jmp ws_t_comment
ws_t_comment:
    skip
    jmp_if_eq '/' ws_t_star                             # "//" -> line comment
    jmp ws_t_line
ws_t_star:
    jmp_if_eq '*' ws_t_reject                           # "/*" -> block comment
    jmp ws_t_block
ws_t_line:
    skip
ws_t_line_loop:
    jmp_if_eq 0 ws_t_line_nl                            # EOF -> line done
    jmp ws_t_line_done
ws_t_line_nl:
    jmp_if_eq '\n' ws_t_line_2028
    jmp ws_t_line_done
ws_t_line_2028:
    jmp_if_eq 0x2028 ws_t_line_2029
    jmp ws_t_line_done
ws_t_line_2029:
    jmp_if_eq 0x2029 ws_t_line_other
    jmp ws_t_line_done
ws_t_line_other:
    skip
    jmp ws_t_line_loop
ws_t_line_done:
    set_flag 1
    jmp ws_t_ws_loop
ws_t_block:
    skip
ws_t_block_loop:
    jmp_if_eq 0 ws_t_block_star_ck                      # EOF -> outer loop
    jmp ws_t_block_eof
ws_t_block_eof:
    jmp ws_t_ws_loop
ws_t_block_star_ck:
    jmp_if_eq '*' ws_t_block_nl_ck
    jmp ws_t_block_star
ws_t_block_star:
    skip
    jmp_if_eq '/' ws_t_block_loop                       # '/' -> consume it, comment done
    skip
    jmp ws_t_block_end
ws_t_block_nl_ck:
    jmp_if_eq '\n' ws_t_block_2028
    jmp ws_t_block_nl
ws_t_block_nl:
    set_result 1
    skip
    jmp ws_t_block_loop
ws_t_block_2028:
    jmp_if_eq 0x2028 ws_t_block_2029
    jmp ws_t_block_nl
ws_t_block_2029:
    jmp_if_eq 0x2029 ws_t_block_other
    jmp ws_t_block_nl
ws_t_block_other:
    skip
    jmp ws_t_block_loop
ws_t_block_end:
    # consume=true: the block comment always continues the outer loop.
    set_flag 1
    jmp ws_t_ws_loop
ws_t_accept:
    set_result 2
    ret
ws_t_reject:
    set_result 0
    ret

# ---------------------------------------------------------------
# scan_automatic_semicolon: state = comment_condition (set by the
# dispatcher), flag = *scanned_comment, result = accept/reject.
# ---------------------------------------------------------------
scan_automatic_semicolon:
    mark_end
asi_loop:
    jmp_if_eq 0 asi_slash_check                         # EOF -> accept
    jmp asi_accept
asi_slash_check:
    jmp_if_eq '/' asi_close_check                       # '/' -> scan whitespace/comments
    jmp asi_slash
asi_slash:
    call ws_false
    jmp_if_result_eq 0 asi_ws_cont                      # REJECT -> reject
    jmp asi_reject
asi_ws_cont:
    # C falls through to the '}'/newline/iswspace checks with the
    # post-comment lookahead — it does not re-loop to the EOF check.
    # if (result == ACCEPT && comment_condition && lookahead != ',' && != '=')
    jmp_if_result_eq 2 asi_close_check                 # ACCEPT -> comment_condition check
    jmp_if_state_eq 1 asi_close_check                   # comment_condition -> lookahead checks
    jmp_if_eq ',' asi_accept                            # ',' -> continue checks
    jmp_if_eq '=' asi_accept                            # '=' -> continue checks
    jmp asi_close_check
asi_close_check:
    jmp_if_eq '}' asi_after_close                       # '}' -> accept
    jmp asi_accept
asi_after_close:
    jmp_if_eq '\n' asi_nl_2028                          # newline -> after-newline handling
    jmp asi_after_nl
asi_nl_2028:
    jmp_if_eq 0x2028 asi_nl_2029
    jmp asi_after_nl
asi_nl_2029:
    jmp_if_eq 0x2029 asi_ws_check
    jmp asi_after_nl
asi_ws_check:
    jmp_if_ws asi_ws_skip                               # whitespace -> skip and re-loop
    skip
    jmp asi_loop
asi_ws_skip:
    jmp asi_reject
asi_after_nl:
    skip
    call ws_true
    jmp_if_result_eq 0 asi_switch                       # REJECT -> reject
    jmp asi_reject
asi_switch:
    # for the forbidding lookaheads: == c -> reject
    jmp_if_eq '`' asi_s1
    jmp asi_reject
asi_s1:
    jmp_if_eq ',' asi_s2
    jmp asi_reject
asi_s2:
    jmp_if_eq ':' asi_s3
    jmp asi_reject
asi_s3:
    jmp_if_eq ';' asi_s4
    jmp asi_reject
asi_s4:
    jmp_if_eq '*' asi_s5
    jmp asi_reject
asi_s5:
    jmp_if_eq '%' asi_s6
    jmp asi_reject
asi_s6:
    jmp_if_eq '>' asi_s7
    jmp asi_reject
asi_s7:
    jmp_if_eq '<' asi_s8
    jmp asi_reject
asi_s8:
    jmp_if_eq '=' asi_s9
    jmp asi_reject
asi_s9:
    jmp_if_eq '[' asi_s10
    jmp asi_reject
asi_s10:
    jmp_if_eq '(' asi_s11
    jmp asi_reject
asi_s11:
    jmp_if_eq '?' asi_s12
    jmp asi_reject
asi_s12:
    jmp_if_eq '^' asi_s13
    jmp asi_reject
asi_s13:
    jmp_if_eq '|' asi_s14
    jmp asi_reject
asi_s14:
    jmp_if_eq '&' asi_s15
    jmp asi_reject
asi_s15:
    jmp_if_eq '/' asi_s16
    jmp asi_reject
asi_s16:
    jmp_if_eq '.' asi_plus_check                        # '.' -> digit check
    jmp asi_dot
asi_dot:
    skip
    jmp_if_digit asi_dot_reject                         # digit -> accept
    jmp asi_accept
asi_dot_reject:
    fail
asi_plus_check:
    jmp_if_eq '+' asi_minus_check                       # '+' -> '++' check
    jmp asi_plus
asi_plus:
    skip
    jmp_if_eq '+' asi_plus_reject                       # '++' -> accept
    jmp asi_accept
asi_plus_reject:
    fail
asi_minus_check:
    jmp_if_eq '-' asi_bang_check                        # '-' -> '--' check
    jmp asi_minus
asi_minus:
    skip
    jmp_if_eq '-' asi_minus_reject
    jmp asi_accept
asi_minus_reject:
    fail
asi_bang_check:
    jmp_if_eq '!' asi_i_check                           # '!' -> '!=' check
    jmp asi_bang
asi_bang:
    skip
    jmp_if_eq '=' asi_bang_reject                       # '!=' -> reject
    fail
asi_bang_reject:
    jmp asi_accept
asi_i_check:
    jmp_if_eq 'i' asi_accept                            # 'i' -> "in"/"instanceof" check
    jmp asi_i
asi_i:
    skip
    jmp_if_ne 'n' asi_i_next                            # not "in" -> accept
    jmp asi_accept
asi_i_next:
    skip
    jmp_if_alpha asi_i_reject                           # "in" + alpha -> check "instanceof"
    jmp asi_inst_of
asi_i_reject:
    fail
asi_inst_of:
    jmp_if_ne 's' asi_inst_1                            # "stanceof" mismatch -> accept
    jmp asi_accept
asi_inst_1:
    skip
    jmp_if_ne 't' asi_inst_2
    jmp asi_accept
asi_inst_2:
    skip
    jmp_if_ne 'a' asi_inst_3
    jmp asi_accept
asi_inst_3:
    skip
    jmp_if_ne 'n' asi_inst_4
    jmp asi_accept
asi_inst_4:
    skip
    jmp_if_ne 'c' asi_inst_5
    jmp asi_accept
asi_inst_5:
    skip
    jmp_if_ne 'e' asi_inst_6
    jmp asi_accept
asi_inst_6:
    skip
    jmp_if_ne 'o' asi_inst_7
    jmp asi_accept
asi_inst_7:
    skip
    jmp_if_ne 'f' asi_inst_8
    jmp asi_accept
asi_inst_8:
    skip
    jmp_if_alpha asi_inst_reject                        # "instanceof" + alpha -> accept
    jmp asi_accept
asi_inst_reject:
    fail
asi_accept:
    set_result 1
    ret
asi_reject:
    set_result 0
    ret

# ---------------------------------------------------------------
# scan_ternary_qmark:
#   skip ws; if ('?') { advance; if ('?') return false;
#     mark_end; if ('.') { advance; if (digit) return true; return false; }
#     return true; } return false;
# ---------------------------------------------------------------
scan_ternary_qmark:
    jmp_if_ws ternary_check                             # whitespace -> skip
    skip
    jmp scan_ternary_qmark
ternary_check:
    jmp_if_eq '?' ternary_reject                        # '?' -> continue; else reject
    jmp ternary_q
ternary_q:
    advance
    jmp_if_eq '?' ternary_mark                          # "??" -> reject
    jmp ternary_reject
ternary_mark:
    mark_end
    jmp_if_eq '.' ternary_emit                          # '.' -> digit check
    advance
    jmp_if_digit ternary_fail                           # digit -> accept
    jmp ternary_emit
ternary_emit:
    set_result 1
    ret
ternary_reject:
    set_result 0
    ret
ternary_fail:
    set_result 0
    ret

# ---------------------------------------------------------------
# scan_html_comment:
#   skip ws; if ('<') match "<!--" else if ('-') match "-->"
#   else reject; consume to newline/EOF; mark_end; accept.
# ---------------------------------------------------------------
scan_html_comment:
    jmp_if_ws html_check                                # whitespace -> skip
    skip
    jmp scan_html_comment
html_check:
    jmp_if_eq '<' html_end_check                        # '<' -> match "<!--"
    jmp html_start
html_start:
    push_bytes "<!--"
    span 4 html_reject
    jmp html_content
html_end_check:
    jmp_if_eq '-' html_reject                           # '-' -> match "-->"
    jmp html_end
html_end:
    push_bytes "-->"
    span 3 html_reject
    jmp html_content
html_content:
    jmp_if_eq 0 html_nl_check                           # EOF -> mark_end + accept
    jmp html_done
html_nl_check:
    jmp_if_eq '\n' html_2028_check                      # '\n' -> mark_end + accept
    jmp html_done
html_2028_check:
    jmp_if_eq 0x2028 html_2029_check
    jmp html_done
html_2029_check:
    jmp_if_eq 0x2029 html_other                         # U+2029 -> mark_end + accept
    jmp html_done
html_other:
    advance
    jmp html_content
html_done:
    mark_end
    set_result 1
    ret
html_reject:
    set_result 0
    ret