package com.wang_lab.mkm_core.components.thermodynamics

import com.wang_lab.mkm_core.misc.Thermo

abstract class ThermoCorrection {
    abstract fun correctEnthalpy(thermo: Thermo): Double
    abstract fun getEntropy(thermo: Thermo): Double
    open fun correctFreeEnergy(thermo: Thermo): Double
        = correctEnthalpy(thermo) - thermo.t!! * getEntropy(thermo)
    open fun flush(){}
}