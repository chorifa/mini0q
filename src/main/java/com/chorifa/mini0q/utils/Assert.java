package com.chorifa.mini0q.utils;

public final class Assert {
    public static void notNull(Object o){
        if(o == null) throw new IllegalArgumentException("parameter cannot be null");
    }

    public static void notEmpty(Object[] o){
        if(o == null || o.length == 0)
            throw new IllegalArgumentException("parameter cannot be empty");
    }

    public static void equalLength(Object[] o1, Object[] o2){
        Assert.notEmpty(o1);
        Assert.notEmpty(o2);
        if(o1.length != o2.length)
            throw new IllegalArgumentException("array parameters' lengths not equal");
    }
}
