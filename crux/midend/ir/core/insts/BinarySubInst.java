package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.LocalVar;

/**
 * Subtracts two values.
 * <p>
 * Operation (pseudo-code): {@code destVar = lhsValue - rhsValue}
 */
public final class BinarySubInst extends BinaryOperator {
    public BinarySubInst(LocalVar destVar, LocalVar lhsValue, LocalVar rhsValue) {
        super(Op.Sub, destVar, lhsValue, rhsValue);
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }
}
