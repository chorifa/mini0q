## Introduction
这个无锁环形队列是仿照disruptor实现的。除了等待策略外都是基于CAS操作的，可以方便的构建生产者消费者模型，支持存在依赖的菱形消费。    

### RingQueue
- 

### 生产者Producer
- 存放有所有消费者的消费位置AtomicLong

### 消费者Consumer

### 等待策略WaitStrategy
#### 消费者

#### 生产者