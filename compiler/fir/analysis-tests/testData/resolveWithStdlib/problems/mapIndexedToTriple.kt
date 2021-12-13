// FILE: Holder.java
import org.jetbrains.annotations.NotNull;
public interface Holder {
    Catch @NotNull [] getCatchClauses();
}

// FILE: Catch.java
import org.jetbrains.annotations.NotNull;
import java.util.*;
public interface Catch {
    @NotNull
    Collection<Reference> getReferences();
}

// FILE: Reference.java
import org.jetbrains.annotations.NotNull;
public interface Reference {
    @NotNull
    Type getType();
}

// FILE: Type.java
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
public interface Type {
    @NotNull
    public PhpType add(@Nullable String aClass);

    public boolean isEmpty();
}

// FILE: PhpType.java
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
public class PhpType {
    @NotNull
    public PhpType add(@Nullable String aClass) {
        return this;
    }

    public boolean isEmpty() {
        return true;
    }
}

// FILE: PhpThrownExceptionsAnalyzer.java
import org.jetbrains.annotations.NotNull;
import java.util.*;
public class PhpThrownExceptionsAnalyzer {

    @NotNull
    public static Collection<String> filterNonThrowable(@NotNull final Set<String> exceptions) {
        return null;
    }

    @NotNull
    public static Collection<String> filterNonThrowable(@NotNull final Type type) {
        return null;
    }
}

// FILE: test.kt

fun build(): Any = object {
    fun foo(holder: Holder) {
        val catches = holder.catchClauses
        for ((catchIndex, catch) in catches.withIndex()) {
            val triples = catch.references.mapIndexed { i, reference ->
                Triple(i, reference, reference.getType())
            }.filter { !it.third.isEmpty }
        }
    }

    private fun Reference.getType() =
        PhpThrownExceptionsAnalyzer.filterNonThrowable(type).fold(PhpType(), { acc, it -> acc.add(it) })
}