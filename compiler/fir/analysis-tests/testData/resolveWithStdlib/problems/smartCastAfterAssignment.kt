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

fun bar(arg: Any) {
    var v = arg
    if (v is List<*>) {
        v = v.joinToString()
        v.length
    }
}
