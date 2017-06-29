package com.suhininalex.clones.core

import com.suhininalex.clones.core.structures.IndexedSequence
import com.suhininalex.clones.core.structures.SourceToken
import com.suhininalex.clones.core.structures.TreeCloneClass
import com.suhininalex.clones.core.utils.*
import com.suhininalex.clones.ide.configuration.PluginSettings
import com.suhininalex.suffixtree.Node
import com.suhininalex.suffixtree.SuffixTree
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object CloneIndexer {

    internal val methodIds: MutableMap<Int, Long> = HashMap()
    internal var tree = SuffixTree<SourceToken>()
    internal val rwLock = ReentrantReadWriteLock()

    fun clear() {
        methodIds.clear()
        tree = SuffixTree()
    }

    fun addSequence(indexedSequence: IndexedSequence) = rwLock.write {
        addSequenceUnlocked(indexedSequence)
    }

    fun removeSequence(indexedSequence: IndexedSequence) = rwLock.write {
        removeSequenceUnlocked(indexedSequence)
    }

    private fun addSequenceUnlocked(indexedSequence: IndexedSequence) {
        if (indexedSequence.id in methodIds) return
        val sequence = indexedSequence.sequence.toList()
        if (sequence.size < PluginSettings.minCloneLength) return //TODO remove from here
        val numberId = tree.addSequence(sequence)
        methodIds.put(indexedSequence.id, numberId)
    }

    private fun removeSequenceUnlocked(indexedSequence: IndexedSequence) {
        val numberId = methodIds[indexedSequence.id] ?: return
        methodIds.remove(indexedSequence.id)
        tree.removeSequence(numberId)
    }

    fun getAllCloneClasses(): Sequence<TreeCloneClass>  = rwLock.read {
        tree.getAllCloneClasses(PluginSettings.minCloneLength)
    }

    fun getAllSequenceClasses(indexedSequence: IndexedSequence): Sequence<TreeCloneClass> = rwLock.read {
        val numberId = methodIds[indexedSequence.id] ?: return emptySequence()
        return tree.getAllSequenceClasses(numberId, PluginSettings.minCloneLength).asSequence()
    }
}

fun SuffixTree<SourceToken>.getAllCloneClasses(minTokenLength: Int): Sequence<TreeCloneClass> =
     root.depthFirstTraverse { it.edges.asSequence().mapNotNull { it.terminal }}
             .map(::TreeCloneClass)
             .filter { it.length > minTokenLength }

fun SuffixTree<SourceToken>.getAllSequenceClasses(id: Long, minTokenLength: Int): Sequence<TreeCloneClass>  {
    val classes = LinkedList<TreeCloneClass>()
    val visitedNodes = HashSet<Node>()
    for (branchNode in this.getAllLastSequenceNodes(id)) {
        for (currentNode in branchNode.riseTraverser()){
            if (visitedNodes.contains(currentNode)) break;
            visitedNodes.add(currentNode)
            classes.addIf(TreeCloneClass(currentNode)) {it.length > minTokenLength}
        }
    }
    return classes.asSequence()
}