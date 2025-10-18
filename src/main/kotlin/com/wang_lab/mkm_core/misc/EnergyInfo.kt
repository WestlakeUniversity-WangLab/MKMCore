package com.wang_lab.mkm_core.misc
import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import kotlin.properties.Delegates

class EnergyInfo(
    val formationEnergy: AlgebraExpr,
    frequencies: List<Double>,
    val reference: String?,
    val attributes: Map<String, String>
){
    var zpe = frequencies.sum() / 2
    var frequencies: List<Double> by Delegates.observable(frequencies) { _, _, newValue ->
        zpe = newValue.sum() / 2
    }
}
