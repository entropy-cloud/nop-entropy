/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

lexer grammar Literals;

import Alphabet, Symbol;

// WI7 Phase 3（plan 26；roadmap EC ②）：NULLS 保留词（fragment 组合=大小写不敏感，同
// Keyword.g4 惯例）。⚠️ 执行期实测修订：plan 设想的"NULLS 走 unreservedWord_ 加词=零
// lexer 改动"不成立——无 lexer 规则的隐式 token 无产生通路、永不可达（BUG-15 characterSet_
// 的 STRING 隐式 token 同形态实测），显式 token 是 NULLS 文本唯一可匹配通路。本文件是
// 最后 import 的 lexer grammar；NULLS 须定义在 IDENTIFIER_ 之前（lexer 优先级=定义序，
// 否则 "nulls" 文本被 IDENTIFIER_ 抢走）。新增 1 个 token、其后 5 个 Literals token 序号
// 顺延 +1（无任何 token 删除——"不删除 lexer token"红线不触；token 均按名引用，.tokens/
// EqlLexer 同批重生成）。
NULLS
    : N U L L S
    ;

// WI7b Phase 2（plan 37；roadmap EC ①）：ROLLUP/CUBE 保留词（fragment 组合=大小写不敏感），
// 供 sqlGroupByItem 的 grouping 操作符备选匹配（ⓒ-A 裁定：新增 AST 类 SqlGroupingOp +
// sqlGroupByItem 新备选前置，ai-dev/design/eql-cross-db-compat/2026-10-04-wi7b-phase0-adjudications.md
// §3）。NULLS 三件套先例：显式 token 定义在 IDENTIFIER_ 之前（无 lexer 规则的隐式 token 永不可达）+
// BaseRule.g4 unreservedWord_ 双登记（保住列名/别名 rollup/cube 可解析）+ 三份必测语料 0 命中。
// 新增 2 个 token、其后 Literals token 序号顺延 +2（零 token 删除——"不删除 lexer token"红线不触）。
ROLLUP
    : R O L L U P
    ;

CUBE
    : C U B E
    ;

NOWAIT
    : N O W A I T
    ;

// ⚠️ 执行期实测修订（plan 38 Phase 2）：裸 SKIP 触发 ANTLR error(159) "cannot declare a rule
// with reserved name SKIP"（skip 是 lexer 命令保留名）。尾下划线形态（EQ_/ASTERISK_ 同族）
// 绕开保留名；NOWAIT/LOCKED 非保留名、保持关键字裸名惯例。
SKIP_
    : S K I P
    ;

LOCKED
    : L O C K E D
    ;

IDENTIFIER_
    : [A-Za-z_$][A-Za-z_$0-9]*
    |  DQ_ ~('\r'|'"'|'\n')+ DQ_
    ;

STRING_
    : (SQ_ ('\\'. | '\'\'' | ~('\'' | '\\'))* SQ_)
    ;

NUMBER_
    : MINUS_? INT_? DOT_? INT_ (E (PLUS_ | MINUS_)? INT_)?
    ;

HEX_DIGIT_
    : '0x' HEX_+ | 'X' SQ_ HEX_+ SQ_
    ;

BIT_NUM_
    : '0b' ('0' | '1')+ | B SQ_ ('0' | '1')+ SQ_
    ;

fragment INT_
    : [0-9]+
    ;

fragment HEX_
    : [0-9a-fA-F]
    ;
