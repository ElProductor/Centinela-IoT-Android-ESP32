# Manual de Usuario y Guía de Operación
## Aplicación Móvil: Centinela-IoT & ESP32 Universal Hub
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — Unidad 2  
**Institución:** Instituto Profesional INACAP — Sede Puerto Montt  
**Plataforma:** Android 8.0+ (API 26 a 35) | Desarrollada en Kotlin con Material Design 3  
**Estándar de Seguridad:** ISO/IEC 27400:2022 (Seguridad y Privacidad IoT/OT)  

---

## 1. Instalación y Puesta en Marcha

### Requisitos Previos
* Dispositivo móvil o emulador Android con versión 8.0 o superior.
* Archivo instalador: `CentinelaIoT_Universal_ESP32.apk` (7.5 MB).

### Pasos de Instalación en el Dispositivo Móvil
1. Transfiere el archivo `CentinelaIoT_Universal_ESP32.apk` a tu teléfono (vía USB, Google Drive, WhatsApp o enlace directo).
2. Toca el archivo descargado para iniciar el instalador de paquetes de Android.
3. Si el sistema te lo solicita, activa la casilla **"Permitir la instalación desde esta fuente"**.
4. Presiona **Instalar** y luego **Abrir**.

---

## 2. Acceso y Autenticación de Seguridad (ISO/IEC 27400)

La aplicación cuenta con control de acceso estricto basado en roles (RBAC) y resguardo criptográfico de credenciales con hash **SHA-256 + Salt** en base de datos local SQLite.

```
       +-------------------------------------------------------+
       |                  PANTALLA DE ACCESO                   |
       |                                                       |
       |    [🛡️] CENTINELA - IoT (Nodo de Supervisión OT)      |
       |                                                       |
       |    Usuario:    [ admin                              ] |
       |    Contraseña: [ ••••••••                         👁️] |
       |                                                       |
       |    [          INICIAR SESIÓN (OT ACCESS)            ] |
       |                                                       |
       |    Atajos Demo: [ Admin ]  [ Operador ]  [ Auditor ]  |
       +-------------------------------------------------------+
```

### Cuentas Preconfiguradas para Evaluación:
Para agilizar la evaluación frente al docente, se han incluido **3 botones de acceso rápido** en la parte inferior:

| Botón Rápido | Usuario | Contraseña | Rol Asignado | Nivel de Privilegios OT |
| :---: | :--- | :--- | :---: | :--- |
| **`Admin`** | `admin` | `admin123` | **ADMIN** | **Control Total:** Permite aperturas remotas de emergencia, modificación de parámetros de red/umbrales, purga de auditoría y gestión de widgets. |
| **`Operador`** | `operador` | `inacap2026` | **OPERATOR** | **Nivel Operativo:** Permite armar/desarmar el perímetro, silenciar alarmas acústicas y ejecutar aperturas bajo justificación. |
| **`Auditor`** | `auditor` | `auditor123` | **MONITOR** | **Solo Lectura:** Visualización de telemetría e inspección forense de auditoría; no tiene permisos de accionamiento físico. |

---

## 3. Pestaña 1: Panel Especializado "Centinela-IoT"

Esta es la pantalla principal del proyecto donde se supervisa el nodo físico en tiempo real.

### Componentes de la Interfaz:
1. **Banner Superior de Estado Perimetral:**
   * **Color Verde:** `PERÍMETRO SEGURO` — No hay objetos dentro del rango de intrusión.
   * **Color Rojo Pulsante:** `¡ALERTA DE INTRUSIÓN EN CURSO!` — El sensor HC-SR04 detectó un cuerpo a menos de 50 cm.
2. **Medidor de Distancia (HC-SR04):**
   * Muestra el valor medido en centímetros con una barra de proximidad dinámica.
   * Cambia a color rojo de advertencia cuando la lectura cae bajo el umbral crítico (< 50 cm).
