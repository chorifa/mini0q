# MEMO

## 细节

### 核心容器RingQueue
- RingQueue是一个有界数组类型自定义,所有元素在创建RingQueue的时候就完成实例化，此后每次读取只是修改元素的部分内容。

### 消费者Consumer

消费者包含Batch和Pool两种方式

##### BatchEventProcessor
- Batch模式下每个Processor自己维护一个AtomicLong类型表征消费进度。
- 每次拿到当前可以消费的最大索引(生产者的索引,多生产者模式下是available数组的最小有效值和依赖消费者的索引的最小值)，根据WaitStrategy可以选择阻塞或者忙等待。 
- 消费从current索引到available索引的所有元素。

##### ConcurrentEventProcessor
- Concurrent模式下多个Processor作为一个Pool竞争消费。
- 一个Pool下的所有Processor共享一个AtomicLong表征总体消费进度。每个Processor也有单独的AtomicLong表征个体进度。
- 每个Processor通过CAS争夺下一个消费元素。然后拿到当前允许消费的最大索引(根据WaitStrategy会阻塞等待或者忙等待等)。这个索引会缓存,每次仅当available指针小于当前想要消费的索引时才会去等待。

### 生产者Producer
- 所有生产者共享一个AtomicLong的消费进度索引,有一个available数组表征实际是否放入了值，有一个AtomicLong缓存存放最小消费者的位置。
- 第一步占坑，第二步消费，第三步将设置对应available数组的值。
- 如果当前想要写入的最大位置已经被消费过了，就进行CAS操作占坑。如果还没被消费过就休眠再试.

## 特点
  在竞争不大的情况下比ArrayBlockingQueue要快，但是没有到一个数量级  
 
  消费者的等待策略有Blocking,LiteBlocking以及Spin等。其中Blocking就是普通的阻塞等待,当消费者不够的消费的时候就阻塞,同时生产者每次放入就唤醒消费者。LiteBlocking存储AtomicBoolean类型作为是否需要唤醒消费者的指示,当消费者不够消费的时候，将boolean置为true;生产者getAndSet(false)为true的时候才会唤醒。Spin则是不阻塞，进行忙循环，同时检测是否可以消费。  
  
  Blocking方式试用场景最普遍,性能中规中矩;Lite模式下在消费者远远大于生产者的时候性能会急剧恶化，其他场景下明显好于Blocking,因为消费者远多于生产者的时候,消费者经常会等待,生产者是必定要唤醒消费者的，这时候就多了大量的CAS标志Boolean的操作;Spin模式可以让消费者更及时的消费,但会降低整体的读写进度。  
  
  在队列长度相对于读写总数不大时(256)，多个生产者,每个生产者连续不断地写入10^6个数据，多个消费者不断的拉去数据。  
  
  1c1p,3c1p,2c2p等都比ArrayBlockingQueue快,大约只要一半时间，同时使用Lite比Blocking还要更快一半时间以上。   
  
  10c10p以及更多的生产者时,Blocking模式和ArrayBlockingQueue时间相近,Lite模式仍要快一倍以上(但在消费者很多的时候会急剧恶化)   
  
  Disruptor中生产者如果不够写,会park(1L),在生产者较多如10个以上的时候会严重拖垮整体速度(因为它会1ns就检查能否写入)。我对其等待次数进行计数,前50次每次等带2L,之后按照4L,16L,256L...递增,最大等1ms,这样在多生产者的情况下性能好了不少。   

  由于生产者消费者的进度都是恒增的,可以大量的使用lazyset,不一定要那么及时,最多就是生产者或者消费者多等一会。  

  支持依赖消费,菱形消费

## 总结
- 真实情况下,生产者和消费者的整体速率不匹配,这时候waitStrategy对整体性能的影响最大,需要根据实际进行选择。
- CAS在竞争不大的时候更快,不能盲目的选择。
