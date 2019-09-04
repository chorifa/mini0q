package com.chorifa.mini0q.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class ThreadUtil {

    private static final MethodHandle ON_SPIN_WAITING;

    private ThreadUtil(){}

    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle handle;
        try {
            handle = lookup.findStatic(Thread.class, "onSpinWait", MethodType.methodType(void.class));
            System.out.println("JDK9+ -> found Thread.onSpinWait() method");
        }catch (NoSuchMethodException | IllegalAccessException e){
            handle = null;
        }
        ON_SPIN_WAITING = handle;
    }

    public static void onSpinWait(){
        if(ON_SPIN_WAITING != null){
            try {
                ON_SPIN_WAITING.invokeExact();
            }catch (Throwable ignored){
            }
        }
        else spin();
    }

    private static void spin(){
        int loop = 1000*1000*1000;
        while (loop > 0) loop--;
    }

}
