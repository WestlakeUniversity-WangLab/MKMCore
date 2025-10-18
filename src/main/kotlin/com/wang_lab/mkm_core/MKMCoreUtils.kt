package com.wang_lab.mkm_core

import Jama.Matrix
import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.algebra.*
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.div
import com.wang_lab.mkm_core.algebra.big_decimal_math.minus
import com.wang_lab.mkm_core.algebra.big_decimal_math.plus
import com.wang_lab.mkm_core.algebra.big_decimal_math.times
import com.wang_lab.mkm_core.algebra.expr.parseExpression
import com.wang_lab.mkm_core.algebra.number_math.plus
import com.wang_lab.mkm_core.components.ComponentsLoader
import com.wang_lab.mkm_core.exception.MKMSetupException
import com.wang_lab.mkm_core.misc.Constraint
import com.wang_lab.mkm_core.misc.Item
import com.wang_lab.mkm_core.reaction.Reaction
import com.wang_lab.mkm_core.exception.ReactionDisabledException
import com.wang_lab.mkm_core.point.GridPoint
import com.wang_lab.mkm_core.species.Species
import java.awt.Point
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern
import javax.swing.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

val example: JsonObject by lazy {
    buildJsonObject{
        put("input_file", JsonPrimitive("example.txt"))
        put("parser", JsonPrimitive("NullParser"))
        put("scaler", JsonPrimitive("ThermodynamicScaler"))
        put("solver", JsonPrimitive("NullSolver"))
        put("descriptor_names", buildJsonArray {
            add(JsonPrimitive("temperature"))
            add(JsonPrimitive("logPressure"))
        })
        put("descriptor_ranges", buildJsonArray {
            add(buildJsonArray{
                add(JsonPrimitive(510))
                add(JsonPrimitive(800))
            })
            add(buildJsonArray{
                add(JsonPrimitive(-1))
                add(JsonPrimitive(3))
            })
        })
        put("resolution", buildJsonArray {
            add(JsonPrimitive(2))
            add(JsonPrimitive(2))
        })
        put("species_definitions", buildJsonObject {})
        put("rxn_expressions", buildJsonArray {})
        put("surface_names", buildJsonArray {
            add(JsonPrimitive("M"))
        })
        put("gas_thermo_mode", JsonPrimitive("frozen_gas"))
        put("adsorbate_thermo_mode", JsonPrimitive("frozen_adsorbate"))
    }
}
fun <T, R> Iterable<T>.toMap(transform: (T) -> R): MutableMap<T, R>{
    val map = mutableMapOf<T, R>()
    this.forEach{ item -> map[item] = transform(item) }
    return map
}
fun <T, R> Iterable<T>.toMapNotNull(transform: (T) -> R?): Map<T, R>{
    val map = mutableMapOf<T, R>()
    this.forEach{ item ->
        val r = transform(item)
        if(r != null) map[item] = r
    }
    return map
}
data class Quaternion<out A, out B, out C, out D> (val a: A, val b: B, val c: C, val d: D)
fun <T, R> Iterable<T>.toMapIndexed(transform: (Int, T) -> R): Map<T, R>{
    val map = mutableMapOf<T, R>()
    this.forEachIndexed{ index, item -> map[item] = transform(index, item) }
    return map
}
fun <T> Iterable<Iterable<T>>.forEachPointIndexed(action: (Int, Int, T) -> Unit ){
    this.forEachIndexed { i, c1 -> c1.forEachIndexed { j, c2 -> action(i, j, c2) } }
}
fun <T> Array<Array<T>>.forEachGridPoint(action: (GridPoint, T) -> Unit ){
    this.forEachIndexed { i, c1 -> c1.forEachIndexed { j, c2 -> action(GridPoint(i, j), c2) } }
}
inline fun <T> Iterable<T>.indexedSumOf(selector: (Int, T) -> Double): Double {
    var sum = 0.0
    this.forEachIndexed{ i, element ->
        sum += selector(i, element)
    }
    return sum
}
inline fun <T> Collection<T>.indexedSumOfBD(selector: (Int, T) -> BigDecimal): BigDecimal {
    var sum: BigDecimal = BigDecimal.ZERO
    this.forEachIndexed{ i, element ->
        sum = sum.plus(selector(i, element))
    }
    return sum
}
inline fun <T> Array<T>.indexedSumOf(selector: (Int, T) -> Number): Number {
    var sum: Number = 0
    this.forEachIndexed{ i, element ->
        sum += selector(i, element)
    }
    return sum
}


