// "Add parameter to function 'called'" "true"
// WITH_RUNTIME
// DISABLE-ERRORS

fun caller() {
    called(<caret>setOf(1, 2, 3))
}

fun called() {}
