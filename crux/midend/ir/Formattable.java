package edu.uci.cs142a.crux.midend.ir;

import edu.uci.cs142a.crux.midend.ir.core.Value;

import java.util.function.Function;

public interface Formattable {
    String format(Function<Value, String> valueFormatter);
}
