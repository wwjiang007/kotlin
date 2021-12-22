// WITH_STDLIB

@JvmName("foo1")
fun foo(x: Inv<String>) {}
fun foo(x: Inv<Int>) {}

@JvmName("foo21")
fun Inv<String>.foo2() {}
fun Inv<Int>.foo2() {}

@JvmName("bar1")
fun String.bar() {}
fun Int.bar() {}

class Inv<K>(x: K)

fun foo0(x: String, y: Float, z: String = "") {}
fun foo0(x: String, y: Float, z: Int = 1) {}

fun foo00(x: String, y: Number) {}
fun foo00(x: CharSequence, y: Float) {}

fun foo000(x: String, y: Number, z: String) {}
fun foo000(x: CharSequence, y: Float, z: Int) {}

fun foo0000(x: String, y: Number, z: String) {}
fun foo0000(x: Int, y: Float, z: Int) {}

fun foo0001(x: List<Int>, y: Number, z: String) {}
fun foo0001(x: String, y: Float, z: Int) {}

fun foo0002(x: Int, y: Number, z: String) {}
fun foo0002(x: String, y: Float, z: Int) {}

fun Int.foo0003(y: Number, z: String) {}
fun String.foo0003(y: Float, z: Int) {}

@JvmName("foo111")
fun foo11(x: MutableSet<MutableMap.MutableEntry<String, Int>>) {}
@JvmName("foo112")
fun foo11(x: MutableSet<MutableMap.MutableEntry<Int, String>>) {}
fun foo11(x: MutableSet<MutableMap.MutableEntry<Int, Int>>) {}

fun main() {
    val list1 = buildList {
        add("one")

        val secondParameter = get(1)
        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>println<!>(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String")!>secondParameter<!>) // ERROR: [OVERLOAD_RESOLUTION_AMBIGUITY] Overload resolution ambiguity. All these functions match.
    }
    val list2 = buildList {
        add("one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>println<!>(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String")!>get(1)<!>) // ERROR: [OVERLOAD_RESOLUTION_AMBIGUITY] Overload resolution ambiguity. All these functions match.
    }
    val list3 = buildList {
        add("one")

        val secondParameter = Inv(get(1))
        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo<!>(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("Inv<TypeVariable(E)>; Inv<String>")!>secondParameter<!>)
    }
    val list4 = buildList {
        add("one")

        val secondParameter = get(1)
        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo<!>(Inv(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>secondParameter<!>))
    }
    val list5 = buildList {
        add("one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo<!>(Inv(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>get(1)<!>))
    }
    val list6 = buildList {
        add("one")

        <!STUB_TYPE_IN_RECEIVER_CAUSES_AMBIGUITY("TypeVariable(E); String, Int; org.jetbrains.kotlin.resolve.calls.util.BuilderLambdaLabelingInfo@431c85ef")!>get(0)<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>bar<!>()
    }
    val list7 = buildList {
        add("one")

        with (get(0)) {
            <!STUB_TYPE_IN_RECEIVER_CAUSES_AMBIGUITY("TypeVariable(E); String, Int; org.jetbrains.kotlin.resolve.calls.util.BuilderLambdaLabelingInfo@431c85ef")!><!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>bar<!>()<!>
        }
    }
    val list71 = buildList {
        add("one")

        with (get(0)) l1@ {
            with (listOf(1)) {
                <!STUB_TYPE_IN_RECEIVER_CAUSES_AMBIGUITY("TypeVariable(E); String, Int; org.jetbrains.kotlin.resolve.calls.util.BuilderLambdaLabelingInfo@28656b0c")!><!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>bar<!>()<!>
            }
        }
    }
    val list8 = buildList {
        add("one")

        <!STUB_TYPE_IN_RECEIVER_CAUSES_AMBIGUITY("Inv<TypeVariable(E)>; Inv<String>, Inv<Int>; org.jetbrains.kotlin.resolve.calls.util.BuilderLambdaLabelingInfo@431c85ef")!>Inv(get(0))<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo2<!>()
    }
    val list9 = buildList {
        add("one")

        with (get(0)) {
            with (Inv(this)) {
                <!STUB_TYPE_IN_RECEIVER_CAUSES_AMBIGUITY("Inv<TypeVariable(E)>; Inv<String>, Inv<Int>; org.jetbrains.kotlin.resolve.calls.util.BuilderLambdaLabelingInfo@431c85ef")!><!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo2<!>()<!>
            }
        }
    }

    // Resolution ambiguities below aren't due to stub types
    val list10 = buildList {
        add("one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY!>foo0<!>(get(0), 0f)
    }
    val list11 = buildList {
        add("one")

        val x = get(0)
        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY!>foo0<!>(x, 0f)
    }
    val list12 = buildList {
        add("one")
        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY!>foo00<!>(get(0), 0f)
    }

    // Below are multi-arguments resolution ambiguities
    val list13 = buildList {
        add("one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo000<!>(get(0), 0f, <!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>get(0)<!>)
    }

    val list14 = buildList {
        add("one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo0000<!>(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>get(0)<!>, 0f, <!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>get(0)<!>)
    }

    val list15 = buildList {
        add("one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo0001<!>(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); List<Int>, String")!>get(0)<!>, 0f, <!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>get(0)<!>)
    }

    val list16 = buildList {
        add("one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo0002<!>(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); Int, String")!>get(0)<!>, 0f, <!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>get(0)<!>)
    }

    val list17 = buildList l1@ {
        add("one")

        with (get(0)) {
            <!STUB_TYPE_IN_RECEIVER_CAUSES_AMBIGUITY("TypeVariable(E); Int, String; org.jetbrains.kotlin.resolve.calls.util.BuilderLambdaLabelingInfo@431c85ef")!><!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo0003<!>(0f, <!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>this@l1.get(0)<!>)<!>
        }
    }

    val list18 = buildList {
        add("one")

        <!STUB_TYPE_IN_RECEIVER_CAUSES_AMBIGUITY("TypeVariable(E); Int, String; org.jetbrains.kotlin.resolve.calls.util.BuilderLambdaLabelingInfo@431c85ef")!>get(0)<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo0003<!>(0f, <!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("TypeVariable(E); String, Int")!>get(0)<!>)
    }

    val map1 = buildMap {
        put(1, "one")

        <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE, OVERLOAD_RESOLUTION_AMBIGUITY_BECAUSE_OF_STUB_TYPES!>foo11<!>(<!STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY("MutableSet<MutableMap.MutableEntry<TypeVariable(K), TypeVariable(V)>>; MutableSet<MutableMap.MutableEntry<String, Int>>, MutableSet<MutableMap.MutableEntry<Int, String>>, MutableSet<MutableMap.MutableEntry<Int, Int>>")!>entries<!>)
    }
}
