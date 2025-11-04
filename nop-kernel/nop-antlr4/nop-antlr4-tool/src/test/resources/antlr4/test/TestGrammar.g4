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

grammar TestGrammar;

import TestLexer;

columnNames_
//    : LP_? columnName (COMMA_ columnName)* RP_?
    : e=sqlColumnName (COMMA_ |e=sqlColumnName)*
    ;

// 必须包含into
sqlInsert
    : INSERT INTO tableName=sqlTableName LP_ {indent();} columns=columnNames_ { br();} RP_
        ( values=sqlValues | select=sqlSelect)+
    ;

sqlTableName:
    sqlIdentifier;

sqlIdentifier:
    Identifier;



sqlColumnName
    : (owner=sqlQualifiedName DOT_)? name=sqlIdentifier
    ;

//viewName
//    : identifier
//    | (owner DOT_)? identifier
//    ;

sqlQualifiedName
    : name=sqlIdentifier (DOT_ next=sqlQualifiedName)?
//    | (owner DOT_)? identifier
    ;


sqlValues:
    sqlIdentifier;

sqlSelect:
    sqlIdentifier;

sqlExpr:
     left=sqlExpr op=(AND | AND_) right=sqlExpr # SqlAndExpr
    | left=sqlExpr op=OR right=sqlIdentifier # SqlOrExpr
    |     sqlIdentifier # SqlIdentifierExpr
    ;

sqlExpr2:
     left=sqlIdentifier op=(AND | AND_) right=sqlExpr # SqlAndExpr_2
    | left=sqlIdentifier op=OR right=sqlIdentifier # SqlOrExpr_2
    ;