package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.LocalVar;

/**
 * Adds two values.
 * <p>
 * Operation (pseudo-code): {@code destVar = lhsValue + rhsValue}
 */
public final class BinaryAddInst extends BinaryOperator {
    public BinaryAddInst(LocalVar destVar, LocalVar lhsValue, LocalVar rhsValue) {
        super(Op.Add, destVar, lhsValue, rhsValue);
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }
}
