//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.InstanceOfExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _InstanceOfExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.NamedTypeNode refType;
    
    protected io.nop.xlang.ast.Expression value;
    

    public _InstanceOfExpression(){
    }

    
    public io.nop.xlang.ast.NamedTypeNode getRefType(){
        return refType;
    }

    public void setRefType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.refType = value;
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
     
          checkMandatory("value",getValue());
       
    }


    public InstanceOfExpression newInstance(){
      return new InstanceOfExpression();
    }

    @Override
    public InstanceOfExpression deepClone(){
       InstanceOfExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(value != null){
                  
                          ret.setValue(value.deepClone());
                      
                }
            
                if(refType != null){
                  
                          ret.setRefType(refType.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(value != null)
                processor.accept(value);
        
            if(refType != null)
                processor.accept(refType);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(value != null && processor.apply(value) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(refType != null && processor.apply(refType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.value == oldChild){
               this.setValue((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.refType == oldChild){
               this.setRefType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.value == child){
                this.setValue(null);
                return true;
            }
        
            if(this.refType == child){
                this.setRefType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    InstanceOfExpression other = (InstanceOfExpression)node;
    
            if(!isNodeEquivalent(this.value,other.getValue())){
               return false;
            }
        
            if(!isNodeEquivalent(this.refType,other.getRefType())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.InstanceOfExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
                    if(refType != null){
                      
                              json.put("refType", refType);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(value != null)
                    value.freeze(cascade);
                if(refType != null)
                    refType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
