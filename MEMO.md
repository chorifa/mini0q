### LockSupport

- LockSupport.parkNanos() vs Thread.sleep() 

    parkNanos不支持中断异常处理,只是在单一线程中阻塞的话可以使用
    
- LockSupport.park()/unPark()
    
    使用类似信号量的许可机制, 因此unPark()可以在park()前边,但无论先unPark()多少次只相当于1次许可,也即只对一次park()有效
    
    相比Object.wait/notify, park/unPark是线程维度的,不需要获得监视器锁,可以指定具体的某个线程的唤醒,而不是随机
    
    
### UNSAFE vs VarHandle
- JDK9中使用Unsafe有很多问题,比如生成目标class为jdk8版本时会报找不到sun.misc.Unsafe的错
- 于是将所有的Unsafe都改成了VarHandle. 
- AtomicXXX中的lazySet()和setRelease()是等价的,都调用Unsafe.setRelease()方法,并且和VarHandle的setRelease()是等价的
- VarHandle可以赋予任何成员变量CAS的能力(远不止如此), 可以代替AtomicReferenceFieldUpdater这个类(用在了AbstractSequencer中)
- VarHandle还有静态的fence方法可以设置更为细粒度的内存栅栏

### TODO JDK中的内存策略 Plain, volatile, release, opaque
