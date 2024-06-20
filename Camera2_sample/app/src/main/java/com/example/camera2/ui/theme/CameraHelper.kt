package com.example.camera2.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.HandlerThread
import android.view.TextureView
import android.os.Handler
import android.util.Log
import android.util.SparseArray
import android.view.Surface
import android.widget.ImageView
import android.widget.Toast
import com.example.camera2.ml.SsdMobilenetv11
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
//import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileOutputStream
import java.lang.RuntimeException
import java.nio.ByteBuffer

class CameraHelper (private val context: Context) {
    private lateinit var cameraDevice: CameraDevice;
    private lateinit var captureRequestBuilder: CaptureRequest.Builder;
    private lateinit var cameraCaptureSession: CameraCaptureSession;
    private var cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager;
    private var backgroundHandler: Handler? = null;
    private var backgroundHandlerThread: HandlerThread? = null;
    private lateinit var imageReader: ImageReader;
    private lateinit var textureView: TextureView;
    private lateinit var cameraId: String;
    private var isFrontCamera: Boolean = false;
    var isObjectDetection: Boolean = false;
    //private lateinit var objectDetectorHelper: ObjectDetectorHelper;
    private lateinit var imageProcessor: ImageProcessor;
    private lateinit var model: SsdMobilenetv11
    private val paint = Paint();
    private lateinit var labels:List<String>
    private var detectedObject: String = "";

    companion object{
        private val ORIENTATIONS = SparseArray<Int>();
        init {
            ORIENTATIONS.append(Surface.ROTATION_0,90);
            ORIENTATIONS.append(Surface.ROTATION_90,0);
            ORIENTATIONS.append(Surface.ROTATION_180,270);
            ORIENTATIONS.append(Surface.ROTATION_270,180);
        }
    }


    @SuppressLint("MissingPermission")  //already permissions check is done,refactor here if needed
    fun openCamera(textureView: TextureView){

        backgroundHandlerThread = HandlerThread("Camera").also { it.start() }
        backgroundHandler = Handler(backgroundHandlerThread!!.looper);
        this.textureView = textureView;
        try{
            //cameraId = cameraManager.cameraIdList[0];
            cameraId = getCameraId(); //back will be first
            cameraManager.openCamera(cameraId,
                object : CameraDevice.StateCallback(){
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera;
                        startPreview();
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        cameraDevice.close();
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        cameraDevice.close();
                    }
                },
                backgroundHandler
                )
        }
        catch (e: CameraAccessException){
            e.printStackTrace();
        }
    }

    private fun getCameraId(): String{
        for(id in cameraManager.cameraIdList){
            val characteristics = cameraManager.getCameraCharacteristics(id);
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if ((!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) ||
                isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT){
                return id
            }
        }
        throw RuntimeException("No camera found")
    }


    private fun startPreview(){
        labels = FileUtil.loadLabels(context, "labels.txt")
        val surfaceTexture: SurfaceTexture = textureView.surfaceTexture!!
        surfaceTexture.setDefaultBufferSize(textureView.width,textureView.height);
        val surface = Surface(surfaceTexture);
        imageReader = ImageReader.newInstance(textureView.width,textureView.height,ImageFormat.JPEG,1);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

        val imageReaderSurface = imageReader.surface;
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        captureRequestBuilder.addTarget(surface);
        cameraDevice.createCaptureSession(listOf(surface, imageReaderSurface), object : CameraCaptureSession.StateCallback(){
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session;
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                try{
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),null, backgroundHandler);
                }
                catch (e: CameraAccessException){
                    e.printStackTrace();
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                TODO("Not yet implemented")
            }
        },backgroundHandler)
    }


    fun processFrames(textureView: TextureView){
        //Log.d("OD", "Processing frames");
        model = SsdMobilenetv11.newInstance(context); //need labels for efficientdet0 TFlite model
        val bitmap = textureView.bitmap ?: return
        var image = TensorImage.fromBitmap(bitmap);
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(320,320,ResizeOp.ResizeMethod.BILINEAR)).build();
        image = imageProcessor.process(image);
        val outputs = model.process(image);
        val locations = outputs.locationsAsTensorBuffer.floatArray;
        val classes = outputs.classesAsTensorBuffer.floatArray;
        val scores = outputs.scoresAsTensorBuffer.floatArray;
        val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray;
        scores.forEachIndexed { index, score ->
            if(score > 0.70){
                //Log.d("OD", labels.get(classes.get(index).toInt()));
                var identifiedLabel: String = labels[classes[index].toInt()]
                if (detectedObject!= identifiedLabel) {
                    detectedObject = identifiedLabel;
                    var scorePercentage: Int= (score*100).toInt();
                    showToast("Object: $detectedObject, Confidence: $scorePercentage%");
                }
            }
        }
        //var textureCanvas = textureView.lockCanvas();
        //if(textureCanvas!=null){
        //textureCanvas.drawBitmap(mutable,0f, 0f, null);
        //textureView.unlockCanvasAndPost(textureCanvas);}

    }

    private fun showToast(message:String){
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

    }

    fun stopProcessingFrames(){
        try{
        model.close();
        }
        catch (e: Exception){
            e.printStackTrace();
        }

    }

    private fun startPreviewDetection(){
        val surfaceTexture: SurfaceTexture = textureView.surfaceTexture!!
        surfaceTexture.setDefaultBufferSize(textureView.width,textureView.height);
        val surface = Surface(surfaceTexture);
        //imageReader = ImageReader.newInstance(textureView.width,textureView.height,ImageFormat.YUV_420_888,1);
        //imageReader.setOnImageAvailableListener(onImageAvailableListener1, backgroundHandler);
        //val imageReaderSurface = imageReader.surface;
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        captureRequestBuilder.addTarget(surface);
        cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback(){
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session;
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                try{
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),null, backgroundHandler);
                }
                catch (e: CameraAccessException){
                    e.printStackTrace();
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                TODO("Not yet implemented")
            }
        },backgroundHandler)
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image: Image = reader.acquireLatestImage();
        val buffer: ByteBuffer = image.planes[0].buffer; //raw image data
        val bytes = ByteArray(buffer.remaining());
        buffer.get(bytes);

        //writes into package folder
