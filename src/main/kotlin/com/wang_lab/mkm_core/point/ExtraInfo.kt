package com.wang_lab.mkm_core.point

import java.io.DataInputStream
import java.io.DataOutputStream

abstract class ExtraInfo {
    abstract fun writeData(dos: DataOutputStream)
    abstract fun readData(dis: DataInputStream)

    open fun show(p: PointInfo): String?{ return null }
}