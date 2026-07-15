package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamRequirementModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from file:/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamRequirementModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: value
     * 
     */
    private io.nop.stream.core.model.StreamRequirement _value ;
    
    /**
     * 
     * xml name: value
     *  
     */
    
    public io.nop.stream.core.model.StreamRequirement getValue(){
      return _value;
    }

    
    public void setValue(io.nop.stream.core.model.StreamRequirement value){
        checkAllowChange();
        
        this._value = value;
           
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
        
        out.putNotNull("value",this.getValue());
    }

    public StreamRequirementModel cloneInstance(){
        StreamRequirementModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamRequirementModel instance){
        super.copyTo(instance);
        
        instance.setValue(this.getValue());
    }

    protected StreamRequirementModel newInstance(){
        return (StreamRequirementModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
