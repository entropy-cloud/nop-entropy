//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.PropertyTypeDef;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _PropertyTypeDef extends io.nop.xlang.ast.StructuredTypeDef {
    
    protected boolean anyName;
    
    protected java.lang.String name;
    
    protected boolean optional;
    
    protected boolean readonly;
    
    protected io.nop.xlang.ast.TypeNode valueType;
    

    public _PropertyTypeDef(){
    }

    
    public boolean getAnyName(){
        return anyName;
    }

    public void setAnyName(boolean value){
        checkAllowChange();
        
        this.anyName = value;
    }
    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    
    public boolean getOptional(){
        return optional;
    }

    public void setOptional(boolean value){
        checkAllowChange();
        
        this.optional = value;
    }
    
    public boolean getReadonly(){
        return readonly;
    }

    public void setReadonly(boolean value){
        checkAllowChange();
        
        this.readonly = value;
    }
    
    public io.nop.xlang.ast.TypeNode getValueType(){
        return valueType;
    }

    public void setValueType(io.nop.xlang.ast.TypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.valueType = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
          checkMandatory("valueType",getValueType());
       
    }


    public PropertyTypeDef newInstance(){
      return new PropertyTypeDef();
    }

    @Override
    public PropertyTypeDef deepClone(){
       PropertyTypeDef ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setNotNull(notNull);
            
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(valueType != null){
                  
                          ret.setValueType(valueType.deepClone());
                      
                }
            
                ret.setAnyName(anyName);
            
                ret.setReadonly(readonly);
            
                ret.setOptional(optional);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(valueType != null)
                processor.accept(valueType);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(valueType != null && processor.apply(valueType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.valueType == oldChild){
               this.setValueType((io.nop.xlang.ast.TypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.valueType == child){
                this.setValueType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    PropertyTypeDef other = (PropertyTypeDef)node;
    
                if(!isValueEquivalent(this.notNull,other.getNotNull())){
                   return false;
                }
            
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.valueType,other.getValueType())){
               return false;
            }
        
                if(!isValueEquivalent(this.anyName,other.getAnyName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.readonly,other.getReadonly())){
                   return false;
                }
            
                if(!isValueEquivalent(this.optional,other.getOptional())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.PropertyTypeDef;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("notNull", notNull);
                
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(valueType != null){
                      
                              json.put("valueType", valueType);
                          
                    }
                
                   json.put("anyName", anyName);
                
                   json.put("readonly", readonly);
                
                   json.put("optional", optional);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(valueType != null)
                    valueType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
