// FIR_IDENTICAL
// https://youtrack.jetbrains.com/issue/KT-49282

fun foo() = runBlocking {
    val f: suspend (String) -> Int = {
        42
    }
}

fun <T> runBlocking(block: suspend Any.() -> T): T = null!!