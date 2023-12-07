//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _PropertyAssignment extends io.nop.xlang.ast.Expression {
    
    protected boolean computed;
    
    protected io.nop.xlang.ast.Expression key;
    
    protected io.nop.xlang.ast.PropertyKind kind;
    
    protected boolean method;
    
    protected boolean shorthand;
    
    protected io.nop.xlang.ast.Expression value;
    

    public _PropertyAssignment(){
    }

    
    public boolean getComputed(){
        return computed;
    }

    public void setComputed(boolean value){
        checkAllowChange();
        
        this.computed = value;
    }
    
    public io.nop.xlang.ast.Expression getKey(){
        return key;
    }

    public void setKey(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.key = value;
    }
    
    public io.nop.xlang.ast.PropertyKind getKind(){
        return kind;
    }

    public void setKind(io.nop.xlang.ast.PropertyKind value){
        checkAllowChange();
        
        this.kind = value;
    }
    
    public boolean getMethod(){
        return method;
    }

    public void setMethod(boolean value){
        checkAllowChange();
        
        this.method = value;
    }
    
    public boolean getShorthand(){
        return shorthand;
    }

    public void setShorthand(boolean value){
        checkAllowChange();
        
        this.shorthand = value;
    }
    
    public io.nop.xlang.ast.Expression getValue(){
        return value;
    }

    public void setValue(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("key",getKey());
       
          checkMandatory("value",getValue());
       
    }


    public PropertyAssignment newInstance(){
      return new PropertyAssignment();
    }

    @Override
    public PropertyAssignment deepClone(){
       PropertyAssignment ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(key != null){
                  
                          ret.setKey(key.deepClone());
                      
                }
            
                if(value != null){
                  
                          ret.setValue(value.deepClone());
                      
                }
            
                if(kind != null){
                  
                          ret.setKind(kind);
                      
                }
            
                ret.setMethod(method);
            
                ret.setShorthand(shorthand);
            
                ret.setComputed(computed);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(key != null)
                processor.accept(key);
        
            if(value != null)
                processor.accept(value);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(key != null && processor.apply(key) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(value != null && processor.apply(value) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.key == oldChild){
               this.setKey((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.value == oldChild){
               this.setValue((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.key == child){
                this.setKey(null);
                return true;
            }
        
            if(this.value == child){
                this.setValue(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    PropertyAssignment other = (PropertyAssignment)node;
    
            if(!isNodeEquivalent(this.key,other.getKey())){
               return false;
            }
        
            if(!isNodeEquivalent(this.value,other.getValue())){
               return false;
            }
        
                if(!isValueEquivalent(this.kind,other.getKind())){
                   return false;
                }
            
                if(!isValueEquivalent(this.method,other.getMethod())){
                   return false;
                }
            
                if(!isValueEquivalent(this.shorthand,other.getShorthand())){
                   return false;
                }
            
                if(!isValueEquivalent(this.computed,other.getComputed())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.PropertyAssignment;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(key != null){
                      
                              json.put("key", key);
                          
                    }
                
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
                    if(kind != null){
                      
                              json.put("kind", kind);
                          
                    }
                
                   json.put("method", method);
                
                   json.put("shorthand", shorthand);
                
                   json.put("computed", computed);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(key != null)
                    key.freeze(cascade);
                if(value != null)
                    value.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
