/*
 * ==============================================================================
 * PROYECTO: Centinela-IoT: Nodo de Seguridad Edge Multiparamétrico
 * INSTITUCIÓN: INACAP - Sede Puerto Montt
 * ASIGNATURA: Aplicaciones Móviles para IoT (TI3042) - Unidad 2
 * ALUMNOS: Juan Pablo Villavicencio, Daryl Izquierdo, Antonio Benedetti
 * DOCENTE: Rodrigo Arturo Ruiz Hess
 * HARDWARE: ESP32-WROOM-32 (Microcontrolador Doble Núcleo 240 MHz con Wi-Fi/BLE)
 * ==============================================================================
 * 
 * DESCRIPCIÓN DE PINES EN ESP32:
 * - HC-SR04 Trig:       GPIO 5  (Salida digital)
 * - HC-SR04 Echo:       GPIO 18 (Entrada digital vía divisor de tensión 1k / 2k)
 * - LDR GL5528:         GPIO 34 (Entrada analógica ADC1_CH6, 0 a 4095)
 * - Micro Servo SG90:   GPIO 13 (PWM 50 Hz, pestillo de bloqueo)
 * - Zumbador Pasivo:    GPIO 14 (Canal LEDC PWM, 2000 Hz)
 * - Pulsador Táctil:    GPIO 4  (INPUT_PULLUP, LOW al presionar)
 * - Alimentación:       5V / VIN para Servo y HC-SR04; 3V3 para ESP32 y LDR; GND común
 * ==============================================================================
 */

#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <ESP32Servo.h>
#include <ArduinoJson.h>

// --- Credenciales Wi-Fi (Modo Estación) ---
const char* WIFI_SSID     = "TU_RED_WIFI";
const char* WIFI_PASSWORD = "TU_PASSWORD";

// --- Servidor Central Node.js (opcional, como en el informe) ---
const char* SERVIDOR_NODE = "http://192.168.1.100:3000/telemetria";

// --- Asignación de Pines GPIO en ESP32 ---
const int PIN_TRIG      = 5;
const int PIN_ECHO      = 18;
const int PIN_LDR       = 34;
const int PIN_SERVO     = 13;
const int PIN_BUZZER    = 14;
const int PIN_BOTON     = 4;

// --- Umbrales de Seguridad ---
const int UMBRAL_DIST_CM  = 50;   // Perímetro invisible (cm)
const int UMBRAL_LUZ_ADC  = 800;  // Umbral de oscuridad (ADC 12 bits: 0 - 4095)

// --- Objetos y Estados Globales ---
Servo pestillo;
WebServer server(80); // Servidor Web HTTP embebido directo en el ESP32

bool alarmaActiva      = false;
bool perimetroArmado   = true;
int  pestilloAngulo     = 90;     // 90° = Desbloqueado, 0° = Bloqueado
unsigned long ultimoEnvio = 0;

// ==========================================
// MEDICIÓN DE DISTANCIA (HC-SR04)
// ==========================================
long medirDistancia() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  // Timeout de 30 ms (~5 metros)
  long duracion = pulseIn(PIN_ECHO, HIGH, 30000);
  if (duracion == 0) return 999; // Fuera de rango
  return duracion / 58;          // Conversión a centímetros
}

// ==========================================
// CONTROL DEL ZUMBADOR (LEDC PWM)
// ==========================================
void activarAlarmaSonora(bool activar) {
  if (activar) {
    ledcWrite(PIN_BUZZER, 128); // 50% ciclo de trabajo a 2000 Hz
  } else {
    ledcWrite(PIN_BUZZER, 0);   // Silencio
  }
}

// ==========================================
// CONTROL DEL PESTILLO FÍSICO (SERVO SG90)
// ==========================================
void moverPestillo(int angulo) {
  pestilloAngulo = constrain(angulo, 0, 180);
  pestillo.write(pestilloAngulo);
}

// ==========================================
// ENVÍO DE TELEMETRÍA AL SERVIDOR NODE.JS
// ==========================================
void enviarTelemetriaNode(long dist, int luz, bool boton) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  http.begin(SERVIDOR_NODE);
  http.addHeader("Content-Type", "application/json");

  String json = "{\"dist\":" + String(dist) +
                ",\"luz\":" + String(luz) +
                ",\"alarma\":" + String(alarmaActiva ? "true" : "false") +
                ",\"pestillo\":" + String(pestilloAngulo) +
                ",\"boton\":" + String(boton ? "true" : "false") + "}";

  http.POST(json);
  http.end();
}

