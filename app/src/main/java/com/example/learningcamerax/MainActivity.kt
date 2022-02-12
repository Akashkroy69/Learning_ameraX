package com.example.learningcamerax

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    // A reference for the ImageCapture use case.
    private var imageCapture: ImageCapture? = null
    private var defaultCamBack: Boolean = true
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashOff: Boolean = true
    private var flashModeValue: Int = 0

    private lateinit var outputDirectory: File

    //AT the end we should shut down camera executor when the Activity is being destroyed to clean the resources
    //so that other app can use the cam hardware resource.

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
            Log.i("cam permission file: ", "$REQUEST_CODE_PERMISSIONS, $REQUIRED_PERMISSIONS")

        } else {
            //It implements code to request permissions that are made in AndroidManifest.xml
            // this code brings options of permissions to be granted by the user. After the user selects
            // something, as a result a callback method, onRequestPermissionsResult, will be called.
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }
        camSwitch.setOnClickListener {
            defaultCamBack = !defaultCamBack
            startCamera()
            Log.i("TAG1", "flashV: $flashModeValue flash: $flashOff")
        }
        flashSw.setOnClickListener {
            flashOff = !flashOff
            /* flashModeValue =
                 if (flashOff) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_ON
             startCamera()*/
            if (flashOff){
                //defaultCamBack = !defaultCamBack
                flashModeValue = ImageCapture.FLASH_MODE_OFF
                startCamera()
                Log.i("TAG1", "flashV: $flashModeValue flash: $flashOff")

            }else{

                defaultCamBack = true
                flashModeValue = ImageCapture.FLASH_MODE_ON
                startCamera()
                Log.i("TAG1", "flashV: $flashModeValue flash: $flashOff")

            }
        }
        //???
        outputDirectory = getOutputDirectory()

        //?????
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {
        //
        // Get a stable reference of the modifiable image capture use case.
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image. This creates a
        // format which is used to give a name to the image file.
        //Following line of code will create a file to hold an image.
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        // Create output options object which contains file + metadata/
        //Create an OutputFileOptions object. This object is where you
        // can specify things about how you want your output to be.
        // You want the output saved in the file we just created, so add your photoFile.
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken.
        //Calling takePicture() on the imageCapture object and
        // Passing in outputOptions, the executor, and a callback for when the image is saved or failed.
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            //In the case that the image capture fails or saving the image capture fails,
            // we are adding an error case to log that it failed
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                //If the capture doesn't fail, the photo was taken successfully!
                // we will save the photo to the file we created earlier, present a toast to let the user know
                // it was successful, and print a log statement.
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }

    //***** The code is added while learning from codelab
    private fun startCamera() {
        // our camerax is lifecycle aware because of which we don't need to write to open/close camera in onStart(),onPause(),onCreate(),
        // when Activity's orientation and configuration changes.

        // ProcessCameraProvider does that on behalf of us.
        // , but we need to bind instance of our camera provider (derived by camerax)
        // for our process/app with the lifecycle owner of the Activity in the context.

        // For any app one process is in the work in Android System and and for a process only one
        // 'process camera provider' exists. Using ProcessCameraProvider.getInstance(this) we can get
        // an instance of that camera provider for the process/app which can be used to bind with a
        // lifeCycleOwner, generally the current context which can be referred using this.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        //The Runnable interface should
        // be implemented by any class whose
        // instances are intended to be executed
        // by a thread.

        // Why a thread is being used here? Why can't the main thread or the process thread will do the work?
        // Probable Answer: In this case one main thread, i.e, process thread is working which has many responsibilities like
        // memory management, staying intact with hardware and sensors to keep them alive and then to bring input data that
        // are being stored in CPU Accessible buffers and much more. And then we want to perform another task of capturing
        // image from the stream of previews and of saving the image. This is quite a task. So we introduce a thread here using
        // Runnable.

        // ???????? Why after executing the following line, execution gets paused and after executing all the lines of onCreate()
        // it comes back to execute rest of the lines??????????????
        //cameraProviderFuture.addListener(listener:Runnable!,executor:Executor!)
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            //A Future represents the result of an asynchronous computation.
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview: Initialize your Preview object,
            // call build on it, get a surface provider from viewfinder, and then set it on the preview.
            //Question: What is Builder()??????
            val preview = Preview.Builder()
                .build()
                .also {
                    //The preview use case interacts with a Surface for display.
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            imageCapture = ImageCapture.Builder()
                .setFlashMode(flashModeValue)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    // we are calling a function LuminosityAnalyzer which takes a lambda function as a parameter, here the
                    // definition of the method LuminosityAnalyzer is using a typealias for the lambda function (luma:Double)->Unit
                    // How the following lines are getting executed?
                    // Actually LuminosityAnalyzer { luma -> Log.d("TAG", "Average Luminosity: $luma") } is like
                    // LuminosityAnalyzer ({ luma -> Log.d("TAG", "Average Luminosity: $luma") }). Value of luma is being assigned in the
                    // method LuminosityAnalyzer.
                    // Understand with this example: if a function is structured as below:
                    //fun walk1To(n: Int, action: (Int) -> Unit) =(1..n).forEach { action(it) }
                    //then, walk1To(5) {i-> print(i) } prints: 1,2,3,4,5.
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d("TAG", "Average Luminosity: $luma")
                    })
                }

            // code for cam selection
            cameraSelector = if (!defaultCamBack) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA


            try {
                // Unbind use cases before rebinding. This will unbind all the use cases from any
                // camerax which is opened now and will initiate close on all the opened cameras.
                cameraProvider.unbindAll()


                //Instead of an application placing specific start and stop method calls in onResume() and onPause(),
                // the application specifies a lifecycle to associate the camera with, using cameraProvider.bindToLifecycle().
                // That lifecycle then informs CameraX when to configure the camera capture session and ensures camera state
                // changes appropriately to match lifecycle transitions.
                // Also Binds use cases to camera.
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        //ContextCompat: helper for accessing features in context.
        //checkSelfPermission(context,string) is a method under ContextCompat.
        ContextCompat.checkSelfPermission(
            baseContext, it
            //PackageManager: is a class for retrieving various kinds of information related to the app/application package.
            // internally PERMISSION_GRANTED calls checkPermission() that returns permission result.
        ) == PackageManager.PERMISSION_GRANTED

    }

    // This is a call back method which is
    // called as a result of ActivityCompat.requestPermissions getting called.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }

    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val TAG1 = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    //inner class which contains the code for analyzing luminosity of the image frame coming in memory.
    //LumaListener is a typealias for a lambda function (luma:Double)->Unit
    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

}
