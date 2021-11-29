var LOG = ""

fun log(message: Any?) {
    LOG += message
    LOG += ";"
}

fun pullLog(): String {
    val string = LOG
    LOG = ""
    return string
}

fun testKt14013() {
    val a: Any? = arrayOf(null, 1)
    log(a)
    log(a.toString())
    log(a!!.toString())

    if (testUtils.isLegacyBackend()) {
        assertEquals(",1;[...];,1;", pullLog())
    } else {
        assertEquals("[...];[...];[...];", pullLog())
    }
}

fun concreteArrayToString(a: Array<Int>) {
    log(a.toString())
    log("$a")
    log("" + a)
}

fun <T> genericValueToString(a: T) {
    log(a.toString())
    log("$a")
    log("" + a)
}

fun anyValueToString(a: Any) {
    log(a.toString())
    log("$a")
    log("" + a)
}

fun box(): String {
    testKt14013()

    val a = arrayOf(1, 2, 3)
    concreteArrayToString(a)

    if (testUtils.isLegacyBackend()) {
        assertEquals("1,2,3;1,2,3;[...];", pullLog())
    } else {
        assertEquals("[...];1,2,3;1,2,3;", pullLog())
    }

    genericValueToString(a)

    if (testUtils.isLegacyBackend()) {
        assertEquals("[...];1,2,3;[...];", pullLog())
    } else {
        assertEquals("[...];[...];[...];", pullLog())
    }

    anyValueToString(a)

    if (testUtils.isLegacyBackend()) {
        assertEquals("1,2,3;1,2,3;[...];", pullLog())
    } else {
        assertEquals("[...];[...];[...];", pullLog())
    }

    return "OK"
}