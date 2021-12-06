// SKIP_TXT
// !DIAGNOSTICS: -UNUSED_VARIABLE
// !LANGUAGE: +NewInference

interface A<E> {
    fun foo(): E
}

interface B : A<Int>
interface C : A<Long>

fun <T> bar(a: A<T>, w: T) {
    if (a is B) {
        baz(a, w) // Fail: expected Int, but found T (on `w`)
    }
}

fun <F> baz(a: A<F>, f: F) {}
