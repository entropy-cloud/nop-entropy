//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MemberExpression extends io.nop.xlang.ast.OptionalExpression {
    
    protected boolean computed;
    
    protected io.nop.xlang.ast.Expression object;
    
    protected io.nop.xlang.ast.Expression property;
    

    public _MemberExpression(){
    }

    
    public boolean getComputed(){
        return computed;
    }

    public void setComputed(boolean value){
        checkAllowChange();
        
        this.computed = value;
    }
    
    public io.nop.xlang.ast.Expression getObject(){
        return object;
    }

    public void setObject(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.object = value;
    }
    
    public io.nop.xlang.ast.Expression getProperty(){
        return property;
    }

    public void setProperty(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.property = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("object",getObject());
       
          checkMandatory("property",getProperty());
       
    }


    public MemberExpression newInstance(){
      return new MemberExpression();
    }

    @Override
    public MemberExpression deepClone(){
       MemberExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setOptional(optional);
            
                if(object != null){
                  
                          ret.setObject(object.deepClone());
                      
                }
            
                if(property != null){
                  
                          ret.setProperty(property.deepClone());
                      
                }
            
                ret.setComputed(computed);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(object != null)
                processor.accept(object);
        
            if(property != null)
                processor.accept(property);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(object != null && processor.apply(object) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(property != null && processor.apply(property) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.object == oldChild){
               this.setObject((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.property == oldChild){
               this.setProperty((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.object == child){
                this.setObject(null);
                return true;
            }
        
            if(this.property == child){
                this.setProperty(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    MemberExpression other = (MemberExpression)node;
    
                if(!isValueEquivalent(this.optional,other.getOptional())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.object,other.getObject())){
               return false;
            }
        
            if(!isNodeEquivalent(this.property,other.getProperty())){
               return false;
            }
        
                if(!isValueEquivalent(this.computed,other.getComputed())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.MemberExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("optional", optional);
                
                    if(object != null){
                      
                              json.put("object", object);
                          
                    }
                
                    if(property != null){
                      
                              json.put("property", property);
                          
                    }
                
                   json.put("computed", computed);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(object != null)
                    object.freeze(cascade);
                if(property != null)
                    property.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
