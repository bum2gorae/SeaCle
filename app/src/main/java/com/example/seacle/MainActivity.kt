package com.example.seacle

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.seacle.ui.theme.SeaCleTheme

private fun checkCameraPermission(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}


private fun checkInternetPermission(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.INTERNET
    ) == PackageManager.PERMISSION_GRANTED
}



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeaCleTheme {
                val context = LocalContext.current
                val contextAct = LocalContext.current as Activity?
                val hasInternetPermission by remember { mutableStateOf(checkInternetPermission(context)) }
                var hasCameraPermission by remember { mutableStateOf(checkCameraPermission(context)) }
                val cameraPermissionRequest = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { permissions ->
                    hasCameraPermission = permissions
                    Log.d("Permissions", "Camera permission granted: $permissions")
                }
                if (hasCameraPermission && hasInternetPermission) {
                    Log.d("Permissions", "Camera permission already granted")
                } else if (!hasCameraPermission) {
                    Log.d("Permissions", "Requesting camera permission")
                    LaunchedEffect(Unit) {
                        cameraPermissionRequest.launch(Manifest.permission.CAMERA)
                    }
                }
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
                    horizontalAlignment = Alignment.End) {
                    Button(onClick = {
                        val intent = Intent(contextAct, VideoActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Text(text = "시작")
                    }
                }
            }
        }
    }
}
