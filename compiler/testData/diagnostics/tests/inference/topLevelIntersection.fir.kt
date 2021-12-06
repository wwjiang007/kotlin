// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE -UNUSED_EXPRESSION
// FULL_JDK
// FILE: DataIndexer.java
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public interface DataIndexer<Key, Value, Data> {
    @NotNull
    Map<Key, Value> map(@NotNull Data var1);
}

// FILE: main.kt
import java.util.*

interface A
interface B

private val INDEXER = DataIndexer { inputData: A ->
    val result = HashMap<B, Void?>()

    result
}
