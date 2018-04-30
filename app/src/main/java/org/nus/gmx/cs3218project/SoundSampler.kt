package org.nus.gmx.cs3218project

import android.media.AudioRecord
import android.util.Log
import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

interface SoundSamplerCallback {
    // no guarantees on when it is heard
    fun heardStartTransmission()
    // no guarantees on when it is heard
    fun heardEndTransmission()
    fun heardFrequency(freq: Float)
}

/**
 * SoundSampler listens for START_FREQ, END_FREQ and normal frequencies
 * SoundSamplerCallback is contacted when these frequencies are detected
 *
 * Samples sound with the following algorithm:
 * Take a audioRecord buffer size of 5120
 * when each fully filled buffer arrives:
 *      Split the buffer into 2 halves (half's size is 2560)
 *      For each half:
 *          Take a few windows of 2048 (5 at time of writing, windows with step size of ~100)
 *          For each window, apply the Hann filter, perform FFT & get the highest frequency.
 *          Ignore all frequencies < 650 (we only play frequencies > 700Hz)
 *          Average the frequencies & consider the average a "detected frequency"
 *
 *          Pass the detected frequency to the listener
 *
 * Detecting START_FREQ & END_FREQ
 * Store the last 5 frequencies detected into a queue
 * Contact listener if 3 are type START_FREQ,
 * Contact listener if 3 are type END_FREQ
 */
class SoundSampler(private val listener: SoundSamplerCallback) {
    // sampling frequency, we only play up till 15,000Hz
    val FS = 40000
    private val audioEncoding = 2
    private val nChannels = 16
    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    private var keepRunningThread = true

    private val encoder = AlphanumEncoder()

    private val WINDOWS_TO_TAKE = 5 // how many averages do we do per audio sampling
    private val minBufferSize = 5120
    private val suggestedWindowSize = 2048
    private val frequencyThreshold = 650.0 // we only produce > 700Hz

    private val lastHeardFrequencies = ArrayDeque<Float>()
    private val queueSize = 5
    private val queueThresholdSize = 3

    private val TAG = "SoundSampler"

    @Throws(Exception::class)
    fun init() {
        // ensures listener will not receive START_FREQ & END_FREQ events at the same time
        assert(queueThresholdSize.toFloat() > queueSize.toFloat())

        val suggestedBufferSize = AudioRecord.getMinBufferSize(FS, nChannels, audioEncoding)
        Log.d("SoundSampler", "Suggested buffer size: $suggestedBufferSize")
        val bufferSize = if (suggestedBufferSize < minBufferSize) minBufferSize else suggestedBufferSize
        try {
            if (audioRecord != null) {
                audioRecord!!.stop()
                audioRecord!!.release()
            }

            audioRecord = AudioRecord(
                1,
                FS,
                nChannels,
                audioEncoding,
                bufferSize)
        } catch (e: Exception) {
            Log.d("Error in Init() ", e.message)
            throw Exception()
        }

        val buffer = ShortArray(bufferSize)
        audioRecord!!.startRecording()

        recordingThread = object : Thread() {
            override fun run() {
                while (keepRunningThread) {
                    audioRecord!!.read(buffer, 0, bufferSize)
                    // split buffer into 2
                    audioBufferUpdated(buffer.sliceArray(0 until (buffer.size/2)))
                    audioBufferUpdated(buffer.sliceArray((buffer.size/2) until buffer.size))
                }
            }
        }
        recordingThread!!.start()
    }

    private fun audioBufferUpdated(freqBuffer: ShortArray) {

        val windowSize = suggestedWindowSize
        val hannWindow = buildHannWindow(windowSize)
        val spareBuffer = freqBuffer.size - suggestedWindowSize
        val intervalSize = spareBuffer / (WINDOWS_TO_TAKE + 1)

        // x2, to insert conjugate components
        val fftSize = windowSize * 2
        val fftObject = FloatFFT_1D(fftSize/2)
        val interval: Float = FS/(fftSize.toFloat()/2)

        // take a few moving windows
        val highestFrequencies = ArrayList<Float>()
        for (i in 0 until WINDOWS_TO_TAKE) {
            val fftBuffer = FloatArray((fftSize))

            // multiply with hann window while filling buffer
            for (j in 0 until windowSize) {
                val bufferOffset = intervalSize * i
                fftBuffer[2*j] = freqBuffer[j+bufferOffset].toFloat() * hannWindow[j]
                fftBuffer[2*j+1] = 0.0f
            }
            fftObject.complexForward(fftBuffer)

            val leftHalf = fftBuffer.toMutableList().subList(0, fftBuffer.size/2)
            val summed = FloatArray(leftHalf.size/2)
            for (z in 0 until summed.size) {
                summed[z] = abs(leftHalf[z*2]) + abs(leftHalf[z*2 + 1])
            }
            // set dc to 0. we don't need it and, it's usually big
            summed[0] = 0.0f;
            val largestIndex = summed.indices.maxBy { summed[it] } ?: 0
            val firstFrequency: Float = largestIndex * interval
            if (firstFrequency > frequencyThreshold)
                highestFrequencies.add(firstFrequency)
        }
        val average = highestFrequencies.average().toFloat()
        Log.i(TAG, "avg: ${average}")
        listener.heardFrequency(average)
        manageLastHeardFrequencies(average)
    }

    /**
     * Contacts the listener if START or END is detected
     */
    private fun manageLastHeardFrequencies(newFrequency: Float) {
        lastHeardFrequencies.addLast(newFrequency)
        if (lastHeardFrequencies.size > queueSize) {
            lastHeardFrequencies.removeFirst()
        }
        // check if it is START or END
        val isStart: Boolean by lazy { encoder.isStartTransmission(lastHeardFrequencies, queueThresholdSize) }
        val isEnd: Boolean by lazy { encoder.isEndTransmission(lastHeardFrequencies, queueThresholdSize) }
        if (isStart) {
            listener.heardStartTransmission()
        } else if (isEnd) {
            listener.heardEndTransmission()
        }
    }

    private fun buildHannWindow(size: Int): FloatArray {
        val arr = FloatArray(size)
        for (i in 0 until size) {
            arr[i] = (0.5 * (1- cos(2* Math.PI * i / (size-1)))).toFloat()
        }
        return arr
    }

    fun close() {
        keepRunningThread = false
        audioRecord?.release()
        recordingThread?.interrupt()
    }
}