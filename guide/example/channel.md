### Channels
延迟值提供了在协程之间传递单个值的便捷方法。Channels 提供了一种方式来传输流式值

### Channel基础

一个 [Channel](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-channel/index.html) 在概念上和 `BlockingQueue` 非常相似。 一个关键的不同点是使用了一个可挂起的 [send](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-send-channel/send.html) 替代阻塞的 `put` 操作， 使用了一个可挂起的 [receive](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-receive-channel/receive.html) 替代阻塞的 `take` 操作。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val channel = Channel<Int>()
    launch {
        // this might be heavy CPU-consuming computation or async logic, we'll just send five squares
        for (x in 1..5) channel.send(x * x)
    }
    // here we print five received integers:
    repeat(5) { println(channel.receive()) }
    println("Done!")
}
```

代码输出如下
```
1
4
9
16
25
Done!
```

### 关闭和遍历 channels

和队列不太像的是，一个 channel 可以被关闭，以表示没有更多的元素进入。在接受端可以很方便的定期使用 `for` 循环从 channel 中收取元素。

在概念上，一个 [close](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-send-channel/close.html) 就像挂起了一个专门的 channel 关闭令牌。当收到关闭令牌后，迭代会尽快停止，所以这可以保证之前发送的元素在 channel 关闭之前被全部接收。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) channel.send(x * x)
        channel.close() // we're done sending
    }
    // here we print received values using `for` loop (until the channel is closed)
    for (y in channel) println(y)
    println("Done!")
}
```

### 创建channel的生产者
协程产生一系列元素的模式很常见，这是 生产者-消费者 模式的一部分，你可以把生产者抽象成一个把channel作为参数的函数，但这常识相反，函数必须返回一个结果。

这有一个非常方便的协程生产者叫做 [produce](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/produce.html)， 它非常容易正确的作为一个生产者。还有一个扩展函数 [consumeEach](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/consume-each.html)，在消费者端替换 `for` 循环。

```kotlin
fun produceSquares() = produce<Int> {
    for (x in 1..5) send(x * x)
}

fun main(args: Array<String>) = runBlocking<Unit> {
    val squares = produceSquares()
    squares.consumeEach { println(it) }
    println("Done!")
}
```

### 管道
管道是一个模式，一个协程作为生产者，可能是无限的流

```kotlin
fun produceNumbers() = produce<Int> {
    var x = 1
    while (true) send(x++) // infinite stream of integers starting from 1
}
```

另一个协程作为流的消费者，做一些处理，并且生产一些其他的结果。下面的例子消费者只是计算了数字的平方：

```kotlin
fun square(numbers: ReceiveChannel<Int>) = produce<Int> {
    for (x in numbers) send(x * x)
}
```

main函数的代码将管道连接:
```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val numbers = produceNumbers() // produces integers from 1 and on
    val squares = square(numbers) // squares integers
    for (i in 1..5) println(squares.receive()) // print first five
    println("Done!") // we are done
    squares.cancel() // need to cancel these coroutines in a larger app
    numbers.cancel()
}
```
在这个实例程序中我们没有取消这些协程，因为协程 [就像守护线程一样](https://github.com/Kotlin/kotlinx.coroutines/blob/379f210f1d6f6ee91d6198348bd16306c93153ce/coroutines-guide.md#coroutines-are-like-daemon-threads)，但是在一个大型应用中，如果我们不使用了，我们需要停止我们的管道。我们可以把管道作为 [main协程的子协程](https://github.com/Kotlin/kotlinx.coroutines/blob/379f210f1d6f6ee91d6198348bd16306c93153ce/coroutines-guide.md#children-of-a-coroutine)， 下面的例子演示了这个：

### 管道实现素数

让我们使用协程构造一个比较极端的示例，使用一个管道协程生成素数。 我们使用一个无穷序列开始。这时候我们介绍一个显示的 `context` 参数，并且通过它去调度 [produce](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/produce.html) 构造器，因此，调用者可以控制我们的协程在哪里运行：

```kotlin
fun numbersFrom(context: CoroutineContext, start: Int) = produce<Int>(context) {
    var x = start
    while (true) send(x++) // infinite stream of integers from start
}
```
以下管道阶段过滤传入的数据，去除所有给定的素数整除的数字：
```kotlin
fun filter(context: CoroutineContext, numbers: ReceiveChannel<Int>, prime: Int) = produce<Int>(context) {
    for (x in numbers) if (x % prime != 0) send(x)
}
```

现在我们建立管道从 2 开始一连串的数字, 从当前频道质数, 并推出新的管道阶段为每个素数的发现:

```
numbersFrom(2) -> filter(2) -> filter(3) -> filter(5) -> filter(7) ... 
```

下面的示例输出第十个质数, 整个管道运行在主线程中。所有的协程都作为主协程使用 [corountinContext](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/coroutine-context.html) 调度的[runBlocking](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/run-blocking.html) 去执行。我们不想保持所有我们启动的协程的列表。我们使用 [cancelChildren](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/kotlin.coroutines.experimental.-coroutine-context/cancel-children.html) 扩展函数去取消所有的子协程。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    var cur = numbersFrom(coroutineContext, 2)
    for (i in 1..10) {
        val prime = cur.receive()
        println(prime)
        cur = filter(coroutineContext, cur, prime)
    }
    coroutineContext.cancelChildren() // cancel all children to let main finish
}
```

代码输出如下：

```kotlin
2
3
5
7
11
13
17
19
23
29
```

需要注意的，你可以使用标准库的 [buildIterator](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines.experimental/build-iterator.html) 协程创建器创建一些管道。`buildIterator` 替代 `produce` , `yield` 替代 `send`, `next` 替换 `receive`, `Iterator` 替换 `ReceiveChannel`, 并且摆脱上下文。你也不再需要 `runBlocking`。然而， 管道的好处是如果你用 [CommonPool](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-common-pool/index.html) 按照如上方式使用 channel 实际上可以使用多核 CPU。

总之，这是一个非常不切实际的方式寻找质数。在实践中，管道调用其他的挂起操作（例如异步调用远程服务），并且这些管道不能使用`buildSeqeunce` / `buildIterator` 创建，因为他们不允许任意挂起。不像 `produce`，是完全异步的。

### 扇出
多个协程可能会从遇到从一个 channel 获取数据以及它们之间的分配工作，让我们从一个生产者协程开始，周期性的产生整数（每秒十个数字）

```kotlin
fun produceNumbers() = produce<Int> {
    var x = 1 // start from 1
    while (true) {
        send(x++) // produce next
        delay(100) // wait 0.1s
    }
}
```

然后我们可以有多个处理器协同程序。在这个例子中,他们只是打印自己的id和收到的数字：

```kotlin
fun launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
    channel.consumeEach {
        println("Processor #$id received $it")
    }    
}
```
现在我们启动五个处理程序几乎让他们同时工作，看看会发生什么：
```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val producer = produceNumbers()
    repeat(5) { launchProcessor(it, producer) }
    delay(950)
    producer.cancel() // cancel producer coroutine and thus kill them all
}
```

输出将类似于下面的一个,尽管处理器接受每个特定整数id可能会有所不同:

```
Processor #2 received 1
Processor #4 received 2
Processor #0 received 3
Processor #1 received 4
Processor #3 received 5
Processor #2 received 6
Processor #4 received 7
Processor #0 received 8
Processor #1 received 9
Processor #3 received 10
```

注意，取消一个生产者协程关闭 channel，因此最终协同程序正在做的事情是终止迭代信道处理器。

### 扇入
多个协同程序可能发送到相同的频道。例如, 让我们有一个字符串, 渠道和暂停功能, 往这个 channel 反复发送一个指定字符串指定的延迟

```kotlin
suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
    while (true) {
        delay(time)
        channel.send(s)
    }
}
```

现在，让我们看看如果我们启动一些发送字符串的协程，会发生什么情况（在本例中，我们将它们作为主协程的子节点在主线程的上下文中启动):

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val channel = Channel<String>()
    launch(coroutineContext) { sendString(channel, "foo", 200L) }
    launch(coroutineContext) { sendString(channel, "BAR!", 500L) }
    repeat(6) { // receive first six
        println(channel.receive())
    }
    coroutineContext.cancelChildren() // cancel all children to let main finish
}
```

