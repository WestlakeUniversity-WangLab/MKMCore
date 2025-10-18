package com.wang_lab.mkm_core

import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogRecord

class MKMFormatter: Formatter() {
    override fun format(record: LogRecord?): String {
        assert(record != null)
        return "[${sdf.format(Date(record!!.millis))}] [${record.level}] ${record.message}\n"
    }
    companion object{
        val sdf = SimpleDateFormat("HH:MM:ss")
    }
}