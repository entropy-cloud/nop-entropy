# Hand translation of tree-sitter-typescript common/scanner.h (MIT) into the
# external-scanner DSL compiled by io.nop.treesitter.scanner.ScannerCompiler.
# Dialect-agnostic: shared verbatim by the typescript and tsx grammars (the
# per-dialect external symbol maps only re-map the token ordinals).
#
# Branch convention (matches the VM): "jmp_if_X <v> <label>" falls through when
# the condition X(v) HOLDS and jumps to <label> otherwise — the fall-through is
# the matched/continue body, the label is the else path. Structure follows the
# proven JavaScript scanner DSL (same subroutines, JS corpus 33/33) with the
# TypeScript differences: FUNCTION_SIGNATURE_AUTOMATIC_SEMICOLON handling, the
# standalone ternary branch, the '}'+':' ternary disambiguation, the simpler
# whitespace/comment scan (no newline tristate), ternary '?.'/'??'/':'/digit
# tails, and U+2028/U+2029 in the closing-comment scan.
#
# Register usage: flag = *scanned_comment (ws out-param, read by the
# dispatcher) / has_content (template) / at_newline (jsx); result = subroutine
# return value (repurposed as saw_text inside scan_jsx_text). Subroutines
# return via set_result + ret; the dispatcher emits.

token automatic_semicolon
token template_chars
token ternary_qmark
token html_comment
token logical_or
token escape_sequence
token regex_pattern
token jsx_text
token function_signature_automatic_semicolon
token error_recovery

# ---------------------------------------------------------------
# external_scanner_scan — the dispatcher.
# ---------------------------------------------------------------
dispatcher:
    jmp_if_valid template_chars d_jsx                  # TC valid -> ASI conflict gate
    jmp_if_valid automatic_semicolon d_tc_scan         # ASI valid -> reject
    fail
d_tc_scan:
    call scan_template_chars
    jmp_if_result_eq 1 d_tc_failed                     # accepted -> emit
    emit template_chars
d_tc_failed:
    fail
d_jsx:
    jmp_if_valid jsx_text d_asi                        # jsx valid -> scan
    call scan_jsx_text
    jmp_if_result_eq 1 d_asi                           # saw_text -> emit
    emit jsx_text
d_asi:
    jmp_if_valid automatic_semicolon d_asi_fs          # ASI valid -> run
    jmp d_asi_run
d_asi_fs:
    jmp_if_valid function_signature_automatic_semicolon d_ternary_only   # fs valid -> run
    jmp d_asi_run
d_asi_run:
    set_flag 0
    call scan_automatic_semicolon
    jmp_if_result_eq 1 d_asi_fallback                  # ret -> emit ASI
    emit automatic_semicolon
d_asi_fallback:
    jmp_if_flag_eq 1 d_asi_tern_gate                   # scanned_comment -> no fallback
    jmp d_asi_fail
d_asi_tern_gate:
    jmp_if_valid ternary_qmark d_asi_fail              # ternary valid -> '?' check
    jmp d_asi_q_ck
d_asi_q_ck:
    jmp_if_eq '?' d_asi_fail                           # '?' -> ternary scan
    call scan_ternary_qmark
    jmp_if_result_eq 1 d_asi_fail                      # accepted -> emit
    emit ternary_qmark
d_asi_fail:
    fail
d_ternary_only:
    jmp_if_valid ternary_qmark d_html                  # ternary valid -> scan
    call scan_ternary_qmark
    jmp_if_result_eq 1 d_tern_failed                   # accepted -> emit
    emit ternary_qmark
d_tern_failed:
    fail
d_html:
    jmp_if_valid html_comment d_html_failed            # html valid -> || gate
    jmp_if_valid logical_or d_html_gate2               # || valid -> reject
    fail
d_html_gate2:
    jmp_if_valid escape_sequence d_html_gate3          # esc valid -> reject
    fail
d_html_gate3:
    jmp_if_valid regex_pattern d_html_call             # regex valid -> reject
    fail
d_html_call:
    call scan_closing_comment
    jmp_if_result_eq 1 d_html_failed                   # accepted -> emit
    emit html_comment
d_html_failed:
    fail


# ---------------------------------------------------------------
# scan_template_chars / scan_jsx_text: JS-verbatim structure (identical C code).
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
# scan_whitespace_and_comments: TS variant (no newline tristate; flag = *scanned_comment only).
# ---------------------------------------------------------------
scan_whitespace_and_comments:
ws_loop:
    jmp_if_ws ws_slash_check                           # ws -> skip
    skip
    jmp ws_loop
ws_slash_check:
    jmp_if_eq '/' ws_accept                            # '/' -> comment body
    jmp ws_slash_body
ws_slash_body:
    skip
    jmp_if_eq '/' ws_star_check                        # "//" -> line comment
    jmp ws_line
ws_line:
    skip