3. **Medidor de Iluminación (LDR GL5528):**
   * Muestra la lectura analógica del conversor ADC1 del ESP32 (0 a 4095).
   * Indica si se cumple la condición de oscuridad requerida (< 800 ADC) para el desarme seguro.
4. **Estado del Pestillo Físico (Servomotor SG90):**
   * Candado Cerrado (Rojo) = `BLOQUEADO (0°)`
   * Candado Abierto (Verde) = `DESBLOQUEADO (90°)`
5. **Estado de la Sirena (Zumbador Pasivo):**
   * `ALARMA ACTIVA (2000 Hz)` o `SILENCIADO (0 Hz)`.
6. **Panel de Desarme Tripartito (Fusión Sensorial):**
   * Muestra en tiempo real si se cumplen las 3 condiciones exigidas simultáneamente por el algoritmo Edge del ESP32:
     - `✔ Distancia despejada (> 50 cm)`
     - `✔ Nivel de oscuridad (< 800 ADC)`
     - `✔ Pulsador físico accionado (GPIO 4)`
7. **Botonera de Control Remoto:**
   * **APERTURA REMOTA (OVERRIDE):** Botón rojo destacado. Al presionarlo, solicita una confirmación formal de seguridad OT y envía la orden al ESP32 para abrir el pestillo a 90° de inmediato. Queda registrado en el historial de auditoría.
   * **Armar / Desarmar Perímetro:** Alterna el estado de vigilancia del sistema.
   * **Silenciar Alarma:** Apaga el zumbador acústico manteniendo el bloqueo físico si la intrusión continúa.

---

## 4. Modo Simulación Embebido (Demostración sin Cables)

Si estás en la sala de clases y no tienes la maqueta física energizada o el router de la sede tiene bloqueo de red local, utiliza el **Simulador Virtual Interno**:

1. En la parte inferior del panel Centinela verás la tarjeta **"Controles de Simulación (Demostración Evaluativa)"**.
2. **`Probar Intrusión (<50cm)`:** Simula un objeto a 28 cm. Observa cómo al instante:
   * El banner cambia a **Rojo**.
   * La distancia marca 28 cm.
   * El servomotor pasa a **Bloqueado (0°)**.
   * La sirena pasa a **Sonando**.
   * Se genera un registro de auditoría crítico en la base de datos SQLite.
3. **`Restaurar Zona Segura`:** Devuelve la distancia a 135 cm (despejado).
4. **`Simular Oscuridad`:** Baja el valor LDR virtual a 350 ADC para cumplir la segunda condición de desarme.
5. **`Pulsar Botón GPIO 4`:** Simula la pulsación del botón físico, completando el desarme tripartito y liberando el pestillo automáticamente.

---

## 5. Pestaña 2: "ESP32 Universal Hub" (Para Cualquier Proyecto ESP32)

Esta sección permite utilizar la aplicación como una central de mando genérica para cualquier placa ESP32:

### Creación y Uso de Widgets Dinámicos
1. Presiona el botón superior **`+ Añadir Widget`**.
2. Asigna un nombre (ej. *Relé Ventilador*, *Luz Taller*, *Servo Cámara*).
3. Ingresa el número de pin GPIO correspondiente de tu ESP32 (ej. 2, 12, 13, 25, etc.).
4. Selecciona el tipo de widget:
   * **Interruptor Digital (SWITCH):** Envía órdenes `1` (HIGH) y `0` (LOW) para encender o apagar relés y actuadores.
   * **Control Deslizante (SLIDER):** Barra de 0° a 180° para servomotores o PWM de velocidad.
   * **Pulsador de Disparo (BUTTON):** Envía un pulso momentáneo al pin seleccionado.
5. El widget aparecerá inmediatamente en la pantalla y enviará comandos al ESP32 cada vez que lo acciones.

### Consola Serial y Terminal Raw
* Permite enviar comandos manuales de texto o tramas JSON hacia el microcontrolador (ej. `{"comando":"status"}`).
* La pantalla oscura tipo terminal muestra los datos transmitidos (`TX`) y recibidos (`RX`) en tiempo real con fecha y hora (`[HH:mm:ss]`).

