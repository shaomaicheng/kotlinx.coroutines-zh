#### 协程基础
-

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

实际上，协程是一种轻量级线程。它们和协程生成器一起启动。您可以用 `thread {...}` 替代 `launcg {...}` 和使用 `Thread.sleep(...)` 替换 `delay(...)`。您可用尝试一下。

如果你开始使用 `thread` 替代 `launch`， 编译器可能会产生下面这个错误

`Error: Kotlin: Suspend functions are only allowed to be called from a coroutine or another suspend function`

这是因为 [delay](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/delay.html) 是一个特殊的正在暂停中的函数，不阻塞线程。但是一个 `suspends coroutine` 只能在协程中被使用。