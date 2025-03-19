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

    // Metodo de creación de la actividad
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

    // Registra el listener del acelerómetro al reanudarse la actividad
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    // Desregistra el listener cuando la actividad se pausa
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Método que recibe los cambios del sensor
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // Verifica que el sensor sea el acelerómetro
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0] // Obtiene el valor en el eje X
                val y = it.values[1] // Obtiene el valor en el eje Y
                // Invierte el valor de x para adecuarlo a la dirección de la pantalla
                ballView.updatePosition(-x, y)
            }
        }
    }

    // Método requerido pero no utilizado para cambios en la precisión del sensor
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Clase interna que define la vista personalizada para animar la pelota
    class BallView(context: Context) : View(context) {

        // Paint general para dibujar elementos no relacionados a la pelota (agujero, textos)
        private val paint = Paint().apply { isAntiAlias = true }

        // Paint exclusivo para la pelota, para conservar su color de forma independiente
        private val ballPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE // Color inicial de la pelota
        }

        // Color de fondo de la vista
        private var backgroundColor: Int = Color.BLACK
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
        // Velocidad máxima permitida y factor de fricción para desacelerar
        private val maxSpeed = 500f
        private val friction = 0.98f

        // Instancia para reproducir sonidos y el ID del sonido de rebote
        private var soundPool: SoundPool
        private var bounceSound: Int = 0

        // Coordenadas y radio del "agujero" en pantalla (puede usarse para otra lógica)
        private var holeX = 0f
        private var holeY = 0f
        private var holeRadius = 100f
        // Temporizador para la interacción cuando la pelota está en el agujero
        private var holeTimer: CountDownTimer? = null
        // Indica si la pelota se encuentra actualmente en el agujero
        private var isInHole = false

        // Puntuación del jugador y tiempo restante para el temporizador del agujero
        private var score = 0
        private var timeLeft = 5

        // Variables para controlar las colisiones y evitar múltiples disparos de evento
        private var collidingLeft = false
        private var collidingRight = false
        private var collidingTop = false
        private var collidingBottom = false

        // Instancia del Vibrator para hacer vibrar el dispositivo al chocar
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

        // Se llama cuando cambia el tamaño de la vista (por ejemplo, al iniciar)
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            screenWidth = w  // Actualiza el ancho de la pantalla
            screenHeight = h // Actualiza el alto de la pantalla
            // Inicializa la posición de la pelota en el centro de la pantalla
            ballX = (w / 2).toFloat()
            ballY = (h / 2).toFloat()
            // Regenera la posición del agujero
            generateHolePosition()
        }

        // Genera una posición aleatoria para el agujero, asegurando que esté dentro de la pantalla
        private fun generateHolePosition() {
            holeX = Random.nextFloat() * (screenWidth - 2 * holeRadius) + holeRadius
            holeY = Random.nextFloat() * (screenHeight - 2 * holeRadius) + holeRadius
        }

        // Actualiza la posición de la pelota basándose en los cambios de aceleración
        fun updatePosition(dx: Float, dy: Float) {
            // Si las dimensiones aún no se han establecido, no hace nada
            if (screenWidth == 0 || screenHeight == 0) return

            // Actualiza las velocidades incrementando con el valor del sensor multiplicado para mayor efecto
            velX += dx * 2
            velY += dy * 2

            // Limita la velocidad para que no exceda la máxima permitida
            velX = velX.coerceIn(-maxSpeed, maxSpeed)
            velY = velY.coerceIn(-maxSpeed, maxSpeed)

            // Actualiza la posición sumando la velocidad
            ballX += velX
            ballY += velY

            // Calcula las condiciones de colisión con cada borde
            val newCollidingLeft = ballX - radius <= 0
            val newCollidingRight = ballX + radius >= screenWidth
            val newCollidingTop = ballY - radius <= 0
            val newCollidingBottom = ballY + radius >= screenHeight

            // Si ocurre una colisión (y no se estaba colisionando previamente), reproduce sonido, cambia colores y vibra
            if ((newCollidingLeft && !collidingLeft) ||
                (newCollidingRight && !collidingRight) ||
                (newCollidingTop && !collidingTop) ||
                (newCollidingBottom && !collidingBottom)) {

                // Reproduce el sonido de rebote
                soundPool.play(bounceSound, 1f, 1f, 0, 0, 1f)
                // Cambia los colores de fondo y de la pelota
                changeColors()
                // Hace vibrar el dispositivo por 100 milisegundos
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(100)
                }
                invalidate() // Redibuja la vista
            }

            // Corrige la posición y la velocidad en caso de colisión para que la pelota no salga de la pantalla
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

            // Actualiza el estado de las colisiones para evitar múltiples eventos
            collidingLeft = newCollidingLeft
            collidingRight = newCollidingRight
            collidingTop = newCollidingTop
            collidingBottom = newCollidingBottom

            // Aplica fricción a las velocidades para desacelerar gradualmente la pelota
            velX *= friction
            velY *= friction

            // Determina si la pelota está en el "agujero" (lógica de juego)
            val inHole = isBallInHole()
            if (inHole) {
                if (!isInHole) {
                    startHoleTimer()//Se inicia el contador
                    changeColors() // Hace cambiar a negro el color del fondo mientras no esté dentro del agujero
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pattern = longArrayOf(0, 1, 0) // Espera 0ms, vibra 500ms, pausa 500ms, repite
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)) // Se repite desde índice 0
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 1, 0), 0) // Para Android < 8.0
                    }
                    isInHole = true
                }
            } else {
                if (isInHole) {
                    stopHoleTimer()
                    changeColors() // Hace cambiar a blanco el color del fondo mientras esté dentro del agujero
                    vibrator.cancel() // Detiene la vibración
                    isInHole = false
                }
            }

            invalidate() // Solicita redibujar la vista con la nueva posición
        }

        // Inicia un temporizador de 5 segundos mientras la pelota se encuentra en el agujero
        private fun startHoleTimer() {
            holeTimer = object : CountDownTimer(5000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeft = (millisUntilFinished / 1000).toInt()
                }
                override fun onFinish() {
                    score++
                    changeColors()
                    generateHolePosition()
                    timeLeft = 5
                    // Ajusta el tamaño del agujero en función del puntaje, limitándolo entre 10 y 100
                    holeRadius = (100f - (score * 8)).coerceIn(10f, 100f)
                }
            }
            holeTimer?.start()
        }

        // Detiene el temporizador cuando la pelota sale del agujero
        private fun stopHoleTimer() {
            holeTimer?.cancel()
            holeTimer = null
        }

        // Cambia el color de fondo y el de la pelota al ocurrir una colisión
        private fun changeColors() {
            if(isBallInHole()){
                // Nuevo color de fondo con tonos oscuros
                backgroundColor = Color.rgb(255,255,255)
            }else{
                // Nuevo color de fondo con tonos oscuros
                backgroundColor = Color.rgb(40,40,40)
                // Nuevo color para la pelota con tonos vivos
                ballPaint.color = Color.rgb(
                    Random.nextInt(100, 256),
                    Random.nextInt(100, 256),
                    Random.nextInt(100, 256)
                )
            }

        }

        // Determina si la pelota está dentro del agujero calculando la distancia entre sus centros
        private fun isBallInHole(): Boolean {
            val distance = sqrt((ballX - holeX).pow(2) + (ballY - holeY).pow(2))
            return distance - radius <= holeRadius
        }

        // Método que dibuja en la vista
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Dibuja el fondo con el color actual
            canvas.drawColor(backgroundColor)
            // Dibuja el agujero (en este ejemplo, con color negro)
            paint.color = Color.BLACK
            canvas.drawCircle(holeX, holeY, holeRadius, paint)
            // Dibuja el marcador de puntos
            if(isBallInHole()){
                paint.color = Color.BLACK
            }else{
                paint.color = Color.WHITE
            }
            paint.textSize = 50f
            canvas.drawText("Puntos: $score", 50f, 100f, paint)
            // Si la pelota está en el agujero, dibuja el contador de tiempo con semitransparencia
            if (isBallInHole()) {
                paint.color = Color.argb(127, 1, 1, 1)
                paint.textSize = 150f
                canvas.drawText("$timeLeft", (screenWidth / 2 - 75f), (screenHeight / 2 + 50f), paint)
            }
            // Aplica transparencia al color de la pelota si se encuentra en el agujero (50% opacidad)
            ballPaint.alpha = if (isBallInHole()) 128 else 255
            // Dibuja la pelota en su posición actual
            canvas.drawCircle(ballX, ballY, radius, ballPaint)
        }
    }
}
