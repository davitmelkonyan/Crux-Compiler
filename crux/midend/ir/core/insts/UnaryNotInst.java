package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.Instruction;
import edu.uci.cs142a.crux.midend.ir.core.Value;
import edu.uci.cs142a.crux.midend.ir.core.LocalVar;

import java.util.List;
import java.util.function.Function;

/**
 * Inverts a boolean.
 * <p>
 * Operation (pseudo-code): {@code destVar = !operand}
 */
public final class UnaryNotInst extends Instruction {
    public UnaryNotInst(LocalVar destVar, LocalVar operand) {
        super(destVar, List.of(operand));
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }

    @Override
    public String format(Function<Value, String> valueFormatter) {
        var destVar = valueFormatter.apply(mDestVar);
        var operand = valueFormatter.apply(mOperands.get(0));
        return String.format("%s = not %s", destVar, operand);
    }
}
