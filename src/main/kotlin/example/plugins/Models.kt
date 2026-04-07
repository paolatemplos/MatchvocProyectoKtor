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