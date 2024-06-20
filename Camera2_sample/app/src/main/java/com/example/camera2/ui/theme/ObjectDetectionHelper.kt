//package com.example.camera2.ui.theme
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.os.Handler
//import android.os.HandlerThread
//import android.os.SystemClock
//import android.util.Log
//import org.tensorflow.lite.gpu.CompatibilityList
//import org.tensorflow.lite.support.image.ImageProcessor
//import org.tensorflow.lite.support.image.TensorImage
//import org.tensorflow.lite.support.image.ops.ResizeOp
//import org.tensorflow.lite.support.image.ops.Rot90Op
//import org.tensorflow.lite.task.core.BaseOptions
//import org.tensorflow.lite.task.vision.detector.Detection
//import org.tensorflow.lite.task.vision.detector.ObjectDetector
//
//
//class ObjectDetectorHelper(
//    var threshold: Float = 0.5f,
//    var numThreads: Int = 2,
//    var maxResults: Int = 2,
//    var currentDelegate: Int = 0,
//    //var currentModel: Int = MODEL_EFFICIENTDETV0,
//    val context: Context,
//    val objectDetectorListener: DetectorListener?,
//    private var imageProcessor: ImageProcessor? = null
//){
//    private var objectDetector: ObjectDetector? = null;
//    private var backgroundHandler: Handler? = null;
//    private var backgroundHandlerThread: HandlerThread?= null;
//
//    companion object {
//        const val DELEGATE_CPU = 0
//        const val DELEGATE_GPU = 1
//        const val DELEGATE_NNAPI = 2
//        const val MODEL_EFFICIENTDETV0 = 3
//    }
//
//    init{
//        setupObjectDetector()
//    }
//
//    interface DetectorListener{
//        fun onError(message: String)
//        fun onResult(result: MutableList<Detection>?,
//                     inferenceTime: Long,
//                     imageHeight: Int,
//                     imageWidth: Int)
//    }
//
//    private fun setupObjectDetector(){
//        Log.d("Setup", "Started")
//        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)
//        val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
//            .setScoreThreshold(threshold)
//            .setMaxResults(maxResults)
//
//        when (currentDelegate) {
//            DELEGATE_CPU -> {
//                Log.d("OD","CPU delegate")
//            }
//            DELEGATE_GPU -> {
//                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
//                    baseOptionsBuilder.useGpu()
//                } else {
//                    Log.d("GPU", "GPU not available")
//                }
//            }
//            DELEGATE_NNAPI -> {
//                baseOptionsBuilder.useNnapi()
//            }
//        }
//        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())
//        val modelName = "ssd-mobilenetv11.tflite";
//
//        try {
//            objectDetector =
//                ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())
//            Log.d("OD", objectDetector.toString())
//        } catch (e: IllegalStateException) {
//            Log.d("GPU", e.message!!)
//        }
//    }
//
//    fun detectImage(image: Bitmap, imageRotation: Int){
//        if (objectDetector == null) {
//            setupObjectDetector()
//        }
//        Log.d("Detect","Detected image")
//        var inferenceTime = SystemClock.uptimeMillis();
//        val processedImage = imageProcessor(image, imageRotation)
//
//        val results = objectDetector?.detect(processedImage);
//        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
//        objectDetectorListener?.onResult(results,inferenceTime,processedImage.height, processedImage.width);
//
//
//    }
//
//    private fun imageProcessor(image: Bitmap, rotation: Int): TensorImage {
//        Log.d("Image processed","Complete")
//        val imageProcessor =
//            ImageProcessor.Builder()
//                .add(ResizeOp(320,320, ResizeOp.ResizeMethod.BILINEAR))
//                .add(Rot90Op(-rotation / 90))
//                .build()
//        return imageProcessor.process(TensorImage.fromBitmap(image));
//
//    }
//
//
//     fun clearObjectDetector(){
//        objectDetector = null;
//    }
//
//
//
//
//
//}