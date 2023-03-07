parser grammar XPathBaseParser;

import XPathExpr;

options {
    tokenVocab=XPathLexer;
}

xpath: singleSelector ('|' singleSelector)*;

singleSelector: valueOperator | rootValueSelector | complexSelector ;

complexSelector: elementSelector  valueOperator?;

valueOperator: SpecialValues | attrOperator | valueExprOperator ;

rootValueSelector: '/' + valueOperator;

attrOperator: '@' XName;

elementSelector: ('.'?  '/' | '//') ? pathComponent (('/' | '//') pathComponent)+ '/'?;

pathComponent: pathComponentName ('[' singleExpression ']')?;

pathComponentName: Sharp ? XName '?'? | '*' | '..';

valueExprOperator: ':[' + singleExpression + ']';
