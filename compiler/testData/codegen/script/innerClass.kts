
inner class InnerClass(val s: String) {
    fun foo() = s
}

val rv = InnerClass("OK").foo()

// expected: rv: OK
