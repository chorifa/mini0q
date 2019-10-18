Mini0q -- a RingQueue for producer-consumer model
===
0q(homophonic as RingQueue) is a bounded RingQueue(disruptor like) based on CAS for producer-consumer model.  
- jdk 9+

Features
---------------------------------------------
- All CAS operations, No locks or synchronized(only in some kind of wait-strategys)  
- Easy to build complicated producer-consumer models

Performance Test
---------------------------------------------
ArrayBlockingQueue, Disruptor and Mini0q all apply size = 256. Each producer(p) will continuesly put 10^6 data into queue, each consumer(c) consumer data together. Count the time when all the consumption is completed.     

|        | ArrayBlockingQueue | Mini0q\(Blocking\) | Mini0q\(LiteBlocking\) | Disruptor\(Blocking\) |
|:------:|:------------------:|:------------------:|:----------------------:|:---------------------:|
| 1c3p   | 1437ms             | 577ms              | 288ms                  | 565ms                 |
| 3c1p   | 655ms              | 234ms              | 118ms                  | 592ms                 |
| 5c5p   | 1368ms             | 2286ms             | 761ms                  | 2276ms                |
| 10c10p | 7526ms             | 5307ms             | 2349ms                 | 6550ms                |


### Note:
- whole architecture refers to [disruptor](https://github.com/LMAX-Exchange/disruptor)  
- only for study, not for business or engineering  

