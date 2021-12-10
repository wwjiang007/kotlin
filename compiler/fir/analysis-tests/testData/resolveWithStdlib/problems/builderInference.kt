class Wrapper(val tag: String)

fun foo(wrappers: List<Wrapper>) = buildList {
    wrappers.mapTo(this) { it.tag }
}

abstract class AnyVisitor {
    abstract fun visit(arg: Any)
}

class ListWrapper(val tags: List<String>)

fun Any.accept(visitor: AnyVisitor) {
    visitor.visit(this)
}

fun bar(wrapper: ListWrapper) = <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>buildSet<!> {
    wrapper.accept(object : AnyVisitor() {
        override fun visit(arg: Any) {
            if (arg is ListWrapper) {
                for (tag in arg.tags) {
                    add(tag)
                }
            }
        }
    })
}
