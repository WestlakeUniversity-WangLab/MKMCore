package com.wang_lab.mkm_core.components.guesser

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.div
import com.wang_lab.mkm_core.exception.MKMRunTimeException
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.point.PointInfo
import java.math.BigDecimal

abstract class Guesser(val model: ReactionModel, par: JsonObject) {
    val zeroCoverage: BDVector
        get() = BDVector(model.adsorbates.size)
    val averageCoverage: BDVector
        get() = BDVector(model.adsorbates.size){ (BigDecimal.ONE.div(BigDecimal(model.adsorbates.size + 1))) }
    interface GuessIterator{
        fun hasNext(): Boolean
        fun next(): Pair<String, BDVector?>
        fun result(result: Boolean)
    }
    protected open fun forEachInitialGuess(point: PointInfo, action: (String, BDVector) -> (Boolean)){
        val o = model.solver.getValue(point)
        if(o != null){
            model.solver.setValue(point, null)
            if(action("original data", o)) return
            model.solver.setValue(point, o)
        }
        if(action("zero coverage", zeroCoverage)) return
        if(action("average coverage", averageCoverage)) return
    }
    /**
     *
     */
    fun tryInitialGuesses(point: PointInfo, action: (BDVector) -> (Unit)){
        forEachInitialGuess(point){ name, guess ->
            try{
                action(guess)
                logger?.info("Succeeded while using $name")
                true
            }catch (e: MKMRunTimeException){
                logger?.info("Failed while using $name: ${e.message}")
                //logger?.finer(e.stackTraceToString())
                false
            }catch (e: Exception){
                logger?.info("Error while using $name: ${e.message}")
                //logger?.info(e.stackTraceToString())
                false
            }
        }
    }
}