package edu.uci.cs142a.crux.midend.ir.core;

import edu.uci.cs142a.crux.midend.ir.types.BooleanTy;

import java.util.HashMap;

/**
 * A constant integer, e.g. an array offset (like the 2 in a[2]). This is equivalent to {@link
 * edu.uci.cs142a.crux.frontend.ast.LiteralBool}.
 */
public final class BooleanConstant extends Constant {
    private boolean mValue;

    private BooleanConstant(Program ctx, boolean val) {
        super(BooleanTy.get(ctx));
        mValue = val;
    }

    public boolean getValue() { return mValue; }

    public static BooleanConstant get(Program ctx, boolean value) {
        var currentMap = mBoolConstantPool.computeIfAbsent(ctx, p -> new HashMap<>());
        return currentMap.computeIfAbsent(value, p -> new BooleanConstant(ctx, value));
    }
}
