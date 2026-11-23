package world.lunlun.furnmigrate.converter

import java.io.File

/**
 * Textures that are not vanilla (`lunworld:furniture/logo`). They live in a local folder that is not in
 * git (the server's own art), as `<folder>/<path>.png`, and are copied to `assets/<namespace>/textures/`.
 */
object CustomTextures {
    fun isCustom(id: String): Boolean = namespaced(id).substringBefore(':') != "minecraft"

    private fun path(id: String) = namespaced(id).substringAfter(':') + ".png"

    /** Custom textures of [design] that are not in [dir]. */
    fun missing(design: Design, dir: File): List<String> =
        design.textures.values.filter { isCustom(it) && !File(dir, path(it)).isFile }

    fun copy(designs: List<Design>, dir: File, assetsOut: File) {
        for (id in designs.flatMap { it.textures.values }.filter(::isCustom).distinct()) {
            val ns = namespaced(id).substringBefore(':')
            File(dir, path(id)).copyTo(File(assetsOut, "$ns/textures/${path(id)}"), overwrite = true)
        }
    }
}
