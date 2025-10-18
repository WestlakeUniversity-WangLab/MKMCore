package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eTEN
import com.wang_lab.mkm_core.algebra.number_math.unaryMinus
import java.lang.NumberFormatException
import java.math.BigDecimal
import java.math.BigInteger

interface StringExpression

const val pattern_prohibited = "=\n\t\r\\\'\""
const val pattern_operator = "+-*/^()（）,，"
class StringExpressionNumber(val value: String): StringExpression
class StringExpressionWord(val value: String): StringExpression
enum class StringExpressionOperator: StringExpression{
    PLUS, MINUS, TIMES, DIVIDE, POWER
}
object StringExpressionBracketLeft: StringExpression
object StringExpressionBracketRight: StringExpression
object StringExpressionBracketComma: StringExpression
class StringExpressionBracket(val value: MutableList<StringExpression>): StringExpression
class StringExpressionFunction(val name: String, val value: MutableList<StringExpression>): StringExpression
class StringExpressionGenerated(val value: AlgebraExpr): StringExpression
fun operator(c: Char) = when(c){
    '+' -> StringExpressionOperator.PLUS
    '-' -> StringExpressionOperator.MINUS
    '*' -> StringExpressionOperator.TIMES
    '/' -> StringExpressionOperator.DIVIDE
    '^' -> StringExpressionOperator.POWER
    '(', '（' -> StringExpressionBracketLeft
    ')', '）' -> StringExpressionBracketRight
    ',', '，' -> StringExpressionBracketComma
    else -> throw Exception("$c is not an operator!")
}
private val functions = listOf("ln", "log", "sin", "cos", "tan", "exp")
fun functions(list: MutableList<StringExpression>){
    var i = 0
    while(i < list.size){
        if(list[i] is StringExpressionBracket){
            functions((list[i] as StringExpressionBracket).value)
            if(i > 0 && list[i-1] is StringExpressionWord){
                val function = function((list[i-1] as StringExpressionWord).value, list[i] as StringExpressionBracket)
                list.removeAt(i-1)
                list.removeAt(i-1)
                list.add(i-1, function)
                i --
            }
        }

        i ++
    }
}
fun function(name: String, bracket: StringExpressionBracket) =
    if(name in functions) StringExpressionFunction(name, bracket.value)
    else throw Exception("$name is not a function name!")
