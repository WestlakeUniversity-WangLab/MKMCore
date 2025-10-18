package com.wang_lab.mkm_core.point

import com.wang_lab.mkm_core.*
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.bdExp
import com.wang_lab.mkm_core.algebra.big_decimal_math.div
import com.wang_lab.mkm_core.algebra.big_decimal_math.nToBigDecimal
import com.wang_lab.mkm_core.algebra.big_decimal_math.times
import com.wang_lab.mkm_core.components.ComponentsLoader
import com.wang_lab.mkm_core.constants.h_e
import com.wang_lab.mkm_core.constants.kB_e
import com.wang_lab.mkm_core.misc.EnergyList
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.misc.ThermoDescriptor
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal

class PointInfo(
    val model: ReactionModel,
    val mapPoint: MapPoint,
    val gridPoint: GridPoint?,
    val thermo: Thermo,
    var energyList: EnergyList,
){
    var coverage: BDVector? = null
    var tof: DoubleArray? = null
    var current: Double? = null
    var pressure: BDVector? = null
    var concentration: BDVector? = null
    private var kf: BDVector? = null
    private var kr: BDVector? = null
    val extraInfo = mutableMapOf<String, ExtraInfo>()

    override fun toString(): String = if(gridPoint != null) "$gridPoint $mapPoint" else mapPoint.toString()

    val rateConstants: Pair<BDVector, BDVector>
        get(){
            synchronized(this){
                if(kf != null && kr != null) return Pair(kf!!, kr!!)
                kf = BDVector(model.reactions.size)
                kr = BDVector(model.reactions.size)
                model.reactions.forEachIndexed{ i, r ->
                    if(r.kf != null && r.kr != null){
                        kf!![i] = r.kf!!
                        kr!![i] = r.kr!!
                    }else{
                        val kBT = (kB_e * thermo.t!!).nToBigDecimal()
                        val kBTh = (kBT / h_e).nToBigDecimal()
                        val pf = (r.prefactor ?: kBTh) * r.correction
                        val (ef, er) = r.reactionActivationEnergies(this)
                        kf!![i] = bdExp((-ef).nToBigDecimal().div(kBT)).times(pf)
                        kr!![i] = bdExp((-er).nToBigDecimal().div(kBT)).times(pf)
                    }
                }
                return Pair(kf!!, kr!!)
            }
    }
    val cvg: DoubleArray? = coverage?.toDoubleArray()

    fun savePoint(dos: DataOutputStream){
        if(gridPoint == null) throw Exception("Only grid points can be saved!")
        gridPoint.write(dos)
        coverage?.let {
            dos.writeByte(1)
            dos.writeBDVector(it)
        }
        tof?.let{
            dos.writeByte(2)
            dos.writeDoubleArray(it)
        }
        pressure?.let {
            dos.writeByte(3)
            dos.writeBDVector(it)
        }
        current?.let {
            dos.writeByte(4)
            dos.writeDouble(it)
        }
        concentration?.let {
            dos.writeByte(5)
            dos.writeBDVector(it)
        }
        extraInfo.forEach { (k, v) ->
            dos.writeByte(0)
            dos.writeUTF(k)
            v.writeData(dos)
        }
        dos.writeByte(-1)
    }
    fun loadPoint(dis: DataInputStream){
        while(true){
            when(val type = dis.readByte().toInt()){
                -1 -> return
                0 -> {
                    val className = dis.readUTF()
                    val info = ComponentsLoader.getNormalInstance(ExtraInfo::class.java, className)
                    if(info == null){
                        logger?.severe("Unknown extra info $className.")
                        continue
                    }else{
                        info.readData(dis)
                        extraInfo[className] = info
                    }
                }
                1 -> coverage = dis.readBDVector()
                2 -> tof = dis.readDoubleArray()
                3 -> pressure = dis.readBDVector()
                4 -> current = dis.readDouble()
                5 -> concentration = dis.readBDVector()
                else -> throw Exception("Unknown data type $type.")
            }
        }
    }
    fun getValues(): Map<String, BigDecimal>{
        val values = mutableMapOf<String, BigDecimal>()
        if(coverage != null) model.adsorbates.zip(coverage!!){ ads, c -> values[ads.identifier] = c }
        if(pressure != null) model.gases.zip(pressure!!){ gas, p -> values[gas.identifier] = p }
        return values
    }
}