operator fun DoubleArray.times(b: Double) = DoubleArray(this.size){i -> this[i] * b}
fun DoubleArray.product(): Double {
    var p = 1.0
    this.forEach{ d -> p *= d }
    return p
}

fun Collection<Double>.product(): Double {
    var p = 1.0
    this.forEach{ d -> p *= d }
    return p
}

fun DoubleArray.averageOf(selector: (Double) -> Double) = this.sumOf{ selector(it) } / this.size
fun DoubleArray.indexedSumOf(selector: (Int, Double) -> Double): Double{
    var sum = 0.0
    forEachIndexed{ i, d -> sum += selector(i, d) }
    return sum
}
fun linearRegression(x: DoubleArray, y: DoubleArray, slope: Double? = null, r: Boolean = false): Triple<Double, Double, Double>{
    val xM = x.average()
    val yM = y.average()
    val m = (x.mapIndexed{ i, xi -> xi * y[i] }.average() - xM * yM) /
            (x.averageOf { xi -> xi.pow(2) } - xM.pow(2))
    val b = yM - xM * m
    return if(r){
        val mae = y.mapIndexed{ i, yi -> abs(yi - m * x[i] - b) }.average()
        Triple(m, b, mae)
    }else{
        Triple(m, b, 0.0)
    }
}
fun linearRegression(x: BDVector, y: BDVector): Pair<BigDecimal, BigDecimal>{
    val xM = x.average()
    val yM = y.average()
    val m = (x.transformIndexed{ i, xi -> xi.times(y[i]) }.average().minus(xM.times(yM))).div(
            (x.transform { xi -> xi.pow(2) }.average().minus(xM.times(xM))))
    val b = yM.minus(xM.times(m))
    return Pair(m, b)
}
fun linearRegressionRSquare(x: BDVector, y: BDVector): Triple<BigDecimal, BigDecimal, BigDecimal>{
    val xM = x.average()
    val yM = y.average()
    val m = (x.transformIndexed{ i, xi -> xi.times(y[i]) }.average().minus(xM.times(yM))).div(
        (x.transform { xi -> xi.pow(2) }.average().minus(xM.times(xM))))
    val b = yM.minus(xM.times(m))
    val r2 = BigDecimal.ONE - y.zip(x).sumOf { (yi, xi) -> (yi - (m * xi + b)).pow(2) }
    return Triple(m, b, r2)
}
fun linearRegressionMultiple(x: List<DoubleArray>, y: DoubleArray, r: Boolean = false, cst: List<Constraint>? = null): Pair<DoubleArray, Double>{
    val n = x.size
    val m = y.size
    cst?.forEachIndexed { i, c ->
        if(c.isConst){
            if(i == n){
                throw MKMSetupException("constraint", "Fixed constant is not supported yet.")
            }else{
                val x2 = x.mapIndexedNotNull{ j, it -> if(j == i) null else it }
                val y2 = DoubleArray(m){ y[it] - x[i][it] * c.value!! }
                val cst2 = cst.mapIndexedNotNull{ j, it -> if(j == i) null else it }
                val (co, cs) = linearRegressionMultiple(x2, y2, r, cst2)
                val co2 = co.toMutableList()
                co2.add(i, c.value!!)
                return Pair(co2.toDoubleArray(), cs)
            }
        }
    }
    val mA = Matrix(y.size, n + 1)
    x.forEachIndexed{ i, list ->
        list.forEachIndexed{ j, x ->
            mA[j, i] = x
        }
    }
    for(i in 0 until mA.rowDimension) mA[i, mA.columnDimension - 1] = 1.0
    val mB = Matrix(y.size, 1)
    for(i in y.indices) mB[i, 0] = y[i]
    val tX = mA.transpose()
    val x0 = (tX * mA).inverse()!! * tX * mB
    if(cst != null){
        fun j(x1: Matrix, a: Matrix, b: Matrix) = x1.transpose() * a.transpose() * a * x1 - b.transpose() * a * x1 * 2.0
        fun findMin(q: Int): Double{
            x0[q, 0] = 0.0
            val v = mA * x0
            var n1 = 0.0
            var n2 = 0.0
            var den = 0.0
            val aq = mA.getMatrix(0, mA.rowDimension-1, q, q)
            for(k in 0 until n){
                n2 += (v.transpose() * aq)[0, 0]
                n1 += (mB.transpose() * aq)[0, 0]
                den += (aq.transpose() * aq)[0, 0]
            }
            return cst[q].clip((n1 - n2) / den)
        }
        var ni = 0
        var residual = 1e99
        while(ni < 10000 && residual > 1e-10){
            ni ++
            val f = j(x0, mA, mB)
            for(j in 0 .. n) x0[j, 0] = findMin(j)
            val nf = j(x0, mA, mB)
            val df = f - nf
            residual = df.norm2()
        }
        if(residual > 1e-10) throw Exception("Constrained relaxation did not converge (residual = $residual).")
    }
    val result = DoubleArray(n + 1){ x0[it, 0] }
    return if(r){
        val mae = y.mapIndexed{ i, yi -> abs(yi - result.indexedSumOf{ j, d -> mA[i, j] * d }) }.average()
        Pair(result, mae)
    }else{
        Pair(result, 0.0)
    }
}
val JsonPrimitive.doubleOrNaN: Double
    get() {
        if(this.isString && this.toString().equals("NaN", true)) return Double.NaN
        return this.double
    }
