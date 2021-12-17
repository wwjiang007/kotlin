val x = object {
    fun foo(types: List<String>) {
        types.mapIndexed { i, length -> Triple(i, length, length.getFilteredType()) }
    }

    private fun String.getFilteredType() = bar(length)
}

fun bar(x: Int) = x
