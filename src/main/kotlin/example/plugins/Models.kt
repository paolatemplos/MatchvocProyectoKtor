package com.example.plugins

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val correo: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String
)
@Serializable
data class CarreraRecomendada(
    val carrera: String,
    val universidad: String
)

@Serializable
data class DiagnosticoResponse(
    val area: String,
    val carreras_recomendadas: List<CarreraRecomendada>
)
@Serializable
data class PreguntaResponse(
    val id: Int,
    val texto: String,
    val area_id: Int
)