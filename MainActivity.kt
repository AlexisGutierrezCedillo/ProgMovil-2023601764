package com.example.holatoast

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UIPrincipal() }
    }
}

@Composable
fun UIPrincipal() {
    val contexto = LocalContext.current
    var nombre by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp) // Padding correcto
    ) {
        Text(
            text = "Nombre:",
            modifier = Modifier.padding(bottom = 8.dp) // Especificado correctamente
        )

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Introduce tu nombre") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp) // Padding horizontal correcto
        )

        Spacer(modifier = Modifier.height(16.dp)) // Espacio entre elementos

        Button(
            onClick = { Toast.makeText(contexto, "Hola $nombre!!", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Saludar!")
        }
    }
}
@Composable
fun ingresarNombre(){
    println("Ingresa tu nombre Completo: ")
    val input = readLine()
}

@Preview(showBackground = true)
@Composable
fun Previsualizacion(){
    UIPrincipal()
}