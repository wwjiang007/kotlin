// TODO KT-36650 Don't generate CHECKCAST on null values in JVM_IR

fun test(ss: List<String?>) {
    val shortStrings = hashSetOf<String>()
    val longStrings = hashSetOf<String>()
    for (s in ss) {
        s?.let {
            if (s.length < 4) {
                shortStrings.add(s)
            }
            else {
                longStrings.add(s)
            }
        }
    }
}

// JVM_TEMPLATES
// 0 INVOKESTATIC java/lang/Boolean\.valueOf
// 0 CHECKCAST java/lang/Boolean
// 0 ACONST_NULL
// 2 POP

// JVM_IR_TEMPLATES
// 0 INVOKESTATIC java/lang/Boolean\.valueOf
// 0 CHECKCAST java/lang/Boolean
// 0 ACONST_NULL
// 1 POP