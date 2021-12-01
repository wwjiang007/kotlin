interface OCHeaderMap : Map<String, String> {
}

abstract class OCMutableHeaderMap : OCHeaderMap, MutableMap<String, String>, java.util.AbstractMap<String, String>() {
}

fun foo(map: OCMutableHeaderMap) {
    // In IR, this 'put' call should be mapped to fake override from OCMutableHeaderMap which doesn't match FIR substitution override:
    //   because FIR substitution override is based on java.util.AbstractMap,
    //   but IR fake override should be based on the first supertype OCHeaderMap
    map.put("Alpha", "Omega")
}
