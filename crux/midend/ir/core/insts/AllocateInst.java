package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.*;

import java.util.List;
import java.util.function.Function;

/**
 * Allocates a chunk of memory, either of a global variable or of an array (global and local).
 * <p>
 * Operation (pseudo-code):
 * <pre>
 * {@code
 * if (global)
 *     destVar = allocateInDataSection(numElement)
 * else
 *     destVar = reserveStackMemory(numElement)
 * }
 * </pre>
 */
public final class AllocateInst extends Instruction {
    public AllocateInst(AddressVar destVar, Constant numElement) {
        super(destVar, List.of(numElement));
    }

    public AddressVar getAllocatedAddress() {
        return (AddressVar)mDestVar;
    }

    public Constant getNumElement() {
        return (Constant)mOperands.get(0);
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }

    @Override
    public String format(Function<Value, String> valueFormatter) {
        var destVar = valueFormatter.apply(mDestVar);
        var typeStr = getAllocatedAddress().getType().print();
        var numElement = valueFormatter.apply(getNumElement());
        return String.format("%s = allocate %s, %s", destVar, typeStr, numElement);
    }
}
