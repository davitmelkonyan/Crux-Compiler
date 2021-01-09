package edu.uci.cs142a.crux.midend.ir.core;

import edu.uci.cs142a.crux.midend.ir.types.Type;

import java.util.HashMap;
import java.util.Map;

/**
 * A constant represents any kind of constant value. In our language that is integers and booleans.
 */
public abstract class Constant extends Value {
    protected static Map<Program, Map<Integer, IntegerConstant>> mIntConstantPool = new HashMap<>();
    protected static Map<Program, Map<Boolean, BooleanConstant>> mBoolConstantPool = new HashMap<>();

    protected Constant(Type type) {
        super(type);
    }
}
