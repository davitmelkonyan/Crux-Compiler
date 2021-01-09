package edu.uci.cs142a.crux.midend.ir.core.insts;

import edu.uci.cs142a.crux.midend.ir.core.AddressVar;
import edu.uci.cs142a.crux.midend.ir.core.Instruction;
import edu.uci.cs142a.crux.midend.ir.core.Value;
import edu.uci.cs142a.crux.midend.ir.core.LocalVar;

import java.util.*;
import java.util.function.Function;

/**
 * Calls a function with the provided arguments.
 * <p>
 * Operation (pseudo-code):
 * <pre>
 * {@code
 * for (var param in params)
 *     push(param)
 * call(callee)
 * }
 * </pre>
 */
public final class CallInst extends Instruction {
    static private List<Value> merge(AddressVar callee, List<LocalVar> params) {
        Value[] l = new Value[1+params.size()];
        l[0]=callee;
        System.out.println("L"+l.length);
        for(int i=1; i<l.length;i++)
          l[i]=params.get(i-1);
        return List.of(l);
    }

    public CallInst(LocalVar destVar, AddressVar callee, List<LocalVar> params) {
      super(destVar, merge(callee, params));
    }

    public CallInst(AddressVar callee, List<LocalVar> params) {
      super(merge(callee, params));
    }

    public AddressVar getCallee() {
        return (AddressVar) mOperands.get(0);
    }

    public List<Value> getParams() {
      return mOperands.subList(1, mOperands.size());
    }

    @Override
    public void accept(InstVisitor v) {
        v.visit(this);
    }

    @Override
    public String format(Function<Value, String> valueFormatter) {
        var callee = valueFormatter.apply(getCallee());
        String paramstr = "";
        List<Value> lparams = getParams();
        for(Value p: lparams) {
          paramstr += valueFormatter.apply(p);
        }
        if(mDestVar != null) {
            var destVar = valueFormatter.apply(mDestVar);
            //System.out.print("F:"+callee);
            return String.format("%s = call %s (%s)", destVar, callee, paramstr);
        } else {
            return String.format("call %s (%s)", callee, paramstr);
        }
    }
}
