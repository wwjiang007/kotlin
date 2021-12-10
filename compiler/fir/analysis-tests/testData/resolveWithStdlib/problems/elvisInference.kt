open class Value(val s: String)

class Obj(s: String) : Value(s)

object Generator {
    fun <T : Value> createValue(s: String) = if (s.isEmpty()) Obj(s) else Value(s)

    fun createObject(s: String) = Obj(s)
}

fun doIt(s: String?) =
    (Generator.<!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>createValue<!>(s ?: "{}") as? Obj) ?: Generator.createObject("")

val x by lazy {
    Generator.<!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>createValue<!>("AlphaBeta") as Obj
}

val y = Generator.<!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>createValue<!>("Omega") as Obj
