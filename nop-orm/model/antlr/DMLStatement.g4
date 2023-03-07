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

grammar DMLStatement;

import Symbol, Keyword, SQL92Keyword, Literals, BaseRule;

// 必须包含into
sqlInsert
    : decorators=sqlDecorators_? INSERT INTO tableName=sqlTableName LP_ <indent> columns=columnNames_ <br> RP_
        ( values=sqlValues | select=sqlSelect)
    ;

sqlUpdate
    : decorators=sqlDecorators_? UPDATE tableName=sqlTableName (AS? alias=sqlAlias?) (<br> assignments=sqlAssignments_) (<br> where=sqlWhere)?
    ;

sqlAssignments_
    : SET e=sqlAssignment (COMMA_ <indent> e=sqlAssignment)*
    ;

sqlAssignment
    : columnName=sqlColumnName EQ_ expr=sqlExpr
    ;

sqlValues:
    values=sqlValues_;

sqlValues_
    : VALUES  LP_ <indent> e=sqlExpr (COMMA_ e=sqlExpr)* (<br> RP_)
//    | LP_ RP_
    ;

//assignmentValue
//    : expr //| DEFAULT | blobValue
//    ;

//blobValue
//    : STRING_
//    ;

sqlDelete
    : decorators=sqlDecorators_? DELETE FROM tableName=sqlTableName AS? alias=sqlAlias? (<br> where=sqlWhere)?
    ;

//singleTableClause
//    : FROM tableName AS? alias?
//    ;

sqlSelectWithCte:
    decorators=sqlDecorators_? withCtes=sqlCteStatements_ select=sqlSelect;

sqlCteStatement
    : name=sqlIdentifier_ AS LP_ statement=sqlSelect RP_
    ;

sqlCteStatements_
    : WITH e=sqlCteStatement (COMMA_ e=sqlCteStatement)*
    ;

sqlSelect
    : sqlUnionSelect
    | sqlQuerySelect
    ;

sqlUnionSelect
    : decorators=sqlDecorators_? LP_ left=sqlQuerySelect RP_ <br> unionType=unionType_ <br> LP_ right=sqlSelect RP_
    ;

unionType_:
    UNION (ALL)?;

sqlQuerySelect
    : decorators=sqlDecorators_? SELECT distinct=distinct_?
        (selectAll=ASTERISK_ | projections=sqlProjections_)
        (<br> from=sqlFrom)?
        (<br> where=sqlWhere)?
        (<br> groupBy=sqlGroupBy)?
        (<br> having=sqlHaving)?
        (<br> orderBy=sqlOrderBy)?
        (<br> limit=sqlLimit)?
        (<br> forUpdate=forUpdate_)?
    ;

//selectSpecification
//    : duplicateSpecification
//    ;

sqlProjections_
    : e=sqlProjection (COMMA_ e=sqlProjection)*
    ;

sqlProjection
    : sqlExprProjection | sqlAllProjection
    ;

sqlExprProjection:
    expr=sqlExpr AS? alias=sqlAlias? decorators=sqlDecorators_?;

sqlAllProjection:
    owner=sqlQualifiedName DOT_ASTERISK_;

sqlAlias
    : alias=sqlAlias_
    ;

sqlAlias_:
    sqlIdentifier_ | STRING_;

sqlFrom
    : FROM decorators=sqlDecorators_? tableSources=tableSources_
    ;

tableSources_
//    : escapedTableReference (COMMA_ escapedTableReference)*
    : e=sqlTableSource (COMMA_ e=sqlTableSource)*
    ;

//escapedTableReference
//    : tableReference  | LBE_ tableReference RBE_
//    ;

sqlTableSource:
    sqlSingleTableSource|sqlSubqueryTableSource|sqlJoinTableSource;

sqlSingleTableSource:
    tableName=sqlTableName AS? alias=sqlAlias? decorators=sqlDecorators_?;

sqlSubqueryTableSource:
   lateral=LATERAL? LP_  query=sqlSelect RP_ AS? alias=sqlAlias;

sqlJoinTableSource
    : left=sqlSingleTableSource <indent> joinType=joinType_ right=sqlTableSource_joinRight <indent> (ON condition=sqlExpr)?
    ;

joinType_:
    innerJoin_ | leftJoin_ | rightJoin_ | fullJoin_;

sqlTableSource_joinRight:
    sqlSingleTableSource | sqlSubqueryTableSource;

innerJoin_:
    INNER? JOIN;

fullJoin_:
   FULL JOIN;

leftJoin_:
    LEFT OUTER? JOIN;

rightJoin_:
    RIGHT OUTER? JOIN;

sqlWhere
    : WHERE expr=sqlExpr
    ;

sqlGroupBy
    : GROUP BY items=sqlGroupByItems_
    ;

sqlGroupByItems_:
      e=sqlGroupByItem (COMMA_ e=sqlGroupByItem)*;

//groupWindow
//    : regularFunction;


sqlHaving
    : HAVING expr=sqlExpr
    ;

sqlLimit
    : LIMIT limit=sqlExpr_limitRowCount (OFFSET offset=sqlExpr_limitOffset)?
    ;

sqlExpr_limitRowCount
    : sqlNumberLiteral | sqlParameterMarker
    ;

sqlExpr_limitOffset
    : sqlNumberLiteral | sqlParameterMarker
    ;

sqlSubQueryExpr
    : LP_ <indent> select=sqlSelect <br> RP_
    ;


// window_type ::= TUMBLING some_interval
//               | HOPPING  some_interval some_interval
//               | SESSION  some_interval

//INTERVAL <num> <time_unit>

forUpdate_
    : FOR UPDATE
    ;
