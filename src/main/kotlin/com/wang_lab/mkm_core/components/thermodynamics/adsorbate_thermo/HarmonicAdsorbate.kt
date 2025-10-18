package com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo

import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.constants.kB_e
import com.wang_lab.mkm_core.misc.ThermoDescriptor
import com.wang_lab.mkm_core.species.SurfaceSpecies
import kotlin.math.exp
import kotlin.math.ln

class HarmonicAdsorbate(solid: SurfaceSpecies): AdsorbateThermo(solid) {
    override fun correctEnthalpy(thermo: Thermo): Double {
        val t = thermo.t!!
        val kT = kB_e * t
        val freq = solid.meanFrequencies!!
        return solid.meanZPE!! + freq.sumOf { e -> e / (exp(e / kT) - 1) }
    }

    override fun getEntropy(thermo: Thermo): Double {
        val t = thermo.t!!
        val kT = kB_e * t
        val freq = solid.meanFrequencies!!
        return freq.sumOf{ e ->
            val x = e / kT
            x / (exp(x) - 1) - ln(1 - exp(-x))
        } * kB_e
    }
}