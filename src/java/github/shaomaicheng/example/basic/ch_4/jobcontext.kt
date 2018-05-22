package github.shaomaicheng.example.basic.ch_4

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) = runBlocking<Unit> {
    println("My job is ${coroutineContext[Job]}")
}
