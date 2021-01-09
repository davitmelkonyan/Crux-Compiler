package edu.uci.cs142a.crux.midend.ir.core;
import java.util.*;
import java.io.*;
import edu.uci.cs142a.crux.midend.ir.core.insts.*;
import edu.uci.cs142a.crux.midend.ir.types.*;

public class Emulator {
    HashMap<String, Function> functions = new HashMap<>();
    Stack<CallContext> stack = new Stack<>();
    HashMap<Long, Long> globalMap = new HashMap<>();
    HashMap<AddressVar, Long> offsetMap = new HashMap<>();
    BufferedReader br;
    PrintStream out;
    public static boolean DEBUG = false;
  
    public Emulator(Program p, InputStream emulatorInput, OutputStream emulatorOutput) {
        br = new BufferedReader(new InputStreamReader(emulatorInput));
        out = new PrintStream(emulatorOutput);

        for(Iterator<Function> func_it = p.getFunctions(); func_it.hasNext(); ) {
            Function f = func_it.next();
            functions.put(f.getName(), f);
        }
        long offset = 0;
        for(Iterator<AllocateInst> glob_it = p.getGlobals(); glob_it.hasNext(); ) {
            AllocateInst g = glob_it.next();
            offsetMap.put(g.getAllocatedAddress(), offset);
            offset += ((IntegerConstant)g.getNumElement()).getValue() * 8;
        }
    }

    public void run() {
        Function main = functions.get("main");
        CallContext mainc = new CallContext(main, null, null);
        stack.push(mainc);
        while(!stack.isEmpty()) {
            CallContext c = stack.peek();
            if (c.pc == null) {
                //Handle implicit return from void function
                stack.pop();
            } else {
                c.pc.accept(c);
            }
        }
    }

    void debug(String msg) {
        if (DEBUG)
            out.println(msg);
    }
  
    class CallContext extends InstVisitor {
        Function f;
        Instruction pc;
        HashMap<Variable, Object> localMap;
        LocalVar retval;
    
        CallContext(Function f, Object[] arguments, LocalVar retval) {
            this.f = f;
            pc = f.getStart();
            localMap = new HashMap<>();
            if (arguments != null) {
                int index = 0;
                for(LocalVar arg : f.getArguments()) {
                    localMap.put(arg, arguments[index++]);
                }
            }
            this.retval = retval;
        }

        public void visit(AllocateInst i) {
            throw new Error();
        }

        public void visit(AddressAt i) {
            AddressVar base = i.getBase();
            long address = offsetMap.get(base);
            Value v = i.getOffset();
            if (v != null) {
                address += 8 * ((Long) localMap.get(v));
            }
            localMap.put(i.mDestVar, address);
            debug("AddressAt: "+i.mDestVar+" = "+address);
            pc = f.getChild(pc);
        }

        public void visit(BinaryOperator i) {
            Object left = localMap.get(i.mOperands.get(0));
            Object right = localMap.get(i.mOperands.get(1));
            Object result = null;
            switch (i.getOperator()) {
            case Add: result = ((Long) left) + ((Long)right); break;
            case Sub: result = ((Long) left) - ((Long)right); break;
            case Mul: result = ((Long) left) * ((Long)right); break;
            case Div: result = ((Long) left) / ((Long)right); break;
            case And: result = ((Boolean) left) && ((Boolean)right); break;
            case Or: result = ((Boolean) left) || ((Boolean)right); break;
            }
            localMap.put(i.mDestVar, result);
            debug("BinaryOperator: "+i.mDestVar +"="+left+i.getOperator()+right);
            pc = f.getChild(pc);
        }
      
        public void visit(CompareInst i) {
            Long left = (Long) localMap.get(i.mOperands.get(0));
            Long right = (Long) localMap.get(i.mOperands.get(1));
            Boolean result = null;
            switch(i.getPredicate()) {
            case GE: result = left >= right; break;
            case GT: result = left > right; break;
            case LE: result = left <= right; break;
            case LT: result = left < right; break;
            case EQ: result = left.equals(right); break;
            case NE: result = !left.equals(right); break;
            }
            localMap.put(i.mDestVar, result);
            debug("CompareInst: "+i.mDestVar +"="+left+i.getPredicate()+right);
            pc = f.getChild(pc);
        }
      
