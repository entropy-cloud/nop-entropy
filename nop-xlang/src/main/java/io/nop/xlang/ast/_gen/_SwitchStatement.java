//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.SwitchStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SwitchStatement extends io.nop.xlang.ast.Statement {
    
    protected boolean asExpr;
    
    protected java.util.List<io.nop.xlang.ast.SwitchCase> cases;
    
    protected io.nop.xlang.ast.Expression defaultCase;
    
    protected io.nop.xlang.ast.Expression discriminant;
    

    public _SwitchStatement(){
    }

    
    public boolean getAsExpr(){
        return asExpr;
    }

    public void setAsExpr(boolean value){
        checkAllowChange();
        
        this.asExpr = value;
    }
    
    public java.util.List<io.nop.xlang.ast.SwitchCase> getCases(){
        return cases;
    }

    public void setCases(java.util.List<io.nop.xlang.ast.SwitchCase> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.cases = value;
    }
    
    public java.util.List<io.nop.xlang.ast.SwitchCase> makeCases(){
        java.util.List<io.nop.xlang.ast.SwitchCase> list = getCases();
        if(list == null){
            list = new java.util.ArrayList<>();
            setCases(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.Expression getDefaultCase(){
        return defaultCase;
    }

    public void setDefaultCase(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.defaultCase = value;
    }
    
    public io.nop.xlang.ast.Expression getDiscriminant(){
        return discriminant;
    }

    public void setDiscriminant(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.discriminant = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("discriminant",getDiscriminant());
       
    }


    public SwitchStatement newInstance(){
      return new SwitchStatement();
    }

    @Override
    public SwitchStatement deepClone(){
       SwitchStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(discriminant != null){
                  
                          ret.setDiscriminant(discriminant.deepClone());
                      
                }
            
                ret.setAsExpr(asExpr);
            
                if(cases != null){
                  
                          java.util.List<io.nop.xlang.ast.SwitchCase> copy_cases = new java.util.ArrayList<>(cases.size());
                          for(io.nop.xlang.ast.SwitchCase item: cases){
                              copy_cases.add(item.deepClone());
                          }
                          ret.setCases(copy_cases);
                      
                }
            
                if(defaultCase != null){
                  
                          ret.setDefaultCase(defaultCase.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(discriminant != null)
                processor.accept(discriminant);
        
            if(cases != null){
               for(io.nop.xlang.ast.SwitchCase child: cases){
                    processor.accept(child);
                }
            }
            if(defaultCase != null)
                processor.accept(defaultCase);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(discriminant != null && processor.apply(discriminant) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(cases != null){
               for(io.nop.xlang.ast.SwitchCase child: cases){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(defaultCase != null && processor.apply(defaultCase) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.discriminant == oldChild){
               this.setDiscriminant((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.cases != null){
               int index = this.cases.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.SwitchCase> list = this.replaceInList(this.cases,index,newChild);
                   this.setCases(list);
                   return true;
               }
            }
            if(this.defaultCase == oldChild){
               this.setDefaultCase((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.discriminant == child){
                this.setDiscriminant(null);
                return true;
            }
        
            if(this.cases != null){
               int index = this.cases.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.SwitchCase> list = this.removeInList(this.cases,index);
                   this.setCases(list);
                   return true;
               }
            }
            if(this.defaultCase == child){
                this.setDefaultCase(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SwitchStatement other = (SwitchStatement)node;
    
            if(!isNodeEquivalent(this.discriminant,other.getDiscriminant())){
               return false;
            }
        
                if(!isValueEquivalent(this.asExpr,other.getAsExpr())){
                   return false;
                }
            
            if(isListEquivalent(this.cases,other.getCases())){
               return false;
            }
            if(!isNodeEquivalent(this.defaultCase,other.getDefaultCase())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.SwitchStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(discriminant != null){
                      
                              json.put("discriminant", discriminant);
                          
                    }
                
                   json.put("asExpr", asExpr);
                
                    if(cases != null){
                      
                              if(!cases.isEmpty())
                                json.put("cases", cases);
                          
                    }
                
                    if(defaultCase != null){
                      
                              json.put("defaultCase", defaultCase);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(discriminant != null)
                    discriminant.freeze(cascade);
                cases = io.nop.api.core.util.FreezeHelper.freezeList(cases,cascade);         
                if(defaultCase != null)
                    defaultCase.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
