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
    val role = integer(name = "rol_id")
    override val primaryKey = PrimaryKey(id)
}
object PreguntasTable : Table(name = "preguntas_test") {
    val id = integer(name = "id").autoIncrement()
    val texto = varchar(name = "texto", length = 500)
    val area_id = integer(name = "area_id")
    val activa = integer(name = "activa")
    override val primaryKey = PrimaryKey(firstColumn = id)
}

object CarrerasUniversidadTable : Table(name = "carreras_universidad") {
    val id = integer(name = "id").autoIncrement()
    val universidad_id = integer(name = "universidad_id")
    val area_id = integer(name = "area_id")
    val nombre_carrera = varchar(name = "nombre_carrera", length = 200)
    override val primaryKey = PrimaryKey(firstColumn = id)
}

object UniversidadesTable : Table(name = "universidades") {
    val id = integer(name = "id").autoIncrement()
    val nombre = varchar(name = "nombre", length = 200)
    val siglas = varchar(name = "siglas", length = 20)
    override val primaryKey = PrimaryKey(firstColumn = id)
}

object AreasTable : Table(name = "areas_vocacionales") {
    val id = integer(name = "id").autoIncrement()
    val nombre = varchar(name = "nombre", length = 100)
    override val primaryKey = PrimaryKey(firstColumn = id)
}
fun Application.configureRouting() {
    // Conexión a la base de datos (MatchVoc)
    Database.connect(
        url = "jdbc:mysql://localhost:3306/matchvoc",
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
                    call.respond(
                        HttpStatusCode.OK,
                        LoginResponse(success = true, message = "Bienvenido")
                    )
                } else {
                    println("RESULTADO: No coincide en BD. Enviando error...")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        LoginResponse(success = false, message = "Correo o contraseña incorrectos")
                    )
                }

            } catch (e: Exception) {
                // Si el celular manda mal los campos, caerá aquí
                println("ERROR EN RECEPCIÓN: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    LoginResponse(
                        success = false,
                        message = "Error de formato: revisa los campos en Android"
                    )
                )
            }
        }
        post("/diagnostico") {
            try {
                val respuestas = call.receive<Map<String, Int>>()

                val resultado = transaction {
                    // Contar puntos por área
                    val puntajesPorArea = mutableMapOf<Int, Int>()

                    respuestas.forEach { (preguntaId, respuesta) ->
                        if (respuesta == 1) {
                            val areaId = PreguntasTable
                                .select { PreguntasTable.id eq preguntaId.toInt() }
                                .firstOrNull()?.get(PreguntasTable.area_id)

                            if (areaId != null) {
                                puntajesPorArea[areaId] = (puntajesPorArea[areaId] ?: 0) + 1
                            }
                        }
                    }
                    // Área con más puntos
                    val areaGanadora = puntajesPorArea.maxByOrNull { it.value }?.key

                    if (areaGanadora != null) {
                        val areaNombre = AreasTable
                            .select { AreasTable.id eq areaGanadora }
                            .firstOrNull()?.get(AreasTable.nombre) ?: "Desconocida"

                        val carreras = CarrerasUniversidadTable
                            .select { CarrerasUniversidadTable.area_id eq areaGanadora }
                            .map { row ->
                                val uniNombre = UniversidadesTable
                                    .select { UniversidadesTable.id eq row[CarrerasUniversidadTable.universidad_id] }
                                    .firstOrNull()?.get(UniversidadesTable.nombre) ?: "Desconocida"
                                mapOf(
                                    "carrera" to row[CarrerasUniversidadTable.nombre_carrera],
                                    "universidad" to uniNombre
                                )
                            }

                        DiagnosticoResponse(
                            area = areaNombre,
                            carreras_recomendadas = carreras.map {
                                CarreraRecomendada(
                                    carrera = it["carrera"] as String,
                                    universidad = it["universidad"] as String
                                )
                            }
                        )
                    } else {
                        mapOf("error" to "No se pudo determinar un área")
                    }
                }

                call.respond(HttpStatusCode.OK, resultado as DiagnosticoResponse)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Error")))
            }
        }
        get("/preguntas") {
            try {
                val preguntas = transaction {
                    PreguntasTable
                        .select { PreguntasTable.activa eq 1 }
                        .orderBy(PreguntasTable.id)
                        .map { row ->
                            PreguntaResponse(
                                id = row[PreguntasTable.id],
                                texto = row[PreguntasTable.texto],
                                area_id = row[PreguntasTable.area_id]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, preguntas)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Error")))
            }
        }
    }
}


