package lesha300398.emotions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import java.nio.ByteBuffer
import android.media.*
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.custom.*
import com.google.firebase.storage.FirebaseStorage
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.sauronsoftware.jave.AudioAttributes
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit
import it.sauronsoftware.jave.Encoder
import it.sauronsoftware.jave.EncodingAttributes
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object {
        private const val RECORD_PERMISSION_REQUEST_CODE = 12345

        private val LABELS = listOf(
            "Male Sad",
            "Male Angry",
            "Male Disgust",
            "Male Fear",
            "Male Happy",
            "Male Neutral",
            "Female Sad",
            "Female Angry",
            "Female Disgust",
            "Female Fear",
            "Female Happy",
            "Female Neutral",
            "Nothing"
        )
    }

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        AndroidFFMPEGLocator(this)
        setContentView(R.layout.activity_main)
        loadModels()

        record_button.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    Array<String>(1) { Manifest.permission.RECORD_AUDIO },
                    RECORD_PERMISSION_REQUEST_CODE
                )
            } else {
                record()
            }
        }

        cancel_button.setOnClickListener {
            compositeDisposable.clear()
        }

//        if (!Python.isStarted()) {
//            Python.start(AndroidPlatform(this))
//        }

//        val f = File.createTempFile("11111111111", null, cacheDir)
//        val os = FileOutputStream(f, false)
//
//        val inputStream = assets.open("1001_DFA_ANG_XX+4-161099-A-47.wav")

//        val buffer = ByteArray(100 * 1024)
//        var len = 0;
//        while (true) {
//            len = inputStream.read(buffer)
//            if (len == -1) {
//                break
//            }
//            os.write(buffer, 0, len)
//        }
//
//        val python = Python.getInstance()
//        val pythonFile = python.getModule("mix_preprocessing")
//        val helloWorldString = pythonFile.callAttr("get_X", f.absolutePath)

        when {
            intent?.action == Intent.ACTION_SEND -> {

                if (intent.type?.startsWith("audio/") == true) {
                    val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri

                    val encoder = Encoder()

                    val audioAttributes = AudioAttributes()
                    audioAttributes.setChannels(1)
                    audioAttributes.setSamplingRate(44100)

                    val attributes = EncodingAttributes()
                    attributes.setAudioAttributes(audioAttributes)
                    attributes.setFormat("wav")


                    val f2 = File.createTempFile("11111111111.wav", null, cacheDir)
                    val f1 = File.createTempFile("1234", null, cacheDir)


                    val inputStream = contentResolver.openInputStream(uri)!!
                    val outputStream = FileOutputStream(f1)
                    val buf = ByteArray(1024)
                    var len = 0
                    while (true) {
                        len = inputStream.read(buf)
                        if (len <= 0) {
                            break
                        }
                        outputStream.write(buf, 0, len)
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    encoder.encode(f1, f2, attributes)

                    val extractor = MediaExtractor()

                    extractor.setDataSource(this, f2.toUri(), null)
                    val trackCount = extractor.trackCount
                    extractor.selectTrack(0)
                    val format = extractor.getTrackFormat(0)
                    val inputBuffer = ByteBuffer.allocate(128 * 1024 * 1024)
                    var offset = 0;
                    while (extractor.readSampleData(inputBuffer, offset) >= 0) {
                        offset += extractor.readSampleData(inputBuffer, offset)
                        val array = inputBuffer.array()
                        val trackIndex = extractor.sampleTrackIndex
                        val presentationTimeUs = extractor.sampleTime
                        extractor.advance()
                    }
                    inputBuffer.rewind()
                    val shorts = inputBuffer.asShortBuffer()
                    val shortArray = ShortArray(44100 * 5)
                    shorts[shortArray]
                    true
                }
            }
        }
    }

    private fun record() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minInternalBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig, audioFormat
        ) + 100
        val internalBufferSize = minInternalBufferSize * 4
