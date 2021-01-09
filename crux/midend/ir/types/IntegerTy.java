package edu.uci.cs142a.crux.midend.ir.types;

import edu.uci.cs142a.crux.midend.ir.core.Program;

import java.util.HashMap;
import java.util.Map;

public class IntegerTy extends Type {
    /**
     * Each Program should be assigned an unique Type
     * instance
     * */
    private static Map<Program, IntegerTy> mSingleton = new HashMap<>();

    private IntegerTy() {
        super(64);
    }

    @Override
    public String print() { return "int"; }

    public static IntegerTy get(Program ctx) {
        return mSingleton.computeIfAbsent(ctx, program -> new IntegerTy());
    }
}
