# Anexo Técnico y Fe de Erratas: Corrección de Plataforma de Hardware
## Proyecto Centinela-IoT: Nodo de Seguridad Edge Multiparamétrico
**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — Unidad 2  
**Institución:** INACAP Sede Puerto Montt  
**Integrantes:** Juan Pablo Villavicencio, Daryl Izquierdo, Antonio Benedetti  
**Docente:** Rodrigo Arturo Ruiz Hess  
**Fecha de Actualización:** Octubre 2026  

---

### 1. Declaración de Fe de Erratas (Figura 1 del Informe)
En la versión preliminar del informe técnico, la **Figura 1 ("Maqueta de conexión: Esquema general")** representó visualmente una placa **Arduino UNO**. 

Se deja constancia explícita de que **la plataforma de cómputo y procesamiento oficial, física y lógica del proyecto es el microcontrolador ESP32-WROOM-32**, tal como se estipula en las especificaciones de hardware (Sección 3.1), la tabla de conexiones oficial (Sección 6) y el código fuente compilado (Sección 7).

---

### 2. Justificación Técnica: ESP32 vs. Arduino UNO en Sistemas IoT/OT

La selección del **ESP32-WROOM-32** sobre Arduino UNO responde a criterios de arquitectura industrial y requerimientos de la asignatura:

| Parámetro Técnico | Arduino UNO (ATmega328P) | ESP32-WROOM-32 | Impacto en el Proyecto Centinela-IoT |
| :--- | :--- | :--- | :--- |
| **Procesador** | 8 bits AVR a 16 MHz (Mononúcleo) | **Xtensa 32-bit LX6 Doble Núcleo a 240 MHz** | Permite ejecutar la lógica crítica de seguridad en el Core 0 mientras atiende el stack Wi-Fi/HTTP en el Core 1 sin bloqueos (*True Multitasking*). |
| **Memoria RAM** | 2 KB SRAM | **520 KB SRAM** | El stack TCP/IP y el búfer de tramas JSON encriptadas requieren memoria superior a 40 KB, imposible de sostener en un Arduino UNO básico. |
| **Conectividad Inalámbrica** | Nula (requiere módulos externos como ESP-01 o Shields con lentitud SPI/UART) | **Wi-Fi 802.11 b/g/n + Bluetooth 4.2 BLE nativos integrados en el silicio** | Comunicación directa con la aplicación móvil Android vía sockets HTTP/REST sin cuellos de botella seriales. |
| **Resolución ADC** | 10 bits (0 a 1023) | **12 bits (0 a 4095)** | Mayor sensibilidad y precisión en la lectura analógica del fotorresistor LDR (GL5528) para la condición de oscuridad. |
| **Generación PWM** | Temporizadores básicos por hardware | **LEDC (16 canales independientes configurables)** | Control de alta precisión para el servomotor SG90 (50 Hz) y modulación de tonos del zumbador pasivo (2000 Hz) sin retardar el loop. |
| **Edge Computing Autónomo** | Limitado a control secuencial simple | **Procesamiento de borde seguro (Edge Computing)** | La cerradura evalúa la fusión de sensores localmente; si la red Wi-Fi cae, la seguridad perimetral nunca se vulnera. |

---

### 3. Matriz de Conexiones Oficial al ESP32-WROOM-32

| Componente | Pin del Componente | Pin en ESP32 | Tipo de Señal | Consideración Eléctrica Crítica |
| :--- | :--- | :--- | :--- | :--- |
| **Sensor Ultrasónico HC-SR04** | VCC | 5V / VIN | Alimentación | Requiere 5V para el transductor ultrasónico. |
| **Sensor Ultrasónico HC-SR04** | GND | GND | Tierra | Línea de referencia común. |
| **Sensor Ultrasónico HC-SR04** | Trig | **GPIO 5** | Salida Digital | Pulso de disparo de 10 µs generado por ESP32 (3.3V es suficiente para el nivel TTL alto del sensor). |
| **Sensor Ultrasónico HC-SR04** | Echo | **GPIO 18** | Entrada Digital | **DIVISOR DE TENSIÓN OBLIGATORIO:** El pin Echo entrega pulsos de 5V. Las entradas del ESP32 solo toleran 3.3V. Se conecta una resistencia de **1 kΩ en serie** y **2 kΩ a GND** (entrega ~3.33V), protegiendo el silicio del ESP32 de sobretensión. |
| **Fotorresistor LDR GL5528** | Pin 1 | 3V3 | Alimentación | Alimentado desde la línea regulada de 3.3V. |
| **Fotorresistor LDR GL5528** | Pin 2 | **GPIO 34 (ADC1_CH6)** | Entrada Analógica | Conectado con resistencia de pull-down de 10 kΩ a GND formando un divisor de tensión. (GPIO 34 pertenece a ADC1, permitiendo lecturas continuas aun con Wi-Fi activo). |
| **Micro Servomotor SG90** | VCC (Rojo) | 5V / VIN | Alimentación | **ALIMENTACIÓN EXTERNA / 5V USB:** El pico de corriente de arranque del servo (~700 mA) excede la capacidad del regulador interno de 3.3V del ESP32. Debe conectarse a la línea de 5V. |
| **Micro Servomotor SG90** | GND (Marrón/Negro) | GND | Tierra | Conectado a la tierra común. |
| **Micro Servomotor SG90** | Señal (Naranja/Amarillo) | **GPIO 13** | Salida PWM | Señal de control a 50 Hz (0° = 1 ms, 90° = 1.5 ms). |
| **Zumbador Pasivo** | Positivo (+) | **GPIO 14** | Salida PWM | Controlado mediante el generador LEDC del ESP32 a 2000 Hz. |
| **Zumbador Pasivo** | Negativo (-) | GND | Tierra | Conectado a tierra común. |
| **Pulsador Táctil (Pushbutton)** | Terminal 1 | **GPIO 4** | Entrada Digital | Configurado con `pinMode(PIN_BOTON, INPUT_PULLUP)`. No requiere resistencia externa; se activa en LOW al pulsar. |
| **Pulsador Táctil (Pushbutton)** | Terminal 2 | GND | Tierra | Conectado a tierra común. |

---

### 4. Cumplimiento de Criterios de Evaluación y Norma ISO/IEC 27400:2022
1. **Criterio 2.1.1 (Estándar de Diseño Android):** Aplicación desarrollada en Kotlin nativo para Android Studio con Material Design 3, ViewBinding y arquitectura desacoplada.
2. **Criterio 2.1.2 (Conexión Inalámbrica Pertinente):** Conectividad directa Wi-Fi HTTP REST / WebSockets entre el teléfono y el ESP32, permitiendo supervisión en tiempo real.
3. **Criterio 2.1.3 (Norma ISO 27400 - Seguridad IoT):**
   - Autenticación obligatoria previa a cualquier acceso operativo.
   - Hashing criptográfico de contraseñas con SHA-256 + Salt único almacenado en base de datos local SQLite.
   - Control de acceso basado en roles (ADMIN, OPERADOR, MONITOR).
   - Trazabilidad inmutable mediante log de auditoría (*Audit Trail*) que registra cada intrusión, login y apertura forzada.
4. **Criterio 2.1.4 (Interconexión Multi-dispositivo):** La arquitectura de red permite desplegar el APK en dos o más dispositivos Android simultáneos, sincronizando el estado del nodo Centinela en tiempo real.
