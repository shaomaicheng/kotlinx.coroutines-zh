package github.shaomaicheng.example.basic.ch_3

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) = runBlocking<Unit> {
    val time = measureTimeMillis {
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}