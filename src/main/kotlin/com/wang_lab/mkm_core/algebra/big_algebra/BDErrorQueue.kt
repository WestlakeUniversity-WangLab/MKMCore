package com.wang_lab.mkm_core.algebra.big_algebra

import com.wang_lab.mkm_core.algebra.big_decimal_math.times
import com.wang_lab.mkm_core.minInfo
import java.math.BigDecimal

/**
 * A queue to list the recent several errors. It is a soft class for judging convergence.
 */
class BDErrorQueue(val size: Int){
    private val queue = MutableList<BigDecimal>(size){ BigDecimal.ZERO }
    private var filled = 0
    fun add(b: BigDecimal){
        if(filled < size){
            queue[filled] = b
            filled += 1
            return
        }
        for(i in 0 .. size - 2){
            queue[i] = queue[i + 1]
        }
        queue[size - 1] = b
    }
    fun checkThreshold(threshold: BigDecimal, newError: BigDecimal): Boolean{
        if(filled < size) return true
        val (id, bd) = queue.minInfo()
        if(id == 0) return newError < bd * threshold
        return true
    }
}