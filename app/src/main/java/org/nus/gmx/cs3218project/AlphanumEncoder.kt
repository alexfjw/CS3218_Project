package org.nus.gmx.cs3218project

import android.util.Log
import java.util.*
import kotlin.collections.ArrayList


class AlphanumEncoder() {

    // 74 + 2 digits to fill
    // ascii range + include first digit + next delimiter + end delimiter + start delimiteer
    // 122-48 +1+1+1 = 77
    // + left bound & right bound
    /*  start and end freq = 10060
        next freq = 9820
     */
    private val baseAscii = 48
    private val numFrequencies = 78 + 2
    private val startFrequency = 700
    private val endFrequency = 14_000  // try to end at ~14,000. speakers can't play higher than that
    private val spacing = 120
    private val frequencies = (startFrequency..endFrequency step spacing)
            .toList()
            .subList(0, numFrequencies)

    private val leftBoundIndex = 0
    private val rightBoundIndex = numFrequencies - 1
    private val endTransmissionIndex = numFrequencies - 2
    private val startTransmissionIndex = numFrequencies - 3
    private val nextCharacterIndex = numFrequencies - 4
    val startTransmissionFrequency = frequencies[startTransmissionIndex].toFloat()
    val endTransmissionFrequency = frequencies[endTransmissionIndex].toFloat()
    val nextCharacterFrequency = frequencies[nextCharacterIndex].toFloat()
    val TAG = "AlphanumEncoder"

    init {
        assert(frequencies.size == numFrequencies)
    }

    /**
     * Assumes no invalid characters
     */
    fun stringToFrequencies(str: String): List<Float> {
        val freqs = ArrayList<Float>()
        freqs.add(startTransmissionFrequency)
        freqs.add(startTransmissionFrequency)
        // add a NEXT, to make FreqToString code easier
        freqs.add(nextCharacterFrequency)
        str.forEach { char ->
            freqs.add(alphanumericToFrequency(char))
            freqs.add(nextCharacterFrequency)
        }
        freqs.add(endTransmissionFrequency)
        freqs.add(endTransmissionFrequency)

        return freqs
    }

    private val guessThreshold = 2
    private val guessQueueSize = 3
    /**
     * The contents are not expected to be sanitized
     */
    fun frequenciesToString(freqs: List<Float>): String {
        val rawGuesses = freqs.map { frequencyToAlphanumericGuess(it) }
        Log.i(TAG, "Freqs\n" + freqs.joinToString())
        val rawClasses = rawGuesses.joinToString { guess ->
            (guess as? Character)?.character?.toString() ?: guess.javaClass.simpleName
        }
        Log.i(TAG, "rawClasses:  $rawClasses")

        // find the last "StartTransmission" within the first 10, and drop everything before it
        // find the first "EndTransmission" within the first 10, and drop everything before it
        val first10 = rawGuesses.subList(0, 10)
        var lastStart = first10.indexOfLast { it is StartTransmission }
        if (lastStart == -1) lastStart = 0
        val last10 = rawGuesses.subList(rawGuesses.size - 10, rawGuesses.size)
        var lastEnd = last10.indexOfFirst { it is EndTransmission }
        if (lastEnd == -1) lastEnd = 0

        val clipped = rawGuesses.subList(lastStart, rawGuesses.size - 10 + lastEnd)

        val clippedClasses = clipped.joinToString { guess ->
            (guess as? Character)?.character?.toString() ?: guess.javaClass.simpleName
        }
        Log.i(TAG, "clippedClasses:  $clippedClasses")

        // drop all start & end freqs
        val filtered = clipped.filter { it is Character || it is Next }

        // drop all items without similar neighbors
        val withoutNeighbors = ArrayList<AlphanumericGuess>()

        for (i in 0 until filtered.size) {
            val sameAsBefore = i == 0 || filtered[i] == filtered[i - 1]
            val sameAsAfter = i == filtered.size - 1 || filtered[i] == filtered[i + 1]

            if (i == 0 && sameAsAfter) {
                withoutNeighbors.add(filtered[i])
            } else if (i == filtered.size - 1 && sameAsBefore) {
                withoutNeighbors.add(filtered[i])
            } else if (sameAsAfter || sameAsBefore) {
                withoutNeighbors.add(filtered[i])
            }
        }

        val neighClasses = withoutNeighbors.joinToString { guess ->
            (guess as? Character)?.character?.toString() ?: guess.javaClass.simpleName
        }
        Log.i(TAG, "neighClasses:  $neighClasses")

        val guesses = withoutNeighbors

        val classes = guesses.joinToString { guess ->
            (guess as? Character)?.character?.toString() ?: guess.javaClass.simpleName
        }
        Log.i(TAG, "classes:  $classes")

        // split by next
        val groups = ArrayList<ArrayList<Character>>()
        var i = 0
        while (true) {
            var currentGuess = guesses[i]
            while (currentGuess is Next) {
                // skip all the way till end of next
                i++
                if (i >= guesses.size) break
                currentGuess = guesses[i]
            }
            if (i >= guesses.size) break
            // not a next anymore!
            groups.add(ArrayList())
            while (currentGuess is Character) {
                groups.last().add(currentGuess)
                i++
                if (i >= guesses.size) break
                currentGuess = guesses[i]
            }
            if (i >= guesses.size) break
        }
        val builder = StringBuilder()
        groups.forEach { builder.append(mostFrequentCharacter(it)) }
        return builder.toString()
    }

