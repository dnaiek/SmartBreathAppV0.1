# SmartBreath — (Kotlin / Android, dos teléfonos)

## Qué demuestra esta versión

Esta app permite demostrar **interconexión inalámbrica real entre dos teléfonos Android** por TCP dentro de una misma red Wi-Fi local. No necesita sensores, ESP32 ni Internet. La telemetría de SmartBreath se genera de forma simulada en el teléfono monitor; el segundo teléfono recibe las muestras y envía órdenes de actuadores al primero.

- **Monitor / servidor:** genera telemetría cada 3 segundos, muestra el panel de monitoreo y escucha en el puerto TCP `45454`.
- **Controlador / cliente:** se conecta a la IP local del monitor, recibe telemetría y manda comandos para modo manual, buzzer, LED rojo y vibración.
- **PING/PONG:** el controlador puede enviar un PING y comprobar la respuesta del monitor.
- **Login:** validación local de demostración.

> La conexión teléfono-a-teléfono es real; los valores ambientales, BPM, SpO₂ y el efecto de los actuadores son ficticios. Esto no conecta ni controla hardware IoT.

## Requisitos

- Dos teléfonos Android con la aplicación instalada.
- Ambos teléfonos conectados a la **misma red Wi-Fi** o a un hotspot que permita comunicación entre clientes.
- En algunos hotspots, redes corporativas o redes públicas se bloquea la comunicación entre dispositivos (aislamiento de clientes). Si ocurre, usa otra red de confianza.
- Android Studio instalado en un computador para compilar e instalar la app, o un APK de depuración generado por Android Studio.

## Cómo compilar e instalar

1. Descomprime el ZIP.
2. Abre la carpeta `SmartBreathApp` en Android Studio.
3. Espera la sincronización de Gradle.
4. Selecciona la configuración `app` y ejecuta `Build > Build APK(s)` o ejecuta en un dispositivo conectado por USB.
5. Instala el mismo APK en ambos teléfonos. Si instalas manualmente, Android puede pedir autorización para instalar aplicaciones de esa fuente.
6. No marques las pruebas como completadas hasta realizarlas en los teléfonos.

## Credenciales de demostración

- Usuario: `admin`
- Contraseña: `SmartBreath2026!`

Estas credenciales son fijas en el código, solo para una evaluación académica. No son autenticación segura de producción.

## Prueba con dos teléfonos

### Teléfono 1 — Monitor

1. Conéctalo a la red Wi-Fi elegida.
2. Abre SmartBreath y selecciona **Monitor / servidor**.
3. Anota la IP local que aparece en la pantalla de inicio. Debe ser una IP alcanzable por el otro teléfono (por ejemplo, `192.168.1.25`).
4. Ingresa con las credenciales de demostración.
5. Espera el mensaje **Servidor listo en puerto 45454**. Mantén la app abierta y la pantalla encendida durante la prueba.

### Teléfono 2 — Controlador

1. Conéctalo a la misma red Wi-Fi.
2. Abre SmartBreath y selecciona **Controlador / cliente**.
3. Escribe la IP que anotaste del teléfono 1, sin `http://` ni el puerto.
4. Ingresa con las credenciales de demostración.
5. Comprueba que el estado indique conexión al monitor. Las lecturas deben llegar desde el otro teléfono.
6. En la pestaña **Activador**, cambia buzzer, LED, vibración o modo manual. Las órdenes se envían al monitor y los estados actualizados vuelven en la telemetría.
7. Pulsa **Probar comunicación (PING)** y comprueba el mensaje PONG.

### Si no conecta

- Comprueba que ambos estén en la misma red y que la IP esté bien escrita.
- Desactiva temporalmente VPN en los teléfonos si interfiere.
- Verifica que el monitor permanezca abierto y que indique servidor listo.
- Prueba otra red Wi-Fi o un hotspot sin aislamiento entre clientes.
- Usa **Reintentar conexión** en el controlador.
- La conexión usa el puerto TCP `45454`; una red que bloquee ese puerto impedirá la demostración.

## Arquitectura

- `MainActivity.kt`: login, pantallas Compose, generación/visualización de telemetría, estados de actuadores y protocolo de mensajes.
- `TcpBridge.kt`: servidor TCP y cliente TCP, gestión de conexión y detección de IP local.
- `AndroidManifest.xml`: permisos de red necesarios (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`).
- Kotlin + Jetpack Compose + Material 3.

### Protocolo de demostración

La app utiliza mensajes de texto delimitados por `|` sobre TCP:

- `T|...`: telemetría y estados de actuadores desde monitor a controlador.
- `C|buzzer|1`, `C|led|0`, etc.: comandos del controlador al monitor.
- `PING|...` / `PONG|...`: prueba básica de ida y vuelta.

TCP garantiza entrega ordenada dentro de la conexión, pero el protocolo de esta demo no implementa cifrado, autenticación mutua, firmas ni protección contra repetición. Úsalo únicamente en una red de confianza y no transmitas datos personales o sensibles. `usesCleartextTraffic=false` no cifra este protocolo TCP; no debe interpretarse como protección TLS.

## Seguridad y normativa

Se incluyen medidas preliminares como permisos de red explícitos, no transmitir la contraseña de login, `allowBackup=false` y documentación del alcance. La contraseña local está hardcodeada y el canal TCP de demo no cifra ni autentica mensajes; por lo tanto, esta app **no acredita conformidad** con ISO/IEC 27001, ISO/IEC 27400 ni normas OT. Para producción se requerirían, como mínimo, autenticación robusta, TLS con validación de certificados, gestión segura de secretos, autorización de comandos, registro de auditoría, tratamiento de errores y evaluación de riesgos.

## Relación con la rúbrica

| Criterio | Evidencia que se puede demostrar | Límite |
|---|---|---|
| Login | Formulario y rechazo de credenciales incorrectas | Credenciales de demo locales, no productivas |
| Monitoreo y activador | Pantallas Material 3, métricas y controles | Datos/actuadores simulados |
| Conectividad inalámbrica | TCP real entre dos teléfonos en la misma LAN | Depende de la red local y de su configuración |
| Resolución de la problemática | Monitor envía telemetría y recibe comandos | No se conecta a sensores ni actuadores físicos |
| Seguridad ISO/OT | Permisos mínimos y riesgos documentados | No equivale a certificación ni conformidad normativa |
| Interconexión entre apps Android | Una instalación actúa como servidor y otra como cliente | Mismo APK, dos instancias; canal de demo sin cifrado |
| Estabilidad | Reintento, estado de conexión y desconexión | Debe comprobarse con pruebas reales en los dispositivos |

## Lista de pruebas

- [ ] La app compila en Android Studio sin errores.
- [ ] Se instala en los dos teléfonos.
- [ ] El monitor inicia el servidor y muestra su IP correcta.
- [ ] El controlador se conecta usando esa IP.
- [ ] Las lecturas llegan y se actualizan aproximadamente cada 3 segundos.
- [ ] Un comando de buzzer se refleja en el monitor.
- [ ] Los comandos LED, vibración y modo manual se reflejan en el monitor.
- [ ] PING recibe respuesta PONG.
- [ ] Al cerrar el controlador, el monitor detecta la desconexión.
- [ ] Al reiniciar la conexión, el controlador puede volver a conectarse.
- [ ] Se capturan evidencias reales de ambos teléfonos y de la conexión.

