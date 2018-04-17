package org.nus.gmx.cs3218project

import android.media.AudioRecord
import android.util.Log
import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D
import java.util.*
import kotlin.math.cos

interface SoundSamplerCallback {
    // no guarantees on when it is heard
    fun heardStartTransmission()
    // no guarantees on when it is heard
    fun heardEndTransmission(sampler: SoundSampler)
    fun heardFrequency(freq: Float)
}

class SoundSampler(private val listener: SoundSamplerCallback) {
    val FS = 40000 // sampling frequency
    private val audioEncoding = 2
    private val nChannels = 16
    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val WINDOWS_TO_TAKE = 5 // how many averages do we do per audio sampling
    var buffer: ShortArray? = null
    val minBufferSize = 6144
    val suggestedWindowSize = 4096
    val spareBuffer = minBufferSize - suggestedWindowSize
    private val lastHeardFrequencies = ArrayDeque<Float>()

    private val encoder = AlphanumEncoder()
    private val queueSize = 4
    private val thresholdSize = 2
    private var go = true

    val TAG = "SoundSampler"

    @Throws(Exception::class)
    fun init() {
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

        buffer = ShortArray(bufferSize)
        audioRecord!!.startRecording()

        recordingThread = object : Thread() {
            override fun run() {
                while (go) {
                    audioRecord!!.read(buffer, 0, bufferSize)
                    audioBufferUpdated()
                }
            }
        }
        recordingThread!!.start()
    }

    private fun audioBufferUpdated() {
        val frequencyThreshold = 600.0

        val windowSize = suggestedWindowSize
        val hannWindow = buildHannWindow(windowSize)
        val intervalSize = spareBuffer / (WINDOWS_TO_TAKE + 1)

        // x2, to insert conjugate components
        val fftSize = windowSize * 2
        val fftObject = FloatFFT_1D(fftSize/2)
        val interval: Float = FS/(fftSize.toFloat())

        val highestFrequencies = ArrayList<Float>()
        for (i in 0 until WINDOWS_TO_TAKE) {
            val fftBuffer = FloatArray((fftSize).toInt()) // try padding with zeros for higher resolution
            for (j in 0 until windowSize) {
                val bufferOffset = intervalSize * i
                fftBuffer[2*j] = buffer!![j+bufferOffset].toFloat() * hannWindow[j]
                fftBuffer[2*j+1] = 0.0f
            }
            fftObject.complexForward(fftBuffer)

            val leftHalf = fftBuffer.toMutableList().subList(0, fftBuffer.size/2)
            // set dc to 0. we don't need it and, it's usually big
            leftHalf[0] = 0.0f;
            val largestIndex = leftHalf.indices.maxBy { leftHalf[it] } ?: 0
            val firstFrequency: Float = largestIndex * interval
            if (firstFrequency > frequencyThreshold)
                highestFrequencies.add(firstFrequency)
        }
        val average = highestFrequencies.average().toFloat()
        //Log.i(TAG, "freqs heard: ${highestFrequencies.joinToString(", ")}")
        Log.i(TAG, "avg: ${average}")
        listener.heardFrequency(average)
        manageLastHeardFrequencies(average)
    }

    private fun manageLastHeardFrequencies(newFrequency: Float) {
        lastHeardFrequencies.addLast(newFrequency)
        if (lastHeardFrequencies.size > queueSize) {
            lastHeardFrequencies.removeFirst()
        }
        // check if it is START or END
        val isStart: Boolean by lazy { encoder.isStartTransmission(lastHeardFrequencies, thresholdSize) }
        val isEnd: Boolean by lazy { encoder.isEndTransmission(lastHeardFrequencies, thresholdSize) }
        if (isStart) {
            listener.heardStartTransmission()
        } else if (isEnd) {
            listener.heardEndTransmission(this)
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
        go = false
        recordingThread?.interrupt()
        audioRecord?.release()
    }
}