package com.chorifa.mini0q.utils;

import com.chorifa.mini0q.core.AtomicLong;
//import sun.misc.Unsafe;
//
//import java.lang.reflect.Field;
//import java.security.AccessController;
//import java.security.PrivilegedExceptionAction;

public final class Util {

    public static long getMinSequence(final AtomicLong[] sequences){
        return getMinSequence(sequences, Long.MAX_VALUE);
    }

    // thread-safe, caz sequences' size will not change, even if sequences is re-assign in other thread
    public static long getMinSequence(final AtomicLong[] sequences, long minimum){
        for(AtomicLong sequence : sequences)
            minimum = Math.min(sequence.get(), minimum);
        return minimum;
    }

    public static int log2(int i){
        int r = 0;
        while((i >>= 1) != 0) ++r;
        return r;
    }

    public static int minPower2largerThan(int i){
        i |= (i>>1);
        i |= (i>>2);
        i |= (i>>4);
        i |= (i>>8);
        i |= (i>>16);
        return i+1;
    }


//    private static final Unsafe theUnsafe;
//
//
//    static {
//        try {
//            final PrivilegedExceptionAction<Unsafe> action = () -> {
//                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
//                theUnsafe.setAccessible(true); // close security check
//                return (Unsafe) theUnsafe.get(null);
//            };
//            theUnsafe = AccessController.doPrivileged(action);
//        } catch (Exception e) {
//            throw new CoreException("Util: cannot load unsafe", e);
//        }
//    }
//
//    public static Unsafe getUnsafe(){
//        return theUnsafe;
//    }

}
