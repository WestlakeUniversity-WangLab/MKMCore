package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.exception.MKMSetupException
import com.wang_lab.mkm_core.misc.Item
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.loadModule
import com.wang_lab.mkm_core.species.Gas
import com.wang_lab.mkm_core.components.thermodynamics.shomate.ShomateTerm

class ShomateGas(gas: Gas, pi: PyInteraction, waitForMolecule: Boolean = false): GasThermo(gas)  {
    private val shomate = getShomateGas(gas.formula)
    private var hRef = Double.NaN
    init{
        for(s in shomate){
            if(s.T.first <= tRef && tRef <= s.T.second){
                hRef = s.enthalpy(tRef)
                break
            }
        }
        if(hRef.isNaN()) throw MKMSetupException("shomate_range", "Shomate data of ${gas.name} does not contain 298.15K!")
    }

    override fun correctEnthalpy(thermo: Thermo): Double {
        val t = thermo.t!!
        for(s in shomate){
            if(s.T.first <= t && t <= s.T.second){
                val cp = s.capacity(tRef)
                val dH = cp * tRef / 1000 + s.enthalpy(t) - hRef
                return gas.energyInfo.zpe + dH
            }
        }
        return Double.NaN
    }

    override fun getEntropy(thermo: Thermo): Double {
        val t = thermo.t!!
        for(s in shomate)
            if(s.T.first <= t && t <= s.T.second)
                return s.entropy(t)
        return Double.NaN
    }

    companion object{
        private const val tRef = 298.15
        private val shomateGasMap = mutableMapOf<String, List<ShomateTerm>>()
        val shomateGasSources = mutableListOf("ShomateGas.json")
        private fun initializeShomateGas()
                = loadModule(Item.same("shomate gas parameters"), shomateGasSources, shomateGasMap, { loadShomateGas(it) })
        fun getShomateGas(gas: String): List<ShomateTerm> {
            if(gas !in shomateGasMap) initializeShomateGas()
            return shomateGasMap[gas] ?: throw MKMSetupException("shomate_not_found", "Shomate gas parameters of $gas is not found!")
        }
        private fun loadShomateGas(content: String): Int{
            val jo = Json.parseToJsonElement(content).jsonObject
            jo.forEach { gas, terms ->
                shomateGasMap[gas] = terms.jsonArray.map{ j ->
                    ShomateTerm(
                        j.jsonObject["T"]!!.jsonArray.map{ d -> d.jsonPrimitive.double},
                        j.jsonObject["parameters"]!!.jsonArray.map{ d -> d.jsonPrimitive.double},
                    )
                }
            }
            return jo.size
        }
    }
}