ws_line_loop:
    jmp_if_eq 0 ws_line_nl                             # EOF -> line done
    jmp ws_line_done
ws_line_nl:
    jmp_if_eq '\n' ws_line_skip                        # '\n' -> line done; else skip
    jmp ws_line_done
ws_line_skip:
    skip
    jmp ws_line_loop
ws_line_done:
    set_flag 1
    jmp ws_loop
ws_star_check:
    jmp_if_eq '*' ws_reject                            # "/*" -> block comment
    jmp ws_block
ws_block:
    skip
ws_block_loop:
    jmp_if_eq 0 ws_block_star_ck                       # EOF -> outer loop; else star check
    jmp ws_block_after
ws_block_star_ck:
    jmp_if_eq '*' ws_block_other                       # '*' -> maybe "*/"
    skip
    jmp_if_eq '/' ws_block_cont                        # "*/" -> close
    jmp ws_block_close
ws_block_cont:
    jmp ws_block_loop
ws_block_other:
    skip
    jmp ws_block_loop
ws_block_close:
    skip
    jmp ws_loop
ws_block_after:
    jmp ws_loop
ws_accept:
    set_result 1
    ret
ws_reject:
    set_result 0
    ret


# ---------------------------------------------------------------
# scan_automatic_semicolon: TS variant ('}'+':' disambiguation via LOGICAL_OR, function-signature '{' gate).
# ---------------------------------------------------------------
scan_automatic_semicolon:
    mark_end
asi_loop:
    jmp_if_eq 0 asi_rbrace_ck                          # EOF -> accept
    jmp asi_accept
asi_rbrace_ck:
    jmp_if_eq '}' asi_ws_ck                            # '}' -> whitespace walk
    jmp asi_rbrace
asi_rbrace:
    skip
asi_rbrace_ws:
    jmp_if_ws asi_rbrace_colon                         # ws -> keep skipping
    jmp asi_rbrace_eat
asi_rbrace_eat:
    skip
    jmp asi_rbrace_ws
asi_rbrace_colon:
    jmp_if_eq ':' asi_accept                           # ':' -> ternary disambiguation
    jmp asi_rbrace_tern
asi_rbrace_tern:
    jmp_if_valid logical_or asi_reject                 # || valid -> accept (ternary ctx)
    jmp asi_accept
asi_ws_ck:
    jmp_if_ws asi_reject                               # ws -> newline check
    jmp asi_nl_ck
asi_nl_ck:
    jmp_if_eq '\n' asi_skip                            # '\n' -> break to ws scan
    jmp asi_break
asi_skip:
    skip
    jmp asi_loop
asi_break:
    skip
    call scan_whitespace_and_comments
    jmp_if_result_eq 1 asi_reject                      # not ok -> reject; ok -> switch
    jmp asi_switch
asi_switch:
    jmp_if_eq '`' asi_s1
    jmp asi_reject
asi_s1:
    jmp_if_eq ',' asi_s2
    jmp asi_reject
asi_s2:
    jmp_if_eq '.' asi_s3
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
    jmp_if_eq '?' asi_s10
    jmp asi_reject
asi_s10:
    jmp_if_eq '^' asi_s11
    jmp asi_reject
asi_s11:
    jmp_if_eq '|' asi_s12
    jmp asi_reject
asi_s12:
    jmp_if_eq '&' asi_s13
    jmp asi_reject
asi_s13:
    jmp_if_eq '/' asi_s14
    jmp asi_reject
asi_s14:
    jmp_if_eq ':' asi_s15
    jmp asi_reject
asi_s15:
    jmp_if_eq '{' asi_paren_ck                         # '{' -> function-signature gate
    jmp asi_lbrace
asi_lbrace:
    jmp_if_valid function_signature_automatic_semicolon asi_accept   # fs valid -> reject
    jmp asi_reject
asi_paren_ck:
    jmp_if_eq '(' asi_lbrack_ck                        # '(' -> LOGICAL_OR gate
    jmp asi_lparen
asi_lparen:
    jmp_if_valid logical_or asi_accept                 # || valid -> reject
    jmp asi_reject
asi_lbrack_ck:
    jmp_if_eq '[' asi_plus_ck                          # '[' -> LOGICAL_OR gate
    jmp asi_lbrack
asi_lbrack:
    jmp_if_valid logical_or asi_accept                 # || valid -> reject
    jmp asi_reject
asi_plus_ck:
    jmp_if_eq '+' asi_minus_ck                         # '+' -> '++' check
    jmp asi_plus
asi_plus:
    advance
    jmp_if_eq '+' asi_reject                           # '++' -> accept (unary)
    jmp asi_accept
asi_minus_ck:
    jmp_if_eq '-' asi_bang_ck                          # '-' -> '--' check
    jmp asi_minus
asi_minus:
    advance
    jmp_if_eq '-' asi_reject                           # '--' -> accept (unary)
    jmp asi_accept