    // if 5 of the last 8 contain NEXT,
    // accept it as a NEXT
    private fun detectedNext(freqs: Queue<AlphanumericGuess>): Boolean {
        val numNexts = freqs.count { it is Next }
        return freqs.size == guessQueueSize && numNexts >= guessThreshold
    }

    fun isStartTransmission(freqs: ArrayDeque<Float>, minNumber: Int): Boolean {
        val guesses = freqs.map { frequencyToAlphanumericGuess(it) }
        val (inLimit, guess) = mostFrequentType(guesses, minNumber)
        //val classes = guesses.joinToString { guess -> guess.javaClass.simpleName }
        //Log.i(TAG, "classes:  $classes")
        //Log.i(TAG, "isStartTransmission")
        //Log.i(TAG,"type: ${guess.javaClass.canonicalName}")
        //Log.i(TAG,"detected: ${inLimit && guess is StartTransmission}")
        return inLimit && guess is StartTransmission
    }

    fun isEndTransmission(freqs: ArrayDeque<Float>, minNumber: Int): Boolean {
        val guesses = freqs.map { frequencyToAlphanumericGuess(it) }
        val (inLimit, guess) = mostFrequentType(guesses, minNumber)
        return inLimit && guess is EndTransmission
    }

    data class MostFrequentType(val inLimit: Boolean, val guess: AlphanumericGuess)

    private fun mostFrequentType(guesses: List<AlphanumericGuess>, minNumber: Int): MostFrequentType {
        var startTransmission = 0
        var endTransmission = 0
        var next = 0
        var character = 0
        var unknown = 0
        guesses.forEach {
            when(it) {
                is StartTransmission -> startTransmission++
                is Next -> next++
                is EndTransmission -> endTransmission++
                is Character -> character++
                is Unknown -> unknown++
            }
        }
        val maxVal = listOf(startTransmission, endTransmission, next, character, unknown).max() ?: 0

        val result = when (maxVal) {
            startTransmission -> StartTransmission(0f)
            endTransmission -> EndTransmission(0f)
            next -> Next(0f)
            character -> Character(0f, '0')
            else -> Unknown(0f)
        }

        return MostFrequentType(maxVal >= minNumber, result)
    }

    // assume that frequencies are equal!
    private fun mostFrequentCharacter(freqs: Collection<AlphanumericGuess>): Char? {
        // might have some unknowns, and other things here
        val sanitized = ArrayList<Char>()
        freqs.forEach { if (it is Character) sanitized.add(it.character) }

        Log.i(TAG, "most freq char guess: ${sanitized.joinToString()}")
        // group by character
        val grouped = sanitized
                .groupingBy { it }
                .eachCount()

        val mostFrequent = grouped.maxBy { it.value }
        return mostFrequent?.key
    }

    private fun alphanumericToFrequency(char: Char): Float {
        val charInt = char.toInt()
        if (charInt < baseAscii || charInt > 122)
            throw IllegalArgumentException("char is out of alphanum range")
        else {
            // +1 for left bound
            val scaled = charInt - baseAscii + 1
            return frequencies[scaled].toFloat()
        }
    }

    fun frequencyToAlphanumericGuess(freq: Float): AlphanumericGuess {

        val nearestIdx = findNearestFrequencyIndex(freq)
        return when (nearestIdx) {
            leftBoundIndex, rightBoundIndex -> Unknown(freq)
            startTransmissionIndex -> StartTransmission(freq)
            endTransmissionIndex -> EndTransmission(freq)
            nextCharacterIndex -> Next(freq)
            else -> {
                val charAscii = (baseAscii + nearestIdx - 1).toChar()
                Character(freq, charAscii)
            }
        }
    }

    private fun findNearestFrequencyIndex(input: Float): Int {
        var low = 0
        var high = frequencies.size - 1

        while (low < high) {
            val mid = (low + high) / 2
            assert(mid < high)
            val d1 = Math.abs(frequencies[mid] - input)
            val d2 = Math.abs(frequencies[mid + 1] - input)
            if (d2 <= d1) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return high
    }

}
