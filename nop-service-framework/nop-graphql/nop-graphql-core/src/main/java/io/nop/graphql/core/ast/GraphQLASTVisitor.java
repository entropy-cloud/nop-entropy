//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast;

import io.nop.commons.functional.visit.AbstractVisitor;

// tell cpd to start ignoring code - CPD-OFF
public class GraphQLASTVisitor extends AbstractVisitor<GraphQLASTNode>{

    @Override
    public void visit(GraphQLASTNode node){
        switch(node.getASTKind()){
        
                case GraphQLDocument:
                    visitGraphQLDocument((GraphQLDocument)node);
                    return;
            
                case GraphQLDirective:
                    visitGraphQLDirective((GraphQLDirective)node);
                    return;
            
                case GraphQLArgument:
                    visitGraphQLArgument((GraphQLArgument)node);
                    return;
            
                case GraphQLLiteral:
                    visitGraphQLLiteral((GraphQLLiteral)node);
                    return;
            
                case GraphQLArrayValue:
                    visitGraphQLArrayValue((GraphQLArrayValue)node);
                    return;
            
                case GraphQLObjectValue:
                    visitGraphQLObjectValue((GraphQLObjectValue)node);
                    return;
            
                case GraphQLPropertyValue:
                    visitGraphQLPropertyValue((GraphQLPropertyValue)node);
                    return;
            
                case GraphQLVariable:
                    visitGraphQLVariable((GraphQLVariable)node);
                    return;
            
                case GraphQLOperation:
                    visitGraphQLOperation((GraphQLOperation)node);
                    return;
            
                case GraphQLVariableDefinition:
                    visitGraphQLVariableDefinition((GraphQLVariableDefinition)node);
                    return;
            
                case GraphQLNamedType:
                    visitGraphQLNamedType((GraphQLNamedType)node);
                    return;
            
                case GraphQLNonNullType:
                    visitGraphQLNonNullType((GraphQLNonNullType)node);
                    return;
            
                case GraphQLListType:
                    visitGraphQLListType((GraphQLListType)node);
                    return;
            
                case GraphQLSelectionSet:
                    visitGraphQLSelectionSet((GraphQLSelectionSet)node);
                    return;
            
                case GraphQLFieldSelection:
                    visitGraphQLFieldSelection((GraphQLFieldSelection)node);
                    return;
            
                case GraphQLFragmentSelection:
                    visitGraphQLFragmentSelection((GraphQLFragmentSelection)node);
                    return;
            
                case GraphQLFragment:
                    visitGraphQLFragment((GraphQLFragment)node);
                    return;
            
                case GraphQLObjectDefinition:
                    visitGraphQLObjectDefinition((GraphQLObjectDefinition)node);
                    return;
            
                case GraphQLInterfaceDefinition:
                    visitGraphQLInterfaceDefinition((GraphQLInterfaceDefinition)node);
                    return;
            
                case GraphQLFieldDefinition:
                    visitGraphQLFieldDefinition((GraphQLFieldDefinition)node);
                    return;
            
                case GraphQLInputDefinition:
                    visitGraphQLInputDefinition((GraphQLInputDefinition)node);
                    return;
            
                case GraphQLInputFieldDefinition:
                    visitGraphQLInputFieldDefinition((GraphQLInputFieldDefinition)node);
                    return;
            
                case GraphQLArgumentDefinition:
                    visitGraphQLArgumentDefinition((GraphQLArgumentDefinition)node);
                    return;
            
                case GraphQLDirectiveDefinition:
                    visitGraphQLDirectiveDefinition((GraphQLDirectiveDefinition)node);
                    return;
            
                case GraphQLUnionTypeDefinition:
                    visitGraphQLUnionTypeDefinition((GraphQLUnionTypeDefinition)node);
                    return;
            
                case GraphQLScalarDefinition:
                    visitGraphQLScalarDefinition((GraphQLScalarDefinition)node);
                    return;
            
                case GraphQLEnumDefinition:
                    visitGraphQLEnumDefinition((GraphQLEnumDefinition)node);
                    return;
            
                case GraphQLEnumValueDefinition:
                    visitGraphQLEnumValueDefinition((GraphQLEnumValueDefinition)node);
                    return;
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
            public void visitGraphQLDocument(GraphQLDocument node){
            
                    this.visitChildren(node.getDefinitions());         
            }
        
            public void visitGraphQLDirective(GraphQLDirective node){
            
                    this.visitChildren(node.getArguments());         
            }
        
            public void visitGraphQLArgument(GraphQLArgument node){
            
                    this.visitChild(node.getValue());
            }
        
            public void visitGraphQLLiteral(GraphQLLiteral node){
            
            }
        
            public void visitGraphQLArrayValue(GraphQLArrayValue node){
            
                    this.visitChildren(node.getItems());         
            }
        
            public void visitGraphQLObjectValue(GraphQLObjectValue node){
            
                    this.visitChildren(node.getProperties());         
            }
        
            public void visitGraphQLPropertyValue(GraphQLPropertyValue node){
            
                    this.visitChild(node.getValue());
            }
        
            public void visitGraphQLVariable(GraphQLVariable node){
            
            }
        
            public void visitGraphQLOperation(GraphQLOperation node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getVariableDefinitions());         
                    this.visitChild(node.getSelectionSet());
            }
        
            public void visitGraphQLVariableDefinition(GraphQLVariableDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChild(node.getType());
                    this.visitChild(node.getDefaultValue());
            }
        
            public void visitGraphQLNamedType(GraphQLNamedType node){
            
            }
        
            public void visitGraphQLNonNullType(GraphQLNonNullType node){
            
                    this.visitChild(node.getType());
            }
        
            public void visitGraphQLListType(GraphQLListType node){
            
                    this.visitChild(node.getType());
            }
        
            public void visitGraphQLSelectionSet(GraphQLSelectionSet node){
            
                    this.visitChildren(node.getSelections());         
            }
        
            public void visitGraphQLFieldSelection(GraphQLFieldSelection node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getArguments());         
                    this.visitChild(node.getSelectionSet());
            }
        
