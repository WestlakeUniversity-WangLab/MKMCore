package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.ExprConst
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eZERO
import com.wang_lab.mkm_core.algebra.expr.ExprVar
import com.wang_lab.mkm_core.algebra.expr.parseExpression
import com.wang_lab.mkm_core.misc.EnergyInfo
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.components.thermodynamics.gas_thermo.*
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.switchJsonElement

class Gas(name: String, formula: String, composition: Map<String, Int>, pars: JsonObject? = null, pi: PyInteraction,
          waitForMolecule: Boolean = false, defaultThermoMode: String? = null, config: String = "", charge: Int = 0):
    MoleculeSpecies(name, formula, composition, pars, config, charge){
    val energyInfo: EnergyInfo
        get() = formationEnergies.firstOrNull() ?: throw Exception("$name has no formation energy!")
    var thermo: GasThermo? = null
    override val concentration: AlgebraExpr =
        if(pars == null) {
            eZERO
        }else if("concentration" in pars){
            switchJsonElement(
                pars["concentration"],
                "concentration of $name",
                d = { ExprConst(it) * ExprVar("p") },
                s = { parseExpression(it) }
            )
        }else if("pressure" in pars){
            switchJsonElement(
                pars["pressure"],
                "pressure of $name",
                d = { ExprConst(it) },
                s = { parseExpression(it) }
            )
        }else{
            null
        } ?: eZERO
    init{
        if(pars != null && pars.containsKey("thermo_mode")) setThermo(pars["thermo_mode"]!!.jsonPrimitive.content, pi, waitForMolecule, pars)
        else if(defaultThermoMode != null) setThermo(defaultThermoMode, pi, waitForMolecule, pars)
        if(name in listOf("pe_g", "ele_g", "H_g", "OH_g"))
            formationEnergies.add(EnergyInfo(eZERO, listOf(), "None", mapOf()))
    }
    override val thermoCorrection = thermo ?: FrozenGas(this)

    private fun setThermo(mode: String, pi: PyInteraction, waitForMolecule: Boolean = false, pars: JsonObject? = null){
        thermo = if(name in listOf("pe_g", "ele_g", "H_g", "OH_g")) EchemThermo(this)
        else when(mode){
            "ideal_gas" -> IdealGas(this, pi, waitForMolecule)
            "shomate_gas" -> ShomateGas(this, pi, waitForMolecule)
            "fixed_enthalpy_entropy_gas" -> FixedEnthalpyEntropyGas(this, pars)
            "fixed_entropy_gas" -> FixedEntropyGas(this, pars)
            "frozen_fixed_entropy_gas" -> FrozenFixedEntropyGas(this, pars)
            "zero_point_gas" -> ZeroPointGas(this)
            "frozen_gas" -> FrozenGas(this)
            else -> {
                logger?.warning("Unknown gas thermo mode $mode.")
                null
            }
        }
    }
    fun isReactant() = concentration is ExprConst && concentration.value == 0

    override fun setEnergyInfo(fe: EnergyInfo, pi: PyInteraction) {
        (thermo as? IdealGas)?.checkFrequencySize(name, fe, pi)
        formationEnergies.add(fe)
    }
    override val identifier = "p[$name]"
}