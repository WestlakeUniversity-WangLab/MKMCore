package com.wang_lab.mkm_core.misc

import kotlin.collections.HashMap

class Thermo{
    private val extraValues = HashMap<String, Double>()
    var t: Double? = null
    var p: Double? = null
    var u: Double? = null
    operator fun set(d: String, v: Double){
        extraValues[d] = v
    }
    operator fun get(d: String) = extraValues[d]
    override fun toString(): String{
        val sb = mutableListOf<String>()
        if(t != null) sb.add("T = ${"%.3f".format(t)} K")
        if(p != null) sb.add("p = ${"%.3f".format(p)} bar")
        if(u != null) sb.add("u = ${"%.3f".format(u)} V")
        extraValues.forEach { (k, v) -> sb.add("$k = ${"%.2f".format(v)}") }
        return sb.joinToString("\n")
    }
    val values: Map<String, Double>
        get(){
            val map = mutableMapOf<String, Double>()
            if(t != null) map["T"] = t!!
            if(p != null) map["p"] = p!!
            if(u != null) map["u"] = u!!
            extraValues.forEach{ (k, v) -> map[k] = v }
            return map
        }
    fun copy(): Thermo{
        val new = Thermo()
        new.t = t
        new.p = p
        new.u = u
        extraValues.forEach{ (k, v) -> new[k] = v }
        return new
    }
}
