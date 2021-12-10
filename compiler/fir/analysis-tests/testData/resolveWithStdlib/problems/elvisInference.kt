// FILE: Generator.java

import org.jetbrains.annotations.NotNull;

public class Generator {
    @NotNull
    public <T extends Value> T createValue(@NotNull String content) {
        return (T) (new Value(content));
    }

    @NotNull
    public Obj createObject(@NotNull String content) {
        return new Obj(content);
    }
}

// FILE: test.kt
open class Value(val s: String)

class Obj(s: String) : Value(s)

val generator = Generator()

fun doIt(s: String?) =
    (generator.<!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>createValue<!>(s ?: "{}") as? Obj).also {
        if (it == null) {
            println()
        }
    } ?: generator.createObject("")

val x by lazy {
    generator.<!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>createValue<!>("AlphaBeta") as Obj
}

val y = generator.<!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>createValue<!>("Omega") as Obj
