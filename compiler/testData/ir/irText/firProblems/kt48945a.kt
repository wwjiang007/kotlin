// SKIP_KLIB_TEST
// FULL_JDK
interface MSS : Map<String, String>

class Test : MSS, java.util.AbstractMap<String, String>() {
    override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
        get() = throw Exception()
}
