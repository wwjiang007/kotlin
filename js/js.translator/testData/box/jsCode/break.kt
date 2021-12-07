// EXPECTED_REACHABLE_NODES: 1282
package foo

var c: Int = 0

fun box(): String {
    js("""
        for (var i = 0; i < 10; i++) {
            c = i;

            if (i === 3) {
                break;
            }
        }
    """)

    assertEquals(3, c)

    return "OK"
}