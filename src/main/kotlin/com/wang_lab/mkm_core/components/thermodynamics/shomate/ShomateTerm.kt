package com.wang_lab.mkm_core.components.thermodynamics.shomate

import com.wang_lab.mkm_core.constants.EnergyUnits
import kotlin.math.ln
import kotlin.math.pow

class ShomateTerm(
    val T: Pair<Double, Double>,
    val A: Double,
    val B: Double,
    val C: Double,
    val D: Double,
    val E: Double,
    val F: Double,
    val G: Double,
    val H: Double
){
    constructor(t: List<Double>, p: List<Double>): this(
        Pair(t[0], t[1]),
        p[0],
        p[1],
        p[2],
        p[3],
        p[4],
        p[5],
        p[6],
        p[7],
    )
    fun capacity(T: Double): Double{
        val t = T / 1000
        return (A + B * t + C * t.pow(2) + D * t.pow(3) + E / t.pow(2)) * EnergyUnits.EUkJmol.scale
    }
    fun enthalpy(T: Double): Double{
        val t = T / 1000
        return (A * t + B * t.pow(2) / 2 + C * t.pow(3) / 3 + D * t.pow(4) / 4 - E / t + F - H) * EnergyUnits.EUkJmol.scale
    }
    fun entropy(T: Double): Double{
        val t = T / 1000
        return (A * ln(t) + B * t + C * t.pow(2) / 2 + D * t.pow(3) / 3 - E / 2 / t.pow(2) + G) * EnergyUnits.EUkJmol.scale / 1000
    }
}