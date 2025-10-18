package com.wang_lab.mkm_core.components.writer

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import java.io.File

/**
 * This class is used for output some text information in command line mode.
 */
abstract class Writer(val model: ReactionModel, par: JsonObject) {
    abstract fun output()
    protected fun findUnusedName(f: File): File{
        val name = f.nameWithoutExtension
        val ext = f.extension
        val parent = f.parent
        var i = 0
        while (true){
            val target = File(parent, "${name}_$i.$ext")
            if(!target.exists())
                return target
            i ++
        }

    }
    protected fun protectFile(f: File): Boolean{
        if(f.exists()) return f.renameTo(findUnusedName(f))
        return true
    }
}