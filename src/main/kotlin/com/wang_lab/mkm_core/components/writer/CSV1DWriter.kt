package com.wang_lab.mkm_core.components.writer

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.switchJsonElement
import java.io.File

class CSV1DWriter(model: ReactionModel, par: JsonObject) : Writer(model, par) {
    private val outputFile = File(model.file.parentFile, switchJsonElement(par["output_file"], "The output file", s = { it }))
    init {
        if(model.mapper.descriptors.size != 1) throw Exception("Mapper must be 1-dimension for CSV1DWriter!")
    }
    override fun output() {
        val data = model.gridPoints().map { p ->
            val map = mutableMapOf<String, Double>()
            p.coverage?.apply {
                forEachIndexed{ i, cvg ->
                    map["theta[${model.adsorbates[i]}]"] = cvg.toDouble()
                }
            }
            p.tof?.apply {
                forEachIndexed{ i, t ->
                    map["r[${model.gases[i]}]"] = t
                }
            }
            p.current?.apply { map["current"] = this }
            p.concentration?.apply {
                forEachIndexed{ i, c ->
                    map[model.aqueous[i].identifier] = c.toDouble()
                }
            }
            p.pressure?.apply {
                forEachIndexed{ i, p ->
                    map[model.gases[i].identifier] = p.toDouble()
                }
            }
            map
        }
        var of = outputFile
        if(of.exists()){
            val rn = protectFile(of)
            if(!rn) of = findUnusedName(outputFile)
        }
        of.parentFile.mkdirs()
        of.createNewFile()
        val rows = LinkedHashSet<String>()
        data.forEach { rows.addAll(it.keys) }
        val writer = of.bufferedWriter()
        writer.write(model.gridPoints().joinToString(", ", "${model.mapper.descriptors[0].name}, ", "\n"){ model.mapper.descriptors[0].scales[it.gridPoint!![0]].toString() })
        rows.forEach {
            writer.write(model.gridPoints().joinToString(", ", "$it, ", "\n"){ p ->
                data[p.gridPoint!![0]][it]?.toString() ?: ""
            })
        }
        writer.close()
    }
}