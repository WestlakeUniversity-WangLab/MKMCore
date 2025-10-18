package com.wang_lab.mkm_core.jpype

import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.point.GridPoint
import com.wang_lab.mkm_core.point.PointInfo


interface PySendString {
    fun transfer(info: String)
}

interface PySendError {
    fun transfer(version: String, cls: String, msg: String?, info: String?, stackTrace: String)
}

interface PySendWarning {
    fun transfer(msg: String)
}
interface PySendIntInt {
    fun transfer(int1: Int, int2: Int)
}

interface PySendResult {
    fun transfer(p: PointInfo)
}

interface PySendPoint {
    fun transfer(pt: GridPoint, state: Boolean)
}
class NoSendStr: PySendString{
    override fun transfer(info: String) {}
}
class NoSendError: PySendError{
    override fun transfer(version: String, cls: String, msg: String?, info: String?, stackTrace: String) {}
}
class NoSendWarning: PySendWarning{
    override fun transfer(msg: String) {
        logger?.warning(msg)
    }
}
class NoSendIntInt: PySendIntInt{
    override fun transfer(int1: Int, int2: Int) {
        //println("$int1 / $int2")
    }
}
class NoSendResult: PySendResult{
    override fun transfer(p: PointInfo) {
    }
}
class NoSendPointList: PySendPoint{
    override fun transfer(pt: GridPoint, state: Boolean) {}
}

/**
 * This class is used to actively transfer information to python side.
 *
 * Use JImplements and JOverride from jpype to implement interface such as PySendString and PySendIntInt in Python.
 *
 * @see <a href="https://jpype.readthedocs.io/en/latest/quickguide.html#implements-and-extension">jpype docs</a>.
 */
class PyInteraction (
    val info: PySendString = NoSendStr(),
    val error: PySendError = NoSendError(),
    val warning: PySendWarning = NoSendWarning(),
    val progress: PySendIntInt = NoSendIntInt(),
    val result: PySendResult = NoSendResult(),
    val ptStart: PySendPoint = NoSendPointList()
)