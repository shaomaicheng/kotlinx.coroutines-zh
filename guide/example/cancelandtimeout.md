### 取消和超时
这一节内容涵盖协程的取消和超时

### 取消协程的执行
在一个小的应用中，从主协程返回，让所有的协程隐式的退出是一个好主意。
在一个大的并且长时间运行的应用中，你需要更细粒度的控制。一个运行的函数返回一个 Job 可以用来取消一个正在运行的协程。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val job = launch {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancel() // cancels the job
    job.join() // waits for job's completion 
    println("main: Now I can quit.")
}
```

它的输出如下：
```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
main: I'm tired of waiting!
main: Now I can quit.
```

一旦主协程调用了 `job.cancel` ，我们就看不到其他协程的输出了，因为它被取消了。还有一个 [Job](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html) 的扩展函数 [cancelAndJoin](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/cancel-and-join.html) 结合了 [cancel](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/cancel.html) 和 [join](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/join.html)的功能。


### 取消是合作性的

协程的取消是合作性的。协程代码必须合作才可以被取消。`kotlinx.coroutines` 包里面所有的 supending 函数都可以被取消。它们检查协程的取消并在取消的时候抛出一个 CancellationException 异常。但是，如果协程正在进行计算并且不检查取消，则不能取消。示例如下：

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val startTime = System.currentTimeMillis()
    val job = launch {
        var nextPrintTime = startTime
        var i = 0
        while (i < 5) { // computation loop, just wastes CPU
            // print a message twice a second
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}
```

运行它你会发现它在取消后继续打印 "I'm sleeping" 直到任务完成了 5 次迭代。

### 使计算的代码可以退出

这里有2个方法可以让正在计算的代码取消。第一个是定期的调用 suspending 函数去检查取消。[yield](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/yield.html)函数是一个很好的选择。另一个办法是显示的检查取消状态。让我们试试后面一种方法：

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val startTime = System.currentTimeMillis()
    val job = launch {
        var nextPrintTime = startTime
        var i = 0
        while (isActive) { // cancellable computation loop
            // print a message twice a second
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}
```

正如你所见，循环终止了。 [isActive](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/is-active.html)是一个属性，可以通过[CoroutineScope](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/index.html)在协程的代码内部使用。

### 通过 finally 关闭资源

取消一个 suspending 函数抛出了一个 CancellationException , 在取消的时候可以用统一的方法去处理。比如，函数在协程被取消时通常在 `try {...} finally {...}` 表达式 和 koltin `use` 中执行最终的行为

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val job = launch {
        try {
            repeat(1000) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        } finally {
            println("I'm running finally")
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}
```

join和cancelAnd都等待所有完成操作完成，因此上面的示例会生成以下输出：
```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
main: I'm tired of waiting!
I'm running finally
main: Now I can quit.
```

### 运行不可取消的代码块

任何在前面例子的 finally 块中使用 suspending 函数都会导致 CancellationException，因为运行这个代码的协程被取消。通常情况下这都不是问题，因为所有的关闭操作（关闭文件、取消任务或者关闭任何类型的通信通道）都是非阻塞的，并且不涉及任何 suspending 函数。但是，在极少数情况下，如果需要在取消的协程中暂停，可以使用 [run]() 函数和  NonCancellable 上下文 将相应的代码封装在 `run(NonCancellable) {...}` 中， 就像下面的实例一样：

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val job = launch {
        try {
            repeat(1000) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        } finally {
            run(NonCancellable) {
                println("I'm running finally")
                delay(1000L)
                println("And I've just delayed for 1 sec because I'm non-cancellable")
            }
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}
```