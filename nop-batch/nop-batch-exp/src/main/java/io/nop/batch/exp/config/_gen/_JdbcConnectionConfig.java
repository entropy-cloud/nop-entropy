package io.nop.batch.exp.config._gen;

import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.exp.config.JdbcConnectionConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/jdbc-connection.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _JdbcConnectionConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: catalog
     * 
     */
    private java.lang.String _catalog ;
    
    /**
     *  
     * xml name: dialect
     * 
     */
    private java.lang.String _dialect ;
    
    /**
     *  
     * xml name: driverClassName
     * 
     */
    private java.lang.String _driverClassName ;
    
    /**
     *  
     * xml name: jdbcUrl
     * 
     */
    private java.lang.String _jdbcUrl ;
    
    /**
     *  
     * xml name: maxConnections
     * 
     */
    private java.lang.Integer _maxConnections ;
    
    /**
     *  
     * xml name: password
     * 
     */
    private java.lang.String _password ;
    
    /**
     *  
     * xml name: username
     * 
     */
    private java.lang.String _username ;
    
    /**
     * 
     * xml name: catalog
     *  
     */
    
    public java.lang.String getCatalog(){
      return _catalog;
    }

    
    public void setCatalog(java.lang.String value){
        checkAllowChange();
        
        this._catalog = value;
           
    }

    
    /**
     * 
     * xml name: dialect
     *  
     */
    
    public java.lang.String getDialect(){
      return _dialect;
    }

    
    public void setDialect(java.lang.String value){
        checkAllowChange();
        
        this._dialect = value;
           
    }

    
    /**
     * 
     * xml name: driverClassName
     *  
     */
    
    public java.lang.String getDriverClassName(){
      return _driverClassName;
    }

    
    public void setDriverClassName(java.lang.String value){
        checkAllowChange();
        
        this._driverClassName = value;
           
    }

    
    /**
     * 
     * xml name: jdbcUrl
     *  
     */
    
    public java.lang.String getJdbcUrl(){
      return _jdbcUrl;
    }

    
    public void setJdbcUrl(java.lang.String value){
        checkAllowChange();
        
        this._jdbcUrl = value;
           
    }

    
    /**
     * 
     * xml name: maxConnections
     *  
     */
    
    public java.lang.Integer getMaxConnections(){
      return _maxConnections;
    }

    
    public void setMaxConnections(java.lang.Integer value){
        checkAllowChange();
        
        this._maxConnections = value;
           
    }

    
    /**
     * 
     * xml name: password
     *  
     */
    
    public java.lang.String getPassword(){
      return _password;
    }

    
    public void setPassword(java.lang.String value){
        checkAllowChange();
        
        this._password = value;
           
    }

    
    /**
     * 
     * xml name: username
     *  
     */
    
    public java.lang.String getUsername(){
      return _username;
    }

    
    public void setUsername(java.lang.String value){
        checkAllowChange();
        
        this._username = value;
           
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
        
        out.putNotNull("catalog",this.getCatalog());
        out.putNotNull("dialect",this.getDialect());
        out.putNotNull("driverClassName",this.getDriverClassName());
        out.putNotNull("jdbcUrl",this.getJdbcUrl());
        out.putNotNull("maxConnections",this.getMaxConnections());
        out.putNotNull("password",this.getPassword());
        out.putNotNull("username",this.getUsername());
    }

    public JdbcConnectionConfig cloneInstance(){
        JdbcConnectionConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(JdbcConnectionConfig instance){
        super.copyTo(instance);
        
        instance.setCatalog(this.getCatalog());
        instance.setDialect(this.getDialect());
        instance.setDriverClassName(this.getDriverClassName());
        instance.setJdbcUrl(this.getJdbcUrl());
        instance.setMaxConnections(this.getMaxConnections());
        instance.setPassword(this.getPassword());
        instance.setUsername(this.getUsername());
    }

    protected JdbcConnectionConfig newInstance(){
        return (JdbcConnectionConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
