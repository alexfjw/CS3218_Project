package org.nus.gmx.cs3218project

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
    val startTransmissionFrequency = frequencies[endTransmissionIndex].toFloat()
    val endTransmissionFrequency = frequencies[endTransmissionIndex].toFloat()
    val nextCharacterFrequency = frequencies[nextCharacterIndex].toFloat()

    init {
        assert(frequencies.size == numFrequencies)
    }

    /**
     * Assumes no invalid characters
     */
    fun stringToFrequencies(str: String): List<Float> {
        val freqs = ArrayList<Float>()
        freqs.add(startTransmissionFrequency)
        str.forEach { char ->
            freqs.add(alphanumericToFrequency(char))
            freqs.add(nextCharacterFrequency)
        }
        freqs.add(endTransmissionFrequency)

        return freqs
    }

    private val guessThreshold = 5
    private val guessQueueSize = 8
    /**
     * Assumes that the list does not contain StartTransmission, or EndTransmission
     *
     * The contents are not expected to be sanitized
     */
    fun frequenciesToString(freqs: List<Float>): String {
        val builder = StringBuilder()
        val guesses = freqs.map { frequencyToAlphanumericGuess(it) }

        val charBuffer = ArrayDeque<AlphanumericGuess>()
        val nextBuffer = ArrayDeque<AlphanumericGuess>()

        for (i in 0 until guesses.size) {
            val currentGuess = guesses[i]

            // keep track of our position (in a next or not)
            if (nextBuffer.size > guessQueueSize)
                nextBuffer.removeFirst()
            nextBuffer.add(currentGuess)

            if (detectedNext(nextBuffer)) {
                // add the char to builder, if we just entered the next
                val justEnteredNext = charBuffer.size > 0
                if (justEnteredNext) {
                    builder.append(mostFrequentCharacter(charBuffer))
                    charBuffer.clear()
                }
                // we're in a next, do nothing

            } else {
                val justEnteredChar = charBuffer.size == 0
                if (justEnteredChar) {
                    // dump in all the items detected so far to get the whole signal
                    charBuffer.addAll(nextBuffer)
                }

                // we're out of a next
                // chalk up the guesses
                charBuffer.add(currentGuess)
            }

        }
        
        return builder.toString()
    }

    // if 5 of the last 8 contain NEXT,
    // accept it as a NEXT
    private fun detectedNext(freqs: Queue<AlphanumericGuess>): Boolean {
        val numNexts = freqs.count { it is Next }
        return freqs.size == guessQueueSize && numNexts >= guessThreshold
    }

    // assume that frequencies are equal!
    private fun mostFrequentCharacter(freqs: ArrayDeque<AlphanumericGuess>): Char? {
        // might have some unknowns, and other things here
        val sanitized = ArrayList<Char>()
        freqs.forEach { if (it is Character) sanitized.add(it.character) }

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
                // -1, for left bound
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

sealed class AlphanumericGuess(val frequency: Float)

class StartTransmission(frequency: Float): AlphanumericGuess(frequency)
class EndTransmission(frequency: Float): AlphanumericGuess(frequency)
class Next(frequency: Float): AlphanumericGuess(frequency)
class Character(frequency: Float, val character: Char): AlphanumericGuess(frequency)
class Unknown(frequency: Float): AlphanumericGuess(frequency)
