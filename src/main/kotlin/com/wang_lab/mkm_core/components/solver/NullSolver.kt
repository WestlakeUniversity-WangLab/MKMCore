package com.wang_lab.mkm_core.components.solver

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.point.PointInfo

class NullSolver(model: ReactionModel, par: JsonObject): Solver(model, par) {
    override fun getValue(p: PointInfo) = null
    override fun setValue(p: PointInfo, value: BDVector?){}
    override fun validPointValue(p: PointInfo) = false

    override fun solveWithInitialValue(p: PointInfo, initialValue: BDVector, source: PointInfo?) {
        logger?.warning("Null solver do not solve anything!")
    }

    override fun plotTypes(): List<String> = listOf()

    override fun solveWithInitialGuess(p: PointInfo) {
        logger?.warning("Null solver do not solve anything!")
    }
}