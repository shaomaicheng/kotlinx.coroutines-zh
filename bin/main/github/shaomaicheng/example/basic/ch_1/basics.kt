package github.shaomaicheng.example.basic.ch_1

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch

fun main(args: Array<String>) {
    launch {
        // launch new coroutine in background and continue
        delay(1000L) // no blocking delay for 1 second (default time unit is ms)
        println("World")
    }

    println("Hello!")
    Thread.sleep(2000L)
}