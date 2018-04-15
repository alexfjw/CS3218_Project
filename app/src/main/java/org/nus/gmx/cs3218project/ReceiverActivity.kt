package org.nus.gmx.cs3218project

import android.Manifest
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.nus.gmx.cs3218project.AlphanumEncoder

class ReceiverActivity : AppCompatActivity() {

    private val RECORD_AUDIO_PERMISSIONS_REQUEST = 1
    private var disposable: Disposable? = null
    private val detectedFrequencies: ArrayList<Float> = ArrayList()
    private val alphanumEncoder: AlphanumEncoder = AlphanumEncoder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)
    }

    override fun onResume() {
        super.onResume()
        disposable.let {
            // already has a disposable,
            // just exit... it means we already missed half the signal
            onBackPressed()
        }

        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            getPermissionToRecordAudioOrStartRecording()
        }
    }

    override fun onPause() {
        disposable?.dispose()
        super.onPause()
    }

    private fun createSamplingObservable(): Observable<Float> {
        var soundSampler: SoundSampler? = null

        return Observable.create<Float> { emitter ->
            try {
                soundSampler = SoundSampler(object: SoundSamplerCallback {
                    var started = false
                    override fun heardStartTransmission() {
                        started = true
                    }
                    override fun heardEndTransmission(sampler: SoundSampler) {
                        emitter.onComplete()
                        sampler.close()
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
                        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    }
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
        } else {
            startRecordingMessage()
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
                startRecordingMessage()
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