asi_bang_ck:
    jmp_if_eq '!' asi_i_ck                             # '!' -> '!=' check
    jmp asi_bang
asi_bang:
    advance
    jmp_if_eq '=' asi_accept                           # '!=' -> reject
    jmp asi_reject
asi_i_ck:
    jmp_if_eq 'i' asi_accept                           # 'i' -> "in"/"instanceof" walk
    jmp asi_i
asi_i:
    skip
    jmp_if_ne 'n' asi_i_n                              # not "in*" -> accept
    jmp asi_accept
asi_i_n:
    skip
    jmp_if_alpha asi_reject                            # "in"+alpha -> instanceof walk
    jmp asi_i_walk
asi_i_walk:
    jmp_if_ne 's' asi_i_w1                             # "stanceof" mismatch -> accept
    jmp asi_accept
asi_i_w1:
    skip
    jmp_if_ne 't' asi_i_w2
    jmp asi_accept
asi_i_w2:
    skip
    jmp_if_ne 'a' asi_i_w3
    jmp asi_accept
asi_i_w3:
    skip
    jmp_if_ne 'n' asi_i_w4
    jmp asi_accept
asi_i_w4:
    skip
    jmp_if_ne 'c' asi_i_w5
    jmp asi_accept
asi_i_w5:
    skip
    jmp_if_ne 'e' asi_i_w6
    jmp asi_accept
asi_i_w6:
    skip
    jmp_if_ne 'o' asi_i_w7
    jmp asi_accept
asi_i_w7:
    skip
    jmp_if_ne 'f' asi_i_w8
    jmp asi_accept
asi_i_w8:
    skip
    jmp_if_alpha asi_reject                            # "instanceof"+alpha -> accept
    jmp asi_accept
asi_accept:
    set_result 1
    ret
asi_reject:
    set_result 0
    ret


# ---------------------------------------------------------------
# scan_ternary_qmark: TS variant ('?.', '??', ':', ')', ',', '.' digit tails).
# ---------------------------------------------------------------
scan_ternary_qmark:
tern_ws:
    jmp_if_ws tern_check                               # ws -> skip
    skip
    jmp tern_ws
tern_check:
    jmp_if_eq '?' tern_reject                          # '?' -> advance
    jmp tern_adv
tern_adv:
    advance
    jmp_if_eq '?' tern_dq                              # "??" -> reject
    jmp tern_reject
tern_dq:
    jmp_if_eq '.' tern_mark                            # "?." -> reject
    jmp tern_reject
tern_mark:
    mark_end
tern_ws2:
    jmp_if_ws tern_tail                                # ws -> advance
    advance
    jmp tern_ws2
tern_tail:
    jmp_if_eq ':' tern_t2                              # ':' -> reject (optional param)
    jmp tern_reject
tern_t2:
    jmp_if_eq ')' tern_t3
    jmp tern_reject
tern_t3:
    jmp_if_eq ',' tern_t4
    jmp tern_reject
tern_t4:
    jmp_if_eq '.' tern_accept                          # '.' -> digit check
    jmp tern_dot
tern_dot:
    advance
    jmp_if_digit tern_reject                           # digit -> accept
    jmp tern_accept
tern_accept:
    set_result 1
    ret
tern_reject:
    set_result 0
    ret


# ---------------------------------------------------------------
# scan_closing_comment: TS variant (U+2028/U+2029 handling).
# ---------------------------------------------------------------
scan_closing_comment:
cc_ws:
    jmp_if_ws cc_2028_ck                               # ws -> skip
    skip
    jmp cc_ws
cc_2028_ck:
    jmp_if_eq 0x2028 cc_2029_ck                        # U+2028 -> skip
    skip
    jmp cc_ws
cc_2029_ck:
    jmp_if_eq 0x2029 cc_lt_ck                          # U+2029 -> skip
    skip
    jmp cc_ws
cc_lt_ck:
    jmp_if_eq '<' cc_dash_ck                           # '<' -> match "<!--"
    jmp cc_open
cc_dash_ck:
    jmp_if_eq '-' cc_reject                            # '-' -> match "-->"
    jmp cc_close
cc_open:
    push_bytes "<!--"
    span 4 cc_reject
    jmp cc_content
cc_close:
    push_bytes "-->"
    span 3 cc_reject
    jmp cc_content
cc_content:
    jmp_if_eq 0 cc_nl_ck                               # EOF -> done
    jmp cc_done
cc_nl_ck:
    jmp_if_eq '\n' cc_2028_ck2                         # '\n' -> done
    jmp cc_done
cc_2028_ck2:
    jmp_if_eq 0x2028 cc_2029_ck2
    jmp cc_done
cc_2029_ck2:
    jmp_if_eq 0x2029 cc_adv
    jmp cc_done
cc_adv:
    advance
    jmp cc_content
cc_done:
    mark_end
    set_result 1
    ret
cc_reject:
    set_result 0
    ret
