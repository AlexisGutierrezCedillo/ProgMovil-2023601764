import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

fun main() {
    var seleccionMenu = 0

    do {
        println("\n--- MENU ---")
        println("1. Sumar tres números")
        println("2. Ingresar nombre completo")
        println("3. Calcular tiempo vivido")
        println("4. Salir")
        println("Opción: ")

        seleccionMenu = readLine()?.toIntOrNull() ?: 0

        when (seleccionMenu) {
            1 -> sumar()
            2 -> ingresarNombre()
            3 -> calcularTiempo()
            4 -> salir()
            else -> println("Opción no válida, intente de nuevo.")
        }

    } while (seleccionMenu != 4)
}

fun sumar() {
    println("Ingrese tres números: ")

    print("Número 1: ")
    val numero1 = readLine()?.toDoubleOrNull() ?: 0.0

    print("Número 2: ")
    val numero2 = readLine()?.toDoubleOrNull() ?: 0.0

    print("Número 3: ")
    val numero3 = readLine()?.toDoubleOrNull() ?: 0.0

    val resultadoSuma = numero1 + numero2 + numero3

    println("La suma es: $resultadoSuma")
}

fun ingresarNombre() {
    println("Ingresa tu nombre completo: ")

    val nombreIngresado = readLine()?.trim() ?: "Nombre no ingresado"

    println("Bienvenido $nombreIngresado")
}

fun calcularTiempo() {
    println("Ingrese su fecha de nacimiento (YYYY-MM-DD): ")
    val fechaIngreso = readLine()

    try {
        val nacimiento = LocalDate.parse(fechaIngreso)  // Convertir texto a fecha
        val hoy = LocalDate.now()  // Fecha de hoy

        if (nacimiento.isAfter(hoy)) {
            println("¡No puedes poner una fecha de nacimiento después de hoy!")
            return
        }

        val tiempoVida = Period.between(nacimiento, hoy)
        val totalDias = ChronoUnit.DAYS.between(nacimiento, hoy)
        val totalHoras = totalDias * 24
        val totalMinutos = totalHoras * 60
        val totalSegundos = totalMinutos * 60

        println("\nTu tiempo vivido es:")
        println(" ${tiempoVida.years} años, ${tiempoVida.months} meses y ${tiempoVida.days} días.")
        println(" Un total de $totalDias días.")
        println(" Aproximadamente $totalHoras horas, $totalMinutos minutos y $totalSegundos segundos.")

    } catch (e: Exception) {
        println("¡Datos invalidos! Prueba poner YYYY-MM-DD")
    }
}

fun salir() {
    println("Gracias por usar el programa. ¡Hasta luego!")
}
