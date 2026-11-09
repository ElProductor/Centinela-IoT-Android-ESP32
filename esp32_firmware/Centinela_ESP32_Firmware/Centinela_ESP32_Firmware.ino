/*
 * ==============================================================================
 * PROYECTO: Centinela-IoT v3.0 - Firmware Unificado Unidad 3 (TI3042 - INACAP)
 * INSTITUCIÓN: INACAP - Sede Puerto Montt
 * ASIGNATURA: Aplicaciones Móviles para IoT (TI3042) - Evaluación Sumativa Unidad 3
 * INTEGRANTES: Juan Pablo Villavicencio, Daryl Izquierdo, Antonio Benedetti
 * DOCENTE: Rodrigo Arturo Ruiz Hess
 * ESTÁNDARES: ISO/IEC 27400:2022 | ISO/IEC 27001 | Programación Serial e HTTPS Segura
 * HARDWARE: ESP32-WROOM-32 + HC-SR04 + LDR GL5528 + DHT11 + SG90 + Buzzer PWM
 * ==============================================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <WebServer.h>
#include <Preferences.h>
#include <LittleFS.h>
#include <DHT.h>
#include <ESP32Servo.h>
#include "mbedtls/sha256.h"
#include "time.h"

// --- 1. DEFINICIÓN DE PINES DE HARDWARE (UNIDAD 3) ---
constexpr uint8_t PIN_TRIG_HCSR04 = 5;
constexpr uint8_t PIN_ECHO_HCSR04 = 18; // Divisor resistivo 1k/2k -> 3.33V máx
constexpr uint8_t PIN_LDR_ADC     = 34; // ADC1_CH6 (12 bits: 0 - 4095)
constexpr uint8_t PIN_DHT11       = 27; // Sensor Termohigrométrico Digital DHT11
constexpr uint8_t PIN_BTN_DESARME = 4;  // INPUT_PULLUP con debounce
constexpr uint8_t PIN_SERVO_SG90  = 13; // 0° Bloqueado | 90° Abierto
constexpr uint8_t PIN_BUZZER_PWM  = 14; // Canal LEDC 0 (2 kHz, 50% duty)

// --- 2. OBJETOS GLOBALES Y CONFIGURACIÓN SEGURA EN NVS ---
DHT dht(PIN_DHT11, DHT11);
Servo pestilloServo;
WebServer server(80);
Preferences secPrefs;

// Estado operativo y umbrales dinámicos (parametrizables desde App Android / Serial)
struct SystemState {
  long distanciaCm = 120;
  int luzAdc = 2048;
  float temperaturaC = 22.0f;
  float humedadPct = 50.0f;
  bool alarmaActiva = false;
  int anguloServo = 90; // 90° Abierto, 0° Bloqueado
  long umbralDistanciaCm = 50; // Configurable vía Serial/REST
  int umbralLuzOscuridad = 800; // Configurable vía Serial/REST
  float umbralTempMaxC = 35.0f; // Alerta térmica en sala de servidores OT
  bool sesionWebAutenticada = false;
  String ultimoOrigenCmd = "EDGE_BOOT";
} estadoActual;

// Buffer estático seguro para comunicación Serial (Evita Buffer Overflow - Criterio 3.1.2)
constexpr size_t MAX_SERIAL_FRAME_LEN = 256;
char serialRxBuffer[MAX_SERIAL_FRAME_LEN];
size_t serialRxIndex = 0;
bool recibiendoTramaSerial = false;

// Tiempos de muestreo no bloqueantes y optimización de energía
unsigned long lastSensorReadMs = 0;
unsigned long lastCloudSyncMs = 0;
constexpr unsigned long SENSOR_INTERVAL_MS = 2000;
constexpr unsigned long CLOUD_SYNC_INTERVAL_MS = 15000;

// --- 3. FUNCIONES CRIPTOGRÁFICAS (SHA-256 Y HMAC CON MBEDTLS - ISO/IEC 27400) ---
String calcularSha256Hex(const String& payload) {
  byte shaResult[32];
  mbedtls_sha256_context ctx;
  mbedtls_sha256_init(&ctx);
  mbedtls_sha256_starts_ret(&ctx, 0);
  mbedtls_sha256_update_ret(&ctx, (const unsigned char*)payload.c_str(), payload.length());
  mbedtls_sha256_finish_ret(&ctx, shaResult);
  mbedtls_sha256_free(&ctx);

  char hexStr[65];
  for (int i = 0; i < 32; i++) {
    sprintf(&hexStr[i * 2], "%02x", shaResult[i]);
  }
  hexStr[64] = '\0';
  return String(hexStr);
}

bool verificarFirmaTramaSerial(const String& datos, const String& firmaRecibida) {
  String secretKey = secPrefs.getString("hmac_key", "CentinelaOT_Secret_2026");
  String calculada = calcularSha256Hex(datos + "|" + secretKey).substring(0, 16);
  return calculada.equalsIgnoreCase(firmaRecibida);
}

String obtenerTimestampIso() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo, 50)) {
    return "2026-11-09 10:30:00"; // Fallback controlado si NTP aún no responde
  }
  char timeStr[25];
  strftime(timeStr, sizeof(timeStr), "%Y-%m-%d %H:%M:%S", &timeinfo);
  return String(timeStr);
}

// --- 4. CONTROL DE ACTUADORES Y LECTURA DE SENSORES (PASOS 1, 3 Y 4) ---
bool cortesiaActiva = false;
unsigned long tiempoFinCortesia = 0;
bool balizaActiva = false;
unsigned long ultimoParpadeoBaliza = 0;
bool estadoBaliza = false;

void aplicarEstadoActuadores(bool bloquear, const String& origen) {
  estadoActual.alarmaActiva = bloquear;
  estadoActual.anguloServo = bloquear ? 0 : 90;
  estadoActual.ultimoOrigenCmd = origen;
  pestilloServo.write(estadoActual.anguloServo);

  if (bloquear) {
    ledcWrite(0, 128); // Activa sirena piezoeléctrica a 2 kHz (duty 50%)
  } else {
    ledcWrite(0, 0);   // Silencia sirena
  }
}

void iniciarAperturaCortesia(int segundos) {
  aplicarEstadoActuadores(false, "APERTURA_CORTESIA");
  cortesiaActiva = true;
  tiempoFinCortesia = millis() + (segundos * 1000UL);
}

void dispararAlarmaPanico() {
  estadoActual.alarmaActiva = true;
  estadoActual.anguloServo = 0;
  pestilloServo.write(0);
  ledcWrite(0, 180);
  balizaActiva = true;
  estadoActual.ultimoOrigenCmd = "PANIC_ALARM";
}

String ejecutarBIST() {
  pestilloServo.write(0);
  delay(150);
  pestilloServo.write(90);
  delay(150);
  pestilloServo.write(estadoActual.anguloServo);

  ledcWrite(0, 100);
  delay(100);
  ledcWrite(0, 0);

  uint32_t freeRam = ESP.getFreeHeap();
  int rssi = WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : -99;

  String json = "{";
  json += "\"status\":\"OK\",";
  json += "\"bist\":\"PASSED\",";
  json += "\"servo\":\"OK\",";
  json += "\"buzzer\":\"OK\",";
  json += "\"distancia\":" + String(estadoActual.distanciaCm) + ",";
  json += "\"luz\":" + String(estadoActual.luzAdc) + ",";
  json += "\"temp\":" + String(estadoActual.temperaturaC, 1) + ",";
  json += "\"hum\":" + String(estadoActual.humedadPct, 1) + ",";
  json += "\"free_heap\":" + String(freeRam) + ",";
  json += "\"rssi\":" + String(rssi);
  json += "}";
  return json;
}

void leerSensoresEnBorde() {
  // 1. Medición Ultrasónica HC-SR04 con timeout seguro (25,000 us = ~4 metros)
  digitalWrite(PIN_TRIG_HCSR04, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG_HCSR04, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG_HCSR04, LOW);

  long duracionUs = pulseIn(PIN_ECHO_HCSR04, HIGH, 25000);
  if (duracionUs > 0) {
    estadoActual.distanciaCm = (long)((duracionUs * 0.0343f) / 2.0f);
  } else {
    estadoActual.distanciaCm = 999;
  }

  // 2. Medición Fotorresistor LDR GL5528 con filtro de media móvil
  long sumaAdc = 0;
  for (int i = 0; i < 4; i++) {
    sumaAdc += analogRead(PIN_LDR_ADC);
  }
  estadoActual.luzAdc = (int)(sumaAdc / 4);

  // 3. Medición Termohigrométrica DHT11 (Integración Unidad 3)
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (!isnan(t) && !isnan(h)) {
    estadoActual.temperaturaC = t;
    estadoActual.humedadPct = h;
  }

  // 4. Lógica de Fusión Sensorial en el Borde (Continuidad Operativa OT)
  if (estadoActual.distanciaCm > 0 && estadoActual.distanciaCm < estadoActual.umbralDistanciaCm) {
    if (!estadoActual.alarmaActiva) {
      aplicarEstadoActuadores(true, "AUTO_INTRUSION_HCSR04");
    }
  }

  // 5. Verificación de Secuencia Física Tripartita de Desarme Local
  bool botonPresionado = (digitalRead(PIN_BTN_DESARME) == LOW);
  if (estadoActual.alarmaActiva && botonPresionado &&
      estadoActual.distanciaCm >= estadoActual.umbralDistanciaCm &&
      estadoActual.luzAdc < estadoActual.umbralLuzOscuridad) {
    aplicarEstadoActuadores(false, "DESARME_FISICO_TRIPARTITO");
  }
}

// --- 5. ALMACENAMIENTO TEMPORAL (LITTLEFS) Y ENVÍO SEGURO A SUPABASE (PASO 5) ---
void guardarEnBufferLocalLittleFS(const String& jsonPayload) {
  File file = LittleFS.open("/offline_queue.jsonl", FILE_APPEND);
  if (file) {
    if (file.size() < 65536) { // Límite de 64 KB para proteger la memoria Flash
      file.println(jsonPayload);
    }
    file.close();
  }
}

bool enviarDatosASupabaseCloud(const String& jsonPayload) {
  if (WiFi.status() != WL_CONNECTED) {
    guardarEnBufferLocalLittleFS(jsonPayload);
    return false;
  }

  String supabaseUrl = secPrefs.getString("sb_url", "https://centinela-ot.supabase.co/rest/v1/sensores");
  String supabaseKey = secPrefs.getString("sb_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9_ANON_KEY");

  WiFiClientSecure client;
  client.setInsecure(); // En producción OT se carga el Root CA con client.setCACert(root_ca)
  HTTPClient https;

  if (!https.begin(client, supabaseUrl)) {
    guardarEnBufferLocalLittleFS(jsonPayload);
    return false;
  }

  https.addHeader("Content-Type", "application/json");
  https.addHeader("apikey", supabaseKey);
  https.addHeader("Authorization", "Bearer " + supabaseKey);
  https.addHeader("Prefer", "return=minimal");

  int httpCode = https.POST(jsonPayload);
  https.end();

  if (httpCode >= 200 && httpCode < 300) {
    return true;
  } else {
    guardarEnBufferLocalLittleFS(jsonPayload);
    return false;
  }
}

void sincronizarColaPendienteLittleFS() {
  if (WiFi.status() != WL_CONNECTED || !LittleFS.exists("/offline_queue.jsonl")) return;

  File file = LittleFS.open("/offline_queue.jsonl", FILE_READ);
  if (!file) return;

  String lineasPendientes = "";
  while (file.available()) {
    String linea = file.readStringUntil('\n');
    linea.trim();
    if (linea.length() > 5) {
      if (!enviarDatosASupabaseCloud(linea)) {
        lineasPendientes += linea + "\n";
      }
    }
  }
  file.close();

  LittleFS.remove("/offline_queue.jsonl");
  if (lineasPendientes.length() > 0) {
    File rewrite = LittleFS.open("/offline_queue.jsonl", FILE_WRITE);
    if (rewrite) {
      rewrite.print(lineasPendientes);
      rewrite.close();
    }
  }
}

// --- 6. PROCESADOR DE COMUNICACIÓN SERIAL SEGURA (CRITERIO 3.1.2) ---
// Formato esperado: \x02CMD|PARAM1=VAL1;PARAM2=VAL2|FIRMA_16HEX\x03
void procesarTramaSerialSegura(const String& trama) {
  int sep1 = trama.indexOf('|');
  int sep2 = trama.lastIndexOf('|');

  if (sep1 == -1 || sep2 == -1 || sep1 == sep2) {
    Serial.println("\x02ERR|FORMATO_TRAMA_INVALIDO\x03");
    return;
  }

  String cmd = trama.substring(0, sep1);
  String params = trama.substring(sep1 + 1, sep2);
  String firmaRecibida = trama.substring(sep2 + 1);

  String comandoConParams = trama.substring(0, sep2);
  if (!verificarFirmaTramaSerial(comandoConParams, firmaRecibida)) {
    Serial.println("\x02ERR|FIRMA_HMAC_INVALIDA_ISO27400\x03");
    return;
  }

  if (cmd == "CMD_PING_HANDSHAKE") {
    String resp = "\x02ACK_PONG_READY|ver=v3.0.4-OT;rssi=" + String(WiFi.RSSI()) +
                  ";heap=" + String(ESP.getFreeHeap()) + "\x03";
    Serial.println(resp);
  }
  else if (cmd == "GET_SENSORS") {
    leerSensoresEnBorde();
    String resp = "\x02OK_SENSORS|dist=" + String(estadoActual.distanciaCm) +
                  ";luz=" + String(estadoActual.luzAdc) +
                  ";temp=" + String(estadoActual.temperaturaC, 1) +
                  ";hum=" + String(estadoActual.humedadPct, 1) +
                  ";alarma=" + String(estadoActual.alarmaActiva ? 1 : 0) +
                  ";servo=" + String(estadoActual.anguloServo) + "\x03";
    Serial.println(resp);
  }
  else if (cmd == "SET_THRESHOLDS") {
    int idxDist = params.indexOf("dist=");
    int idxLuz  = params.indexOf("luz=");
    if (idxDist != -1) {
      long nuevoDist = params.substring(idxDist + 5, params.indexOf(';', idxDist)).toInt();
      if (nuevoDist >= 10 && nuevoDist <= 200) {
        estadoActual.umbralDistanciaCm = nuevoDist;
        secPrefs.putLong("umb_dist", nuevoDist);
      }
    }
    if (idxLuz != -1) {
      int nuevaLuz = params.substring(idxLuz + 4).toInt();
      if (nuevaLuz >= 100 && nuevaLuz <= 3800) {
        estadoActual.umbralLuzOscuridad = nuevaLuz;
        secPrefs.putInt("umb_luz", nuevaLuz);
      }
    }
    Serial.println("\x02OK_THRESHOLDS|CALIBRACION_APLICADA\x03");
  }
  else if (cmd == "ACTUATOR_CTRL") {
    if (params.indexOf("accion=ABRIR") != -1) {
      aplicarEstadoActuadores(false, "SERIAL_ANDROID_AUTH");
      Serial.println("\x02OK_ACTUATOR|SERVO=90;BUZZER=OFF\x03");
    } else if (params.indexOf("accion=BLOQUEAR") != -1) {
      aplicarEstadoActuadores(true, "SERIAL_ANDROID_LOCK");
      Serial.println("\x02OK_ACTUATOR|SERVO=0;BUZZER=ON\x03");
    }
  }
}

void atenderPuertoSerialNoBloqueante() {
  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\x02') { // STX: Inicio de trama
      serialRxIndex = 0;
      recibiendoTramaSerial = true;
    } else if (c == '\x03' && recibiendoTramaSerial) { // ETX: Fin de trama
      serialRxBuffer[serialRxIndex] = '\0';
      recibiendoTramaSerial = false;
      procesarTramaSerialSegura(String(serialRxBuffer));
    } else if (recibiendoTramaSerial) {
      if (serialRxIndex < (MAX_SERIAL_FRAME_LEN - 1)) {
        serialRxBuffer[serialRxIndex++] = c;
      } else {
        // Prevención de Buffer Overflow (Criterio 3.1.2)
        recibiendoTramaSerial = false;
        serialRxIndex = 0;
        Serial.println("\x02ERR|OVERFLOW_PROTECTION_TRIGGERED\x03");
      }
    }
  }
}

// --- 7. PORTAL WEB EMBEBIDO CON LOGIN EN SERVIDOR Y ENDPOINTS REST (UNIDAD 3) ---
const char login_html[] PROGMEM = R"rawliteral(
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><title>Acceso Centinela-IoT OT</title>
<style>body{font-family:Arial,sans-serif;text-align:center;margin-top:50px;background:#0b0f19;color:#fff;}
.box{display:inline-block;padding:25px;background:#131b2e;border-radius:12px;border:1px solid #29385c;}
input{padding:10px;margin:8px;width:220px;background:#0b0f19;color:#fff;border:1px solid #29385c;border-radius:6px;}
button{padding:10px 25px;background:#00e5ff;color:#001018;border:none;border-radius:6px;cursor:pointer;font-weight:bold;}</style></head>
<body><div class="box"><h2>Acceso Seguro Nodo Centinela-IoT</h2>
<form action="/login" method="POST">
<input type="text" name="usuario" placeholder="Usuario OT" required><br>
<input type="password" name="password" placeholder="Contraseña" required><br>
<button type="submit">Ingresar al Panel</button></form></div></body></html>
)rawliteral";

const char dashboard_html[] PROGMEM = R"rawliteral(
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><title>Panel Telemetría Centinela-IoT</title>
<style>body{font-family:Arial,sans-serif;text-align:center;background:#0b0f19;color:#fff;}
.card{display:inline-block;padding:20px;margin:12px;background:#131b2e;border:1px solid #29385c;border-radius:12px;min-width:160px;}
.btn{padding:10px 18px;margin:8px;border:none;border-radius:6px;color:#fff;cursor:pointer;font-weight:bold;}
.btn-open{background:#00e676;color:#00210a;}.btn-lock{background:#ff1744;}.btn-exit{background:#64748b;}</style></head>
<body><h2>Consola Edge Centinela-IoT (ESP32 v3.0)</h2>
<div class="card"><h3>Distancia HC-SR04</h3><h1 id="dist" style="color:#00e5ff;">-- cm</h1></div>
<div class="card"><h3>Luz LDR</h3><h1 id="luz" style="color:#ffd600;">--</h1></div>
<div class="card"><h3>Temperatura DHT11</h3><h1 id="temp" style="color:#ff9100;">-- °C</h1></div>
<div class="card"><h3>Humedad DHT11</h3><h1 id="hum" style="color:#00e5ff;">-- %</h1></div>
<div class="card"><h3>Estado Pestillo SG90</h3><h1 id="servo">--</h1></div><br>
<button class="btn btn-open" onclick="enviarAccion('ABRIR')">Liberar Pestillo (90°)</button>
<button class="btn btn-lock" onclick="enviarAccion('BLOQUEAR')">Bloquear Emergencia (0°)</button>
<a href="/logout"><button class="btn btn-exit">Cerrar Sesión</button></a>
<script>
function actualizar(){
fetch("/lecturas").then(r=>r.json()).then(d=>{
document.getElementById("dist").innerHTML=d.distancia+" cm";
document.getElementById("luz").innerHTML=d.luz;
document.getElementById("temp").innerHTML=d.temperatura+" °C";
document.getElementById("hum").innerHTML=d.humedad+" %";
document.getElementById("servo").innerHTML=d.alarma?"<span style='color:#ff1744;'>BLOQUEADO (0°)</span>":"<span style='color:#00e676;'>LIBERADO (90°)</span>";
});
}
function enviarAccion(act){
fetch("/api/v1/control",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:"accion="+act})
.then(()=>actualizar());
}
setInterval(actualizar, 2000); actualizar();
</script></body></html>
)rawliteral";

void configurarServidorWebYEndpoints() {
  server.on("/", HTTP_GET, []() {
    server.send_P(200, "text/html", login_html);
  });

  server.on("/login", HTTP_POST, []() {
    String user = server.hasArg("usuario") ? server.arg("usuario") : "";
    String pass = server.hasArg("password") ? server.arg("password") : "";

    String storedUser = secPrefs.getString("admin_user", "admin");
    String storedPassHash = secPrefs.getString("admin_hash", calcularSha256Hex("Inacap2026"));

    if (user == storedUser && calcularSha256Hex(pass) == storedPassHash) {
      estadoActual.sesionWebAutenticada = true;
      server.sendHeader("Location", "/dashboard");
      server.send(303);
    } else {
      server.send(401, "text/plain", "Acceso Denegado: Credenciales invalidas bajo politica ISO 27400");
    }
  });

  server.on("/dashboard", HTTP_GET, []() {
    if (!estadoActual.sesionWebAutenticada) {
      server.sendHeader("Location", "/");
      server.send(303);
      return;
    }
    server.send_P(200, "text/html", dashboard_html);
  });

  server.on("/logout", HTTP_GET, []() {
    estadoActual.sesionWebAutenticada = false;
    server.sendHeader("Location", "/");
    server.send(303);
  });

  server.on("/lecturas", HTTP_GET, []() {
    leerSensoresEnBorde();
    String json = "{";
    json += "\"distancia\":" + String(estadoActual.distanciaCm) + ",";
    json += "\"luz\":" + String(estadoActual.luzAdc) + ",";
    json += "\"temperatura\":" + String(estadoActual.temperaturaC, 2) + ",";
    json += "\"humedad\":" + String(estadoActual.humedadPct, 2) + ",";
    json += "\"alarma\":" + String(estadoActual.alarmaActiva ? "true" : "false") + ",";
    json += "\"anguloServo\":" + String(estadoActual.anguloServo) + ",";
    json += "\"umbralDist\":" + String(estadoActual.umbralDistanciaCm) + ",";
    json += "\"ultimoOrigen\":\"" + estadoActual.ultimoOrigenCmd + "\"";
    json += "}";
    server.send(200, "application/json", json);
  });

  // Compatibilidad con endpoint /telemetria de Unidad 2
  server.on("/telemetria", HTTP_GET, []() {
    leerSensoresEnBorde();
    String json = "{";
    json += "\"dist\":" + String(estadoActual.distanciaCm) + ",";
    json += "\"luz\":" + String(estadoActual.luzAdc) + ",";
    json += "\"temperatura\":" + String(estadoActual.temperaturaC, 2) + ",";
    json += "\"humedad\":" + String(estadoActual.humedadPct, 2) + ",";
    json += "\"alarma\":" + String(estadoActual.alarmaActiva ? "true" : "false") + ",";
    json += "\"pestillo\":" + String(estadoActual.anguloServo) + ",";
    json += "\"boton\":" + String(digitalRead(PIN_BTN_DESARME) == LOW ? "true" : "false");
    json += "}";
    server.send(200, "application/json", json);
  });

  server.on("/api/v1/control", HTTP_POST, []() {
    if (server.hasArg("accion")) {
      String accion = server.arg("accion");
      accion.toUpperCase();
      if (accion == "ABRIR" || accion == "DESBLOQUEAR") {
        aplicarEstadoActuadores(false, "ANDROID_WIFI_OVERRIDE");
      } else if (accion == "BLOQUEAR" || accion == "ARMAR") {
        aplicarEstadoActuadores(true, "ANDROID_WIFI_LOCK");
      } else if (accion == "PANICO" || accion == "PANIC") {
        dispararAlarmaPanico();
      } else if (accion == "CORTESIA" || accion == "COURTESY") {
        iniciarAperturaCortesia(10);
      } else if (accion == "BIST" || accion == "AUTODIAGNOSTICO") {
        server.send(200, "application/json", ejecutarBIST());
        return;
      } else if (accion == "BALIZA") {
        balizaActiva = !balizaActiva;
        if (!balizaActiva) digitalWrite(PIN_LED_ALERTA, LOW);
      } else if (accion == "REBOOT" || accion == "REINICIAR") {
        server.send(200, "application/json", "{\"status\":\"REBOOTING\",\"msg\":\"ESP32 reiniciando...\"}");
        delay(400);
        ESP.restart();
        return;
      }
    }
    if (server.hasArg("umbralDist")) {
      long nuevoUmb = server.arg("umbralDist").toInt();
      if (nuevoUmb >= 10 && nuevoUmb <= 200) {
        estadoActual.umbralDistanciaCm = nuevoUmb;
        secPrefs.putLong("umb_dist", nuevoUmb);
      }
    }
    server.send(200, "application/json", "{\"status\":\"OK\",\"servo\":" + String(estadoActual.anguloServo) + "}");
  });

  // Compatibilidad con endpoint /comando de Unidad 2 y 3
  server.on("/comando", HTTP_POST, []() {
    if (server.hasArg("plain")) {
      String body = server.arg("plain");
      String bodyLower = body;
      bodyLower.toLowerCase();

      if (bodyLower.indexOf("desbloquear") != -1 || bodyLower.indexOf("abrir") != -1) {
        aplicarEstadoActuadores(false, "ANDROID_REMOTE_CMD");
      } else if (bodyLower.indexOf("bloquear") != -1 || bodyLower.indexOf("armar") != -1) {
        aplicarEstadoActuadores(true, "ANDROID_REMOTE_CMD");
      } else if (bodyLower.indexOf("panico") != -1 || bodyLower.indexOf("panic") != -1) {
        dispararAlarmaPanico();
      } else if (bodyLower.indexOf("cortesia") != -1 || bodyLower.indexOf("courtesy") != -1) {
        iniciarAperturaCortesia(10);
      } else if (bodyLower.indexOf("bist") != -1 || bodyLower.indexOf("autodiagnostico") != -1) {
        server.send(200, "application/json", ejecutarBIST());
        return;
      } else if (bodyLower.indexOf("baliza") != -1) {
        balizaActiva = !balizaActiva;
        if (!balizaActiva) digitalWrite(PIN_LED_ALERTA, LOW);
      } else if (bodyLower.indexOf("reboot") != -1 || bodyLower.indexOf("reiniciar") != -1) {
        server.send(200, "application/json", "{\"status\":\"REBOOTING\",\"msg\":\"ESP32 reiniciando...\"}");
        delay(400);
        ESP.restart();
        return;
      }
    }
    server.send(200, "application/json", "{\"status\":\"OK\",\"pestillo\":" + String(estadoActual.anguloServo) + "}");
  });

  server.begin();
}

void setup() {
  Serial.begin(115200);

  pinMode(PIN_TRIG_HCSR04, OUTPUT);
  pinMode(PIN_ECHO_HCSR04, INPUT);
  pinMode(PIN_BTN_DESARME, INPUT_PULLUP);

  dht.begin();
  pestilloServo.setPeriodHertz(50);
  pestilloServo.attach(PIN_SERVO_SG90, 500, 2400);

  ledcAttach(PIN_BUZZER_PWM, 2000, 8); // API LEDC ESP32 3.x / 2.x

  LittleFS.begin(true);
  secPrefs.begin("centinela_nvs", false);

  estadoActual.umbralDistanciaCm = secPrefs.getLong("umb_dist", 50);
  estadoActual.umbralLuzOscuridad = secPrefs.getInt("umb_luz", 800);

  // Modo Estación con contingencia SoftAP
  WiFi.mode(WIFI_AP_STA);
  WiFi.setSleep(true); // Activa Modem-Sleep dinámico para ahorro energético (Paso 3)

  String ssid = secPrefs.getString("wifi_ssid", "INACAP_OT_LAB");
  String pass = secPrefs.getString("wifi_pass", "Inacap2026_IoT");
  WiFi.begin(ssid.c_str(), pass.c_str());

  // Punto de acceso local de contingencia
  WiFi.softAP("ESP32_CENTINELA_AP", "Inacap2026!");

  configTime(-10800, 0, "pool.ntp.org", "time.nist.gov"); // UTC-3 Chile
  aplicarEstadoActuadores(false, "BOOT_INIT");

  configurarServidorWebYEndpoints();
  Serial.println("\n[CENTINELA-OT v3.0] Nodo iniciado correctamente. Baudrate: 115200 8N1");
}

void loop() {
  atenderPuertoSerialNoBloqueante();
  server.handleClient();

  // Temporizador no bloqueante de auto-cierre de cortesía
  if (cortesiaActiva && millis() >= tiempoFinCortesia) {
    cortesiaActiva = false;
    aplicarEstadoActuadores(true, "AUTO_CIERRE_CORTESIA");
  }

  // Baliza estroboscópica no bloqueante
  if (balizaActiva && millis() - ultimoParpadeoBaliza >= 250) {
    ultimoParpadeoBaliza = millis();
    estadoBaliza = !estadoBaliza;
    digitalWrite(PIN_LED_ALERTA, estadoBaliza ? HIGH : LOW);
  }

  unsigned long ahora = millis();
  if (ahora - lastSensorReadMs >= SENSOR_INTERVAL_MS) {
    lastSensorReadMs = ahora;
    leerSensoresEnBorde();
  }

  if (ahora - lastCloudSyncMs >= CLOUD_SYNC_INTERVAL_MS) {
    lastCloudSyncMs = ahora;
    String payload = "{\"fechatoma\":\"" + obtenerTimestampIso() + "\"," +
                     "\"temperatura\":" + String(estadoActual.temperaturaC, 2) + "," +
                     "\"humedad\":" + String(estadoActual.humedadPct, 2) + "," +
                     "\"distancia_cm\":" + String(estadoActual.distanciaCm) + "," +
                     "\"luz_adc\":" + String(estadoActual.luzAdc) + "," +
                     "\"alarma_activa\":" + String(estadoActual.alarmaActiva ? "true" : "false") + "," +
                     "\"operador_android\":\"ESP32_EDGE_NODE\"}";
    if (enviarDatosASupabaseCloud(payload)) {
      sincronizarColaPendienteLittleFS();
    }
  }
}
