package edu.uci.cs142a.crux.midend.ir.core;

import edu.uci.cs142a.crux.midend.ir.types.IntegerTy;

import java.util.HashMap;

/**
 * A constant integer, e.g. an array offset (like the 2 in a[2]). This is equivalent to {@link
 * edu.uci.cs142a.crux.frontend.ast.LiteralInt}.
 */
public final class IntegerConstant extends Constant {
    private int mValue;

    private IntegerConstant(Program ctx, int val) {
        super(IntegerTy.get(ctx));
        mValue = val;
    }

    public int getValue() { return mValue; }

    public static IntegerConstant get(Program ctx, int value) {
        var currentMap = mIntConstantPool.computeIfAbsent(ctx, p -> new HashMap<>());
        return currentMap.computeIfAbsent(value, p -> new IntegerConstant(ctx, value));
    }
}
