// FIR_IDENTICAL
// WITH_STDLIB

fun main() {
    val map1 = buildMap {
        put(1, "one")
        val x = get(0)

        foo(x) // type mismatch because foo(Int) was chosen as the most specific, but after postponed type variable fixation it isn't suitable anymore
    }
}

fun foo(x: Int) {}
fun foo(x: Any) {}