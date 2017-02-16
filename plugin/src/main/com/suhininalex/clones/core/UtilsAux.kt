package com.suhininalex.clones.core

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.ElementType.*
import com.intellij.psi.tree.TokenSet
import com.suhininalex.clones.core.clonefilter.LengthFilter
import com.suhininalex.clones.core.clonefilter.filterClones
import com.suhininalex.clones.ide.document
import com.suhininalex.clones.ide.endLine
import com.suhininalex.clones.ide.method
import com.suhininalex.clones.ide.startLine
import com.suhininalex.suffixtree.SuffixTree
import java.util.*

class CloneRangeID(val cloneRange: CloneRange){

    val file = cloneRange.firstPsi.containingFile
    val startLine = cloneRange.firstPsi.startLine
    val endLine = cloneRange.lastPsi.endLine

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as CloneRangeID

        if (file != other.file) return false
        if (startLine != other.startLine) return false
        if (endLine != other.endLine) return false

        return true
    }

    override fun hashCode(): Int {
        var result = file?.hashCode() ?: 0
        result = 31 * result + startLine
        result = 31 * result + endLine
        return result
    }
}

fun filterSameCloneRangeClasses(clones: List<CloneRangeClass>): List<CloneRangeClass> {
    val map = HashMap<CloneRangeID, Int>()
    var groupId = 0
    clones.forEach { cloneRangeClass ->
        val cloneWithAnotherParent = cloneRangeClass.cloneRanges.find { map[CloneRangeID(it)] != null }
        val groupId: Int =
                if (cloneWithAnotherParent == null) {
                    groupId++
                } else {
                    map[CloneRangeID(cloneWithAnotherParent)]!!
                }

        cloneRangeClass.cloneRanges.map(::CloneRangeID).forEach {
            map.put(it, groupId)
        }
    }
    return map.entries.groupBy { it.value }.values.map { CloneRangeClass(it.map { it.key.cloneRange }) }
}

fun CloneRangeClass.scoreSelfCoverage(): Int =
        cloneRanges[0].scoreSelfCoverage()

fun CloneRange.scoreSelfCoverage(): Int{
    val sequence = sequenceFromRange(firstPsi, lastPsi).toList()

    val tree = SuffixTree<Token>()
    tree.addSequence(sequence.map(::Token))
    val clones = tree.getAllCloneClasses().filterClones();
    val raw = clones.map { CloneRangeClass(it.clones.map { CloneRange(it.firstPsi, it.lastPsi) }.toList()) }
    val length = raw.flatMap { it.cloneRanges }
            .map{ TextRange(it.firstPsi.startLine, it.lastPsi.endLine+1) }
            .uniteRanges()
            .sumBy { it.length }
    val bigLength = TextRange(firstPsi.startLine, lastPsi.endLine).length + 1
    return length*100/bigLength
}

fun PsiElement.nextElement(): PsiElement{
    var current = this
    while (current.nextSibling == null)
        current = current.parent
    current = current.nextSibling
    while (current.firstChild != null)
        current = current.firstChild
    return current
}

fun sequenceFromRange(firstPsi: PsiElement, lastPsi: PsiElement): Sequence<PsiElement> {
    var first = firstPsi
    while (first.firstChild != null)
        first = first.firstChild
    return generateSequence (firstPsi) { it.nextElement() }.takeWhile { it.textRange.endOffset <= lastPsi.textRange.endOffset }.filter { it !in javaTokenFilter }
}

val lengthClassFilter = LengthFilter(10)

fun SuffixTree<Token>.getAllCloneClasses(): Sequence<CloneClass>  =
        root.depthFirstTraverse { it.edges.asSequence().map { it.terminal }.filter { it != null } }
                .map(::CloneClass)
                .filter { lengthClassFilter.isAllowed(it) }

fun List<TextRange>.uniteRanges(): List<TextRange> {
    if (size < 2) return this
    val sorted = sortedBy { it.startOffset }.asSequence()
    val result = ArrayList<TextRange>()
    val first = sorted.first()
    var lastLeft = first.startOffset
    var lastRight = first.endOffset
    sorted.forEach {
        if (it.endOffset <= lastRight ) {
            // skip
        } else if (it.startOffset <= lastRight)  {
            lastRight = it.endOffset
        } else {
            result.add(TextRange(lastLeft, lastRight))
            lastLeft = it.startOffset
            lastRight = it.endOffset
        }
    }
    result.add(TextRange(lastLeft, lastRight))
    return result
}

data class CloneScore(val selfCoverage: Double, val sameMethodCount: Double, val length: Int)

fun CloneScore.score(): Double =
        (1-selfCoverage*sameMethodCount)*length

fun CloneRangeClass.getScore() =
    CloneScore(scoreSelfCoverage()/100.0, scoreSameMethod()/100.0, cloneRanges[0].getLength())

fun CloneRangeClass.scoreSameMethod(): Int =
    if (cloneRanges.size < 2) 100
    else (cloneRanges.map{ it.firstPsi.method }.groupBy { it }.map { it.value.size }.max()!!-1)*100/(cloneRanges.size-1)