fun file2JsonObject(jmkm: File)= Json.parseToJsonElement(jmkm.readLines().joinToString("")).jsonObject

private fun loadModule(item: Item, sources: List<String>, loadAction: (String) -> Int, urlPath: Class<*>){
    for(m in sources){
        val url = urlPath.getResource(m)
        if(url != null){
            val n = loadAction(url.readText())
            logger?.fine("Loaded ${item.n(n)} from ${url.path}.")
        }
        val file = File(m)
        if(file.exists()) {
            val n = loadAction(file.readText())
            logger?.fine("Loaded ${item.n(n)} from ${file.absolutePath}.")
        }
        for(p in paths){
            val file1 = File(p, m)
            if(file1.exists()){
                val n = loadAction(file1.readText())
                logger?.fine("Loaded ${item.n(n)} from ${file.absolutePath}.")
            }
        }
    }
}

fun loadModule(item: Item, sources: List<String>, collection: Collection<*>, loadAction: (String) -> Int, urlPath: Class<*> = ReactionModel::class.java){
    loadModule(item, sources, loadAction, urlPath)
    if(collection.isEmpty()) throw Exception("No $item loaded!")
    logger?.info("Loaded ${item.n(collection.size)}.")
}
fun loadModule(item: Item, sources: List<String>, map: Map<*, *>, loadAction: (String) -> Int, urlPath: Class<*> = ReactionModel::class.java){
    loadModule(item, sources, loadAction, urlPath)
    if(map.isEmpty()) throw Exception("No $item loaded!")
    logger?.info("Loaded ${item.n(map.size)}.")
}
fun <R> DoubleArray.mapD(transform: (Double) -> Double): DoubleArray {
    return DoubleArray(size){ transform(this[it]) }
}

fun Collection<BigDecimal>.sum(): BigDecimal {
    var sum = BigDecimal.ZERO
    for(bi in this) sum = (sum + bi)
    return sum
}
fun <R, T> forEachZipped(c1: Iterable<R>, c2: Iterable<T>, action: (R, T) -> Unit){
    val i1 = c1.iterator()
    val i2 = c2.iterator()
    while(i1.hasNext() && i2.hasNext()) action(i1.next(), i2.next())
    if(i1.hasNext() || i2.hasNext()) throw Exception("Can not zip tho collections with different size!")
}
fun <A, B, C> mapZipped(c1: Collection<A>, c2: Collection<B>, action: (A, B) -> C): List<C>{
    if(c1.size != c2.size) throw Exception("Can not zip tho collections with different size! (${c1.size} and ${c2.size})")
    val i1 = c1.iterator()
    val i2 = c2.iterator()
    return List(c1.size){ action(i1.next(), i2.next()) }
}
fun <R, S, T> forEachZipped(c1: Collection<R>, c2: Collection<S>, c3: Collection<T>, action: (R, S, T) -> Unit){
    if(c1.size != c2.size) throw Exception("Can not zip tho collections with different size! (${c1.size} and ${c2.size})")
    val i1 = c1.iterator()
    val i2 = c2.iterator()
    val i3 = c3.iterator()
    while(i1.hasNext()) action(i1.next(), i2.next(), i3.next())
}

