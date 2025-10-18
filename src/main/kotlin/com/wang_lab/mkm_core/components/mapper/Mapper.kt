package com.wang_lab.mkm_core.components.mapper

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.*
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.*
import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.parseExpression
import com.wang_lab.mkm_core.misc.*
import com.wang_lab.mkm_core.components.mapper.task.MultiThreadTask
import com.wang_lab.mkm_core.point.GridPoint
import com.wang_lab.mkm_core.point.MapPoint
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.components.solver.SteadyStateSolver
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

val BISECTION = Item.withS("bisection")
val THREAD = Item.withS("thread")
abstract class Mapper(private val dim: Int, val model: ReactionModel, par: JsonObject) {
    val maxBisect = switchJsonElement(par["max_bisect"], "Max bisection times", i = { it }, v = { 10 })
    inner class Descriptor(
        val name: String,
        val scales: DoubleArray
    ){
        val descriptor: DescriptorType
        val realScales: List<BigDecimal>
        val expr = parseExpression(name).simplify()
        init{
            val vars = expr.getVariables()
            if(vars.size != 1) throw Exception("Only one variable can exist in a descriptor expression!")
            descriptor = vars.first().let{
                for(t in ThermoDescriptor.values()) if(t.symbol == it || t.descriptor == it) return@let t
                for(ads in model.adsorbates) if(ads.name == it) return@let ScalingSpeciesDescriptor(ads)
                for(ts in model.transitions) if(ts.name == it) return@let ScalingSpeciesDescriptor(ts)
                return@let CustomDescriptor(it)
            }
            realScales = scales.map{ expr.simpleSolve(it).nToBigDecimal() }
        }
        operator fun get(i: Int) = realScales[i]
        operator fun get(d: Double) = expr.simpleSolve(d).nToBigDecimal()
    }
    inner class Size{
        operator fun get(index: Int) = descriptors[index].scales.size
    }
    inner class Name{
        operator fun get(index: Int) = descriptors[index].name
    }
    inner class Type{
        operator fun get(index: Int) = descriptors[index].descriptor
    }
    inner class Scale{
        operator fun get(index: Int) = descriptors[index].scales
    }
    inner class Range{
        operator fun get(index: Int) = listOf(descriptors[index].scales.min(), descriptors[index].scales.max())
    }
    abstract val descriptors: List<Descriptor>
    abstract val grids: List<GridPoint>
    var locking = false
    var stopFlag = false
    var pauseFlag = false
    val size = Size()
    val name = Name()
    val types = Type()
    val scale = Scale()
    val range = Range()

    /**
     * parameters:
     *
     * Int: threadNumber
     *
     * List<GridPoint>?: selection points
     */
    protected val threadTasks = mutableListOf<MultiThreadTask>()
    val haveTask: Boolean
        get() = threadTasks.isNotEmpty()
    fun stop(){
        try{
            stopFlag = true
            val tCopy = threadTasks.map{ it }
            tCopy.forEach{
                try{
                    it.stop()
                }catch (e: Exception){
                    e.printStackTrace()
                }
            }
            threadTasks.clear()
        }catch (e: Exception){
            e.printStackTrace()
        }
    }
    fun pause(){
        pauseFlag = true
        while(locking) Thread.sleep(50)
        threadTasks.forEach{ it.suspend() }
    }
    fun resume(){
        threadTasks.forEach{ it.resume() }
        pauseFlag = false
    }

