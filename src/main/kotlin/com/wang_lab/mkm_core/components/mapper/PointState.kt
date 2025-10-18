package com.wang_lab.mkm_core.components.mapper

import com.wang_lab.mkm_core.algebra.number_math.getBit
import com.wang_lab.mkm_core.algebra.number_math.setBit

class PointState(var state: Int = 0, var progress: Int = 0){
    var hasFinished: Boolean
        set(value){ state = state.setBit(0, value) }
        get() = state.getBit(0)
    fun directionTried(i: Int) = if(i < 0) false else state.getBit(2 + i)
    fun setDirectionTried(i: Int, value: Boolean) = state.setBit(2 + i, value)
    fun directionInQueue(i: Int) = if(i < 0) false else state.getBit(6 + i)
    fun setDirectionInQueue(i: Int, value: Boolean) = state.setBit(6 + i, value)
    var isInInitialGuessing: Boolean
        set(value){ state = state.setBit(10, value) }
        get() = state.getBit(10)
}