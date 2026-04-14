package com.example.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

object UserTable : Table("usuarios") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val correo = varchar("correo", 100)
    val password = varchar("password", 100)
    val role = varchar("role", 20)
    override val primaryKey = PrimaryKey(id)
}

// Tabla de tarjetas actualizada con el campo 'carrera'
object TarjetasTable : Table("tarjetas") {
    val id = integer("id").autoIncrement()
    val texto = varchar("texto", 255)
    val sector = varchar("sector", 50)
    val carrera = varchar("carrera", 100) // Se añade para el nuevo test estructurado
    val imagenUrl = varchar("imagen_url", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

// Nueva tabla para las universidades que el admin va a gestionar
object UniversidadesTable : Table("universidades") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 255)
    val localidad = varchar("localidad", 255)
    val sitioWeb = varchar("sitio_web", 500) // El primer texto es el nombre REAL en la BD
    val ofertaEducativa = text("oferta_educativa")
    val sectorRelacionado = varchar("sector_relacionado", 100)

    override val primaryKey = PrimaryKey(id)
}
object SectoresTable : Table("sectores") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    override val primaryKey = PrimaryKey(id)
}
// lista de alumnos
object ResultadosTable : Table("resultados") {
    val id = integer("id").autoIncrement()
    val idUsuario = integer("id_usuario")
    val sectorSugerido = varchar("sector_sugerido", 100)
    val carrerasAfines = text("carreras_afines")
    val fecha = datetime("fecha").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}
// Nueva tabla para la mostrar graficas por alumno de su test
object RespuestasIndividualesTable : Table("respuestas_individuales") {
    val id = integer("id").autoIncrement()
    val idUsuario = integer("id_usuario")
    val pregunta = varchar("pregunta", 255)
    val sector = varchar("sector", 50)
    val carrera = varchar("carrera", 100)
    val leIntereso = bool("le_intereso") // True = +1, False = 0
    override val primaryKey = PrimaryKey(id)
}
// Tabla para guardar el progreso y la trazabilidad del alumno
object TestProgressTable : Table("test_progress") {
    val id = integer("id").autoIncrement()
    val idUsuario = integer("id_usuario")
    val pregunta = varchar("pregunta", 255)
    val sector = varchar("sector", 50)
    val carrera = varchar("carrera", 100)
    val respuesta = bool("respuesta") // true = like, false = dislike
    val fecha = datetime("fecha").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}

