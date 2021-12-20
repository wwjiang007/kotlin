val x = object {
    fun foo(types: List<String>) {
        val length = "123"
        types.mapIndexed { i, length -> Triple(i, length, length.getFilteredType()) }
    }

    private fun String.getFilteredType() = bar(<!ARGUMENT_TYPE_MISMATCH!>length<!>)
}

fun bar(x: Int) = x
