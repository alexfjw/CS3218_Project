package org.nus.gmx.cs3218project

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_broadcast.*

class BroadcastActivity : AppCompatActivity() {

    private inner class Player : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg p0: Void?): Void? {
            val freq = et_message.text.toString()
            val freqs = AlphanumEncoder().stringToFrequencies(freq)
            playSound(1f, freqs)
            return null
        }

        override fun onCancelled() {
            super.onCancelled()
            Log.e("onCancelled", "player task cancelled")
        }
    }

    private val RECORD_REQUEST_CODE = 101
    private lateinit var player: Player
    private lateinit var mAudioTrack: AudioTrack
    private var isPlaying: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_broadcast)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_REQUEST_CODE)
        }

        init()
    }

    private fun init() {
        btn_play.setOnClickListener {
            if (!isPlaying) {
                player = Player()
                player.execute()
                isPlaying = true
            } else {
                Toast.makeText(this, "Message is already being played!", Toast.LENGTH_SHORT).show()
            }
        }

        btn_broadcast_cancel.setOnClickListener {
            if (::mAudioTrack.isInitialized) {
                stopAudioTrack()
            } else {
                Log.e("Cancel", "mAudioTrack is not initialized")
            }
        }

        btn_broadcast_back.setOnClickListener{
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mAudioTrack.isInitialized) {
            stopAudioTrack()
        }
    }

    private fun playSound(duration: Float, freqs: List<Float>) {
        val dur = duration * 44100
        val mBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO,  AudioFormat.ENCODING_PCM_8BIT)
//        var mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize, AudioTrack.MODE_STREAM)
        mAudioTrack = AudioTrack(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                mBufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        val mSound = DoubleArray(44100)
        val mBuffer = ShortArray(dur.toInt())

        mAudioTrack.setVolume(AudioTrack.getMaxVolume())
        mAudioTrack.play()

        for (j in freqs.indices) {
            for (i in mSound.indices) {
                mSound[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (44100 / freqs[j]))
                mBuffer[i] = (mSound[i] * Short.MAX_VALUE).toShort()
            }
            Log.e("Current freq: ", freqs[j].toString())
            mAudioTrack.write(mBuffer, 0, mSound.size)
        }
        stopAudioTrack()
    }

    private fun stopAudioTrack() {
        try {
            mAudioTrack.stop()
            mAudioTrack.release()
        } catch (e: IllegalStateException) {
            Log.e("mAudioTrack", "mAudioTrack cancelled")
        }
        isPlaying = false
    }
}
