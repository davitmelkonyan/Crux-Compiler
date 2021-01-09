package edu.uci.cs142a.crux.backend;
import java.util.*;
import java.io.*;
import edu.uci.cs142a.crux.midend.ir.core.*;
import edu.uci.cs142a.crux.midend.ir.core.insts.*;
import edu.uci.cs142a.crux.midend.ir.types.*;

public class CodeGen extends InstVisitor {
    Program p;
    CodePrinter out;
  
    public CodeGen(Program p) {
        this.p = p;
        out = new CodePrinter("a.s");
    }

    public void genCode() {
        //This function should generate code for the entire program
        out.close();
    }

    private int labelcount = 1;

    String getNewLabel() {
        return  "L"+ (labelcount++);
    }

    void genCode(Function f) {
    }

    /** Assigns Labels to any Instruction that might be the target of a
     * conditional or unconditional jump. */

    HashMap<Instruction, String> assignLabels(Function f) {
        HashMap<Instruction, String> labelMap = new HashMap<Instruction, String>();
        Stack<Instruction> tovisit=new Stack<Instruction>();
        HashSet<Instruction> discovered=new HashSet<Instruction>();
        tovisit.push(f.getStart());
        while(!tovisit.isEmpty()) {
          Instruction inst = tovisit.pop();

          for(Iterator<Instruction> childit = f.getChildren(inst); childit.hasNext(); ) {
              Instruction child = childit.next();
              if (discovered.contains(child)) {
                  //Found the node for a second time...need a label for merge points
                  if (!labelMap.containsKey(child)) {
                      labelMap.put(child, getNewLabel());
                  }
              } else {
                  discovered.add(child);
                  tovisit.push(child);
                  //Need a label for jump targets also
                  if (f.isJump(inst, child) && !labelMap.containsKey(child)) {
                      labelMap.put(child, getNewLabel());
                  }
              }
          }
        }        
        return labelMap;
    }

    public void visit(AllocateInst i) {
        throw new Error();
    }

    public void visit(AddressAt i) {

    }
    
    public void visit(BinaryOperator i) {
    }
    
    public void visit(CompareInst i) {
    }
    
    public void visit(CopyInst i) {
    }
    
    public void visit(JumpInst i) {
    }
      
    public void visit(LoadInst i) {
    }
    
    public void visit(NopInst i) {
        out.bufferCode("/* Nop */");
    }
    
    public void visit(StoreInst i) {
    }
    
    public void visit(ReturnInst i) {
    }

    public void visit(CallInst i) {
    }
    
    public void visit(UnaryNotInst i) {
    } 
}
