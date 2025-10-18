package com.wang_lab.mkm_core.components

import com.wang_lab.mkm_core.components.mapper.*
import com.wang_lab.mkm_core.components.parsers.*
import com.wang_lab.mkm_core.components.guesser.*
import com.wang_lab.mkm_core.components.modifier.*
import com.wang_lab.mkm_core.components.scaler.*
import com.wang_lab.mkm_core.components.solver.*
import com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo.*
import com.wang_lab.mkm_core.components.thermodynamics.gas_thermo.*
import com.wang_lab.mkm_core.components.thermodynamics.site_thermo.*
import com.wang_lab.mkm_core.components.writer.*
import com.wang_lab.mkm_core.point.ExtraInfo

object BuiltInComponentsLoader: ComponentsLoader() {
    override fun registerModule() {
        //class
        registerClass(Mapper::class.java)
        registerClass(Parser::class.java)
        registerClass(Guesser::class.java)
        registerClass(Solver::class.java)
        registerClass(Scaler::class.java)
        registerClass(GasThermo::class.java)
        registerClass(AdsorbateThermo::class.java)
        registerClass(SiteThermo::class.java)
        registerClass(Modifier::class.java)
        registerClass(Writer::class.java)
        //Mapper
        registerComponent(Mapper1D::class.java)
        registerComponent(Mapper2D::class.java)
        //Parser
        registerComponent(TableParser::class.java)
        registerComponent(NullParser::class.java)
        //InitialGuesser
        registerComponent(BoltzmannGuesser::class.java)
        registerComponent(ODEGuesser::class.java)
        registerComponent(NullGuesser::class.java)
        //Solver
        registerComponent(SteadyStateSolver::class.java)
        registerComponent(NullSolver::class.java)
        //Scaler
        registerComponent(Scaler::class.java)
        //GasThermo
        registerComponent(IdealGas::class.java)
        registerComponent(ShomateGas::class.java)
        registerComponent(FixedEnthalpyEntropyGas::class.java)
        registerComponent(FixedEntropyGas::class.java)
        registerComponent(FrozenFixedEntropyGas::class.java)
        registerComponent(ZeroPointGas::class.java)
        registerComponent(FrozenGas::class.java)
        //AdsorbateThermo
        registerComponent(HarmonicAdsorbate::class.java)
        registerComponent(FixedEnthalpyEntropyAdsorbate::class.java)
        registerComponent(FrozenAdsorbate::class.java)
        //SiteThermo
        registerComponent(SiteThermo::class.java)
        registerComponent(HarmonicSite::class.java)

        registerComponent(CSV1DWriter::class.java)

        registerClass(ExtraInfo::class.java)
    }
}