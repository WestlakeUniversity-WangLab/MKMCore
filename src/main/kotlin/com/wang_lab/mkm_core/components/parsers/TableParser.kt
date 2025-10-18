package com.wang_lab.mkm_core.components.parsers

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.expr.ExprConst
import com.wang_lab.mkm_core.algebra.expr.parseExpression
import com.wang_lab.mkm_core.constants.EnergyUnits
import com.wang_lab.mkm_core.exception.MKMSetupException
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.misc.EnergyInfo
import com.wang_lab.mkm_core.components.thermodynamics.site_thermo.HarmonicSite
import com.wang_lab.mkm_core.species.*
import com.wang_lab.mkm_core.switchJsonElement
import java.io.File

val requiredHeaders = listOf("species_name", "formation_energy", "frequencies")
val siteGas = mapOf("g" to 1)
val siteLiquid = mapOf("l" to 1)
val siteAqua = mapOf("aq" to 1)

class TableParser(model: ReactionModel, par: JsonObject): Parser(model){
    var inputFile: File
    init{
        val path = switchJsonElement(par["input_file"], "Input file", s = { it })
        inputFile = File(model.file.parent, path)
        if(!inputFile.exists()) File(path)
        parseInputFile(inputFile)
    }
    private fun parseContent(inputFile: File): Pair<List<MutableMap<String, String>>, Map<String, EnergyUnits>>{
        var headers: List<String>? = null
        var index = 0
        val info = mutableListOf<MutableMap<String, String>>()
        val energyUnits = mutableMapOf<String, EnergyUnits>()
        inputFile.bufferedReader().forEachLine { line ->
            index ++
            if(!line.contains("\t") && line.contains(":")){
                val pars = line.split(":")
                energyUnits[pars[0].trim()] = EnergyUnits.read(pars[1].trim())!!
            }else if(line.isNotBlank()){
                if(headers == null){
                    headers = line.split('\t')
                    for(h in requiredHeaders) if(h !in headers!!) throw MKMSetupException("table_parser", "$h is not concluded in headers! At input file line $index.")
                }else{
                    val pars = line.split('\t')
                    if(pars.size != headers!!.size) throw Exception("Header has ${headers!!.size} items but this line only has ${pars.size}. At input file line $index.")
                    val map = mutableMapOf<String, String>()
                    info.add(map)
                    for(i in headers!!.indices) map[headers!![i]] = pars[i]
                }
            }
        }
        return Pair(info, energyUnits)
    }
    private fun parseInputFile(inputFile: File){
        val (info, energyUnits) = parseContent(inputFile)
        val unitFE = energyUnits["formation_energy"] ?: EnergyUnits.EUeV
        val unitFreq = energyUnits["frequencies"] ?: EnergyUnits.EUcm1
        info.forEach { map ->
            val speciesName = map.remove("species_name")!!
            val siteOld = map.remove("site_name")
            val interpret = Species.interpret(if(siteOld != null)"$speciesName*" else speciesName, model.defaultSite)
            if(interpret == null){
                logger?.warning("$speciesName is not a valid species name!")
                return@forEach
            }
            val (config, formula, charge) = interpret.first
            val sp = if(formula == "None") {
                val sites = interpret.third
                val rs = if (sites.size == 1) sites.keys.first() else throw Exception("You can only define energies for a single site!")
                model.sitesE.first{ it.name == rs }
            }else if(siteOld != null){
                when(siteOld){
                    "gas" -> model.gasesE
                    "liquid" -> model.liquidsE
                    "aqua" -> model.aqueous
                    else -> {
                        val sites = model.sitesE.filter { it.siteNames.contains(siteOld) }
                        model.surfaceSpeciesE.filter { it.site.size == 1 && it.site.keys.first() in sites }
                    }
                }.firstOrNull{ it.formula == formula && it.config == config && it.charge == charge }
            }else{
                val site = interpret.third
                when(site){
                    siteGas -> model.gasesE
                    siteLiquid -> model.liquidsE
                    siteAqua -> model.aqueous
                    else -> {
                        val rs = site.mapKeys { (k, _) -> model.sitesE.first{ it.name == k } }
                        model.surfaceSpeciesE.filter { it.site == rs }
                    }
                }.firstOrNull{ it.formula == formula && it.config == config && it.charge == charge }
            } ?: return@forEach
            val formationEnergy = (ExprConst(unitFE.scale) * parseExpression(map.remove("formation_energy")!!)).simplify()
            val reference = map.remove("reference")
            val fs = map.remove("frequencies")
            val freq =
                fs?.substring(1, fs.length -1)?.split(',')?.mapNotNull{ s -> s.toDoubleOrNull()?.times(unitFreq.scale) }
                    ?.filter{ it > 0.0 }?.sorted()?.reversed()
                    ?: mutableListOf()
            sp.setEnergyInfo(EnergyInfo(formationEnergy, freq, reference, map), model.pi)
            if(sp is SolidSite) sp.thermoCorrection = HarmonicSite(sp)
        }
    }

    fun getSurfaces(inputFile: File?): List<String> {
        if(inputFile == null) return listOf()
        val info = parseContent(inputFile).first
        val surfaces = mutableSetOf<String>()
        info.forEach{ m -> m["surface_name"]?.let{ surfaces.add(it) } }
        surfaces.remove("None")
        return surfaces.toList()
    }
    fun getSpecies(inputFile: File?, useDefault: Boolean): Pair<List<String>, List<String>> {
        if(inputFile == null) return Pair(listOf(), listOf())
        val info = parseContent(inputFile).first
        val ads = mutableSetOf<String>()
        val gases = mutableSetOf<String>()
        info.forEach { map ->
            val speciesName = map["species_name"] ?: return@forEach
            val siteOld = map["site_name"]
            if(siteOld == null){
                val (interpret, _, sStr) = Species.interpret(speciesName, model.defaultSite) ?: return@forEach
                val (config, formula, charge) = interpret
                val defaultName = Species.defaultName(config, formula, charge, sStr)
                if(sStr.size == 1 && sStr.values.first() == 1){
                    val site = sStr.keys.first()
                    if(site == "g") gases.add(defaultName)
                    else if(useDefault && site == model.defaultSite) ads.add(defaultName.substringBefore('_') + "*")
                    else ads.add(defaultName)
                }else{
                    ads.add(defaultName)
                }
            }else if(siteOld != "None"){
                for(sd in model.sites){
                    if(sd is SolidSite && siteOld in sd.siteNames){
                        if(!useDefault) ads.add(speciesName + "_" + sd.name)
                        else ads.add(speciesName + if(sd.name == model.defaultSite) "*" else ("_" + sd.name))
                        break
                    }
                }
            }else if(siteOld == "gas"){
                gases.add(speciesName + "_g")
            }
        }
        return Pair(gases.toList(), ads.toList())
    }
}