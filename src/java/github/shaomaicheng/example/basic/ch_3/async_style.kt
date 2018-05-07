package github.shaomaicheng.example.basic.ch_3

import kotlinx.coroutines.experimental.*
import kotlin.system.measureTimeMillis

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

fun asyncSomethingUsefulOne() = async {
    doSomethingUsefulOne()
}

fun asyncSomethingUsefulTwo() = async {
    doSomethingUsefulTwo()
}