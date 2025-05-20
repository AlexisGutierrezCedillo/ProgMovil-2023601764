package com.example.pelota

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.activity.ComponentActivity
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import android.os.Bundle

// Actividad principal que implementa SensorEventListener para manejar el acelerómetro
class MainActivity : ComponentActivity(), SensorEventListener {

    // Instancia para gestionar los sensores del dispositivo
    private lateinit var sensorManager: SensorManager
    // Sensor de acelerómetro
    private var accelerometer: Sensor? = null
    // Vista personalizada donde se dibuja y anima la pelota
    private lateinit var ballView: BallView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializa la vista personalizada de la pelota
        ballView = BallView(this)
        // Establece la vista personalizada como contenido de la actividad
        setContentView(ballView)

        // Obtiene el servicio de sensores y el sensor de acelerómetro
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Método que recibe los cambios del sensor
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0] // Eje X
                val y = it.values[1] // Eje Y
                // Invierte el valor de x para adecuarlo a la dirección de la pantalla
                ballView.updatePosition(-x, y)
            }
        }
    }

    // Método requerido pero no utilizado para cambios en la precisión del sensor
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Clase interna que define la vista personalizada para animar la pelota
    class BallView(context: Context) : View(context) {

        // Paint para elementos como el agujero y textos
        private val paint = Paint().apply { isAntiAlias = true }
        // Paint exclusivo para la pelota
        private val ballPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE // Color inicial de la pelota
        }

        // Color de fondo de la vista (cuando no está en el agujero se usa un tono oscuro)
        private var backgroundColor: Int = Color.rgb(40, 40, 40)
        // Radio de la pelota
        private val radius = 50f
        // Posición actual de la pelota
        private var ballX = 0f
        private var ballY = 0f
        // Dimensiones de la pantalla
        private var screenWidth = 0
        private var screenHeight = 0

        // Velocidades actuales de la pelota en X y Y
        private var velX = 0f
        private var velY = 0f
        // Velocidad máxima permitida y factor de fricción
        private val maxSpeed = 500f
        private val friction = 0.98f

        // Instancia para reproducir sonidos y el ID del sonido de rebote
        private var soundPool: SoundPool
        private var bounceSound: Int = 0

        // Coordenadas y radio del "agujero" en pantalla
        private var holeX = 0f
        private var holeY = 0f
        private var holeRadius = 100f
        // Temporizador para cuando la pelota está en el agujero
        private var holeTimer: CountDownTimer? = null
        // Indica si la pelota se encuentra actualmente en el agujero
        private var isInHole = false

        // Puntuación del jugador y tiempo restante para el temporizador
        private var score = 0
        private var timeLeft = 5

        // Variables para controlar las colisiones y evitar múltiples eventos
        private var collidingLeft = false
        private var collidingRight = false
        private var collidingTop = false
        private var collidingBottom = false

        // Instancia del Vibrator para vibrar el dispositivo al chocar
        private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        init {
            // Configura los atributos de audio para efectos de juego
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Inicializa SoundPool para reproducir sonidos
            soundPool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build()

            // Carga el sonido de choque (asegúrate de que R.raw.choque exista en tus recursos)
            bounceSound = soundPool.load(context, R.raw.choque, 1)

            // Genera una posición aleatoria para el agujero
            generateHolePosition()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            screenWidth = w  // Actualiza el ancho de la pantalla
            screenHeight = h // Actualiza el alto de la pantalla
            // Inicializa la posición de la pelota en el centro
            ballX = (w / 2).toFloat()
            ballY = (h / 2).toFloat()
            generateHolePosition()
        }

        // Genera una posición aleatoria para el agujero, asegurando que esté dentro de la pantalla
        private fun generateHolePosition() {
            holeX = Random.nextFloat() * (screenWidth - 2 * holeRadius) + holeRadius
            holeY = Random.nextFloat() * (screenHeight - 2 * holeRadius) + holeRadius
        }

        // Actualiza la posición de la pelota basándose en los cambios de aceleración
        fun updatePosition(dx: Float, dy: Float) {
            if (screenWidth == 0 || screenHeight == 0) return

            // Actualiza las velocidades
            velX += dx * 2
            velY += dy * 2

            velX = velX.coerceIn(-maxSpeed, maxSpeed)
            velY = velY.coerceIn(-maxSpeed, maxSpeed)

            // Actualiza la posición
            ballX += velX
            ballY += velY

            // Calcula las colisiones con los bordes
            val newCollidingLeft = ballX - radius <= 0
            val newCollidingRight = ballX + radius >= screenWidth
            val newCollidingTop = ballY - radius <= 0
            val newCollidingBottom = ballY + radius >= screenHeight

            // Si ocurre una colisión (y no se estaba colisionando previamente)
            if ((newCollidingLeft && !collidingLeft) ||
                (newCollidingRight && !collidingRight) ||
                (newCollidingTop && !collidingTop) ||
                (newCollidingBottom && !collidingBottom)) {

                soundPool.play(bounceSound, 1f, 1f, 0, 0, 1f)
                changeColors()  // Cambia colores en colisión (solo si no está en el agujero)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(100)
                }
                invalidate() // Redibuja la vista
            }

            // Corrige la posición y velocidad para evitar que la pelota salga de la pantalla
            if (newCollidingLeft) {
                ballX = radius
                velX *= -0.8f
            }
            if (newCollidingRight) {
                ballX = screenWidth - radius
                velX *= -0.8f
            }
            if (newCollidingTop) {
                ballY = radius
                velY *= -0.8f
            }
            if (newCollidingBottom) {
                ballY = screenHeight - radius
                velY *= -0.8f
            }

            collidingLeft = newCollidingLeft
            collidingRight = newCollidingRight
            collidingTop = newCollidingTop
            collidingBottom = newCollidingBottom

            // Aplica fricción
            velX *= friction
            velY *= friction

            // Verifica si la pelota está en el agujero
            val inHole = isBallInHole()
            if (inHole) {
                if (!isInHole) {
                    // Al entrar en el agujero, inicia el temporizador y la transición de fondo
                    startHoleTimer()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pattern = longArrayOf(0, 1, 0)
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 1, 0), 0)
                    }
                    isInHole = true
                }
            } else {
                if (isInHole) {
                    // Si la pelota sale del agujero, se cancela el temporizador y se restablece el fondo
                    stopHoleTimer()
                    backgroundColor = Color.rgb(40, 40, 40)
                    vibrator.cancel()
                    isInHole = false
                }
            }

            invalidate() // Solicita redibujar la vista
        }

        // Inicia el temporizador de 5 segundos y actualiza gradualmente el fondo de oscuro a blanco
        private fun startHoleTimer() {
            holeTimer = object : CountDownTimer(5000, 50) {
                override fun onTick(millisUntilFinished: Long) {
                    // Actualiza el contador entero para mostrar en pantalla
                    timeLeft = (millisUntilFinished / 1000).toInt()
                    // Calcula la fracción de transición (0 = fondo oscuro, 1 = blanco)
                    val fraction = 1 - (millisUntilFinished.toFloat() / 5000f)
                    val colorValue = (fraction * 215+40).toInt().coerceIn(0, 255)
                    backgroundColor = Color.rgb(colorValue, colorValue, colorValue)
                    invalidate()
                }
                override fun onFinish() {
                    score++
                    generateHolePosition()
                    timeLeft = 0
                    backgroundColor = Color.WHITE
                    // Ajusta el tamaño del agujero en función del puntaje
                    holeRadius = (100f - (score * 8)).coerceIn(10f, 100f)
                    invalidate()
                }
            }
            holeTimer?.start()
        }

        // Detiene el temporizador
        private fun stopHoleTimer() {
            holeTimer?.cancel()
            holeTimer = null
        }

        // Cambia el color de la pelota en caso de colisión
        private fun changeColors() {
            ballPaint.color = Color.rgb(
                Random.nextInt(100, 256),
                Random.nextInt(100, 256),
                Random.nextInt(100, 256))
        }

        // Determina si la pelota está dentro del agujero
        private fun isBallInHole(): Boolean {
            val distance = sqrt((ballX - holeX).pow(2) + (ballY - holeY).pow(2))
            return distance - radius <= holeRadius
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Dibuja el fondo
            canvas.drawColor(backgroundColor)
            // Dibuja el agujero en color negro
            paint.color = Color.BLACK
            canvas.drawCircle(holeX, holeY, holeRadius, paint)
            // Dibuja el marcador de puntos
            paint.color = if (isBallInHole()) Color.BLACK else Color.WHITE
            paint.textSize = 50f
            canvas.drawText("Puntos: $score", 50f, 100f, paint)
            // Si la pelota está en el agujero, muestra el contador
            if (isBallInHole()) {
                paint.color = Color.argb(127, 40, 40, 40)
                paint.textSize = 150f
                canvas.drawText("$timeLeft", (screenWidth / 2 - 75f), (screenHeight / 2 + 50f), paint)
            }
            // Aplica transparencia a la pelota si está en el agujero
            ballPaint.alpha = if (isBallInHole()) 128 else 255
            canvas.drawCircle(ballX, ballY, radius, ballPaint)
        }
    }
}
