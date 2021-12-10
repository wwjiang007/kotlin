open class Value(val s: String)

class Obj(s: String) : Value(s)

object Generator {
    fun <T : Value> createValue(s: String): T = (if (s.isEmpty()) Obj(s) else Value(s)) <!UNCHECKED_CAST!>as T<!>

    fun createObject(s: String) = Obj(s)
}

fun doIt(s: String?) =
    (Generator.createValue(s ?: "{}") as? Obj).also {
        if (it == null) {
            println()
        }
    } ?: Generator.createObject("")

val x by lazy {
    Generator.createValue("AlphaBeta") as Obj
}

val y = Generator.createValue("Omega") as Obj