fun Application.configureRouting() {
    Database.connect(
        url = "jdbc:mysql://monorail.proxy.rlwy.net:54392/railway",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",
        password = "VuWMrBHouQPJVpfqwzbRNhmJfVICjvgM"
    )

    routing {
        trace { application.log.trace(it.buildText()) }

        get("/") {
            call.respondText("¡Servidor de MatchVoc encendido!")
        }

        // --- BLOQUE: LOGIN, REGISTRO Y PASSWORD (TU CÓDIGO ORIGINAL) ---
        post("/login") {
            println("--- ALGUIEN ESTÁ INTENTANDO ENTRAR ---")
            try {
                val request = call.receive<LoginRequest>()
                println("DATOS RECIBIDOS: ${request.correo}")

                val userRow = transaction {
                    UserTable.select {
                        (UserTable.correo eq request.correo.trim()) and
                                (UserTable.password eq request.password.trim())
                    }.firstOrNull()
                }

                if (userRow != null) {
                    val userRole = userRow[UserTable.role]
                    val userId = userRow[UserTable.id]
                    println("RESULTADO: Usuario encontrado con rol: $userRole")

                    call.respond(
                        HttpStatusCode.OK, LoginResponse(
                            success = true,
                            message = "Bienvenido",
                            role = userRole,
                            id = userId
                        )
                    )
                } else {
                    println("RESULTADO: Credenciales incorrectas")
                    call.respond(
                        HttpStatusCode.Unauthorized, LoginResponse(
                            success = false,
                            message = "Correo o contraseña incorrectos"
                        )
                    )
                }

            } catch (e: Exception) {
                println("ERROR CRÍTICO: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest, LoginResponse(
                        success = false,
                        message = "Error de formato en el servidor"
                    )
                )
            }
        }

        post("/register") {
            println("--- NUEVA SOLICITUD DE REGISTRO ---")
            try {
                val signup = call.receive<RegisterRequest>()

                val userId = transaction {
                    // Insertamos el nuevo usuario en la tabla que ya tienes
                    UserTable.insert {
                        it[nombre] = signup.nombre
                        it[correo] = signup.correo
                        it[password] = signup.password
                        it[role] = signup.role
                    } get UserTable.id
                }

                println("REGISTRO EXITOSO: ID $userId")
                call.respond(
                    HttpStatusCode.Created,
                    RegisterResponse(true, "Cuenta creada con éxito")
                )

            } catch (e: Exception) {
                println("ERROR AL REGISTRAR: ${e.message}")
                call.respond(
                    HttpStatusCode.Conflict,
                    RegisterResponse(
                        false,
                        "El correo ya está registrado o hay un error en los datos"
                    )
                )
            }
        }
        post("/reset-password") {
            try {
                val request = call.receive<ResetPasswordRequest>()

                val updated = transaction {
                    // Buscamos el correo ignorando espacios y minúsculas
                    UserTable.update({ UserTable.correo eq request.correo.trim() }) {
                        it[password] = request.newPassword.trim()
                    }
                }

                if (updated > 0) {
                    // Enviamos un objeto real, no un mapOf
                    call.respond(HttpStatusCode.OK, AuthResponse(true, "Contraseña actualizada"))
                } else {
                    // Si llega aquí es porque el correo no existe en la BD
                    call.respond(
                        HttpStatusCode.NotFound,
                        AuthResponse(false, "Correo no encontrado")
                    )
                }
            } catch (e: Exception) {
                println("Error en reset-password: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Error en el servidor"))
            }
        }

        // --- BLOQUE: TARJETAS (ESTUDIANTE Y ADMIN) ---
        get("/tarjetas") {
            try {
                val listaTarjetas = transaction {
                    TarjetasTable.selectAll().map {
                        Tarjeta(
                            id = it[TarjetasTable.id],
                            texto = it[TarjetasTable.texto],
                            sector = it[TarjetasTable.sector],
                            carrera = it[TarjetasTable.carrera], // Incluimos carrera
                            imagenUrl = it[TarjetasTable.imagenUrl]
                        )
                    }
                }
                call.respond(listaTarjetas)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error en la base de datos")
            }
        }

        // Ruta para que el Admin ELIMINE una tarjeta
        delete("/admin/tarjetas/{id}") {
            val idParam = call.parameters["id"]?.toIntOrNull()
            if (idParam != null) {
                transaction {
                    TarjetasTable.deleteWhere { TarjetasTable.id eq idParam }
                }
                call.respond(HttpStatusCode.OK, "Tarjeta eliminada")
            }
        }

        // --- BLOQUE: UNIVERSIDADES (Para Alumnos y Admin) ---
        // 1. Obtener la lista completa de universidades
        get("/admin/universidades") {
            try {
                val listaUnis = transaction {
                    UniversidadesTable.selectAll().map { row ->
                        // Usamos el nombre de la clase que definiste en Models.kt
                        University(
                            id = row[UniversidadesTable.id],
                            nombre = row[UniversidadesTable.nombre],
                            localidad = row[UniversidadesTable.localidad],
                            sitio_web = row[UniversidadesTable.sitioWeb], // Mapeo correcto a la columna
                            oferta_educativa = row[UniversidadesTable.ofertaEducativa],
                            sector_relacionado = row[UniversidadesTable.sectorRelacionado]
                        )
                    }
                }
                println("DEBUG: Enviando ${listaUnis.size} universidades a la App")
                call.respond(listaUnis)
            } catch (e: Exception) {
                println("Error en GET /admin/universidades: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error al cargar universidades")
            }
        }

