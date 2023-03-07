//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast;

import io.nop.core.lang.ast.optimize.AbstractOptimizer;

// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class GraphQLASTOptimizer<C> extends AbstractOptimizer<GraphQLASTNode,C>{

    public GraphQLASTNode optimize(GraphQLASTNode node,C context){
        switch(node.getASTKind()){
        
                case GraphQLDocument:
                return optimizeGraphQLDocument((GraphQLDocument)node,context);
            
                case GraphQLDirective:
                return optimizeGraphQLDirective((GraphQLDirective)node,context);
            
                case GraphQLArgument:
                return optimizeGraphQLArgument((GraphQLArgument)node,context);
            
                case GraphQLLiteral:
                return optimizeGraphQLLiteral((GraphQLLiteral)node,context);
            
                case GraphQLArrayValue:
                return optimizeGraphQLArrayValue((GraphQLArrayValue)node,context);
            
                case GraphQLObjectValue:
                return optimizeGraphQLObjectValue((GraphQLObjectValue)node,context);
            
                case GraphQLPropertyValue:
                return optimizeGraphQLPropertyValue((GraphQLPropertyValue)node,context);
            
                case GraphQLVariable:
                return optimizeGraphQLVariable((GraphQLVariable)node,context);
            
                case GraphQLOperation:
                return optimizeGraphQLOperation((GraphQLOperation)node,context);
            
                case GraphQLVariableDefinition:
                return optimizeGraphQLVariableDefinition((GraphQLVariableDefinition)node,context);
            
                case GraphQLNamedType:
                return optimizeGraphQLNamedType((GraphQLNamedType)node,context);
            
                case GraphQLNonNullType:
                return optimizeGraphQLNonNullType((GraphQLNonNullType)node,context);
            
                case GraphQLListType:
                return optimizeGraphQLListType((GraphQLListType)node,context);
            
                case GraphQLSelectionSet:
                return optimizeGraphQLSelectionSet((GraphQLSelectionSet)node,context);
            
                case GraphQLFieldSelection:
                return optimizeGraphQLFieldSelection((GraphQLFieldSelection)node,context);
            
                case GraphQLFragmentSelection:
                return optimizeGraphQLFragmentSelection((GraphQLFragmentSelection)node,context);
            
                case GraphQLFragment:
                return optimizeGraphQLFragment((GraphQLFragment)node,context);
            
                case GraphQLObjectDefinition:
                return optimizeGraphQLObjectDefinition((GraphQLObjectDefinition)node,context);
            
                case GraphQLFieldDefinition:
                return optimizeGraphQLFieldDefinition((GraphQLFieldDefinition)node,context);
            
                case GraphQLInputDefinition:
                return optimizeGraphQLInputDefinition((GraphQLInputDefinition)node,context);
            
                case GraphQLInputFieldDefinition:
                return optimizeGraphQLInputFieldDefinition((GraphQLInputFieldDefinition)node,context);
            
                case GraphQLArgumentDefinition:
                return optimizeGraphQLArgumentDefinition((GraphQLArgumentDefinition)node,context);
            
                case GraphQLDirectiveDefinition:
                return optimizeGraphQLDirectiveDefinition((GraphQLDirectiveDefinition)node,context);
            
                case GraphQLUnionTypeDefinition:
                return optimizeGraphQLUnionTypeDefinition((GraphQLUnionTypeDefinition)node,context);
            
                case GraphQLScalarDefinition:
                return optimizeGraphQLScalarDefinition((GraphQLScalarDefinition)node,context);
            
                case GraphQLEnumDefinition:
                return optimizeGraphQLEnumDefinition((GraphQLEnumDefinition)node,context);
            
                case GraphQLEnumValueDefinition:
                return optimizeGraphQLEnumValueDefinition((GraphQLEnumValueDefinition)node,context);
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
	public GraphQLASTNode optimizeGraphQLDocument(GraphQLDocument node, C context){
        GraphQLDocument ret = node;

        
                    if(node.getDefinitions() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> definitionsOpt = optimizeList(node.getDefinitions(),true, context);
                            if(definitionsOpt != node.getDefinitions()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDefinitions(definitionsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLDirective(GraphQLDirective node, C context){
        GraphQLDirective ret = node;

        
                    if(node.getArguments() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLArgument> argumentsOpt = optimizeList(node.getArguments(),true, context);
                            if(argumentsOpt != node.getArguments()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setArguments(argumentsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLArgument(GraphQLArgument node, C context){
        GraphQLArgument ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.graphql.core.ast.GraphQLValue valueOpt = (io.nop.graphql.core.ast.GraphQLValue)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLLiteral(GraphQLLiteral node, C context){
        GraphQLLiteral ret = node;

        
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLArrayValue(GraphQLArrayValue node, C context){
        GraphQLArrayValue ret = node;

        
                    if(node.getItems() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLValue> itemsOpt = optimizeList(node.getItems(),true, context);
                            if(itemsOpt != node.getItems()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setItems(itemsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLObjectValue(GraphQLObjectValue node, C context){
        GraphQLObjectValue ret = node;

        
                    if(node.getProperties() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> propertiesOpt = optimizeList(node.getProperties(),true, context);
                            if(propertiesOpt != node.getProperties()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setProperties(propertiesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLPropertyValue(GraphQLPropertyValue node, C context){
        GraphQLPropertyValue ret = node;

        
                    if(node.getValue() != null){
                    
                            io.nop.graphql.core.ast.GraphQLValue valueOpt = (io.nop.graphql.core.ast.GraphQLValue)optimize(node.getValue(),context);
                            if(valueOpt != node.getValue()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setValue(valueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLVariable(GraphQLVariable node, C context){
        GraphQLVariable ret = node;

        
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLOperation(GraphQLOperation node, C context){
        GraphQLOperation ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getVariableDefinitions() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> variableDefinitionsOpt = optimizeList(node.getVariableDefinitions(),true, context);
                            if(variableDefinitionsOpt != node.getVariableDefinitions()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setVariableDefinitions(variableDefinitionsOpt);
                            }
                        
                    }
                
                    if(node.getSelectionSet() != null){
                    
                            io.nop.graphql.core.ast.GraphQLSelectionSet selectionSetOpt = (io.nop.graphql.core.ast.GraphQLSelectionSet)optimize(node.getSelectionSet(),context);
                            if(selectionSetOpt != node.getSelectionSet()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setSelectionSet(selectionSetOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLVariableDefinition(GraphQLVariableDefinition node, C context){
        GraphQLVariableDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getType() != null){
                    
                            io.nop.graphql.core.ast.GraphQLType typeOpt = (io.nop.graphql.core.ast.GraphQLType)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setType(typeOpt);
                            }
                        
                    }
                
                    if(node.getDefaultValue() != null){
                    
                            io.nop.graphql.core.ast.GraphQLValue defaultValueOpt = (io.nop.graphql.core.ast.GraphQLValue)optimize(node.getDefaultValue(),context);
                            if(defaultValueOpt != node.getDefaultValue()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setDefaultValue(defaultValueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLNamedType(GraphQLNamedType node, C context){
        GraphQLNamedType ret = node;

        
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLNonNullType(GraphQLNonNullType node, C context){
        GraphQLNonNullType ret = node;

        
                    if(node.getType() != null){
                    
                            io.nop.graphql.core.ast.GraphQLType typeOpt = (io.nop.graphql.core.ast.GraphQLType)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setType(typeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLListType(GraphQLListType node, C context){
        GraphQLListType ret = node;

        
                    if(node.getType() != null){
                    
                            io.nop.graphql.core.ast.GraphQLType typeOpt = (io.nop.graphql.core.ast.GraphQLType)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setType(typeOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLSelectionSet(GraphQLSelectionSet node, C context){
        GraphQLSelectionSet ret = node;

        
                    if(node.getSelections() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLSelection> selectionsOpt = optimizeList(node.getSelections(),true, context);
                            if(selectionsOpt != node.getSelections()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setSelections(selectionsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLFieldSelection(GraphQLFieldSelection node, C context){
        GraphQLFieldSelection ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getArguments() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLArgument> argumentsOpt = optimizeList(node.getArguments(),true, context);
                            if(argumentsOpt != node.getArguments()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setArguments(argumentsOpt);
                            }
                        
                    }
                
                    if(node.getSelectionSet() != null){
                    
                            io.nop.graphql.core.ast.GraphQLSelectionSet selectionSetOpt = (io.nop.graphql.core.ast.GraphQLSelectionSet)optimize(node.getSelectionSet(),context);
                            if(selectionSetOpt != node.getSelectionSet()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setSelectionSet(selectionSetOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLFragmentSelection(GraphQLFragmentSelection node, C context){
        GraphQLFragmentSelection ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLFragment(GraphQLFragment node, C context){
        GraphQLFragment ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getSelectionSet() != null){
                    
                            io.nop.graphql.core.ast.GraphQLSelectionSet selectionSetOpt = (io.nop.graphql.core.ast.GraphQLSelectionSet)optimize(node.getSelectionSet(),context);
                            if(selectionSetOpt != node.getSelectionSet()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setSelectionSet(selectionSetOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLObjectDefinition(GraphQLObjectDefinition node, C context){
        GraphQLObjectDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getFields() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> fieldsOpt = optimizeList(node.getFields(),true, context);
                            if(fieldsOpt != node.getFields()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setFields(fieldsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLFieldDefinition(GraphQLFieldDefinition node, C context){
        GraphQLFieldDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getType() != null){
                    
                            io.nop.graphql.core.ast.GraphQLType typeOpt = (io.nop.graphql.core.ast.GraphQLType)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setType(typeOpt);
                            }
                        
                    }
                
                    if(node.getArguments() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> argumentsOpt = optimizeList(node.getArguments(),true, context);
                            if(argumentsOpt != node.getArguments()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setArguments(argumentsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLInputDefinition(GraphQLInputDefinition node, C context){
        GraphQLInputDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getFields() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLInputFieldDefinition> fieldsOpt = optimizeList(node.getFields(),true, context);
                            if(fieldsOpt != node.getFields()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setFields(fieldsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLInputFieldDefinition(GraphQLInputFieldDefinition node, C context){
        GraphQLInputFieldDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getType() != null){
                    
                            io.nop.graphql.core.ast.GraphQLType typeOpt = (io.nop.graphql.core.ast.GraphQLType)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setType(typeOpt);
                            }
                        
                    }
                
                    if(node.getDefaultValue() != null){
                    
                            io.nop.graphql.core.ast.GraphQLValue defaultValueOpt = (io.nop.graphql.core.ast.GraphQLValue)optimize(node.getDefaultValue(),context);
                            if(defaultValueOpt != node.getDefaultValue()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setDefaultValue(defaultValueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLArgumentDefinition(GraphQLArgumentDefinition node, C context){
        GraphQLArgumentDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getType() != null){
                    
                            io.nop.graphql.core.ast.GraphQLType typeOpt = (io.nop.graphql.core.ast.GraphQLType)optimize(node.getType(),context);
                            if(typeOpt != node.getType()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setType(typeOpt);
                            }
                        
                    }
                
                    if(node.getDefaultValue() != null){
                    
                            io.nop.graphql.core.ast.GraphQLValue defaultValueOpt = (io.nop.graphql.core.ast.GraphQLValue)optimize(node.getDefaultValue(),context);
                            if(defaultValueOpt != node.getDefaultValue()){
                               incChangeCount();
                               if(shouldClone(ret,node))  ret = node.deepClone();
                               ret.setDefaultValue(defaultValueOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLDirectiveDefinition(GraphQLDirectiveDefinition node, C context){
        GraphQLDirectiveDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getArguments() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> argumentsOpt = optimizeList(node.getArguments(),true, context);
                            if(argumentsOpt != node.getArguments()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setArguments(argumentsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLUnionTypeDefinition(GraphQLUnionTypeDefinition node, C context){
        GraphQLUnionTypeDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getTypes() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> typesOpt = optimizeList(node.getTypes(),true, context);
                            if(typesOpt != node.getTypes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setTypes(typesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLScalarDefinition(GraphQLScalarDefinition node, C context){
        GraphQLScalarDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLEnumDefinition(GraphQLEnumDefinition node, C context){
        GraphQLEnumDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
                    if(node.getEnumValues() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> enumValuesOpt = optimizeList(node.getEnumValues(),true, context);
                            if(enumValuesOpt != node.getEnumValues()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setEnumValues(enumValuesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public GraphQLASTNode optimizeGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, C context){
        GraphQLEnumValueDefinition ret = node;

        
                    if(node.getDirectives() != null){
                    
                            java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directivesOpt = optimizeList(node.getDirectives(),true, context);
                            if(directivesOpt != node.getDirectives()){
                                incChangeCount();
                                if(shouldClone(ret,node))  ret = node.deepClone();
                                ret.setDirectives(directivesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
}
// resume CPD analysis - CPD-ON
