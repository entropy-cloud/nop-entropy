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
