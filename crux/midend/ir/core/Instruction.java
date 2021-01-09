package edu.uci.cs142a.crux.midend.ir.core;

import edu.uci.cs142a.crux.midend.ir.Formattable;

import java.util.List;
import edu.uci.cs142a.crux.midend.ir.core.insts.InstVisitor;

/**
 * The base class for all instructions. Every instruction consists of a destination variable and a list of operands.
 * Note that not every instruction needs a destination variable (for example a jump instruction that takes a target
 * address as operand). Further, the list operands can be empty as well (e.g. a nop instruction that does nothing.)
 */
public abstract class Instruction implements Formattable {
    protected Variable mDestVar;
    protected List<Value> mOperands;

    public enum ControlEdgeKind {
        Continue,
        Jump
    }

    protected Instruction(Variable destVar, List<Value> operands) {
        mDestVar = destVar;
        mOperands = List.copyOf(operands);
    }

    protected Instruction(List<Value> operands) {
        mDestVar = null;
        mOperands = List.copyOf(operands);
    }
  
  public abstract void accept(InstVisitor v);
}
