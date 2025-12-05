//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast;

// tell cpd to start ignoring code - CPD-OFF
public class GraphQLASTProcessor<T,C>{

    public T processAST(GraphQLASTNode node, C context){
        if(node == null)
            return null;
       switch(node.getASTKind()){
    
            case GraphQLDocument:
                return processGraphQLDocument((GraphQLDocument)node,context);
        
            case GraphQLDirective:
                return processGraphQLDirective((GraphQLDirective)node,context);
        
            case GraphQLArgument:
                return processGraphQLArgument((GraphQLArgument)node,context);
        
            case GraphQLLiteral:
                return processGraphQLLiteral((GraphQLLiteral)node,context);
        
            case GraphQLArrayValue:
                return processGraphQLArrayValue((GraphQLArrayValue)node,context);
        
            case GraphQLObjectValue:
                return processGraphQLObjectValue((GraphQLObjectValue)node,context);
        
            case GraphQLPropertyValue:
                return processGraphQLPropertyValue((GraphQLPropertyValue)node,context);
        
            case GraphQLVariable:
                return processGraphQLVariable((GraphQLVariable)node,context);
        
            case GraphQLOperation:
                return processGraphQLOperation((GraphQLOperation)node,context);
        
            case GraphQLVariableDefinition:
                return processGraphQLVariableDefinition((GraphQLVariableDefinition)node,context);
        
            case GraphQLNamedType:
                return processGraphQLNamedType((GraphQLNamedType)node,context);
        
            case GraphQLNonNullType:
                return processGraphQLNonNullType((GraphQLNonNullType)node,context);
        
            case GraphQLListType:
                return processGraphQLListType((GraphQLListType)node,context);
        
            case GraphQLSelectionSet:
                return processGraphQLSelectionSet((GraphQLSelectionSet)node,context);
        
            case GraphQLFieldSelection:
                return processGraphQLFieldSelection((GraphQLFieldSelection)node,context);
        
            case GraphQLFragmentSelection:
                return processGraphQLFragmentSelection((GraphQLFragmentSelection)node,context);
        
            case GraphQLFragment:
                return processGraphQLFragment((GraphQLFragment)node,context);
        
            case GraphQLObjectDefinition:
                return processGraphQLObjectDefinition((GraphQLObjectDefinition)node,context);
        
            case GraphQLFieldDefinition:
                return processGraphQLFieldDefinition((GraphQLFieldDefinition)node,context);
        
            case GraphQLInputDefinition:
                return processGraphQLInputDefinition((GraphQLInputDefinition)node,context);
        
            case GraphQLInputFieldDefinition:
                return processGraphQLInputFieldDefinition((GraphQLInputFieldDefinition)node,context);
        
            case GraphQLArgumentDefinition:
                return processGraphQLArgumentDefinition((GraphQLArgumentDefinition)node,context);
        
            case GraphQLDirectiveDefinition:
                return processGraphQLDirectiveDefinition((GraphQLDirectiveDefinition)node,context);
        
            case GraphQLUnionTypeDefinition:
                return processGraphQLUnionTypeDefinition((GraphQLUnionTypeDefinition)node,context);
        
            case GraphQLScalarDefinition:
                return processGraphQLScalarDefinition((GraphQLScalarDefinition)node,context);
        
            case GraphQLEnumDefinition:
                return processGraphQLEnumDefinition((GraphQLEnumDefinition)node,context);
        
            case GraphQLEnumValueDefinition:
                return processGraphQLEnumValueDefinition((GraphQLEnumValueDefinition)node,context);
        
          default:
             throw new IllegalArgumentException("invalid ast kind");
       }
    }

    
	public T processGraphQLDocument(GraphQLDocument node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLDirective(GraphQLDirective node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLArgument(GraphQLArgument node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLLiteral(GraphQLLiteral node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLArrayValue(GraphQLArrayValue node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLObjectValue(GraphQLObjectValue node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLPropertyValue(GraphQLPropertyValue node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLVariable(GraphQLVariable node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLOperation(GraphQLOperation node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLVariableDefinition(GraphQLVariableDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLNamedType(GraphQLNamedType node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLNonNullType(GraphQLNonNullType node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLListType(GraphQLListType node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLSelectionSet(GraphQLSelectionSet node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLFieldSelection(GraphQLFieldSelection node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLFragmentSelection(GraphQLFragmentSelection node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLFragment(GraphQLFragment node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLObjectDefinition(GraphQLObjectDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLFieldDefinition(GraphQLFieldDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLInputDefinition(GraphQLInputDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLInputFieldDefinition(GraphQLInputFieldDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLArgumentDefinition(GraphQLArgumentDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLDirectiveDefinition(GraphQLDirectiveDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLUnionTypeDefinition(GraphQLUnionTypeDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLScalarDefinition(GraphQLScalarDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLEnumDefinition(GraphQLEnumDefinition node, C context){
        return defaultProcess(node, context);
	}
    
	public T processGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, C context){
        return defaultProcess(node, context);
	}
    

    public T defaultProcess(GraphQLASTNode node, C context){
        return null;
    }
}
// resume CPD analysis - CPD-ON