// 2. Ruta para AGREGAR (POST)
        post("/admin/universidades") {
            try {
                // Recibe el objeto desde la App
                val uni = call.receive<Map<String, String>>()
                transaction {
                    UniversidadesTable.insert {
                        it[nombre] = uni["nombre"] ?: ""
                        it[localidad] = uni["localidad"] ?: ""
                        it[sitioWeb] = uni["sitio_web"] ?: ""
                        it[ofertaEducativa] = uni["oferta_educativa"] ?: ""
                        it[sectorRelacionado] = uni["sector_relacionado"] ?: "General" // <--- NUEVO
                    }
                }
                call.respond(HttpStatusCode.Created, "Universidad agregada con éxito")
            } catch (e: Exception) {
                println("Error en POST: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, "Error al insertar en BD")
            }
        }

// 3. Ruta para ELIMINAR (DELETE)
        delete("/admin/universidades/{id}") {
            val idParam = call.parameters["id"]?.toIntOrNull()
            if (idParam != null) {
                try {
                    transaction {
                        // Asegúrate de importar: org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
                        // Y también: org.jetbrains.exposed.sql.deleteWhere
                        UniversidadesTable.deleteWhere { id eq idParam }
                    }
                    call.respond(HttpStatusCode.OK, "Eliminado con éxito")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error al eliminar")
                }
            } else {
                call.respond(HttpStatusCode.BadRequest, "ID no válido")
            }
        }
        // 4. Ruta para ACTUALIZAR (PUT)
        put("/admin/universidades/{id}") {
            val idParam = call.parameters["id"]?.toIntOrNull()
            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest, "ID no válido")
                return@put
            }

            try {
                // Recibimos el objeto que viene de la App (asegúrate que sea Universidad)
                val uni = call.receive<University>()

                transaction {
                    // Sintaxis corregida para Exposed
                    UniversidadesTable.update({ UniversidadesTable.id eq idParam }) {
                        it[nombre] = uni.nombre
                        it[localidad] = uni.localidad
                        it[sitioWeb] = uni.sitio_web
                        it[ofertaEducativa] = uni.oferta_educativa
                        it[sectorRelacionado] = uni.sector_relacionado
                    }
                }
                call.respond(HttpStatusCode.OK, "Universidad actualizada con éxito")
            } catch (e: Exception) {
                println("Error en PUT: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error al actualizar")
            }
        }

        // --- BLOQUE: ESTADÍSTICAS (Para el Dashboard del Admin) ---
        // Esto llenará los cuadritos de "Registros" en tu pantalla de inicio
        get("/admin/stats") {
            try {
                val stats = transaction {
                    val usersCount = UserTable.selectAll().count() // Cuenta total de usuarios
                    val cardsCount = TarjetasTable.selectAll().count() // Cuenta total de tarjetas/preguntas

                    // Retornamos un mapa que Ktor convierte a JSON automáticamente
                    mapOf(
                        "usuarios" to usersCount,
                        "tarjetas" to cardsCount
                    )
                }
                call.respond(stats)
            } catch (e: Exception) {
                println("Error en GET /admin/stats: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener estadísticas")
            }
        }
        // En tu Routing.kt del servidor
        get("/admin/sectores") {
            try {
                val sectores = transaction {
                    // 1. Obtenemos sectores que ya tienen tarjetas
                    val desdeTarjetas = TarjetasTable.slice(TarjetasTable.sector)
                        .selectAll().withDistinct().map { it[TarjetasTable.sector] }

                    // 2. Obtenemos los sectores nuevos de la tabla SectoresTable
                    val desdeSectoresTable = SectoresTable.selectAll().map { it[SectoresTable.nombre] }

                    // 3. Unimos ambos sin repetir nombres
                    (desdeTarjetas + desdeSectoresTable).distinct().map {
                        mapOf("nombre" to it)
                    }
                }
                call.respond(sectores)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
        //lista de preguntas para el administrador dentro de los sectores
        // Acceso a las tarjetas filtradas por sector
        get("/admin/tarjetas/sector/{sectorName}") {
            val sector = call.parameters["sectorName"] ?: return@get call.respond(HttpStatusCode.BadRequest)

            try {
                // Usamos la misma lógica de transaction que te funcionó en sectores
                val listaTarjetas = transaction {
                    // Buscamos en TarjetasTable filtrando donde la columna 'sector' coincida
                    TarjetasTable.select { TarjetasTable.sector eq sector }
                        .map {
                            Tarjeta(
                                id = it[TarjetasTable.id], // ID de la tabla
                                texto = it[TarjetasTable.texto], // Campo 'texto' de tu MySQL
                                sector = it[TarjetasTable.sector],
                                carrera = it[TarjetasTable.carrera],
                                imagenUrl = it[TarjetasTable.imagenUrl]
                            )
                        }
                }

                // Enviamos la lista encontrada
                call.respond(listaTarjetas)
            } catch (e: Exception) {
                // En caso de error, mostramos qué pasó
                call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error al cargar tarjetas")
            }
        }
        // vamos a editar las tarjetas
        get("/admin/tarjetas/{id}") {
            // 1. Obtenemos el ID de la URL
            val idParam = call.parameters["id"]?.toIntOrNull()

            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest, "ID no válido")
                return@get
            }

            // 2. Buscamos en la base de datos
            val tarjeta = transaction {
                TarjetasTable
                    .select { TarjetasTable.id eq idParam } // Aquí ya no debería marcar error en 'eq'
                    .map { row ->
                        Tarjeta(
                            // Fíjate bien aquí: usamos row[columna]
                            id = row[TarjetasTable.id],
                            texto = row[TarjetasTable.texto],
                            sector = row[TarjetasTable.sector],
                            carrera = row[TarjetasTable.carrera],
                            imagenUrl = row[TarjetasTable.imagenUrl] ?: ""
                        )
                    }.singleOrNull()
            }

            // 3. Respondemos al Front
            if (tarjeta != null) {
                call.respond(tarjeta)
            } else {
                call.respond(HttpStatusCode.NotFound, "Tarjeta no encontrada")
            }
        }
        // En el Backend (Routing.kt) - PARA GUARDAR
        put("/admin/tarjetas/actualizar/{id}") {
            val idParam = call.parameters["id"]?.toIntOrNull()
            val tarjetaEditada = call.receive<Tarjeta>() // El objeto que enviamos desde el cel

            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }

            val filasCambiadas = transaction {
                TarjetasTable.update({ TarjetasTable.id eq idParam }) {
                    it[texto] = tarjetaEditada.texto // Solo actualizamos el texto de la pregunta
                }
            }

            if (filasCambiadas > 0) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        //agregar nuevos sectores
        // Busca esta parte en tu Routing.kt y cámbiala por esta:
        post("/admin/sectores/nuevo") {
            try {
                // Recibe el mapOf("nombre" to nombre) que manda el celular
                val recibidos = call.receive<Map<String, String>>()
                val nombreNuevo = recibidos["nombre"] ?: ""

                if (nombreNuevo.isNotBlank()) {
                    transaction {
                        // Inserta en la tabla física de MySQL
                        SectoresTable.insert {
                            it[nombre] = nombreNuevo
                        }
                    }
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Nombre vacío")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }

        get("/usuarios/{id}") {
            val idParam = call.parameters["id"]?.toIntOrNull()

            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@get
            }

            try {
                val usuario = dbQuery {
                    // Usamos .select en lugar de .selectAll().where para que coincida con tu Login
                    UserTable.select { UserTable.id eq idParam }
                        .map { row ->
                            User(
                                id = row[UserTable.id],
                                nombre = row[UserTable.nombre],
                                correo = row[UserTable.correo],
                                role = row[UserTable.role]
                            )
                        }
                        .singleOrNull()
                }

                if (usuario != null) {
                    call.respond(usuario)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Usuario no encontrado")
                }
            } catch (e: Exception) {
                println("Error en get-usuario: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
        //recibe este objeto y cree múltiples registros en la tabla TarjetasTable
        post("/admin/carreras/guardar") {
            try {
                val request = call.receive<CarreraRequest>()

                transaction {
                    request.preguntas.forEach { textoPregunta ->
                        TarjetasTable.insert {
                            it[texto] = textoPregunta
                            it[carrera] = request.nombreCarrera
                            it[sector] = request.sector
                            it[imagenUrl] = "" // Por ahora vacío
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, "Carrera y preguntas guardadas")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
        // VA A MOSTRAR LA LISTA DE ALUMNOS
        // ESTA RUTA MOSTRARÁ LA LISTA CON EL ESTATUS REAL
        get("/admin/alumnos") {
            try {
                val alumnos = transaction {
                    // Unimos las tablas
                    Join(UserTable, ResultadosTable,
                        onColumn = UserTable.id,
                        otherColumn = ResultadosTable.idUsuario,
                        joinType = JoinType.LEFT
                    )
                        .select { UserTable.role eq "student" }
                        .map { row ->
                            // IMPORTANTE: Aquí creamos el objeto User en lugar de un Map
                            User(
                                id = row[UserTable.id],
                                nombre = row[UserTable.nombre],
                                correo = row[UserTable.correo],
                                role = row[UserTable.role],
                                // Esta es la lógica para el booleano
                                isCompleted = row.getOrNull(ResultadosTable.id) != null
                            )
                        }
                }
                call.respond(alumnos) // Ahora sí Ktor sabrá cómo enviarlo
            } catch (e: Exception) {
                println("Error en servidor: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
        // --- RUTA PARA OBTENER LOS DATOS DEL ALUMNO (Nombre y Correo) ---
        get("/admin/usuarios/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID de usuario inválido")
                return@get
            }

            val usuario = transaction {
                // Usamos UserTable que es el que ya tienes definido
                UserTable.select { UserTable.id eq id }
                    .map {
                        User(
                            id = it[UserTable.id],
                            nombre = it[UserTable.nombre],
                            correo = it[UserTable.correo],
                            role = it[UserTable.role]
                        )
                    }.firstOrNull()
            }

            if (usuario != null) {
                call.respond(usuario)
            } else {
                call.respond(HttpStatusCode.NotFound, "Usuario no encontrado")
            }
        }

// --- RUTA PARA OBTENER EL RESULTADO DEL TEST ---
        get("/admin/resultados/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, "ID de usuario inválido")
                return@get
            }

            val resultado = transaction {
                // Filtramos por idUsuario y ordenamos por fecha para tener el más reciente
                ResultadosTable.select { ResultadosTable.idUsuario eq userId }
                    .orderBy(ResultadosTable.fecha, SortOrder.DESC)
                    .map {
                        ResultadoTest(
                            id = it[ResultadosTable.id],
                            idUsuario = it[ResultadosTable.idUsuario],
                            sectorSugerido = it[ResultadosTable.sectorSugerido],
                            carrerasAfines = it[ResultadosTable.carrerasAfines],
                            fecha = it[ResultadosTable.fecha].toString() // Convertimos la fecha a String para el modelo
                        )
                    }.firstOrNull()
            }

            if (resultado != null) {
                call.respond(resultado)
            } else {
                // Si no hay resultados, enviamos un mensaje claro
                call.respond(HttpStatusCode.NotFound, "Este alumno aún no ha realizado el test")
            }
        }
        //La ruta para generar el PDF en admin
        get("/admin/reporte/diario") {
            try {
                val bytes = com.lowagie.text.Document().use { document ->
                    val outputStream = java.io.ByteArrayOutputStream()
                    com.lowagie.text.pdf.PdfWriter.getInstance(document, outputStream)
                    document.open()

                    // Título del PDF
                    val fontTitulo = com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18f, com.lowagie.text.Font.BOLD)
                    document.add(com.lowagie.text.Paragraph("Reporte General de Resultados - MatchVoc", fontTitulo))
                    document.add(com.lowagie.text.Paragraph("Bachillerato Digital Num.73 (BD73)"))
                    document.add(com.lowagie.text.Paragraph("Fecha: ${java.time.LocalDate.now()}\n\n"))

                    // Tabla de datos
                    val tabla = com.lowagie.text.pdf.PdfPTable(3) // 3 columnas
                    tabla.addCell("Alumno")
                    tabla.addCell("Sector Sugerido")
                    tabla.addCell("Carreras Afines")

                    transaction {
                        Join(UserTable, ResultadosTable,
                            onColumn = UserTable.id,
                            otherColumn = ResultadosTable.idUsuario,
                            joinType = JoinType.INNER
                        ).selectAll().forEach { row ->
                            tabla.addCell(row[UserTable.nombre])
                            tabla.addCell(row[ResultadosTable.sectorSugerido])
                            tabla.addCell(row[ResultadosTable.carrerasAfines])
                        }
                    }

                    document.add(tabla)
                    document.close()
                    outputStream.toByteArray()
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "Reporte_MatchVoc.pdf").toString()
                )
                call.respondBytes(bytes, ContentType.Application.Pdf)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al generar PDF: ${e.message}")
            }
        }
        // --- NUEVA RUTA: ÚLTIMA ACTIVIDAD (PARA EL DASHBOARD) ---
        get("/admin/resultados/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, "ID inválido")
                return@get
            }

            val response = transaction {
                // 1. Buscamos el resultado general
                val resGeneral = ResultadosTable.select { ResultadosTable.idUsuario eq userId }.singleOrNull()

                // 2. Buscamos todas las preguntas que contestó este alumno
                val historial = RespuestasIndividualesTable.select { RespuestasIndividualesTable.idUsuario eq userId }
                    .map {
                        RespuestaDetallada( // El modelo que creamos en el paso 1
                            idAlumno = it[RespuestasIndividualesTable.idUsuario],
                            pregunta = it[RespuestasIndividualesTable.pregunta],
                            sector = it[RespuestasIndividualesTable.sector],
                            carrera = it[RespuestasIndividualesTable.carrera],
                            leIntereso = it[RespuestasIndividualesTable.leIntereso]
                        )
                    }

                if (resGeneral != null) {
                    // Retornamos el ResultadoTest con el historial incluido
                    ResultadoTest(
                        id = resGeneral[ResultadosTable.id],
                        idUsuario = resGeneral[ResultadosTable.idUsuario],
                        sectorSugerido = resGeneral[ResultadosTable.sectorSugerido],
                        carrerasAfines = resGeneral[ResultadosTable.carrerasAfines],
                        historialPreguntas = historial // ¡Aquí va la magia para las gráficas!
                    )
                } else null
            }

            if (response != null) call.respond(response)
            else call.respond(HttpStatusCode.NotFound, "Sin resultados")
        }
        // Endpoint para guardar cada respuesta una por una
        post("/test/save-progress") {
            try {
                val progress = call.receive<SaveProgressRequest>()

                transaction {
                    // Guardamos para el progreso del alumno (trazabilidad)
                    TestProgressTable.insert {
                        it[idUsuario] = progress.idUsuario
                        it[pregunta] = progress.pregunta
                        it[sector] = progress.sector
                        it[carrera] = progress.carrera
                        it[respuesta] = progress.respuesta
                    }

                    // También guardamos en respuestas individuales para las gráficas del admin
                    RespuestasIndividualesTable.insert {
                        it[idUsuario] = progress.idUsuario
                        it[pregunta] = progress.pregunta
                        it[sector] = progress.sector
                        it[carrera] = progress.carrera
                        it[leIntereso] = progress.respuesta
                    }
                }
                call.respond(HttpStatusCode.Created, "Progreso y respuesta guardados")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }

        // Endpoint extra: Para que el alumno recupere su progreso si sale y entra
        get("/test/get-progress/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

            try {
                val respuestasPrevias = dbQuery {
                    TestProgressTable.select { TestProgressTable.idUsuario eq userId }
                        .map { it[TestProgressTable.pregunta] } // Solo traemos los textos de las preguntas ya hechas
                }
                call.respond(respuestasPrevias)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error al obtener progreso")
            }
        }
        // Ruta para obtener el diagnóstico final con universidades sugeridas
        get("/estudiante/diagnostico/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

            try {
                val diagnosticoCompleto = transaction {
                    // 1. Buscamos el resultado más reciente del alumno
                    val resultado = ResultadosTable.select { ResultadosTable.idUsuario eq userId }
                        .orderBy(ResultadosTable.fecha, SortOrder.DESC)
                        .firstOrNull()

                    if (resultado != null) {
                        val sectorAlcanzado = resultado[ResultadosTable.sectorSugerido]

                        // 2. BUSQUEDA MAGICA: Buscamos universidades que coincidan con ese sector
                        val unisSugeridas = UniversidadesTable.select {
                            UniversidadesTable.sectorRelacionado like "%$sectorAlcanzado%"
                        }.map { row ->
                            University(
                                id = row[UniversidadesTable.id],
                                nombre = row[UniversidadesTable.nombre],
                                localidad = row[UniversidadesTable.localidad],
                                sitio_web = row[UniversidadesTable.sitioWeb],
                                oferta_educativa = row[UniversidadesTable.ofertaEducativa],
                                sector_relacionado = row[UniversidadesTable.sectorRelacionado]
                            )
                        }

                        // Retornamos todo junto
                        mapOf(
                            "resultado" to sectorAlcanzado,
                            "carreras" to resultado[ResultadosTable.carrerasAfines],
                            "universidades" to unisSugeridas
                        )
                    } else null
                }

                if (diagnosticoCompleto != null) {
                    call.respond(diagnosticoCompleto)
                } else {
                    call.respond(HttpStatusCode.NotFound, "No se encontró un test finalizado")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
    }
}
// ESTO VA AL FINAL DE TU ARCHIVO Routing.kt
fun getTarjetasPorSector(sectorBusqueda: String, connection: java.sql.Connection): List<Pregunta> {
    val lista = mutableListOf<Pregunta>()
    // Consulta exacta a tu tabla 'tarjetas'
    val sql = "SELECT * FROM tarjetas WHERE sector = ?"

    try {
        val statement = connection.prepareStatement(sql)
        statement.setString(1, sectorBusqueda)
        val rs = statement.executeQuery()

        while (rs.next()) {
            lista.add(
                Pregunta(
                    id = rs.getInt("id"),
                    // Usamos "texto" que es el nombre real en tu MySQL
                    texto = rs.getString("texto"),
                    sector = rs.getString("sector"),
                    carrera = rs.getString("carrera"),
                    imagenUrl = rs.getString("imagen_url") ?: ""
                )
            )
        }
    } catch (e: Exception) {
        println("Error SQL: ${e.message}")
    }
    return lista
}