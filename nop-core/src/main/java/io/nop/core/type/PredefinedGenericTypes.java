//__XGEN_TPL_FORCE_OVERRIDE__
package io.nop.core.type;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopScriptError;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.type.StdDataType;

import java.lang.reflect.Type;


import io.nop.core.type.impl.PredefinedGenericType;
import io.nop.core.type.impl.PredefinedRawType;
import io.nop.core.type.impl.PredefinedVariableType;
import io.nop.core.type.impl.PredefinedWildcardType;
import io.nop.commons.util.objects.Pair;

import java.util.Map;

import static io.nop.core.type.impl.PredefinedGenericType.primitiveType;
import static io.nop.core.type.impl.PredefinedGenericType.simpleType;
import static io.nop.core.type.impl.PredefinedGenericType.javaType;
import static io.nop.core.type.impl.PredefinedGenericType.arrayType;
import static io.nop.core.type.impl.PredefinedGenericType.parameterizedType;
import static io.nop.core.type.impl.PredefinedGenericType.variableType;

import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_PREDEFINED_TYPE;



@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class PredefinedGenericTypes {

    public static final PredefinedWildcardType NO_BOUND_WILDCARD_TYPE = PredefinedWildcardType.NO_BOUND_WILDCARD_TYPE;
    public static final PredefinedGenericType PRIMITIVE_VOID_TYPE =
        new PredefinedRawType("void", StdDataType.VOID, void.class, "PRIMITIVE_GENERIC_TYPE");


    
    public static final PredefinedVariableType VARIABLE_A_TYPE = variableType("A");

    public static final PredefinedVariableType VARIABLE_B_TYPE = variableType("B");

    public static final PredefinedVariableType VARIABLE_C_TYPE = variableType("C");

    public static final PredefinedVariableType VARIABLE_D_TYPE = variableType("D");

    public static final PredefinedVariableType VARIABLE_E_TYPE = variableType("E");

    public static final PredefinedVariableType VARIABLE_F_TYPE = variableType("F");

    public static final PredefinedVariableType VARIABLE_G_TYPE = variableType("G");

    public static final PredefinedVariableType VARIABLE_H_TYPE = variableType("H");

    public static final PredefinedVariableType VARIABLE_I_TYPE = variableType("I");

    public static final PredefinedVariableType VARIABLE_J_TYPE = variableType("J");

    public static final PredefinedVariableType VARIABLE_K_TYPE = variableType("K");

    public static final PredefinedVariableType VARIABLE_L_TYPE = variableType("L");

    public static final PredefinedVariableType VARIABLE_M_TYPE = variableType("M");

    public static final PredefinedVariableType VARIABLE_N_TYPE = variableType("N");

    public static final PredefinedVariableType VARIABLE_O_TYPE = variableType("O");

    public static final PredefinedVariableType VARIABLE_P_TYPE = variableType("P");

    public static final PredefinedVariableType VARIABLE_Q_TYPE = variableType("Q");

    public static final PredefinedVariableType VARIABLE_R_TYPE = variableType("R");

    public static final PredefinedVariableType VARIABLE_S_TYPE = variableType("S");

    public static final PredefinedVariableType VARIABLE_T_TYPE = variableType("T");

    public static final PredefinedVariableType VARIABLE_U_TYPE = variableType("U");

    public static final PredefinedVariableType VARIABLE_V_TYPE = variableType("V");

    public static final PredefinedVariableType VARIABLE_W_TYPE = variableType("W");

    public static final PredefinedVariableType VARIABLE_X_TYPE = variableType("X");

    public static final PredefinedVariableType VARIABLE_Y_TYPE = variableType("Y");

    public static final PredefinedVariableType VARIABLE_Z_TYPE = variableType("Z");

    public static final PredefinedVariableType VARIABLE_T0_TYPE = variableType("T0");

    public static final PredefinedVariableType VARIABLE_T1_TYPE = variableType("T1");

    public static final PredefinedVariableType VARIABLE_T2_TYPE = variableType("T2");


    private static final Map<String,PredefinedVariableType> predefinedVars = buildVarMap(
       
           Pair.of("A", VARIABLE_A_TYPE)
       
           ,Pair.of("B", VARIABLE_B_TYPE)
       
           ,Pair.of("C", VARIABLE_C_TYPE)
       
           ,Pair.of("D", VARIABLE_D_TYPE)
       
           ,Pair.of("E", VARIABLE_E_TYPE)
       
           ,Pair.of("F", VARIABLE_F_TYPE)
       
           ,Pair.of("G", VARIABLE_G_TYPE)
       
           ,Pair.of("H", VARIABLE_H_TYPE)
       
           ,Pair.of("I", VARIABLE_I_TYPE)
       
           ,Pair.of("J", VARIABLE_J_TYPE)
       
           ,Pair.of("K", VARIABLE_K_TYPE)
       
           ,Pair.of("L", VARIABLE_L_TYPE)
       
           ,Pair.of("M", VARIABLE_M_TYPE)
       
           ,Pair.of("N", VARIABLE_N_TYPE)
       
           ,Pair.of("O", VARIABLE_O_TYPE)
       
           ,Pair.of("P", VARIABLE_P_TYPE)
       
           ,Pair.of("Q", VARIABLE_Q_TYPE)
       
           ,Pair.of("R", VARIABLE_R_TYPE)
       
           ,Pair.of("S", VARIABLE_S_TYPE)
       
           ,Pair.of("T", VARIABLE_T_TYPE)
       
           ,Pair.of("U", VARIABLE_U_TYPE)
       
           ,Pair.of("V", VARIABLE_V_TYPE)
       
           ,Pair.of("W", VARIABLE_W_TYPE)
       
           ,Pair.of("X", VARIABLE_X_TYPE)
       
           ,Pair.of("Y", VARIABLE_Y_TYPE)
       
           ,Pair.of("Z", VARIABLE_Z_TYPE)
       
           ,Pair.of("T0", VARIABLE_T0_TYPE)
       
           ,Pair.of("T1", VARIABLE_T1_TYPE)
       
           ,Pair.of("T2", VARIABLE_T2_TYPE)
       
    );

    // Java的类型推导存在问题，自动推导非常慢无法结束
    private static Map<String,PredefinedVariableType> buildVarMap(Pair<String,PredefinedVariableType>... items){
        return CollectionHelper.buildImmutableMap(items);
    }

    public static PredefinedVariableType getPredefinedVar(String varName){
        return predefinedVars.get(varName);
    }


      public static final PredefinedRawType ANY_TYPE =
          simpleType(StdDataType.ANY,"ANY_TYPE");


    public static final PredefinedGenericType PRIMITIVE_BOOLEAN_TYPE =
        primitiveType(StdDataType.BOOLEAN,"PRIMITIVE_BOOLEAN_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_BOOLEAN_TYPE =
        arrayType(PRIMITIVE_BOOLEAN_TYPE,boolean[].class);
  
      public static final PredefinedRawType BOOLEAN_TYPE =
          simpleType(StdDataType.BOOLEAN,"BOOLEAN_TYPE");


    public static final PredefinedGenericType PRIMITIVE_CHAR_TYPE =
        primitiveType(StdDataType.CHAR,"PRIMITIVE_CHAR_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_CHAR_TYPE =
        arrayType(PRIMITIVE_CHAR_TYPE,char[].class);
  
      public static final PredefinedRawType CHAR_TYPE =
          simpleType(StdDataType.CHAR,"CHAR_TYPE");


    public static final PredefinedGenericType PRIMITIVE_BYTE_TYPE =
        primitiveType(StdDataType.BYTE,"PRIMITIVE_BYTE_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_BYTE_TYPE =
        arrayType(PRIMITIVE_BYTE_TYPE,byte[].class);
  
      public static final PredefinedRawType BYTE_TYPE =
          simpleType(StdDataType.BYTE,"BYTE_TYPE");


    public static final PredefinedGenericType PRIMITIVE_SHORT_TYPE =
        primitiveType(StdDataType.SHORT,"PRIMITIVE_SHORT_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_SHORT_TYPE =
        arrayType(PRIMITIVE_SHORT_TYPE,short[].class);
  
      public static final PredefinedRawType SHORT_TYPE =
          simpleType(StdDataType.SHORT,"SHORT_TYPE");


    public static final PredefinedGenericType PRIMITIVE_INT_TYPE =
        primitiveType(StdDataType.INT,"PRIMITIVE_INT_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_INT_TYPE =
        arrayType(PRIMITIVE_INT_TYPE,int[].class);
  
      public static final PredefinedRawType INT_TYPE =
          simpleType(StdDataType.INT,"INT_TYPE");


    public static final PredefinedGenericType PRIMITIVE_LONG_TYPE =
        primitiveType(StdDataType.LONG,"PRIMITIVE_LONG_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_LONG_TYPE =
        arrayType(PRIMITIVE_LONG_TYPE,long[].class);
  
      public static final PredefinedRawType LONG_TYPE =
          simpleType(StdDataType.LONG,"LONG_TYPE");


    public static final PredefinedGenericType PRIMITIVE_FLOAT_TYPE =
        primitiveType(StdDataType.FLOAT,"PRIMITIVE_FLOAT_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_FLOAT_TYPE =
        arrayType(PRIMITIVE_FLOAT_TYPE,float[].class);
  
      public static final PredefinedRawType FLOAT_TYPE =
          simpleType(StdDataType.FLOAT,"FLOAT_TYPE");


    public static final PredefinedGenericType PRIMITIVE_DOUBLE_TYPE =
        primitiveType(StdDataType.DOUBLE,"PRIMITIVE_DOUBLE_TYPE");

    public static final PredefinedGenericType ARRAY_PRIMITIVE_DOUBLE_TYPE =
        arrayType(PRIMITIVE_DOUBLE_TYPE,double[].class);
  
      public static final PredefinedRawType DOUBLE_TYPE =
          simpleType(StdDataType.DOUBLE,"DOUBLE_TYPE");


      public static final PredefinedRawType DECIMAL_TYPE =
          simpleType(StdDataType.DECIMAL,"DECIMAL_TYPE");


      public static final PredefinedRawType BIGINT_TYPE =
          simpleType(StdDataType.BIGINT,"BIGINT_TYPE");


      public static final PredefinedRawType STRING_TYPE =
          simpleType(StdDataType.STRING,"STRING_TYPE");


      public static final PredefinedRawType DATE_TYPE =
          simpleType(StdDataType.DATE,"DATE_TYPE");


      public static final PredefinedRawType DATETIME_TYPE =
          simpleType(StdDataType.DATETIME,"DATETIME_TYPE");


      public static final PredefinedRawType TIMESTAMP_TYPE =
          simpleType(StdDataType.TIMESTAMP,"TIMESTAMP_TYPE");


      public static final PredefinedRawType TIME_TYPE =
          simpleType(StdDataType.TIME,"TIME_TYPE");


      public static final PredefinedRawType DURATION_TYPE =
          simpleType(StdDataType.DURATION,"DURATION_TYPE");


      public static final PredefinedRawType MAP_TYPE =
          simpleType(StdDataType.MAP,"MAP_TYPE");


      public static final PredefinedRawType LIST_TYPE =
          simpleType(StdDataType.LIST,"LIST_TYPE");


      public static final PredefinedRawType FILE_TYPE =
          simpleType(StdDataType.FILE,"FILE_TYPE");


      public static final PredefinedRawType FILES_TYPE =
          simpleType(StdDataType.FILES,"FILES_TYPE");


      public static final PredefinedRawType POINT_TYPE =
          simpleType(StdDataType.POINT,"POINT_TYPE");


      public static final PredefinedRawType GEOMETRY_TYPE =
          simpleType(StdDataType.GEOMETRY,"GEOMETRY_TYPE");


      public static final PredefinedRawType BYTES_TYPE =
          simpleType(StdDataType.BYTES,"BYTES_TYPE");


      public static final PredefinedRawType VOID_TYPE =
          simpleType(StdDataType.VOID,"VOID_TYPE");


      public static final PredefinedRawType NULL_TYPE =
          simpleType(StdDataType.NULL,"NULL_TYPE");


      public static final PredefinedRawType UNKNOWN_TYPE =
          simpleType(StdDataType.UNKNOWN,"UNKNOWN_TYPE");


      public static final PredefinedRawType NEVER_TYPE =
          simpleType(StdDataType.NEVER,"NEVER_TYPE");


    public static final PredefinedRawType CLONEABLE_TYPE = javaType(java.lang.Cloneable.class,"CLONEABLE_TYPE");

    public static final PredefinedRawType COMPARABLE_TYPE = javaType(java.lang.Comparable.class,"COMPARABLE_TYPE");

    public static final PredefinedRawType RUNNABLE_TYPE = javaType(java.lang.Runnable.class,"RUNNABLE_TYPE");

    public static final PredefinedRawType NUMBER_TYPE = javaType(java.lang.Number.class,"NUMBER_TYPE");

    public static final PredefinedRawType SERIALIZABLE_TYPE = javaType(java.io.Serializable.class,"SERIALIZABLE_TYPE");

    public static final PredefinedRawType COLLECTION_TYPE = javaType(java.util.Collection.class,"COLLECTION_TYPE");

    public static final PredefinedRawType SET_TYPE = javaType(java.util.Set.class,"SET_TYPE");

    public static final PredefinedRawType HASH_MAP_TYPE = javaType(java.util.HashMap.class,"HASH_MAP_TYPE");

    public static final PredefinedRawType LINKED_HASH_MAP_TYPE = javaType(java.util.LinkedHashMap.class,"LINKED_HASH_MAP_TYPE");

    public static final PredefinedRawType SORTED_MAP_TYPE = javaType(java.util.SortedMap.class,"SORTED_MAP_TYPE");

    public static final PredefinedRawType FUNCTION_TYPE = javaType(java.util.function.Function.class,"FUNCTION_TYPE");

    public static final PredefinedRawType CONSUMER_TYPE = javaType(java.util.function.Consumer.class,"CONSUMER_TYPE");

    public static final PredefinedRawType SUPPLIER_TYPE = javaType(java.util.function.Supplier.class,"SUPPLIER_TYPE");

    public static final PredefinedRawType CALLABLE_TYPE = javaType(java.util.concurrent.Callable.class,"CALLABLE_TYPE");

    public static final PredefinedRawType BI_CONSUMER_TYPE = javaType(java.util.function.BiConsumer.class,"BI_CONSUMER_TYPE");

    public static final PredefinedRawType BI_FUNCTION_TYPE = javaType(java.util.function.BiFunction.class,"BI_FUNCTION_TYPE");

    public static final PredefinedRawType PREDICATE_TYPE = javaType(java.util.function.Predicate.class,"PREDICATE_TYPE");

    public static final PredefinedRawType COMPARATOR_TYPE = javaType(java.util.Comparator.class,"COMPARATOR_TYPE");

    public static final PredefinedRawType COMPLETION_STAGE_TYPE = javaType(java.util.concurrent.CompletionStage.class,"COMPLETION_STAGE_TYPE");

    public static final PredefinedRawType COMPLETABLE_FUTURE_TYPE = javaType(java.util.concurrent.CompletableFuture.class,"COMPLETABLE_FUTURE_TYPE");

    public static final PredefinedRawType FUTURE_TYPE = javaType(java.util.concurrent.Future.class,"FUTURE_TYPE");

    public static final PredefinedRawType X_NODE_TYPE = javaType(io.nop.core.lang.xml.XNode.class,"X_NODE_TYPE");

    public static final PredefinedRawType PAIR_TYPE = javaType(io.nop.commons.util.objects.Pair.class,"PAIR_TYPE");

    public static final PredefinedRawType I_GENERIC_TYPE_TYPE = javaType(io.nop.core.type.IGenericType.class,"I_GENERIC_TYPE_TYPE");

    public static final PredefinedRawType I_EVAL_ACTION_TYPE = javaType(io.nop.core.lang.eval.IEvalAction.class,"I_EVAL_ACTION_TYPE");

    public static final PredefinedRawType I_EVAL_PREDICATE_TYPE = javaType(io.nop.core.lang.eval.IEvalPredicate.class,"I_EVAL_PREDICATE_TYPE");

    public static final PredefinedRawType I_EVAL_FUNCTION_TYPE = javaType(io.nop.core.lang.eval.IEvalFunction.class,"I_EVAL_FUNCTION_TYPE");

    public static final PredefinedRawType I_PROPERTY_GETTER_TYPE = javaType(io.nop.core.reflect.IPropertyGetter.class,"I_PROPERTY_GETTER_TYPE");

    public static final PredefinedRawType I_PROPERTY_SETTER_TYPE = javaType(io.nop.core.reflect.IPropertySetter.class,"I_PROPERTY_SETTER_TYPE");

    public static final PredefinedRawType I_EVAL_SCOPE_TYPE = javaType(io.nop.core.lang.eval.IEvalScope.class,"I_EVAL_SCOPE_TYPE");

    public static final PredefinedRawType I_TEXT_TEMPLATE_OUTPUT_TYPE = javaType(io.nop.core.resource.tpl.ITextTemplateOutput.class,"I_TEXT_TEMPLATE_OUTPUT_TYPE");

    public static final PredefinedRawType I_TEMPLATE_OUTPUT_TYPE = javaType(io.nop.core.resource.tpl.ITemplateOutput.class,"I_TEMPLATE_OUTPUT_TYPE");

    public static final PredefinedRawType I_SQL_GENERATOR_TYPE = javaType(io.nop.core.lang.sql.ISqlGenerator.class,"I_SQL_GENERATOR_TYPE");

    public static final PredefinedRawType I_X_NODE_GENERATOR_TYPE = javaType(io.nop.core.lang.xml.IXNodeGenerator.class,"I_X_NODE_GENERATOR_TYPE");

    public static final PredefinedRawType J_OBJECT_TYPE = javaType(io.nop.core.lang.json.JObject.class,"J_OBJECT_TYPE");

    public static final PredefinedRawType J_ARRAY_TYPE = javaType(io.nop.core.lang.json.JArray.class,"J_ARRAY_TYPE");

    public static final PredefinedRawType I_JSON_CONTAINER_TYPE = javaType(io.nop.core.lang.json.IJsonContainer.class,"I_JSON_CONTAINER_TYPE");

    public static final PredefinedRawType I_SOURCE_LOCATION_GETTER_TYPE = javaType(io.nop.api.core.util.ISourceLocationGetter.class,"I_SOURCE_LOCATION_GETTER_TYPE");

    public static final PredefinedRawType I_DEEP_CLONEABLE_TYPE = javaType(io.nop.api.core.util.IDeepCloneable.class,"I_DEEP_CLONEABLE_TYPE");

    public static final PredefinedRawType I_CLONEABLE_TYPE = javaType(io.nop.api.core.util.ICloneable.class,"I_CLONEABLE_TYPE");

    public static final PredefinedRawType I_FREEZABLE_TYPE = javaType(io.nop.api.core.util.IFreezable.class,"I_FREEZABLE_TYPE");

    public static final PredefinedRawType SOURCE_LOCATION_TYPE = javaType(io.nop.api.core.util.SourceLocation.class,"SOURCE_LOCATION_TYPE");

    public static final PredefinedRawType I_CANCELLABLE_TYPE = javaType(io.nop.api.core.util.ICancellable.class,"I_CANCELLABLE_TYPE");

    public static final PredefinedRawType I_CANCEL_TOKEN_TYPE = javaType(io.nop.api.core.util.ICancelToken.class,"I_CANCEL_TOKEN_TYPE");

    public static final PredefinedRawType API_REQUEST_TYPE = javaType(io.nop.api.core.beans.ApiRequest.class,"API_REQUEST_TYPE");

    public static final PredefinedRawType API_RESPONSE_TYPE = javaType(io.nop.api.core.beans.ApiResponse.class,"API_RESPONSE_TYPE");

    public static final PredefinedRawType PAGE_BEAN_TYPE = javaType(io.nop.api.core.beans.PageBean.class,"PAGE_BEAN_TYPE");

    public static final PredefinedRawType GRAPH_Q_L_CONNECTION_TYPE = javaType(io.nop.api.core.beans.graphql.GraphQLConnection.class,"GRAPH_Q_L_CONNECTION_TYPE");

    public static final PredefinedRawType GRAPH_Q_L_REQUEST_BEAN_TYPE = javaType(io.nop.api.core.beans.graphql.GraphQLRequestBean.class,"GRAPH_Q_L_REQUEST_BEAN_TYPE");

    public static final PredefinedRawType GRAPH_Q_L_RESPONSE_BEAN_TYPE = javaType(io.nop.api.core.beans.graphql.GraphQLResponseBean.class,"GRAPH_Q_L_RESPONSE_BEAN_TYPE");

    public static final PredefinedRawType GRAPH_Q_L_PAGE_INFO_TYPE = javaType(io.nop.api.core.beans.graphql.GraphQLPageInfo.class,"GRAPH_Q_L_PAGE_INFO_TYPE");

    public static final PredefinedRawType GRAPH_Q_L_CONNECTION_INPUT_TYPE = javaType(io.nop.api.core.beans.graphql.GraphQLConnectionInput.class,"GRAPH_Q_L_CONNECTION_INPUT_TYPE");

    public static final PredefinedRawType GRAPH_Q_L_ERROR_BEAN_TYPE = javaType(io.nop.api.core.beans.graphql.GraphQLErrorBean.class,"GRAPH_Q_L_ERROR_BEAN_TYPE");

    public static final PredefinedRawType I_CONTEXT_TYPE = javaType(io.nop.api.core.context.IContext.class,"I_CONTEXT_TYPE");

    public static final PredefinedRawType I_KEYED_LIST_TYPE = javaType(io.nop.commons.collections.IKeyedList.class,"I_KEYED_LIST_TYPE");

    public static final PredefinedRawType I_KEYED_ELEMENT_TYPE = javaType(io.nop.commons.collections.IKeyedElement.class,"I_KEYED_ELEMENT_TYPE");

    public static final PredefinedRawType STD_DATA_TYPE_TYPE = javaType(io.nop.commons.type.StdDataType.class,"STD_DATA_TYPE_TYPE");

    public static final PredefinedRawType NOP_EXCEPTION_TYPE = javaType(io.nop.api.core.exceptions.NopException.class,"NOP_EXCEPTION_TYPE");

    public static final PredefinedRawType NOP_SCRIPT_ERROR_TYPE = javaType(io.nop.api.core.exceptions.NopScriptError.class,"NOP_SCRIPT_ERROR_TYPE");

      public static final PredefinedGenericType ARRAY_ANY_TYPE =
          arrayType(ANY_TYPE,java.lang.Object[].class);

      public static final PredefinedGenericType LIST_ANY_TYPE =
          parameterizedType("LIST_ANY_TYPE",LIST_TYPE, ANY_TYPE);

      public static final PredefinedGenericType SET_ANY_TYPE =
          parameterizedType("SET_ANY_TYPE",SET_TYPE,ANY_TYPE);

      public static final PredefinedGenericType COLLECTION_ANY_TYPE =
          parameterizedType("COLLECTION_ANY_TYPE",COLLECTION_TYPE,ANY_TYPE);

      public static final PredefinedGenericType MAP_STRING_ANY_TYPE =
          parameterizedType("MAP_STRING_ANY_TYPE",MAP_TYPE, STRING_TYPE, ANY_TYPE);

      public static final PredefinedGenericType ARRAY_BOOLEAN_TYPE =
          arrayType(BOOLEAN_TYPE,java.lang.Boolean[].class);

      public static final PredefinedGenericType LIST_BOOLEAN_TYPE =
          parameterizedType("LIST_BOOLEAN_TYPE",LIST_TYPE, BOOLEAN_TYPE);

      public static final PredefinedGenericType SET_BOOLEAN_TYPE =
          parameterizedType("SET_BOOLEAN_TYPE",SET_TYPE,BOOLEAN_TYPE);

      public static final PredefinedGenericType COLLECTION_BOOLEAN_TYPE =
          parameterizedType("COLLECTION_BOOLEAN_TYPE",COLLECTION_TYPE,BOOLEAN_TYPE);

      public static final PredefinedGenericType MAP_STRING_BOOLEAN_TYPE =
          parameterizedType("MAP_STRING_BOOLEAN_TYPE",MAP_TYPE, STRING_TYPE, BOOLEAN_TYPE);

      public static final PredefinedGenericType ARRAY_CHAR_TYPE =
          arrayType(CHAR_TYPE,java.lang.Character[].class);

      public static final PredefinedGenericType LIST_CHAR_TYPE =
          parameterizedType("LIST_CHAR_TYPE",LIST_TYPE, CHAR_TYPE);

      public static final PredefinedGenericType SET_CHAR_TYPE =
          parameterizedType("SET_CHAR_TYPE",SET_TYPE,CHAR_TYPE);

      public static final PredefinedGenericType COLLECTION_CHAR_TYPE =
          parameterizedType("COLLECTION_CHAR_TYPE",COLLECTION_TYPE,CHAR_TYPE);

      public static final PredefinedGenericType MAP_STRING_CHAR_TYPE =
          parameterizedType("MAP_STRING_CHAR_TYPE",MAP_TYPE, STRING_TYPE, CHAR_TYPE);

      public static final PredefinedGenericType ARRAY_BYTE_TYPE =
          arrayType(BYTE_TYPE,java.lang.Byte[].class);

      public static final PredefinedGenericType LIST_BYTE_TYPE =
          parameterizedType("LIST_BYTE_TYPE",LIST_TYPE, BYTE_TYPE);

      public static final PredefinedGenericType SET_BYTE_TYPE =
          parameterizedType("SET_BYTE_TYPE",SET_TYPE,BYTE_TYPE);

      public static final PredefinedGenericType COLLECTION_BYTE_TYPE =
          parameterizedType("COLLECTION_BYTE_TYPE",COLLECTION_TYPE,BYTE_TYPE);

      public static final PredefinedGenericType MAP_STRING_BYTE_TYPE =
          parameterizedType("MAP_STRING_BYTE_TYPE",MAP_TYPE, STRING_TYPE, BYTE_TYPE);

      public static final PredefinedGenericType ARRAY_SHORT_TYPE =
          arrayType(SHORT_TYPE,java.lang.Short[].class);

      public static final PredefinedGenericType LIST_SHORT_TYPE =
          parameterizedType("LIST_SHORT_TYPE",LIST_TYPE, SHORT_TYPE);

      public static final PredefinedGenericType SET_SHORT_TYPE =
          parameterizedType("SET_SHORT_TYPE",SET_TYPE,SHORT_TYPE);

      public static final PredefinedGenericType COLLECTION_SHORT_TYPE =
          parameterizedType("COLLECTION_SHORT_TYPE",COLLECTION_TYPE,SHORT_TYPE);

      public static final PredefinedGenericType MAP_STRING_SHORT_TYPE =
          parameterizedType("MAP_STRING_SHORT_TYPE",MAP_TYPE, STRING_TYPE, SHORT_TYPE);

      public static final PredefinedGenericType ARRAY_INT_TYPE =
          arrayType(INT_TYPE,java.lang.Integer[].class);

      public static final PredefinedGenericType LIST_INT_TYPE =
          parameterizedType("LIST_INT_TYPE",LIST_TYPE, INT_TYPE);

      public static final PredefinedGenericType SET_INT_TYPE =
          parameterizedType("SET_INT_TYPE",SET_TYPE,INT_TYPE);

      public static final PredefinedGenericType COLLECTION_INT_TYPE =
          parameterizedType("COLLECTION_INT_TYPE",COLLECTION_TYPE,INT_TYPE);

      public static final PredefinedGenericType MAP_STRING_INT_TYPE =
          parameterizedType("MAP_STRING_INT_TYPE",MAP_TYPE, STRING_TYPE, INT_TYPE);

      public static final PredefinedGenericType ARRAY_LONG_TYPE =
          arrayType(LONG_TYPE,java.lang.Long[].class);

      public static final PredefinedGenericType LIST_LONG_TYPE =
          parameterizedType("LIST_LONG_TYPE",LIST_TYPE, LONG_TYPE);

      public static final PredefinedGenericType SET_LONG_TYPE =
          parameterizedType("SET_LONG_TYPE",SET_TYPE,LONG_TYPE);

      public static final PredefinedGenericType COLLECTION_LONG_TYPE =
          parameterizedType("COLLECTION_LONG_TYPE",COLLECTION_TYPE,LONG_TYPE);

      public static final PredefinedGenericType MAP_STRING_LONG_TYPE =
          parameterizedType("MAP_STRING_LONG_TYPE",MAP_TYPE, STRING_TYPE, LONG_TYPE);

      public static final PredefinedGenericType ARRAY_FLOAT_TYPE =
          arrayType(FLOAT_TYPE,java.lang.Float[].class);

      public static final PredefinedGenericType LIST_FLOAT_TYPE =
          parameterizedType("LIST_FLOAT_TYPE",LIST_TYPE, FLOAT_TYPE);

      public static final PredefinedGenericType SET_FLOAT_TYPE =
          parameterizedType("SET_FLOAT_TYPE",SET_TYPE,FLOAT_TYPE);

      public static final PredefinedGenericType COLLECTION_FLOAT_TYPE =
          parameterizedType("COLLECTION_FLOAT_TYPE",COLLECTION_TYPE,FLOAT_TYPE);

      public static final PredefinedGenericType MAP_STRING_FLOAT_TYPE =
          parameterizedType("MAP_STRING_FLOAT_TYPE",MAP_TYPE, STRING_TYPE, FLOAT_TYPE);

      public static final PredefinedGenericType ARRAY_DOUBLE_TYPE =
          arrayType(DOUBLE_TYPE,java.lang.Double[].class);

      public static final PredefinedGenericType LIST_DOUBLE_TYPE =
          parameterizedType("LIST_DOUBLE_TYPE",LIST_TYPE, DOUBLE_TYPE);

      public static final PredefinedGenericType SET_DOUBLE_TYPE =
          parameterizedType("SET_DOUBLE_TYPE",SET_TYPE,DOUBLE_TYPE);

      public static final PredefinedGenericType COLLECTION_DOUBLE_TYPE =
          parameterizedType("COLLECTION_DOUBLE_TYPE",COLLECTION_TYPE,DOUBLE_TYPE);

      public static final PredefinedGenericType MAP_STRING_DOUBLE_TYPE =
          parameterizedType("MAP_STRING_DOUBLE_TYPE",MAP_TYPE, STRING_TYPE, DOUBLE_TYPE);

      public static final PredefinedGenericType ARRAY_DECIMAL_TYPE =
          arrayType(DECIMAL_TYPE,java.math.BigDecimal[].class);

      public static final PredefinedGenericType LIST_DECIMAL_TYPE =
          parameterizedType("LIST_DECIMAL_TYPE",LIST_TYPE, DECIMAL_TYPE);

      public static final PredefinedGenericType SET_DECIMAL_TYPE =
          parameterizedType("SET_DECIMAL_TYPE",SET_TYPE,DECIMAL_TYPE);

      public static final PredefinedGenericType COLLECTION_DECIMAL_TYPE =
          parameterizedType("COLLECTION_DECIMAL_TYPE",COLLECTION_TYPE,DECIMAL_TYPE);

      public static final PredefinedGenericType MAP_STRING_DECIMAL_TYPE =
          parameterizedType("MAP_STRING_DECIMAL_TYPE",MAP_TYPE, STRING_TYPE, DECIMAL_TYPE);

      public static final PredefinedGenericType ARRAY_BIGINT_TYPE =
          arrayType(BIGINT_TYPE,java.math.BigInteger[].class);

      public static final PredefinedGenericType LIST_BIGINT_TYPE =
          parameterizedType("LIST_BIGINT_TYPE",LIST_TYPE, BIGINT_TYPE);

      public static final PredefinedGenericType SET_BIGINT_TYPE =
          parameterizedType("SET_BIGINT_TYPE",SET_TYPE,BIGINT_TYPE);

      public static final PredefinedGenericType COLLECTION_BIGINT_TYPE =
          parameterizedType("COLLECTION_BIGINT_TYPE",COLLECTION_TYPE,BIGINT_TYPE);

      public static final PredefinedGenericType MAP_STRING_BIGINT_TYPE =
          parameterizedType("MAP_STRING_BIGINT_TYPE",MAP_TYPE, STRING_TYPE, BIGINT_TYPE);

      public static final PredefinedGenericType ARRAY_STRING_TYPE =
          arrayType(STRING_TYPE,java.lang.String[].class);

      public static final PredefinedGenericType LIST_STRING_TYPE =
          parameterizedType("LIST_STRING_TYPE",LIST_TYPE, STRING_TYPE);

      public static final PredefinedGenericType SET_STRING_TYPE =
          parameterizedType("SET_STRING_TYPE",SET_TYPE,STRING_TYPE);

      public static final PredefinedGenericType COLLECTION_STRING_TYPE =
          parameterizedType("COLLECTION_STRING_TYPE",COLLECTION_TYPE,STRING_TYPE);

      public static final PredefinedGenericType MAP_STRING_STRING_TYPE =
          parameterizedType("MAP_STRING_STRING_TYPE",MAP_TYPE, STRING_TYPE, STRING_TYPE);

      public static final PredefinedGenericType ARRAY_DATE_TYPE =
          arrayType(DATE_TYPE,java.time.LocalDate[].class);

      public static final PredefinedGenericType LIST_DATE_TYPE =
          parameterizedType("LIST_DATE_TYPE",LIST_TYPE, DATE_TYPE);

      public static final PredefinedGenericType SET_DATE_TYPE =
          parameterizedType("SET_DATE_TYPE",SET_TYPE,DATE_TYPE);

      public static final PredefinedGenericType COLLECTION_DATE_TYPE =
          parameterizedType("COLLECTION_DATE_TYPE",COLLECTION_TYPE,DATE_TYPE);

      public static final PredefinedGenericType MAP_STRING_DATE_TYPE =
          parameterizedType("MAP_STRING_DATE_TYPE",MAP_TYPE, STRING_TYPE, DATE_TYPE);

      public static final PredefinedGenericType ARRAY_DATETIME_TYPE =
          arrayType(DATETIME_TYPE,java.time.LocalDateTime[].class);

      public static final PredefinedGenericType LIST_DATETIME_TYPE =
          parameterizedType("LIST_DATETIME_TYPE",LIST_TYPE, DATETIME_TYPE);

      public static final PredefinedGenericType SET_DATETIME_TYPE =
          parameterizedType("SET_DATETIME_TYPE",SET_TYPE,DATETIME_TYPE);

      public static final PredefinedGenericType COLLECTION_DATETIME_TYPE =
          parameterizedType("COLLECTION_DATETIME_TYPE",COLLECTION_TYPE,DATETIME_TYPE);

      public static final PredefinedGenericType MAP_STRING_DATETIME_TYPE =
          parameterizedType("MAP_STRING_DATETIME_TYPE",MAP_TYPE, STRING_TYPE, DATETIME_TYPE);

      public static final PredefinedGenericType ARRAY_TIMESTAMP_TYPE =
          arrayType(TIMESTAMP_TYPE,java.sql.Timestamp[].class);

      public static final PredefinedGenericType LIST_TIMESTAMP_TYPE =
          parameterizedType("LIST_TIMESTAMP_TYPE",LIST_TYPE, TIMESTAMP_TYPE);

      public static final PredefinedGenericType SET_TIMESTAMP_TYPE =
          parameterizedType("SET_TIMESTAMP_TYPE",SET_TYPE,TIMESTAMP_TYPE);

      public static final PredefinedGenericType COLLECTION_TIMESTAMP_TYPE =
          parameterizedType("COLLECTION_TIMESTAMP_TYPE",COLLECTION_TYPE,TIMESTAMP_TYPE);

      public static final PredefinedGenericType MAP_STRING_TIMESTAMP_TYPE =
          parameterizedType("MAP_STRING_TIMESTAMP_TYPE",MAP_TYPE, STRING_TYPE, TIMESTAMP_TYPE);

      public static final PredefinedGenericType ARRAY_TIME_TYPE =
          arrayType(TIME_TYPE,java.time.LocalTime[].class);

      public static final PredefinedGenericType LIST_TIME_TYPE =
          parameterizedType("LIST_TIME_TYPE",LIST_TYPE, TIME_TYPE);

      public static final PredefinedGenericType SET_TIME_TYPE =
          parameterizedType("SET_TIME_TYPE",SET_TYPE,TIME_TYPE);

      public static final PredefinedGenericType COLLECTION_TIME_TYPE =
          parameterizedType("COLLECTION_TIME_TYPE",COLLECTION_TYPE,TIME_TYPE);

      public static final PredefinedGenericType MAP_STRING_TIME_TYPE =
          parameterizedType("MAP_STRING_TIME_TYPE",MAP_TYPE, STRING_TYPE, TIME_TYPE);

      public static final PredefinedGenericType ARRAY_DURATION_TYPE =
          arrayType(DURATION_TYPE,java.time.Duration[].class);

      public static final PredefinedGenericType LIST_DURATION_TYPE =
          parameterizedType("LIST_DURATION_TYPE",LIST_TYPE, DURATION_TYPE);

      public static final PredefinedGenericType SET_DURATION_TYPE =
          parameterizedType("SET_DURATION_TYPE",SET_TYPE,DURATION_TYPE);

      public static final PredefinedGenericType COLLECTION_DURATION_TYPE =
          parameterizedType("COLLECTION_DURATION_TYPE",COLLECTION_TYPE,DURATION_TYPE);

      public static final PredefinedGenericType MAP_STRING_DURATION_TYPE =
          parameterizedType("MAP_STRING_DURATION_TYPE",MAP_TYPE, STRING_TYPE, DURATION_TYPE);

      public static final PredefinedGenericType ARRAY_MAP_TYPE =
          arrayType(MAP_TYPE,java.util.Map[].class);

      public static final PredefinedGenericType LIST_MAP_TYPE =
          parameterizedType("LIST_MAP_TYPE",LIST_TYPE, MAP_TYPE);

      public static final PredefinedGenericType SET_MAP_TYPE =
          parameterizedType("SET_MAP_TYPE",SET_TYPE,MAP_TYPE);

      public static final PredefinedGenericType COLLECTION_MAP_TYPE =
          parameterizedType("COLLECTION_MAP_TYPE",COLLECTION_TYPE,MAP_TYPE);

      public static final PredefinedGenericType MAP_STRING_MAP_TYPE =
          parameterizedType("MAP_STRING_MAP_TYPE",MAP_TYPE, STRING_TYPE, MAP_TYPE);

      public static final PredefinedGenericType ARRAY_LIST_TYPE =
          arrayType(LIST_TYPE,java.util.List[].class);

      public static final PredefinedGenericType LIST_LIST_TYPE =
          parameterizedType("LIST_LIST_TYPE",LIST_TYPE, LIST_TYPE);

      public static final PredefinedGenericType SET_LIST_TYPE =
          parameterizedType("SET_LIST_TYPE",SET_TYPE,LIST_TYPE);

      public static final PredefinedGenericType COLLECTION_LIST_TYPE =
          parameterizedType("COLLECTION_LIST_TYPE",COLLECTION_TYPE,LIST_TYPE);

      public static final PredefinedGenericType MAP_STRING_LIST_TYPE =
          parameterizedType("MAP_STRING_LIST_TYPE",MAP_TYPE, STRING_TYPE, LIST_TYPE);

      public static final PredefinedGenericType ARRAY_FILE_TYPE =
          arrayType(FILE_TYPE,io.nop.commons.type.FileReference[].class);

      public static final PredefinedGenericType LIST_FILE_TYPE =
          parameterizedType("LIST_FILE_TYPE",LIST_TYPE, FILE_TYPE);

      public static final PredefinedGenericType SET_FILE_TYPE =
          parameterizedType("SET_FILE_TYPE",SET_TYPE,FILE_TYPE);

      public static final PredefinedGenericType COLLECTION_FILE_TYPE =
          parameterizedType("COLLECTION_FILE_TYPE",COLLECTION_TYPE,FILE_TYPE);

      public static final PredefinedGenericType MAP_STRING_FILE_TYPE =
          parameterizedType("MAP_STRING_FILE_TYPE",MAP_TYPE, STRING_TYPE, FILE_TYPE);

      public static final PredefinedGenericType ARRAY_FILES_TYPE =
          arrayType(FILES_TYPE,io.nop.commons.type.FileListReference[].class);

      public static final PredefinedGenericType LIST_FILES_TYPE =
          parameterizedType("LIST_FILES_TYPE",LIST_TYPE, FILES_TYPE);

      public static final PredefinedGenericType SET_FILES_TYPE =
          parameterizedType("SET_FILES_TYPE",SET_TYPE,FILES_TYPE);

      public static final PredefinedGenericType COLLECTION_FILES_TYPE =
          parameterizedType("COLLECTION_FILES_TYPE",COLLECTION_TYPE,FILES_TYPE);

      public static final PredefinedGenericType MAP_STRING_FILES_TYPE =
          parameterizedType("MAP_STRING_FILES_TYPE",MAP_TYPE, STRING_TYPE, FILES_TYPE);

      public static final PredefinedGenericType ARRAY_POINT_TYPE =
          arrayType(POINT_TYPE,io.nop.api.core.beans.PointBean[].class);

      public static final PredefinedGenericType LIST_POINT_TYPE =
          parameterizedType("LIST_POINT_TYPE",LIST_TYPE, POINT_TYPE);

      public static final PredefinedGenericType SET_POINT_TYPE =
          parameterizedType("SET_POINT_TYPE",SET_TYPE,POINT_TYPE);

      public static final PredefinedGenericType COLLECTION_POINT_TYPE =
          parameterizedType("COLLECTION_POINT_TYPE",COLLECTION_TYPE,POINT_TYPE);

      public static final PredefinedGenericType MAP_STRING_POINT_TYPE =
          parameterizedType("MAP_STRING_POINT_TYPE",MAP_TYPE, STRING_TYPE, POINT_TYPE);

      public static final PredefinedGenericType ARRAY_GEOMETRY_TYPE =
          arrayType(GEOMETRY_TYPE,io.nop.commons.type.GeometryObject[].class);

      public static final PredefinedGenericType LIST_GEOMETRY_TYPE =
          parameterizedType("LIST_GEOMETRY_TYPE",LIST_TYPE, GEOMETRY_TYPE);

      public static final PredefinedGenericType SET_GEOMETRY_TYPE =
          parameterizedType("SET_GEOMETRY_TYPE",SET_TYPE,GEOMETRY_TYPE);

      public static final PredefinedGenericType COLLECTION_GEOMETRY_TYPE =
          parameterizedType("COLLECTION_GEOMETRY_TYPE",COLLECTION_TYPE,GEOMETRY_TYPE);

      public static final PredefinedGenericType MAP_STRING_GEOMETRY_TYPE =
          parameterizedType("MAP_STRING_GEOMETRY_TYPE",MAP_TYPE, STRING_TYPE, GEOMETRY_TYPE);

      public static final PredefinedGenericType ARRAY_BYTES_TYPE =
          arrayType(BYTES_TYPE,io.nop.commons.bytes.ByteString[].class);

      public static final PredefinedGenericType LIST_BYTES_TYPE =
          parameterizedType("LIST_BYTES_TYPE",LIST_TYPE, BYTES_TYPE);

      public static final PredefinedGenericType SET_BYTES_TYPE =
          parameterizedType("SET_BYTES_TYPE",SET_TYPE,BYTES_TYPE);

      public static final PredefinedGenericType COLLECTION_BYTES_TYPE =
          parameterizedType("COLLECTION_BYTES_TYPE",COLLECTION_TYPE,BYTES_TYPE);

      public static final PredefinedGenericType MAP_STRING_BYTES_TYPE =
          parameterizedType("MAP_STRING_BYTES_TYPE",MAP_TYPE, STRING_TYPE, BYTES_TYPE);


    private static final Map<String, PredefinedGenericType> NAME_TO_PREDEFINED_TYPES =
            buildMap(
            
            Pair.of(ANY_TYPE.getTypeName(), ANY_TYPE),
            
            Pair.of(ARRAY_ANY_TYPE.getTypeName(), ARRAY_ANY_TYPE),
            
            Pair.of(LIST_ANY_TYPE.getTypeName(), LIST_ANY_TYPE),
            
            Pair.of(SET_ANY_TYPE.getTypeName(), SET_ANY_TYPE),
            
            Pair.of(COLLECTION_ANY_TYPE.getTypeName(), COLLECTION_ANY_TYPE),
            
            Pair.of(MAP_STRING_ANY_TYPE.getTypeName(), MAP_STRING_ANY_TYPE),
            
            Pair.of(PRIMITIVE_BOOLEAN_TYPE.getTypeName(), PRIMITIVE_BOOLEAN_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_BOOLEAN_TYPE.getTypeName(), ARRAY_PRIMITIVE_BOOLEAN_TYPE),
            
            Pair.of(BOOLEAN_TYPE.getTypeName(), BOOLEAN_TYPE),
            
            Pair.of(ARRAY_BOOLEAN_TYPE.getTypeName(), ARRAY_BOOLEAN_TYPE),
            
            Pair.of(LIST_BOOLEAN_TYPE.getTypeName(), LIST_BOOLEAN_TYPE),
            
            Pair.of(SET_BOOLEAN_TYPE.getTypeName(), SET_BOOLEAN_TYPE),
            
            Pair.of(COLLECTION_BOOLEAN_TYPE.getTypeName(), COLLECTION_BOOLEAN_TYPE),
            
            Pair.of(MAP_STRING_BOOLEAN_TYPE.getTypeName(), MAP_STRING_BOOLEAN_TYPE),
            
            Pair.of(PRIMITIVE_CHAR_TYPE.getTypeName(), PRIMITIVE_CHAR_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_CHAR_TYPE.getTypeName(), ARRAY_PRIMITIVE_CHAR_TYPE),
            
            Pair.of(CHAR_TYPE.getTypeName(), CHAR_TYPE),
            
            Pair.of(ARRAY_CHAR_TYPE.getTypeName(), ARRAY_CHAR_TYPE),
            
            Pair.of(LIST_CHAR_TYPE.getTypeName(), LIST_CHAR_TYPE),
            
            Pair.of(SET_CHAR_TYPE.getTypeName(), SET_CHAR_TYPE),
            
            Pair.of(COLLECTION_CHAR_TYPE.getTypeName(), COLLECTION_CHAR_TYPE),
            
            Pair.of(MAP_STRING_CHAR_TYPE.getTypeName(), MAP_STRING_CHAR_TYPE),
            
            Pair.of(PRIMITIVE_BYTE_TYPE.getTypeName(), PRIMITIVE_BYTE_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_BYTE_TYPE.getTypeName(), ARRAY_PRIMITIVE_BYTE_TYPE),
            
            Pair.of(BYTE_TYPE.getTypeName(), BYTE_TYPE),
            
            Pair.of(ARRAY_BYTE_TYPE.getTypeName(), ARRAY_BYTE_TYPE),
            
            Pair.of(LIST_BYTE_TYPE.getTypeName(), LIST_BYTE_TYPE),
            
            Pair.of(SET_BYTE_TYPE.getTypeName(), SET_BYTE_TYPE),
            
            Pair.of(COLLECTION_BYTE_TYPE.getTypeName(), COLLECTION_BYTE_TYPE),
            
            Pair.of(MAP_STRING_BYTE_TYPE.getTypeName(), MAP_STRING_BYTE_TYPE),
            
            Pair.of(PRIMITIVE_SHORT_TYPE.getTypeName(), PRIMITIVE_SHORT_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_SHORT_TYPE.getTypeName(), ARRAY_PRIMITIVE_SHORT_TYPE),
            
            Pair.of(SHORT_TYPE.getTypeName(), SHORT_TYPE),
            
            Pair.of(ARRAY_SHORT_TYPE.getTypeName(), ARRAY_SHORT_TYPE),
            
            Pair.of(LIST_SHORT_TYPE.getTypeName(), LIST_SHORT_TYPE),
            
            Pair.of(SET_SHORT_TYPE.getTypeName(), SET_SHORT_TYPE),
            
            Pair.of(COLLECTION_SHORT_TYPE.getTypeName(), COLLECTION_SHORT_TYPE),
            
            Pair.of(MAP_STRING_SHORT_TYPE.getTypeName(), MAP_STRING_SHORT_TYPE),
            
            Pair.of(PRIMITIVE_INT_TYPE.getTypeName(), PRIMITIVE_INT_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_INT_TYPE.getTypeName(), ARRAY_PRIMITIVE_INT_TYPE),
            
            Pair.of(INT_TYPE.getTypeName(), INT_TYPE),
            
            Pair.of(ARRAY_INT_TYPE.getTypeName(), ARRAY_INT_TYPE),
            
            Pair.of(LIST_INT_TYPE.getTypeName(), LIST_INT_TYPE),
            
            Pair.of(SET_INT_TYPE.getTypeName(), SET_INT_TYPE),
            
            Pair.of(COLLECTION_INT_TYPE.getTypeName(), COLLECTION_INT_TYPE),
            
            Pair.of(MAP_STRING_INT_TYPE.getTypeName(), MAP_STRING_INT_TYPE),
            
            Pair.of(PRIMITIVE_LONG_TYPE.getTypeName(), PRIMITIVE_LONG_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_LONG_TYPE.getTypeName(), ARRAY_PRIMITIVE_LONG_TYPE),
            
            Pair.of(LONG_TYPE.getTypeName(), LONG_TYPE),
            
            Pair.of(ARRAY_LONG_TYPE.getTypeName(), ARRAY_LONG_TYPE),
            
            Pair.of(LIST_LONG_TYPE.getTypeName(), LIST_LONG_TYPE),
            
            Pair.of(SET_LONG_TYPE.getTypeName(), SET_LONG_TYPE),
            
            Pair.of(COLLECTION_LONG_TYPE.getTypeName(), COLLECTION_LONG_TYPE),
            
            Pair.of(MAP_STRING_LONG_TYPE.getTypeName(), MAP_STRING_LONG_TYPE),
            
            Pair.of(PRIMITIVE_FLOAT_TYPE.getTypeName(), PRIMITIVE_FLOAT_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_FLOAT_TYPE.getTypeName(), ARRAY_PRIMITIVE_FLOAT_TYPE),
            
            Pair.of(FLOAT_TYPE.getTypeName(), FLOAT_TYPE),
            
            Pair.of(ARRAY_FLOAT_TYPE.getTypeName(), ARRAY_FLOAT_TYPE),
            
            Pair.of(LIST_FLOAT_TYPE.getTypeName(), LIST_FLOAT_TYPE),
            
            Pair.of(SET_FLOAT_TYPE.getTypeName(), SET_FLOAT_TYPE),
            
            Pair.of(COLLECTION_FLOAT_TYPE.getTypeName(), COLLECTION_FLOAT_TYPE),
            
            Pair.of(MAP_STRING_FLOAT_TYPE.getTypeName(), MAP_STRING_FLOAT_TYPE),
            
            Pair.of(PRIMITIVE_DOUBLE_TYPE.getTypeName(), PRIMITIVE_DOUBLE_TYPE),
            
            Pair.of(ARRAY_PRIMITIVE_DOUBLE_TYPE.getTypeName(), ARRAY_PRIMITIVE_DOUBLE_TYPE),
            
            Pair.of(DOUBLE_TYPE.getTypeName(), DOUBLE_TYPE),
            
            Pair.of(ARRAY_DOUBLE_TYPE.getTypeName(), ARRAY_DOUBLE_TYPE),
            
            Pair.of(LIST_DOUBLE_TYPE.getTypeName(), LIST_DOUBLE_TYPE),
            
            Pair.of(SET_DOUBLE_TYPE.getTypeName(), SET_DOUBLE_TYPE),
            
            Pair.of(COLLECTION_DOUBLE_TYPE.getTypeName(), COLLECTION_DOUBLE_TYPE),
            
            Pair.of(MAP_STRING_DOUBLE_TYPE.getTypeName(), MAP_STRING_DOUBLE_TYPE),
            
            Pair.of(DECIMAL_TYPE.getTypeName(), DECIMAL_TYPE),
            
            Pair.of(ARRAY_DECIMAL_TYPE.getTypeName(), ARRAY_DECIMAL_TYPE),
            
            Pair.of(LIST_DECIMAL_TYPE.getTypeName(), LIST_DECIMAL_TYPE),
            
            Pair.of(SET_DECIMAL_TYPE.getTypeName(), SET_DECIMAL_TYPE),
            
            Pair.of(COLLECTION_DECIMAL_TYPE.getTypeName(), COLLECTION_DECIMAL_TYPE),
            
            Pair.of(MAP_STRING_DECIMAL_TYPE.getTypeName(), MAP_STRING_DECIMAL_TYPE),
            
            Pair.of(BIGINT_TYPE.getTypeName(), BIGINT_TYPE),
            
            Pair.of(ARRAY_BIGINT_TYPE.getTypeName(), ARRAY_BIGINT_TYPE),
            
            Pair.of(LIST_BIGINT_TYPE.getTypeName(), LIST_BIGINT_TYPE),
            
            Pair.of(SET_BIGINT_TYPE.getTypeName(), SET_BIGINT_TYPE),
            
            Pair.of(COLLECTION_BIGINT_TYPE.getTypeName(), COLLECTION_BIGINT_TYPE),
            
            Pair.of(MAP_STRING_BIGINT_TYPE.getTypeName(), MAP_STRING_BIGINT_TYPE),
            
            Pair.of(STRING_TYPE.getTypeName(), STRING_TYPE),
            
            Pair.of(ARRAY_STRING_TYPE.getTypeName(), ARRAY_STRING_TYPE),
            
            Pair.of(LIST_STRING_TYPE.getTypeName(), LIST_STRING_TYPE),
            
            Pair.of(SET_STRING_TYPE.getTypeName(), SET_STRING_TYPE),
            
            Pair.of(COLLECTION_STRING_TYPE.getTypeName(), COLLECTION_STRING_TYPE),
            
            Pair.of(MAP_STRING_STRING_TYPE.getTypeName(), MAP_STRING_STRING_TYPE),
            
            Pair.of(DATE_TYPE.getTypeName(), DATE_TYPE),
            
            Pair.of(ARRAY_DATE_TYPE.getTypeName(), ARRAY_DATE_TYPE),
            
            Pair.of(LIST_DATE_TYPE.getTypeName(), LIST_DATE_TYPE),
            
            Pair.of(SET_DATE_TYPE.getTypeName(), SET_DATE_TYPE),
            
            Pair.of(COLLECTION_DATE_TYPE.getTypeName(), COLLECTION_DATE_TYPE),
            
            Pair.of(MAP_STRING_DATE_TYPE.getTypeName(), MAP_STRING_DATE_TYPE),
            
            Pair.of(DATETIME_TYPE.getTypeName(), DATETIME_TYPE),
            
            Pair.of(ARRAY_DATETIME_TYPE.getTypeName(), ARRAY_DATETIME_TYPE),
            
            Pair.of(LIST_DATETIME_TYPE.getTypeName(), LIST_DATETIME_TYPE),
            
            Pair.of(SET_DATETIME_TYPE.getTypeName(), SET_DATETIME_TYPE),
            
            Pair.of(COLLECTION_DATETIME_TYPE.getTypeName(), COLLECTION_DATETIME_TYPE),
            
            Pair.of(MAP_STRING_DATETIME_TYPE.getTypeName(), MAP_STRING_DATETIME_TYPE),
            
            Pair.of(TIMESTAMP_TYPE.getTypeName(), TIMESTAMP_TYPE),
            
            Pair.of(ARRAY_TIMESTAMP_TYPE.getTypeName(), ARRAY_TIMESTAMP_TYPE),
            
            Pair.of(LIST_TIMESTAMP_TYPE.getTypeName(), LIST_TIMESTAMP_TYPE),
            
            Pair.of(SET_TIMESTAMP_TYPE.getTypeName(), SET_TIMESTAMP_TYPE),
            
            Pair.of(COLLECTION_TIMESTAMP_TYPE.getTypeName(), COLLECTION_TIMESTAMP_TYPE),
            
            Pair.of(MAP_STRING_TIMESTAMP_TYPE.getTypeName(), MAP_STRING_TIMESTAMP_TYPE),
            
            Pair.of(TIME_TYPE.getTypeName(), TIME_TYPE),
            
            Pair.of(ARRAY_TIME_TYPE.getTypeName(), ARRAY_TIME_TYPE),
            
            Pair.of(LIST_TIME_TYPE.getTypeName(), LIST_TIME_TYPE),
            
            Pair.of(SET_TIME_TYPE.getTypeName(), SET_TIME_TYPE),
            
            Pair.of(COLLECTION_TIME_TYPE.getTypeName(), COLLECTION_TIME_TYPE),
            
            Pair.of(MAP_STRING_TIME_TYPE.getTypeName(), MAP_STRING_TIME_TYPE),
            
            Pair.of(DURATION_TYPE.getTypeName(), DURATION_TYPE),
            
            Pair.of(ARRAY_DURATION_TYPE.getTypeName(), ARRAY_DURATION_TYPE),
            
            Pair.of(LIST_DURATION_TYPE.getTypeName(), LIST_DURATION_TYPE),
            
            Pair.of(SET_DURATION_TYPE.getTypeName(), SET_DURATION_TYPE),
            
            Pair.of(COLLECTION_DURATION_TYPE.getTypeName(), COLLECTION_DURATION_TYPE),
            
            Pair.of(MAP_STRING_DURATION_TYPE.getTypeName(), MAP_STRING_DURATION_TYPE),
            
            Pair.of(MAP_TYPE.getTypeName(), MAP_TYPE),
            
            Pair.of(ARRAY_MAP_TYPE.getTypeName(), ARRAY_MAP_TYPE),
            
            Pair.of(LIST_MAP_TYPE.getTypeName(), LIST_MAP_TYPE),
            
            Pair.of(SET_MAP_TYPE.getTypeName(), SET_MAP_TYPE),
            
            Pair.of(COLLECTION_MAP_TYPE.getTypeName(), COLLECTION_MAP_TYPE),
            
            Pair.of(MAP_STRING_MAP_TYPE.getTypeName(), MAP_STRING_MAP_TYPE),
            
            Pair.of(LIST_TYPE.getTypeName(), LIST_TYPE),
            
            Pair.of(ARRAY_LIST_TYPE.getTypeName(), ARRAY_LIST_TYPE),
            
            Pair.of(LIST_LIST_TYPE.getTypeName(), LIST_LIST_TYPE),
            
            Pair.of(SET_LIST_TYPE.getTypeName(), SET_LIST_TYPE),
            
            Pair.of(COLLECTION_LIST_TYPE.getTypeName(), COLLECTION_LIST_TYPE),
            
            Pair.of(MAP_STRING_LIST_TYPE.getTypeName(), MAP_STRING_LIST_TYPE),
            
            Pair.of(FILE_TYPE.getTypeName(), FILE_TYPE),
            
            Pair.of(ARRAY_FILE_TYPE.getTypeName(), ARRAY_FILE_TYPE),
            
            Pair.of(LIST_FILE_TYPE.getTypeName(), LIST_FILE_TYPE),
            
            Pair.of(SET_FILE_TYPE.getTypeName(), SET_FILE_TYPE),
            
            Pair.of(COLLECTION_FILE_TYPE.getTypeName(), COLLECTION_FILE_TYPE),
            
            Pair.of(MAP_STRING_FILE_TYPE.getTypeName(), MAP_STRING_FILE_TYPE),
            
            Pair.of(FILES_TYPE.getTypeName(), FILES_TYPE),
            
            Pair.of(ARRAY_FILES_TYPE.getTypeName(), ARRAY_FILES_TYPE),
            
            Pair.of(LIST_FILES_TYPE.getTypeName(), LIST_FILES_TYPE),
            
            Pair.of(SET_FILES_TYPE.getTypeName(), SET_FILES_TYPE),
            
            Pair.of(COLLECTION_FILES_TYPE.getTypeName(), COLLECTION_FILES_TYPE),
            
            Pair.of(MAP_STRING_FILES_TYPE.getTypeName(), MAP_STRING_FILES_TYPE),
            
            Pair.of(POINT_TYPE.getTypeName(), POINT_TYPE),
            
            Pair.of(ARRAY_POINT_TYPE.getTypeName(), ARRAY_POINT_TYPE),
            
            Pair.of(LIST_POINT_TYPE.getTypeName(), LIST_POINT_TYPE),
            
            Pair.of(SET_POINT_TYPE.getTypeName(), SET_POINT_TYPE),
            
            Pair.of(COLLECTION_POINT_TYPE.getTypeName(), COLLECTION_POINT_TYPE),
            
            Pair.of(MAP_STRING_POINT_TYPE.getTypeName(), MAP_STRING_POINT_TYPE),
            
            Pair.of(GEOMETRY_TYPE.getTypeName(), GEOMETRY_TYPE),
            
            Pair.of(ARRAY_GEOMETRY_TYPE.getTypeName(), ARRAY_GEOMETRY_TYPE),
            
            Pair.of(LIST_GEOMETRY_TYPE.getTypeName(), LIST_GEOMETRY_TYPE),
            
            Pair.of(SET_GEOMETRY_TYPE.getTypeName(), SET_GEOMETRY_TYPE),
            
            Pair.of(COLLECTION_GEOMETRY_TYPE.getTypeName(), COLLECTION_GEOMETRY_TYPE),
            
            Pair.of(MAP_STRING_GEOMETRY_TYPE.getTypeName(), MAP_STRING_GEOMETRY_TYPE),
            
            Pair.of(BYTES_TYPE.getTypeName(), BYTES_TYPE),
            
            Pair.of(ARRAY_BYTES_TYPE.getTypeName(), ARRAY_BYTES_TYPE),
            
            Pair.of(LIST_BYTES_TYPE.getTypeName(), LIST_BYTES_TYPE),
            
            Pair.of(SET_BYTES_TYPE.getTypeName(), SET_BYTES_TYPE),
            
            Pair.of(COLLECTION_BYTES_TYPE.getTypeName(), COLLECTION_BYTES_TYPE),
            
            Pair.of(MAP_STRING_BYTES_TYPE.getTypeName(), MAP_STRING_BYTES_TYPE),
            
            Pair.of(VOID_TYPE.getTypeName(), VOID_TYPE),
            
            Pair.of(NULL_TYPE.getTypeName(), NULL_TYPE),
            
            Pair.of(UNKNOWN_TYPE.getTypeName(), UNKNOWN_TYPE),
            
            Pair.of(NEVER_TYPE.getTypeName(), NEVER_TYPE),
            
            Pair.of(CLONEABLE_TYPE.getTypeName(), CLONEABLE_TYPE),
            
            Pair.of(COMPARABLE_TYPE.getTypeName(), COMPARABLE_TYPE),
            
            Pair.of(RUNNABLE_TYPE.getTypeName(), RUNNABLE_TYPE),
            
            Pair.of(NUMBER_TYPE.getTypeName(), NUMBER_TYPE),
            
            Pair.of(SERIALIZABLE_TYPE.getTypeName(), SERIALIZABLE_TYPE),
            
            Pair.of(COLLECTION_TYPE.getTypeName(), COLLECTION_TYPE),
            
            Pair.of(SET_TYPE.getTypeName(), SET_TYPE),
            
            Pair.of(HASH_MAP_TYPE.getTypeName(), HASH_MAP_TYPE),
            
            Pair.of(LINKED_HASH_MAP_TYPE.getTypeName(), LINKED_HASH_MAP_TYPE),
            
            Pair.of(SORTED_MAP_TYPE.getTypeName(), SORTED_MAP_TYPE),
            
            Pair.of(FUNCTION_TYPE.getTypeName(), FUNCTION_TYPE),
            
            Pair.of(CONSUMER_TYPE.getTypeName(), CONSUMER_TYPE),
            
            Pair.of(SUPPLIER_TYPE.getTypeName(), SUPPLIER_TYPE),
            
            Pair.of(CALLABLE_TYPE.getTypeName(), CALLABLE_TYPE),
            
            Pair.of(BI_CONSUMER_TYPE.getTypeName(), BI_CONSUMER_TYPE),
            
            Pair.of(BI_FUNCTION_TYPE.getTypeName(), BI_FUNCTION_TYPE),
            
            Pair.of(PREDICATE_TYPE.getTypeName(), PREDICATE_TYPE),
            
            Pair.of(COMPARATOR_TYPE.getTypeName(), COMPARATOR_TYPE),
            
            Pair.of(COMPLETION_STAGE_TYPE.getTypeName(), COMPLETION_STAGE_TYPE),
            
            Pair.of(COMPLETABLE_FUTURE_TYPE.getTypeName(), COMPLETABLE_FUTURE_TYPE),
            
            Pair.of(FUTURE_TYPE.getTypeName(), FUTURE_TYPE),
            
            Pair.of(X_NODE_TYPE.getTypeName(), X_NODE_TYPE),
            
            Pair.of(PAIR_TYPE.getTypeName(), PAIR_TYPE),
            
            Pair.of(I_GENERIC_TYPE_TYPE.getTypeName(), I_GENERIC_TYPE_TYPE),
            
            Pair.of(I_EVAL_ACTION_TYPE.getTypeName(), I_EVAL_ACTION_TYPE),
            
            Pair.of(I_EVAL_PREDICATE_TYPE.getTypeName(), I_EVAL_PREDICATE_TYPE),
            
            Pair.of(I_EVAL_FUNCTION_TYPE.getTypeName(), I_EVAL_FUNCTION_TYPE),
            
            Pair.of(I_PROPERTY_GETTER_TYPE.getTypeName(), I_PROPERTY_GETTER_TYPE),
            
            Pair.of(I_PROPERTY_SETTER_TYPE.getTypeName(), I_PROPERTY_SETTER_TYPE),
            
            Pair.of(I_EVAL_SCOPE_TYPE.getTypeName(), I_EVAL_SCOPE_TYPE),
            
            Pair.of(I_TEXT_TEMPLATE_OUTPUT_TYPE.getTypeName(), I_TEXT_TEMPLATE_OUTPUT_TYPE),
            
            Pair.of(I_TEMPLATE_OUTPUT_TYPE.getTypeName(), I_TEMPLATE_OUTPUT_TYPE),
            
            Pair.of(I_SQL_GENERATOR_TYPE.getTypeName(), I_SQL_GENERATOR_TYPE),
            
            Pair.of(I_X_NODE_GENERATOR_TYPE.getTypeName(), I_X_NODE_GENERATOR_TYPE),
            
            Pair.of(J_OBJECT_TYPE.getTypeName(), J_OBJECT_TYPE),
            
            Pair.of(J_ARRAY_TYPE.getTypeName(), J_ARRAY_TYPE),
            
            Pair.of(I_JSON_CONTAINER_TYPE.getTypeName(), I_JSON_CONTAINER_TYPE),
            
            Pair.of(I_SOURCE_LOCATION_GETTER_TYPE.getTypeName(), I_SOURCE_LOCATION_GETTER_TYPE),
            
            Pair.of(I_DEEP_CLONEABLE_TYPE.getTypeName(), I_DEEP_CLONEABLE_TYPE),
            
            Pair.of(I_CLONEABLE_TYPE.getTypeName(), I_CLONEABLE_TYPE),
            
            Pair.of(I_FREEZABLE_TYPE.getTypeName(), I_FREEZABLE_TYPE),
            
            Pair.of(SOURCE_LOCATION_TYPE.getTypeName(), SOURCE_LOCATION_TYPE),
            
            Pair.of(I_CANCELLABLE_TYPE.getTypeName(), I_CANCELLABLE_TYPE),
            
            Pair.of(I_CANCEL_TOKEN_TYPE.getTypeName(), I_CANCEL_TOKEN_TYPE),
            
            Pair.of(API_REQUEST_TYPE.getTypeName(), API_REQUEST_TYPE),
            
            Pair.of(API_RESPONSE_TYPE.getTypeName(), API_RESPONSE_TYPE),
            
            Pair.of(PAGE_BEAN_TYPE.getTypeName(), PAGE_BEAN_TYPE),
            
            Pair.of(GRAPH_Q_L_CONNECTION_TYPE.getTypeName(), GRAPH_Q_L_CONNECTION_TYPE),
            
            Pair.of(GRAPH_Q_L_REQUEST_BEAN_TYPE.getTypeName(), GRAPH_Q_L_REQUEST_BEAN_TYPE),
            
            Pair.of(GRAPH_Q_L_RESPONSE_BEAN_TYPE.getTypeName(), GRAPH_Q_L_RESPONSE_BEAN_TYPE),
            
            Pair.of(GRAPH_Q_L_PAGE_INFO_TYPE.getTypeName(), GRAPH_Q_L_PAGE_INFO_TYPE),
            
            Pair.of(GRAPH_Q_L_CONNECTION_INPUT_TYPE.getTypeName(), GRAPH_Q_L_CONNECTION_INPUT_TYPE),
            
            Pair.of(GRAPH_Q_L_ERROR_BEAN_TYPE.getTypeName(), GRAPH_Q_L_ERROR_BEAN_TYPE),
            
            Pair.of(I_CONTEXT_TYPE.getTypeName(), I_CONTEXT_TYPE),
            
            Pair.of(I_KEYED_LIST_TYPE.getTypeName(), I_KEYED_LIST_TYPE),
            
            Pair.of(I_KEYED_ELEMENT_TYPE.getTypeName(), I_KEYED_ELEMENT_TYPE),
            
            Pair.of(STD_DATA_TYPE_TYPE.getTypeName(), STD_DATA_TYPE_TYPE),
            
            Pair.of(NOP_EXCEPTION_TYPE.getTypeName(), NOP_EXCEPTION_TYPE),
            
            Pair.of(NOP_SCRIPT_ERROR_TYPE.getTypeName(), NOP_SCRIPT_ERROR_TYPE),
            
                  Pair.of("Object",ANY_TYPE),
               
                  Pair.of("boolean", PRIMITIVE_BOOLEAN_TYPE),
               
                  Pair.of("Boolean",BOOLEAN_TYPE),
               
                  Pair.of("char", PRIMITIVE_CHAR_TYPE),
               
                  Pair.of("Character",CHAR_TYPE),
               
                  Pair.of("byte", PRIMITIVE_BYTE_TYPE),
               
                  Pair.of("Byte",BYTE_TYPE),
               
                  Pair.of("short", PRIMITIVE_SHORT_TYPE),
               
                  Pair.of("Short",SHORT_TYPE),
               
                  Pair.of("int", PRIMITIVE_INT_TYPE),
               
                  Pair.of("Integer",INT_TYPE),
               
                  Pair.of("long", PRIMITIVE_LONG_TYPE),
               
                  Pair.of("Long",LONG_TYPE),
               
                  Pair.of("float", PRIMITIVE_FLOAT_TYPE),
               
                  Pair.of("Float",FLOAT_TYPE),
               
                  Pair.of("double", PRIMITIVE_DOUBLE_TYPE),
               
                  Pair.of("Double",DOUBLE_TYPE),
               
                  Pair.of("String",STRING_TYPE),
               
                  Pair.of("Void",VOID_TYPE),
               
            Pair.of("string",STRING_TYPE),
            Pair.of("number",NUMBER_TYPE),
            Pair.of("Number",NUMBER_TYPE),
            Pair.of("BigDecimal",DECIMAL_TYPE),
            Pair.of("never", NEVER_TYPE),
            Pair.of("unknown",UNKNOWN_TYPE),
            Pair.of("any", ANY_TYPE),
            Pair.of("Map", MAP_TYPE),
            Pair.of("void",PRIMITIVE_VOID_TYPE),
            Pair.of("List", LIST_TYPE),
            Pair.of("Set", SET_TYPE),
            Pair.of("Collection", COLLECTION_TYPE),
            Pair.of("PageBean", PAGE_BEAN_TYPE)
            );

    // Java的类型推导存在问题，自动推导非常慢无法结束
    private static Map<String,PredefinedGenericType> buildMap(Pair<String,PredefinedGenericType>... items){
        return CollectionHelper.buildImmutableMap(items);
    }

    public static PredefinedGenericType getPredefinedTypeForJavaType(Type type){
       return getPredefinedType(type.getTypeName());
    }

    public static PredefinedGenericType getPredefinedType(String typeName){
        return NAME_TO_PREDEFINED_TYPES.get(typeName);
    }

    public static PredefinedGenericType requirePredefinedTypeForJavaType(Type type) {
        return requirePredefinedType(type.getTypeName());
    }

    public static PredefinedGenericType requirePredefinedType(String typeName){
        PredefinedGenericType type = getPredefinedType(typeName);
        if(type == null)
            throw new NopException(ERR_TYPE_NOT_PREDEFINED_TYPE).param(ARG_TYPE_NAME, typeName);
        return type;
    }

    public static PredefinedGenericType getPredefinedArrayType(PredefinedGenericType type){
        if(!type.isArray())
            return getPredefinedType(type.getTypeName()+"[]");
        return null;
    }

    public static String normalizeTypeName(Class<?> clazz) {
        String typeName = clazz.getCanonicalName();
        if (typeName == null)
            typeName = clazz.getName();
        return typeName;
    }

    public static IGenericType normalizeType(IGenericType type) {
        IGenericType predefined = getPredefinedType(type.getTypeName());
        if (predefined != null)
            return predefined;
        return type;
    }
}
