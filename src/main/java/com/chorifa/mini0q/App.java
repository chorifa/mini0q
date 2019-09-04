package com.chorifa.mini0q;

import java.util.concurrent.CountDownLatch;

/**
 * Hello world!
 *
 */
public class App 
{
    public static final CountDownLatch latch = new CountDownLatch(1);
    public static final int consumers = 10;
    public static final long[] lastTimeConsumeMap = new long[consumers];
    public static final long[] consumerCountMap = new long[consumers];
    public static final long[] consumerTimeMap = new long[consumers];
    public static final int producers = 10;
    public static final long[] lastTimeProduceMap = new long[producers];
    public static final long[] producerTimeMap = new long[producers];
    public static final long products = 1000*1000;

    public static void main(String[] args )
    {
        System.out.println( "Hello World!" );
    }
}
