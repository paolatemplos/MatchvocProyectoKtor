package com.example.plugins
import kotlinx.serialization.Serializable


@Serializable
data class User(
    val id: Int,
    val nombre: String,
    val correo: String,
    val role: String,
    val isCompleted: Boolean = false
)
@Serializable
data class LoginRequest(val correo: String, val password: String)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val id: Int? = null,
    val nombre: String? = null,
    val correo: String? = null,
    val role: String? = null
)
@Serializable
data class RegisterRequest(
    val nombre: String,
    val correo: String,
    val password: String,
    val role: String = "student" // Por defecto se registran como alumnos
)

@Serializable
data class RegisterResponse(
    val success: Boolean,
    val message: String
)
@Serializable
data class ResetPasswordRequest(val correo: String, val newPassword: String)
@Serializable
data class AuthResponse(val success: Boolean, val message: String)
@Serializable
data class Tarjeta(
    val id: Int? = null, // El ID puede ser nulo al crear una nueva
    val texto: String,
    val sector: String,
    val carrera: String,
    val imagenUrl: String? = null
)
@Serializable
data class University(
    val id: Int,
    val nombre: String,
    val localidad: String,
    val sitio_web: String,
    val oferta_educativa: String,
    val sector_relacionado: String
)
// En tu proyecto de Servidor (Ktor) -> Models.kt
@Serializable
data class SectorResponse(val nombre: String)
@Serializable
data class Pregunta(
    val id: Int,
    val texto: String,
    val sector: String,
    val carrera: String,
    val imagenUrl: String? = null // Cámbialo aquí a imagenUrl (U mayúscula)
)
@Serializable
data class Sector(
    val id: Int? = null,
    val nombre: String
)
//vamos a resibir nueva carrera y nuevas preguntas
@Serializable
data class CarreraRequest(
    val nombreCarrera: String,
    val sector: String,
    val preguntas: List<String>
)
//estas 3 clases resibiran las tarjetas contestadas si o no
@Serializable
data class SaveProgressRequest(
    val idUsuario: Int,
    val pregunta: String,
    val sector: String,
    val carrera: String,
    val respuesta: Boolean
)

@Serializable
data class ResultadoTest(
    val id: Int,
    val idUsuario: Int,
    val sectorSugerido: String,
    val carrerasAfines: String,
    val fecha: String? = null,
    val historialPreguntas: List<RespuestaDetallada> = emptyList()
)

@Serializable
data class RespuestaDetallada(
    val idAlumno: Int,
    val pregunta: String,
    val sector: String,
    val carrera: String,
    val leIntereso: Boolean
)
/**
 * Este es el que usa el TestViewModel para recalcular los puntos
 * cuando el alumno vuelve a entrar al test.
 */
@Serializable
data class TestProgressItem(
    val pregunta: String,
    val sector: String,
    val respuesta: Boolean
)
@Serializable
data class DiagnosisResponse(
    val estado: String,
    val sectorPrincipal: String,
    val totalContestadas: String,
    val respuestas: List<RespuestaHistorial>,
    // Agregamos la lista de universidades para que se incluya en el JSON
    val universidades: List<University> = emptyList()
)
@Serializable
data class RespuestaHistorial(
    val pregunta: String,
    val respuesta: String,
    val sector: String
)


