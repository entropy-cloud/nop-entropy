package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamEnvironmentModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamEnvironmentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: deploymentMode
     * 
     */
    private io.nop.stream.core.execution.DeploymentMode _deploymentMode ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: deploymentMode
     *  
     */
    
    public io.nop.stream.core.execution.DeploymentMode getDeploymentMode(){
      return _deploymentMode;
    }

    
    public void setDeploymentMode(io.nop.stream.core.execution.DeploymentMode value){
        checkAllowChange();
        
        this._deploymentMode = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
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
        
        out.putNotNull("deploymentMode",this.getDeploymentMode());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("name",this.getName());
    }

    public StreamEnvironmentModel cloneInstance(){
        StreamEnvironmentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamEnvironmentModel instance){
        super.copyTo(instance);
        
        instance.setDeploymentMode(this.getDeploymentMode());
        instance.setDescription(this.getDescription());
        instance.setName(this.getName());
    }

    protected StreamEnvironmentModel newInstance(){
        return (StreamEnvironmentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
