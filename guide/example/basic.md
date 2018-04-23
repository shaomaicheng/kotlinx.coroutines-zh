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

`runBlocking {...}` 工作类似一个适配器，在这使用是开始一个顶层的主协程。 `runBlocking` 之外的常规代码阻塞， 直到协程内部的 `runBlocking` 活跃。

这也是一个方法去给suspending的函数编写单元测试
```kotlin
class MyTest {
    @Test
    fun testMySuspendingFunction() = runBlocking<Unit> {
        // here we can use suspending functions using any assertion style that we like
    }
}
```