fun <R> List<R>.mapBDV( t: (R) -> BigDecimal) = BDVector(size){ t(this[it]) }
fun <R> List<R>.mapBDVIndexed( t: (Int, R) -> BigDecimal) = BDVector(size){ t(it, this[it]) }

fun List<BigDecimal>.maxInfo(): Pair<Int, BigDecimal>{
    var i = 0
    var b = first()
    for(j in 1 until size){
        if(this[j] > b){
            i = j
            b = this[j]
        }
    }
    return Pair(i, b)
}
fun List<BigDecimal>.minInfo(): Pair<Int, BigDecimal>{
    var i = 0
    var b = first()
    for(j in 1 until size){
        if(this[j] < b){
            i = j
            b = this[j]
        }
    }
    return Pair(i, b)
}

fun <T: Species> List<T>.real(): List<T> = filter{ it.notVirtual }
fun <T: Species> List<T>.energetic(): List<T> = filter{ it.includeEnergy }

private const val JEString = "string"
private const val JEBool = "boolean"
private const val JEInt = "integer"
private const val JEDouble = "double"
private const val JENull = "json null"
private const val JEArray = "json array"
private const val JEObject = "json object"
private const val JE_ = "null (not exist)"

/**
 * An encapsulated function to read the content of a json element, and decide what to do based on its type.
 * If the element does not match any of the defined types, an error will be thrown, and remind you which types are acceptable.
 * @param je: The input json element.
 * @param name: The name of this element to show in the exception.
 * @param s: To do if the element is a string.
 * @param b: To do if the element is a boolean.
 * @param d: To do if the element is a double or an integer.
 * @param i: To do if the element is an integer.
 * @param n: To do if the element is a json null.
 * @param a: To do if the element is a json array.
 * @param o: To do if the element is a json object.
 * @param v: To do if the element is null (does not exist).
 */
fun <T> switchJsonElement(
    je: JsonElement?,
    name: String,
    s: ((String) -> T)? = null,
    b: ((Boolean) -> T)? = null,
    d: ((Double) -> T)? = null,
    i: ((Int) -> T)? = null,
    n: (() -> T)? = null,
    a: ((JsonArray) -> T)? = null,
    o: ((JsonObject) -> T)? = null,
    v: (() -> T)? = null,
): T{
    fun errorInfo(type: String?): String{
        val sb = mutableListOf<String>()
        if(s != null) sb.add(JEString)
        if(b != null) sb.add(JEBool)
        if(i != null) sb.add(JEInt)
        if(d != null) sb.add(JEDouble)
        if(n != null) sb.add(JENull)
        if(a != null) sb.add(JEArray)
        if(o != null) sb.add(JEObject)
        if(v != null) sb.add(JE_)
        val types = if(sb.size > 1) "one of these: ${sb.joinToString()}" else "a ${sb[0]}"
        return if(type == null) "$name does not exist, it should be $types."
        else "The type of $name is $type, but should be $types."
    }
    return when(je){
        null -> if(v != null) v() else throw Exception(errorInfo(JE_))
        is JsonArray -> if(a != null) a(je) else throw Exception(errorInfo(JEArray))
        is JsonObject -> if(o != null) o(je) else throw Exception(errorInfo(JEObject))
        is JsonNull -> if(n != null) n() else throw Exception(errorInfo(JENull))
        is JsonPrimitive -> {
            if(je.booleanOrNull != null && b != null) b(je.boolean)
            else if(je.doubleOrNull != null && d != null) d(je.double)
            else if(je.intOrNull != null && i != null) i(je.int)
            else if(je.contentOrNull != null && s != null) s(je.content)
            else throw if(je.booleanOrNull != null) Exception(errorInfo(JEBool))
            else if(je.doubleOrNull != null) Exception(errorInfo(JEDouble))
            else if(je.intOrNull != null) Exception(errorInfo(JEInt))
            else if(je.contentOrNull != null) Exception(errorInfo(JEString))
            else Exception("$name is an unsupported json primitive type: $je")
        }
    }
}
operator fun DoubleArray.minus(b: DoubleArray): DoubleArray {
    if(size != b.size) throw Exception("Different size!")
    return DoubleArray(size){ this[it] - b[it] }
}

