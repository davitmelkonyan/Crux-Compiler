package edu.uci.cs142a.crux.midend.ir.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FunctionTy extends Type {
    private static Map<List<Type>, FunctionTy> mSingleton = new HashMap<>();

    private Type mRetType;
    private List<Type> mArgTypes;

    private FunctionTy(Type retType, List<Type> argTypes) {
        super(0);
        mRetType = retType;
        mArgTypes = List.copyOf(argTypes);
    }

    public Type getReturnType() { return mRetType; }
    public List<Type> getArgTypes() { return mArgTypes; }

    public static FunctionTy get(Type retType, List<Type> argTypes) {
        ArrayList<Type> key = new ArrayList<>();
        key.add(retType);
        key.addAll(argTypes);
        return mSingleton.computeIfAbsent(key, k -> new FunctionTy(retType, argTypes));
    }

    @Override
    public String print() {
        var typeStr = getArgTypes().stream().map(Type::print).collect(Collectors.joining(","));
        return String.format("(%s) -> %s", typeStr, getReturnType().print());
    }
}