fun genFunction(f: StringExpressionFunction): AlgebraExpr = when(f.name){
    "exp" -> ExprPow(ExprConst(Math.E), generate(f.value))
    "ln" -> ExprLn(generate(f.value))
    "log" -> {
        if(f.value.contains(StringExpressionBracketComma)){
            val index = f.value.indexOf(StringExpressionBracketComma)
            ExprLn(generate(f.value.subList(index+1, f.value.size))) / ExprLn(generate(f.value.subList(0, index)))
        }else{
            ExprLn(generate(f.value)) / ExprLn(eTEN)
        }
    }
    else -> throw Exception("${f.name} has not been supported.")
}
fun generate(list: MutableList<StringExpression>): AlgebraExpr{
    //Generate numbers and variables
    for(i in list.indices){
        if(list[i] is StringExpressionBracket){
            list[i] = StringExpressionGenerated(generate((list[i] as StringExpressionBracket).value))
        } else if(list[i] is StringExpressionFunction){
            list[i] = StringExpressionGenerated(genFunction(list[i] as StringExpressionFunction))
        } else if(list[i] is StringExpressionNumber){
            val str = (list[i] as StringExpressionNumber).value
            val value =
                if(str.all { it in '0'..'9' || it == '-' }){
                    try{
                        str.toInt()
                    }catch (e: NumberFormatException){
                        try{
                            str.toLong()
                        }catch (e: NumberFormatException){
                            BigInteger(str)
                        }
                    }
                }else{
                    BigDecimal(str)
                }
            list[i] = StringExpressionGenerated(ExprConst(value))
        }else if(list[i] is StringExpressionWord){
            list[i] = StringExpressionGenerated(ExprVar((list[i] as StringExpressionWord).value))
        }
    }
    var index: Int
    //Generate power
    while(true){
        index = list.indexOf(StringExpressionOperator.POWER)
        if(index == -1) break
        val base = list[index-1] as StringExpressionGenerated
        val exp = list[index+1] as StringExpressionGenerated
        list.removeAt(index-1)
        list.removeAt(index-1)
        list.removeAt(index-1)
        list.add(index-1, StringExpressionGenerated(ExprPow(base.value, exp.value)))
    }
    //Generate times and divide
    while(true){
        index = list.indexOfFirst{ it == StringExpressionOperator.TIMES || it == StringExpressionOperator.DIVIDE }
        if(index == -1) break
        val operator = list[index]
        val expr1 = (list[index-1] as StringExpressionGenerated).value
        val expr2 = (list[index+1] as StringExpressionGenerated).value
        repeat(3){ list.removeAt(index-1) }
        list.add(index-1, StringExpressionGenerated(
            if(operator == StringExpressionOperator.TIMES) expr1 * expr2 else expr1 / expr2
        ))
    }
    //Generate plus and minus
    while(true){
        index = list.indexOfFirst{ it == StringExpressionOperator.PLUS || it == StringExpressionOperator.MINUS }
        if(index == -1) break
        var operator = list[index]
        if(index == 0){
            val expr = (list[1] as StringExpressionGenerated).value
            repeat(2){ list.removeAt(0) }
            list.add(0, StringExpressionGenerated(
                if(operator == StringExpressionOperator.PLUS) expr else expr.unaryMinus()
            ))
        }else{
            val expr1 = (list[index-1] as StringExpressionGenerated).value
            while(list[index+1] is StringExpressionOperator){
                if(list[index+1]  == StringExpressionOperator.MINUS){
                    if(operator == StringExpressionOperator.PLUS) operator = StringExpressionOperator.MINUS
                    else if(operator == StringExpressionOperator.MINUS)  operator = StringExpressionOperator.PLUS
                }
                list.removeAt(index+1)
            }
            val expr2 = (list[index+1] as StringExpressionGenerated).value
            repeat(3){ list.removeAt(index-1) }
            list.add(index-1, StringExpressionGenerated(
                if(operator == StringExpressionOperator.PLUS) expr1 + expr2 else expr1 - expr2
            ))
        }
    }
    if(list.size != 1) throw Exception("Invalid expression.")
    return (list[0] as StringExpressionGenerated).value
}

fun parseExpression(expr: String): AlgebraExpr{
    var state = 0// 0: None, 1: Number, 2: Word
    var startIndex = 0
    val content = mutableListOf<StringExpression>()
    fun flush(i: Int): StringExpression{
        if(state == 1) return StringExpressionNumber(expr.substring(startIndex, i))
        if(state == 2) return StringExpressionWord(expr.substring(startIndex, i))
        throw Exception("Invalid State")
    }
    expr.forEachIndexed{ i, c ->
        if(c in pattern_prohibited) throw Exception("Invalid character at index $i in $expr")
        if(c in pattern_operator){
            if(state != 0) content.add(flush(i))
            content.add(operator(c))
            state = 0
            return@forEachIndexed
        }
        if(c == ' '){
            if(state != 0) content.add(flush(i))
            state = 0
            return@forEachIndexed
        }
        if(state == 0){
            startIndex = i
            state = if(c in '0'..'9' || c == '.') 1 else 2
        }
    }
    if(state != 0) content.add(flush(expr.length))
    //Handle brackets
    fun bracket(): Boolean{
        val indexRight = content.indexOf(StringExpressionBracketRight)
        if(indexRight == -1){
            val indexLeft = content.lastIndexOf(StringExpressionBracketLeft)
            if(indexLeft == -1) return false
            val bracket = StringExpressionBracket(MutableList(content.size-indexLeft-1){ content[indexLeft+1+it] })
            repeat(content.size - indexLeft) { content.removeAt(indexLeft) }
            content.add(bracket)
            return true
        }else{
            var indexLeft = indexRight - 1
            while(indexLeft >= 0 && content[indexLeft] != StringExpressionBracketLeft) indexLeft --
            if(indexLeft == -1) throw Exception("Unpaired right bracket at index $indexRight in $expr")
            val bracket = StringExpressionBracket(MutableList(indexRight-indexLeft-1){ content[indexLeft+1+it] })
            repeat(indexRight - indexLeft + 1) { content.removeAt(indexLeft) }
            content.add(indexLeft, bracket)
            return true
        }
    }
    while(true) if(!bracket()) break
    //Handle functions
    functions(content)
    //Generate to Expression
    return generate(content)
}