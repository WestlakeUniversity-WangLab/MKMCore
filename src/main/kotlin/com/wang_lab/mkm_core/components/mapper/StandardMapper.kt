package com.wang_lab.mkm_core.components.mapper

import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.*
import com.wang_lab.mkm_core.algebra.number_math.iProductOf
import com.wang_lab.mkm_core.point.GridPoint

abstract class StandardMapper(dim: Int, model: ReactionModel, par: JsonObject): Mapper(dim, model, par) {
    final override val descriptors: List<Descriptor>
    final override val grids: List<GridPoint>
    val capacity: Int
    init{
        if("descriptors" in par){
            descriptors = par["descriptors"]!!.jsonObject.map { (name, values) ->
                val scales = mutableSetOf<Double>()
                var reverse: Boolean? = null
                var first: Double? = null
                values.jsonArray.forEach {
                    try{
                        when (it) {
                            is JsonPrimitive -> {
                                val a = it.double.roundTo(12)
                                scales.add(a)
                                if(reverse == null){
                                    if(first == null) first = a
                                    else if(first != a) reverse = first!! > a
                                }
                            }
                            is JsonArray -> {
                                val start = it.jsonArray[0].jsonPrimitive.double
                                val end = it.jsonArray[1].jsonPrimitive.double
                                val n = it.jsonArray[2].jsonPrimitive.int
                                assert(n > 1)
                                val step = (end - start) / (n - 1)
                                (0 until n).forEach{ m -> scales.add((start + step * m).roundTo(12)) }
                                if(reverse == null) reverse = start > end
                            }
                            else -> {
                                throw Exception()
                            }
                        }
                    }catch (_: Exception){
                        logger?.warning("Skipped item $it in descriptor $name, which is not valid.")
                    }
                }
                if(scales.size < 2) throw Exception("The number of distinct values of descriptor $name is less than 2!")
                Descriptor(name, scales.sorted().let { if(reverse == true) it.reversed() else it }.toDoubleArray())
            }
        }else{
            throw Exception("No descriptors found while initializing Mapper!")
        }
        capacity = descriptors.iProductOf { it.scales.size }
        val g = ArrayList<GridPoint>(capacity)
        var grid = IntArray(dim){ 0 }
        grids = ArrayList<GridPoint>(g)
        while (true){
            grids.add(GridPoint(grid.size){ grid[it] })
            grid = IntArray(dim){ grid[it] }
            grid[dim-1] ++
            if(grid[dim-1] >= size[dim-1]){
                var pos = dim - 1
                while(pos > 0){
                    grid[pos] = 0
                    grid[pos-1] ++
                    if(grid[pos-1] < size[pos-1]) break
                    pos --
                }
                if(pos <= 0) break
            }
        }
    }
}