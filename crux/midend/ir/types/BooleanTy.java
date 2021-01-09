package edu.uci.cs142a.crux.midend.ir.types;

import edu.uci.cs142a.crux.midend.ir.core.Program;

import java.util.HashMap;
import java.util.Map;

public class BooleanTy extends Type {
    /**
     * Each Program should be assigned an unique Type
     * instance
     * */
    private static Map<Program, BooleanTy> mSingleton = new HashMap<>();

    private BooleanTy() {
        super(64);
    }

    @Override
    public String print() { return "bool"; }

    public static BooleanTy get(Program ctx) {
        return mSingleton.computeIfAbsent(ctx, program -> new BooleanTy());
    }
}
