package com.wang_lab.mkm_core.reaction

import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.join
import com.wang_lab.mkm_core.algebra.mmap
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.species.Species

class ReactionPath(val model: ReactionModel,
                   val reactions: MutableList<Pair<Reaction, Boolean>> = mutableListOf(),
                   val totalIS: MutableMap<Species, Int> = mutableMapOf(),
                   val totalFS: MutableMap<Species, Int> = mutableMapOf()) {
    val reactionPools = model.reactions

    fun addReaction(r: Reaction, direction: Boolean): ReactionPath {
        if(reactions.isNotEmpty()){
            val last = reactions.last()
            if(r === last.first && direction != last.second) reactions.remove(last)
            else reactions.add(Pair(r, direction))
        }else{
            reactions.add(Pair(r, direction))
        }
        val init = if(direction) r.initialState else r.finalState
        val final = if(direction) r.finalState else r.initialState
        init.forEach { p -> totalIS[p.first] = (totalIS[p.first] ?: 0) + p.second }
        final.forEach { p -> totalFS[p.first] = (totalFS[p.first] ?: 0) + p.second }
        model.species.forEach { (_, sp) ->
            if(totalIS.containsKey(sp) && totalFS.containsKey(sp)){
                val i = totalIS[sp]!!
                val f = totalFS[sp]!!
                if(i == f){
                    totalIS.remove(sp)
                    totalFS.remove(sp)
                } else if(i > f){
                    totalIS[sp] = i - f
                    totalFS.remove(sp)
                } else {
                    totalIS.remove(sp)
                    totalFS[sp] = f - i
                }
            }
        }
        return this
    }
    fun rearrange(from: Int, to: Int){
        if(from == to) return
        if(from > to){
            val r = reactions[from]
            for(i in (to + 1 .. from).reversed()){
                reactions[i] = reactions[i - 1]
            }
            reactions[to] = r
        }else{
            val r = reactions[from]
            for(i in from until to){
                reactions[i] = reactions[i + 1]
            }
            reactions[to] = r
        }
    }

    fun reactantsExpression() = " + ".join(totalIS.map { e -> e.key.expression(e.value) })
    fun productExpression() = " + ".join(totalFS.map { e -> e.key.expression(e.value) })
    fun totalExpression() = "${reactantsExpression()} -> ${productExpression()}"

    var sequence: String = ""
        set(value){
            try{
                val rl = value.split(',').map{ s ->
                    val a = kotlin.math.abs(s.toInt())
                    val b = s.trim().contains('-')
                    Pair(reactionPools[a], b)
                }
                reactions.clear()
                reactions.addAll(rl)
            }catch (_: Exception){}
            field = value
        }
        get() {
            val rl = reactions.map { p -> (if(p.second) "" else "-") + reactionPools.indexOf(p.first).toString() }
            return ", ".join(rl)
        }

    /**
     * Return the reaction path.
     * Triple:
     * First -> Is this state a transition state?
     * Second -> Energy of this state.
     * Third -> The composition of this state.
     */
    fun getEnergies(p: PointInfo): List<Triple<Boolean, Double, String>>{
        val list = mutableListOf(Triple(false, 0.0, reactantsExpression() ))
        var e = 0.0
        val species = mutableMapOf<Species, Int>()
        totalIS.forEach { (t, u) -> species[t] = u }
        model.species.forEach { (_, u) -> if(!species.containsKey(u)) species[u] = 0 }
        reactions.forEach { (r, d) ->
            val re = r.energyInfo(p, d)
            if(re.second != null) list.add(Triple(true, e + re.second!!, r.transition?.shortName ?: ""))
            e += re.first

            val a = if(d) r.initialState else r.finalState
            val b = if(d) r.finalState else r.initialState

            a.forEach { (sd, i) -> species[sd] = species[sd]!! - i }
            b.forEach { (sd, i) -> species[sd] = species[sd]!! + i }

            list.add(Triple(false, e, " + ".join(species.mapNotNull { e -> if(e.value <= 0) null else e.key.shortExpression(e.value) })))
        }
        return list
    }
    fun getEnthalpy(p: PointInfo) = reactions.sumOf {  it.first.reactionEnthalpy(p, it.second) }
    fun getEntropy(p: PointInfo) = reactions.sumOf {  it.first.reactionEntropy(p, it.second) }
    fun getFreeEnergy(p: PointInfo) = reactions.sumOf {  it.first.reactionFreeEnergy(p, it.second) }
    fun clone() = ReactionPath(
        model, reactions.mmap{ p -> Pair(p.first, p.second) }, totalIS.toMutableMap(), totalFS.toMutableMap()
    )

    override fun toString(): String {
        return listOf(reactions.map { p ->
            if(p.second) p.first.expressionNoT else p.first.expressionReverseNoT
        }, totalFS
        ).toString()
    }
    val reactionMap: Map<Reaction, Int>
        get() {
            val rMap = mutableMapOf<Reaction, Int>()
            reactions.forEach{ (r, b) ->
                rMap[r] = (rMap[r] ?: 0) + if(b) 1 else -1
            }
            return rMap
        }
    /*
    fun getRate(i: Int, j: Int): Double {
        val map = reactionMap
        val rates = mutableListOf<Double>()
        map.forEach { (r, n) ->
            try {
                rates.add((r.rateForwardData!![i, j]!! - r.rateReverseData!![i, j]!!) / n)
            }catch (_: Exception){
                return Double.NaN
            }
        }
        return rates.min()
    }

     */

    override fun equals(other: Any?): Boolean {
        if(other is ReactionPath) return reactionMap == other.reactionMap
        return false
    }

    override fun hashCode(): Int {
        return reactionMap.hashCode()
    }
    fun saveInfo(): String{
        return "\t".join(reactions.map{ (r, d) -> if(d) r.expressionNoT else r.expressionReverseNoT })
    }
}