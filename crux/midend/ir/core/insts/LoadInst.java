package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.AddressVar;
import edu.uci.cs142a.crux.midend.ir.core.Instruction;
import edu.uci.cs142a.crux.midend.ir.core.LocalVar;
import edu.uci.cs142a.crux.midend.ir.core.Value;

import java.util.List;
import java.util.function.Function;

/**
 * Loads the value located at the source address into the destination variable.
 * <p>
 * Operation (pseudo-code): {@code destVar = *srcAddress}
 */
public final class LoadInst extends Instruction {
    public LoadInst(LocalVar destVar, AddressVar srcAddress) {
        super(destVar, List.of(srcAddress));
    }

    public AddressVar getSrcAddress() {
        return (AddressVar) mOperands.get(0);
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }

    @Override
    public String format(Function<Value, String> valueFormatter) {
        var destVar = valueFormatter.apply(mDestVar);
        var srcAddr = valueFormatter.apply(getSrcAddress());
        return String.format("%s = load %s", destVar, srcAddr);
    }
}
