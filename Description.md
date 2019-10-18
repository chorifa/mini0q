## Introduction
这个无锁环形队列是仿照disruptor实现的。除了等待策略外都是基于CAS操作的，可以方便的构建生产者消费者模型，支持存在依赖的菱形消费。    

### RingQueue
- 泛型类RingQueue是核心容器，内部有个有界Object数组(长度为2的幂)，所有元素在创建RingQueue的时候就完成实例化，此后每次读写只是修改元素的部分内容。

### 生产者Producer
如果要写入数据，外部线程调用RingQueue的publishEvent方法写入数据。所有生产者共享一个AtomicLong表征正在写入的进度，进度是指一共写入或消费了多少次。available数组表明当前位置是否生产过数据了。存放有所有消费者的消费进度，每个消费进度是AtomicLong，同时还有一个AtomicLong缓存最小消费者的消费进度。  
- 写入时主要分成三步，第一步调用next()函数争抢下个slot的写入拥有权。首先拿到当前写入的进度，接着拿到缓存的最小的消费者的消费进度，如果期待写入的进度减去环形队列的大小 小于等于 最小消费者的消费进度 说明期待写入的位置已经被消费过了，对写入进度的AtomicLong进行compareAndSet期待写入的位置，成功了就说明拿到这个位置的写入权。如果期待写入的进度减去环形队列的大小 大于 最小消费者的消费进度，说明生产的太快消费者没跟上，这时候会sleep一会直到消费者消费了期待写入的位置。整个函数在while(true)循环中，只有当compareAndSet为true后才会跳出。
- 第二步，修改RingQueue中对应位置的对象写入数据。第三步在available数组中写入相应数据。available和RingQueue对应，其中的元素表示当前位置被写入了几次数据了，就是写入进度除以RingQueue的大小。然后唤醒可能等待的消费者。整个写入过程只有一开始争抢下一个位置的写入权时需要CAS保障线程安全。

### 消费者Consumer
前边说到生产数据实际只需要外部线程调用publishEvent方法就可以。而消费者则是实现了Runable接口，用户只需要设置自己需要的处理hanlder就可以。消费者支持依赖消费，即A消费者必须在BC消费者消费过之后才能消费。消费者包含Batch和Concurrent两种方式，Batch方式各个消费者互不干扰每人维护独立的消费进度。Concurrent方式下多个消费者共享一个消费进度竞争消费。
- Batch模式下的run()方法中每次拿到当前可以消费的最大进度(生产者的available数组的最小有效值和依赖消费者的进度两方面的最小值)。在拿到这个进度时根据等待策略可能会进行阻塞或者自旋。然后对调用处理Handler进行消费。
- Concurrent模式下首先对共享的消费进度CAS操作拿到下一个期待消费的位置，等待直到允许消费的最大进度大于等于当前期待消费的位置，然后进行消费。允许消费的最大进度会被缓存，每次仅当期待消费的位置大于该值时才会去更新允许消费的最大进度。       

### 等待策略WaitStrategy
总之，生产者通过所有消费者的最小进度来判断自己是否能够写入。消费者通过生产者维护的available数组以及依赖的消费者进度来判断当前位置是否能被消费。而不能写入或是消费的时候则会根据等待策略进行等待。

#### 消费者
消费者的等待策略主要有Blocking,LiteBlocking以及Spin。
- Blocking策略当生产者的进度小于期待消费的位置时，加锁并且在while循环中wait在lock上，当生产者每次写入数据后都会唤醒wait在lock上的消费者。消费者唤醒之后还会通过自旋的方式，等待所有依赖的消费者的消费进度都大于自己期待消费的进度。
- LiteBlocking策略对比Blocking策略的不同在于，消费者在wait前会将AtomicBoolean设置为true，生产者发现这个标志为true的时候才会加锁唤醒消费者。
- Span就是忙循环，直到可以进行消费。    
Blocking方式试用场景最普遍,性能中规中矩;Lite模式下在消费者远远大于生产者的时候性能会急剧恶化，其他场景下明显好于Blocking,因为消费者远多于生产者的时候,消费者经常会等待,生产者是必定要唤醒消费者的，这时候就多了大量的CAS标志Boolean的操作;Spin模式可以让消费者更及时的消费,但会降低整体的读写进度。   

#### 生产者
Disruptor中生产者每次不够写入时都是park1ns，在生产者较多如10个以上的时候会严重拖垮整体速度(因为它会1ns就检查能否写入)。我对其等待次数进行计数,前50次每次等待2L,之后按照4L,16L,256L...递增,最大等1ms,这样在多生产者的情况下性能好了不少。

## 总结
另外，由于生产者消费者的进度都是恒增的,对原子变量可以大量的使用lazyset,不一定要那么及时,最多就是生产者或者消费者多等一会。    
### Question Prediction
- 和加锁的队列相比有什么优势
- 在队列长度相对于读写总数不大时(256)，多个生产者,每个生产者连续不断地写入10^6个数据，多个消费者不断的拉去数据。 1c1p,3c1p,2c2p等都比ArrayBlockingQueue快,大约只要一半时间，同时使用Lite比Blocking还要更快一半时间以上。 10c10p以及更多的生产者时,Blocking模式和ArrayBlockingQueue时间相近,Lite模式仍要快一倍以上(但在消费者很多的时候会急剧恶化)。由于是RingQueue大小不大，很容易遇到等待的情况，因此等待策略对整体性能影响更大。如果队列较大，那CAS的优势要更大。    

- 内存栏栅和内存排序
- JDK有Plain, volatile, release/acquire, opaque四种内存序。opaque仅保证最终可见性，plain就是普通读写，layset对应于setRelease，保证Release写前的读写不会重排到Release写后，对应的getAcquire能保证Acquire读之后的读写不会重排到Acquire读前，但是release/acquire不能保证可见性。volatile模式允许以volatile的方式读写变量，volatile写相当于Release barrier(禁止重排) -> 写变量 -> Store barrier(可见性)，volatile读相当于 Load barrier(可见性) -> 读变量 -> Acquire barrier(禁止重排)。