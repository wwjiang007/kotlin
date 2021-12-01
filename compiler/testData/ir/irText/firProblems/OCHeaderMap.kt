interface OCHeaderMap : Map<String, String> {
}

abstract class OCMutableHeaderMap : OCHeaderMap, MutableMap<String, String>, java.util.AbstractMap<String, String>() {
}