// ==========================================
// RUTAS DE LA API REST INTERNA DEL ESP32
// ==========================================
void handleGetTelemetria() {
  long dist = medirDistancia();
  int luz   = analogRead(PIN_LDR);
  bool btn  = (digitalRead(PIN_BOTON) == LOW);

  String json = "{\"dist\":" + String(dist) +
                ",\"luz\":" + String(luz) +
                ",\"alarma\":" + String(alarmaActiva ? "true" : "false") +
                ",\"pestillo\":" + String(pestilloAngulo) +
                ",\"boton\":" + String(btn ? "true" : "false") +
                ",\"ip\":\"" + WiFi.localIP().toString() + "\"" +
                ",\"uptime\":" + String(millis() / 1000) + "}";

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

void handlePostComando() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"status\":\"error\",\"mensaje\":\"Sin cuerpo JSON\"}");
    return;
  }

  String body = server.arg("plain");
  StaticJsonDocument<256> doc;
  DeserializationError err = deserializeJson(doc, body);
  if (err) {
    server.send(400, "application/json", "{\"status\":\"error\",\"mensaje\":\"JSON invalido\"}");
    return;
  }

  String cmd = doc["comando"] | "";
  cmd.toLowerCase();

  if (cmd == "desbloquear" || cmd == "unlock") {
    alarmaActiva = false;
    activarAlarmaSonora(false);
    moverPestillo(90);
    server.send(200, "application/json", "{\"status\":\"ok\",\"mensaje\":\"Pestillo abierto remotamente (90 grados)\"}");
  } else if (cmd == "bloquear" || cmd == "lock") {
    moverPestillo(0);
    server.send(200, "application/json", "{\"status\":\"ok\",\"mensaje\":\"Pestillo bloqueado remotamente (0 grados)\"}");
  } else if (cmd == "silenciar" || cmd == "silence") {
    activarAlarmaSonora(false);
    alarmaActiva = false;
    server.send(200, "application/json", "{\"status\":\"ok\",\"mensaje\":\"Alarma silenciada\"}");
  } else if (cmd == "armar") {
    perimetroArmado = true;
    server.send(200, "application/json", "{\"status\":\"ok\",\"mensaje\":\"Perimetro armado\"}");
  } else if (cmd == "desarmar") {
    perimetroArmado = false;
    alarmaActiva = false;
    activarAlarmaSonora(false);
    moverPestillo(90);
    server.send(200, "application/json", "{\"status\":\"ok\",\"mensaje\":\"Perimetro desarmado\"}");
  } else if (cmd == "gpio_set") {
    int pin = doc["pin"] | -1;
    int val = doc["valor"] | 0;
    if (pin >= 0) {
      pinMode(pin, OUTPUT);
      digitalWrite(pin, val);
      server.send(200, "application/json", "{\"status\":\"ok\",\"mensaje\":\"GPIO actualizado\"}");
    }
  } else {
    server.send(404, "application/json", "{\"status\":\"error\",\"mensaje\":\"Comando no reconocido\"}");
  }
}

// ==========================================
// CONFIGURACIÓN INICIAL (SETUP)
// ==========================================
void setup() {
  Serial.begin(115200);
  Serial.println("\n=== INICIANDO NODO CENTINELA-IOT (ESP32) ===");

  // Configuración de Pines I/O
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_BOTON, INPUT_PULLUP);

  // Configuración PWM del Zumbador (Canal LEDC a 2000 Hz, resolución 8 bits)
  ledcAttach(PIN_BUZZER, 2000, 8);
  activarAlarmaSonora(false);

  // Configuración del Servomotor SG90
  ESP32PWM::allocateTimer(0);
  pestillo.setPeriodHertz(50); // 50 Hz estándar para servos
  pestillo.attach(PIN_SERVO, 500, 2400);
  moverPestillo(90); // Posición inicial: Abierto

  // Conexión Wi-Fi
  Serial.print("Conectando a Wi-Fi: ");
  Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 20) {
    delay(500);
    Serial.print(".");
    intentos++;
  }

  // Si no conecta al router, levantar Access Point propio de respaldo
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\n[AVISO] No se pudo conectar a la red. Levantando Modo Access Point...");
    WiFi.mode(WIFI_AP);
    WiFi.softAP("ESP32_CENTINELA_AP", "centinela2026");
    Serial.print("AP IP: ");
    Serial.println(WiFi.softAPIP());
  } else {
    Serial.print("\nWi-Fi Conectado. IP Local: ");
    Serial.println(WiFi.localIP());
  }

  // Endpoints del Servidor Web HTTP embebido
  server.on("/telemetria", HTTP_GET, handleGetTelemetria);
  server.on("/comando", HTTP_POST, handlePostComando);
  server.begin();
  Serial.println("Servidor Web HTTP activo en puerto 80.");
}

// ==========================================
// CICLO PRINCIPAL (LOOP) - EDGE COMPUTING
// ==========================================
void loop() {
  server.handleClient(); // Atiende peticiones HTTP de la app Android

  long distancia = medirDistancia();
  int  luz       = analogRead(PIN_LDR);
  bool boton     = (digitalRead(PIN_BOTON) == LOW); // LOW al pulsar

  // 1. Detección de Intrusión Perimetral (Edge Computing)
  if (perimetroArmado && !alarmaActiva && distancia > 0 && distancia < UMBRAL_DIST_CM) {
    Serial.println("[ALERTA] ¡Intrusión detectada en perímetro! Distancia: " + String(distancia) + " cm");
    alarmaActiva = true;
    moverPestillo(0);            // Bloqueo físico inmediato del pestillo
    activarAlarmaSonora(true);   // Activa zumbador de alerta a 2000 Hz
  }

  // 2. Secuencia de Desarme Físico Simultáneo (Fusión de Sensores):
  // Exige: Perímetro despejado (> 50 cm) + Oscuridad (< 800 ADC) + Pulsación física
  if (alarmaActiva && distancia > UMBRAL_DIST_CM && luz < UMBRAL_LUZ_ADC && boton) {
    Serial.println("[SEGURIDAD] Secuencia física de desarme completada con éxito.");
    alarmaActiva = false;
    activarAlarmaSonora(false);
    moverPestillo(90);          // Liberación física del pestillo
  }

  // 3. Envío periódico de telemetría hacia el servidor Node.js (cada 2 segundos)
  if (millis() - ultimoEnvio > 2000) {
    enviarTelemetriaNode(distancia, luz, boton);
    ultimoEnvio = millis();
  }

  delay(50);
}
