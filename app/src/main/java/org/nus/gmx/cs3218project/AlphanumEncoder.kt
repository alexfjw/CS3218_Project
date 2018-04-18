package org.nus.gmx.cs3218project

import android.util.Log
import java.util.*
import kotlin.collections.ArrayList


/**
 *
 */
class AlphanumEncoder() {

    // map a range of frequencies to ascii 0 to Z
    // there are 77 digits to fill
    // 122-48 +1 +3 = 78
    // ascii_of_Z - ascii_of_0 + 1(to include 0) + 3 (START, END, NEXT)
    private val baseAscii = 48
    // + 2 to simplify catching frequencies beyond the left & right most frequencies
    private val numFrequencies = 78 + 2
    // frequency range: 700 - 14,000
    // noise is usually 100-300
    // stop at ~14,000. some speakers can't play higher than that
    private val startFrequency = 700
    private val endFrequency = 14_000
    // spacing between each frequency
    private val spacing = 115
    private val frequencies = (startFrequency..endFrequency step spacing)
            .toList()
            .subList(0, numFrequencies)

    // indices for special frequencies
    private val leftBoundIndex = 0
    private val rightBoundIndex = numFrequencies - 1
    private val endTransmissionIndex = numFrequencies - 2
    private val startTransmissionIndex = numFrequencies - 3
    private val nextItemIndex = numFrequencies - 4
    // special frequencies
    private val startTransmissionFrequency = frequencies[startTransmissionIndex].toFloat()
    private val endTransmissionFrequency = frequencies[endTransmissionIndex].toFloat()
    private val nextCharacterFrequency = frequencies[nextItemIndex].toFloat()

    private val TAG = "AlphanumEncoder"

    init {
        assert(frequencies.size == numFrequencies)
    }

    /**
     * Assumes no invalid characters
     * Encodes input characters in the following format
     * 1) START_FREQ * 2
     * 2) For each character:
     *      Character's frequency
     *      NEXT_FREQ
     * 3) END_FREQ * 3
     *
     * START_FREQ & END_FREQ are extended to reduce the chance of missing them
     * END_FREQ is 1 longer, as it is the highest freq & is empirically easy to miss
     */
    fun stringToFrequencies(str: String): List<Float> {
        val freqs = ArrayList<Float>()
        freqs.add(startTransmissionFrequency)
        freqs.add(startTransmissionFrequency)
        freqs.add(nextCharacterFrequency)
        str.forEach { char ->
            freqs.add(alphanumericToFrequency(char))
            freqs.add(nextCharacterFrequency)
        }
        freqs.add(endTransmissionFrequency)
        freqs.add(endTransmissionFrequency)
        freqs.add(endTransmissionFrequency)

        return freqs
    }

    // for debugging, prints the Guess type, or the character if it is a Character
    private fun printGuesses(guesses: List<AlphanumericGuess>, textHeader: String) {
        val classes = guesses.joinToString { guess ->
            (guess as? Character)?.character?.toString() ?: guess.javaClass.simpleName
        }
        Log.i(TAG, "$textHeader: $classes")
    }

    /**
     * Converts a list of frequencies into a String
     * Process:
     * 1) clip off START_FREQ & END_FREQ from the start & end of input
     * 2) drop all guesses not NEXT or Character
     * 3) drop all guesses that don't have an equal neighbor
     * (transitioning between frequencies inserts an extra frequency between the original 2)
     * (this also removes background noise. noise is 1 item long by heuristics])
     * 4) split the guesses by NEXT
     * 5) pick the most frequent character in each group & string them up
     */
    fun frequenciesToString(freqs: List<Float>): String {
        val rawGuesses = freqs.map { frequencyToAlphanumericGuess(it) }
        printGuesses(rawGuesses, "Raw Guesses")

        // find the last "StartTransmission" within the first 20, and drop everything before it
        // find the first "EndTransmission" within the first 10, and drop everything before it
        val first20 = rawGuesses.subList(0, 20)
        var lastStart = first20.indexOfLast { it is StartTransmission }
        if (lastStart == -1) lastStart = 0
        val last10 = rawGuesses.subList(rawGuesses.size - 10, rawGuesses.size)
        var lastEnd = last10.indexOfFirst { it is EndTransmission }
        if (lastEnd == -1) lastEnd = 0
        val clipped = rawGuesses.subList(lastStart, rawGuesses.size - 10 + lastEnd)
        printGuesses(clipped, "Clipped Guesses")

        // keep only Character & Next
        val filtered = clipped.filter { it is Character || it is Next }

        // drop all items without similar neighbors
        val droppedNeighbors = ArrayList<AlphanumericGuess>()

        for (i in 0 until filtered.size) {
            val sameAsBefore by lazy {filtered[i] == filtered[i - 1]}
            val sameAsAfter by lazy {filtered[i] == filtered[i + 1]}

            if (i == 0) {
                if (sameAsAfter) droppedNeighbors.add(filtered[i])
            } else if (i == filtered.size - 1) {
                if (sameAsBefore) droppedNeighbors.add(filtered[i])
            } else if (sameAsAfter || sameAsBefore) {
                droppedNeighbors.add(filtered[i])
            }
        }
        printGuesses(droppedNeighbors, "Without Neighbors")

        val guesses = droppedNeighbors

        // split the guesses by NEXT
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

        // pick the most frequent character in each group
        val builder = StringBuilder()
        groups.forEach { builder.append(mostFrequentCharacter(it)) }

        return builder.toString()
    }

    /**
     * Returns yes, when there are at least minNumber of START_FREQs in freqs
     */
    fun isStartTransmission(freqs: ArrayDeque<Float>, minNumber: Int): Boolean {
        val guesses = freqs.map { frequencyToAlphanumericGuess(it) }
        val (inLimit, guess) = mostFrequentType(guesses, minNumber)
        return inLimit && guess is StartTransmission
    }

    /**
     * Returns yes, when there are at least minNumber of END_FREQs in freqs
     */
    fun isEndTransmission(freqs: ArrayDeque<Float>, minNumber: Int): Boolean {
        val guesses = freqs.map { frequencyToAlphanumericGuess(it) }
        val (inLimit, guess) = mostFrequentType(guesses, minNumber)
        return inLimit && guess is EndTransmission
    }

    data class MostFrequentType(val inLimit: Boolean, val guess: AlphanumericGuess)

    // returns the most frequent Guess & whether it has more than minNumber
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

    /**
     * Returns the most frequent character from the list
     */
    private fun mostFrequentCharacter(freqs: Collection<AlphanumericGuess>): Char? {
        // get chars out of the guesses
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

    private fun frequencyToAlphanumericGuess(freq: Float): AlphanumericGuess {

        val nearestIdx = findNearestFrequencyIndex(freq)
        return when (nearestIdx) {
            leftBoundIndex, rightBoundIndex -> Unknown(freq)
            startTransmissionIndex -> StartTransmission(freq)
            endTransmissionIndex -> EndTransmission(freq)
            nextItemIndex -> Next(freq)
            else -> {
                val charAscii = (baseAscii + nearestIdx - 1).toChar()
                Character(freq, charAscii)
            }
        }
    }

    /**
     * Returns the nearest matching index in this.frequencies
     * for the input frequency
     */
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
