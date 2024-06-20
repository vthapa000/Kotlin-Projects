package com.example.camera2.ui.theme

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera2.R
import androidx.compose.runtime.*

@Composable
fun CameraScreen(cameraHelper: CameraHelper) {
    val context = LocalContext.current;
    val textureView = remember {
        TextureView(context)
    }
//    val cameraHelper = remember {
//        CameraHelper(context)
//    }
//    var isFrontCamera by remember {
//        mutableStateOf(false)
//    }
    var isObjectDetection by remember {
            mutableStateOf(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(cameraHelper, textureView, Modifier.fillMaxSize())
        Row(modifier = Modifier
            .align(Alignment.BottomCenter)
            //.padding(12.dp)
            .background(Color.White)
            .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly)
            {
            IconButton(
                onClick = {
                //isFrontCamera = !isFrontCamera;
                cameraHelper.switchCamera(textureView);
            },
                enabled = !isObjectDetection
                ) {
                Icon(painter = if (!isObjectDetection) painterResource(R.drawable.flip_camera)
                    else painterResource(R.drawable.disabled),
                    contentDescription = "Camera switch",
                    tint = Color.LightGray)
            }
            IconButton(
                onClick = {
                    cameraHelper.takePhoto()
                },
                enabled = !isObjectDetection
            ) {
                Icon(painter = if(!isObjectDetection) painterResource(R.drawable.camera_shutter)
                    else painterResource(R.drawable.disabled),
                    contentDescription = "Click Photo",
                    tint = Color.LightGray)
            }
            IconButton(
                onClick = {
                    isObjectDetection = !isObjectDetection;
                    if(!isObjectDetection){
                        cameraHelper.isObjectDetection = false; //remove this flag setting to run processing on front cam
                        cameraHelper.stopProcessingFrames();    //for front cam OD, don't close model
                        cameraHelper.closeCamera();
                        cameraHelper.openCamera(textureView);
                    }
                    else{
                        cameraHelper.startDetection(textureView);
                    }
                }
            ) {
                Icon(painter = if (!isObjectDetection) painterResource(R.drawable.object_detection)
                    else painterResource(R.drawable.back_arrow),
                    contentDescription = "Detection",
                    tint = Color.LightGray)
            }
        }
    }
}



@Composable
fun CameraPreview(cameraHelper: CameraHelper, textureView: TextureView, modifier: Modifier){
    AndroidView(
        factory ={
            textureView.apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener{
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        cameraHelper.openCamera(this@apply);
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        cameraHelper.closeCamera();
                        return true;
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        if(cameraHelper.isObjectDetection){
                            cameraHelper.processFrames(this@apply)
                        }
                    }

                }
            }
        } )
}