        public void visit(CopyInst i) {
            Value srcval = i.getSrcValue();
            Object val;
            if (srcval instanceof IntegerConstant) {
                val = Long.valueOf(((IntegerConstant) srcval).getValue());
            } else if (srcval instanceof BooleanConstant) {
                val = ((BooleanConstant) srcval).getValue();
            } else {
                val = localMap.get(srcval);
            }

            debug("CopyInst: "+i.getDstVar()+"="+val);
            localMap.put(i.getDstVar(), val);
            pc = f.getChild(pc);
        }
      
        public void visit(JumpInst i) {
            Boolean pred = (Boolean) localMap.get(i.getPredicate());
            debug("Jump: "+i.getPredicate()+"="+pred);
            for(Iterator<Instruction> child_it = f.getChildren(pc); child_it.hasNext(); ) {
                Instruction dst = child_it.next();
                if (pred && f.isJump(pc, dst)) {
                    pc = dst;
                    break;
                } else if (!pred && !f.isJump(pc, dst)) {
                    pc = dst;
                    break;
                }
            }
        }
      
        public void visit(LoadInst i) {
            AddressVar var = i.getSrcAddress();
            Long address = (Long) localMap.get(var);
            Long value = globalMap.get(address);
            if (value == null) {
                out.println("Reading from uninitialized memory");
                value = Long.valueOf(0);
            }
            
            Object val;
            if (var.getType() instanceof IntegerTy) {
              val = value;
            } else {
              val = Boolean.valueOf(value != 0);
            } 
            
            debug("LoadInst: "+i.mDestVar+"="+val);
            localMap.put(i.mDestVar, val);
            pc = f.getChild(pc);
        }
      
        public void visit(NopInst i) {
            //Do nothing
            debug("Nop:");
            pc = f.getChild(pc);
        }
      
        public void visit(StoreInst i) {
            Value srcval = i.getSrcValue();
            Object val = localMap.get(srcval);
            AddressVar dst = i.getDestAddress();
            Long address = (Long) localMap.get(dst);
            debug("StoreInst: *"+address+"="+val);

            if (val instanceof Long) {
                globalMap.put(address, (Long) val);
            } else if (val instanceof Boolean) {
                globalMap.put(address, ((Boolean)val) ? Long.valueOf(1) : Long.valueOf(0));
            }
            pc = f.getChild(pc);
        }

        public void visit(ReturnInst i) {
            Object val = i.getReturnValue() != null ? localMap.get(i.getReturnValue()) : null;
            debug("ReturnInst: "+ val);
            //Remove ourselves from the stack
            stack.pop();
            //Return value to caller
            if (!stack.isEmpty()) {
                CallContext caller = stack.peek();
                if (retval != null)
                    caller.localMap.put(retval, val);
            }
        }

        public void visit(CallInst i) {
            List<Value> params = i.getParams();
            Object[] args = new Object[params.size()];
            for(int j = 0; j < args.length; j++) {
                args[j] = localMap.get(params.get(j));
            }

            AddressVar varCallee = i.getCallee();
            //Chop off leading %
            String fName = varCallee.getName().substring(1);
            debug("Calling "+fName + " with " + Arrays.toString(args));
            //System.out.print("fName: "+fName);
            if (fName.equals("readInt")) {
                try {
                    out.print("int?");
                    String line = br.readLine();
                    localMap.put(i.mDestVar, Long.valueOf(line));
                } catch (IOException e) {
                    throw new Error("Error in inputting Integer.");
                }
            } else if (fName.equals("printBool")) {
                out.print(args[0]);
            } else if (fName.equals("printInt")) {
                out.print(args[0]);
            } else if (fName.equals("println")) {
                out.println("");
            } else {
                Function f = functions.get(fName);
                CallContext callee = new CallContext(f, args, (LocalVar)  i.mDestVar);
                stack.push(callee);
            }
            pc = f.getChild(pc);
        }

        public void visit(UnaryNotInst i) {
            Object left = localMap.get(i.mOperands.get(0));
            Object result = ! ((Boolean) left);
            localMap.put(i.mDestVar, result);
            debug("UnaryNotInst: "+result);
            pc = f.getChild(pc);
        } 
    }
}
