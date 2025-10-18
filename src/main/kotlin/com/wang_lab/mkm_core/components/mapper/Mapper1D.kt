package com.wang_lab.mkm_core.components.mapper

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.availableProcessors
import com.wang_lab.mkm_core.components.mapper.task.MultiThreadTask
import com.wang_lab.mkm_core.components.mapper.task.PooledThread
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.point.GridPoint
import com.wang_lab.mkm_core.point.PointInfo
import java.util.concurrent.ConcurrentLinkedQueue

class Mapper1D(model: ReactionModel, par: JsonObject): StandardMapper(1, model, par){
    val mapSample: (Int, List<GridPoint>, Any?) -> Unit = { threadNumber, _, par ->
        val sample = (par as? Int) ?: 25
        val t0 = System.currentTimeMillis()
        val t = if(threadNumber > 0) threadNumber else availableProcessors()
        logger?.info("Auto map with ${THREAD.n(t)}.")
        val xList = (0 until sample).map { ((size[0]-1).toDouble() / (sample-1).toDouble() * it.toDouble()).toInt() }
        val heap = ConcurrentLinkedQueue<Triple<PointInfo, PointInfo?, Int>>()// target, source, direction
        val countAll = grids.size
        var countCompleted = 0
        var countFailed = 0
        model.pi.info.transfer("info_map_expand%$countCompleted%$countAll%$countFailed")
        model.pi.progress.transfer(countAll, countCompleted)
        val lock = Any()
        val ptMap = List(size[0]){ PointState() }
        xList.forEach { _x ->  heap.add(Triple(model.getPoint(GridPoint(_x)), null, -1)) }
        //model.gridPoints().forEach{ heap.add(Triple(it, null, -1)) }
        val threads = mutableListOf<PooledThread<Triple<PointInfo, PointInfo?, Int>>>()
        threads.addAll(List(t){
            PooledThread(
                { (target, source, dir) ->
                    val targetGrid = target.gridPoint!!
                    val targetState = ptMap[targetGrid[0]]
                    // Skip if this point has been solved.
                    if(targetState.hasFinished) return@PooledThread
                    //Return if this direction has been tried
                    if(targetState.directionTried(dir)) return@PooledThread
                    //If this point is in progress, only if it is initial guessing, and this thread is not,
                    //to solve it at the same time.
                    if(targetState.progress > 0) {
                        if(targetState.progress != 1 || !targetState.isInInitialGuessing || source == null) {
                            heap.add(Triple(target, source, dir))
                            if(heap.size == 1) Thread.sleep(10)
                            return@PooledThread
                        }
                    }
                    mapperSynchronized(lock) {
                        targetState.progress += 1
                        if(source == null) targetState.isInInitialGuessing = true
                        model.pi.ptStart.transfer(targetGrid, true)
                    }
                    val success: Boolean
                    if(source != null){
                        val (result, bi) = solvePointFrom(target, source, 0)
                        success = result
                        if(success) logger?.info("Succeeded in expanding from $source to $target after ${BISECTION.n(bi)} in thread#$it.")
                        else logger?.info("Failed in expanding from $source to $target after ${BISECTION.n(bi)} in thread#$it.")
                    }else{
                        success = solveWithInitialGuess(target, it)
                        if(success) logger?.info("Succeeded in solving $target with initial guess in thread#$it.")
                        else logger?.info("Failed in solving $target with initial guess in thread#$it.")
                    }
                    if(success){
                        model.pi.result.transfer(target)
                        model.pi.ptStart.transfer(targetGrid, false)
                    }
                    var failed = !success
                    //If directions have not been tried all, this point is still possible to be solved.
                    if(failed) adjacentPoints(targetGrid){ _, i -> if(!targetState.directionTried(i)) failed = false }
                    mapperSynchronized(lock) {
                        if(!success) targetState.setDirectionTried(dir, true)
                        if(source == null) targetState.isInInitialGuessing = false
                        targetState.progress -= 1
                        if(success){
                            //if(!targetState.hasFinished && solveSuccess) countCompleted += 1
                            if(!targetState.hasFinished) countCompleted += 1
                            targetState.hasFinished = true
                            adjacentPoints(targetGrid){ og, i ->
                                val ps = ptMap[og[0]]
                                if(!ps.hasFinished && !ps.directionTried(i) && !ps.directionInQueue(i)){
                                    heap.add(Triple(model.getPoint(og), target, i))
                                    ps.setDirectionInQueue(i, true)
                                }
                            }
                        }
                        if(failed) {
                            countFailed += 1
                            countCompleted += 1
                        }
                        model.pi.info.transfer("info_map_expand%$countCompleted%$countAll%$countFailed")
                        model.pi.progress.transfer(countAll, countCompleted)
                    }
                },
                threads,
                heap
            )
        })
        MultiThreadTask(
            threadTasks = threadTasks,
            threads = threads.map{ it.thread },
            finalAction = {
                logger?.info("Map expand finished.")
                model.pi.info.transfer("info_map_expand_done%${countCompleted}%$countFailed%${(System.currentTimeMillis()-t0).toDouble()/1e3}")
            }
        ).start()
    }
    private fun adjacentPoints(g: GridPoint, action: (GridPoint, Int) -> Unit){
        if(g[0] > 0) action(GridPoint(g[0]-1), 0)
        if(g[0] < size[0]-1) action(GridPoint(g[0]+1), 1)
    }

    init {
        functionsMap["map_sample"] = mapSample
    }
}