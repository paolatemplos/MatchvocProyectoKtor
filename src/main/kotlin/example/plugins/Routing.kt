package com.example.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

// El objeto de la tabla se queda aquí para que la base de datos lo encuentre
object UserTable : Table("usuarios") {
    val id = integer("id").autoIncrement()
    val correo = varchar("correo", 100)
    val password = varchar("password", 100)
    val role = varchar("role", 20)
    override val primaryKey = PrimaryKey(id)
}

fun Application.configureRouting() {
    // Conexión a la base de datos (MatchVoc)
    Database.connect(
        url = "jdbc:mysql://localhost:3307/matchvoc",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",
        password = ""
    )

    routing {
        // Rastro para ver intentos de conexión en consola
        trace { application.log.trace(it.buildText()) }

        get("/") {
            call.respondText("¡Servidor de MatchVoc encendido!")
        }

        post("/login") {
            println("--- ALGUIEN ESTÁ INTENTANDO ENTRAR ---")
            try {
                // Ktor buscará automáticamente LoginRequest en tu archivo Models.kt
                val request = call.receive<LoginRequest>()

                println("--- DATOS RECIBIDOS EN SERVIDOR ---")
                println("Correo: [${request.correo}]")
                println("Pass: [${request.password}]")

                val userRow = transaction {
                    UserTable.select {
                        (UserTable.correo eq request.correo.trim()) and
                                (UserTable.password eq request.password.trim())
                    }.firstOrNull()
                }

                if (userRow != null) {
                    println("RESULTADO: Usuario encontrado. Enviando éxito...")
                    call.respond(HttpStatusCode.OK, LoginResponse(success = true, message = "Bienvenido"))
                } else {
                    println("RESULTADO: No coincide en BD. Enviando error...")
                    call.respond(HttpStatusCode.Unauthorized, LoginResponse(success = false, message = "Correo o contraseña incorrectos"))
                }

            } catch (e: Exception) {
                // Si el celular manda mal los campos, caerá aquí
                println("ERROR EN RECEPCIÓN: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, LoginResponse(success = false, message = "Error de formato: revisa los campos en Android"))
            }
        }
    }
}