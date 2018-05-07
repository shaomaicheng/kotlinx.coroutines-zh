### 组合 suspending 函数

本节涵盖了组合 suspending 函数的几种方法

### 按默认顺序

假设我们有 2 个在别处定义的有用的 suspending 函数，例如请求远程服务或者运算，我们只是假装它有用，但实际上每个函数只是为了本例的目的延迟 1 秒：

```kotlin
suspend fun doSomethingUsefulOne(): Int {
    delay(1000L) // pretend we are doing something useful here
    return 13
}

suspend fun doSomethingUsefulTwo(): Int {
    delay(1000L) // pretend we are doing something useful here, too
    return 29
}
```

如果需要依次调用它们，我们应该如何做呢？-- 首先运行 `doSomethingUsefulOne`  如何运行 `doSomethingUsefulTwo`，如何计算它们的总和？实际上如果我们通过第一个函数的结果来决定是否需要调用第二个函数或者决定如何调用它，我们会这样做。

我们只是用正常的顺序去调用，因为协程中的代码和普通代码中的代码一样，默认情况下是顺序执行的。以下实例是通过衡量两个suspending 函数所需要的总时间的演示：

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val time = measureTimeMillis {
        val one = doSomethingUsefulOne()
        val two = doSomethingUsefulTwo()
        println("The answer is ${one + two}")
    }
    println("Completed in $time ms")
}
```

它产生如下结果：
```
The answer is 42
Completed in 2017 ms
```

### 使用 async 并发

如果doSomethingUsefulOne和doSomethingUsefulTwo的调用之间没有依赖关系，并且我们希望通过同时执行这两者来更快地获得答案，那该怎么办？这时候 [async](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/async.html) 就来帮我们了。

从概念上，[async](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/async.html) 就像是 [launch](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/launch.html)，它启动一个单独的协程，它是一个和其他协程同时工作的轻量级线程。不同点在于 `launch` 返回一个 [Job](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html) 并且不会有任何结果， 而 `async` 返回一个 [Deferred](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-deferred/index.html) -- 这是一个轻量级的无阻塞未来，它代表了稍后提供结果的承诺。 您可以在一个延迟的值上使用`.await()` 去获取它最终的结果，但是 `Deferred` 仍然是一个 `Job`,如果你需要，你可以取消它。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val time = measureTimeMillis {
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}
```

它产生如下结果:

```
The answer is 42
Completed in 1017 ms
```

这是两倍的速度， 因为我们同时执行 2 个协程程序。请注意， 协程的并发总是显示的。

### 惰性启动async
在使用 `async` 的时候有一个惰性选项，将 [CoroutineStart.LAZY](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-start/-l-a-z-y.html) 作为 `start` 的参数值。它只在结果需要被[await](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-deferred/await.html) 或者一个 [start](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/start.html) 函数被调用的时候启动一个协程。运行下例代码，和以前不同的是，现在只通过这个选项。

```kotlin
fun main(args: Array<String>) = runBlocking<Unit> {
    val time = measureTimeMillis {
        val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
        val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}
```

它产生如下结果：
```
The answer is 42
Completed in 2017 ms
```


所以，我们回到了顺序执行，因为我们开始等待 `one` ， 然后开始等待 `two`。这并不是惰性的预期用例。它被设计为计算涉及暂停函数的情况下替代标准的 `lazy` 函数。

### Async 风格的函数

我们可以使用 [async](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/async.html) 构造协程，d定义 async 风格的函数去异步的调用 `doSomethingUsefulOne` 和 `doSomethingUsefulTwo`。以“async” 前缀或者 “Async”后缀命名的函数是一个很好的风格，突出显示它们只启动异步计算的事实，并且需要使用得到的延迟值获取结果。

```kotlin
// The result type of asyncSomethingUsefulOne is Deferred<Int>
fun asyncSomethingUsefulOne() = async {
    doSomethingUsefulOne()
}

// The result type of asyncSomethingUsefulTwo is Deferred<Int>
fun asyncSomethingUsefulTwo() = async {
    doSomethingUsefulTwo()
}
```

注意，这些 `asyncXXX` 函数并不是 `suspending`函数。它们可以在任何地方被使用。然而，它们的使用总是按时它们的行为和代码调用异步（这里指并发）执行。

以下示例显示了它们在协程之外的用法

```kotlin
// note, that we don't have `runBlocking` to the right of `main` in this example
fun main(args: Array<String>) {
    val time = measureTimeMillis {
        // we can initiate async actions outside of a coroutine
        val one = asyncSomethingUsefulOne()
        val two = asyncSomethingUsefulTwo()
        // but waiting for a result must involve either suspending or blocking.
        // here we use `runBlocking { ... }` to block the main thread while waiting for the result
        runBlocking {
            println("The answer is ${one.await() + two.await()}")
        }
    }
    println("Completed in $time ms")
}
```