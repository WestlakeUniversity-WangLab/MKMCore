package com.wang_lab.mkm_core.constants

enum class EnergyUnits(val scale: Double, val names: List<String>) {
    EUeV(1.0, listOf("eV")),
    EUMeV(1.0e6, listOf("MeV")),
    EUmeV(1.0e-3, listOf("meV")),
    EUcm1(1.239842e-4, listOf("cm-1", "cm^-1")),
    EUkJmol(0.01036427, listOf("kJ/mol", "kJ / mol", "kJ mol^-1"));
    companion object{
        fun read(s: String) = values().firstOrNull{ eu -> eu.names.contains(s) }
    }
}