package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.ExprVar
import com.wang_lab.mkm_core.algebra.join
import com.wang_lab.mkm_core.exception.MKMSetupException
import com.wang_lab.mkm_core.misc.EnergyInfo
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.reaction.Reaction
import com.wang_lab.mkm_core.components.scaler.Scaler
import com.wang_lab.mkm_core.components.thermodynamics.ThermoCorrection
import java.util.regex.Pattern
import kotlin.math.absoluteValue

/**
 * The definition of a species, containing all its information.
 *
 * To create a definition, see {@link SpeciesDefinition#load}.
 *
 * One species should only have one instance of SpeciesDefinition in one model.
 * Please use its reference from the species definitions map.
 */
abstract class Species(
    val name: String, val charge: Int = 0
){
    val formationEnergies = mutableListOf<EnergyInfo>()
    fun expression(count: Int = 1) = (if(count == 1) "" else count.toString()) + name
    fun shortExpression(count: Int = 1) = (if(count == 1) "" else count.toString()) + shortName
    abstract val identifier: String
    open val exprVar: AlgebraExpr by lazy { ExprVar(identifier) }
    override fun toString(): String = name
    open val shortName: String
        get() = name
    abstract val thermoCorrection: ThermoCorrection
    var notVirtual: Boolean = false
    var includeEnergy: Boolean = false
    open fun setEnergyInfo(fe: EnergyInfo, pi: PyInteraction){
        formationEnergies.add(fe)
    }
    fun getFreeEnergy(p: PointInfo, scaler: Scaler)
            = scaler.getElectronicEnergy(this, p) + thermoCorrection.correctFreeEnergy(p.thermo)
    fun getEnthalpy(p: PointInfo, scaler: Scaler)
            = scaler.getElectronicEnergy(this, p) + thermoCorrection.correctEnthalpy(p.thermo)
    fun getEntropy(thermo: Thermo) = thermoCorrection.getEntropy(thermo)
    companion object{
        val speciesFormat: Pattern = Pattern.compile("(?<config>\\w+~)?(?<formula>[|-]*((([A-Z][a-z]*)|ele|pe)\\d*[|-]*)+)(?<charge>:\\d*[+-])?(\\*|_(?<site>[a-z\\d|]+))")
        val siteFormat: Pattern = Pattern.compile("\\*?_?(?<site>[a-z]+)")
        private val nSite: Pattern = Pattern.compile("(?<ns>\\d*)(?<site>[a-z]+)")
        private val speciesComposition = Pattern.compile("[|-]*(?<element>(([A-Z][a-z]*)|ele|pe))(?<mount>\\d*)[|-]*(?<rest>.*)")
        private val eChemTSFormat = Pattern.compile("\\^(?<energy>[\\d.]+)eV(\\*|_(?<ns>\\d*)(?<site>[a-z]+))")

        /**
         * A function to separate a species name to some basic pieces. Return null if it is a site.
         * Else it returns config, formula, charge, site string and sites.
         */
        fun interpret(name: String, defaultSite: String?): Triple<Triple<String, String, Int>, String, Map<String, Int>>?{
            val matcher = speciesFormat.matcher(name)
            return if(matcher.matches()){
                val config = matcher.group("config") ?: ""
                val formula = matcher.group("formula")
                var siteStr = matcher.group("site")
                var charge = 0
                var cStr = matcher.group("charge") ?: ""
                if(cStr.startsWith(':')) cStr = cStr.substring(1)
                if(cStr.isNotEmpty()){
                    charge = if(cStr.length == 1) 1
                    else cStr.substring(0, cStr.length - 1).toInt()
                    if(cStr.endsWith('-')) charge = -charge
                }
                if(siteStr == null || siteStr.isEmpty()) siteStr = defaultSite ?: throw Exception(Reaction.dnf)
                val siteMap = mutableMapOf<String, Int>()
                siteStr.split('|').forEach{
                    val matcherSite = nSite.matcher(it)
                    if(!matcherSite.matches()) throw Exception("Site name $it in $siteStr is invalid!")
                    val ns = matcherSite.group("ns")
                    val n = if(ns == null || ns.isEmpty()) 1 else ns.toInt()
                    val s = matcherSite.group("site")
                    siteMap[s] = (siteMap[s] ?: 0) + n
                }
                Triple(Triple(config, formula, charge), siteStr, siteMap)
            }else{
                null
            }
        }
        fun defaultName(config: String, formula: String, charge: Int, sites: Map<String, Int>): String{
            val cStr = if(charge == 0) ""
            else ':' + (if(charge == 1 || charge == -1) "" else charge.absoluteValue.toString()) + if(charge > 0) '+' else '-'
            var name = "$config$formula${cStr}_"
            val siteNames = sites.keys.sorted()
            name += "|".join(siteNames.map{ if(sites[it]!! == 1) it else "${sites[it]!!}$it" })
            return name
        }
        /**
         * Create a SpeciesDefinition from its name. For example, "CO2_g".
         *
         * The format of a species name is {formula}_{site} or {site}.
         *
         * The site consists entirely of lowercase letters.
         *
         * Its formula consists of elements, numbers and dash.
         *
         * "\*" refers to an empty site or the default site. For example, "\*_s" is
         * equivalent to "s". "H\*" is equivalent to "H_s". The default site is initially
         * "s", and can be modified by "default_site" field in mkm file.
         *
         * There are Gas, Adsorbate, TransitionState, Liquid, Site, Aqua as species types.
         * Site "g" refer to Gas, "l" refer to Liquid and "aq" refer to Aqua.
         *
         * Site "s" with no dash in formula refer to Adsorbate, with dashes in formula
         * refer to TransitionState.
         */
        fun load(name: String, defaultSite: String?, pi: PyInteraction, pars: JsonObject? = null, defaultThermoMode: Map<String, String>? = null, allSpecies: MutableMap<String, Species>): Species{
            if(name == "ele") return Electron
            val interpreted = interpret(name, defaultSite)
            return if(interpreted != null){
                val (info, siteStr, sStr) = interpreted
                val (config, formula, charge) = info
                val sites = sStr.mapKeys { (s, _) -> getSite(s, allSpecies) }
                val composition = mutableMapOf<String, Int>()
                var c = formula
                while(c.isNotEmpty()){
                    val m = speciesComposition.matcher(c)
                    if(! m.matches()) throw Exception("Formula $formula is invalid!")
                    val ele = m.group("element")
                    val gMount = m.group("mount")
                    val mount = if(gMount.isEmpty()) 1 else gMount.toInt()
                    composition[ele] = (composition[ele] ?: 0) + mount
                    c = m.group("rest")
                }
                val cf = if(config.isNotEmpty()) config + formula else formula
                val defaultName = defaultName(config, formula, charge, sStr)
                when(siteStr){
                    "g" -> Gas(defaultName, cf, composition, pars, pi, true, defaultThermoMode?.get("gas"), config, charge)
                    "l" -> Liquid(defaultName, cf, composition, pars, defaultThermoMode?.get("liquid"), config, charge)
                    "aq" -> Aqua(defaultName, cf, composition, pars, defaultThermoMode?.get("aqua"), config, charge)
                    else -> if('-' in cf) Transition(defaultName, cf, composition, sites, pars, defaultThermoMode?.get("adsorbate"), config, charge)
                    else Adsorbate(defaultName, cf, composition, sites, pars, defaultThermoMode?.get("adsorbate"), config, charge)
                }
            }else{
                val sMatcher = siteFormat.matcher(name)
                if(sMatcher.matches()){
                    if(allSpecies.containsKey(name)){
                        val sd = allSpecies[name]!! as SolidSite
                        if(pars != null) sd.loadPyMap(pars)
                        return sd
                    }else{
                        return SolidSite(name, pars)
                    }
                }else{
                    throw Exception("Species name $name is invalid!")
                }
            }
        }
        fun loadEChem(name: String, defaultSite: String?, index: Int, pars: JsonObject? = null, defaultThermoMode: Map<String, String>? = null, allSpecies: MutableMap<String, Species>): EChemTS{
            val matcher = eChemTSFormat.matcher(name)
            matcher.find()
            var siteStr = matcher.group("site") ?: throw Exception("No site found in $name!")
            if(siteStr.isEmpty()) siteStr = defaultSite ?: throw Exception(Reaction.dnf)
            val sites = getSites(siteStr, allSpecies)
            val energy = matcher.group("energy") ?: throw Exception("No energy found in $name!")
            return EChemTS(energy, sites, mapOf(), index, pars, defaultThermoMode?.get("adsorbate"))
        }
        fun getSites(siteStr: String,  allSpecies: MutableMap<String, Species>): MutableMap<SolidSite, Int> {
            val sites = mutableMapOf<SolidSite, Int>()
            siteStr.split('|').forEach{
                val matcherSite = nSite.matcher(it)
                if(!matcherSite.matches()) throw Exception("Site name $it in $siteStr is invalid!")
                val ns = matcherSite.group("ns")
                val n = if(ns == null || ns.isEmpty()) 1 else ns.toInt()
                val s = getSite(matcherSite.group("site"), allSpecies)
                sites[s] = n
            }
            return sites
        }

        fun getSite(name: String, allSpecies: MutableMap<String, Species>) =
            if(allSpecies.containsKey(name)){
                allSpecies[name]!! as SolidSite
            }else{
                val nsd = SolidSite(name)
                allSpecies[name] = nsd
                nsd
            }
    }
}

