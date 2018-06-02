### 协程上下文和调度器

协程总是运行在一些定义在 kotlin 标准库中，以 [CoroutineContext](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines.experimental/-coroutine-context/)类型为代表的上下文中。

协程的上下文是一个各种元素的集合。主要的元素是协程的 [Job](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html)，我们将在这节介绍它的调度器。

### 调度器和线程

协程上下文包括一个协程调度器(见 [CoroutineDispatcher](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-dispatcher/index.html))，这个调度器决定了相应的协程运行在哪个线程（也可能是多个线程）。协程调度器可以限定协程运行在一个特定的线程，或者分派到一个线程池，或者让它不受限制的运行。

所有的像 [launch](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/launch.html) 和 [async](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/async.html) 一样协程构造器接受一个可选的 [CoroutineContext](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines.experimental/-coroutine-context/) 参数可以用来显示的为新协程或者其他上下文元素指定调度器。

尝试下面的例子：

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val jobs = arrayListOf<Job>()
    jobs += launch(Unconfined) { // not confined -- will work with main thread
        println("      'Unconfined': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs += launch(coroutineContext) { // context of the parent, runBlocking coroutine
        println("'coroutineContext': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs += launch(CommonPool) { // will get dispatched to ForkJoinPool.commonPool (or equivalent)
        println("      'CommonPool': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs += launch(newSingleThreadContext("MyOwnThread")) { // will get its own new thread
        println("          'newSTC': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs.forEach { it.join() }
}
```

它产生如下输出：
```
'Unconfined': I'm working in thread main
      'CommonPool': I'm working in thread ForkJoinPool.commonPool-worker-1
          'newSTC': I'm working in thread MyOwnThread
'coroutineContext': I'm working in thread main
```

我们在前几节使用的默认调度器由 [DefaultDispatcher](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-default-dispatcher.html)代表，这个和当前实现的 [CommonPool](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-common-pool/index.html) 相同，所以 `launch {...}` 和 `launch(DefaultDispather) {...}` 一样，和 `launch(CommonPool) {...}` 也一样。

父 [coroutineContext](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/coroutine-context.html) 和 [Unconfined](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-unconfined/index.html) 的区别会在稍后展示。

注意，[newSingleThreadContext](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/new-single-thread-context.html) 创建了一个新的线程，这是非常耗费资源的。在实际的应用中，当他它不再需要的时候，它必须被释放，使用 [close](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-thread-pool-dispatcher/close.html) 函数，或者存储在一个底层变量让整个应用重用。

### 无限制的和有限制的调度器

[Unconfined](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-unconfined/index.html) 协程调度器在调用者线程中启动，但是只到第一个暂停点。暂停后在由被调用的暂停功能完全确定的线程中恢复。当协程没有耗费 CPU 时间或者没有更新任何局限在特定线程的共享数据（例如 UI），无限制的调度器是合适的。

另一方面，[coroutineContext](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/coroutine-context.html) 属性在通过 [CoroutineScope](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/index.html) 接口在任何协程块中使用，是对这个特定协程的上下文的引用。通过这个方法，一个父上下文可以被继承。[runBlocking](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/run-blocking.html) 协程默认的调度器，尤其是仅限于调用者线程的，所以继承它具有将执行限制在具有可预测的FIFO调度的该线程的效果。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val jobs = arrayListOf<Job>()
    jobs += launch(Unconfined) {
        // not confined -- will work with main thread
        println("      'Unconfined': I'm working in thread ${Thread.currentThread().name}")
        delay(500)
        println("      'Unconfined': After delay in thread ${Thread.currentThread().name}")
    }
    jobs += launch(coroutineContext) {
        // context of the parent, runBlocking coroutine
        println("'coroutineContext': I'm working in thread ${Thread.currentThread().name}")
        delay(1000)
        println("'coroutineContext': After delay in thread ${Thread.currentThread().name}")
    }
    jobs.forEach { it.join() }
}

```

会产生一下输出：
```
 'Unconfined': I'm working in thread main
'coroutineContext': I'm working in thread main
      'Unconfined': After delay in thread kotlinx.coroutines.DefaultExecutor
'coroutineContext': After delay in thread main
```

所以，继承了 `runBlocking {...}` 的 `coroutineContext` 协程运行在 `main` 线程，不受限的协程在使用了 [delay](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/delay.html) 的时候在默认的执行者线程中恢复。

### 调试协程和线程

在使用 [Unconfined](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-unconfined/index.html) 调度器 或者 使用默认的多线程调度器的时候，协程是可以暂停并且在另一个线程恢复的。即使在单线程的调度器中，也是非常不容易去判断协程什么时候，在什么地方，正在做什么事情。一个公共的多线程下的调试方法就是在每一句log的代码里面去打印当前的线程名称。这个功能在日志框架中是普遍支持的。 当使用协程的时候，单独的线程名是不会给出很多上下文。所以 `kotlinx.coroutines` 包括了调试设备让调试协程更加容易。

使用 `-Dkotlinx.coroutines.debug` jvm参数运行如下代码：
```kotlin
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

fun main(args: Array<String>) = runBlocking<Unit> {
    val a = async(coroutineContext) {
        log("I'm computing a piece of the answer")
        6
    }
    val b = async(coroutineContext) {
        log("I'm computing another piece of the answer")
        7
    }
    log("The answer is ${a.await() * b.await()}")
}
```

这里有3个协程，主协程(#1) -- 一个是 `runBlocking`， 其它 2 个协程计算延迟的值 a(#2) 和 b(#3)。它们都运行在 `runBlocking` 的上下文，并且被主线程控制。输出的代码如下：

```
[main @coroutine#2] I'm computing a piece of the answer
[main @coroutine#3] I'm computing another piece of the answer
[main @coroutine#1] The answer is 42
```

你可以看到 `log` 功能在方括号中打印线程的名称。那是 `main` 线程。当时当前运行的协程的识别码附加到了它上面。在打开调试模式时，此标识符将连续分配给所有创建的协同程序。

您可以在 [newCoroutineContext](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/new-coroutine-context.html)的文档上阅读更多关于调试设备的内容。

### 在线程之间跳转

使用 `-Dkotlinx.coroutines.debug` jvm参数运行如下代码：

```kotlin
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

fun main(args: Array<String>) {
    newSingleThreadContext("Ctx1").use { ctx1 ->
        newSingleThreadContext("Ctx2").use { ctx2 ->
            runBlocking(ctx1) {
                log("Started in ctx1")
                run(ctx2) {
                    log("Working in ctx2")
                }
                log("Back to ctx1")
            }
        }
    }
}
```

这演示了几项新技术，一个是使用 [runBlocking](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/run-blocking.html) 明确指定上下文， 另一个是使用 [run]() 函数去改变协程的上下文，同时仍然保留在相同的协程中，您可以在下面的输出中看到：
```
[Ctx1 @coroutine#1] Started in ctx1
[Ctx2 @coroutine#1] Working in ctx2
[Ctx1 @coroutine#1] Back to ctx1
```

注意，那个例子也使用了 `use` 函数在不再需要使用 [newSingleThreadContext](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/new-single-thread-context.html) 创建的时候，去释放线程。

### 上下文中的Job

协程的 [job](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html) 是上下文的一部分，协程可以使用 `coroutineContext[Job]` 表达式从它自己的上下文中检索它:

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    println("My job is ${coroutineContext[Job]}")
}
```

在 debug model 下会产生如下输出：

```
My job is "coroutine#1":BlockingCoroutine{Active}@6d311334
```

所以， [CoroutineScope](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/index.html) 的[isActive](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/is-active.html) 只是 `coroutineContext[Job]!!.isActive.` 的一个方便途径。

### 子协程
当一个协程的 [coroutineContext](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/coroutine-context.html) 被用来启动另一个协程，新的协程的 [Job](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html) 成了父协程的任务的一个孩子。当一个父协程被取消的时候，它的所以的子协程也会被递归的取消。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    // launch a coroutine to process some kind of incoming request
    val request = launch {
        // it spawns two other jobs, one with its separate context
        val job1 = launch {
            println("job1: I have my own context and execute independently!")
            delay(1000)
            println("job1: I am not affected by cancellation of the request")
        }
        // and the other inherits the parent context
        val job2 = launch(coroutineContext) {
            println("job2: I am a child of the request coroutine")
            delay(1000)
            println("job2: I will not execute this line if my parent request is cancelled")
        }
        // request completes when both its sub-jobs complete:
        job1.join()
        job2.join()
    }
    delay(500)
    request.cancel() // cancel processing of the request
    delay(1000) // delay a second to see what happens
    println("main: Who has survived request cancellation?")
}
```

这段代码的输出是：
```
job1: I have my own context and execute independently!
job2: I am a child of the request coroutine
job1: I am not affected by cancellation of the request
main: Who has survived request cancellation?
```

### 合并上下文
协程上下文可以使用`+`操作符合并。右边的上下文取代左边上下文的条目。举个例子，父协程的任务可以被继承，而他的调度员被取代。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    // start a coroutine to process some kind of incoming request
    val request = launch(coroutineContext) { // use the context of `runBlocking`
        // spawns CPU-intensive child job in CommonPool !!! 
        val job = launch(coroutineContext + CommonPool) {
            println("job: I am a child of the request coroutine, but with a different dispatcher")
            delay(1000)
            println("job: I will not execute this line if my parent request is cancelled")
        }
        job.join() // request completes when its sub-job completes
    }
    delay(500)
    request.cancel() // cancel processing of the request
    delay(1000) // delay a second to see what happens
    println("main: Who has survived request cancellation?")
}
```

代码预期的输出是：
```
job: I am a child of the request coroutine, but with a different dispatcher
main: Who has survived request cancellation?
```


### 父协程的职责
一个父协程总是等待它所有的子协程完成。父协程不需要显式的跟踪子协程的启动，并且它不需要去使用子协程的 [Job.join](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/join.html) 等待它们。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    // launch a coroutine to process some kind of incoming request
    val request = launch {
        repeat(3) { i -> // launch a few children jobs
            launch(coroutineContext)  {
                delay((i + 1) * 200L) // variable delay 200ms, 400ms, 600ms
                println("Coroutine $i is done")
            }
        }
        println("request: I'm done and I don't explicitly join my children that are still active")
    }
    request.join() // wait for completion of the request, including all its children
    println("Now processing of the request is complete")
}
```

结果如下：
```
request: I'm done and I don't explicitly join my children that are still active
Coroutine 0 is done
Coroutine 1 is done
Coroutine 2 is done
Now processing of the request is complete
```

### 命名协程用以调试
当 log 经常使用在 coroutine 的时候，自动分配一个id是非常棒的主意。你只需要关联来自于同一个协程的日志记录。然而，当协程和特定请求的处理或者和执行一些特殊的后台任务相关联的时候，最好拥有一个显式的名字以确保调试的目的明确。 [CoroutineName](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-name/index.html) 上下文元素作为一个线程的名字提供相同的功能。当 [调试模式](https://github.com/Kotlin/kotlinx.coroutines/blob/master/coroutines-guide.md#debugging-coroutines-and-threads) 打开的时候，他会显示在执行此协程的线程名字中。

下面的例子演示了这一概念：

```kotlin
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

fun main(args: Array<String>) = runBlocking(CoroutineName("main")) {
    log("Started main coroutine")
    // run two background value computations
    val v1 = async(CoroutineName("v1coroutine")) {
        delay(500)
        log("Computing v1")
        252
    }
    val v2 = async(CoroutineName("v2coroutine")) {
        delay(1000)
        log("Computing v2")
        6
    }
    log("The answer for v1 / v2 = ${v1.await() / v2.await()}")
}
```
使用 `-Dkotlinx.coroutines.debug` JVM 选项显示结果:

```
[main @main#1] Started main coroutine
[ForkJoinPool.commonPool-worker-1 @v1coroutine#2] Computing v1
[ForkJoinPool.commonPool-worker-2 @v2coroutine#3] Computing v2
[main @main#1] The answer for v1 / v2 = 42
```
### 通过显式的Job取消

让我们把我们关于上下文、子协程和任务的知识放下，假设我们的应用有一个生命周期相关的对象，但是这个对象并不是一个协程。比如，我们写一个 Android 应用， 在 Activity 的Context 启动各种协程, 用以执行请求和获取数据的异步操作，执行动画等待。当 Activity 销毁的时候，所有的协程都应该被取消。这可以避免内存泄露。

我们可以通过创建一个和 Activity 生命周期关联的 [Job](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html) 实例来管理我们协程的生命周期。一个协程实例使用下面实例的 [Job()](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job.html) 工厂函数生成。方便起见，我们可以写 `launch(coroutineContext, parent = job)` 去显式的指明一个父任务正在被使用的事实，而不是使用 `launch(coroutineContext + job)` 表达式。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val job = Job() // create a job object to manage our lifecycle
    // now launch ten coroutines for a demo, each working for a different time
    val coroutines = List(10) { i ->
        // they are all children of our job object
        launch(coroutineContext, parent = job) { // we use the context of main runBlocking thread, but with our parent job
            delay((i + 1) * 200L) // variable delay 200ms, 400ms, ... etc
            println("Coroutine $i is done")
        }
    }
    println("Launched ${coroutines.size} coroutines")
    delay(500L) // delay for half a second
    println("Cancelling the job!")
    job.cancelAndJoin() // cancel all our coroutines and wait for all of them to complete
}
```

例子输出如下：
```
Launched 10 coroutines
Coroutine 0 is done
Coroutine 1 is done
Cancelling the job!
```

正如你所见，只有最先的三个协程打印了消息， 其他的协程都被 `job.cancelAndJoin()` 单一的调用取消。所以在我们假设的 Android 程序中，我们所需要做的就是在 Activity 创建的时候，创建一个父 job 对象，用它来生成子协程，当 Activity 销毁的时候，取消它。在 Android 的生命周期中，我们不能调用它们的 `join` 函数，因为它是同步的，但是这个加入的能力是在建立后端服务确保有限的资源使用的时候比较有用。