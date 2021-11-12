// FULL_JDK
import java.util.*

fun <E : Enum<E>> foo(values: Array<E>) {
    val value = values.first()
    value.declaringClass
}
