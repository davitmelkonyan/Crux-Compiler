package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.LocalVar;

/**
 * Divides one value by another. Note that this is integer division, so there might be a remainder, which is discarded.
 * <p>
 * Operation (pseudo-code): {@code destVar = lhsValue / rhsValue}
 */
public final class BinaryDivInst extends BinaryOperator {
    public BinaryDivInst(LocalVar destVar, LocalVar lhsValue, LocalVar rhsValue) {
        super(Op.Div, destVar, lhsValue, rhsValue);
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }
}
