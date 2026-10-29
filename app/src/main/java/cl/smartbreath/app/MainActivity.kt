package cl.smartbreath.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.smartbreath.app.ui.theme.SmartBreathAppTheme
import kotlinx.coroutines.delay

private data class DemoReading(
    val temperature: Double = 23.8,
    val humidity: Int = 46,
    val pressure: Int = 1012,
    val gas: Int = 18,
    val co: Int = 420,
    val bpm: Int = 72,
    val spo2: Int = 98,
    val sequence: Int = 1
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartBreathAppTheme { SmartBreathApp() } }
    }
}

@Composable
private fun SmartBreathApp() {
    var logged by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("HOST") }
    var hostIp by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf(DemoReading()) }
    var manual by remember { mutableStateOf(false) }
    var buzzer by remember { mutableStateOf(false) }
    var led by remember { mutableStateOf(false) }
    var vibration by remember { mutableStateOf(false) }
    var coThreshold by remember { mutableFloatStateOf(800f) }
    var lastAction by remember { mutableStateOf("Listo para iniciar") }
    var linkStatus by remember { mutableStateOf("Sin iniciar") }
    var inboundLine by remember { mutableStateOf("") }
    var inboundSequence by remember { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val bridge = remember {
        TcpBridge(
            onStatus = { status -> mainHandler.post { linkStatus = status } },
            onLine = { line -> mainHandler.post { inboundLine = line; inboundSequence++ } }
        )
    }

    DisposableEffect(Unit) { onDispose { bridge.close() } }

    // Process messages on the main thread, where Compose state is managed.
    LaunchedEffect(inboundSequence) {
        if (inboundLine.isBlank()) return@LaunchedEffect
        val parts = inboundLine.split('|')
        if (parts.firstOrNull() == "T" && parts.size >= 14 && role == "CLIENT") {
            try {
                reading = DemoReading(
                    temperature = parts[2].toDouble(), humidity = parts[3].toInt(),
                    pressure = parts[4].toInt(), gas = parts[5].toInt(), co = parts[6].toInt(),
                    bpm = parts[7].toInt(), spo2 = parts[8].toInt(), sequence = parts[1].toInt()
                )
                manual = parts[9] == "1"; buzzer = parts[10] == "1"
                led = parts[11] == "1"; vibration = parts[12] == "1"
                coThreshold = parts[13].toFloat()
                lastAction = "Telemetría recibida del teléfono monitor"
            } catch (_: Exception) {
                lastAction = "Se recibió un mensaje con formato incorrecto"
            }
        } else if (parts.firstOrNull() == "C" && parts.size >= 3 && role == "HOST") {
            val enabled = parts[2] == "1"
            when (parts[1]) {
                "manual" -> manual = enabled
                "buzzer" -> buzzer = enabled
                "led" -> led = enabled
                "vibration" -> vibration = enabled
                "threshold" -> parts.getOrNull(2)?.toFloatOrNull()?.let { coThreshold = it }
            }
            lastAction = "Orden recibida desde el teléfono controlador: ${parts[1]}"
        } else if (inboundLine.startsWith("PING") && role == "HOST") {
            bridge.send("PONG|${System.currentTimeMillis()}")
            lastAction = "PING recibido; respuesta PONG enviada"
        } else if (inboundLine.startsWith("PONG") && role == "CLIENT") {
            lastAction = "Respuesta PONG recibida del monitor"
        }
    }

    LaunchedEffect(logged, role, hostIp) {
        if (!logged) return@LaunchedEffect
        if (role == "HOST") bridge.startServer() else bridge.connect(hostIp)
    }

    // Only the monitor generates demo readings; the controller displays received samples.
    LaunchedEffect(logged, role) {
        while (logged) {
            delay(3000)
            if (role == "HOST") {
                val n = reading.sequence + 1
                reading = DemoReading(
                    temperature = 23.8 + ((n % 5) - 2) * 0.3,
                    humidity = 46 + ((n % 7) - 3),
                    pressure = 1012 + ((n % 5) - 2),
                    gas = 18 + (n % 4), co = 420 + ((n % 6) * 35),
                    bpm = 72 + ((n % 9) - 4), spo2 = 98 - (n % 2), sequence = n
                )
            }
        }
    }

    // The monitor publishes current readings and actuator states to the connected client.
    LaunchedEffect(logged, role, reading, manual, buzzer, led, vibration, coThreshold) {
        if (logged && role == "HOST") {
            val line = listOf(
                "T", reading.sequence, reading.temperature, reading.humidity, reading.pressure,
                reading.gas, reading.co, reading.bpm, reading.spo2,
                if (manual) 1 else 0, if (buzzer) 1 else 0, if (led) 1 else 0,
                if (vibration) 1 else 0, coThreshold.toInt()
            ).joinToString("|")
            bridge.send(line)
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (!logged) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("SmartBreath", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Evaluación 2 · demo de interconexión Android por Wi-Fi")
                    Spacer(Modifier.height(20.dp))
                    Text("Función de este teléfono", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = role == "HOST", onClick = { role = "HOST" }, label = { Text("Monitor / servidor") })
                        FilterChip(selected = role == "CLIENT", onClick = { role = "CLIENT" }, label = { Text("Controlador / cliente") })
                    }
                    if (role == "HOST") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("IP local detectada: ${TcpBridge.localIpv4()}", style = MaterialTheme.typography.bodyMedium)
                                Text("Después de ingresar, deja esta pantalla abierta. En el otro teléfono elige Controlador y escribe esta IP.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = hostIp, onValueChange = { hostIp = it },
                            label = { Text("IP del teléfono monitor") }, placeholder = { Text("Ej.: 192.168.1.25") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Text("Ambos teléfonos deben estar en la misma red Wi-Fi o hotspot.", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (loginError.isNotBlank()) {
                        Spacer(Modifier.height(8.dp)); Text(loginError, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        if (user == "admin" && pass == "SmartBreath2026!") {
                            if (role == "CLIENT" && hostIp.trim().isBlank()) loginError = "Ingresa la IP del teléfono monitor."
                            else { logged = true; loginError = ""; lastAction = "Sesión local iniciada" }
                        } else loginError = "Credenciales incorrectas. Revisa los datos de demostración."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ingresar") }
                    Spacer(Modifier.height(12.dp))
                    Text("Credenciales académicas: admin / SmartBreath2026!", style = MaterialTheme.typography.bodySmall)
                    Text("El login es local y fijo para la demostración; no es autenticación de producción.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("SmartBreath", style = MaterialTheme.typography.headlineMedium)
                    Text(if (role == "HOST") "Monitor · genera telemetría simulada" else "Controlador · recibe telemetría remota", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(if (role == "HOST") "IP de este monitor: ${TcpBridge.localIpv4()}" else "Monitor configurado: $hostIp:${TcpBridge.PORT}", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Conexión: $linkStatus", style = MaterialTheme.typography.bodyMedium)
                            Text("Muestra #${reading.sequence} · actualización simulada cada 3 s", style = MaterialTheme.typography.bodySmall)
                            if (role == "CLIENT") {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { bridge.connect(hostIp) }) { Text("Reintentar conexión") }
                            }
                            if (role == "HOST") Text("Mantén esta app abierta y comparte la IP con el otro teléfono.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Monitoreo") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Activador") })
                    }
                    if (tab == 0) {
                        Spacer(Modifier.height(12.dp))
                        Text("Lecturas ${if (role == "CLIENT") "recibidas" else "simuladas"}", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Temperatura", "${"%.1f".format(reading.temperature)} °C", Modifier.weight(1f))
                            MetricCard("Humedad", "${reading.humidity} %", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Presión", "${reading.pressure} hPa", Modifier.weight(1f))
                            MetricCard("Gas", "${reading.gas} kΩ", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("CO (referencia)", "${reading.co} mV", Modifier.weight(1f))
                            MetricCard("Pulso", "${reading.bpm} BPM", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("SpO₂", "${reading.spo2} %", Modifier.weight(1f))
                            MetricCard("Muestra", "#${reading.sequence}", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Umbral de CO (demostración)", style = MaterialTheme.typography.titleMedium)
                                Text("${coThreshold.toInt()} mV")
                                Slider(value = coThreshold, onValueChange = { coThreshold = it }, valueRange = 200f..1500f, steps = 12, enabled = role == "HOST")
                                if (role == "HOST") Button(onClick = { lastAction = "Umbral simulado actualizado a ${coThreshold.toInt()} mV" }) { Text("Aplicar umbral") }
                                else Text("El umbral se configura desde el monitor.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (reading.co >= coThreshold) {
                            Spacer(Modifier.height(8.dp))
                            Text("Aviso de demostración: la lectura simulada alcanzó el umbral configurado.", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Importante: todas las lecturas, incluidos BPM y SpO₂, son ficticias; no son mediciones médicas ni ambientales reales.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Activadores simulados", style = MaterialTheme.typography.titleLarge)
                                Text(if (role == "CLIENT") "Los cambios se envían por Wi-Fi al teléfono monitor." else "Los cambios recibidos desde el controlador aparecen aquí." , style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                ControlRow("Modo manual", manual) { value -> updateControl("manual", value, role, bridge, { manual = it }, { lastAction = it }) }
                                ControlRow("Buzzer", buzzer) { value -> updateControl("buzzer", value, role, bridge, { buzzer = it }, { lastAction = it }) }
                                ControlRow("LED rojo", led) { value -> updateControl("led", value, role, bridge, { led = it }, { lastAction = it }) }
                                ControlRow("Vibración", vibration) { value -> updateControl("vibration", value, role, bridge, { vibration = it }, { lastAction = it }) }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = {
                                    if (role == "CLIENT") { bridge.send("PING|${System.currentTimeMillis()}"); lastAction = "PING enviado al monitor" }
                                    else lastAction = "PING solo se envía desde el teléfono controlador"
                                }, enabled = role == "CLIENT") { Text("Probar comunicación (PING)") }
                                Spacer(Modifier.height(8.dp))
                                Text("Última acción", style = MaterialTheme.typography.titleSmall)
                                Text(lastAction)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Seguridad y alcance", style = MaterialTheme.typography.titleSmall)
                            Text("Comunicación TCP dentro de la red local para demostración académica. No transmite credenciales. Úsala solo en una red de confianza; el protocolo de esta demo no cifra ni autentica los mensajes y no es apto para producción.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { bridge.close(); logged = false; pass = ""; user = ""; tab = 0; loginError = ""; linkStatus = "Desconectado" }) { Text("Desconectar y cerrar sesión") }
                }
            }
        }
    }
}

private fun updateControl(
    key: String, value: Boolean, role: String, bridge: TcpBridge,
    updateLocal: (Boolean) -> Unit, updateStatus: (String) -> Unit
) {
    updateLocal(value)
    if (role == "CLIENT") bridge.send("C|$key|${if (value) 1 else 0}")
    updateStatus("${if (value) "Activado" else "Desactivado"}: $key${if (role == "CLIENT") " (orden enviada)" else " (local)"}")
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    } }
}

@Composable
private fun ControlRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
