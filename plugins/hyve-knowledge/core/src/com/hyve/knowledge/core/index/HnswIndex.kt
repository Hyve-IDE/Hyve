// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.index

import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import io.github.jbellis.jvector.disk.SimpleMappedReader
import io.github.jbellis.jvector.graph.GraphIndexBuilder
import io.github.jbellis.jvector.graph.GraphSearcher
import io.github.jbellis.jvector.graph.ImmutableGraphIndex
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues
import io.github.jbellis.jvector.graph.SearchResult
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider
import io.github.jbellis.jvector.vector.VectorizationProvider
import io.github.jbellis.jvector.util.Bits
import io.github.jbellis.jvector.vector.VectorSimilarityFunction
import java.nio.file.Path

class HnswIndex(
    private val dimension: Int,
    private val maxDegree: Int = 32,
    private val efConstruction: Int = 200,
    private val log: LogProvider = StdoutLogProvider,
) {
    private val similarity = VectorSimilarityFunction.COSINE
    private val vts = VectorizationProvider.getInstance().vectorTypeSupport

    private var builtGraph: ImmutableGraphIndex? = null
    private var onDiskGraph: OnDiskGraphIndex? = null
    private var readerSupplier: SimpleMappedReader.Supplier? = null
    private var vectorValues: ListRandomAccessVectorValues? = null

    fun build(vectors: List<FloatArray>): Int {
        if (vectors.isEmpty()) return 0

        val vectorFloats = vectors.map { vts.createFloatVector(it) }
        val ravv = ListRandomAccessVectorValues(vectorFloats, dimension)
        vectorValues = ravv

        val scoreProvider = BuildScoreProvider.randomAccessScoreProvider(ravv, similarity)
        val builder = GraphIndexBuilder(
            scoreProvider,
            dimension,
            maxDegree,
            efConstruction,
            1.2f,
            1.4f,
            false,
        )

        val graph = builder.build(ravv)
        builder.close()

        builtGraph = graph
        log.info("Built HNSW index: ${graph.size(0)} vectors, dimension=$dimension")
        return graph.size(0)
    }

    fun save(path: Path) {
        val graph = builtGraph ?: throw IllegalStateException("No in-memory graph to save")
        val ravv = vectorValues ?: throw IllegalStateException("No vector values to save")
        path.parent?.toFile()?.mkdirs()

        OnDiskGraphIndex.write(graph, ravv, path)
        log.info("Saved HNSW index to $path (${graph.size(0)} vectors)")
    }

    fun load(path: Path) {
        close()
        val supplier = SimpleMappedReader.Supplier(path)
        readerSupplier = supplier
        onDiskGraph = OnDiskGraphIndex.load(supplier)
        log.info("Loaded HNSW index from $path (${onDiskGraph!!.size(0)} vectors)")
    }

    fun query(queryVector: FloatArray, k: Int): List<Pair<Int, Float>> {
        val graph = onDiskGraph ?: builtGraph
            ?: throw IllegalStateException("No index loaded or built")

        val queryVec = vts.createFloatVector(queryVector)
        val ravv = vectorValues

        val result: SearchResult = if (ravv != null) {
            GraphSearcher.search(queryVec, k, ravv, similarity, graph, Bits.ALL)
        } else {
            val diskGraph = onDiskGraph ?: throw IllegalStateException("No on-disk index")
            val view = diskGraph.getView()
            val searcher = GraphSearcher(graph)
            val scoreProvider = DefaultSearchScoreProvider.exact(queryVec, similarity, view)
            val searchResult = searcher.search(scoreProvider, k, Bits.ALL)
            searcher.close()
            view.close()
            searchResult
        }

        return result.nodes.map { Pair(it.node, it.score) }
    }

    fun size(): Int = onDiskGraph?.size(0) ?: builtGraph?.size(0) ?: 0

    fun isLoaded(): Boolean = onDiskGraph != null || builtGraph != null

    fun close() {
        builtGraph?.close()
        builtGraph = null
        onDiskGraph?.close()
        onDiskGraph = null
        readerSupplier?.close()
        readerSupplier = null
        vectorValues = null
    }
}
