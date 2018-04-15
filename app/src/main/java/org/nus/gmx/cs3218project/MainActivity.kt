package org.nus.gmx.cs3218project

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val RECORD_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_REQUEST_CODE)
        }

        init()
    }

    fun init() {
        btn_play.setOnClickListener {
            var freq = et_freq.text.toString().toFloat()
            playSound(1, freq)
        }
    }

    fun playSound(duration: Int, frequency: Float) {
        var dur = duration * 44100
        var mBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO,  AudioFormat.ENCODING_PCM_8BIT)
//        var mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize, AudioTrack.MODE_STREAM)
        val mAudioTrack = AudioTrack(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                mBufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        val mSound = DoubleArray(44100)
        val mBuffer = ShortArray(dur)
        for (i in mSound.indices) {
            mSound[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (44100 / frequency))
            mBuffer[i] = (mSound[i] * Short.MAX_VALUE).toShort()
        }

        mAudioTrack.setVolume(AudioTrack.getMaxVolume())
        mAudioTrack.play()

        mAudioTrack.write(mBuffer, 0, mSound.size)
        mAudioTrack.stop()
        mAudioTrack.release()
    }
}
