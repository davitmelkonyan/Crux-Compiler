package edu.uci.cs142a.crux.midend.ir.types;

public abstract class Type {
    /**
     * The bit width in memory
     * */
    protected int mWidth;

    protected Type(int bitWidth) {
        mWidth = bitWidth;
    }

    public int getBitWidth() { return mWidth; }

    public abstract String print();
}
