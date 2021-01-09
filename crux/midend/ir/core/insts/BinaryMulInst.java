package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.LocalVar;

/**
 * Multiplies two values.
 * <p>
 * Operation (pseudo-code): {@code destVar = lhsValue * rhsValue}
 */
public final class BinaryMulInst extends BinaryOperator {
    public BinaryMulInst(LocalVar destVar, LocalVar lhsValue, LocalVar rhsValue) {
        super(Op.Mul, destVar, lhsValue, rhsValue);
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }
}
