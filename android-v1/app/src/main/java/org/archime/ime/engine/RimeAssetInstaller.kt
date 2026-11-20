package org.archime.ime.engine

import android.content.Context
import java.io.File

/** Copies bundled Rime schemas and dictionaries into app-private storage once. */
object RimeAssetInstaller {
    data class InstallReport(val copied: Int, val kept: Int)

    fun install(context: Context, destination: File): Result<InstallReport> = runCatching {
        var copied = 0
        var kept = 0

        fun copyTree(assetPath: String, output: File) {
            val children = context.assets.list(assetPath).orEmpty()
            if (children.isNotEmpty()) {
                if (!output.exists() && !output.mkdirs()) error("Cannot create ${output.path}")
                children.forEach { child -> copyTree("$assetPath/$child", File(output, child)) }
                return
            }
            if (output.exists() && output.length() > 0L) {
                kept++
                return
            }
            output.parentFile?.let {
                if (!it.exists() && !it.mkdirs()) error("Cannot create ${it.path}")
            }
            val temporary = File(output.parentFile, output.name + ".tmp")
            try {
                context.assets.open(assetPath).use { input ->
                    temporary.outputStream().use { stream -> input.copyTo(stream) }
                }
                if (!temporary.renameTo(output)) error("Cannot finalize ${output.path}")
                copied++
            } finally {
                temporary.delete()
            }
        }

        copyTree("rime", destination)
        val schema = File(destination, "shared/double_pinyin_mspy.schema.yaml")
        check(schema.isFile && schema.length() > 0L) { "Microsoft Shuangpin schema missing" }
        InstallReport(copied, kept)
    }
}
