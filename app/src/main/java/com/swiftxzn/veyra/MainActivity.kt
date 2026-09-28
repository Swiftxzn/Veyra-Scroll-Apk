package com.swiftxzn.veyra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService

    // Elementos do Flash
    private lateinit var btnFlash: Button
    private lateinit var viewFlashOverlay: View

    // Variáveis configuráveis de sensibilidade e tempo
    private var lastScrollTime = 0L
    private var sensitivity = 18       // Sensibilidade inicial (modificável nos botões)
    private var scrollCooldown = 1500L // Tempo de cooldown em milissegundos

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permissão da câmera negada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Vincular componentes originais
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Vincular os novos componentes do XML modificados
        btnFlash = findViewById(R.id.btnFlash)
        viewFlashOverlay = findViewById(R.id.viewFlashOverlay)

        // Configurar a ação de clique do botão de Flash de Tela
        btnFlash.setOnClickListener {
            // Exibe o quadrado branco por cima de tudo
            viewFlashOverlay.visibility = View.VISIBLE

            // Oculta após 500 milissegundos (meio segundo) simulando um flash
            viewFlashOverlay.postDelayed({
                viewFlashOverlay.visibility = View.GONE
            }, 5000)
        }

        // Configurar os botões de ajuste de sensibilidade e cooldown adicionais
        setupControlButtons()

        // Verificar e iniciar a câmera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupControlButtons() {
        val txtSensitivity = findViewById<TextView>(R.id.txtSensitivity)
        val btnSensLess = findViewById<Button>(R.id.btnSensLess)
        val btnSensMore = findViewById<Button>(R.id.btnSensMore)

        val txtCooldown = findViewById<TextView>(R.id.txtCooldown)
        val btnCooldownLess = findViewById<Button>(R.id.btnCooldownLess)
        val btnCooldownMore = findViewById<Button>(R.id.btnCooldownMore)

        // Controle de Sensibilidade
        btnSensLess.setOnClickListener {
            if (sensitivity > 5) {
                sensitivity -= 1
                txtSensitivity.text = sensitivity.toString()
            }
        }
        btnSensMore.setOnClickListener {
            if (sensitivity < 40) {
                sensitivity += 1
                txtSensitivity.text = sensitivity.toString()
            }
        }

        // Controle de Cooldown (Tempo entre scrolls)
        btnCooldownLess.setOnClickListener {
            if (scrollCooldown > 200) {
                scrollCooldown -= 100
                txtCooldown.text = scrollCooldown.toString()
            }
        }
        btnCooldownMore.setOnClickListener {
            if (scrollCooldown < 5000) {
                scrollCooldown += 100
                txtCooldown.text = scrollCooldown.toString()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .build()

            val faceDetector = FaceDetection.getClient(options)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val face = faces[0]
                                val pitch = face.headEulerAngleX

                                runOnUiThread {
                                    // Utiliza a variável dinâmica 'sensitivity' ao invés do valor fixo 18
                                    when {
                                        pitch > sensitivity -> {
                                            statusText.text = "⬆ Cima"
                                            tryScroll("up")
                                        }
                                        pitch < -sensitivity -> {
                                            statusText.text = "⬇ Baixo"
                                            tryScroll("down")
                                        }
                                        else -> {
                                            statusText.text = "Olhando reto (${"%.1f".format(pitch)}°)"
                                        }
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    statusText.text = "Nenhum rosto detectado"
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("HeadScroll", "Erro na câmera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun tryScroll(direction: String) {
        val now = System.currentTimeMillis()
        // Utiliza a variável dinâmica 'scrollCooldown' regulada pelos botões
        if (now - lastScrollTime < scrollCooldown) return

        lastScrollTime = now

        val service = ScrollService.instance
        if (service != null) {
            if (direction == "down") {
                service.scrollDown()
                statusText.text = "⬇ Scroll"
            } else {
                service.scrollUp()
                statusText.text = "⬆ Scroll"
            }
        } else {
            statusText.text = "Ative o serviço de acessibilidade!"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
