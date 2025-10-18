package com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo

import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.species.SurfaceSpecies

class FrozenAdsorbate(solid: SurfaceSpecies): AdsorbateThermo(solid) {
    override fun correctEnthalpy(thermo: Thermo) = 0.0
    override fun getEntropy(thermo: Thermo) = 0.0
}