// FILE: Base.java
public class Base {
    protected String field;
}

// FILE: test.kt
class Derived : Base() {
    fun foo() {
        if (field == null) {
            field = "Alpha"
            field<!UNSAFE_CALL!>.<!>length
        }
    }
}

interface First

interface Second : First {
    val baz: Third
}

interface Third : First {
    val s: String
}

fun bar(arg: First) {
    var v = arg
    if (v is Second) {
        v = v.baz
        v.s.length
    }
}
