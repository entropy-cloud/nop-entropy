package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.DialectErrorCodeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/dialect.xdef <p>
 * > 在标签内是以逗号分隔的、不含空白字符的异常错误码（`SQLException#getErrorCode`）、
 * > 异常状态（`SQLException#getSQLState`）列表，如 `03000,42000,42601,42602,42622,42804,42P01`。
 * > 当执行 SQL 得到的 `SQLException` 中的异常错误码或异常状态在该列表内，
 * > 则将转换为 `name` 所对应的 `ErrorCode` 后再抛出。
 * >
 * > 如果 JDBC 驱动（如 DuckDB）抛出的 `SQLException` 没有提供具体的异常错误码和异常状态信息，
 * > 则可以采用正则表达式匹配异常信息（SQLException#getMessage），
 * > 但需要将该正则表达式中的空白字符替换为 `_`，如 `.+_with_name_.+_does_not_exist.*`，
 * > 其将匹配 `Table with name AUTH_USER does not exist!` 异常。
 * > 注：正则表达式默认做多行、大小写无关匹配，且 `.` 包含换行符
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DialectErrorCodeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: useSqlState
     * 设置此属性为true时，使用SQLState变量来映射，而不是ErrorCode。例如PostgreSQL数据库
     */
    private java.lang.Boolean _useSqlState ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.util.Set<java.lang.String> _values ;
    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: useSqlState
     *  设置此属性为true时，使用SQLState变量来映射，而不是ErrorCode。例如PostgreSQL数据库
     */
    
    public java.lang.Boolean getUseSqlState(){
      return _useSqlState;
    }

    
    public void setUseSqlState(java.lang.Boolean value){
        checkAllowChange();
        
        this._useSqlState = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.Set<java.lang.String> getValues(){
      return _values;
    }

    
    public void setValues(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._values = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("name",this.getName());
        out.putNotNull("useSqlState",this.getUseSqlState());
        out.putNotNull("values",this.getValues());
    }

    public DialectErrorCodeModel cloneInstance(){
        DialectErrorCodeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DialectErrorCodeModel instance){
        super.copyTo(instance);
        
        instance.setName(this.getName());
        instance.setUseSqlState(this.getUseSqlState());
        instance.setValues(this.getValues());
    }

    protected DialectErrorCodeModel newInstance(){
        return (DialectErrorCodeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
