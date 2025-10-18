package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.algebra.mmap
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.misc.EnergyInfo
import com.wang_lab.mkm_core.components.thermodynamics.site_thermo.SiteThermo
import java.math.BigDecimal

class SolidSite(name: String, pars: JsonObject? = null) : Species(name) {
    var meanFrequencies: List<Double>? = null
    var meanZPE: Double? = null
    var siteNames: MutableList<String>
    var total: Double = 1.0
    var density: Double? = null
    override var thermoCorrection = thermo
    init{
        siteNames = mutableListOf(name)
        if(pars != null) loadPyMap(pars)
    }
    fun calculateMeanFrequencies(){
        val sum = mutableListOf<Double>()
        formationEnergies.forEach{ e ->
            e.frequencies.forEachIndexed{ i, f ->
                if(sum.size == i) sum.add(f) else sum[i] += f
            }
        }
        meanFrequencies = sum.map{ s -> s / formationEnergies.size }
        meanZPE = meanFrequencies!!.sum() / 2
    }

    override fun setEnergyInfo(fe: EnergyInfo, pi: PyInteraction) {
        if(fe.frequencies.isNotEmpty())
            pi.warning.transfer("The energy info of ${this.name} on $name has ${fe.frequencies.size} frequencies, but expected to be 0. Please check!")
        formationEnergies.add(fe)
    }
    fun loadPyMap(pars: JsonObject){
        if(pars.containsKey("site_names")){
            siteNames = pars["site_names"]!!.jsonArray.mmap{ s -> s.jsonPrimitive.content }
        }
        if(pars.containsKey("total")){
            total = pars["total"] !!.jsonPrimitive.double
        }
        if(pars.containsKey("density")){
            density = pars["density"] !!.jsonPrimitive.double
        }
    }
    override val identifier = "θ[$name]"
    companion object{
        val thermo = SiteThermo()
    }
}