package com.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.plugins.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    // Esto lee el puerto de Railway, o usa el 8080 si estás en local
    val port = System.getenv("PORT")?.toInt() ?: 8080

    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization() // Esto permite leer JSON
    configureRouting()      // Esto activa las rutas de login
    // Agrega este bloque para crear las tablas en Railway
    transaction {
        SchemaUtils.create(
            UserTable,
            TarjetasTable,
            RespuestasIndividualesTable,
            TestProgressTable,
            ResultadosTable,
            SectoresTable
        )
    }
}