输出如下：

```
foo
foo
BAR!
foo
foo
BAR!
```

### 缓冲channel
目前为止还没显式的带缓冲 channel，没有缓冲的 channel 转移元素的时候，发送方和接受方相见（又叫对接）。如果发送调用，那么它将被暂停直到接收方调用。如果接收方接受了第一个元素，它会暂停，直到发送方调用。

[Channel()](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-channel.html) 和 [produce](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/produce.html)构造器都有一个可选的 `capacity` 参数去定义缓冲的大小。缓冲允许发送方在挂起之前发送多个元素，就像 `BlockingQueue` 有一个指定的容量一样，当缓冲区满了将会阻塞。

看一下下面代码的行为：
```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val channel = Channel<Int>(4) // create buffered channel
    val sender = launch(coroutineContext) { // launch sender coroutine
        repeat(10) {
            println("Sending $it") // print before sending each element
            channel.send(it) // will suspend when buffer is full
        }
    }
    // don't receive anything... just wait....
    delay(1000)
    sender.cancel() // cancel sender coroutine
}
```

它可以使用四个容量的缓冲 channel 打印 sending 五次。

发送方发送的前四个元素存在了channel的缓冲区，并且在尝试发送第五个元素的时候发送方被挂起。

### channel是公平的

对于多协程的调用顺序来说， channel 的发送和接受操作是公平的。它们都是先进先出，例如，第一个协程调用 `receive` 去获取元素。在下面的示例中，2个协程 ping 和 pong 从共享的 "table" channel 中 接收 ball 对象

```kotlin
data class Ball(var hits: Int)

fun main(args: Array<String>) = runBlocking<Unit> {
    val table = Channel<Ball>() // a shared table
    launch(coroutineContext) { player("ping", table) }
    launch(coroutineContext) { player("pong", table) }
    table.send(Ball(0)) // serve the ball
    delay(1000) // delay 1 second
    coroutineContext.cancelChildren() // game over, cancel them
}

suspend fun player(name: String, table: Channel<Ball>) {
    for (ball in table) { // receive the ball in a loop
        ball.hits++
        println("$name $ball")
        delay(300) // wait a bit
        table.send(ball) // send the ball back
    }
}
```

ping 协程首先启动，因此它是第一个接收 ball 的。 尽管 ping 协同程序在发回 ball 后立即开始接收 ball，但  ball 被 ping pong 协程接收，因为它已经在等待 ball：

```
ping Ball(hits=1)
pong Ball(hits=2)
ping Ball(hits=3)
pong Ball(hits=4)
```

注意,由于执行程序所使用的性质, 有时 channel 可能会产生执行看起来不公平。有关详细信息,请参阅这一问题 [issus](https://github.com/Kotlin/kotlinx.coroutines/issues/111)。