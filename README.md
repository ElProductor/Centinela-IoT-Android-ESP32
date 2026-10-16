# Centinela-IoT: Nodo de Seguridad Edge & ESP32 Universal Hub 🛡️⚡

[![INACAP](https://img.shields.io/badge/INACAP-Puerto%20Montt-red.svg)](https://www.inacap.cl)
[![Asignatura](https://img.shields.io/badge/TI3042-Aplicaciones%20M%C3%B3viles%20para%20IoT-blue.svg)]()
[![Plataforma](https://img.shields.io/badge/Hardware-ESP32--WROOM--32-00E5FF.svg)]()
[![Seguridad](https://img.shields.io/badge/Norma-ISO%2FIEC%2027400%3A2022-success.svg)]()
[![Android](https://img.shields.io/badge/Android%20Studio-Kotlin%20%7C%20Material%203-brightgreen.svg)]()

> **Evaluación Sumativa Unidad 2 (25%)**  
> **Carrera:** Ingeniería / Técnico en Informática  
> **Integrantes:** Juan Pablo Villavicencio, Daryl Izquierdo, Antonio Benedetti  
> **Docente:** Rodrigo Arturo Ruiz Hess  
> **Fecha:** Octubre de 2026  

---

## 📱 1. Descarga del APK Generado

El instalador compilado para Android se encuentra disponible en la raíz del repositorio:
* **Archivo APK:** [`CentinelaIoT_Universal_ESP32.apk`](CentinelaIoT_Universal_ESP32.apk)
* **Ruta de compilación:** `app/build/outputs/apk/debug/app-debug.apk`
* **Compatibilidad:** Android 8.0 (Oreo) en adelante (API 26 hasta API 35)

---

## 🎯 2. Visión General del Proyecto y Cumplimiento de Criterios

Esta solución móvil integra dos componentes esenciales:
1. **Módulo Centinela-IoT (Específico):** Supervisión y control perimetral en tiempo real del nodo de seguridad basado en ESP32 con procesamiento local (*Edge Computing*), sensor ultrasónico HC-SR04, fotorresistor LDR, pestillo servomotor SG90 y zumbador pasivo de 2 kHz.
2. **ESP32 Universal Hub (Genérico):** Panel de control y telemetría universal para interactuar con **cualquier proyecto de ESP32**, permitiendo comandar pines GPIO digitales (relés, luces), sliders PWM (motores, servomotores), leer sensores analógicos y enviar/recibir comandos en bruto por consola serial.

### Alineación con los Criterios de Evaluación (Unidad 2):
* **2.1.1 (Herramientas y Diseño Android):** Desarrollada con **Android Studio y Kotlin**, siguiendo los lineamientos de diseño **Material Design 3 (M3)**, ViewBinding, ViewModel desacoplado y componentes industriales adaptativos.
* **2.1.2 (Conexiones Inalámbricas):** Soporta conexión Wi-Fi mediante **API REST HTTP**, WebSockets y peticiones JSON asíncronas de telemetría y telemando hacia el microcontrolador ESP32 o servidor intermediario.
* **2.1.3 (Estándar ISO/IEC 27400:2022 de Seguridad IoT/OT):**
  - Autenticación segura obligatoria previa a cualquier operación.
  - Hashing criptográfico de contraseñas con **SHA-256 + Salt único** por usuario.
  - Control de Acceso Basado en Roles (**ADMIN**, **OPERADOR**, **MONITOR**).
  - Trazabilidad y **Registro Inmutable de Auditoría (*Audit Trail*)** en base de datos local SQLite que audita cada intrusión perimetral, apertura remota de emergencia, cambio de parámetros o inicio de sesión.
* **2.1.4 (Interconexión Multi-dispositivo):** Permite ser desplegada en dos o más dispositivos móviles Android sincronizados, compartiendo la telemetría en tiempo real del nodo.

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
