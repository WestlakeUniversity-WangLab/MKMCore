package com.wang_lab.mkm_core

import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.algebra.*
import com.wang_lab.mkm_core.algebra.big_decimal_math.*
import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.parseExpression
import com.wang_lab.mkm_core.components.ComponentsLoader.Companion.getComponentInstance
import com.wang_lab.mkm_core.misc.*
import com.wang_lab.mkm_core.exception.MKMSetupException
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.components.mapper.*
import com.wang_lab.mkm_core.molecule.Atom.Companion.atomSources
import com.wang_lab.mkm_core.components.parsers.Parser
import com.wang_lab.mkm_core.components.scaler.Scaler
import com.wang_lab.mkm_core.species.*
import com.wang_lab.mkm_core.molecule.Molecule.Companion.moleculeSources
import com.wang_lab.mkm_core.reaction.Reaction
import com.wang_lab.mkm_core.exception.ReactionDisabledException
import com.wang_lab.mkm_core.components.guesser.Guesser
import com.wang_lab.mkm_core.components.modifier.Modifier
import com.wang_lab.mkm_core.point.GridPoint
import com.wang_lab.mkm_core.point.MapPoint
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.reaction.ReactionPath
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.components.solver.*
import com.wang_lab.mkm_core.components.thermodynamics.gas_thermo.ShomateGas.Companion.shomateGasSources
import com.wang_lab.mkm_core.constants.F
import com.wang_lab.mkm_core.misc.ThermoDescriptor.*
import java.io.*
import java.math.MathContext
import java.util.logging.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

val paths = mutableListOf<String>()
const val version = "v0.2.0-alpha"
var logger: Logger? = null

