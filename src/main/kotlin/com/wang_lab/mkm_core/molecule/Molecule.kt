package com.wang_lab.mkm_core.molecule

import Jama.Matrix
import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.algebra.number_math.nSumOf
import com.wang_lab.mkm_core.misc.Item
import com.wang_lab.mkm_core.loadModule
import com.wang_lab.mkm_core.molecule.Atom.Companion.getAtom
import com.wang_lab.mkm_core.molecule.Operator.Companion.inversion
import com.wang_lab.mkm_core.molecule.Operator.Companion.mirror
import com.wang_lab.mkm_core.molecule.Operator.Companion.reflection
import com.wang_lab.mkm_core.molecule.Operator.Companion.rotation
import java.io.File
import java.util.regex.Pattern
import kotlin.math.*


class Molecule(
    var name: String,
    _coordinates : List<Pair<Atom, Vector3d>>,
    var symbols: List<String> = listOf(name),
    _pointGroup: String? = null,
    _geometry: MoleculeGeometry? = null,
    _symmetry: Int? = null,
    var spin: Double = 0.0
) {
    val mass = _coordinates.nSumOf { p -> p.first.mass }.toDouble()
    val coordinates: List<Pair<Atom, Vector3d>>
    val pointGroup: String
    var geometry: MoleculeGeometry
    var symmetry: Int
    init{
        val massCenter = _coordinates.sumOfCoordinate { (a, p) -> p * a.mass } / mass
        coordinates = _coordinates.map{ (a, p) -> Pair(a, p - massCenter) }
        if(_pointGroup == null || _geometry == null){
            val gp = getPointGroup(coordinates)
            geometry = gp.first
            pointGroup = gp.second
        }else{
            geometry = _geometry
            pointGroup = _pointGroup
        }
        symmetry = _symmetry ?: getSymmetry(pointGroup)
    }
    val momentsOfInertia: DoubleArray by lazy {
        val i11 = coordinates.sumOf { (a, p) -> a.mass * (p.y * p.y + p.z * p.z) }
        val i22 = coordinates.sumOf { (a, p) -> a.mass * (p.x * p.x + p.z * p.z) }
        val i33 = coordinates.sumOf { (a, p) -> a.mass * (p.x * p.x + p.y * p.y) }
        val i12 = coordinates.sumOf { (a, p) -> -a.mass * p.x * p.y }
        val i13 = coordinates.sumOf { (a, p) -> -a.mass * p.x * p.z }
        val i23 = coordinates.sumOf { (a, p) -> -a.mass * p.y * p.z }
        val i = Matrix(arrayOf(
            doubleArrayOf(i11, i12, i13),
            doubleArrayOf(i12, i22, i23),
            doubleArrayOf(i13, i23, i33)
        ))
        i.eig().realEigenvalues
    }


    companion object{
        val pointGroupPattern = Pattern.compile("(?<group>[CDSTOI])(?<fold>\\d*|\\*)(?<addon>[vhdis]?)")!!
        private val moleculesMap = mutableMapOf<String, Molecule>()
        val molecules = mutableSetOf<Molecule>()
        val moleculeSources = mutableListOf("Molecules.json")
        private fun initializeMolecules() = loadModule(Item.withS("molecule"), moleculeSources, molecules, { loadMolecules(it) })
        fun getMolecule(symbol: String): Molecule?{
            if(symbol !in moleculesMap) initializeMolecules()
            return moleculesMap[symbol]
        }
        fun loadMolecules(content: String): Int{
            val ja = Json.parseToJsonElement(content).jsonArray
            ja.forEach {
                val m = Molecule(it.jsonObject)
                m.symbols.forEach { s ->
                    molecules.add(m)
                    moleculesMap[s] = m
                }
            }
            return ja.size
        }
        fun loadMoleculeFormFile(path: String): Molecule {
            val file = File(path)
            val content = file.readLines()
            return if(file.name == "POSCAR" || file.name == "CONTCAR" || file.name.endsWith(".vasp")){
                throw Exception()
            }else if(file.name.endsWith(".gjf")){
                var s = 0
                var spin = Double.NaN
                //var charge = Double.NaN
                var name = "Title Card Required"
                val coordinates = mutableListOf<Pair<Atom, Vector3d>>()
                for(line in content){
                    when(s){
                        0 -> if(line.isBlank()) s++
                        1 -> if(line.isBlank()) s++ else name = line
                        2 -> {
                            val values = line.split(" ").mapNotNull { p -> if(p.isBlank()) null else p.toDouble() }
                            //charge = values[0]
                            spin = (values[1] -1) / 2
                            s ++
                        }
                        3 ->if(line.isBlank()) {
                            s++
                        }else{
                            val values = line.split(" ").filter { it.isNotBlank() }
                            coordinates.add(Pair(getAtom(values[0]), Vector3d(values[1].toDouble(), values[2].toDouble(), values[3].toDouble())))
                        }
                        4 -> break
                    }
                }
                Molecule(name, coordinates, listOf(name), null, null, null, spin)
            }else{
                throw Exception()
            }
        }
        fun getPointGroup(coordinates: List<Pair<Atom, Vector3d>>, tolerance: Double = 1e-3): Pair<MoleculeGeometry, String>{
            if(coordinates.isEmpty()) throw Exception("Input structures has no atoms!")
            if(coordinates.size == 1) return Pair(MoleculeGeometry.Point, "")
            var geometry = MoleculeGeometry.Linear
            var l = 0
            val pointGroup: String
            while(coordinates[l].second.length() < tolerance) l ++
            val vector = coordinates[l].second

            fun validateOperator(op: Operator, tolerance: Double): Boolean{
                val atoms = coordinates.map{ c -> Pair(c.first, op.operate(c.second)) }.toMutableList()
                coordinates.forEach { c ->
                    val d = atoms.firstOrNull { it.first == c.first && (it.second - c.second).length() < 2 * tolerance }
                    if(d == null) return false
                    else atoms.remove(d)
                }
                return true
            }

            for(i in coordinates.indices){
                if(i == l) continue
                if(vector.vectorProduct(coordinates[i].second).length() >
                    vector.length() * coordinates[i].second.length() * tolerance * 2){
                    geometry = MoleculeGeometry.Nonlinear
                    break
                }
            }
            if(geometry == MoleculeGeometry.Linear){
                val distanceList = mutableListOf<Pair<Atom, Vector3d>>()
                coordinates.forEach { c1 ->
                    if(c1.second.length() > tolerance){
                        var sym = false
                        for(i in distanceList.indices){
                            val c2 = distanceList[i]
                            if(c1.first != c2.first) continue
                            if((c1.second + c2.second).squareLength() < sqrt(c1.second.length() * c2.second.length()) * tolerance * 2){
                                distanceList.removeAt(i)
                                sym = true
                                break
                            }
                        }
                        if(!sym) distanceList.add(c1)
                    }
                }
                pointGroup = if(distanceList.isEmpty()) "D*h" else "C*v"
            }else{
                val atomMap = mutableListOf<Triple<Atom, Double, MutableList<Vector3d>>>()
                coordinates.forEach { c ->
                    val d = c.second.length()
                    if(d > tolerance){
                        var newGroup = true
                        for(tr in atomMap){
                            if(tr.first == c.first && abs(d - tr.second) < tolerance * 2){
                                tr.third.add(c.second)
                                newGroup = false
                                break
                            }
                        }
                        if(newGroup) atomMap.add(Triple(c.first, d, mutableListOf(c.second)))
                    }
                }
                atomMap.sortBy { tr -> -tr.third.size }

                val ax = mutableListOf<Pair<Int, Vector3d>>()
                val c2 =  mutableListOf<Vector3d>()
                for(am in atomMap){
                    val a0 = am.third
                    for(i in a0.indices){
                        for(j in a0.indices){
                            if(j == i) continue
                            val v1 = a0[i] - a0[j]
                            for(k in a0.indices){
                                if(k == j) continue
                                val v2 = a0[j] - a0[k]
                                val fold = if(i == k){
                                    2
                                }else{
                                    val outerAngle = v1.angleWith(v2)
                                    round(2 * PI / outerAngle).toInt()
                                }
                                val v = if(fold > 2) v1.vectorProduct(v2) else (a0[i] + a0[j]) / 2
                                if(v.length() > 2 * tolerance){
                                    val vn = v.normal()
                                    if(validateOperator(rotation(vn, fold), tolerance)){
                                        var exist = false
                                        for(x in ax) {
                                            if(x.first == fold && ((x.second - vn).length() < tolerance || (x.second + vn).length() < tolerance)){
                                                exist = true
                                                break
                                            }
                                        }
                                        if(!exist) ax.add(Pair(fold, vn))
                                    }
                                }else{
                                    when(c2.size){
                                        0 -> c2.add(v1.normal())
                                        1 -> {
                                            val oc2 = v1.vectorProduct(c2[0])
                                            if(oc2.length() > tolerance){
                                                val vc2 = oc2.normal()
                                                if(validateOperator(rotation(vc2, 2), tolerance)){
                                                    var exist = false
                                                    for(x in ax){
                                                        if(x.first == 2 && ((x.second - vc2).length() < tolerance || (x.second + vc2).length() < tolerance)){
                                                            exist = true
                                                            break
                                                        }
                                                    }
                                                    if(!exist) ax.add(Pair(2, vc2))
                                                }
                                                c2.add(v1.normal())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val axisMap = mutableMapOf<Int, MutableList<Vector3d>>()
                ax.sortBy { a -> -a.first }
                ax.forEach{ (i, v) ->
                    var exist = false
                    axisMap.forEach { (t, u) ->
                        if(t > i && t % i == 0){
                            for(v1 in u){
                                if((v1 - v).length() < tolerance || (v1 + v).length() < tolerance){
                                    exist = true
                                    break
                                }
                            }
                        }
                    }
                    if(!exist){
                        if(i in axisMap.keys) axisMap[i]!!.add(v)
                        else axisMap[i] = mutableListOf(v)
                    }
                }
                fun checkMirror(): Boolean{
                    if(coordinates.size <= 3) return true
                    var isPlanar = true
                    var r1r2 = Vector3d(0.0, 0.0, 0.0)
                    rr@for(i in coordinates.indices){
                        for(j in i+1 until coordinates.size){
                            val r = coordinates[0].second.vectorProduct(coordinates[1].second)
                            if(r.length() > coordinates[0].second.length() * coordinates[1].second.length() * tolerance)
                                r1r2 = r
                            break@rr
                        }
                    }
                    if(r1r2.length() < tolerance) return true
                    for(i in 2 until coordinates.size){
                        if(r1r2.product(coordinates[i].second) > r1r2.length() * coordinates[i].second.length() * tolerance){
                            isPlanar = false
                            break
                        }
                    }
                    if(isPlanar) return true
                    for(am in atomMap){
                        val a0 = am.third
                        for(a in a0){
                            for(b in a0){
                                if(validateOperator(mirror((a-b).normal()), tolerance)){
                                    return true
                                }
                            }
                        }
                    }
                    return false
                }
                pointGroup = if(axisMap.isNotEmpty()){
                    val maxFold = axisMap.keys.max()
                    val foldCount = axisMap[maxFold]!!.size
                    if(maxFold > 2 && foldCount > 1){
                        if(maxFold == 5 && foldCount == 6){
                            if(axisMap[3]?.size != 10) throw Exception("There should be 10 C3 axes in I point group, but found ${axisMap[3] ?: 0}!")
                            if(axisMap[2]?.size != 15) throw Exception("There should be 15 C2 axes in I point group, but found ${axisMap[3] ?: 0}!")
                            val m = mirror((axisMap[5]!![0] + axisMap[5]!![1]).normal())
                            if(validateOperator(m, tolerance)) "Ih"
                            else "I"
                        }else if(maxFold == 4 && foldCount == 3){
                            if(axisMap[3]?.size != 4) throw Exception("There should be 4 C3 axes in O point group, but found ${axisMap[3] ?: 0}!")
                            if(axisMap[2]?.size != 6) throw Exception("There should be 6 C2 axes in O point group, but found ${axisMap[3] ?: 0}!")
                            val m = mirror(axisMap[4]!!.first().normal())
                            if(validateOperator(m, tolerance)) "Oh"
                            else "O"
                        }else if(maxFold == 3 && foldCount == 4){
                            if(axisMap[2]?.size != 3) throw Exception("There should be 3 C2 axes in T point group, but found ${axisMap[3] ?: 0}!")
                            val m = mirror(axisMap[3]!!.first().vectorProduct(axisMap[2]!!.first()).normal())
                            val m2 = mirror(axisMap[2]!!.first().normal())
                            if(validateOperator(m, tolerance)) "Td"
                            else if(validateOperator(m2, tolerance)) "Th"
                            else "T"
                        }else{
                            throw Exception("Unreasonable point group with $foldCount C$maxFold axes!")
                        }
                    }else if(maxFold > 2 || foldCount == 1){
                        if(axisMap.size == 1){
                            val o = axisMap[maxFold]!![0].normal()
                            if(maxFold % 4 != 2 && validateOperator(inversion(o, maxFold), tolerance)){
                                if(maxFold % 2 == 1) "C${maxFold}i"
                                else "S$maxFold"
                            }else if(validateOperator(mirror(o), tolerance)){
                                "C${maxFold}h"
                            }else{
                                if(checkMirror()) "C${maxFold}v"
                                else "C$maxFold"
                            }
                        }else{
                            if(axisMap.size > 2)
                                throw Exception("Unreasonable point group with ${axisMap.size} axis folds!")
                            if(!axisMap.containsKey(2))
                                throw Exception("Unreasonable point group without C2 axes!")
                            if(axisMap[2]!!.size != maxFold)
                                throw Exception("Unreasonable point group with ${axisMap[2]!!.size} C2 axis folds while highest axis is C$maxFold!")
                            val o = axisMap[maxFold]!![0].normal()
                            if(validateOperator(mirror(o), tolerance)){
                                "D${maxFold}h"
                            }else{
                                if(checkMirror()) "D${maxFold}v"
                                else "D$maxFold"
                            }
                        }
                    }else if(foldCount == 3){
                        if(
                            validateOperator(mirror(axisMap[2]!![0].normal()), tolerance) ||
                            validateOperator(mirror(axisMap[2]!![1].normal()), tolerance) ||
                            validateOperator(mirror(axisMap[2]!![2].normal()), tolerance)
                        ) "D2h"
                        else if(checkMirror()) "D2d"
                        else "D2"
                    }else{
                        throw Exception("Unreasonable point group with $maxFold C2 axes!")
                    }
                }else{
                    if(checkMirror()) "Cs"
                    else if(validateOperator(reflection, tolerance)) "Ci"
                    else "C1"
                }
            }
            return Pair(geometry, pointGroup)
        }

        private fun getSymmetry(pointGroup: String): Int{
            if(pointGroup.isEmpty()) return 1
            val gp = pointGroupPattern.matcher(pointGroup)
            if(!gp.matches()) throw Exception("Point group $pointGroup is invalid!")
            val group = gp.group("group")
            val fold = gp.group("fold")
            return if(fold == "*"){
                when(pointGroup){
                    "C*v" -> 1
                    "D*h" -> 2
                    else -> throw Exception("Point group $pointGroup is unreasonable!")
                }
            }else if(group == "T"){
                12
            }else if(group == "O"){
                24
            }else if(group == "I"){
                60
            }else if(fold.isEmpty()){
                1
            }else{
                val n = fold.toInt()
                if(group == "D") 2 * n
                else n
            }
        }
    }
    constructor(jo: JsonObject): this(
        name = jo["name"]!!.jsonPrimitive.content,
        _coordinates = jo["coordinates"]!!.jsonArray.map{
            val co = it.jsonArray
            Pair(
                getAtom(co[0].jsonPrimitive.content),
                Vector3d(
                    co[1].jsonPrimitive.double,
                    co[2].jsonPrimitive.double,
                    co[3].jsonPrimitive.double
                )
            )
        },
        _pointGroup = jo["point_group"]!!.jsonPrimitive.content,
        symbols = jo["symbols"]!!.jsonArray.map{ j -> j.jsonPrimitive.content },
        spin = jo["spin"]!!.jsonPrimitive.double
    )
    val jsonObject: JsonObject
        get() = JsonObject(
            mapOf(
                "name" to JsonPrimitive(name),
                "coordinates" to JsonArray(coordinates.map{ c ->
                    JsonArray(listOf(
                        JsonPrimitive(c.first.symbol),
                        JsonPrimitive(c.second.x),
                        JsonPrimitive(c.second.y),
                        JsonPrimitive(c.second.z),
                    ))
                }),
                "symbols" to JsonArray(symbols.map{ n -> JsonPrimitive(n) }),
                "point_group" to JsonPrimitive(pointGroup),
                "spin" to JsonPrimitive(spin)
            )
        )
}
