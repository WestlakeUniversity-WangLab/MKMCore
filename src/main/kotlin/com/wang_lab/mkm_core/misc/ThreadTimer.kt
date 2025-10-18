package com.wang_lab.mkm_core.misc

import java.lang.management.ManagementFactory

class ThreadTimer {
    private var startTime = 0L
    private var duration = 0L
    fun reset(){
        startTime = 0L
        duration = 0L
    }
    fun start(){
        startTime = System.nanoTime()
    }
    fun pause(){
        if(startTime != 0L) duration += System.nanoTime() - startTime
        startTime = 0L
    }
    fun end(): Double{
        if(startTime != 0L) duration += System.nanoTime() - startTime
        return duration.toDouble() / 1e9
    }
}