interface A {
    fun foo(): Any
}

fun test(x: A?) {
    x?.foo()
}

// JVM_TEMPLATES
// 2 POP
// 0 ACONST_NULL

// JVM_IR_TEMPLATES
// 1 POP
// 0 ACONST_NULL
