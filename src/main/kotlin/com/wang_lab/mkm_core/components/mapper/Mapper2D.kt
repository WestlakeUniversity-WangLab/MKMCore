package com.wang_lab.mkm_core.components.mapper

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.*
import com.wang_lab.mkm_core.components.mapper.task.MultiThreadTask
import com.wang_lab.mkm_core.components.mapper.task.PooledThread
import com.wang_lab.mkm_core.point.GridPoint
import com.wang_lab.mkm_core.point.PointInfo
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min
import kotlin.math.sqrt

@Suppress("MemberVisibilityCanBePrivate", "unused")
class Mapper2D (model: ReactionModel, par: JsonObject): StandardMapper(2, model, par){
    private fun validGrid(g: GridPoint) = (g[0] in 0 until size[0]) && (g[1] in 0 until size[1])
    private fun validGridValue(g: GridPoint) = model.solver.validPointValue(model.getPoint(g))

    private val searchDirections = listOf(GridPoint(-1, 0), GridPoint(1, 0), GridPoint(0, -1), GridPoint(0, 1))
    private fun adjacentPoints(g: GridPoint, action: (GridPoint, Int) -> Unit){
        searchDirections.forEachIndexed { i, sd ->
            val og = g + sd
            if(validGrid(og)) action(og, i)
        }
    }
}