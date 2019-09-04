Mini0q -- a RingQueue for producer-consumer model
===
0q(homophonic as RingQueue) is a bounded RingQueue(disruptor like) based on CAS for producer-consumer model.  
- jdk 9+

Features
---------------------------------------------
- All CAS operations, No locks or synchronized(only in some kind of wait-strategys)  
- Easy to build complicated producer-consumer models

### Note:
- whole architecture refers to [disruptor](https://github.com/LMAX-Exchange/disruptor)  
- only for study, not for business or engineering  