/**
 * This is a class that includes all components of a chemical catalyst system.
 *
 * Though have been deeply instantiated, it is still not recommended to build more than one reaction model at one time.
 * There may still be some global variables.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class ReactionModel(
    val initPars: JsonObject,
    _filePath: String,
    val pi: PyInteraction = PyInteraction()
) {
    //basic data
    var defaultSite : String

    val thermo: Thermo

    val allSites: List<SolidSite>

    val sites: List<SolidSite>
    val sitesE: List<SolidSite>
    val gases: List<Gas>
    val gasesE: List<Gas>
    val liquids: List<Liquid>
    val liquidsE: List<Liquid>
    val adsorbates: List<Adsorbate>
    val adsorbatesE: List<Adsorbate>
    val surfaceSpeciesE: List<SurfaceSpecies>
    val transitions: List<Transition>
    val transitionsE: List<Transition>
    val aqueous: List<Aqua>
    val aqueousE: List<Aqua>
    val moleculeSpecies: List<MoleculeSpecies>

    val solids: List<SurfaceSpecies>
    val solidsE: List<SurfaceSpecies>

    val reactions: List<Reaction>
    //components
    val mapper: Mapper
    val parser: Parser
    val guesser: Guesser
    val solver: Solver
    val scaler: Scaler
    val modifiers = mutableListOf<Modifier>()
    val writers = mutableListOf<com.wang_lab.mkm_core.components.writer.Writer>()
    //parameters

    val file = File(_filePath)
    val dataFile = File(file.parentFile, file.nameWithoutExtension + ".dat")
    val species: MutableMap<String, Species> = mutableMapOf()
    //thermo
    val defaultThermoMode = mutableMapOf<String, String>()

    val selectivityAtom: List<String>
    val selectivityMap: List<Pair<Int, Int>>?

    val customPlot: MutableMap<String, AlgebraExpr>

    private val gridPointMap: Map<GridPoint, PointInfo>
    private val pointMap: MutableMap<MapPoint, PointInfo>
    var zip = true
    init{
        fun <T> key(
            key: String,
            s: ((String) -> T)? = null,
            b: ((Boolean) -> T)? = null,
            d: ((Double) -> T)? = null,
            i: ((Int) -> T)? = null,
            n: (() -> T)? = null,
            a: ((JsonArray) -> T)? = null,
            o: ((JsonObject) -> T)? = null,
            v: (() -> T)? = null,
        ): T{
            return switchJsonElement(
                initPars[key],
                "\"$key\" in MKM file",
                s, b, d, i, n, a, o, v
            )
        }

        try {
            if(_filePath.isNotEmpty()) logger = Logger.getLogger(file.nameWithoutExtension)
            logger?.level = Level.ALL
            key("log",
                s={
                    if(it == "on"){
                        val handler = FileHandler(File(file.parent, file.nameWithoutExtension + ".log").absolutePath)
                        handler.formatter = MKMFormatter()
                        handler.level = Level.ALL
                        logger?.addHandler(handler)
                    }else if(it == "off"){
                        logger?.info("Logger close.")
                        logger = null
                    }
                    Unit
                },
                v = {}
            )
            logger?.info("MKM Core $version by Hexatomic Ring.")
            pi.info.transfer("info_load_data")
            logger?.info("Loading $file.")

            try {
                paths.add(file.parent)
            }catch (_: Exception){}
            key(
                key = "extra_atoms",
                s = { atomSources.add(it) },
                a = { atomSources.addAll(it.map { m -> m.jsonPrimitive.content }) },
                v = {}
            )
            key(
                key = "extra_molecules",
                s = { moleculeSources.add(it) },
                a = { moleculeSources.addAll(it.map { m -> m.jsonPrimitive.content }) },
                v = {}
            )
            key(
                key = "extra_shomate",
                s = { shomateGasSources.add(it) },
                a = { shomateGasSources.addAll(it.map { m -> m.jsonPrimitive.content }) },
                v = {}
            )

            key("decimal_precision", i = { decimalPrecision = it }, v = { decimalPrecision = 75 })
            precision = MathContext(decimalPrecision + 5)

            defaultSite = key("default_site", s = { it }, v = { "s" })
            val reactionExpressions = key("reactions", a = { it.map{ s -> s.jsonPrimitive.content } })

            thermo = Thermo()
            initPars["thermo"]?.let{je ->
                je.jsonObject.forEach{ k, v ->
                    val va = v.jsonPrimitive.doubleOrNull ?: return@forEach
                    val d = values().firstOrNull { it.descriptor == k || it.symbol == k }
                    when(d){
                        Temperature -> thermo.t = va
                        Pressure -> thermo.p = va
                        Voltage -> thermo.u = va
                        null -> thermo[k] = va
                    }
                }
            }

            pi.info.transfer("info_analyse_species")
            logger?.info("Analysing species.")

            key("default_thermo",
                o = { it.forEach { (k, v) -> defaultThermoMode[k] = v.jsonPrimitive.content } },
                v = {}
            )

            val sds = key("species", o = { it.mapValues { e -> e.value.jsonObject} })
            sds.forEach { (t, u) -> species[t] = Species.load(t, defaultSite, pi, u, defaultThermoMode, species) }
            reactions = reactionExpressions.mapIndexedNotNull { i, expr ->
                try {
                    Reaction(expr, species, defaultSite, i, this)
                }catch(e: ReactionDisabledException){
                    null
                }
            }

            allSites = species.values.filterIsInstance<SolidSite>().sortedBy{ it.name }
            val allGases = species.values.filterIsInstance<Gas>().sortedBy{ it.name }
            val allLiquids = species.values.filterIsInstance<Liquid>().sortedBy{ it.name }
            val allAds = species.values.filterIsInstance<Adsorbate>().sortedBy{ it.name }
            val allTS = species.values.filterIsInstance<Transition>().sortedBy{ it.name }
            val allAq = species.values.filterIsInstance<Aqua>().sortedBy{ it.name }

            moleculeSpecies = species.values.filterIsInstance<MoleculeSpecies>().real()

            gases = allGases.real()
            adsorbates = allAds.real()
            transitions = allTS.real()
            liquids = allLiquids.real()
            aqueous = allAq.real()
            solids = listOf<SurfaceSpecies>().nAddAllR(adsorbates).addAllR(transitions)
            val sitesSet = mutableSetOf<SolidSite>()
            adsorbates.forEach{ it.site.forEach{ (s, _) -> sitesSet.add(s) } }
            transitions.forEach{ it.site.forEach{ (s, _) -> sitesSet.add(s) } }
            sites = sitesSet.toList().sortedBy { it.name }

            sitesE = allSites.energetic()
            gasesE = allGases.energetic()
            liquidsE = allLiquids.energetic()
            aqueousE = allAq.energetic()
            adsorbatesE = allAds.energetic()
            transitionsE = allTS.energetic()

            surfaceSpeciesE = species.values.filterIsInstance<SurfaceSpecies>().energetic()

            solidsE = listOf<SurfaceSpecies>().nAddAllR(adsorbatesE).addAllR(transitionsE)

            // initializing components
            mapper = key("mapper", o = { getComponentInstance(Mapper::class.java, it, this) })
            pi.info.transfer("info_parse_input_file")
            parser = key("parser", o = { getComponentInstance(Parser::class.java, it, this) })
            adsorbatesE.forEach { ads -> ads.calculateMeanFrequencies() }
            transitionsE.forEach { ads -> ads.calculateMeanFrequencies() }
            sitesE.forEach { s -> s.calculateMeanFrequencies() }

            selectivityAtom = key("selectivity_tracking", s = { listOf(it) }, v = { listOf() }, a = { it.map { s -> s.jsonPrimitive.content } })
            selectivityMap =
                if(selectivityAtom.isNotEmpty())
                    List(gases.size){ Pair(it, selectivityAtom.sumOf { atom -> gases[it].composition[atom] ?: 0 }) }.filter { it.second > 0 }
                else null

            pi.info.transfer("info_solver_compile")
            logger?.info("Compiling solver.")
            solver = key("solver", o = { getComponentInstance(Solver::class.java, it, this) })
            guesser = key("guesser", o = { getComponentInstance(Guesser::class.java, it, this) })
            scaler = key("scaler", o = { getComponentInstance(Scaler::class.java, it, this) })

            gridPointMap = mapper.grids.toMap { createPoint(it) }
            pointMap = gridPointMap.mapKeys{ it.value.mapPoint } as MutableMap<MapPoint, PointInfo>
            customPlot = key("custom_plot",
                o = { jo ->
                    jo.mapValues{ parseExpression(it.value.jsonPrimitive.content) }.toMutableMap()
                },
                v = { mutableMapOf() }
            )
            key("modifiers",
                a = {
                    it.forEach{ j ->
                        if(j is JsonObject) modifiers.add(getComponentInstance(Modifier::class.java, j, this))
                    }
                },
                v = {}
            )
            key("writer",
                o = { writers.add(getComponentInstance(com.wang_lab.mkm_core.components.writer.Writer::class.java, it, this)) },
                v = {}
            )
            key("writers",
                a = {
                    it.forEach{ j ->
                        if(j is JsonObject) writers.add(getComponentInstance(com.wang_lab.mkm_core.components.writer.Writer::class.java, j, this))
                    }
                },
                v = {}
            )
            pi.info.transfer("info_init_done")
            logger?.info("Reaction model initialization done.")
            logger?.fine("Site list: $sites")
            logger?.fine("Gas list: $gases")
            logger?.fine("Adsorbate list: $adsorbates")
            logger?.fine("Transition state list: $transitions")
        } catch (e: Exception){
            logger?.warning("Error while initializing reaction model!")
            logger?.warning(e.stackTraceToString())
            pi.error.transfer(version, e.javaClass.toString(), e.message, (e as? MKMSetupException)?.type, e.stackTraceToString())
            throw e
        }
    }
    constructor(path: String): this(file2JsonObject(File(path)), path)
    constructor(file: File): this(file2JsonObject(file), file.absolutePath)
    constructor(jsonContent: String, _filePath: String, pyInteraction: PyInteraction):
            this(Json.parseToJsonElement(jsonContent).jsonObject, _filePath, pyInteraction)
    fun reactionPath() = ReactionPath(this)
    fun loadReactionPath(info: String): ReactionPath{
        val r = ReactionPath(this)
        info.split('\t').forEach { expr ->
            val fr = reactions.firstOrNull{ it.expressionNoT == expr }
            if(fr != null){
                r.addReaction(fr, true)
            }else{
                val rr = reactions.firstOrNull{ it.expressionReverseNoT == expr }
                if(rr != null) r.addReaction(rr, false)
            }
        }
        return r
    }

    fun saveData() {
        logger?.info("Saving.")
        var i = 0
        while (File("${dataFile.absolutePath}_$i").exists()) i++
        val tempFile = File("${dataFile.absolutePath}_$i")
        tempFile.createNewFile()
        try {
            logger?.fine("Write data to $tempFile.")
            val os = tempFile.outputStream()
            try{
                NoCloseOutputStream(os).use { output ->
                    if(zip){
                        DataOutputStream(output).use{ it.writeUTF("MKMCoreDataZipped") }
                        DataOutputStream(GZIPOutputStream(output)).use{
                            mapper.outputData(it)
                        }
                    }else{
                        DataOutputStream(output).use{
                            it.writeUTF("MKMCoreData")
                            mapper.outputData(it)
                        }
                    }
                }
            }catch (e: Exception){
                logger?.severe("Error while saving!")
                logger?.warning(e.stackTraceToString())
                pi.info.transfer("info_save_failed")
            }finally {
                os.close()
            }
            var moveFail = dataFile.exists()
            if(moveFail) moveFail = !dataFile.delete()
            if(moveFail){
                logger?.severe("Failed deleting existing datafile $dataFile! The data are saved to $tempFile")
            }else{
                logger?.fine("Moving temporary file to $dataFile.")
                tempFile.renameTo(dataFile)
            }
            logger?.info("Saving complete.")
            pi.info.transfer("info_save_complete")
        } catch (e: Exception) {
            logger?.severe("Error while saving!")
            logger?.warning(e.stackTraceToString())
            pi.info.transfer("info_save_failed")
        }
    }
    fun loadData() {
        try {
            pi.info.transfer("info_loading")
            val `is` = dataFile.inputStream()
            try{
                NoCloseInputStream(`is`).use{ input ->
                    when(DataInputStream(input).use{ it.readUTF() }){
                        "MKMCoreData" -> DataInputStream(input).use{ mapper.inputData(it) }
                        "MKMCoreDataZipped" -> DataInputStream(GZIPInputStream(input)).use{ mapper.inputData(it) }
                    }
                }
                pi.info.transfer("info_load_complete")
            }catch(e: Exception){
                logger?.warning(e.stackTraceToString())
            }finally {
                `is`.close()
            }
        } catch (e: Exception) {
            logger?.warning(e.stackTraceToString())
        }
    }
    fun calcVacancy(p: PointInfo): Map<String, Double>?{
        val cvg = p.coverage ?: return null
        val map = mutableMapOf<String, Double>()
        sites.forEach{ s ->
            var c = s.total.toBigDecimal()
            adsorbates.forEachIndexed { i, a ->
                if(s in a.site) c = c.minus(cvg[i].times(a.site[s]!!))
            }
            map[s.name] = c.toDouble()
        }
        return map
    }
    fun getReactionPath(expr: String): List<ReactionPath>{
        try{
            val r = Reaction(expr, species, defaultSite, -1,this, false)
            val mapIS = mutableMapOf<Gas, Int>()
            val mapFS = mutableMapOf<Gas, Int>()
            r.initialState.forEach{ (s, i) -> mapIS[s as Gas] = i }
            r.finalState.forEach{ (s, i) -> mapFS[s as Gas] = i }
            return getReactionPath(mapIS, mapFS)
        }catch(e: Exception){
            logger?.severe("Error while getting reaction path!")
            logger?.severe(e.stackTraceToString())
            pi.error.transfer(version, e.javaClass.toString(), e.message, (e as? MKMSetupException)?.type, e.stackTraceToString())
            throw e
        }
    }
    fun getReactionPath(reactants:Map<Gas, Int>, product:Map<Gas, Int>): List<ReactionPath>{
        val iSide = mutableListOf<ReactionPath>()
        val fSide = mutableListOf<ReactionPath>()
        fun doAdsorption(direction: Boolean, set: Map<Gas, Int>, r: Reaction, target: MutableList<Pair<Reaction, Boolean>>){
            val state = if(direction) r.initialState else r.finalState
            val speciesNotSite = state.filter { it.first is SolidSite }
            if(speciesNotSite.size == 1 && set.containsKey(speciesNotSite[0].first)){
                val gas = speciesNotSite[0].first
                if(set[gas]!! % speciesNotSite[0].second != 0)
                    throw Exception("There are ${set[gas]} ${gas.name} in reactants, but they adsorb ${speciesNotSite[0].second} at a time.")
                val times = set[gas]!! / speciesNotSite[0].second
                repeat(times){ target.add(Pair(r, direction)) }
            }
        }
        val ads = mutableListOf<Pair<Reaction, Boolean>>()
        val dsp = mutableListOf<Pair<Reaction, Boolean>>()
        val gasReactions = mutableListOf<Reaction>()
        val solidReactions = mutableListOf<Reaction>()
        reactions.forEach {
            var hasGas = false
            for(sp in it.initialState){
                if(sp.first is Gas){
                    hasGas = true
                    break
                }
            }
            if(!hasGas){
                for(sp in it.finalState){
                    if(sp.first is Gas){
                        hasGas = true
                        break
                    }
                }
            }
            if(hasGas) gasReactions.add(it) else solidReactions.add(it)
        }
        gasReactions.forEach{ r ->
            doAdsorption(true, reactants, r, ads)
            doAdsorption(false, reactants, r, ads)
            doAdsorption(true, product, r, dsp)
            doAdsorption(false, product, r, dsp)
        }
        val iPath = ReactionPath(this)
        ads.forEach { iPath.addReaction(it.first, it.second) }
        iSide.add(iPath)
        val fPath = ReactionPath(this)
        dsp.forEach { fPath.addReaction(it.first, it.second) }
        fSide.add(fPath)
        val paths = hashSetOf<ReactionPath>()
        fun containInState(direction: Boolean, set: Map<Species, Int>, r: Reaction): Boolean{
            val state = (if(direction) r.initialState else r.finalState).filter { it.first !is SolidSite }
            state.forEach { (sp, i) -> if(!set.containsKey(sp) || set[sp]!! < i) return false }
            return true
        }
        fun step(side: MutableList<ReactionPath>): Boolean{
            val newAds = mutableListOf<ReactionPath>()
            side.forEach { path ->
                val rList = path.reactions
                val cpn = path.totalFS
                val rSet = rList.map{p -> p.first}
                reactions.forEach { r ->
                    var bf = false
                    var br = false
                    if(r in rSet){
                        if(rList[rSet.indexOf(r)].second){
                            bf = containInState(true, cpn, r)
                        }else{
                            br = containInState(false, cpn, r)
                        }
                    }else{
                        bf = containInState(true, cpn, r)
                        br = containInState(false, cpn, r)
                    }
                    if(bf){newAds.add(path.clone().addReaction(r, true))}
                    if(br){newAds.add(path.clone().addReaction(r, false))}
                }
            }
            val set = mutableListOf<ReactionPath>()
            newAds.forEach {
                val result = it.totalFS
                val eq = mutableListOf<ReactionPath>()
                set.forEach { p ->
                    if(p.totalFS == result)
                        eq.add(p)
                }
                if(eq.isNotEmpty()){
                    var repeat = false
                    val rMap = it.reactionMap
                    for(p in eq){
                        if(p.reactionMap == rMap){
                            repeat = true
                            break
                        }
                    }
                    if(!repeat) set.add(it)
                }else{
                    set.add(it)
                }
            }
            if(set.isEmpty()) return true
            side.clear()
            side.addAll(set)
            return false
        }
        fun addPath(ip: ReactionPath, fp: ReactionPath){
            if(ip.totalFS != fp.totalFS) return
            fp.reactions.forEach { (rf, bf) ->
                ip.reactions.forEach{ (ri, bi) ->
                    if(rf == ri && bf == bi) return
                }
            }
            val path = ip.clone()
            for(i in fp.reactions.size-1 downTo  0){
                path.addReaction(fp.reactions[i].first, !fp.reactions[i].second)
            }
            paths.add(path)
        }
        while(true){
            if(step(iSide)) break
            iSide.toList().forEach { ip ->
                fSide.toList().forEach { fp ->
                    addPath(ip, fp)
                }
            }
            if(paths.isNotEmpty()) break
            if(step(fSide)) break
            iSide.toList().forEach { ip ->
                fSide.toList().forEach { fp ->
                    addPath(ip, fp)
                }
            }
            if(paths.isNotEmpty()) break
        }
        return paths.toList()
    }
    fun getSpeciesSelectivityWeight(selection: Boolean): Map<String, Int>?{
        return if(selection){
            if(selectivityAtom.isNotEmpty())
                species.mapValues{ (_, sp) ->
                    if(sp is MoleculeSpecies){
                        val c = sp.composition
                        selectivityAtom.sumOf { c[it] ?: 0 }
                    }else{
                        0
                    }
                }
            else null
        }else{
            species.mapValues{ (_, sp) ->
                if(sp is MoleculeSpecies){
                    sp.composition.values.sum()
                }else{
                    0
                }
            }
        }
    }
    fun closeLogger(){
        if(logger != null){
            logger?.handlers?.forEach { it.close() }
        }
    }

    private fun createPoint(gp: GridPoint) = createPoint(mapper.mapPoint(gp), gp)
    private fun createPoint(mp: MapPoint, gp: GridPoint? = null): PointInfo{
        val thermo = if(gp != null) getThermo(gp) else getThermo(mp)
        val p = PointInfo(this, mp, gp, thermo, EnergyList(this))
        scaler.buildFreeEnergyList(p)
        return p
    }
    /**
     * Input the descriptor values, return the temperature and pressure in a pair.
     */
    private fun getThermo(gp: GridPoint): Thermo {
        val t = thermo.copy()
        mapper.descriptors.forEachIndexed{ i, d ->
            val v = d[gp[i]].toDouble()
            if(d.descriptor is ThermoDescriptor){
                t[d.descriptor.symbol] = v
                when(d.descriptor){
                    Temperature -> t.t = v
                    Pressure -> t.p = v
                    Voltage -> t.u = v
                }
            }
            when(d.descriptor){
                is ThermoDescriptor -> t[d.descriptor.symbol] = d[gp[i]].toDouble()
                else -> t[d.descriptor.descriptor] = d[gp[i]].toDouble()
            }
            t[d.descriptor.descriptor] = v
        }
        return t
    }
    private fun getThermo(mp: MapPoint): Thermo {
        val t = thermo.copy()
        mapper.descriptors.forEachIndexed{ i, d ->
            when(d.descriptor){
                is ThermoDescriptor -> t[d.descriptor.symbol] = d[mp[i]].toDouble()
                else -> t[d.descriptor.descriptor] = d[mp[i]].toDouble()
            }
        }
        return t
    }
    fun getPoint(gp: GridPoint) = gridPointMap[gp] ?: throw Exception("$gp is out of range!")

    fun getPoint(mp: MapPoint): PointInfo{
        var p = pointMap[mp]
        if(p != null) return p
        p = createPoint(mp, null)
        //pointMap[mp] = p
        return p
    }
    fun gridPoints() = gridPointMap.values
    fun getItem(key: String): List<String>{
        return when(key){
            "coverage" -> adsorbates.map{ it.name }
            "TOF" -> gases.map{ it.name }
            "selectivity" -> selectivityMap!!.map { gases[it.first].name }
            "pressure" -> gases.map{ it.name }
            "current" -> listOf("current")
            "current density" -> listOf("current density")
            "custom" -> customPlot.keys.toList()
            else -> throw Exception("$key is a not supported plot type.")
        }
    }
    fun getPointData(p: PointInfo, key: String): DoubleArray?{
        return when(key){
            "coverage" -> p.coverage?.toDoubleArray()
            "TOF" -> p.tof
            "selectivity" -> getSelectivity(p.tof)
            "pressure" -> p.pressure?.toDoubleArray()
            "current" -> p.current?.let { doubleArrayOf(it) }
            "current density" -> p.current?.let { doubleArrayOf(it * p.thermo["siteDensity"]!! * F * 0.1) }
            "custom" -> {
                val values = p.getValues()
                return customPlot.values.map { it.arithmetic(values).toDouble() }.toDoubleArray()
            }
            else -> throw Exception("$key is a not supported plot type.")
        }
    }
    fun getSelectivity(tof: DoubleArray?): DoubleArray?{
        if(tof == null) return null
        if(selectivityMap == null) return null
        val rate = selectivityMap.map{ (index, num) -> if(tof[index] > 0) tof[index] * num else 0.0 }
        val total = rate.sum()
        return DoubleArray(rate.size){ rate[it] / total }
    }
    class NoCloseOutputStream(out: OutputStream) : FilterOutputStream(out) {
        override fun close() {}
    }
    class NoCloseInputStream(`in`: InputStream) : FilterInputStream(`in`) {
        override fun close() {}
    }
}