    fun mapPoint(g: GridPoint) = MapPoint(dim){ descriptors[it].scales[g[it]] }
    private val mapInTurn: (Int, List<GridPoint>, Any?) -> Unit = { threadNumber, selection, _ ->
        val t0 = System.currentTimeMillis()
        logger?.info("Map in turn with ${THREAD.n(threadNumber)}.")
        val heap = ConcurrentLinkedQueue<PointInfo>()
        val all = if(selection.isEmpty())grids.size else selection.size
        var countFinished = 0
        var countFailed = 0
        val lock = Any()
        model.pi.info.transfer("info_map_in_turn%0%$all%0")
        model.pi.progress.transfer(all, 0)
        if(selection.isEmpty()) heap.addAll(model.gridPoints())
        else selection.forEach { heap.add(model.getPoint(it)) }

        MultiThreadTask(
            threadTasks = threadTasks,
            threads = List(threadNumber){
                Thread{
                    while(true){
                        val task = heap.poll() ?: break
                        //model.pi.selection.transfer(task.gridPoint, true)
                        val success = solveWithInitialGuess(task)
                        mapperSynchronized(lock){
                            //model.pi.selection.transfer(task, false)
                            countFinished += 1
                            if(! success) countFailed += 1
                            model.pi.info.transfer("info_map_in_turn%$countFinished%$all%$countFailed")
                            model.pi.progress.transfer(all, countFinished)
                        }
                    }
                }
            },
            finalAction = {
                logger?.info("Map in turn finished. ")
                model.pi.info.transfer("info_map_in_turn_done%${all-countFailed}%$countFailed%${(System.currentTimeMillis()-t0).toDouble()/1e3}")
            }
        ).start()
        println("$countFinished : $all")
    }
    private val mapRandom: (Int, List<GridPoint>, Any?) -> Unit = { threadNumber, selection, par ->
        val count = (par as? Int) ?: 1000
        val heap = ConcurrentLinkedQueue<PointInfo>()
        selection.forEach { heap.add(model.getPoint(it)) }
        var success = false
        MultiThreadTask(
            threadTasks = threadTasks,
            threads = List(threadNumber){
                Thread{
                    while(true){
                        val task = heap.poll() ?: break
                        val r = Random(System.currentTimeMillis())
                        for(i in 1 until count / threadNumber){
                            val initCvg = BDVector(model.adsorbates.size) { 10.0.pow(-r.nextDouble() * 3).nToBigDecimal() }
                            model.solver.solveWithInitialValue(task, initCvg)
                            if(model.solver.validPointValue(task)){
                                success = true
                                model.pi.result.transfer(task)
                                break
                            }
                        }
                    }
                }
            },
            finalAction = {
                if(success)
                    model.pi.info.transfer("info_map_in_turn_succeed")
                else
                    model.pi.info.transfer("info_map_in_turn_fail")
            }
        ).start()
    }
    protected val functionsMap = mutableMapOf<String, (Int, List<GridPoint>, Any?) -> Unit>(
        "map_in_turn" to mapInTurn,
        "map_random" to mapRandom
    )
    val mapFunctions: List<String>
        get() = functionsMap.keys.toList()
    fun map(functionName: String, threads: Int, selection: List<GridPoint> = listOf(), par: Any? = null)
        = functionsMap[functionName]?.invoke(threads, selection, par)


    fun solvePointFrom(target: PointInfo, source: PointInfo, bisect: Int): Pair<Boolean, Int>{
        val c = target.coverage
        target.coverage = null
        if(c != null){
            try{
                model.solver.solveWithInitialValue(target, c, source)
            }catch (e: Exception){
                e.printStackTrace()
                println()
            }
            if(model.solver.validPointValue(target)) return Pair(true, bisect)
        }
        if(bisect > maxBisect){
            logger?.warning("Too small gap.")
            return Pair(false, bisect)
        }
        logger?.finer("Solving $target from $source.")
        val sourceValue = model.solver.getValue(source)
        if(sourceValue != null) try{ model.solver.solveWithInitialValue(target, sourceValue, source) }catch (_: Exception){}
        if(model.solver.validPointValue(target)){
            return Pair(true, bisect)
        }else{
            val midPoint = model.getPoint(target.mapPoint.midPoint(source.mapPoint))
            val (nr, nb) = solvePointFrom(midPoint, source, bisect + 1)
            if(!nr || !model.solver.validPointValue(midPoint)) return Pair(false, nb)
            val (nr2, nb2) = solvePointFrom(target, midPoint, bisect + 1)
            if(!nr2 || !model.solver.validPointValue(target)) return Pair(false, Integer.max(nb, nb2))
            return Pair(true, Integer.max(nb, nb2))
        }
    }

