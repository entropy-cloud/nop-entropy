package io.nop.jq.jq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ported from jq official test suite (jq.test).
 * Each test: jq expression -> input -> expected output.
 * Tests actual execution using the AST-based execution engine.
 */
class TestJqOfficial {

    static Stream<Arguments> jqTests() {
        return Stream.of(
            Arguments.of(
                "true",
                "null",
                "true"
            ),
            Arguments.of(
                "false",
                "null",
                "false"
            ),
            Arguments.of(
                "null",
                "42",
                "null"
            ),
            Arguments.of(
                "1",
                "null",
                "1"
            ),
            Arguments.of(
                "-1",
                "null",
                "-1"
            ),
            Arguments.of(
                "{}",
                "null",
                "{}"
            ),
            Arguments.of(
                "[]",
                "null",
                "[]"
            ),
            Arguments.of(
                "{x:-1},{x:-.},{x:-.|abs}",
                "1",
                "{\"x\":-1}"
            ),
            Arguments.of(
                "{\"x\":-1}",
                "{\"x\":1}",
                ""
            ),
            Arguments.of(
                ".",
                "﻿\"byte order mark\"",
                "\"byte order mark\""
            ),
            Arguments.of(
                "\"Aa\\r\\n\\t\\b\\f\\u03bc\"",
                "null",
                "\"Aa\\u000d\\u000a\\u0009\\u0008\\u000c\\u03bc\""
            ),
            Arguments.of(
                ".",
                "\"Aa\\r\\n\\t\\b\\f\\u03bc\"",
                "\"Aa\\u000d\\u000a\\u0009\\u0008\\u000c\\u03bc\""
            ),
            Arguments.of(
                "    \"u\\vw\"",
                "      ^^",
                ""
            ),
            Arguments.of(
                "\"inter\\(\"pol\" + \"ation\")\"",
                "null",
                "\"interpolation\""
            ),
            Arguments.of(
                "@text,@json,([1,.]|@csv,@tsv),@html,(@uri|.,@urid),@sh,(@base64|.,@base64d)",
                "\"!()<>&'\\\"\\t\"",
                "\"!()<>&'\\\"\\t\""
            ),
            Arguments.of(
                "\"\\\"!()<>&'\\\\\\\"\\\\t\\\"\"",
                "\"1,\\\"!()<>&'\\\"\\\"\\t\\\"\"",
                "\"1\\t!()<>&'\\\"\\\\t\""
            ),
            Arguments.of(
                "\"!()&lt;&gt;&amp;&apos;&quot;\\t\"",
                "\"%21%28%29%3C%3E%26%27%22%09\"",
                "\"!()<>&'\\\"\\t\""
            ),
            Arguments.of(
                "\"'!()<>&'\\\\''\\\"\\t'\"",
                "\"ISgpPD4mJyIJ\"",
                "\"!()<>&'\\\"\\t\""
            ),
            Arguments.of(
                "@base64",
                "\"foóbar\\n\"",
                "\"Zm/Ds2Jhcgo=\""
            ),
            Arguments.of(
                "@base64d",
                "\"Zm/Ds2Jhcgo=\"",
                "\"foóbar\\n\""
            ),
            Arguments.of(
                "@uri",
                "\"\\u03bc\"",
                "\"%CE%BC\""
            ),
            Arguments.of(
                "@urid",
                "\"%CE%BC\"",
                "\"\\u03bc\""
            ),
            Arguments.of(
                "@html \"<b>\\(.)</b>\"",
                "\"<script>hax</script>\"",
                "\"<b>&lt;script&gt;hax&lt;/script&gt;</b>\""
            ),
            Arguments.of(
                "[.[]|tojson|fromjson]",
                "[\"foo\", 1, [\"a\", 1, \"b\", 2, {\"foo\":\"bar\"}]]",
                "[\"foo\",1,[\"a\",1,\"b\",2,{\"foo\":\"bar\"}]]"
            ),
            Arguments.of(
                "{a: 1}",
                "null",
                "{\"a\":1}"
            ),
            Arguments.of(
                "{a,b,(.d):.a,e:.b}",
                "{\"a\":1, \"b\":2, \"c\":3, \"d\":\"c\"}",
                "{\"a\":1, \"b\":2, \"c\":1, \"e\":2}"
            ),
            Arguments.of(
                "{\"a\",b,\"a$\\(1+1)\"}",
                "{\"a\":1, \"b\":2, \"c\":3, \"a$2\":4}",
                "{\"a\":1, \"b\":2, \"a$2\":4}"
            ),
            Arguments.of(
                "    {(0):1}",
                "      ^",
                ""
            ),
            Arguments.of(
                "    {1+2:3}",
                "     ^^^",
                ""
            ),
            Arguments.of(
                "    {non_const:., (0):1}",
                "                   ^",
                ""
            ),
            Arguments.of(
                ".foo",
                "{\"foo\": 42, \"bar\": 43}",
                "42"
            ),
            Arguments.of(
                ".foo | .bar",
                "{\"foo\": {\"bar\": 42}, \"bar\": \"badvalue\"}",
                "42"
            ),
            Arguments.of(
                ".foo.bar",
                "{\"foo\": {\"bar\": 42}, \"bar\": \"badvalue\"}",
                "42"
            ),
            Arguments.of(
                ".foo_bar",
                "{\"foo_bar\": 2}",
                "2"
            ),
            Arguments.of(
                ".[\"foo\"].bar",
                "{\"foo\": {\"bar\": 42}, \"bar\": \"badvalue\"}",
                "42"
            ),
            Arguments.of(
                ".\"foo\".\"bar\"",
                "{\"foo\": {\"bar\": 20}}",
                "20"
            ),
            Arguments.of(
                ".e0, .E1, .E-1, .E+1",
                "{\"e0\": 1, \"E1\": 2, \"E\": 3}",
                "1"
            ),
            Arguments.of(
                "2",
                "2",
                "4"
            ),
            Arguments.of(
                "[.[]|.foo?]",
                "[1,[2],{\"foo\":3,\"bar\":4},{},{\"foo\":5}]",
                "[3,null,5]"
            ),
            Arguments.of(
                "[.[]|.foo?.bar?]",
                "[1,[2],[],{\"foo\":3},{\"foo\":{\"bar\":4}},{}]",
                "[4,null]"
            ),
            Arguments.of(
                "[..]",
                "[1,[[2]],{ \"a\":[1]}]",
                "[[1,[[2]],{\"a\":[1]}],1,[[2]],[2],2,{\"a\":[1]},[1],1]"
            ),
            Arguments.of(
                "[.[]|.[]?]",
                "[1,null,[],[1,[2,[[3]]]],[{}],[{\"a\":[1,[2]]}]]",
                "[1,[2,[[3]]],{},{\"a\":[1,[2]]}]"
            ),
            Arguments.of(
                "[.[]|.[1:3]?]",
                "[1,null,true,false,\"abcdef\",{},{\"a\":1,\"b\":2},[],[1,2,3,4,5],[1,2]]",
                "[null,\"bc\",[],[2,3],[2]]"
            ),
            Arguments.of(
                "map(try .a[] catch ., try .a.[] catch ., .a[]?, .a.[]?)",
                "[{\"a\": [1,2]}, {\"a\": 123}]",
                "[1,2,1,2,1,2,1,2,\"Cannot iterate over number (123)\",\"Cannot iterate over number (123)\"]"
            ),
            Arguments.of(
                "try [\"OK\", (.[] | error)] catch [\"KO\", .]",
                "{\"a\":[\"b\"],\"c\":[\"d\"]}",
                "[\"KO\",[\"b\"]]"
            ),
            Arguments.of(
                "try (.foo[-1] = 0) catch .",
                "null",
                "\"Out of bounds negative array index\""
            ),
            Arguments.of(
                "try (.foo[-2] = 0) catch .",
                "null",
                "\"Out of bounds negative array index\""
            ),
            Arguments.of(
                ".[-1] = 5",
                "[0,1,2]",
                "[0,1,5]"
            ),
            Arguments.of(
                ".[-2] = 5",
                "[0,1,2]",
                "[0,5,2]"
            ),
            Arguments.of(
                "try (.[999999999] = 0) catch .",
                "null",
                "\"Array index too large\""
            ),
            Arguments.of(
                ".[]",
                "[1,2,3]",
                "1"
            ),
            Arguments.of(
                "2",
                "3",
                ""
            ),
            Arguments.of(
                "1,1",
                "[]",
                "1"
            ),
            Arguments.of(
                "1",
                "",
                "1,."
            ),
            Arguments.of(
                "[]",
                "1",
                "[]"
            ),
            Arguments.of(
                "[.]",
                "[2]",
                "[[2]]"
            ),
            Arguments.of(
                "[[2]]",
                "[3]",
                "[[2]]"
            ),
            Arguments.of(
                "[{}]",
                "[2]",
                "[{}]"
            ),
            Arguments.of(
                "[.[]]",
                "[\"a\"]",
                "[\"a\"]"
            ),
            Arguments.of(
                "[(.,1),((.,.[]),(2,3))]",
                "[\"a\",\"b\"]",
                "[[\"a\",\"b\"],1,[\"a\",\"b\"],\"a\",\"b\",2,3]"
            ),
            Arguments.of(
                "[([5,5][]),.,.[]]",
                "[1,2,3]",
                "[5,5,[1,2,3],1,2,3]"
            ),
            Arguments.of(
                "{x: (1,2)},{x:3} | .x",
                "null",
                "1"
            ),
            Arguments.of(
                "2",
                "3",
                ""
            ),
            Arguments.of(
                "[.[-4,-3,-2,-1,0,1,2,3]]",
                "[1,2,3]",
                "[null,1,2,3,1,2,3,null]"
            ),
            Arguments.of(
                "[range(0;10)]",
                "null",
                "[0,1,2,3,4,5,6,7,8,9]"
            ),
            Arguments.of(
                "[range(0,1;3,4)]",
                "null",
                "[0,1,2, 0,1,2,3, 1,2, 1,2,3]"
            ),
            Arguments.of(
                "[range(0;10;3)]",
                "null",
                "[0,3,6,9]"
            ),
            Arguments.of(
                "[range(0;10;-1)]",
                "null",
                "[]"
            ),
            Arguments.of(
                "[range(0;-5;-1)]",
                "null",
                "[0,-1,-2,-3,-4]"
            ),
            Arguments.of(
                "[range(0,1;4,5;1,2)]",
                "null",
                "[0,1,2,3,0,2, 0,1,2,3,4,0,2,4, 1,2,3,1,3, 1,2,3,4,1,3]"
            ),
            Arguments.of(
                "[while(.<100; .*2)]",
                "1",
                "[1,2,4,8,16,32,64]"
            ),
            Arguments.of(
                "[(label $here | .[] | if .>1 then break $here else . end), \"hi!\"]",
                "[0,1,2]",
                "[0,1,\"hi!\"]"
            ),
            Arguments.of(
                "[(label $here | .[] | if .>1 then break $here else . end), \"hi!\"]",
                "[0,2,1]",
                "[0,\"hi!\"]"
            ),
            Arguments.of(
                "    . as $foo | break $foo",
                "                ^^^^^^^^^^",
                ""
            ),
            Arguments.of(
                "[.[]|[.,1]|until(.[0] < 1; [.[0] - 1, .[1] * .[0]])|.[1]]",
                "[1,2,3,4,5]",
                "[1,2,6,24,120]"
            ),
            Arguments.of(
                "[label $out | foreach .[] as $item ([3, null]; if .[0] < 1 then break $out else [.[0] -1, $item] end; .[1])]",
                "[11,22,33,44,55,66,77,88,99]",
                "[11,22,33]"
            ),
            Arguments.of(
                "[foreach range(5) as $item (0; $item)]",
                "null",
                "[0,1,2,3,4]"
            ),
            Arguments.of(
                "[foreach .[] as [$i, $j] (0; . + $i - $j)]",
                "[[2,1], [5,3], [6,4]]",
                "[1,3,5]"
            ),
            Arguments.of(
                "[foreach .[] as {a:$a} (0; . + $a; -.)]",
                "[{\"a\":1}, {\"b\":2}, {\"a\":3, \"b\":4}]",
                "[-1, -1, -4]"
            ),
            Arguments.of(
                "[-foreach -.[] as $x (0; . + $x)]",
                "[1,2,3]",
                "[1,3,6]"
            ),
            Arguments.of(
                "[foreach .[] / .[] as $i (0; . + $i)]",
                "[1,2]",
                "[1,3,3.5,4.5]"
            ),
            Arguments.of(
                "[foreach .[] as $x (0; . + $x) as $x | $x]",
                "[1,2,3]",
                "[1,3,6]"
            ),
            Arguments.of(
                "[limit(3; .[])]",
                "[11,22,33,44,55,66,77,88,99]",
                "[11,22,33]"
            ),
            Arguments.of(
                "[limit(0; error)]",
                "\"badness\"",
                "[]"
            ),
            Arguments.of(
                "[limit(1; 1, error)]",
                "\"badness\"",
                "[1]"
            ),
            Arguments.of(
                "try limit(-1; error) catch .",
                "null",
                "\"limit doesn't support negative count\""
            ),
            Arguments.of(
                "[skip(3; .[])]",
                "[1,2,3,4,5,6,7,8,9]",
                "[4,5,6,7,8,9]"
            ),
            Arguments.of(
                "[skip(0,2,3,4; .[])]",
                "[1,2,3]",
                "[1,2,3,3]"
            ),
            Arguments.of(
                "[skip(3; .[])]",
                "[]",
                "[]"
            ),
            Arguments.of(
                "try skip(-1; error) catch .",
                "null",
                "\"skip doesn't support negative count\""
            ),
            Arguments.of(
                "nth(1; 0,1,error(\"foo\"))",
                "null",
                "1"
            ),
            Arguments.of(
                "[first(range(.)), last(range(.))]",
                "10",
                "[0,9]"
            ),
            Arguments.of(
                "[first(range(.)), last(range(.))]",
                "0",
                "[]"
            ),
            Arguments.of(
                "[nth(0,5,9,10,15; range(.)), try nth(-1; range(.)) catch .]",
                "10",
                "[0,5,9,\"nth doesn't support negative indices\"]"
            ),
            Arguments.of(
                "first(1,error(\"foo\"))",
                "null",
                "1"
            ),
            Arguments.of(
                "[limit(5,7; range(9))]",
                "null",
                "[0,1,2,3,4,0,1,2,3,4,5,6]"
            ),
            Arguments.of(
                "[nth(5,7; range(9;0;-1))]",
                "null",
                "[4,2]"
            ),
            Arguments.of(
                "[range(0,1,2;4,3,2;2,3)]",
                "null",
                "[0,2,0,3,0,2,0,0,0,1,3,1,1,1,1,1,2,2,2,2]"
            ),
            Arguments.of(
                "[range(3,5)]",
                "null",
                "[0,1,2,0,1,2,3,4]"
            ),
            Arguments.of(
                "[(index(\",\",\"|\"), rindex(\",\",\"|\")), indices(\",\",\"|\")]",
                "\"a,b|c,d,e||f,g,h,|,|,i,j\"",
                "[1,3,22,19,[1,5,7,12,14,16,18,20,22],[3,9,10,17,19]]"
            ),
            Arguments.of(
                "join(\",\",\"/\")",
                "[\"a\",\"b\",\"c\",\"d\"]",
                "\"a,b,c,d\""
            ),
            Arguments.of(
                "\"a/b/c/d\"",
                "",
                "[.[]|join(\"a\")]"
            ),
            Arguments.of(
                "[[],[\"\"],[\"\",\"\"],[\"\",\"\",\"\"]]",
                "[\"\",\"\",\"a\",\"aa\"]",
                ""
            ),
            Arguments.of(
                "flatten(3,2,1)",
                "[0, [1], [[2]], [[[3]]]]",
                "[0,1,2,3]"
            ),
            Arguments.of(
                "[0,1,2,[3]]",
                "[0,1,[2],[[3]]]",
                ""
            ),
            Arguments.of(
                "[.[3:2], .[-5:4], .[:-2], .[-2:], .[3:3][1:], .[10:]]",
                "[0,1,2,3,4,5,6]",
                "[[], [2,3], [0,1,2,3,4], [5,6], [], []]"
            ),
            Arguments.of(
                "[.[3:2], .[-5:4], .[:-2], .[-2:], .[3:3][1:], .[10:]]",
                "\"abcdefghi\"",
                "[\"\",\"\",\"abcdefg\",\"hi\",\"\",\"\"]"
            ),
            Arguments.of(
                "del(.[2:4],.[0],.[-2:])",
                "[0,1,2,3,4,5,6,7]",
                "[1,4,5]"
            ),
            Arguments.of(
                ".[2:4] = ([], [\"a\",\"b\"], [\"a\",\"b\",\"c\"])",
                "[0,1,2,3,4,5,6,7]",
                "[0,1,4,5,6,7]"
            ),
            Arguments.of(
                "[0,1,\"a\",\"b\",4,5,6,7]",
                "[0,1,\"a\",\"b\",\"c\",4,5,6,7]",
                ""
            ),
            Arguments.of(
                "reduce range(65540;65536;-1) as $i ([]; .[$i] = $i)|.[65536:]",
                "null",
                "[null,65537,65538,65539,65540]"
            ),
            Arguments.of(
                "1 as $x | 2 as $y | [$x,$y,$x]",
                "null",
                "[1,2,1]"
            ),
            Arguments.of(
                "[1,2,3][] as $x | [[4,5,6,7][$x]]",
                "null",
                "[5]"
            ),
            Arguments.of(
                "[6]",
                "[7]",
                ""
            ),
            Arguments.of(
                "42 as $x | . | . | . + 432 | $x + 1",
                "34324",
                "43"
            ),
            Arguments.of(
                "1 + 2 as $x | -$x",
                "null",
                "-3"
            ),
            Arguments.of(
                "\"x\" as $x | \"a\"+\"y\" as $y | $x+\",\"+$y",
                "null",
                "\"x,ay\""
            ),
            Arguments.of(
                "1 as $x | [$x,$x,$x as $x | $x]",
                "null",
                "[1,1,1]"
            ),
            Arguments.of(
                "[1, {c:3, d:4}] as [$a, {c:$b, b:$c}] | $a, $b, $c",
                "null",
                "1"
            ),
            Arguments.of(
                "3",
                "null",
                ""
            ),
            Arguments.of(
                ". as {as: $kw, \"str\": $str, (\"e\"+\"x\"+\"p\"): $exp} | [$kw, $str, $exp]",
                "{\"as\": 1, \"str\": 2, \"exp\": 3}",
                "[1, 2, 3]"
            ),
            Arguments.of(
                ".[] as [$a, $b] | [$b, $a]",
                "[[1], [1, 2, 3]]",
                "[null, 1]"
            ),
            Arguments.of(
                "[2, 1]",
                "",
                ". as $i | . as [$i] | $i"
            ),
            Arguments.of(
                "[0]",
                "0",
                ""
            ),
            Arguments.of(
                ". as [$i] | . as $i | $i",
                "[0]",
                "[0]"
            ),
            Arguments.of(
                "    . as [] | null",
                "          ^",
                ""
            ),
            Arguments.of(
                "    . as {} | null",
                "          ^",
                ""
            ),
            Arguments.of(
                "    . as $foo | [$foo, $bar]",
                "                       ^^^^",
                ""
            ),
            Arguments.of(
                "    . as {(true):$foo} | $foo",
                "           ^^^^",
                ""
            ),
            Arguments.of(
                "1+1",
                "null",
                "2"
            ),
            Arguments.of(
                "1+1",
                "\"wtasdf\"",
                "2.0"
            ),
            Arguments.of(
                "2-1",
                "null",
                "1"
            ),
            Arguments.of(
                "2-(-1)",
                "null",
                "3"
            ),
            Arguments.of(
                "1e+0+0.001e3",
                "\"I wonder what this will be?\"",
                "20e-1"
            ),
            Arguments.of(
                ".+4",
                "15",
                "19.0"
            ),
            Arguments.of(
                ".+null",
                "{\"a\":42}",
                "{\"a\":42}"
            ),
            Arguments.of(
                "null+.",
                "null",
                "null"
            ),
            Arguments.of(
                ".a+.b",
                "{\"a\":42}",
                "42"
            ),
            Arguments.of(
                "[1,2,3] + [.]",
                "null",
                "[1,2,3,null]"
            ),
            Arguments.of(
                "{\"a\":1} + {\"b\":2} + {\"c\":3}",
                "\"asdfasdf\"",
                "{\"a\":1, \"b\":2, \"c\":3}"
            ),
            Arguments.of(
                "\"asdf\" + \"jkl;\" + . + . + .",
                "\"some string\"",
                "\"asdfjkl;some stringsome stringsome string\""
            ),
            Arguments.of(
                "\"\\u0000\\u0020\\u0000\" + .",
                "\"\\u0000\\u0020\\u0000\"",
                "\"\\u0000 \\u0000\\u0000 \\u0000\""
            ),
            Arguments.of(
                "42 - .",
                "11",
                "31"
            ),
            Arguments.of(
                "[1,2,3,4,1] - [.,3]",
                "1",
                "[2,4]"
            ),
            Arguments.of(
                "[-1 as $x | 1,$x]",
                "null",
                "[1,-1]"
            ),
            Arguments.of(
                "[10 * 20, 20 / .]",
                "4",
                "[200, 5]"
            ),
            Arguments.of(
                "1 + 2 * 2 + 10 / 2",
                "null",
                "10"
            ),
            Arguments.of(
                "[16 / 4 / 2, 16 / 4 * 2, 16 - 4 - 2, 16 - 4 + 2]",
                "null",
                "[2, 8, 10, 14]"
            ),
            Arguments.of(
                "1e-19 + 1e-20 - 5e-21",
                "null",
                "1.05e-19"
            ),
            Arguments.of(
                "1 / 1e-17",
                "null",
                "1e+17"
            ),
            Arguments.of(
                "9E999999999, 9999999999E999999990, 1E-999999999, 0.000000001E-999999990",
                "null",
                "9E+999999999"
            ),
            Arguments.of(
                "9.999999999E+999999999",
                "1E-999999999",
                "1E-999999999"
            ),
            Arguments.of(
                "5E500000000 > 5E-5000000000, 10000E500000000 > 10000E-5000000000",
                "null",
                "true"
            ),
            Arguments.of(
                "true",
                "",
                "# #2825"
            ),
            Arguments.of(
                "(1e999999999, 10e999999999) > (1e-1147483646, 0.1e-1147483646)",
                "null",
                "true"
            ),
            Arguments.of(
                "true",
                "true",
                "true"
            ),
            Arguments.of(
                "25 % 7",
                "null",
                "4"
            ),
            Arguments.of(
                "49732 % 472",
                "null",
                "172"
            ),
            Arguments.of(
                "[(infinite, -infinite) % (1, -1, infinite)]",
                "null",
                "[0,0,0,0,0,-1]"
            ),
            Arguments.of(
                "[nan % 1, 1 % nan | isnan]",
                "null",
                "[true,true]"
            ),
            Arguments.of(
                "1 + tonumber + (\"10\" | tonumber)",
                "4",
                "15"
            ),
            Arguments.of(
                "\"123\\u0000456\" | try tonumber catch .",
                "null",
                "\"string (\\\"123\\\\u0000456\\\") cannot be parsed as a number\""
            ),
            Arguments.of(
                "map(toboolean)",
                "[\"false\",\"true\",false,true]",
                "[false,true,false,true]"
            ),
            Arguments.of(
                ".[] | try toboolean catch .",
                "[null,0,\"tru\",\"truee\",\"fals\",\"falsee\",[],{}]",
                "\"null (null) cannot be parsed as a boolean\""
            ),
            Arguments.of(
                "\"number (0) cannot be parsed as a boolean\"",
                "\"string (\\\"tru\\\") cannot be parsed as a boolean\"",
                "\"string (\\\"truee\\\") cannot be parsed as a boolean\""
            ),
            Arguments.of(
                "\"string (\\\"fals\\\") cannot be parsed as a boolean\"",
                "\"string (\\\"falsee\\\") cannot be parsed as a boolean\"",
                "\"array ([]) cannot be parsed as a boolean\""
            ),
            Arguments.of(
                "\"object ({}) cannot be parsed as a boolean\"",
                "",
                "\"true\\u0000x\", \"false\\u0000\" | try toboolean catch ."
            ),
            Arguments.of(
                "null",
                "\"string (\\\"true\\\\u0000x\\\") cannot be parsed as a boolean\"",
                "\"string (\\\"false\\\\u0000\\\") cannot be parsed as a boolean\""
            ),
            Arguments.of(
                "[{\"a\":42},.object,10,.num,false,true,null,\"b\",[1,4]] | .[] as $x | [$x == .[]]",
                "{\"object\": {\"a\":42}, \"num\":10.0}",
                "[true,  true,  false, false, false, false, false, false, false]"
            ),
            Arguments.of(
                "[true,  true,  false, false, false, false, false, false, false]",
                "[false, false, true,  true,  false, false, false, false, false]",
                "[false, false, true,  true,  false, false, false, false, false]"
            ),
            Arguments.of(
                "[false, false, false, false, true,  false, false, false, false]",
                "[false, false, false, false, false, true,  false, false, false]",
                "[false, false, false, false, false, false, true,  false, false]"
            ),
            Arguments.of(
                "[false, false, false, false, false, false, false, true,  false]",
                "[false, false, false, false, false, false, false, false, true ]",
                ""
            ),
            Arguments.of(
                "[.[] | length]",
                "[[], {}, [1,2], {\"a\":42}, \"asdf\", \"\\u03bc\"]",
                "[0, 0, 2, 1, 4, 1]"
            ),
            Arguments.of(
                "utf8bytelength",
                "\"asdf\\u03bc\"",
                "6"
            ),
            Arguments.of(
                "[.[] | try utf8bytelength catch .]",
                "[[], {}, [1,2], 55, true, false]",
                "[\"array ([]) only strings have UTF-8 byte length\",\"object ({}) only strings have UTF-8 byte length\",\"array ([1,2]) only strings have UTF-8 byte length\",\"number (55) only strings have UTF-8 byte length\",\"boolean (true) only strings have UTF-8 byte length\",\"boolean (false) only strings have UTF-8 byte length\"]"
            ),
            Arguments.of(
                "map(keys)",
                "[{}, {\"abcd\":1,\"abc\":2,\"abcde\":3}, {\"x\":1, \"z\": 3, \"y\":2}]",
                "[[], [\"abc\",\"abcd\",\"abcde\"], [\"x\",\"y\",\"z\"]]"
            ),
            Arguments.of(
                "[1,2,empty,3,empty,4]",
                "null",
                "[1,2,3,4]"
            ),
            Arguments.of(
                "map(add)",
                "[[], [1,2,3], [\"a\",\"b\",\"c\"], [[3],[4,5],[6]], [{\"a\":1}, {\"b\":2}, {\"a\":3}]]",
                "[null, 6, \"abc\", [3,4,5,6], {\"a\":3, \"b\": 2}]"
            ),
            Arguments.of(
                "map_values(.+1)",
                "[0,1,2]",
                "[1,2,3]"
            ),
            Arguments.of(
                "[add(null), add(range(range(10))), add(empty), add(10,range(10))]",
                "null",
                "[null,120,null,55]"
            ),
            Arguments.of(
                ".sum = add(.arr[])",
                "{\"arr\":[]}",
                "{\"arr\":[],\"sum\":null}"
            ),
            Arguments.of(
                "add({(.[]):1}) | keys",
                "[\"a\",\"a\",\"b\",\"a\",\"d\",\"b\",\"d\",\"a\",\"d\"]",
                "[\"a\",\"b\",\"d\"]"
            ),
            Arguments.of(
                "def f: . + 1; def g: def g: . + 100; f | g | f; (f | g), g",
                "3.0",
                "106.0"
            ),
            Arguments.of(
                "105.0",
                "",
                "def f: (1000,2000); f"
            ),
            Arguments.of(
                "123412345",
                "1000",
                "2000"
            ),
            Arguments.of(
                "def f(a;b;c;d;e;f): [a+1,b,c,d,e,f]; f(.[0];.[1];.[0];.[0];.[0];.[0])",
                "[1,2]",
                "[2,2,1,1,1,1]"
            ),
            Arguments.of(
                "def f: 1; def g: f, def f: 2; def g: 3; f, def f: g; f, g; def f: 4; [f, def f: g; def g: 5; f, g]+[f,g]",
                "null",
                "[4,1,2,3,3,5,4,1,2,3,3]"
            ),
            Arguments.of(
                "def a: 0; . | a",
                "null",
                "0"
            ),
            Arguments.of(
                "def f(a;b;c;d;e;f;g;h;i;j): [j,i,h,g,f,e,d,c,b,a]; f(.[0];.[1];.[2];.[3];.[4];.[5];.[6];.[7];.[8];.[9])",
                "[0,1,2,3,4,5,6,7,8,9]",
                "[9,8,7,6,5,4,3,2,1,0]"
            ),
            Arguments.of(
                "([1,2] + [4,5])",
                "[1,2,3]",
                "[1,2,4,5]"
            ),
            Arguments.of(
                "true",
                "[1]",
                "true"
            ),
            Arguments.of(
                "null,1,null",
                "\"hello\"",
                "null"
            ),
            Arguments.of(
                "1",
                "null",
                ""
            ),
            Arguments.of(
                "[1,2,3]",
                "[5,6]",
                "[1,2,3]"
            ),
            Arguments.of(
                "[.[]|floor]",
                "[-1.1,1.1,1.9]",
                "[-2, 1, 1]"
            ),
            Arguments.of(
                "[.[]|sqrt]",
                "[4,9]",
                "[2,3]"
            ),
            Arguments.of(
                "(add / length) as $m | map((. - $m) as $d | $d * $d) | add / length | sqrt",
                "[2,4,4,4,5,5,7,9]",
                "2"
            ),
            Arguments.of(
                "atan * 4 * 1000000|floor / 1000000",
                "1",
                "3.141592"
            ),
            Arguments.of(
                "[(3.141592 / 2) * (range(0;20) / 20)|cos * 1000000|floor / 1000000]",
                "null",
                "[1,0.996917,0.987688,0.972369,0.951056,0.923879,0.891006,0.85264,0.809017,0.760406,0.707106,0.649448,0.587785,0.522498,0.45399,0.382683,0.309017,0.233445,0.156434,0.078459]"
            ),
            Arguments.of(
                "[(3.141592 / 2) * (range(0;20) / 20)|sin * 1000000|floor / 1000000]",
                "null",
                "[0,0.078459,0.156434,0.233445,0.309016,0.382683,0.45399,0.522498,0.587785,0.649447,0.707106,0.760405,0.809016,0.85264,0.891006,0.923879,0.951056,0.972369,0.987688,0.996917]"
            ),
            Arguments.of(
                "def f(x): x | x; f([.], . + [42])",
                "[1,2,3]",
                "[[[1,2,3]]]"
            ),
            Arguments.of(
                "[[1,2,3],42]",
                "[[1,2,3,42]]",
                "[1,2,3,42,42]"
            ),
            Arguments.of(
                "def f: .+1; def g: f; def f: .+100; def f(a):a+.+11; [(g|f(20)), f]",
                "1",
                "[33,101]"
            ),
            Arguments.of(
                "def id(x):x; 2000 as $x | def f(x):1 as $x | id([$x, x, x]); def g(x): 100 as $x | f($x,$x+x); g($x)",
                "\"more testing\"",
                "[1,100,2100.0,100,2100.0]"
            ),
            Arguments.of(
                "def x(a;b): a as $a | b as $b | $a + $b; def y($a;$b): $a + $b; def check(a;b): [x(a;b)] == [y(a;b)]; check(.[];.[]*2)",
                "[1,2,3]",
                "true"
            ),
            Arguments.of(
                "[[20,10][1,0] as $x | def f: (100,200) as $y | def g: [$x + $y, .]; . + $x | g; f[0] | [f][0][1] | f]",
                "999999999",
                "[[110.0, 130.0], [210.0, 130.0], [110.0, 230.0], [210.0, 230.0], [120.0, 160.0], [220.0, 160.0], [120.0, 260.0], [220.0, 260.0]]"
            ),
            Arguments.of(
                "def fac: if . == 1 then 1 else . * (. - 1 | fac) end; [.[] | fac]",
                "[1,2,3,4]",
                "[1,2,6,24]"
            ),
            Arguments.of(
                "reduce .[] as $x (0; . + $x)",
                "[1,2,4]",
                "7"
            ),
            Arguments.of(
                "reduce .[] as [$i, {j:$j}] (0; . + $i - $j)",
                "[[2,{\"j\":1}], [5,{\"j\":3}], [6,{\"j\":4}]]",
                "5"
            ),
            Arguments.of(
                "reduce [[1,2,10], [3,4,10]][] as [$i,$j] (0; . + $i * $j)",
                "null",
                "14"
            ),
            Arguments.of(
                "[-reduce -.[] as $x (0; . + $x)]",
                "[1,2,3]",
                "[6]"
            ),
            Arguments.of(
                "[reduce .[] / .[] as $i (0; . + $i)]",
                "[1,2]",
                "[4.5]"
            ),
            Arguments.of(
                "reduce .[] as $x (0; . + $x) as $x | $x",
                "[1,2,3]",
                "6"
            ),
            Arguments.of(
                "reduce . as $n (.; .)",
                "null",
                "null"
            ),
            Arguments.of(
                ". as {$a, b: [$c, {$d}]} | [$a, $c, $d]",
                "{\"a\":1, \"b\":[2,{\"d\":3}]}",
                "[1,2,3]"
            ),
            Arguments.of(
                ". as {$a, $b:[$c, $d]}| [$a, $b, $c, $d]",
                "{\"a\":1, \"b\":[2,{\"d\":3}]}",
                "[1,[2,{\"d\":3}],2,{\"d\":3}]"
            ),
            Arguments.of(
                ".[] | . as {$a, b: [$c, {$d}]} ?// [$a, {$b}, $e] ?// $f | [$a, $b, $c, $d, $e, $f]",
                "[{\"a\":1, \"b\":[2,{\"d\":3}]}, [4, {\"b\":5, \"c\":6}, 7, 8, 9], \"foo\"]",
                "[1, null, 2, 3, null, null]"
            ),
            Arguments.of(
                "[4, 5, null, null, 7, null]",
                "[null, null, null, null, null, \"foo\"]",
                ""
            ),
            Arguments.of(
                ".[] | . as {a:$a} ?// {a:$a} ?// {a:$a} | $a",
                "[[3],[4],[5],6]",
                "# Runtime error: \"jq: Cannot index array with string (\\\"c\\\")\""
            ),
            Arguments.of(
                ".[] as {a:$a} ?// {a:$a} ?// {a:$a} | $a",
                "[[3],[4],[5],6]",
                "# Runtime error: \"jq: Cannot index array with string (\\\"c\\\")\""
            ),
            Arguments.of(
                "[[3],[4],[5],6][] | . as {a:$a} ?// {a:$a} ?// {a:$a} | $a",
                "null",
                "# Runtime error: \"jq: Cannot index array with string (\\\"c\\\")\""
            ),
            Arguments.of(
                "[[3],[4],[5],6] | .[] as {a:$a} ?// {a:$a} ?// {a:$a} | $a",
                "null",
                "# Runtime error: \"jq: Cannot index array with string (\\\"c\\\")\""
            ),
            Arguments.of(
                ".[] | . as {a:$a} ?// {a:$a} ?// $a | $a",
                "[[3],[4],[5],6]",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                ".[] as {a:$a} ?// {a:$a} ?// $a | $a",
                "[[3],[4],[5],6]",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                "[[3],[4],[5],6][] | . as {a:$a} ?// {a:$a} ?// $a | $a",
                "null",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                "[[3],[4],[5],6] | .[] as {a:$a} ?// {a:$a} ?// $a | $a",
                "null",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                ".[] | . as {a:$a} ?// $a ?// {a:$a} | $a",
                "[[3],[4],[5],6]",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                ".[] as {a:$a} ?// $a ?// {a:$a} | $a",
                "[[3],[4],[5],6]",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                "[[3],[4],[5],6][] | . as {a:$a} ?// $a ?// {a:$a} | $a",
                "null",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                "[[3],[4],[5],6] | .[] as {a:$a} ?// $a ?// {a:$a} | $a",
                "null",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                ".[] | . as $a ?// {a:$a} ?// {a:$a} | $a",
                "[[3],[4],[5],6]",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                ".[] as $a ?// {a:$a} ?// {a:$a} | $a",
                "[[3],[4],[5],6]",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                "[[3],[4],[5],6][] | . as $a ?// {a:$a} ?// {a:$a} | $a",
                "null",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                "[[3],[4],[5],6] | .[] as $a ?// {a:$a} ?// {a:$a} | $a",
                "null",
                "[3]"
            ),
            Arguments.of(
                "[4]",
                "[5]",
                "6"
            ),
            Arguments.of(
                ". as $dot|any($dot[];not)",
                "[1,2,3,4,true,false,1,2,3,4,5]",
                "true"
            ),
            Arguments.of(
                ". as $dot|any($dot[];not)",
                "[1,2,3,4,true]",
                "false"
            ),
            Arguments.of(
                ". as $dot|all($dot[];.)",
                "[1,2,3,4,true,false,1,2,3,4,5]",
                "false"
            ),
            Arguments.of(
                ". as $dot|all($dot[];.)",
                "[1,2,3,4,true]",
                "true"
            ),
            Arguments.of(
                "any(true, error; .)",
                "\"badness\"",
                "true"
            ),
            Arguments.of(
                "all(false, error; .)",
                "\"badness\"",
                "false"
            ),
            Arguments.of(
                "any(not)",
                "[]",
                "false"
            ),
            Arguments.of(
                "all(not)",
                "[]",
                "true"
            ),
            Arguments.of(
                "any(not)",
                "[false]",
                "true"
            ),
            Arguments.of(
                "all(not)",
                "[false]",
                "true"
            ),
            Arguments.of(
                "[any,all]",
                "[]",
                "[false,true]"
            ),
            Arguments.of(
                "[any,all]",
                "[true]",
                "[true,true]"
            ),
            Arguments.of(
                "[any,all]",
                "[false]",
                "[false,false]"
            ),
            Arguments.of(
                "[any,all]",
                "[true,false]",
                "[true,false]"
            ),
            Arguments.of(
                "[any,all]",
                "[null,null,true]",
                "[true,false]"
            ),
            Arguments.of(
                "path(.foo[0,1])",
                "null",
                "[\"foo\", 0]"
            ),
            Arguments.of(
                "[\"foo\", 1]",
                "",
                "path(.[] | select(.>3))"
            ),
            Arguments.of(
                "[1,5,3]",
                "[1]",
                ""
            ),
            Arguments.of(
                "path(.)",
                "42",
                "[]"
            ),
            Arguments.of(
                "try path(.a | map(select(.b == 0))) catch .",
                "{\"a\":[{\"b\":0}]}",
                "\"Invalid path expression with result [{\\\"b\\\":0}]\""
            ),
            Arguments.of(
                "try path(.a | map(select(.b == 0)) | .[0]) catch .",
                "{\"a\":[{\"b\":0}]}",
                "\"Invalid path expression near attempt to access element 0 of [{\\\"b\\\":0}]\""
            ),
            Arguments.of(
                "try path(.a | map(select(.b == 0)) | .c) catch .",
                "{\"a\":[{\"b\":0}]}",
                "\"Invalid path expression near attempt to access element \\\"c\\\" of [{\\\"b\\\":0}]\""
            ),
            Arguments.of(
                "try path(.a | map(select(.b == 0)) | .[]) catch .",
                "{\"a\":[{\"b\":0}]}",
                "\"Invalid path expression near attempt to iterate through [{\\\"b\\\":0}]\""
            ),
            Arguments.of(
                "path(.a[path(.b)[0]])",
                "{\"a\":{\"b\":0}}",
                "[\"a\",\"b\"]"
            ),
            Arguments.of(
                "[paths]",
                "[1,[[],{\"a\":2}]]",
                "[[0],[1],[1,0],[1,1],[1,1,\"a\"]]"
            ),
            Arguments.of(
                "[\"foo\",1] as $p | getpath($p), setpath($p; 20), delpaths([$p])",
                "{\"bar\": 42, \"foo\": [\"a\", \"b\", \"c\", \"d\"]}",
                "\"b\""
            ),
            Arguments.of(
                "{\"bar\": 42, \"foo\": [\"a\", 20, \"c\", \"d\"]}",
                "{\"bar\": 42, \"foo\": [\"a\", \"c\", \"d\"]}",
                ""
            ),
            Arguments.of(
                "map(getpath([2])), map(setpath([2]; 42)), map(delpaths([[2]]))",
                "[[0], [0,1], [0,1,2]]",
                "[null, null, 2]"
            ),
            Arguments.of(
                "[[0,null,42], [0,1,42], [0,1,42]]",
                "[[0], [0,1], [0,1]]",
                ""
            ),
            Arguments.of(
                "map(delpaths([[0,\"foo\"]]))",
                "[[{\"foo\":2, \"x\":1}], [{\"bar\":2}]]",
                "[[{\"x\":1}], [{\"bar\":2}]]"
            ),
            Arguments.of(
                "[\"foo\",1] as $p | getpath($p), setpath($p; 20), delpaths([$p])",
                "{\"bar\":false}",
                "null"
            ),
            Arguments.of(
                "{\"bar\":false, \"foo\": [null, 20]}",
                "{\"bar\":false}",
                ""
            ),
            Arguments.of(
                "delpaths([[-200]])",
                "[1,2,3]",
                "[1,2,3]"
            ),
            Arguments.of(
                "try delpaths(0) catch .",
                "{}",
                "\"Paths must be specified as an array\""
            ),
            Arguments.of(
                "del(.), del(empty), del((.foo,.bar,.baz) | .[2,3,0]), del(.foo[0], .bar[0], .foo, .baz.bar[0].x)",
                "{\"foo\": [0,1,2,3,4], \"bar\": [0,1]}",
                "null"
            ),
            Arguments.of(
                "{\"foo\": [0,1,2,3,4], \"bar\": [0,1]}",
                "{\"foo\": [1,4], \"bar\": [1]}",
                "{\"bar\": [1]}"
            ),
            Arguments.of(
                "del(.[1], .[-6], .[2], .[-3:9])",
                "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]",
                "[0, 3, 5, 6, 9]"
            ),
            Arguments.of(
                "del(.[nan])",
                "[1,2,3]",
                "[1,2,3]"
            ),
            Arguments.of(
                "del(.[nan,nan])",
                "[1,2,3]",
                "[1,2,3]"
            ),
            Arguments.of(
                "setpath([-1]; 1)",
                "[0]",
                "[1]"
            ),
            Arguments.of(
                "pick(.a.b.c)",
                "null",
                "{\"a\":{\"b\":{\"c\":null}}}"
            ),
            Arguments.of(
                "pick(first)",
                "[1,2]",
                "[1]"
            ),
            Arguments.of(
                "pick(first|first)",
                "[[10,20],30]",
                "[[10]]"
            ),
            Arguments.of(
                "try pick(last) catch .",
                "[1,2]",
                "\"Out of bounds negative array index\""
            ),
            Arguments.of(
                ".message = \"goodbye\"",
                "{\"message\": \"hello\"}",
                "{\"message\": \"goodbye\"}"
            ),
            Arguments.of(
                ".foo = .bar",
                "{\"bar\":42}",
                "{\"foo\":42, \"bar\":42}"
            ),
            Arguments.of(
                ".foo |= .+1",
                "{\"foo\": 42}",
                "{\"foo\": 43}"
            ),
            Arguments.of(
                ".[] += 2, .[] *= 2, .[] -= 2, .[] /= 2, .[] %=2",
                "[1,3,5]",
                "[3,5,7]"
            ),
            Arguments.of(
                "[2,6,10]",
                "[-1,1,3]",
                "[0.5, 1.5, 2.5]"
            ),
            Arguments.of(
                "[1,1,1]",
                "",
                "[.[] % 7]"
            ),
            Arguments.of(
                "[-7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7]",
                "[0,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,0]",
                ""
            ),
            Arguments.of(
                ".foo += .foo",
                "{\"foo\":2}",
                "{\"foo\":4}"
            ),
            Arguments.of(
                ".[0].a |= {\"old\":., \"new\":(.+1)}",
                "[{\"a\":1,\"b\":2}]",
                "[{\"a\":{\"old\":1, \"new\":2},\"b\":2}]"
            ),
            Arguments.of(
                "def inc(x): x |= .+1; inc(.[].a)",
                "[{\"a\":1,\"b\":2},{\"a\":2,\"b\":4},{\"a\":7,\"b\":8}]",
                "[{\"a\":2,\"b\":2},{\"a\":3,\"b\":4},{\"a\":8,\"b\":8}]"
            ),
            Arguments.of(
                ".[] | try (getpath([\"a\",0,\"b\"]) |= 5) catch .",
                "[null,{\"b\":0},{\"a\":0},{\"a\":null},{\"a\":[0,1]},{\"a\":{\"b\":1}},{\"a\":[{}]},{\"a\":[{\"c\":3}]}]",
                "{\"a\":[{\"b\":5}]}"
            ),
            Arguments.of(
                "{\"b\":0,\"a\":[{\"b\":5}]}",
                "\"Cannot index number with number (0)\"",
                "{\"a\":[{\"b\":5}]}"
            ),
            Arguments.of(
                "\"Cannot index number with string (\\\"b\\\")\"",
                "\"Cannot index object with number (0)\"",
                "{\"a\":[{\"b\":5}]}"
            ),
            Arguments.of(
                "{\"a\":[{\"c\":3,\"b\":5}]}",
                "",
                "# #2051, deletion using assigning empty against arrays"
            ),
            Arguments.of(
                "(.[] | select(. >= 2)) |= empty",
                "[1,5,3,0,7]",
                "[1,0]"
            ),
            Arguments.of(
                ".[] |= select(. % 2 == 0)",
                "[0,1,2,3,4,5]",
                "[0,2,4]"
            ),
            Arguments.of(
                ".foo[1,4,2,3] |= empty",
                "{\"foo\":[0,1,2,3,4,5]}",
                "{\"foo\":[0,5]}"
            ),
            Arguments.of(
                ".[2][3] = 1",
                "[4]",
                "[4, null, [null, null, null, 1]]"
            ),
            Arguments.of(
                ".foo[2].bar = 1",
                "{\"foo\":[11], \"bar\":42}",
                "{\"foo\":[11,null,{\"bar\":1}], \"bar\":42}"
            ),
            Arguments.of(
                "try ((map(select(.a == 1))[].b) = 10) catch .",
                "[{\"a\":0},{\"a\":1}]",
                "\"Invalid path expression near attempt to iterate through [{\\\"a\\\":1}]\""
            ),
            Arguments.of(
                "try ((map(select(.a == 1))[].a) |= .+1) catch .",
                "[{\"a\":0},{\"a\":1}]",
                "\"Invalid path expression near attempt to iterate through [{\\\"a\\\":1}]\""
            ),
            Arguments.of(
                "def x: .[1,2]; x=10",
                "[0,1,2]",
                "[0,10,10]"
            ),
            Arguments.of(
                "try (def x: reverse; x=10) catch .",
                "[0,1,2]",
                "\"Invalid path expression with result [2,1,0]\""
            ),
            Arguments.of(
                ".[] = 1",
                "[1,null,Infinity,-Infinity,NaN,-NaN]",
                "[1,1,1,1,1,1]"
            ),
            Arguments.of(
                "[.[] | if .foo then \"yep\" else \"nope\" end]",
                "[{\"foo\":0},{\"foo\":1},{\"foo\":[]},{\"foo\":true},{\"foo\":false},{\"foo\":null},{\"foo\":\"foo\"},{}]",
                "[\"yep\",\"yep\",\"yep\",\"yep\",\"nope\",\"nope\",\"yep\",\"nope\"]"
            ),
            Arguments.of(
                "[.[] | if .baz then \"strange\" elif .foo then \"yep\" else \"nope\" end]",
                "[{\"foo\":0},{\"foo\":1},{\"foo\":[]},{\"foo\":true},{\"foo\":false},{\"foo\":null},{\"foo\":\"foo\"},{}]",
                "[\"yep\",\"yep\",\"yep\",\"yep\",\"nope\",\"nope\",\"yep\",\"nope\"]"
            ),
            Arguments.of(
                "[if 1,null,2 then 3 else 4 end]",
                "null",
                "[3,4,3]"
            ),
            Arguments.of(
                "[if empty then 3 else 4 end]",
                "null",
                "[]"
            ),
            Arguments.of(
                "[if 1 then 3,4 else 5 end]",
                "null",
                "[3,4]"
            ),
            Arguments.of(
                "[if null then 3 else 5,6 end]",
                "null",
                "[5,6]"
            ),
            Arguments.of(
                "[if true then 3 end]",
                "7",
                "[3]"
            ),
            Arguments.of(
                "[if false then 3 end]",
                "7",
                "[7]"
            ),
            Arguments.of(
                "[if false then 3 else . end]",
                "7",
                "[7]"
            ),
            Arguments.of(
                "[if false then 3 elif false then 4 end]",
                "7",
                "[7]"
            ),
            Arguments.of(
                "[if false then 3 elif false then 4 else . end]",
                "7",
                "[7]"
            ),
            Arguments.of(
                "[-if true then 1 else 2 end]",
                "null",
                "[-1]"
            ),
            Arguments.of(
                "{x: if true then 1 else 2 end}",
                "null",
                "{\"x\":1}"
            ),
            Arguments.of(
                "if true then [.] else . end []",
                "null",
                "null"
            ),
            Arguments.of(
                "[.[] | [.foo[] // .bar]]",
                "[{\"foo\":[1,2], \"bar\": 42}, {\"foo\":[1], \"bar\": null}, {\"foo\":[null,false,3], \"bar\": 18}, {\"foo\":[], \"bar\":42}, {\"foo\": [null,false,null], \"bar\": 41}]",
                "[[1,2], [1], [3], [42], [41]]"
            ),
            Arguments.of(
                ".[] //= .[0]",
                "[\"hello\",true,false,[false],null]",
                "[\"hello\",true,\"hello\",[false],\"hello\"]"
            ),
            Arguments.of(
                ".[] | [.[0] and .[1], .[0] or .[1]]",
                "[[true,[]], [false,1], [42,null], [null,false]]",
                "[true,true]"
            ),
            Arguments.of(
                "[false,true]",
                "[false,true]",
                "[false,false]"
            ),
            Arguments.of(
                "[.[] | not]",
                "[1,0,false,null,true,\"hello\"]",
                "[false,false,true,true,false,false]"
            ),
            Arguments.of(
                "[10 > 0, 10 > 10, 10 > 20, 10 < 0, 10 < 10, 10 < 20]",
                "{}",
                "[true,false,false,false,false,true]"
            ),
            Arguments.of(
                "[10 >= 0, 10 >= 10, 10 >= 20, 10 <= 0, 10 <= 10, 10 <= 20]",
                "{}",
                "[true,true,false,false,true,true]"
            ),
            Arguments.of(
                "[ 10 == 10, 10 != 10, 10 != 11, 10 == 11]",
                "{}",
                "[true,false,true,false]"
            ),
            Arguments.of(
                "[\"hello\" == \"hello\", \"hello\" != \"hello\", \"hello\" == \"world\", \"hello\" != \"world\" ]",
                "{}",
                "[true,false,false,true]"
            ),
            Arguments.of(
                "[[1,2,3] == [1,2,3], [1,2,3] != [1,2,3], [1,2,3] == [4,5,6], [1,2,3] != [4,5,6]]",
                "{}",
                "[true,false,false,true]"
            ),
            Arguments.of(
                "[{\"foo\":42} == {\"foo\":42},{\"foo\":42} != {\"foo\":42}, {\"foo\":42} != {\"bar\":42}, {\"foo\":42} == {\"bar\":42}]",
                "{}",
                "[true,false,true,false]"
            ),
            Arguments.of(
                "[{\"foo\":[1,2,{\"bar\":18},\"world\"]} == {\"foo\":[1,2,{\"bar\":18},\"world\"]},{\"foo\":[1,2,{\"bar\":18},\"world\"]} == {\"foo\":[1,2,{\"bar\":19},\"world\"]}]",
                "{}",
                "[true,false]"
            ),
            Arguments.of(
                "[(\"foo\" | contains(\"foo\")), (\"foobar\" | contains(\"foo\")), (\"foo\" | contains(\"foobar\"))]",
                "{}",
                "[true, true, false]"
            ),
            Arguments.of(
                "[contains(\"\"), contains(\"\\u0000\")]",
                "\"\\u0000\"",
                "[true, true]"
            ),
            Arguments.of(
                "[contains(\"\"), contains(\"a\"), contains(\"ab\"), contains(\"c\"), contains(\"d\")]",
                "\"ab\\u0000cd\"",
                "[true, true, true, true, true]"
            ),
            Arguments.of(
                "[contains(\"cd\"), contains(\"b\\u0000\"), contains(\"ab\\u0000\")]",
                "\"ab\\u0000cd\"",
                "[true, true, true]"
            ),
            Arguments.of(
                "[contains(\"b\\u0000c\"), contains(\"b\\u0000cd\"), contains(\"b\\u0000cd\")]",
                "\"ab\\u0000cd\"",
                "[true, true, true]"
            ),
            Arguments.of(
                "[contains(\"@\"), contains(\"\\u0000@\"), contains(\"\\u0000what\")]",
                "\"ab\\u0000cd\"",
                "[false, false, false]"
            ),
            Arguments.of(
                "[.[]|try if . == 0 then error(\"foo\") elif . == 1 then .a elif . == 2 then empty else . end catch .]",
                "[0,1,2,3]",
                "[\"foo\",\"Cannot index number with string (\\\"a\\\")\",3]"
            ),
            Arguments.of(
                "[.[]|(.a, .a)?]",
                "[null,true,{\"a\":1}]",
                "[null,null,1,1]"
            ),
            Arguments.of(
                "[[.[]|[.a,.a]]?]",
                "[null,true,{\"a\":1}]",
                "[]"
            ),
            Arguments.of(
                "[if error then 1 else 2 end?]",
                "\"foo\"",
                "[]"
            ),
            Arguments.of(
                "try error(0) // 1",
                "null",
                "1"
            ),
            Arguments.of(
                "1, try error(2), 3",
                "null",
                "1"
            ),
            Arguments.of(
                "3",
                "",
                "1 + try 2 catch 3 + 4"
            ),
            Arguments.of(
                "null",
                "7",
                ""
            ),
            Arguments.of(
                "[-try .]",
                "1",
                "[-1]"
            ),
            Arguments.of(
                "try -.? catch .",
                "\"foo\"",
                "\"string (\\\"foo\\\") cannot be negated\""
            ),
            Arguments.of(
                "{x: try 1, y: try error catch 2, z: if true then 3 end}",
                "null",
                "{\"x\":1,\"y\":2,\"z\":3}"
            ),
            Arguments.of(
                "{x: 1 + 2, y: false or true, z: null // 3}",
                "null",
                "{\"x\":3,\"y\":true,\"z\":3}"
            ),
            Arguments.of(
                ".[] | try error catch .",
                "[1,null,2]",
                "1"
            ),
            Arguments.of(
                "null",
                "2",
                ""
            ),
            Arguments.of(
                "try error(\"\\($__loc__)\") catch .",
                "null",
                "\"{\\\"file\\\":\\\"<top-level>\\\",\\\"line\\\":1}\""
            ),
            Arguments.of(
                "[.[]|startswith(\"foo\")]",
                "[\"fo\", \"foo\", \"barfoo\", \"foobar\", \"barfoob\"]",
                "[false, true, false, true, false]"
            ),
            Arguments.of(
                "[.[]|endswith(\"foo\")]",
                "[\"fo\", \"foo\", \"barfoo\", \"foobar\", \"barfoob\"]",
                "[false, true, true, false, false]"
            ),
            Arguments.of(
                "[.[] | split(\", \")]",
                "[\"a,b, c, d, e,f\",\", a,b, c, d, e,f, \"]",
                "[[\"a,b\",\"c\",\"d\",\"e,f\"],[\"\",\"a,b\",\"c\",\"d\",\"e,f\",\"\"]]"
            ),
            Arguments.of(
                "split(\"\")",
                "\"abc\"",
                "[\"a\",\"b\",\"c\"]"
            ),
            Arguments.of(
                "[.[]|ltrimstr(\"foo\")]",
                "[\"fo\", \"foo\", \"barfoo\", \"foobar\", \"afoo\"]",
                "[\"fo\",\"\",\"barfoo\",\"bar\",\"afoo\"]"
            ),
            Arguments.of(
                "[.[]|rtrimstr(\"foo\")]",
                "[\"fo\", \"foo\", \"barfoo\", \"foobar\", \"foob\"]",
                "[\"fo\",\"\",\"bar\",\"foobar\",\"foob\"]"
            ),
            Arguments.of(
                "[.[]|trimstr(\"foo\")]",
                "[\"fo\", \"foo\", \"barfoo\", \"foobarfoo\", \"foob\"]",
                "[\"fo\",\"\",\"bar\",\"bar\",\"b\"]"
            ),
            Arguments.of(
                "[.[]|ltrimstr(\"\")]",
                "[\"a\", \"xx\", \"\"]",
                "[\"a\", \"xx\", \"\"]"
            ),
            Arguments.of(
                "[.[]|rtrimstr(\"\")]",
                "[\"a\", \"xx\", \"\"]",
                "[\"a\", \"xx\", \"\"]"
            ),
            Arguments.of(
                "[.[]|trimstr(\"\")]",
                "[\"a\", \"xx\", \"\"]",
                "[\"a\", \"xx\", \"\"]"
            ),
            Arguments.of(
                "[(index(\",\"), rindex(\",\")), indices(\",\")]",
                "\"a,bc,def,ghij,klmno\"",
                "[1,13,[1,4,8,13]]"
            ),
            Arguments.of(
                "[ index(\"aba\"), rindex(\"aba\"), indices(\"aba\") ]",
                "\"xababababax\"",
                "[1,7,[1,3,5,7]]"
            ),
            Arguments.of(
                "try _strindices(\"abc\") catch .",
                "123",
                "\"number (123) cannot be searched, as it is not a string\""
            ),
            Arguments.of(
                "try _strindices(123) catch .",
                "\"abc\"",
                "\"number (123) is not a string\""
            ),
            Arguments.of(
                "map(trim), map(ltrim), map(rtrim)",
                "[\" \\n\\t\\r\\f\\u000b\", \"\",\"  \", \"a\", \" a \", \"abc\", \"  abc  \", \"  abc\", \"abc  \"]",
                "[\"\", \"\", \"\", \"a\", \"a\", \"abc\", \"abc\", \"abc\", \"abc\"]"
            ),
            Arguments.of(
                "[\"\", \"\", \"\", \"a\", \"a \", \"abc\", \"abc  \", \"abc\", \"abc  \"]",
                "[\"\", \"\", \"\", \"a\", \" a\", \"abc\", \"  abc\", \"  abc\", \"abc\"]",
                ""
            ),
            Arguments.of(
                "trim, ltrim, rtrim",
                "\"\\u0009\\u000A\\u000B\\u000C\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006\\u2007\\u2008\\u2009\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000abc\\u0009\\u000A\\u000B\\u000C\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006\\u2007\\u2008\\u2009\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\"",
                "\"abc\""
            ),
            Arguments.of(
                "\"abc\\u0009\\u000A\\u000B\\u000C\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006\\u2007\\u2008\\u2009\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\"",
                "\"\\u0009\\u000A\\u000B\\u000C\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006\\u2007\\u2008\\u2009\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000abc\"",
                ""
            ),
            Arguments.of(
                "try trim catch ., try ltrim catch ., try rtrim catch .",
                "123",
                "\"trim input must be a string\""
            ),
            Arguments.of(
                "\"trim input must be a string\"",
                "\"trim input must be a string\"",
                ""
            ),
            Arguments.of(
                "indices(1)",
                "[0,1,1,2,3,4,1,5]",
                "[1,2,6]"
            ),
            Arguments.of(
                "indices([1,2])",
                "[0,1,2,3,1,4,2,5,1,2,6,7]",
                "[1,8]"
            ),
            Arguments.of(
                "indices([1,2])",
                "[1]",
                "[]"
            ),
            Arguments.of(
                "indices(\", \")",
                "\"a,b, cd,e, fgh, ijkl\"",
                "[3,9,14]"
            ),
            Arguments.of(
                "index(\"!\")",
                "\"здравствуй мир!\"",
                "14"
            ),
            Arguments.of(
                ".[:rindex(\"x\")]",
                "\"正xyz\"",
                "\"正\""
            ),
            Arguments.of(
                "indices(\"o\")",
                "\"🇬🇧oo\"",
                "[2,3]"
            ),
            Arguments.of(
                "indices(\"o\")",
                "\"ƒoo\"",
                "[1,2]"
            ),
            Arguments.of(
                "[.[]|split(\",\")]",
                "[\"a, bc, def, ghij, jklmn, a,b, c,d, e,f\", \"a,b,c,d, e,f,g,h\"]",
                "[[\"a\",\" bc\",\" def\",\" ghij\",\" jklmn\",\" a\",\"b\",\" c\",\"d\",\" e\",\"f\"],[\"a\",\"b\",\"c\",\"d\",\" e\",\"f\",\"g\",\"h\"]]"
            ),
            Arguments.of(
                "[.[]|split(\", \")]",
                "[\"a, bc, def, ghij, jklmn, a,b, c,d, e,f\", \"a,b,c,d, e,f,g,h\"]",
                "[[\"a\",\"bc\",\"def\",\"ghij\",\"jklmn\",\"a,b\",\"c,d\",\"e,f\"],[\"a,b,c,d\",\"e,f,g,h\"]]"
            ),
            Arguments.of(
                "[.[] * 3]",
                "[\"a\", \"ab\", \"abc\"]",
                "[\"aaa\", \"ababab\", \"abcabcabc\"]"
            ),
            Arguments.of(
                "[.[] * \"abc\"]",
                "[-1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 3.7, 10.0]",
                "[null,null,\"\",\"\",\"abc\",\"abc\",\"abcabcabc\",\"abcabcabcabcabcabcabcabcabcabc\"]"
            ),
            Arguments.of(
                "[. * (nan,-nan)]",
                "\"abc\"",
                "[null,null]"
            ),
            Arguments.of(
                ". * 100000 | [.[:10],.[-10:]]",
                "\"abc\"",
                "[\"abcabcabca\",\"cabcabcabc\"]"
            ),
            Arguments.of(
                ". * 1000000000",
                "\"\"",
                "\"\""
            ),
            Arguments.of(
                "try (. * 1000000000) catch .",
                "\"abc\"",
                "\"Repeat string result too long\""
            ),
            Arguments.of(
                "[.[] / \",\"]",
                "[\"a, bc, def, ghij, jklmn, a,b, c,d, e,f\", \"a,b,c,d, e,f,g,h\"]",
                "[[\"a\",\" bc\",\" def\",\" ghij\",\" jklmn\",\" a\",\"b\",\" c\",\"d\",\" e\",\"f\"],[\"a\",\"b\",\"c\",\"d\",\" e\",\"f\",\"g\",\"h\"]]"
            ),
            Arguments.of(
                "[.[] / \", \"]",
                "[\"a, bc, def, ghij, jklmn, a,b, c,d, e,f\", \"a,b,c,d, e,f,g,h\"]",
                "[[\"a\",\"bc\",\"def\",\"ghij\",\"jklmn\",\"a,b\",\"c,d\",\"e,f\"],[\"a,b,c,d\",\"e,f,g,h\"]]"
            ),
            Arguments.of(
                "map(.[1] as $needle | .[0] | contains($needle))",
                "[[[],[]], [[1,2,3], [1,2]], [[1,2,3], [3,1]], [[1,2,3], [4]], [[1,2,3], [1,4]]]",
                "[true, true, true, false, false]"
            ),
            Arguments.of(
                "map(.[1] as $needle | .[0] | contains($needle))",
                "[[[\"foobar\", \"foobaz\"], [\"baz\", \"bar\"]], [[\"foobar\", \"foobaz\"], [\"foo\"]], [[\"foobar\", \"foobaz\"], [\"blap\"]]]",
                "[true, true, false]"
            ),
            Arguments.of(
                "[({foo: 12, bar:13} | contains({foo: 12})), ({foo: 12} | contains({})), ({foo: 12, bar:13} | contains({baz:14}))]",
                "{}",
                "[true, true, false]"
            ),
            Arguments.of(
                "{foo: {baz: 12, blap: {bar: 13}}, bar: 14} | contains({bar: 14, foo: {blap: {}}})",
                "{}",
                "true"
            ),
            Arguments.of(
                "{foo: {baz: 12, blap: {bar: 13}}, bar: 14} | contains({bar: 14, foo: {blap: {bar: 14}}})",
                "{}",
                "false"
            ),
            Arguments.of(
                "sort",
                "[42,[2,5,3,11],10,{\"a\":42,\"b\":2},{\"a\":42},true,2,[2,6],\"hello\",null,[2,5,6],{\"a\":[],\"b\":1},\"abc\",\"ab\",[3,10],{},false,\"abcd\",null]",
                "[null,null,false,true,2,10,42,\"ab\",\"abc\",\"abcd\",\"hello\",[2,5,3,11],[2,5,6],[2,6],[3,10],{},{\"a\":42},{\"a\":42,\"b\":2},{\"a\":[],\"b\":1}]"
            ),
            Arguments.of(
                "(sort_by(.b) | sort_by(.a)), sort_by(.a, .b), sort_by(.b, .c), group_by(.b), group_by(.a + .b - .c == 2)",
                "[{\"a\": 1, \"b\": 4, \"c\": 14}, {\"a\": 4, \"b\": 1, \"c\": 3}, {\"a\": 1, \"b\": 4, \"c\": 3}, {\"a\": 0, \"b\": 2, \"c\": 43}]",
                "[{\"a\": 0, \"b\": 2, \"c\": 43}, {\"a\": 1, \"b\": 4, \"c\": 14}, {\"a\": 1, \"b\": 4, \"c\": 3}, {\"a\": 4, \"b\": 1, \"c\": 3}]"
            ),
            Arguments.of(
                "[{\"a\": 0, \"b\": 2, \"c\": 43}, {\"a\": 1, \"b\": 4, \"c\": 14}, {\"a\": 1, \"b\": 4, \"c\": 3}, {\"a\": 4, \"b\": 1, \"c\": 3}]",
                "[{\"a\": 4, \"b\": 1, \"c\": 3}, {\"a\": 0, \"b\": 2, \"c\": 43}, {\"a\": 1, \"b\": 4, \"c\": 3}, {\"a\": 1, \"b\": 4, \"c\": 14}]",
                "[[{\"a\": 4, \"b\": 1, \"c\": 3}], [{\"a\": 0, \"b\": 2, \"c\": 43}], [{\"a\": 1, \"b\": 4, \"c\": 14}, {\"a\": 1, \"b\": 4, \"c\": 3}]]"
            ),
            Arguments.of(
                "[[{\"a\": 1, \"b\": 4, \"c\": 14}, {\"a\": 0, \"b\": 2, \"c\": 43}], [{\"a\": 4, \"b\": 1, \"c\": 3}, {\"a\": 1, \"b\": 4, \"c\": 3}]]",
                "",
                "unique"
            ),
            Arguments.of(
                "[1,2,5,3,5,3,1,3]",
                "[1,2,3,5]",
                ""
            ),
            Arguments.of(
                "unique",
                "[]",
                "[]"
            ),
            Arguments.of(
                "[min, max, min_by(.[1]), max_by(.[1]), min_by(.[2]), max_by(.[2])]",
                "[[4,2,\"a\"],[3,1,\"a\"],[2,4,\"a\"],[1,3,\"a\"]]",
                "[[1,3,\"a\"],[4,2,\"a\"],[3,1,\"a\"],[2,4,\"a\"],[4,2,\"a\"],[1,3,\"a\"]]"
            ),
            Arguments.of(
                "[min,max,min_by(.),max_by(.)]",
                "[]",
                "[null,null,null,null]"
            ),
            Arguments.of(
                ".foo[.baz]",
                "{\"foo\":{\"bar\":4},\"baz\":\"bar\"}",
                "4"
            ),
            Arguments.of(
                ".[] | .error = \"no, it's OK\"",
                "[{\"error\":true}]",
                "{\"error\": \"no, it's OK\"}"
            ),
            Arguments.of(
                "[{a:1}] | .[] | .a=999",
                "null",
                "{\"a\": 999}"
            ),
            Arguments.of(
                "to_entries",
                "{\"a\": 1, \"b\": 2}",
                "[{\"key\":\"a\", \"value\":1}, {\"key\":\"b\", \"value\":2}]"
            ),
            Arguments.of(
                "from_entries",
                "[{\"key\":\"a\", \"value\":1}, {\"Key\":\"b\", \"Value\":2}, {\"name\":\"c\", \"value\":3}, {\"Name\":\"d\", \"Value\":4}]",
                "{\"a\": 1, \"b\": 2, \"c\": 3, \"d\": 4}"
            ),
            Arguments.of(
                "with_entries(.key |= \"KEY_\" + .)",
                "{\"a\": 1, \"b\": 2}",
                "{\"KEY_a\": 1, \"KEY_b\": 2}"
            ),
            Arguments.of(
                "map(has(\"foo\"))",
                "[{\"foo\": 42}, {}]",
                "[true, false]"
            ),
            Arguments.of(
                "map(has(2))",
                "[[0,1], [\"a\",\"b\",\"c\"]]",
                "[false, true]"
            ),
            Arguments.of(
                "has(nan)",
                "[0,1,2]",
                "false"
            ),
            Arguments.of(
                "keys",
                "[42,3,35]",
                "[0,1,2]"
            ),
            Arguments.of(
                "[][.]",
                "1000000000000000000",
                "null"
            ),
            Arguments.of(
                "map([1,2][0:.])",
                "[-1, 1, 2, 3, 1000000000000000000]",
                "[[1], [1], [1,2], [1,2], [1,2]]"
            ),
            Arguments.of(
                "{\"k\": {\"a\": 1, \"b\": 2}} * .",
                "{\"k\": {\"a\": 0,\"c\": 3}}",
                "{\"k\": {\"a\": 0, \"b\": 2, \"c\": 3}}"
            ),
            Arguments.of(
                "{\"k\": {\"a\": 1, \"b\": 2}, \"hello\": {\"x\": 1}} * .",
                "{\"k\": {\"a\": 0,\"c\": 3}, \"hello\": 1}",
                "{\"k\": {\"a\": 0, \"b\": 2, \"c\": 3}, \"hello\": 1}"
            ),
            Arguments.of(
                "{\"k\": {\"a\": 1, \"b\": 2}, \"hello\": 1} * .",
                "{\"k\": {\"a\": 0,\"c\": 3}, \"hello\": {\"x\": 1}}",
                "{\"k\": {\"a\": 0, \"b\": 2, \"c\": 3}, \"hello\": {\"x\": 1}}"
            ),
            Arguments.of(
                "{\"a\": {\"b\": 1}, \"c\": {\"d\": 2}, \"e\": 5} * .",
                "{\"a\": {\"b\": 2}, \"c\": {\"d\": 3, \"f\": 9}}",
                "{\"a\": {\"b\": 2}, \"c\": {\"d\": 3, \"f\": 9}, \"e\": 5}"
            ),
            Arguments.of(
                "[.[]|arrays]",
                "[1,2,\"foo\",[],[3,[]],{},true,false,null]",
                "[[],[3,[]]]"
            ),
            Arguments.of(
                "[.[]|objects]",
                "[1,2,\"foo\",[],[3,[]],{},true,false,null]",
                "[{}]"
            ),
            Arguments.of(
                "[.[]|iterables]",
                "[1,2,\"foo\",[],[3,[]],{},true,false,null]",
                "[[],[3,[]],{}]"
            ),
            Arguments.of(
                "[.[]|scalars]",
                "[1,2,\"foo\",[],[3,[]],{},true,false,null]",
                "[1,2,\"foo\",true,false,null]"
            ),
            Arguments.of(
                "[.[]|values]",
                "[1,2,\"foo\",[],[3,[]],{},true,false,null]",
                "[1,2,\"foo\",[],[3,[]],{},true,false]"
            ),
            Arguments.of(
                "[.[]|booleans]",
                "[1,2,\"foo\",[],[3,[]],{},true,false,null]",
                "[true,false]"
            ),
            Arguments.of(
                "[.[]|nulls]",
                "[1,2,\"foo\",[],[3,[]],{},true,false,null]",
                "[null]"
            ),
            Arguments.of(
                "flatten",
                "[0, [1], [[2]], [[[3]]]]",
                "[0, 1, 2, 3]"
            ),
            Arguments.of(
                "flatten(0)",
                "[0, [1], [[2]], [[[3]]]]",
                "[0, [1], [[2]], [[[3]]]]"
            ),
            Arguments.of(
                "flatten(2)",
                "[0, [1], [[2]], [[[3]]]]",
                "[0, 1, 2, [3]]"
            ),
            Arguments.of(
                "flatten(2)",
                "[0, [1, [2]], [1, [[3], 2]]]",
                "[0, 1, 2, 1, [3], 2]"
            ),
            Arguments.of(
                "try flatten(-1) catch .",
                "[0, [1], [[2]], [[[3]]]]",
                "\"flatten depth must not be negative\""
            ),
            Arguments.of(
                "transpose",
                "[[1], [2,3]]",
                "[[1,2],[null,3]]"
            ),
            Arguments.of(
                "transpose",
                "[]",
                "[]"
            ),
            Arguments.of(
                "ascii_upcase",
                "\"useful but not for é\"",
                "\"USEFUL BUT NOT FOR é\""
            ),
            Arguments.of(
                "bsearch(0,1,2,3,4)",
                "[1,2,3]",
                "-1"
            ),
            Arguments.of(
                "0",
                "1",
                "2"
            ),
            Arguments.of(
                "-4",
                "",
                "bsearch({x:1})"
            ),
            Arguments.of(
                "[{ \"x\": 0 },{ \"x\": 1 },{ \"x\": 2 }]",
                "1",
                ""
            ),
            Arguments.of(
                "try [\"OK\", bsearch(0)] catch [\"KO\",.]",
                "\"aa\"",
                "[\"KO\",\"string (\\\"aa\\\") cannot be searched from\"]"
            ),
            Arguments.of(
                "strftime(\"%Y-%m-%dT%H:%M:%SZ\")",
                "[2015,2,5,23,51,47,4,63]",
                "\"2015-03-05T23:51:47Z\""
            ),
            Arguments.of(
                "strftime(\"%A, %B %d, %Y\")",
                "1435677542.822351",
                "\"Tuesday, June 30, 2015\""
            ),
            Arguments.of(
                "strftime(\"%Y-%m-%dT%H:%M:%SZ\")",
                "[2024,2,15]",
                "\"2024-03-15T00:00:00Z\""
            ),
            Arguments.of(
                "mktime",
                "[2024,8,21]",
                "1726876800"
            ),
            Arguments.of(
                "gmtime",
                "1425599507",
                "[2015,2,5,23,51,47,4,63]"
            ),
            Arguments.of(
                "gmtime[5]",
                "1425599507.25",
                "47.25"
            ),
            Arguments.of(
                "try strftime(\"%Y-%m-%dT%H:%M:%SZ\") catch .",
                "[\"a\",1,2,3,4,5,6,7]",
                "\"strftime/1 requires parsed datetime inputs\""
            ),
            Arguments.of(
                "try strflocaltime(\"%Y-%m-%dT%H:%M:%SZ\") catch .",
                "[\"a\",1,2,3,4,5,6,7]",
                "\"strflocaltime/1 requires parsed datetime inputs\""
            ),
            Arguments.of(
                "try mktime catch .",
                "[\"a\",1,2,3,4,5,6,7]",
                "\"mktime requires parsed datetime inputs\""
            ),
            Arguments.of(
                "try [\"OK\", strftime([])] catch [\"KO\", .]",
                "0",
                "[\"KO\",\"strftime/1 requires a string format\"]"
            ),
            Arguments.of(
                "try [\"OK\", strflocaltime({})] catch [\"KO\", .]",
                "0",
                "[\"KO\",\"strflocaltime/1 requires a string format\"]"
            ),
            Arguments.of(
                "[strptime(\"%Y-%m-%dT%H:%M:%SZ\")|(.,mktime)]",
                "\"2015-03-05T23:51:47Z\"",
                "[[2015,2,5,23,51,47,4,63],1425599507]"
            ),
            Arguments.of(
                "last(range(365 * 67)|(\"1970-03-01T01:02:03Z\"|strptime(\"%Y-%m-%dT%H:%M:%SZ\")|mktime) + (86400 * .)|strftime(\"%Y-%m-%dT%H:%M:%SZ\")|strptime(\"%Y-%m-%dT%H:%M:%SZ\"))",
                "null",
                "[2037,1,11,1,2,3,3,41]"
            ),
            Arguments.of(
                "import \"a\" as foo; import \"b\" as bar; def fooa: foo::a; [fooa, bar::a, bar::b, foo::a]",
                "null",
                "[\"a\",\"b\",\"c\",\"a\"]"
            ),
            Arguments.of(
                "import \"c\" as foo; [foo::a, foo::c]",
                "null",
                "[0,\"acmehbah\"]"
            ),
            Arguments.of(
                "include \"c\"; [a, c]",
                "null",
                "[0,\"acmehbah\"]"
            ),
            Arguments.of(
                "import \"data\" as $e; import \"data\" as $d; [$d[].this,$e[].that,$d::d[].this,$e::e[].that]|join(\";\")",
                "null",
                "\"is a test;is too;is a test;is too\""
            ),
            Arguments.of(
                "import \"data\" as $a; import \"data\" as $b; def f: {$a, $b}; f",
                "null",
                "{\"a\":[{\"this\":\"is a test\",\"that\":\"is too\"}],\"b\":[{\"this\":\"is a test\",\"that\":\"is too\"}]}"
            ),
            Arguments.of(
                "include \"shadow1\"; e",
                "null",
                "2"
            ),
            Arguments.of(
                "include \"shadow1\"; include \"shadow2\"; e",
                "null",
                "3"
            ),
            Arguments.of(
                "import \"shadow1\" as f; import \"shadow2\" as f; import \"shadow1\" as e; [e::e, f::e]",
                "null",
                "[2,3]"
            ),
            Arguments.of(
                "    module (.+1); 0",
                "           ^^^^^",
                ""
            ),
            Arguments.of(
                "    module []; 0",
                "           ^^",
                ""
            ),
            Arguments.of(
                "    include \"a\" (.+1); 0",
                "                ^^^^^",
                ""
            ),
            Arguments.of(
                "    include \"a\" []; 0",
                "                ^^",
                ""
            ),
            Arguments.of(
                "    include \"\\ \"; 0",
                "             ^^",
                ""
            ),
            Arguments.of(
                "    include \"\\(a)\"; 0",
                "            ^^^^^^",
                ""
            ),
            Arguments.of(
                "modulemeta",
                "\"c\"",
                "{\"whatever\":null,\"deps\":[{\"as\":\"foo\",\"is_data\":false,\"relpath\":\"a\"},{\"search\":\"./\",\"as\":\"d\",\"is_data\":false,\"relpath\":\"d\"},{\"search\":\"./\",\"as\":\"d2\",\"is_data\":false,\"relpath\":\"d\"},{\"search\":\"./../lib/jq\",\"as\":\"e\",\"is_data\":false,\"relpath\":\"e\"},{\"search\":\"./../lib/jq\",\"as\":\"f\",\"is_data\":false,\"relpath\":\"f\"},{\"as\":\"d\",\"is_data\":true,\"relpath\":\"data\"}],\"defs\":[\"a/0\",\"c/0\"]}"
            ),
            Arguments.of(
                "modulemeta | .deps | length",
                "\"c\"",
                "6"
            ),
            Arguments.of(
                "modulemeta | .defs | length",
                "\"c\"",
                "2"
            ),
            Arguments.of(
                "    wat;",
                "       ^",
                ""
            ),
            Arguments.of(
                "    %::wat",
                "    ^",
                ""
            ),
            Arguments.of(
                "import \"test_bind_order\" as check; check::check",
                "null",
                "true"
            ),
            Arguments.of(
                "try -. catch .",
                "\"very-long-long-long-long-string\"",
                "\"string (\\\"very-long-long-long-long...\\\") cannot be negated\""
            ),
            Arguments.of(
                "try (.-.) catch .",
                "\"very-long-long-long-long-string\"",
                "\"string (\\\"very-long-long-long-long...\\\") and string (\\\"very-long-long-long-long...\\\") cannot be subtracted\""
            ),
            Arguments.of(
                "\"x\" * range(0; 12; 2) + \"☆\" * 8 | try -. catch .",
                "null",
                "\"string (\\\"☆☆☆☆☆☆☆☆\\\") cannot be negated\""
            ),
            Arguments.of(
                "\"string (\\\"xx☆☆☆☆☆☆☆☆\\\") cannot be negated\"",
                "\"string (\\\"xxxx☆☆☆☆☆☆...\\\") cannot be negated\"",
                "\"string (\\\"xxxxxx☆☆☆☆☆☆...\\\") cannot be negated\""
            ),
            Arguments.of(
                "\"string (\\\"xxxxxxxx☆☆☆☆☆...\\\") cannot be negated\"",
                "\"string (\\\"xxxxxxxxxx☆☆☆☆...\\\") cannot be negated\"",
                ""
            ),
            Arguments.of(
                "try (. + \"x\") catch . == if have_decnum then \"number (12345678901234567890123456...) and string (\\\"x\\\") cannot be added\" else \"number (12345678901234568000000000...) and string (\\\"x\\\") cannot be added\" end",
                "123456789012345678901234567890",
                "true"
            ),
            Arguments.of(
                "join(\",\")",
                "[\"1\",2,true,false,3.4]",
                "\"1,2,true,false,3.4\""
            ),
            Arguments.of(
                ".[] | join(\",\")",
                "[[], [null], [null,null], [null,null,null]]",
                "\"\""
            ),
            Arguments.of(
                "\"\"",
                "\",\"",
                "\",,\""
            ),
            Arguments.of(
                ".[] | join(\",\")",
                "[[\"a\",null], [null,\"a\"]]",
                "\"a,\""
            ),
            Arguments.of(
                "\",a\"",
                "",
                "try join(\",\") catch ."
            ),
            Arguments.of(
                "[\"1\",\"2\",{\"a\":{\"b\":{\"c\":33}}}]",
                "\"string (\\\"1,2,\\\") and object ({\\\"a\\\":{\\\"b\\\":{\\\"c\\\":33}}}) cannot be added\"",
                ""
            ),
            Arguments.of(
                "try join(\",\") catch .",
                "[\"1\",\"2\",[3,4,5]]",
                "\"string (\\\"1,2,\\\") and array ([3,4,5]) cannot be added\""
            ),
            Arguments.of(
                "{if:0,and:1,or:2,then:3,else:4,elif:5,end:6,as:7,def:8,reduce:9,foreach:10,try:11,catch:12,label:13,import:14,include:15,module:16}",
                "null",
                "{\"if\":0,\"and\":1,\"or\":2,\"then\":3,\"else\":4,\"elif\":5,\"end\":6,\"as\":7,\"def\":8,\"reduce\":9,\"foreach\":10,\"try\":11,\"catch\":12,\"label\":13,\"import\":14,\"include\":15,\"module\":16}"
            ),
            Arguments.of(
                "try (1/.) catch .",
                "0",
                "\"number (1) and number (0) cannot be divided because the divisor is zero\""
            ),
            Arguments.of(
                "try (1/0) catch .",
                "0",
                "\"number (1) and number (0) cannot be divided because the divisor is zero\""
            ),
            Arguments.of(
                "try (0/0) catch .",
                "0",
                "\"number (0) and number (0) cannot be divided because the divisor is zero\""
            ),
            Arguments.of(
                "try (1%.) catch .",
                "0",
                "\"number (1) and number (0) cannot be divided (remainder) because the divisor is zero\""
            ),
            Arguments.of(
                "try (1%0) catch .",
                "0",
                "\"number (1) and number (0) cannot be divided (remainder) because the divisor is zero\""
            ),
            Arguments.of(
                "[range(-52;52;1)] as $powers | [$powers[]|pow(2;.)|log2|round] == $powers",
                "null",
                "true"
            ),
            Arguments.of(
                "[range(-99/2;99/2;1)] as $orig | [$orig[]|pow(2;.)|log2] as $back | ($orig|keys)[]|. as $k | (($orig|.[$k])-($back|.[$k]))|if . < 0 then . * -1 else . end|select(.>.00005)",
                "null",
                ""
            ),
            Arguments.of(
                "    {",
                "    ^",
                ""
            ),
            Arguments.of(
                "    }",
                "    ^",
                ""
            ),
            Arguments.of(
                "(.[{}] = 0)?",
                "null",
                ""
            ),
            Arguments.of(
                "INDEX(range(5)|[., \"foo\\(.)\"]; .[0])",
                "null",
                "{\"0\":[0,\"foo0\"],\"1\":[1,\"foo1\"],\"2\":[2,\"foo2\"],\"3\":[3,\"foo3\"],\"4\":[4,\"foo4\"]}"
            ),
            Arguments.of(
                "JOIN({\"0\":[0,\"abc\"],\"1\":[1,\"bcd\"],\"2\":[2,\"def\"],\"3\":[3,\"efg\"],\"4\":[4,\"fgh\"]}; .[0]|tostring)",
                "[[5,\"foo\"],[3,\"bar\"],[1,\"foobar\"]]",
                "[[[5,\"foo\"],null],[[3,\"bar\"],[3,\"efg\"]],[[1,\"foobar\"],[1,\"bcd\"]]]"
            ),
            Arguments.of(
                "range(5;10)|IN(range(10))",
                "null",
                "true"
            ),
            Arguments.of(
                "true",
                "true",
                "true"
            ),
            Arguments.of(
                "true",
                "",
                "range(5;13)|IN(range(0;10;3))"
            ),
            Arguments.of(
                "null",
                "false",
                "true"
            ),
            Arguments.of(
                "false",
                "false",
                "true"
            ),
            Arguments.of(
                "false",
                "false",
                "false"
            ),
            Arguments.of(
                "range(10;12)|IN(range(10))",
                "null",
                "false"
            ),
            Arguments.of(
                "false",
                "",
                "IN(range(10;20); range(10))"
            ),
            Arguments.of(
                "null",
                "false",
                ""
            ),
            Arguments.of(
                "IN(range(5;20); range(10))",
                "null",
                "true"
            ),
            Arguments.of(
                "(.a as $x | .b) = \"b\"",
                "{\"a\":null,\"b\":null}",
                "{\"a\":null,\"b\":\"b\"}"
            ),
            Arguments.of(
                "(.. | select(type == \"object\" and has(\"b\") and (.b | type) == \"array\")|.b) |= .[0]",
                "{\"a\": {\"b\": [1, {\"b\": 3}]}}",
                "{\"a\": {\"b\": 1}}"
            ),
            Arguments.of(
                "isempty(empty)",
                "null",
                "true"
            ),
            Arguments.of(
                "isempty(range(3))",
                "null",
                "false"
            ),
            Arguments.of(
                "isempty(1,error(\"foo\"))",
                "null",
                "false"
            ),
            Arguments.of(
                "index(\"\")",
                "\"\"",
                "null"
            ),
            Arguments.of(
                "builtins|length > 10",
                "null",
                "true"
            ),
            Arguments.of(
                "\"-1\"|IN(builtins[] / \"/\"|.[1])",
                "null",
                "false"
            ),
            Arguments.of(
                "all(builtins[] / \"/\"; .[1]|tonumber >= 0)",
                "null",
                "true"
            ),
            Arguments.of(
                "builtins|any(.[:1] == \"_\")",
                "null",
                "false"
            ),
            Arguments.of(
                "map(. == 1)",
                "[1, 1.0, 1.000, 100e-2, 1e+0, 0.0001e4]",
                "[true, true, true, true, true, true]"
            ),
            Arguments.of(
                ".[0] | tostring | . == if have_decnum then \"13911860366432393\" else \"13911860366432392\" end",
                "[13911860366432393]",
                "true"
            ),
            Arguments.of(
                ".x | tojson | . == if have_decnum then \"13911860366432393\" else \"13911860366432392\" end",
                "{\"x\":13911860366432393}",
                "true"
            ),
            Arguments.of(
                "(13911860366432393 == 13911860366432392) | . == if have_decnum then false else true end",
                "null",
                "true"
            ),
            Arguments.of(
                ". - 10",
                "13911860366432393",
                "13911860366432382"
            ),
            Arguments.of(
                ".[0] - 10",
                "[13911860366432393]",
                "13911860366432382"
            ),
            Arguments.of(
                ".x - 10",
                "{\"x\":13911860366432393}",
                "13911860366432382"
            ),
            Arguments.of(
                "-. | tojson == if have_decnum then \"-13911860366432393\" else \"-13911860366432392\" end",
                "13911860366432393",
                "true"
            ),
            Arguments.of(
                "-. | tojson == if have_decnum then \"0.12345678901234567890123456789\" else \"0.12345678901234568\" end",
                "-0.12345678901234567890123456789",
                "true"
            ),
            Arguments.of(
                "[1E+1000,-1E+1000 | tojson] == if have_decnum then [\"1E+1000\",\"-1E+1000\"] else [\"1.7976931348623157e+308\",\"-1.7976931348623157e+308\"] end",
                "null",
                "true"
            ),
            Arguments.of(
                ". |= try . catch .",
                "1",
                "1"
            ),
            Arguments.of(
                ".[] as $n | $n+0 | [., tostring, . == $n]",
                "[-9007199254740993, -9007199254740992, 9007199254740992, 9007199254740993, 13911860366432393]",
                "[-9007199254740992,\"-9007199254740992\",true]"
            ),
            Arguments.of(
                "[-9007199254740992,\"-9007199254740992\",true]",
                "[9007199254740992,\"9007199254740992\",true]",
                "[9007199254740992,\"9007199254740992\",true]"
            ),
            Arguments.of(
                "[13911860366432392,\"13911860366432392\",true]",
                "",
                "# abs, fabs, length"
            ),
            Arguments.of(
                "abs",
                "\"abc\"",
                "\"abc\""
            ),
            Arguments.of(
                "map(abs)",
                "[-0, 0, -10, -1.1]",
                "[0,0,10,1.1]"
            ),
            Arguments.of(
                "map(fabs)",
                "[-0, 0, -10, -1.1]",
                "[0,0,10,1.1]"
            ),
            Arguments.of(
                "map(abs == length) | unique",
                "[-10, -1.1, -1e-1, 1000000000000000002]",
                "[true]"
            ),
            Arguments.of(
                "map(abs)",
                "[0.1,1000000000000000002]",
                "[1e-1, 1000000000000000002]"
            ),
            Arguments.of(
                "[1E+1000,-1E+1000 | abs | tojson] | unique == if have_decnum then [\"1E+1000\"] else [\"1.7976931348623157e+308\"] end",
                "null",
                "true"
            ),
            Arguments.of(
                "[1E+1000,-1E+1000 | length | tojson] | unique == if have_decnum then [\"1E+1000\"] else [\"1.7976931348623157e+308\"] end",
                "null",
                "true"
            ),
            Arguments.of(
                "123 as $label | $label",
                "null",
                "123"
            ),
            Arguments.of(
                "[ label $if | range(10) | ., (select(. == 5) | break $if) ]",
                "null",
                "[0,1,2,3,4,5]"
            ),
            Arguments.of(
                "reduce .[] as $then (4 as $else | $else; . as $elif | . + $then * $elif)",
                "[1,2,3]",
                "96"
            ),
            Arguments.of(
                "1 as $foreach | 2 as $and | 3 as $or | { $foreach, $and, $or, a }",
                "{\"a\":4,\"b\":5}",
                "{\"foreach\":1,\"and\":2,\"or\":3,\"a\":4}"
            ),
            Arguments.of(
                "[ foreach .[] as $try (1 as $catch | $catch - 1; . + $try; .) ]",
                "[10,9,8,7]",
                "[10,19,27,34]"
            ),
            Arguments.of(
                "{ a, $__loc__, c }",
                "{\"a\":[1,2,3],\"b\":\"foo\",\"c\":{\"hi\":\"hey\"}}",
                "{\"a\":[1,2,3],\"__loc__\":{\"file\":\"<top-level>\",\"line\":1},\"c\":{\"hi\":\"hey\"}}"
            ),
            Arguments.of(
                "1 as $x | \"2\" as $y | \"3\" as $z | { $x, as, $y: 4, ($z): 5, if: 6, foo: 7 }",
                "{\"as\":8}",
                "{\"x\":1,\"as\":8,\"2\":4,\"3\":5,\"if\":6,\"foo\":7}"
            ),
            Arguments.of(
                "fromjson | isnan",
                "\"nan\"",
                "true"
            ),
            Arguments.of(
                "tojson | fromjson",
                "{\"a\":nan}",
                "{\"a\":null}"
            ),
            Arguments.of(
                ".[] | try (fromjson | isnan) catch .",
                "[\"NaN\",\"-NaN\",\"NaN1\",\"NaN10\",\"NaN100\",\"NaN1000\",\"NaN10000\",\"NaN100000\"]",
                "true"
            ),
            Arguments.of(
                "true",
                "\"Invalid numeric literal at EOF at line 1, column 4 (while parsing 'NaN1')\"",
                "\"Invalid numeric literal at EOF at line 1, column 5 (while parsing 'NaN10')\""
            ),
            Arguments.of(
                "\"Invalid numeric literal at EOF at line 1, column 6 (while parsing 'NaN100')\"",
                "\"Invalid numeric literal at EOF at line 1, column 7 (while parsing 'NaN1000')\"",
                "\"Invalid numeric literal at EOF at line 1, column 8 (while parsing 'NaN10000')\""
            ),
            Arguments.of(
                "\"Invalid numeric literal at EOF at line 1, column 9 (while parsing 'NaN100000')\"",
                "",
                "# calling input/0, or debug/0 in a test doesn't crash jq"
            ),
            Arguments.of(
                "try input catch .",
                "null",
                "\"break\""
            ),
            Arguments.of(
                "debug",
                "1",
                "1"
            ),
            Arguments.of(
                "\"foo\" | try ((try . catch \"caught too much\") | error) catch \"caught just right\"",
                "null",
                "\"caught just right\""
            ),
            Arguments.of(
                ".[]|(try (if .==\"hi\" then . else error end) catch empty) | \"\\(.) there!\"",
                "[\"hi\",\"ho\"]",
                "\"hi there!\""
            ),
            Arguments.of(
                "try ([\"hi\",\"ho\"]|.[]|(try . catch (if .==\"ho\" then \"BROKEN\"|error else empty end)) | if .==\"ho\" then error else \"\\(.) there!\" end) catch \"caught outside \\(.)\"",
                "null",
                "\"hi there!\""
            ),
            Arguments.of(
                "\"caught outside ho\"",
                "",
                ".[]|(try . catch (if .==\"ho\" then \"BROKEN\"|error else empty end)) | if .==\"ho\" then error else \"\\(.) there!\" end"
            ),
            Arguments.of(
                "[\"hi\",\"ho\"]",
                "\"hi there!\"",
                ""
            ),
            Arguments.of(
                "try (try error catch \"inner catch \\(.)\") catch \"outer catch \\(.)\"",
                "\"foo\"",
                "\"inner catch foo\""
            ),
            Arguments.of(
                "try ((try error catch \"inner catch \\(.)\")|error) catch \"outer catch \\(.)\"",
                "\"foo\"",
                "\"outer catch inner catch foo\""
            ),
            Arguments.of(
                "first(.?,.?)",
                "null",
                "null"
            ),
            Arguments.of(
                "{foo: \"bar\"} | .foo |= .?",
                "null",
                "{\"foo\": \"bar\"}"
            ),
            Arguments.of(
                ". |= try 2",
                "1",
                "2"
            ),
            Arguments.of(
                ". |= try 2 catch 3",
                "1",
                "2"
            ),
            Arguments.of(
                ".[] |= try tonumber",
                "[\"1\", \"2a\", \"3\", \" 4\", \"5 \", \"6.7\", \".89\", \"-876\", \"+5.43\", 21]",
                "[1, 3, 6.7, 0.89, -876, 5.43, 21]"
            ),
            Arguments.of(
                "any(keys[]|tostring?;true)",
                "{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"}",
                "true"
            ),
            Arguments.of(
                "implode|explode",
                "[-1,0,1,2,3,1114111,1114112,55295,55296,57343,57344,1.1,1.9]",
                "[65533,0,1,2,3,1114111,65533,55295,65533,65533,57344,1,1]"
            ),
            Arguments.of(
                "map(try implode catch .)",
                "[123,[\"a\"],[nan]]",
                "[\"implode input must be an array\",\"string (\\\"a\\\") can't be imploded, unicode codepoint needs to be numeric\",\"number (null) can't be imploded, unicode codepoint needs to be numeric\"]"
            ),
            Arguments.of(
                "try 0[implode] catch .",
                "[]",
                "\"Cannot index number with string (\\\"\\\")\""
            ),
            Arguments.of(
                "walk(.)",
                "{\"x\":0}",
                "{\"x\":0}"
            ),
            Arguments.of(
                "walk(1)",
                "{\"x\":0}",
                "1"
            ),
            Arguments.of(
                "[walk(.,1)]",
                "{\"x\":0}",
                "[{\"x\":0},1]"
            ),
            Arguments.of(
                "walk(select(IN({}, []) | not))",
                "{\"a\":1,\"b\":[]}",
                "{\"a\":1}"
            ),
            Arguments.of(
                "[range(10)] | .[1.2:3.5]",
                "null",
                "[1,2,3]"
            ),
            Arguments.of(
                "[range(10)] | .[1.5:3.5]",
                "null",
                "[1,2,3]"
            ),
            Arguments.of(
                "[range(10)] | .[1.7:3.5]",
                "null",
                "[1,2,3]"
            ),
            Arguments.of(
                "[range(10)] | .[1.7:4294967295]",
                "null",
                "[1,2,3,4,5,6,7,8,9]"
            ),
            Arguments.of(
                "[range(10)] | .[1.7:-4294967296]",
                "null",
                "[]"
            ),
            Arguments.of(
                "[[range(10)] | .[1.1,1.5,1.7]]",
                "null",
                "[1,1,1]"
            ),
            Arguments.of(
                "[range(5)] | .[1.1] = 5",
                "null",
                "[0,5,2,3,4]"
            ),
            Arguments.of(
                "[range(3)] | .[nan:1]",
                "null",
                "[0]"
            ),
            Arguments.of(
                "[range(3)] | .[1:nan]",
                "null",
                "[1,2]"
            ),
            Arguments.of(
                "[range(3)] | .[nan]",
                "null",
                "null"
            ),
            Arguments.of(
                "try ([range(3)] | .[nan] = 9) catch .",
                "null",
                "\"Cannot set array element at NaN index\""
            ),
            Arguments.of(
                "try (\"foobar\" | .[1.5:3.5] = \"xyz\") catch .",
                "null",
                "\"Cannot update string slices\""
            ),
            Arguments.of(
                "try ([range(10)] | .[1.5:3.5] = [\"xyz\"]) catch .",
                "null",
                "[0,\"xyz\",4,5,6,7,8,9]"
            ),
            Arguments.of(
                "try (\"foobar\" | .[1.5]) catch .",
                "null",
                "\"Cannot index string with number (1.5)\""
            ),
            Arguments.of(
                "try [\"ok\", setpath([1]; 1)] catch [\"ko\", .]",
                "{\"hi\":\"hello\"}",
                "[\"ko\",\"Cannot index object with number (1)\"]"
            ),
            Arguments.of(
                "try fromjson catch .",
                "\"{'a': 123}\"",
                "\"Invalid string literal; expected \\\", but got ' at line 1, column 5 (while parsing '{'a': 123}')\""
            ),
            Arguments.of(
                "try ltrimstr(1) catch \"x\", try rtrimstr(1) catch \"x\" | \"ok\"",
                "\"hi\"",
                "\"ok\""
            ),
            Arguments.of(
                "\"ok\"",
                "",
                "try ltrimstr(\"x\") catch \"x\", try rtrimstr(\"x\") catch \"x\" | \"ok\""
            ),
            Arguments.of(
                "{\"hey\":[]}",
                "\"ok\"",
                "\"ok\""
            ),
            Arguments.of(
                ".[] as [$x, $y] | try [\"ok\", ($x | ltrimstr($y))] catch [\"ko\", .]",
                "[[\"hi\",1],[1,\"hi\"],[\"hi\",\"hi\"],[1,1]]",
                "[\"ko\",\"startswith() requires string inputs\"]"
            ),
            Arguments.of(
                "[\"ko\",\"startswith() requires string inputs\"]",
                "[\"ok\",\"\"]",
                "[\"ko\",\"startswith() requires string inputs\"]"
            ),
            Arguments.of(
                ".[] as [$x, $y] | try [\"ok\", ($x | rtrimstr($y))] catch [\"ko\", .]",
                "[[\"hi\",1],[1,\"hi\"],[\"hi\",\"hi\"],[1,1]]",
                "[\"ko\",\"endswith() requires string inputs\"]"
            ),
            Arguments.of(
                "[\"ko\",\"endswith() requires string inputs\"]",
                "[\"ok\",\"\"]",
                "[\"ko\",\"endswith() requires string inputs\"]"
            ),
            Arguments.of(
                "try [\"OK\", setpath([[1]]; 1)] catch [\"KO\", .]",
                "[]",
                "[\"KO\",\"Cannot update field at array index of array\"]"
            ),
            Arguments.of(
                "foreach .[] as $x (0, 1; . + $x)",
                "[1, 2]",
                "1"
            ),
            Arguments.of(
                "3",
                "2",
                "4"
            ),
            Arguments.of(
                "strflocaltime(\"\" | ., @uri)",
                "0",
                "\"\""
            ),
            Arguments.of(
                "\"\"",
                "",
                "# regression tests for #3413"
            ),
            Arguments.of(
                "reduce range(9999) as $_ ([];[.]) | tojson | fromjson | flatten",
                "null",
                "[]"
            ),
            Arguments.of(
                "reduce range(10000) as $_ ([];[.]) | tojson | try (fromjson) catch . | (contains(\"<skipped: too deep>\") | not) and contains(\"Exceeds depth limit for parsing\")",
                "null",
                "true"
            ),
            Arguments.of(
                "reduce range(10001) as $_ ([];[.]) | tojson | contains(\"<skipped: too deep>\")",
                "null",
                "true"
            ),
            Arguments.of(
                "setpath([range(10000) | 0]; 0) | flatten",
                "null",
                "[0]"
            ),
            Arguments.of(
                "try setpath([range(10001) | 0]; 0) catch .",
                "null",
                "\"Path too deep\""
            ),
            Arguments.of(
                "getpath([range(10000) | 0])",
                "null",
                "null"
            ),
            Arguments.of(
                "try getpath([range(10001) | 0]) catch .",
                "null",
                "\"Path too deep\""
            ),
            Arguments.of(
                "delpaths([[range(10000) | 0]])",
                "null",
                "null"
            ),
            Arguments.of(
                "try delpaths([[range(10001) | 0]]) catch .",
                "null",
                "\"Path too deep\""
            ),
            Arguments.of(
                "reduce range(10000) as $_ ([]; [.]) | contains([[]])",
                "null",
                "true"
            ),
            Arguments.of(
                "try (reduce range(10001) as $_ ([]; [.]) as $x | $x | contains($x)) catch .",
                "null",
                "\"Containment check too deep\""
            ),
            Arguments.of(
                "reduce range(10000) as $_ ({}; {a: .}) as $x | $x * $x | length",
                "null",
                "1"
            ),
            Arguments.of(
                "try (reduce range(10001) as $_ ({}; {a: .}) as $x | $x * $x) catch .",
                "null",
                "\"Object merge too deep\""
            ),
            Arguments.of(
                "try ((reduce range(10001) as $_ ([]; [.])) as $x | (reduce range(10001) as $_ ([]; [.])) as $y | $x == $y) catch .",
                "null",
                "\"Equality check too deep\""
            ),
            Arguments.of(
                "try ((reduce range(10001) as $_ ([]; [.])) as $x | [$x, $x] | sort) catch .",
                "null",
                "\"Comparison too deep\""
            ),
            Arguments.of(
                "try ((reduce range(10001) as $_ ([]; [.])) as $x | [$x, $x] | unique) catch .",
                "null",
                "\"Comparison too deep\""
            ),
            Arguments.of(
                "try ((reduce range(10001) as $_ ({}; {a: .})) as $x | [$x, $x] | sort) catch .",
                "null",
                "\"Comparison too deep\""
            ),
            Arguments.of(
                "try ((reduce range(10001) as $_ ({}; {a: .})) as $x | [$x, $x] | unique) catch .",
                "null",
                "\"Comparison too deep\""
            )
        );
    }

    @ParameterizedTest(name = "jq[{index}] {0}")
    @MethodSource("jqTests")
    void testJqTranslation(String program, String input, String expected) {
        // Test actual execution using the AST-based engine
        IJsonQuery query = JqEngine.compile(program);
        assertNotNull(query, "Failed to compile: " + program);

        // Parse input
        Object inputObj = parseInput(input);

        // Execute and get result
        Object result = query.applyOne(inputObj);

        // Compare result with expected output
        if ("null".equals(expected)) {
            assertNull(result, "Expected null for program: " + program);
        } else {
            Object expectedObj = parseExpected(expected);
            assertEquals(expectedObj, result, "Program: " + program + ", Input: " + input);
        }
    }

    private Object parseInput(String input) {
        if ("null".equals(input)) return null;
        if ("true".equals(input)) return true;
        if ("false".equals(input)) return false;
        // Try to parse as number
        try {
            if (input.contains(".")) {
                return Double.parseDouble(input);
            }
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            // Not a number, treat as string
        }
        // Try to parse as JSON
        try {
            return io.nop.core.lang.json.JsonTool.parse(input);
        } catch (Exception e) {
            // Not valid JSON, return as string
            return input;
        }
    }

    private Object parseExpected(String expected) {
        if ("null".equals(expected)) return null;
        if ("true".equals(expected)) return true;
        if ("false".equals(expected)) return false;
        // Try to parse as number
        try {
            if (expected.contains(".")) {
                return Double.parseDouble(expected);
            }
            return Integer.parseInt(expected);
        } catch (NumberFormatException e) {
            // Not a number, treat as string
        }
        // Try to parse as JSON
        try {
            return io.nop.core.lang.json.JsonTool.parse(expected);
        } catch (Exception e) {
            // Not valid JSON, return as string
            return expected;
        }
    }
}