//        Log.d(
//            FragmentActivity.TAG, "minInternalBufferSize = " + minInternalBufferSize
//                    + ", internalBufferSize = " + internalBufferSize
//                    + ", myBufferSize = " + myBufferSize
//        )


        val single = Single.fromCallable {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, internalBufferSize
            )

            audioRecord.startRecording()
            val shortArray = ShortArray(44100 * 5)
            val x = audioRecord.read(shortArray, 0, 44100 * 5, AudioRecord.READ_BLOCKING)
            audioRecord.stop()
            audioRecord.release()
            shortArray
        }.subscribeOn(Schedulers.io())


        Observable.interval(100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .take(53)
            .doOnSubscribe {
                record_progress.visibility = View.VISIBLE
                record_button.isEnabled = false
                cancel_button.isEnabled = true
            }
            .doFinally {
                record_progress.visibility = View.INVISIBLE
                cancel_button.isEnabled = false
            }
            .subscribe {
                record_progress.progress = (it.toFloat() / 52 * 100).toInt()
            }
            .disp()

        single.subscribe { arr ->
            arr
        }.disp()

        Completable.timer(200, TimeUnit.MILLISECONDS)
            .andThen(single)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                cancel_button.isEnabled = false
                results_progress.visibility = View.VISIBLE
                spectrogram_progress.visibility = View.VISIBLE

                spectrogram_image.setImageBitmap(null)

                model1_container.removeAllViews()
                model2_container.removeAllViews()

            }
            .observeOn(Schedulers.computation())
            .map { arr ->
                Spectrogram().spectrogram(arr)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                spectrogram_progress.visibility = View.GONE
                val bitmap = bitmapFromArray(it)
                spectrogram_image.setImageBitmap(bitmap)
            }
            .doFinally {
                record_button.isEnabled = true
                label_button.isEnabled = false
            }
            .flatMap { spectrogram ->
                Completable.create { compl ->
                    val emotionsOnlyModel =
                        FirebaseCustomRemoteModel.Builder("emotions_only").build()
                    val emotionsAndNothingModel =
                        FirebaseCustomRemoteModel.Builder("emotions_and_nothing").build()
                    val interpreterEmotionsOnly = FirebaseModelInterpreter.getInstance(
                        FirebaseModelInterpreterOptions.Builder(emotionsOnlyModel).build()
                    )!!
                    val interpreterEmotionsAndNothing = FirebaseModelInterpreter.getInstance(
                        FirebaseModelInterpreterOptions.Builder(emotionsAndNothingModel).build()
                    )!!

                    val input = Array(1) { Array(128) { Array(512) { FloatArray(1) } } }

                    for (i in spectrogram.indices) {
                        for (j in spectrogram[i].indices) {
                            input[0][j][i][0] = spectrogram[i][j].toFloat()
                        }
                    }
                    val inputs = FirebaseModelInputs.Builder()
                        .add(input)
                        .build()
                    val inputOutputOptions1 = FirebaseModelInputOutputOptions.Builder()
                        .setInputFormat(
                            0,
                            FirebaseModelDataType.FLOAT32,
                            intArrayOf(1, 128, 512, 1)
                        )
                        .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 12))
                        .build()
                    val inputOutputOptions2 = FirebaseModelInputOutputOptions.Builder()
                        .setInputFormat(
                            0,
                            FirebaseModelDataType.FLOAT32,
                            intArrayOf(1, 128, 512, 1)
                        )
                        .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 13))
                        .build()

                    var modelsDone = 0
                    interpreterEmotionsOnly.run(inputs, inputOutputOptions1)
                        .addOnSuccessListener { result ->
                            val output = result.getOutput<Array<FloatArray>>(0)
                            val probabilities = output[0]
                            if (!compl.isDisposed) {
                                showProbabilities(probabilities, model1_container)
                                if (++modelsDone >= 2) {
                                    compl.onComplete()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            compl.onError(e)
                            Log.e(null, e.message, e)
                        }

                    interpreterEmotionsAndNothing.run(inputs, inputOutputOptions2)
                        .addOnSuccessListener { result ->
                            val output = result.getOutput<Array<FloatArray>>(0)
                            val probabilities = output[0]
                            if (!compl.isDisposed) {
                                showProbabilities(probabilities, model2_container)
                                if (++modelsDone >= 2) {
                                    compl.onComplete()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            compl.onError(e)
                            Log.e(null, e.message, e)
                        }
                }.andThen(Single.just(spectrogram))
            }
            .subscribe { spectrogram ->
                label_button.isEnabled = true
                label_button.setOnClickListener {
                    val builderSingle = AlertDialog.Builder(this);
                    builderSingle.setTitle("Select One Label:");

                    val arrayAdapter =
                        ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice)
                    arrayAdapter.addAll(LABELS)

                    builderSingle.setNegativeButton("Cancel") { d, i ->
                        d.dismiss()
                    }

                    builderSingle.setAdapter(arrayAdapter) { d, i ->

                        val builderInner = AlertDialog.Builder(this);
                        builderInner.setMessage(LABELS[i])
                        builderInner.setTitle("Your Selected Label is");
                        builderInner.setPositiveButton("Submit") { d, _ ->
                            //i - selected label
                            val storage = FirebaseStorage.getInstance()
                            val storageRef = storage.reference


                            val dataRef = storageRef.child("data")

                            val name = UUID.randomUUID().toString() + "_%02d.csv".format(i)

                            val fileRef = dataRef.child(name)

                            val stringBuilder = StringBuilder()

                            for (i in spectrogram[0].indices){
                                for (j in spectrogram.indices) {
                                    stringBuilder.append("%.20f,".format(spectrogram[j][i]))
                                }
                                stringBuilder.append("\n")
                            }
                            fileRef.putBytes(stringBuilder.toString().toByteArray(Charset.forName("UTF-8")))


                            label_button.isEnabled = false
                            d.dismiss()

                        }
                        builderInner.setNegativeButton("Cancel") { d, _ ->
                            d.dismiss()
                        }
                        builderInner.show()
                        d.dismiss()

                    }
                    builderSingle.show()
                }
            }
            .disp()


    }

    private fun bitmapFromArray(pixels2d: Array<DoubleArray>): Bitmap {
        val width = pixels2d.size
        val height = pixels2d[0].size
        val pixels = IntArray(width * height)
        var pixelsIndex = 0
        for (i in 0 until height) {
            for (j in 0 until width) {
                val value = ((pixels2d[j][height - 1 - i].toFloat() + 80) / 80 * 255).toInt()

                val red = (value shl 16) and 0x00FF0000 //Shift red 16-bits and mask out other stuff
                val green =
                    (value shl 8) and 0x0000FF00 //Shift Green 8-bits and mask out other stuff
                val blue = value and 0x000000FF //Mask out anything not blue.
                val colorInt: Int = (0x00FF0000 shl 8) or red or green or blue
                pixels[pixelsIndex] = colorInt
                pixelsIndex++
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }


    private fun showProbabilities(probabilities: FloatArray, container: LinearLayout) {
        results_progress.visibility = View.GONE
        container.removeAllViews()

        val maxProb = probabilities.max()

        for (i in probabilities.indices) {

            val textView = TextView(this)
            textView.text = LABELS[i] + ": %.2f%%".format(probabilities[i] * 100)
            if (probabilities[i] == maxProb) {
                textView.setTypeface(textView.typeface, Typeface.BOLD_ITALIC)
            }
            container.addView(textView)
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            RECORD_PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    record()

                } else {
//                    Log.d("TAG", "permission denied by user")
                }
                return
            }
        }
    }

    private fun loadModels() {
        val conditionsBuilder: FirebaseModelDownloadConditions.Builder =
            FirebaseModelDownloadConditions.Builder()//.requireWifi()

        val conditions = conditionsBuilder.build()

        val emotionsOnlyModel = FirebaseCustomRemoteModel.Builder("emotions_only").build()
        val emotionsAndNothingModel =
            FirebaseCustomRemoteModel.Builder("emotions_and_nothing").build()

        var loadedModels = 0

        FirebaseModelManager.getInstance().download(emotionsOnlyModel, conditions)
            .addOnCompleteListener {
                Log.d("FirebaseModels", "EmotionsOnly loaded")
                if (++loadedModels >= 2) {
                    record_button.isEnabled = true
                }
            }

        FirebaseModelManager.getInstance().download(emotionsAndNothingModel, conditions)
            .addOnCompleteListener {
                Log.d("FirebaseModels", "EmotionsAndNothing loaded")
                if (++loadedModels >= 2) {
                    record_button.isEnabled = true
                }
            }


    }

    private fun Disposable.disp() {
        compositeDisposable.add(this)
    }
//    private fun getSpectrogram(intent: Intent) {
//        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.also {
//            val disp = AudioDispatcherFactory.fromPipe(it.path, 44100, 5000, 2500)
//            val mfcc = MFCC(512, 44100)
//            disp.addAudioProcessor(mfcc)
//            disp.addAudioProcessor(object : AudioProcessor {
//
//                override fun processingFinished() {}
//
//                override fun process(audioEvent: AudioEvent): Boolean {
//                    val mfcc1 = mfcc.mfcc
//                    return true
//                }
//            })
//            disp.run()
//
//            // Update UI to reflect image being shared
//        }
//    }
}
