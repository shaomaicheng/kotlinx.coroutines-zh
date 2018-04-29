
   
  
#### 协程基础

本节涵盖协程的基本概念

##### 您的第一个协程程序

运行如下代码：

```kotlin
fun main(args: Array<String>) {
    launch {
        // launch new coroutine in background and continue
        delay(1000L) // no blocking delay for 1 second (default time unit is ms)
        println("World")
    }

    println("Hello!")
    Thread.sleep(2000L)
}
```

运行这段代码：
```
Hello!
World
```

实际上，协程是一种轻量级线程。它们和协程生成器一起启动。您可以用 `thread {...}` 替代 `launch {...}` 和使用 `Thread.sleep(...)` 替换 `delay(...)`。您可用尝试一下。

如果你开始使用 `thread` 替代 `launch`， 编译器可能会产生下面这个错误

`Error: Kotlin: Suspend functions are only allowed to be called from a coroutine or another suspend function`

这是因为 [delay](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/delay.html) 是一个特殊的正在暂停中的函数，不阻塞线程。但是一个 `suspends coroutine` 只能在协程中被使用。


#### 连接阻塞和非阻塞的世界
第一个例子在主函数的相同代码中混用了非阻塞的 `delay(...)` 和阻塞的 `Thread.sleep`， 很容易疑惑，让我们使用 [runBlocking](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/run-blocking.html) 分离阻塞和非阻塞
```kotlin
fun main(args: Array<String>) = runBlocking<Unit> { // start main coroutine
    launch { // launch new coroutine
        delay(1000L)
        println("World!")
    }
    println("Hello,") // main coroutine continues while child is delayed
    delay(2000L) // non-blocking delay for 2 seconds to keep JVM alive
}
```
结果是一样的，但是这段代码只使用了非阻塞的 [delay](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/delay.html)

`runBlocking {...}` 在这使用是开始一个顶层的主协程的适配器。 `runBlocking` 之外的常规代码阻塞， 直到协程内部的 `runBlocking` 活跃。

这也是一个方法去给suspending的函数编写单元测试
```kotlin
class MyTest {
    @Test
    fun testMySuspendingFunction() = runBlocking<Unit> {
        // here we can use suspending functions using any assertion style that we like
    }
}
```

### 等待一个任务
当另一个协程在运行的时候，延时一段时间去等它并不是一个好的办法。我们可以主动等待（非阻塞的）一个后台运行的协程直到它运行完毕。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
	val job = launch { // launch new coroutine and keep a reference to its Job
	delay(1000L)
	println("World!")
}
	println("Hello,")
    job.join() // wait until child coroutine completes
}
```

这个结果和上面的实例仍然是一致的。但好多了的是，主协程的代码不需要和后台任务的时间关联起来。

### 提取函数进行重构
让我们把 `launche {...}  `里面的代码提取到一个单独的函数里面。当你提取这个单独的函数的时候， 这个新的函数会出现一个新的修饰符 `suspend` 。这是你的第一个 suspend 函数。协程内部可以像一个普通的函数去使用 suspend 函数，相反的，suspend 函数的额外功能就是使用其他的 suspend 函数，比如示例中的 `delay` ，用来暂停执行协程

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val job = launch { doWorld() }
    println("Hello,")
    job.join()
}

// this is your first suspending function
suspend fun doWorld() {
    delay(1000L)
    println("World!")
}
```

### 协程是轻量级的
运行下面这段代码：
```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val jobs = List(100_000) { // launch a lot of coroutines and list their jobs
        launch {
            delay(1000L)
            print(".")
        }
    }
    jobs.forEach { it.join() } // wait for all jobs to complete
}
```

它启动了 10k 个协程， 1s 后，每个协程会都打印一个点。现在尝试用线程去实现相同的代码，会发生什么呢？（多半会产生 内存溢出 异常）


### 协程像守护线程

下面这段代码会启动一个长时间运行的协程， 打印 2 次  “I'm sleeping” 并且在一段延时之后从主协程返回。
```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    launch {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
    delay(1300L) // just quit after delay
}
```

你会在终端里面看到打印出下面这 3 行:
```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
```