package com.bangdream.pet.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamDisplayPublisherTest {
    @Test
    fun directOutputPublishesEachSnapshotImmediately() = runBlocking {
        val displayed = mutableListOf<String>()
        val output = StreamDisplayPublisher(this, blockOutput = false, publish = displayed::add)
        output.submit("a")
        output.submit("ab")
        output.submit("abc")
        assertEquals(listOf("a", "ab", "abc"), displayed)
    }

    @Test
    fun blockOutputFlushesTheLatestSnapshotWhenTheStreamEnds() = runBlocking {
        val displayed = mutableListOf<String>()
        val output = StreamDisplayPublisher(this, blockOutput = true, nanoTime = { 0L }, publish = displayed::add)
        output.submit("a")
        output.submit("ab")
        output.submit("abc")
        assertEquals(listOf("a"), displayed)
        output.flush()
        output.flush()
        assertEquals(listOf("a", "abc"), displayed)
    }

    @Test
    fun blockOutputPublishesPendingTextEvenWhenNoMoreEventsArrive() = runBlocking {
        val displayed = CompletableDeferred<String>()
        val output = StreamDisplayPublisher<String>(this, blockOutput = true, nanoTime = { 0L }) {
            if (it.length > 1) displayed.complete(it)
        }
        output.submit("a")
        output.submit("ab")
        output.submit("abc")
        assertEquals("abc", withTimeout(2_000) { displayed.await() })
    }
}