    fun inputData(dis: DataInputStream) {
        model.pi.progress.transfer(1, 0)
        val pp = dis.readInt()
        repeat(pp){
            val p = model.getPoint(GridPoint(dis))
            p.loadPoint(dis)
            try{
                model.pi.result.transfer(p)
            }catch (e: Exception){
                e.printStackTrace()
            }
            model.pi.progress.transfer(grids.size, it+1)
        }
    }
    fun outputData(dos: DataOutputStream) {
        model.pi.progress.transfer(1, 0)
        dos.writeInt(grids.size)
        model.gridPoints().forEachIndexed{ i, p ->
            p.savePoint(dos)
            model.pi.progress.transfer(grids.size, i+1)
        }
    }
    fun solveWithInitialGuess(p: PointInfo, thread: Int = -1): Boolean{
        logger?.finer("Solving $p with initial guess.")
        model.solver.solveWithInitialGuess(p)
        return if(model.solver.validPointValue(p)){
            logger?.info("Succeeded in solving $p with initial guess in thread#$thread.")
            true
        }else{
            logger?.info("Failed in solving $p with initial guess in thread#$thread.")
            false
        }
    }
    /*
    fun getCurrent(grid: GridPoint):Double{
        if(!model.isEChem()) throw Exception("Not an electrochemical model.")
        val values = gridVar(grid)
        var current = 0.0
        (model.solver as SteadyStateSolver).rates.forEachIndexed { i, r ->
            if(model.reactions[i].electron != 0)
                current += r.arithmetic(values).toDouble() * model.reactions[i].electron
        }
        return current
    }

     */
    private fun gridVar(point: PointInfo): MutableMap<String, Number>{
        val solver = model.solver as SteadyStateSolver
        val (kf, kr) = point.rateConstants
        val vars = mutableMapOf<String, Number>()
        point.thermo.values.forEach { (k, v) -> vars[k] = v.nToBigDecimal() }
        model.moleculeSpecies.forEach { g -> g.concentration?.arithmetic(vars)?.nToBigDecimal()?.let{ vars[g.identifier] = it } }
        kf.forEachIndexed { i, k -> vars["kf[$i]"] = k }
        kr.forEachIndexed { i, k -> vars["kr[$i]"] = k }
        forEachZipped(point.coverage!!, model.adsorbates) { cvg, ads -> vars[ads.identifier] = cvg }
        solver.derivable.forEach{ (si, expr) -> vars[si.identifier] = expr.arithmetic(vars).nToBigDecimal() }
        return vars
    }
    fun rateInformationColor(grid: GridPoint): List<Pair<String, Boolean>>{
        val values = gridVar(model.getPoint(grid))
        val rates = (model.solver as SteadyStateSolver).rates.mapIndexed{ i, r ->
            val scale = model.reactions[i].getSelectivity(model.selectivityAtom)
            if(scale == 0) BigDecimal.ZERO
            else r.arithmetic(values).nToBigDecimal() * scale
        }
        println(rates)
        val lr = rates.map{ if(it.isZero()) -1e99 else it.abs().ln().toDouble() }
        val max = lr.max()
        val d = ln(100.0)
        val min = max - d
        return mapZipped(lr, rates){ l, r ->
            Pair("%02x".format(((max - max(l, min)) / d * 224.0).toInt()), r.signum() >= 0)
        }
    }
    fun rateInformation(grid: GridPoint): String{
        val sb = StringBuilder()
        val values = gridVar(model.getPoint(grid))
        val dict = model.solver.expressionDictionary
        val buffer = mutableMapOf<AlgebraExpr, Number>()
        val rates = model.reactions.indices.map { dict["r[$it]"]!!.arithmetic(values, dict, buffer).nToBigDecimal() }
        forEachZipped(model.reactions, rates){ r, rate ->
            sb.append(r.expression)
            sb.append(": ")
            sb.append("%.6e".format(rate))
            sb.append("\n")
        }
        sb.append("\n")
        values.forEach { (e, v) ->
            sb.append("$e: ${"%.6e".format(v)}\n")
        }
        sb.append("\n")
        dict.forEach { (e, expr) ->
            val v = expr.arithmetic(values, dict, buffer).nToBigDecimal()
            sb.append("$e: ${"%.6e".format(v)}\n")
        }
        return sb.toString()
    }
    protected fun mapperSynchronized(lock: Any, block: () -> Unit){
        while(pauseFlag) Thread.sleep(50)
        synchronized(lock) {
            locking = true
            block()
            locking = false
        }
    }
}