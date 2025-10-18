package com.wang_lab.mkm_core.molecule

import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.misc.Item
import com.wang_lab.mkm_core.doubleOrNaN
import com.wang_lab.mkm_core.loadModule

data class Atom (
    val index: Int,
    val symbol: String,
    val name: String,
    val mass: Double,
    val commonMass: Double,
    val covalentRadii: Double,
    val vdrRadii: Double,
    val magneticMoments: Double){
    override fun toString() = symbol
    companion object{
        private val atomsMap = mutableMapOf<String, Atom>()
        val atomSources = mutableListOf("Atoms.json")
        private fun initializeAtoms() = loadModule(Item.withS("atom"), atomSources, atomsMap, { loadAtoms(it) })
        fun getAtom(symbol: String): Atom{
            if(symbol !in atomsMap) initializeAtoms()
            return atomsMap[symbol] ?: throw Exception("Atom $symbol is not found!")
        }
        private fun loadAtoms(content: String): Int{
            val ja = Json.parseToJsonElement(content).jsonArray
            ja.forEach {
                val dict = it.jsonObject
                val a = Atom(
                    index = dict["index"]!!.jsonPrimitive.int,
                    symbol = dict["symbol"]!!.jsonPrimitive.content,
                    name = dict["name"]!!.jsonPrimitive.content,
                    mass = dict["mass"]!!.jsonPrimitive.doubleOrNaN,
                    commonMass = dict["common_mass"]!!.jsonPrimitive.doubleOrNaN,
                    covalentRadii = dict["covalent_radii"]!!.jsonPrimitive.doubleOrNaN,
                    vdrRadii = dict["vdw_radii"]!!.jsonPrimitive.doubleOrNaN,
                    magneticMoments = dict["magnetic_moments"]!!.jsonPrimitive.doubleOrNaN,
                )
                atomsMap[a.symbol] = a
            }
            return ja.size
        }
    }
}
