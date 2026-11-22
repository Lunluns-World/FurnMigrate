package world.lunlun.furnmigrate.converter


@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any>.compound(key: String): Map<String, Any>? = this[key] as? Map<String, Any>

internal fun Map<String, Any>.num(key: String): Double? = (this[key] as? Number)?.toDouble()

internal fun Map<String, Any>.flag(key: String): Boolean = (this[key] as? Number)?.toInt() == 1