operator fun DoubleArray.div(scalar: Double): DoubleArray {
    return DoubleArray(size){ this[it] / scalar }
}
fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) {
        val temp = y
        y = x % y
        x = temp
    }
    return x
}

operator fun IntArray.times(b: IntArray): IntArray {
    if(size != b.size) throw Exception("Different size!")
    return IntArray(size){ this[it] * b[it] }
}
operator fun IntArray.minus(b: IntArray): IntArray {
    if(size != b.size) throw Exception("Different size!")
    return IntArray(size){ this[it] - b[it] }
}

fun IntArray.reduction(): IntArray {
    if(isEmpty()) return IntArray(0)
    var gcd = first()
    for(i in 1 until size) gcd = gcd(gcd, this[i])
    if(gcd == 0) return IntArray(size)
    return IntArray(size){ this[it] / gcd }
}

fun GridPoint.isZero(): Boolean{
    forEach { if(it != 0) return false }
    return true
}

operator fun <T> Array<Array<T>>.get(pos: Point): T = this[pos.x][pos.y]
operator fun <T> Array<Array<T>>.set(pos: Point, value: T){
    this[pos.x][pos.y] = value
}
fun Double.roundTo(precision: Int) = BigDecimal(this).setScale(precision, RoundingMode.HALF_EVEN).toDouble()
@Suppress("MemberVisibilityCanBePrivate", "unused")
object MKMCoreUtils{
    @JvmStatic
    fun initializeComponents() = ComponentsLoader.initializeComponents()
    @JvmStatic
    fun getClass(name: String) = ComponentsLoader.getClass(name)

    private var privateModel: ReactionModel? = null
    val model: ReactionModel
        get() = privateModel ?: ReactionModel(example, "").apply { privateModel = this }

    @JvmStatic
    fun setDefaultSite(site: String){
        model.defaultSite = site
    }

    @JvmStatic
    fun loadSpecies(info: String){
        val sds = Json.parseToJsonElement(info).jsonObject.mapValues { e -> e.value.jsonObject}
        sds.forEach { (t, u) -> model.species[t] =
            Species.load(
                t,
                model.defaultSite,
                model.pi,
                u,
                model.defaultThermoMode,
                model.species
            ) }
    }

    @JvmStatic
    fun ia(vararg i: Int) = i.toList().toIntArray()

    @JvmStatic
    fun da(vararg i: Double) = i.toList().toDoubleArray()

    @JvmStatic
    fun isValidateReaction(r: String, defaultSite: String?): String{
        model.species.clear()
        return try{
            Reaction(r, model.species, defaultSite, 0, model, true)
            "pass"
        }catch(e: ReactionDisabledException){
            "disabled"
        }catch(e: Exception){
            e.printStackTrace()
            e.message ?: "null"
        }
    }
    @JvmStatic
    fun isValidateExpression(expr: String): String{
        return try{
            val vars = parseExpression(expr).getVariables()
            if(vars.isEmpty() || (vars.size == 1 && vars.contains("p"))) "pass" else "Only 'p' can exist in the expression as a variable."
        }catch (e: Exception){
            e.message ?: "null"
        }
    }
    @JvmStatic
    fun getReaction(s: String?, defaultSite: String?): Reaction?{
        model.species.clear()
        return try{
            Reaction(s ?: "", model.species, defaultSite, 0, model, false)
        }catch(e: Exception){
            e.printStackTrace()
            null
        }
    }
    private val standardSiteFormat = Pattern.compile("[a-z]+")
    @JvmStatic
    fun isValidSite(site: String) = standardSiteFormat.matcher(site).matches()
    @JvmStatic
    fun resetModel(){
        logger = null
        privateModel = ReactionModel(example, "")
    }
    @JvmStatic
    fun speciesType(name: String): String?{
        val matcher = Species.speciesFormat.matcher(name)
        if(matcher.matches()){
            return when(matcher.group("site")!!){
                "g" -> "gas"
                "l" -> "liquid"
                "aq" -> "aqua"
                else -> "solid"
            }
        }
        val sMatcher = Species.siteFormat.matcher(name)
        return if(sMatcher.matches()) "site" else null
    }
    @JvmStatic
    fun checkConstraint(s: String?): Boolean{
        return try{
            Constraint.stringToConstraint(s)
            true
        }catch (_: Exception){
            false
        }
    }
}
fun availableProcessors() = Runtime.getRuntime().availableProcessors()
