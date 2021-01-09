package edu.uci.cs142a.crux.midend.ir.types;

import edu.uci.cs142a.crux.midend.ir.core.Program;

import java.util.HashMap;
import java.util.Map;

public class VoidTy extends Type {
    /**
     * Each Program should be assigned an unique Type
     * instance
     * */
    private static Map<Program, VoidTy> mSingleton = new HashMap<>();

    private VoidTy() { super(0); }

    @Override
    public String print() { return "void"; }

    public static VoidTy get(Program ctx) {
        return mSingleton.computeIfAbsent(ctx, program -> new VoidTy());
    }
}
