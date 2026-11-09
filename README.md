# Centinela-IoT v3.0: Nodo Edge & App Android OT 🛡️☁️⚡

[![INACAP](https://img.shields.io/badge/INACAP-Puerto%20Montt-red.svg)](https://www.inacap.cl)
[![Asignatura](https://img.shields.io/badge/TI3042-Aplicaciones%20M%C3%B3viles%20para%20IoT-blue.svg)]()
[![Hardware](https://img.shields.io/badge/Hardware-ESP32--WROOM--32%20%7C%20DHT11-00E5FF.svg)]()
[![Seguridad](https://img.shields.io/badge/Norma-ISO%2FIEC%2027400%3A2022-success.svg)]()
[![Cloud BaaS](https://img.shields.io/badge/Supabase-PostgreSQL%20Cloud-3ECF8E.svg)](docs/INTEGRACION_SUPABASE_Y_ROLES.md)
[![Serial Seguro](https://img.shields.io/badge/Serial%20USB--OTG-HMAC--SHA256%20Truncado-orange.svg)]()
[![Android](https://img.shields.io/badge/Android%20Studio-Kotlin%20%7C%20Material%203-brightgreen.svg)]()

> **Evaluación Sumativa Unidad 3 (35%) — Integración de Smart Devices con Aplicaciones Externas**  
> **Carrera:** Ingeniería / Técnico en Informática  
> **Integrantes:** Juan Pablo Villavicencio, Daryl Izquierdo, Antonio Benedetti  
> **Docente:** Rodrigo Arturo Ruiz Hess  
> **Fecha:** Noviembre de 2026  

---

## 📱 1. Descarga del APK Generado (v3.0)

El instalador compilado para Android se encuentra disponible en la raíz del repositorio:
* **Archivo APK:** [`CentinelaIoT_Universal_ESP32.apk`](CentinelaIoT_Universal_ESP32.apk)
* **Ruta de compilación:** `app/build/outputs/apk/debug/app-debug.apk`
* **Compatibilidad:** Android 8.0 (Oreo) en adelante (API 26 hasta API 35)

---

## 🎯 2. Visión General del Proyecto y Cumplimiento de Criterios (Unidad 3)

Esta solución ciberfísica unifica sensores, actuadores, protocolo serial seguro y persistencia cuádruple:
1. **Nodo Edge Multiparamétrico (ESP32-WROOM-32):**
   - Transductor ultrasónico HC-SR04 (GPIO 5/18 con divisor resistivo a 3.33V y timeout seguro 25 ms).
   - Fotorresistor LDR GL5528 (GPIO 34 ADC1_CH6 con filtro de media móvil).
   - **Sensor digital de temperatura y humedad DHT11 (GPIO 27 One-Wire).**
   - Micro servomotor SG90 (GPIO 13 PWM 50 Hz, 0° bloqueo / 90° apertura).
   - Zumbador piezoeléctrico pasivo (GPIO 14 canal LEDC, 2 kHz).
   - Pulsador físico con filtro debounce de 50 ms (GPIO 4 INPUT_PULLUP).
2. **Servidor Web Asíncrono Embebido en ESP32:**
   - Portal web en PROGMEM (`/`, `/login`, `/dashboard`, `/lecturas`, `/api/v1/control`, `/logout`) con autenticación validada estrictamente en backend C++ con hash SHA-256 en NVS (`Preferences.h`).
3. **Protocolo de Comunicación Serial Segura USB-OTG (Criterio 3.1.2):**
   - Analizador sintáctico no bloqueante a 115200 bps con buffer estático acotado a 256 bytes (anti-overflow).
   - Delimitadores binarios `<STX>` (`\x02`) y `<ETX>` (`\x03`) con firma criptográfica HMAC-SHA256 truncada a 16 hex.
   - Handshake bidireccional Mobile-MCU: `CMD_PING_HANDSHAKE` -> `ACK_PONG_READY`.
4. **Arquitectura de Persistencia Cuádruple (Store-and-Forward):**
   - **Capa 1:** NVS y LittleFS (`/offline_queue.jsonl`, 64 KB, 512 eventos) en ESP32.
   - **Capa 2:** SQLite Room en Android con tareas diferidas `WorkManager`.
   - **Capa 3:** API Local PHP/MySQL (`server_php/api/guardar_datos.php`) endurecida contra SQL Injection con sentencias preparadas.
   - **Capa 4:** Cloud Supabase PostgreSQL (`public.sensores`, `roles`, `app_users`, `audit_logs`) sobre HTTPS/TLS con políticas Row Level Security (RLS).
5. **Panel de Administración Dinámico de Roles (RBAC):**
   - Creación en caliente de roles a medida con permisos granulares (desarme, apertura remota, control GPIO, auditoría) y alta de operadores con contraseñas hasheadas (SHA-256 + Salt).

### Alineación con los Criterios de Evaluación de la Unidad 3:
* **3.1.1 (Estándar de envío de información segura):** Cifrado HTTPS/TLS con `WiFiClientSecure` y `OkHttp3`, cabeceras obligatorias `apikey` y Bearer JWT en Supabase, sentencias preparadas en PHP/MySQL y firmas SHA-256 con `mbedtls`.
* **3.1.2 (Comunicación Serial Segura):** Analizador sintáctico no bloqueante a 115200 bps con buffer estático de 256 bytes, delimitadores STX/ETX y firma obligatoria HMAC-SHA256 (`GET_SENSORS`, `SET_THRESHOLDS`, `ACTUATOR_CTRL`, `CMD_PING_HANDSHAKE`).
* **3.1.3 (Control Inalámbrico de Microcontrolador):** Servidor HTTP embebido en ESP32 para recepción de comandos remotos de actuación (SG90/Buzzer) y parametrización de sensores (HC-SR04/LDR/DHT11), más cliente HTTPS activo hacia Supabase Cloud.
* **3.1.4 (Conexión Segura App Android - IoT bajo ISO/IEC 27000):** Resguardo de tokens en `AndroidKeyStore` (AES-256-GCM), bloqueo de tráfico en texto claro (`network_security_config.xml`), minimización de datos personales, reconexión automática con Exponential Backoff with Jitter y almacenamiento Store-and-Forward.

---

## 🔐 3. Cuentas de Acceso para Evaluación (ISO 27400)

La aplicación cuenta con credenciales precargadas y botones de acceso rápido en la pantalla de inicio de sesión:

| Usuario | Contraseña | Rol Asignado | Permisos y Alcance OT |
| :--- | :--- | :--- | :--- |
| **`admin`** | `admin123` | **ADMIN** | Control total: Apertura remota de emergencia, armar/desarmar, cambio de parámetros de red/umbrales, purga y exportación de auditoría. |
| **`operador`** | `inacap2026` | **OPERATOR** | Control operacional: Armar/desarmar perímetro, silenciar alarmas, apertura forzada autorizada, visualización de logs. |
| **`auditor`** | `auditor123` | **MONITOR** | Solo lectura: Inspección de telemetría y trazabilidad de eventos de seguridad sin privilegios de mando físico. |

---

## ⚙️ 4. Modos de Operación: Hardware Real vs. Simulador Embebido

Para facilitar la evaluación y defensa del proyecto tanto en laboratorio con la maqueta física como en el aula sin red Wi-Fi compartida, la app incluye un **Simulador Virtual de Firmware ESP32**:

* **Modo Hardware Real (Wi-Fi ESP32):** La app sondea directamente la IP del ESP32 (por defecto `192.168.1.100` o la IP asignada por el router/Access Point) consultando los endpoints `/telemetria` y enviando comandos a `/comando`.
* **Modo Simulación (ESP32 Virtual):** Un motor interno de corrutinas emula el firmware exacto del ESP32. Incluye botones de prueba interactiva:
  - *Probar Intrusión (< 50 cm):* Dispara la alarma sonora, bloquea el pestillo a 0° y registra el evento crítico en SQLite.
  - *Restaurar Zona Segura (135 cm):* Vuelve al perímetro normal.
  - *Simular Oscuridad (< 800 ADC):* Satisface la condición lumínica de desarme.
  - *Pulsar Botón GPIO 4:* Simula la presencia humana requerida por el algoritmo de fusión de sensores.

---

## 🔌 5. Conexiones Físicas al ESP32-WROOM-32 (Fe de Erratas Oficial)

> **Nota para el informe:** Por un error de maquetado en la entrega anterior, la Figura 1 mostraba un Arduino UNO. El proyecto real y operativo utiliza **ESP32-WROOM-32**. (Ver detalle en [`docs/INFORME_CORRECCION_ESP32.md`](docs/INFORME_CORRECCION_ESP32.md)).

```
ESP32 GPIO 5  --------> Trig (HC-SR04)
ESP32 GPIO 18 <-------- Divisor de Tensión (1k / 2k) <--- Echo (HC-SR04) [Protección 3.3V]
ESP32 GPIO 34 <-------- Divisor LDR GL5528 + 10k a GND [ADC1_CH6]
ESP32 GPIO 13 --------> Señal PWM Servomotor SG90 (Pestillo)
ESP32 GPIO 14 --------> Zumbador Pasivo (Alarma 2000 Hz por LEDC)
ESP32 GPIO 4  <-------- Pulsador Táctil (INPUT_PULLUP a GND)
ESP32 5V/VIN  --------> VCC de HC-SR04 y Servomotor SG90
ESP32 3V3     --------> Referencia LDR
ESP32 GND     --------> Tierra común de todos los componentes
```

---

## 🚀 6. Instrucciones de Despliegue y Ejecución

### Opción A: Probar la APK en Teléfono Android
1. Transfiere el archivo `CentinelaIoT_Universal_ESP32.apk` a tu teléfono vía USB, Google Drive o WhatsApp.
2. Habilita la instalación desde orígenes desconocidos y presiona **Instalar**.
3. Abre la app, presiona el botón rápido **Admin** y luego **INICIAR SESIÓN**.
4. ¡Listo! Puedes probar el panel con el simulador interno o conectar tu ESP32 por Wi-Fi.

### Opción B: Cargar el Firmware al ESP32
1. Abre el sketch `esp32_firmware/Centinela_ESP32_Firmware.ino` en **Arduino IDE**.
2. Instala las librerías `ESP32Servo` y `ArduinoJson` desde el Gestor de Bibliotecas.
3. Modifica tu nombre de red y clave Wi-Fi en las constantes `WIFI_SSID` y `WIFI_PASSWORD`.
4. Conecta el ESP32 por USB y presiona **Subir**.
5. Abre el Monitor Serial a 115200 baudios para conocer la IP local asignada.
6. En la pestaña **Configuración** de la app Android, ingresa la IP del ESP32 y desactiva el modo simulador.

### Opción C: Servidor Node.js Opcional (Arquitectura Centralizada)
Si deseas ejecutar el servidor centralizado de telemetría descrito en el informe:
```bash
cd server_nodejs
npm install
npm start
```
El servidor quedará a la escucha en el puerto `3000`.
