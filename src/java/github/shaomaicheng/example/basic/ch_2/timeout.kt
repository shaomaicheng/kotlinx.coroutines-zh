package github.shaomaicheng.example.basic.ch_2

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withTimeout

fun main(args: Array<String>) = runBlocking {
    withTimeout(1300L) {
        repeat(1000) { i->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
}