---

## 6. Pestaña 3: Registro Inmutable de Auditoría (Audit Trail)

Cumpliendo con la norma **ISO/IEC 27400**, cada evento relevante queda grabado de forma inmutable en la base de datos SQLite local:

* **Eventos que se registran automáticamente:**
  - Inicios de sesión exitosos o fallidos con ID de usuario y rol.
  - Detecciones de intrusión perimetral con distancia exacta en centímetros.
  - Desarmes físicos tripartitos en el borde.
  - Aperturas remotas forzadas de emergencia.
  - Modificaciones en la configuración de red y umbrales.
* **Badges de Severidad:**
  - `CRITICAL` (Rojo): Intrusiones perimetrales no autorizadas.
  - `WARNING` (Amarillo): Intentos fallidos de login o aperturas forzadas.
  - `INFO` (Verde/Cyan): Desarmes autorizados, logins y cambios de parámetros.
* **Exportar Resumen:** Presiona el botón **Exportar Resumen** para compartir el informe de eventos por correo, WhatsApp o bloc de notas.
* **Purgar Logs:** Función restringida exclusivamente a usuarios con rol `ADMIN`.

---

## 7. Pestaña 4: Configuración de Red y Hardware Real

Permite alternar entre el simulador y la conexión directa con tu hardware físico:

1. **Switch "Modo Simulación (ESP32 Virtual)":**
   * Activado = Pruebas autónomas sin hardware.
   * Desactivado = Conexión real hacia la IP del ESP32 o servidor Node.js.
2. **Dirección IP del ESP32 / Servidor:**
   * Ingresa la IP de tu placa en la red Wi-Fi (ejemplo: `192.168.1.100` o la IP de tu router).
   * Si configuraste el ESP32 en modo Access Point independiente, ingresa `192.168.4.1`.
3. **Puerto de Comunicación:**
   * Puerto `80` si te conectas directo al servidor web HTTP del ESP32.
   * Puerto `3000` si utilizas el servidor intermediario Node.js.
4. **Umbrales de Detección:**
   * Permite recalibrar en caliente el umbral perimetral (default: 50 cm) y el de luz (default: 800 ADC).
5. Presiona **GUARDAR CONFIGURACIÓN** para aplicar los cambios de inmediato.

---

## 8. Guía Rápida para la Demostración en Clase (En 3 Minutos)

Sigue estos pasos ante el docente Rodrigo Arturo Ruiz Hess para evidenciar todos los criterios de la pauta:

```text
PASO 1 (30 seg) -> Abrir la app, pulsar el botón rápido "Admin" y luego "INICIAR SESIÓN".
                   Explicar: "La app valida credenciales mediante hash criptográfico SHA-256
                   con Salt en SQLite local cumpliendo ISO/IEC 27400."

PASO 2 (60 seg) -> En el panel Centinela, mostrar la telemetría en tiempo real.
                   Presionar "Probar Intrusión (<50cm)" en el simulador (o acercar la mano al sensor).
                   Mostrar cómo el banner se pone rojo, la distancia cae a 28 cm, el servo
                   se bloquea a 0° y suena la alarma acústica.

PASO 3 (45 seg) -> Presionar el botón rojo "APERTURA REMOTA (OVERRIDE)".
                   Confirmar en el diálogo de seguridad OT.
                   Mostrar cómo el servomotor se abre inmediatamente a 90° y se silencia la sirena.

PASO 4 (30 seg) -> Ir a la pestaña "Auditoría".
                   Mostrar los registros generados con marca de tiempo exacta, ID de usuario y nivel CRITICAL.

PASO 5 (15 seg) -> Ir a la pestaña "ESP32 Hub".
                   Mover un slider PWM o presionar un interruptor GPIO y mostrar la terminal
                   con tramas TX/RX.
                   Concluir: "La app no solo gestiona Centinela, sino que es un Hub universal
                   para cualquier prototipo ESP32."
```