//        val file = File(context.externalMediaDirs.first(), "image_${System.currentTimeMillis()}.jpg");
//        FileOutputStream(file).use{
//            it.write(bytes);
//        }
//        image.close();


        //writes into DCIM folder
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "image_${System.currentTimeMillis()}.jpg");
        FileOutputStream(file).use {
            it.write(bytes);
        }
        image.close();
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath),null){
            path,uri ->
        }


    }


    fun takePhoto(){
        try{
            Log.d("TakePhoto", "Photo captured");
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader.surface);
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,getJpegOrientation());
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback(){
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result);
                    startPreview();
                }
                        }, backgroundHandler);
        }catch (e: CameraAccessException){
            e.printStackTrace();
        }
    }

    //fix pic orientation as it is getting rotated by 90 so doing + 270
    private fun getJpegOrientation(): Int{
        val sensorOrientation = cameraManager.getCameraCharacteristics(cameraDevice.id)
            .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0;
        val display = context.display;
        val rotation = display?.rotation ?: 0;
        return (sensorOrientation + ORIENTATIONS.get(rotation) + 270) % 360;
    }


    fun switchCamera(textureView: TextureView){
        val TAG = "Switch Camera"
        Log.d(TAG, "switch called");
        isFrontCamera = !isFrontCamera;
        closeCamera();
        openCamera(textureView);
    }


    fun closeCamera(){
        try{
            cameraCaptureSession.close();
            cameraDevice.close();
            backgroundHandlerThread!!.quitSafely();
            backgroundHandlerThread?.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;

        }catch (e: Exception){
            e.printStackTrace();
        }
    }


     @SuppressLint("MissingPermission")
     fun startDetection(textureView: TextureView){
         closeCamera();
         isObjectDetection = true;
         backgroundHandlerThread = HandlerThread("Camera").also { it.start() }
         backgroundHandler = Handler(backgroundHandlerThread!!.looper);
         this.textureView = textureView;
         try{
             cameraId = cameraManager.cameraIdList[0]; //back will be first
             //cameraId = getCameraId();
             cameraManager.openCamera(cameraId,
                 object : CameraDevice.StateCallback(){
                     override fun onOpened(camera: CameraDevice) {
                         cameraDevice = camera;
                         startPreviewDetection();
                     }
                     override fun onDisconnected(camera: CameraDevice) {
                         cameraDevice.close();
                     }

                     override fun onError(camera: CameraDevice, error: Int) {
                         cameraDevice.close();
                     }
                 },
                 backgroundHandler
             )
         }
         catch (e: CameraAccessException){
             e.printStackTrace();
         }
    }

//    private fun drawBox(result: MutableList<Detection>){
//        val canvas = textureView.lockCanvas();
//        val paint = Paint();
//        paint.color = Color.WHITE
//        paint.strokeWidth = 2.0f
//
//        for (detection in result){
//            val box = detection.boundingBox
//            canvas?.drawRect(box, paint);
//            val label = detection.categories.firstOrNull()?.label
//            label?.let {
//                val textPainter = Paint();
//                textPainter.color = Color.RED;
//                textPainter.strokeWidth = 2.0f;
//                canvas?.drawText(label, box.left, box.top, textPainter);
//            }
//
//        }
//        textureView.unlockCanvasAndPost(canvas!!)
//    }

//
//
//    }
}