            public void visitGraphQLFragmentSelection(GraphQLFragmentSelection node){
            
                    this.visitChildren(node.getDirectives());         
            }
        
            public void visitGraphQLFragment(GraphQLFragment node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChild(node.getSelectionSet());
            }
        
            public void visitGraphQLObjectDefinition(GraphQLObjectDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getFields());         
                    this.visitChildren(node.getInterfaces());         
            }
        
            public void visitGraphQLInterfaceDefinition(GraphQLInterfaceDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getFields());         
            }
        
            public void visitGraphQLFieldDefinition(GraphQLFieldDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChild(node.getType());
                    this.visitChildren(node.getArguments());         
            }
        
            public void visitGraphQLInputDefinition(GraphQLInputDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getFields());         
            }
        
            public void visitGraphQLInputFieldDefinition(GraphQLInputFieldDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChild(node.getType());
                    this.visitChild(node.getDefaultValue());
            }
        
            public void visitGraphQLArgumentDefinition(GraphQLArgumentDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChild(node.getType());
                    this.visitChild(node.getDefaultValue());
            }
        
            public void visitGraphQLDirectiveDefinition(GraphQLDirectiveDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getArguments());         
            }
        
            public void visitGraphQLUnionTypeDefinition(GraphQLUnionTypeDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getTypes());         
            }
        
            public void visitGraphQLScalarDefinition(GraphQLScalarDefinition node){
            
                    this.visitChildren(node.getDirectives());         
            }
        
            public void visitGraphQLEnumDefinition(GraphQLEnumDefinition node){
            
                    this.visitChildren(node.getDirectives());         
                    this.visitChildren(node.getEnumValues());         
            }
        
            public void visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node){
            
                    this.visitChildren(node.getDirectives());         
            }
        
}
// resume CPD analysis - CPD-ON
