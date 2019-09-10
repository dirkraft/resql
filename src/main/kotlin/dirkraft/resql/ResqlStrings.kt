package dirkraft.resql

object ResqlStrings {
    // That is to say snake_case
    fun camel2Snake(s: String): String {
        return s.replace(Regex("[A-Z]"), "_$0").toLowerCase().removePrefix("_")
    }
}