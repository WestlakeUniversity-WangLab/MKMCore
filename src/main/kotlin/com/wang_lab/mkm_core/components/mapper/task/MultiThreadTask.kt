package com.wang_lab.mkm_core.components.mapper.task

@Suppress("DEPRECATION")
/**
 * A class that used for multi-thread task that manage a set of threads.
 *
 * @param threadTasks A list of MultiThreadTask. It will add itself to the list when threads start,
 * and remove itself when threads stop or end.
 * @param threads A list of threads that it manages.
 * @param afterAction What to do when all threads start.
 * This action run in sync with the threads.
 * @param finalAction What to do when all threads end.
 * This action will be skipped if the threads are terminated by stop().
 */
class MultiThreadTask(
    private val threadTasks: MutableList<MultiThreadTask>,
    val threads: List<Thread>,
    val afterAction: () -> Unit = {},
    val finalAction: () -> Unit
) {
    private var stopFlag = false
    /** Start all threads. */
    fun start(){
        threadTasks.add(this)
        threads.forEach { it.start() }
        afterAction()
        threads.forEach { it.join() }
        if(!stopFlag) finalAction()
        threadTasks.remove(this)
    }
    /** Suspend all threads. */
    fun suspend(){
        threads.forEach { it.suspend() }
    }
    /** Resume all threads. */
    fun resume(){
        threads.forEach { it.resume() }
    }
    /** Force to stop all threads. Doing this will skip final action. */
    fun stop(){
        stopFlag = true
        threadTasks.remove(this)
        threads.forEach { it.stop() }
    }
}