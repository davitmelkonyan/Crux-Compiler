package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.Instruction;
import edu.uci.cs142a.crux.midend.ir.core.Value;
import edu.uci.cs142a.crux.midend.ir.core.LocalVar;

import java.util.List;
import java.util.function.Function;

/**
 * Any binary expression operator.
 */
public abstract class BinaryOperator extends Instruction {
    public enum Op {
        Add,
        Sub,
        Mul,
        Div,
        And,
        Or
    }
    protected Op mOp;
    public Op getOperator() { return mOp; }

    protected BinaryOperator(Op op, LocalVar destVar, LocalVar lhsValue, LocalVar rhsValue) {
        super(destVar, List.of(lhsValue, rhsValue));
        mOp = op;
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }

    @Override
    public String format(Function<Value, String> valueFormatter) {
        String opStr = "";
        switch (getOperator()) {
            case Add: opStr = "+"; break;
            case Sub: opStr = "-"; break;
            case Mul: opStr = "*"; break;
            case Div: opStr = "/"; break;
            case And: opStr = "and"; break;
            case Or: opStr = "or"; break;
        }
        var destVar = valueFormatter.apply(mDestVar);
        var lhs = valueFormatter.apply(mOperands.get(0));
        var rhs = valueFormatter.apply(mOperands.get(1));
        return String.format("%s = %s %s %s", destVar, lhs, opStr, rhs);
    }
}
