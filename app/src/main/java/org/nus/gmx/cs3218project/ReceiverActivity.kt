package org.nus.gmx.cs3218project

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_receiver.*

class ReceiverActivity : AppCompatActivity() {

    private val RECORD_AUDIO_PERMISSIONS_REQUEST = 1
    private var disposable: Disposable? = null
    private val detectedFrequencies: ArrayList<Float> = ArrayList()
    private val alphanumEncoder: AlphanumEncoder = AlphanumEncoder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)

        buttonListen.setOnClickListener {
            startRecordingMessage()
            spinnerListen.visibility = View.VISIBLE
            buttonListen.isEnabled = false
            messageContainer.visibility = View.INVISIBLE
        }
        buttonCancel.setOnClickListener {
            disposable?.dispose()
            spinnerListen.visibility = View.INVISIBLE
            messageContainer.visibility = View.INVISIBLE
            buttonListen.isEnabled = true
        }
        buttonBack.setOnClickListener {
            disposable?.dispose()
            onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()

        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            getPermissionToRecordAudioOrStartRecording()
        }
    }

    override fun onPause() {
        buttonCancel.performClick()
        super.onPause()
    }

    private val TAG = "ReceiverActivity"

    private fun createSamplingObservable(): Observable<Float> {
        var soundSampler: SoundSampler? = null

        return Observable.create<Float> { emitter ->
            try {
                soundSampler = SoundSampler(object: SoundSamplerCallback {
                    var started = false
                    override fun heardStartTransmission() {
                        Log.i(TAG, "Heard the start")
                        started = true
                    }
                    override fun heardEndTransmission() {
                        started = false
                        Log.i(TAG, "Heard the end")
                        soundSampler?.close()
                        emitter.onComplete()
                    }
                    override fun heardFrequency(freq: Float) {
                        if (started) emitter.onNext(freq)
                    }
                })

                soundSampler?.init()
                emitter.setCancellable { soundSampler?.close() }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
    }

    private fun startRecordingMessage(){
        try {
            val observable = createSamplingObservable()
            disposable = observable
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnComplete {
                        val message = alphanumEncoder.frequenciesToString(detectedFrequencies)
                        buttonListen.isEnabled = true
                        messageContainer.visibility = View.VISIBLE
                        spinnerListen.visibility = View.INVISIBLE
                        messageField.text = message
                        Log.i(TAG, "message: $message")
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { detectedFrequencies.clear() }
                    .subscribe { detectedFrequencies.add(it) }

        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Cannot instantiate SoundSampler", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresApi(VERSION_CODES.M)
    private fun getPermissionToRecordAudioOrStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(applicationContext,
                "Getting permission", Toast.LENGTH_LONG).show()

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSIONS_REQUEST
            )
        }
    }

    @RequiresApi(VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        if (requestCode == RECORD_AUDIO_PERMISSIONS_REQUEST) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "record audio permission granted", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(this, "record audio permission denied", Toast.LENGTH_SHORT)
                    .show()
                onBackPressed()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
