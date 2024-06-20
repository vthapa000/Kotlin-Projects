package com.example.camera2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camera2.ui.theme.Camera2Theme
import com.example.camera2.ui.theme.CameraHelper
import com.example.camera2.ui.theme.CameraScreen

class MainActivity : ComponentActivity() {

private lateinit var cameraHelper: CameraHelper;
private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
    isGranted ->
    if(isGranted){
        setContent {
            Camera2Theme {
                CameraScreen(cameraHelper)
            }
        }
    }
    else{
        TODO("If permissions not granted")
    }
}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraHelper = CameraHelper(this);
        if(!hasPermissionRequired()){
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
        else{
        setContent {
            Camera2Theme {
                CameraScreen(cameraHelper)
            }
        }
        }
    }

    override fun onDestroy() {
        super.onDestroy();
        cameraHelper.stopProcessingFrames();
        cameraHelper.closeCamera();

    }


private fun hasPermissionRequired(): Boolean{
    val permissions = arrayOf(
        Manifest.permission.CAMERA
    )
    return permissions.all {
        ContextCompat.checkSelfPermission(
            applicationContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }
}

//private fun cameraPermissionRequired(){
//    if(ContextCompat.checkSelfPermission(this,
//        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
//        ActivityCompat.requestPermissions(this,
//            permissions, 0);
//    }
//}

}

