package com.chorifa.mini0q.core;

import com.chorifa.mini0q.utils.CoreException;
//import com.chorifa.mini0q.utils.Util;
//import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class LhsPadding{
    protected long p1, p2, p3, p4, p5, p6, p7;
}

class Value extends LhsPadding{
    protected volatile long value;
}

class RhsPadding extends Value{
    protected long p9, p10, p11, p12, p13, p14, p15;
}

public class AtomicLong extends RhsPadding{
    public static final long INITIAL_VALUE = -1L;
//    private static final Unsafe UNSAFE;
//    private static final long VALUE_OFFSET;

    private static final VarHandle valueHandler;

    static {
        try {
            valueHandler = MethodHandles.lookup().in(AtomicLong.class).findVarHandle(AtomicLong.class, "value", long.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new CoreException("AtomicLong: cannot get VarHandle");
        }
    }

//    static {
//        UNSAFE = Util.getUnsafe();
//        try {
//            VALUE_OFFSET = UNSAFE.objectFieldOffset(Value.class.getDeclaredField("value"));
//        }catch (Exception e){
//            throw new CoreException(e);
//        }
//    }

    // @Contended
//    protected volatile long value;

    public AtomicLong(){
        value = INITIAL_VALUE;
    }

    public AtomicLong(final long initialValue){
        value = initialValue;
    }

    public long get(){
        return (long)valueHandler.getVolatile(this);
    }

    public void lazySet(long newValue){
        valueHandler.setRelease(this, newValue);
//        UNSAFE.putOrderedLong(this, VALUE_OFFSET, newValue);
    }

    public void volatileSet(long newValue){
        valueHandler.setVolatile(this, newValue);
//        UNSAFE.putLongVolatile(this, VALUE_OFFSET, newValue);
    }

    public boolean compareAndSet(long expectedValue, long newValue) {
        return valueHandler.compareAndSet(this, expectedValue, newValue);
//        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expectedValue, newValue);
    }

    public long addAndGet(long delta) {
        return (long)valueHandler.getAndAdd(this, delta) +delta;
//         return UNSAFE.getAndAddLong(this, VALUE_OFFSET, delta) + delta;
    }

    public long getAndAdd(long delta) {
        return (long)valueHandler.getAndAdd(this, delta);
//        return UNSAFE.getAndAddLong(this, VALUE_OFFSET, delta);
    }

    @Override
    public String toString() {
        return Long.toString(get());
    }

}
