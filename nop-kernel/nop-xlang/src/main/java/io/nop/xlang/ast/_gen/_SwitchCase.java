//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.SwitchCase;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SwitchCase extends XLangASTNode {
    
    protected java.util.List<io.nop.xlang.ast.Statement> consequent;
    
    protected boolean fallthrough;
    
    protected io.nop.xlang.ast.Expression test;
    

    public _SwitchCase(){
    }

    
    public java.util.List<io.nop.xlang.ast.Statement> getConsequent(){
        return consequent;
    }

    public void setConsequent(java.util.List<io.nop.xlang.ast.Statement> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.consequent = value;
    }
    
    public java.util.List<io.nop.xlang.ast.Statement> makeConsequent(){
        java.util.List<io.nop.xlang.ast.Statement> list = getConsequent();
        if(list == null){
            list = new java.util.ArrayList<>();
            setConsequent(list);
        }
        return list;
    }
    
    public boolean getFallthrough(){
        return fallthrough;
    }

    public void setFallthrough(boolean value){
        checkAllowChange();
        
        this.fallthrough = value;
    }
    
    public io.nop.xlang.ast.Expression getTest(){
        return test;
    }

    public void setTest(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.test = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("test",getTest());
       
    }


    public SwitchCase newInstance(){
      return new SwitchCase();
    }

    @Override
    public SwitchCase deepClone(){
       SwitchCase ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(test != null){
                  
                          ret.setTest(test.deepClone());
                      
                }
            
                if(consequent != null){
                  
                          java.util.List<io.nop.xlang.ast.Statement> copy_consequent = new java.util.ArrayList<>(consequent.size());
                          for(io.nop.xlang.ast.Statement item: consequent){
                              copy_consequent.add(item.deepClone());
                          }
                          ret.setConsequent(copy_consequent);
                      
                }
            
                ret.setFallthrough(fallthrough);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(test != null)
                processor.accept(test);
        
            if(consequent != null){
               for(io.nop.xlang.ast.Statement child: consequent){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(test != null && processor.apply(test) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(consequent != null){
               for(io.nop.xlang.ast.Statement child: consequent){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.test == oldChild){
               this.setTest((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.consequent != null){
               int index = this.consequent.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Statement> list = this.replaceInList(this.consequent,index,newChild);
                   this.setConsequent(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.test == child){
                this.setTest(null);
                return true;
            }
        
            if(this.consequent != null){
               int index = this.consequent.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Statement> list = this.removeInList(this.consequent,index);
                   this.setConsequent(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SwitchCase other = (SwitchCase)node;
    
            if(!isNodeEquivalent(this.test,other.getTest())){
               return false;
            }
        
            if(isListEquivalent(this.consequent,other.getConsequent())){
               return false;
            }
                if(!isValueEquivalent(this.fallthrough,other.getFallthrough())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.SwitchCase;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(test != null){
                      
                              json.put("test", test);
                          
                    }
                
                    if(consequent != null){
                      
                              if(!consequent.isEmpty())
                                json.put("consequent", consequent);
                          
                    }
                
                   json.put("fallthrough", fallthrough);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(test != null)
                    test.freeze(cascade);
                consequent = io.nop.api.core.util.FreezeHelper.freezeList(consequent,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
