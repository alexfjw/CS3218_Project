package org.nus.gmx.cs3218project

import android.util.Log
import io.reactivex.Observable

class SamplingObservable {

    companion object {
        private val TAG = "SamplingObservable"

        /**
         * Sampling observable initializes SoundSampler and watches for 3 events:
         * heardStartTransmission(), heardEndTransmission() & heardFrequency()
         */
        fun createSamplingObservable(): Observable<Float> {
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
                    soundSampler?.close()
                    emitter.onError(e)
                }
            }
        }

    }
}
