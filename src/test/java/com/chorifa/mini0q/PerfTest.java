package com.chorifa.mini0q;

import com.chorifa.mini0q.builder.DefaultExecutor;
import com.chorifa.mini0q.builder.RingQueueManager;
import com.chorifa.mini0q.core.consumer.EventHandler;
import com.chorifa.mini0q.core.event.EventFactory;
import com.chorifa.mini0q.core.event.EventTranslator;
import com.chorifa.mini0q.core.wait.AllBlockingWaitStrategy;
import com.chorifa.mini0q.core.wait.BlockingWaitStrategy;
import com.chorifa.mini0q.core.wait.LiteBlockingWaitStrategy;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.dsl.Disruptor;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class PerfTest {

    /**
     * size 4096 ->
     * 10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 3490ms (both c or p)
     * size 16384 ->
     * 10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 3238ms
     * ----------------------------------------------------------------------------------
     * no rest
     * 10 consumer, 10 producer * (1000*100) >>>> maxPeriod : 608ms / 680ms (both c or p)
     * 10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 7526ms (both c or p)
     *  5 consumer,  5 producer * (1000*1000) >>> maxPeriod : 2151ms / 2099ms (both c or p)
     *  3 consumer,  3 producer * (1000*1000) >>> maxPeriod : 1368ms / 1174ms (both c or p)
     *  2 consumer,  2 producer * (1000*1000) >>> maxPeriod : 585ms (both c or p)
     *  1 consumer,  1 producer * (1000*1000) >>> maxPeriod : 369ms / 344ms (both c or p)
     *  1 consumer,  3 producer * (1000*1000) >>> maxPeriod : 1437ms (both c or p)
     *  3 consumer,  1 producer * (1000*1000) >>> maxPeriod : 655ms (both c or p)
     *  3 consumer,  1 producer * (1000*1000*10) >>> maxPeriod : 4800ms (both c or p)
     *
     *  30 consumer, 10 producer * (1000)   >>> maxPeriod : 52ms (both c or p)
     *  100 consumer, 10 producer * (1000*100) >>> maxPeriod : 1970ms / 2410ms (both c or p)
     *  10 consumer, 100 producer * (1000*10) >>> maxPeriod : 1859ms (both c or p)
     *  10 consumer, 100 producer * (1000*100) >>> maxPeriod : 20617ms (both c or p)
     *  ----------------------------------------------------------------------------------
     *  has rest (1us for each put)
     *  10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 70850ms / 63653ms (both c or p)
     *  2 consumer,  2 producer * (1000*1000) >>> maxPeriod : 62807ms (both c or p)
     *  ----------------------------------------------------------------------------------
     *  1000 loop
     *  10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 48226ms / ms (both c or p)
     */
    @Test
    public void testArrayBlockingQueue(){
        BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(256);
        Thread[] producers = new Thread[App.producers];
        Thread[] consumers = new Thread[App.consumers];
        for(int i = 0; i < producers.length; i++)
            producers[i] = new Thread(new BlockingProducer(blockingQueue, i));
        for(int i = 0; i < consumers.length; i++)
            consumers[i] = new Thread(new BlockingConsumer(blockingQueue, i));

        // start
        for(Thread thread : consumers)
            thread.start();
        for(Thread thread : producers)
            thread.start();

        System.out.println("All started.");
        long init = System.currentTimeMillis();
        try {
            Thread.sleep(1000); // 1s
            init = System.currentTimeMillis();
            App.latch.countDown();
            for(Thread thread : producers)
                thread.join();
            Thread.sleep(10000); // 10s for consume
            for(Thread thread : consumers)
                thread.interrupt();
            Thread.sleep(1000);  //1s
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        analyse(init);
    }

    /**
     * size 4096 -> Blocking
     * 10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 1968ms both
     * size 16384 -> Blocking
     * 10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 1972ms both
     * -------------------------------------------------------------------------
     * no rest
     * 10 consumer, 10 producer * (1000*100) >>>> maxPeriod :
     * BlockingWait
     *  750ms (both c or p)
     * LiteBlockingWait
     *  318ms / 304ms (both c or p)
     * ******************************************************
     * 10 consumer, 10 producer * (1000*1000) >>> maxPeriod :
     * BlockingWait
     *  5307ms (both c or p)
     * LiteBlockingWait
     *  2349ms / 2333ms (both c or p)
     * ******************************************************
     *  5 consumer,  5 producer * (1000*1000) >>> maxPeriod :
     *  BlockingWait
     *   2286ms (both c or p)
     *  LiteBlockingWait
     *   761ms / 778ms (both c or p)
     * ******************************************************
     *  3 consumer,  3 producer * (1000*1000) >>> maxPeriod :
     *  BlockingWait
     *   699ms (both c or p)
     *  LiteBlockingWait
     *   311ms / 300ms (both c or p)
     * ******************************************************
     *  2 consumer,  2 producer * (1000*1000) >>> maxPeriod :
     *  BlockingWait
     *   416ms (both c or p)
     *  LiteBlockingWait
     *   205ms / 223ms (both c or p)
     * ******************************************************
     *  1 consumer,  1 producer * (1000*1000) >>> maxPeriod :
     *  BlockingWait
     *   161ms (both c or p)
     *  LiteBlockingWait
     *   102ms (both c or p)
     * ******************************************************
     *  1 consumer,  3 producer * (1000*1000) >>> maxPeriod :
     *  BlockingWait
     *   577ms (both c or p)
     *  LiteBlockingWait
     *   288ms / 291ms (both c or p)
     * ******************************************************
     *  3 consumer,  1 producer * (1000*1000) >>> maxPeriod :
     *  BlockingWait
     *   234ms (both c or p)
     *  LiteBlockingWait
     *   118ms / 136ms (both c or p)
     * ******************************************************
     *  3 consumer,  1 producer * (1000*1000*10) >>> maxPeriod :
     *  BlockingWait
     *   2088ms (both c or p)
     *  LiteBlockingWait
     *   858ms / 855ms (both c or p)
     * ******************************************************
     * IMPORTANT ---------------------------------- IMPORTANT
     * ******************************************************
     *  30 consumer, 10 producer * (1000)   >>> maxPeriod :
     *  BlockingWait
     *   42ms (both c or p)
     *  LiteBlockingWait
     *   57ms (both c or p)
     * ******************************************************
     *  100 consumer, 10 producer * (1000*100) >>> maxPeriod :
     *  BlockingWait
     *   3773ms / 3686ms / 6390 (c) 3671ms / 3686ms / 6388ms(p)
     *  LiteBlockingWait
     *   14212ms / 11445ms (c) 14210ms / 11444ms (p)
     * ******************************************************
     *  10 consumer, 100 producer * (1000*10) >>> maxPeriod :
     *  BlockingWait
     *   1340ms (both c or p)
     *  LiteBlockingWait
     *   456ms / 480ms (both c or p)
     * ******************************************************
     *  10 consumer, 100 producer * (1000*100) >>> maxPeriod :
     *  BlockingWait
     *   6049ms (both c or p)
     *  LiteBlockingWait
     *   3838ms / 3816ms (both c or p)
     *  ----------------------------------------------------------------------------------
     *  has rest (1us for each publish)
     *  10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 56410ms / 54580ms (both c or p)
     *  2 consumer, 2 producer * (1000*1000) >>> maxPeriod : 62483ms (both c or p)
     *  ----------------------------------------------------------------------------------
     *  1000 loop
     *  10 consumer, 10 producer * (1000*1000) >>> maxPeriod : 48519ms / ms (both c or p)
     */
    @Test
    public void testRingQueueBlockingWait(){
        RingQueueManager<Event> manager = RingQueueManager.createBuilder(factory,255, new LiteBlockingWaitStrategy())
                                            .handleEventInPoolWith(handle, App.consumers).getManager();
        Thread[] producers = new Thread[App.producers];
        for(int i = 0; i < App.producers; i++)
            producers[i] = new Thread(new CASProducer(i,manager));

        // start
        manager.start();
        for(Thread thread : producers)
            thread.start();

        System.out.println("All started.");
        long init = System.currentTimeMillis();
        try {
            Thread.sleep(1000); // 1s
            init = System.currentTimeMillis();
            App.latch.countDown();
            for(Thread thread : producers)
                thread.join();
            Thread.sleep(5000); // 5s for consume
            manager.stop();
            Thread.sleep(1000);  //1s
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        analyse(init);
    }

    /**
     * 10 consumer, 10 producer (1000*1000) >>> maxPeriod4Producer : 8809ms / 7185ms / 6550ms (p only)
     * 5 consumer, 5 producer (1000*1000) >>>>> maxPeriod4Producer : 2276ms / 2481ms (p only)
     * 2 consumer, 2 producer (1000*1000) >>>>> maxPeriod4Producer : 497ms (p only)
     * 1 consumer, 3 producer (1000*1000) >>>>> maxPeriod4Producer : 565ms (p only)
     * 3 consumer, 1 producer (1000*1000) >>>>> maxPeriod4Producer : 592ms (p only)
     */
    @Test
    public void testDisruptor(){
        final WorkHandler<Event> handler = (Event event)-> event.value = 0;
        WorkHandler<Event>[] handlers = new WorkHandler[App.consumers];
        Arrays.fill(handlers, handler);
        Disruptor<Event> disruptor = new Disruptor<>(Event::new,256, DefaultExecutor.DefaultThreadFactory.INSTANCE);
        disruptor.handleEventsWithWorkerPool(handlers); // consumer

        Thread[] producers = new Thread[App.producers];
        for(int i = 0; i < App.producers; i++)
            producers[i] = new Thread(new DisruptorProducer(i, disruptor));

        // start
        disruptor.start(); // disruptor should start first
        for(Thread thread : producers)
            thread.start();

        System.out.println("All started.");
        long init = System.currentTimeMillis();
        try {
            Thread.sleep(1000); // 1s
            init = System.currentTimeMillis();
            App.latch.countDown();
            for(Thread thread : producers)
                thread.join();
            Thread.sleep(5000); // 10s for consume
            disruptor.halt();
            Thread.sleep(1000);  //1s
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        analyse(init);
    }


    static class BlockingConsumer implements Runnable{
        private final BlockingQueue<Integer> queue;
        private final int num;
        BlockingConsumer(BlockingQueue<Integer> queue, int num) {
            this.queue = queue;
            this.num = num;
        }

        @Override
        public void run() {
            long startTime = -1, endTime = -1;
            long consume = 0;
            try {
                int i = 10000;
                while (i > 0) i--;
                App.latch.await();
                startTime = System.currentTimeMillis();
                long loop = App.products* App.producers+1;
                while (loop-->0) {
                    queue.take();
                    consume++;
                    endTime = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                if(startTime > 0) {
                    App.consumerTimeMap[num] = endTime - startTime;
                    App.consumerCountMap[num] = consume;
                    App.lastTimeConsumeMap[num] = endTime;
                }
                else System.out.println("Consumer "+num+" error start.");
            }
        }
    }

    static class BlockingProducer implements Runnable{
        private final BlockingQueue<Integer> queue;
        private final int num;
        BlockingProducer(BlockingQueue<Integer> queue, int num) {
            this.queue = queue;
            this.num = num;
        }

        @Override
        public void run() {
            long startTime = -1, endTime = -1;
            long loop = App.products;
            try {
                int i = 10000;
                while (i > 0) i--;
                App.latch.await();
                startTime = System.currentTimeMillis();
                while (loop-->0) {
                    queue.put((int)loop);
//                    for(int j = 0; j < 1000; j++){
//                        System.currentTimeMillis();
//                    }
//                    LockSupport.parkNanos(1000L); // 1us
                    endTime = System.currentTimeMillis();
                }
                if(startTime > 0 && loop == -1) {
                    App.producerTimeMap[num] = endTime - startTime;
                    App.lastTimeProduceMap[num] = endTime;
                }else System.out.println("Producer "+num+" error.");
            } catch (InterruptedException e) {
                System.out.println("Producer "+num+" interrupt.");
            }
        }
    }

    private static void analyse(long initTime){
        long t = 0;
        for(long i : App.consumerCountMap)
            t += i;
        if(t != App.products* App.producers)
            System.out.println("error in consume: actually consume "+t+" , while should consume "+ App.products* App.producers);
        long maxProducePeriod = 0;
        for(long time : App.producerTimeMap)
            if(time > maxProducePeriod) maxProducePeriod = time;
        System.out.println("MaxPeriod of producer: "+maxProducePeriod+" ms.");
        long maxConsumePeriod = 0;
        for(long time : App.consumerTimeMap)
            if(time > maxConsumePeriod) maxConsumePeriod = time;
        System.out.println("MaxPeriod of consumer: "+maxConsumePeriod+" ms.");
        long maxConsumerEndTime = 0;
        for(long time : App.lastTimeConsumeMap)
            if(time > maxConsumerEndTime)  maxConsumerEndTime = time;
        System.out.println("[TOTAL] MaxPeriod of consumer: "+ (maxConsumerEndTime - initTime) +" ms.");
        long maxProducerEndTime = 0;
        for(long time : App.lastTimeProduceMap)
            if(time > maxProducerEndTime) maxProducerEndTime = time;
        System.out.println("[TOTAL] MaxPeriod of producer: "+ (maxProducerEndTime - initTime) +" ms.");
        System.out.println("[TOTAL] Average : "+((maxConsumerEndTime - initTime)/(App.products* App.producers))+" ms.");
    }

    private static class Event{
        private int value = 0;
    }

    private static final EventTranslator<Event> translator = (Event event, long sequence)-> event.value = (int)sequence;

    private static final EventFactory<Event> factory = Event::new;

    private static final EventHandler<Event> handle = (Event event, long sequence)-> event.value = (int) sequence;

    static class CASProducer implements Runnable{

        private final int num;
        private final RingQueueManager<Event> manager;

        CASProducer(int num, RingQueueManager<Event> manager) {
            this.num = num;
            this.manager = manager;
        }

        @Override
        public void run() {
            long startTime = -1, endTime = -1;
            long loop = App.products;
            try {
                int i = 1000;
                while (i > 0) i--;
                App.latch.await();
                startTime = System.currentTimeMillis();
                while (loop-->0) {
                    manager.publishEvent(translator);
//                    try {
//                        Thread.sleep(0,1000); // actually, will sleep millis++ ms
//                    }catch (InterruptedException ignore){
//                        ;
//                    }
//                    for(int j = 0; j < 1000; j++){
//                        System.currentTimeMillis();
//                    }
//                    LockSupport.parkNanos(1000L); // 1us
                }
                endTime = System.currentTimeMillis();
                if(startTime > 0 && loop == -1) {
                    App.producerTimeMap[num] = endTime - startTime;
                    App.lastTimeProduceMap[num] = endTime;
                }else System.out.println("Producer "+num+" error.");
            } catch (InterruptedException e) {
                System.out.println("Producer "+num+" interrupt.");
            }
        }
    }

    private final static com.lmax.disruptor.EventTranslator<Event> translator4Disruptor = (Event event, long sequence)->event.value = (int)sequence;

    static class DisruptorProducer implements Runnable{

        private final int num;
        private final Disruptor<Event> disruptor;

        DisruptorProducer(int num, Disruptor<Event> disruptor) {
            this.num = num;
            this.disruptor = disruptor;
        }

        @Override
        public void run() {
            long startTime = -1, endTime = -1;
            long loop = App.products;
            try {
//                int i = 10000;
//                while (i > 0) i--;
                App.latch.await();
                startTime = System.currentTimeMillis();
                while (loop-->0) {
                    disruptor.publishEvent(translator4Disruptor);
//                    LockSupport.parkNanos(1000L); // 1us
                    endTime = System.currentTimeMillis();
                }
                if(startTime > 0 && loop == -1) {
                    App.producerTimeMap[num] = endTime - startTime;
                    App.lastTimeProduceMap[num] = endTime;
                }else System.out.println("Producer "+num+" error.");
            } catch (InterruptedException e) {
                System.out.println("Producer "+num+" interrupt.");
            }
        }
    }

}
