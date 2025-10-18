package com.wang_lab.mkm_core.reaction

import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.parseExpression
import com.wang_lab.mkm_core.algebra.number_math.dSumOf
import com.wang_lab.mkm_core.algebra.number_math.iSumOf
import com.wang_lab.mkm_core.exception.MKMSetupException
import com.wang_lab.mkm_core.exception.ReactionDisabledException
import com.wang_lab.mkm_core.indexedSumOf
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.species.*
import com.wang_lab.mkm_core.species.Species.Companion.defaultName
import com.wang_lab.mkm_core.species.Species.Companion.interpret
import java.math.BigDecimal
import java.util.regex.Pattern

class Reaction(name: String, sdDict: MutableMap<String, Species>, defaultSite: String?, index: Int,
               val model: ReactionModel, throws: Boolean = true, isVirtual: Boolean = false) {
    class EnergyData(val enthalpy: AlgebraExpr, val entropy: AlgebraExpr? = null){
        fun freeEnergy(values: Map<String, Double>, t: Double): Double{
            val h = enthalpy.arithmetic(values).toDouble()
            if(entropy == null) return h
            return h - entropy.arithmetic(values).toDouble() * t
        }

        override fun toString(): String {
            if(entropy == null) return enthalpy.toString()
            return "$enthalpy:$entropy"
        }
    }
    private fun readEnergyData(s: String): EnergyData{
        return if(':' in s){
            val p = s.split(':')
            EnergyData(parseExpression(p[0]), parseExpression(p[1]))
        }else{
            EnergyData(parseExpression(s))
        }
    }
    private fun List<EnergyData>.freeEnergy(p: PointInfo): Double{
        val t = p.thermo.t!!
        val values = p.thermo.values
        val mp = p.mapPoint
        return indexedSumOf { i, e -> if(i == 0) e.freeEnergy(values, t) else e.freeEnergy(values, t) * mp[i-1] }
    }
    fun List<EnergyData>.enthalpy(p: PointInfo): Double{
        val values = p.thermo.values
        val mp = p.mapPoint
        return indexedSumOf { i, e -> if(i == 0) e.enthalpy.arithmetic(values).toDouble() else e.enthalpy.arithmetic(values).toDouble() * mp[i-1] }
    }
    private fun List<EnergyData>.entropy(p: PointInfo): Double{
        val values = p.thermo.values
        val mp = p.mapPoint
        return indexedSumOf { i, e ->
            if(i == 0) e.entropy?.arithmetic(values)?.toDouble() ?: 0.0
            else (e.entropy?.arithmetic(values)?.toDouble() ?: 0.0) * mp[i-1]
        }
    }

    val expression: String
    val expressionReverse: String
    val expressionNoT: String
    val expressionReverseNoT: String
    val states: List<String>
    val initialState: List<Pair<Species, Int>>
    val transitionState: List<Pair<Species, Int>>?
    val finalState: List<Pair<Species, Int>>
    val transition: Transition?
    var prefactor: BigDecimal? = null
    var correction: BigDecimal = BigDecimal.ONE
    var beta: Double? = null
    var disabled = false
    val electron: Int
    var energySource: Reaction? = null
    var dGr: List<EnergyData>? = null
    var dGa: List<EnergyData>? = null
    var kf: BigDecimal? = null
    var kr: BigDecimal? = null

    var bufferSelectivityScale: Int = 0
    var bufferSelectivityAtom: List<String> = listOf()

    init{
        val options = name.split(";")
        for(i in 1 until options.size){
            if(options[i].trim() == "disabled"){
                if(throws) throw ReactionDisabledException()
                disabled = true
            }else if(options[i].contains("=")){
                val p = options[i].split("=")
                when(p[0].trim()){
                    "prefactor" -> {
                        try{
                            prefactor = BigDecimal(p[1].trim())
                        }catch (e: NumberFormatException){
                            if(throws) throw Exception("Prefactor must ba a number!")
                        }
                    }
                    "correction" -> {
                        try{
                            correction = BigDecimal(p[1].trim())
                        }catch (e: NumberFormatException){
                            if(throws) throw Exception("Correction must ba a number!")
                        }
                    }
                    "beta" -> {
                        try{
                            beta = p[1].trim().toDouble()
                        }catch (e: NumberFormatException){
                            if(throws) throw Exception("Beta must ba a number!")
                        }
                    }
                    "energy_source" -> {
                        val expr = p[1].trim()
                        energySource = Reaction(expr, sdDict, defaultSite, index, model, throws, true)
                    }
                    "dGr" -> {
                        try{
                            dGr = p[1].split(',').map{ readEnergyData(it) }
                        }catch (e: NumberFormatException){
                            if(throws) throw Exception("Reaction free energy must ba a set of numbers separated by ',' and ':'.")
                        }
                    }
                    "dGa" -> {
                        try{
                            dGa = p[1].split(',').map{ readEnergyData(it) }
                        }catch (e: NumberFormatException){
                            if(throws) throw Exception("Activation free energy must ba a set of numbers separated by ',' and ':'.")
                        }
                    }
                    "kf" -> {
                        try{
                            kf = BigDecimal(p[1].trim())
                        }catch (e: NumberFormatException){
                            if(throws) throw Exception("Rate constant must ba a number!")
                        }
                    }
                    "kr" -> {
                        try{
                            kr = BigDecimal(p[1].trim())
                        }catch (e: NumberFormatException){
                            if(throws) throw Exception("Rate constant must ba a number!")
                        }
                    }
                }
            }
        }
        val split = options[0].split(reactionSplitter)
        states = List(3){ i ->
            when(i){
                0 -> split[0].trim()
                1 -> if(split.size == 3) split[1].trim() else ""
                2 -> split[split.size-1].trim()
                else -> ""
            }
        }
        initialState = try{
            getSpecies(states[0], defaultSite, sdDict, model, index)!!
        }catch(e: Exception){
            if(throws) throw e else listOf()
        }
        val cIS = totalComposition(initialState)

        transitionState = try{
            getSpecies(states[1], defaultSite, sdDict, model, index, cIS)
        }catch(e: Exception){
            if(throws) throw e else null
        }
        finalState = try{
            getSpecies(states[2], defaultSite, sdDict, model, index)!!
        }catch(e: Exception){
            if(throws) throw e else listOf()
        }
        val cFS = totalComposition(finalState)

        val ts = transitionState?.mapNotNull { p -> if(p.first is Transition) p.first else null}
        transition = try{
            if(ts == null || ts.isEmpty()) null
            else if(ts.size > 1) throw Exception("One reaction should only contains one transition state! \"$name\" contains ${ts.size}.")
            else ts[0] as Transition
        }catch(e: Exception){
            if(throws) throw e else null
        }
        if(transition != null) transition.reaction = this

        val sb = StringBuilder()
        sb.append(getExpression(initialState))
        if(transitionState != null){
            sb.append(" <-> ")
            sb.append(getExpression(transitionState))
        }
        sb.append(" -> ")
        sb.append(getExpression(finalState))
        expression = sb.toString()

        if(throws){
            if(cIS != cFS)
                throw MKMSetupException("unequal_composition", "Composition of initial state is not equal to final state in reaction $expression.")
            if(transitionState != null)
                if(totalComposition(transitionState) != cIS)
                    throw MKMSetupException("unequal_composition", "Composition of initial state is not equal to transition state in reaction $expression.")

            val charge = totalCharge(initialState)
            if(totalCharge(finalState) != charge)
                throw MKMSetupException("unequal_charge", "Charge of initial state is not equal to final state in reaction $expression.")
            if(transitionState != null)
                if(totalCharge(finalState) != charge)
                    throw MKMSetupException("unequal_charge", "Charge of initial state is not equal to transition state in reaction $expression.")
        }
        sb.clear()
        sb.append(getExpression(finalState))
        if(transitionState != null){
            sb.append(" <-> ")
            sb.append(getExpression(transitionState))
        }
        sb.append(" -> ")
        sb.append(getExpression(initialState))
        expressionReverse = sb.toString()

        sb.clear()
        sb.append(getExpression(initialState))
        sb.append(" -> ")
        sb.append(getExpression(finalState))
        expressionNoT = sb.toString()
        sb.clear()
        sb.append(getExpression(finalState))
        sb.append(" -> ")
        sb.append(getExpression(initialState))
        expressionReverseNoT = sb.toString()
        val ni = initialState.iSumOf { (sp, n) -> if(sp == Electron) n else 0 }
        val nf = finalState.iSumOf { (sp, n) -> if(sp == Electron) n else 0 }
        electron = nf - ni
        if(isVirtual){
            initialState.forEach{ it.first.includeEnergy = true }
            transitionState?.forEach{ it.first.includeEnergy = true }
            finalState.forEach{ it.first.includeEnergy = true }
        }else{
            if(kf == null && kr == null && energySource == null && dGr == null){
                initialState.forEach{ it.first.includeEnergy = true }
                transitionState?.forEach{ it.first.includeEnergy = true }
                finalState.forEach{ it.first.includeEnergy = true }
            }
            initialState.forEach{ it.first.notVirtual = true }
            transitionState?.forEach{ it.first.notVirtual = true }
            finalState.forEach{ it.first.notVirtual = true }
        }
    }
    override fun toString() = expression

    /**
     * Returns the reaction energy and activation energy of the reaction.
     * The activation energy is not checked, so it may be negative or lower than reaction energy.
     */
    fun energyInfo(p: PointInfo, forward: Boolean = true): Pair<Double, Double?> {
        if(dGr != null){
            return if(forward){
                Pair(dGr!!.freeEnergy(p), dGa?.freeEnergy(p))
            }else{
                val gr = dGr!!.freeEnergy(p)
                val ga = dGa?.freeEnergy(p)
                if(ga == null) Pair(-gr, null)
                else Pair(-gr, ga - gr)
            }
        }
        if(energySource == null){
            val el = p.energyList
            val eIS = initialState.dSumOf { (sp, n) -> el[sp] * n }
            val eFS = finalState.dSumOf { (sp, n) -> el[sp] * n }
            val eTS =
                if(dGa == null) transitionState?.dSumOf { (sp, n) -> el[sp] * n } ?: Double.NaN
                else eIS + dGa!!.freeEnergy(p)
            var ea: Double? = eTS - if(forward) eIS else eFS
            if(ea!!.isNaN()) ea = null
            return if(forward) Pair(eFS - eIS, ea)
            else Pair(eIS - eFS, ea)
        }else{
            return if(dGa == null){
                energySource!!.energyInfo(p, forward)
            }else{
                val (gr, _) = energySource!!.energyInfo(p, forward)
                val ga = dGa!!.freeEnergy(p)
                if(forward) Pair(gr, ga) else Pair(gr, gr + ga)
            }
        }
    }
    fun reactionEnthalpy(p: PointInfo, forward: Boolean = false): Double {
        if(dGr != null) return dGr!!.enthalpy(p)
        if(energySource != null) return energySource!!.reactionEnthalpy(p, forward)
        val eIS = initialState.dSumOf { (sp, n) -> sp.getEnthalpy(p, model.scaler) * n }
        val eFS = finalState.dSumOf { (sp, n) -> sp.getEnthalpy(p, model.scaler) * n }
        return if(forward) eFS - eIS else eIS - eFS
    }
    fun reactionEntropy(p: PointInfo, forward: Boolean = false): Double {
        if(dGr != null) return dGr!!.entropy(p)
        val thermo = p.thermo
        if(energySource != null) return energySource!!.reactionEntropy(p, forward)
        val eIS = initialState.dSumOf { (sp, n) -> sp.getEntropy(thermo) * n }
        val eFS = finalState.dSumOf { (sp, n) -> sp.getEntropy(thermo) * n }
        return if(forward) eFS - eIS else eIS - eFS
    }
    fun reactionFreeEnergy(p: PointInfo, forward: Boolean = false): Double {
        if(dGr != null) return dGr!!.freeEnergy(p)
        if(energySource != null) return energySource!!.reactionFreeEnergy(p, forward)
        val el = p.energyList
        val eIS = initialState.dSumOf { (sp, n) -> el[sp] * n }
        val eFS = finalState.dSumOf { (sp, n) -> el[sp] * n }
        return if(forward) eFS - eIS else eIS - eFS
    }

    /**
     * Returns the activation energy of the forward reaction and reversed reaction.
     * This energy is directly used for calculating rate constant.
     */
    fun reactionActivationEnergies(p: PointInfo): Pair<Double, Double>{
        val init =
            if(dGr != null) 0.0
            else (energySource ?: this).initialState.dSumOf{ (sp, n) -> p.energyList[sp] * n }
        val final =
            if(dGr != null) dGr!!.freeEnergy(p)
            else (energySource ?: this).finalState.dSumOf{ (sp, n) -> p.energyList[sp] * n }
        val es = mutableListOf(init, final)
        if(dGa != null){
            es.add(init + dGa!!.freeEnergy(p))
        }else if(dGr == null){
            val ts = (energySource ?: this).transitionState
            if(ts != null) es.add(ts.dSumOf{ (sp, n) -> p.energyList[sp] * n })
        }
        val ts = es.max()
        return Pair(ts - init, ts - final)
    }
    companion object{
        private val reactionSplitter = Pattern.compile("(<->|->)")
        private val speciesPattern = Pattern.compile("(?<mount>\\d*)(?<name>\\^?[A-Za-z\\d-:+-.~|_*]+)")
        private val splitter = Regex("(?<!:\\d{0,9})\\+")
        val dnf = "Default site not defined!"
        fun getSpecies(expr: String, defaultSite: String?, sdDict: MutableMap<String, Species>, model: ReactionModel, index: Int, composition: Map<String, Int>? = null): List<Pair<Species, Int>>?{
            if(expr.isBlank()) return null
            val map = mutableMapOf<Species, Int>()
            val speciesItems = expr.split(splitter)
            val s = mutableListOf<String>()
            var eCh = false
            speciesItems.forEach{
                val sp = it.trim()
                if(sp.contains('^')){
                    if(eCh) throw Exception("More than one electrochemical transition states in one reaction state: $expr.")
                    eCh = true
                    s.add(sp)
                }else{
                    s.add(0, sp)
                }
            }
            val restC = composition?.toMutableMap()
            for(sp in s){
                val matcher = speciesPattern.matcher(sp)
                if(!matcher.matches()) throw Exception("${sp.trim()} of $expr in reaction expressions is invalid!")
                val gm = matcher.group("mount")
                val mount = if(gm.isEmpty()) 1 else gm.toInt()
                var name = matcher.group("name")
                if(name == "*") name = defaultSite ?: throw Exception(dnf)
                else if(name.endsWith("_*")) name = name.replace("*", defaultSite ?: throw Exception(dnf))
                else if(name.endsWith("*")) name = name.replace("*", "_${defaultSite ?: throw Exception(dnf)}")
                name = name.replace("*_", "")
                if(sp.startsWith("^")){
                    val sd = Species.loadEChem(name, defaultSite, index,null, model.defaultThermoMode, model.species)
                    sdDict[sd.name] = sd
                    map.forEach{ (sp, n) ->
                        if(sp is SolidSite) restC!![sp.name] = restC[sp.name]!! - n
                        if(sp is MoleculeSpecies){
                            sp.composition.forEach{ (el, ne) ->
                                restC!![el] = restC[el]!! - n * ne
                            }
                        }
                    }
                    sd.site.forEach{ (site, n) ->
                        restC!![site.name] = restC[site.name]!! - n
                    }
                    sd.composition = restC!!
                    map[sd] = (map[sd] ?: 0) + mount
                }else{
                    val interpreted = interpret(name, defaultSite)
                    val sd = if(interpreted != null){
                        val (info, _, sStr) = interpreted
                        val (config, formula, charge) = info
                        val defaultName = defaultName(config, formula, charge, sStr)
                        if(sdDict.containsKey(defaultName)){
                            sdDict[defaultName]!!
                        }else {
                            val sd = Species.load(name, defaultSite, model.pi,null, model.defaultThermoMode, model.species)
                            sdDict[defaultName] = sd
                            sd
                        }
                    }else{
                        if(sdDict.containsKey(name)){
                            sdDict[name]!!
                        }else {
                            val sd = Species.load(name, defaultSite, model.pi,null, model.defaultThermoMode, model.species)
                            sdDict[name] = sd
                            sd
                        }
                    }
                    map[sd] = (map[sd] ?: 0) + mount
                }
            }
            val list = ArrayList<Pair<Species, Int>>()
            map.forEach { (t, u) -> list.add(Pair(t, u)) }
            return list.toList()
        }
        fun getExpression(state: List<Pair<Species, Int>>) =
            state.joinToString(" + ") { p ->
                if (p.second == 1) p.first.name
                else "${p.second}${p.first.name}"
            }

        fun totalCharge(state: List<Pair<Species, Int>>): Int
                = state.sumOf { (sp, n) -> sp.charge * n }
        fun totalComposition(state: List<Pair<Species, Int>>): Map<String, Int>{
            val c = mutableMapOf<String, Int>()
            state.forEach { (sp, n) ->
                if(sp is SolidSite){
                    c[sp.name] = (c[sp.name]?: 0) + n
                }else if(sp is MoleculeSpecies){
                    sp.composition.forEach{ (ele, i) ->
                        c[ele] = (c[ele]?: 0) + i * n
                    }
                    if(sp is SurfaceSpecies) sp.site.forEach{ (sn, snn) ->
                        c[sn.name] = (c[sn.name]?: 0) + snn * n
                    }
                }
            }
            if("pe" in c){
                c["H"] = (c["H"] ?: 0) + c["pe"]!!
                c.remove("pe")
            }
            if("ele" in c) c.remove("ele")
            return c
        }
    }
    fun getParameters(): Map<String, String>{
        val map = mutableMapOf(
            "is" to states[0],
            "ts" to states[1],
            "fs" to states[2],
        )
        if(prefactor != null) map["prefactor"] = prefactor.toString()
        if(beta != null) map["beta"] = beta.toString()
        if(energySource != null) map["energy_source"] = energySource.toString()
        if(dGr != null) map["dGr"] = dGr!!.joinToString(",")
        if(dGa != null) map["dGa"] = dGa!!.joinToString(",")
        if(kf != null) map["kf"] = kf.toString()
        if(kr != null) map["kr"] = kr.toString()
        return map
    }
    fun density(): Double{
        if(transition != null) return transition.site.minOf { (site, _) -> site.density!! }
        val sites = mutableSetOf<SolidSite>()
        initialState.forEach { (sd, _) -> if(sd is Adsorbate) sd.site.forEach{ (s, _) -> sites.add(s) } }
        finalState.forEach { (sd, _) -> if(sd is Adsorbate) sd.site.forEach{ (s, _) -> sites.add(s) } }
        return sites.minOf { it.density!! }
    }

    fun getSelectivity(selectivityAtom: List<String>): Int {
        if(bufferSelectivityAtom == selectivityAtom) return bufferSelectivityScale
        bufferSelectivityAtom = selectivityAtom
        bufferSelectivityScale = if(bufferSelectivityAtom.isEmpty()){
            0
        }else{
            initialState.sumOf { (sp, n) ->
                if(sp is MoleculeSpecies){
                    selectivityAtom.sumOf { sp.composition[it] ?: 0 } * n
                }else{
                    0
                }
            }
        }
        return bufferSelectivityScale
    }
    /*
    fun genRateData(model: ReactionModel){
        val row = rateConstForwardData.rows
        val column = rateConstForwardData.columns
        rateForwardData = PlotData(expression, row, column)
        rateReverseData = PlotData(expression, row, column)
        val ef = ExprMultiply(
            mutableListOf<AlgebraExpr>(ExprVar("kf")).addAllR(initialState.mmap{ r ->
                ExprVar(r.first.identifier, r.second)
            })).simplify()
        val er = ExprMultiply(
            mutableListOf<AlgebraExpr>(ExprVar("kr")).addAllR(finalState.mmap{ r ->
                ExprVar(r.first.identifier, r.second)
            })).simplify()
        for(i in 0 until  row){
            for(j in 0 until  column){
                val p = mutableMapOf<String, Double>()
                val t = model.scaler.getThermo(model.mapPoint(GridPoint(i, j)))
                try{
                    model.adsorbates.forEach { p[it.identifier] = it.coverageData[i, j]!!.toDouble() }
                    model.gases.forEach { p[it.identifier] = it.getPressure(t) }
                    (model.solver as SteadyStateSolver).sites.forEachIndexed { k, e -> p[model.sites[k].identifier] = e.arithmetic(p).toDouble() }
                    if(rateConstForwardData[i, j] != null){
                        p["kf"] = rateConstForwardData[i, j]!!.toDouble()
                        rateForwardData!![i, j] = ef.arithmetic(p).toDouble()
                    }else{
                        rateForwardData!![i, j] = Double.NaN
                    }
                    if(rateConstReverseData[i, j] != null){
                        p["kr"] = rateConstReverseData[i, j]!!.toDouble()
                        rateReverseData!![i, j] = er.arithmetic(p).toDouble()
                    }else{
                        rateReverseData!![i, j] = Double.NaN
                    }
                }catch (e: Exception){
                    rateForwardData!![i, j] = Double.NaN
                    rateReverseData!![i, j] = Double.NaN
                }
            }
        }
    }
     */
}