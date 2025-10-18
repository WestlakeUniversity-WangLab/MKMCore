package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import com.wang_lab.mkm_core.*
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.constants.amu
import com.wang_lab.mkm_core.constants.h
import com.wang_lab.mkm_core.constants.kB
import com.wang_lab.mkm_core.constants.kB_e
import com.wang_lab.mkm_core.molecule.Molecule
import com.wang_lab.mkm_core.molecule.MoleculeGeometry.*
import com.wang_lab.mkm_core.misc.EnergyInfo
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.molecule.Molecule.Companion.getMolecule
import com.wang_lab.mkm_core.species.Gas
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class IdealGas(gas: Gas, pi: PyInteraction, waitForMolecule: Boolean = false): GasThermo(gas) {
    val molecule: Molecule
    val vibNum: Int
    init{
        molecule = getMolecule(gas.formula)
            ?: if(waitForMolecule){
                //val infoBuffer = info.info.value
                //info.info.value = "info_ask_for_molecule%${gas.formula}"
                //info.receiver.value = null
                //while(info.receiver.isNull){
                //    Thread.sleep(10)
                //}
                //info.info.value = infoBuffer
                //loadMoleculeFormFile("")//info.receiver.flush()!!)
                throw Exception("Undefined molecule ${gas.name}")
            }else{
                throw Exception("Structure of ${gas.name}(${gas.formula}) not found.")
            }
        vibNum = when(molecule.geometry){
            Nonlinear -> molecule.coordinates.size * 3 - 6
            Linear -> molecule.coordinates.size * 3 - 5
            Point -> 0
        }
    }
    fun checkFrequencySize(name: String, fe: EnergyInfo, pi: PyInteraction){
        if(fe.frequencies.size > vibNum){
            if(fe.frequencies.isNotEmpty())
                pi.warning.transfer("The energy info of ${this.gas.name} on $name has ${fe.frequencies.size} frequencies, but expected to be $vibNum. The redundant frequencies have been cutoff.")
            val f = fe.frequencies
            f.sortedBy { -it }
            fe.frequencies = fe.frequencies.subList(0, vibNum)
        }
        if(fe.frequencies.size < vibNum)
            pi.warning.transfer("The energy info of ${this.gas.name} on $name has ${fe.frequencies.size} frequencies, but expected to be $vibNum. Please check!")

        fe.zpe = fe.frequencies.sum() / 2
    }

    override fun correctEnthalpy(thermo: Thermo): Double {
        val t = thermo.t!!
        //Calculate enthalpy
        //Translational heat capacity
        val cvT = 1.5 * kB_e
        //Rotational heat capacity
        val cvR = when(molecule.geometry){
            Nonlinear -> 1.5 * kB_e
            Linear -> kB_e
            Point -> 0.0
        }
        //Vibrational energy contribution
        val kt = kB_e * t
        val hV = gas.energyInfo.frequencies.sumOf { e -> e / (exp(e / kt) - 1) }
        return gas.energyInfo.zpe + (cvR + cvT + kB_e) * t + hV
    }

    override fun getEntropy(thermo: Thermo): Double {
        val t = thermo.t!!//Calculate entropy
        val kt = kB_e * t
        val mass = molecule.mass * amu
        //Translational entropy
        var sT = (2.0 * Math.PI * mass * kB * t / h.pow(2)).pow(1.5)
        sT *= kB * t / referencePressure
        sT = kB_e * (ln(sT) + 2.5)
        //Rotational entropy
        val sR =  when(molecule.geometry){
            Nonlinear -> {
                val inertia = (molecule.momentsOfInertia * (amu / 1e20))
                var s = sqrt(Math.PI * inertia.product()) / molecule.symmetry
                s *= (8 * Math.PI.pow(2) * kB * t / h.pow(2)).pow(1.5)
                kB_e * (ln(s) + 1.5)
            }
            Linear -> {
                val inertia = (molecule.momentsOfInertia * (amu / 1e20))
                val s = 8 * Math.PI.pow(2) * inertia.max() * kB * t / molecule.symmetry / h.pow(2)
                kB_e * (ln(s) + 1)
            }
            Point -> 0.0
        }
        //Electronic entropy
        val sE = kB_e * ln(2 * molecule.spin + 1)
        //Vibrational entropy
        val sV = gas.energyInfo.frequencies.sumOf { e ->
            val x = e / kt
            x / (exp(x) - 1) - ln(1 - exp(-x))
        } * kB_e
        //Pressure correction to translational entropy
        val sP = 0.0 //-kB_e * ln(thermo.pressure)
        return sT + sR + sE + sV + sP
    }

    companion object{
        const val referencePressure = 1e5
    }
}