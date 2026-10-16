# Diagrama Esquemático y Manual de Conexión: ESP32-WROOM-32
## Proyecto: Centinela-IoT: Nodo de Seguridad Edge Multiparamétrico

Este documento describe la conexión física exacta para el microcontrolador **ESP32-WROOM-32**, corrigiendo el montaje referencial erróneo de la Figura 1 que mostraba un Arduino UNO.

---

### 1. Diagrama de Conexión en Protoboard (ASCII)

```text
               +-------------------------------------------+
               |             ESP32-WROOM-32                |
               |                                           |
               | [3V3]  [EN]  [VP]  [VN]  [34]  [35]  [32] |
               | [33]   [25]  [26]  [27]  [14]  [12]  [GND]|
               |                                           |
               | [VIN]  [GND] [13]  [12]  [14]  [27]  [26] |
               | [GPIO 4]  [GPIO 5]  [GPIO 18]             |
               +-----+---------+---------+-----------------+
                     |         |         |
                     |         |         +---------> Divisor Resistor (1k) <--- [HC-SR04 Echo (5V)]
                     |         |                         |
                     |         |                      Resistor (2k)
                     |         |                         |
                     |         |                        [GND]
                     |         |
                     |         +-------------------> [HC-SR04 Trig]
                     |
                     +-----------------------------> [Pulsador Táctil (INPUT_PULLUP)] -> [GND]

  [LDR GL5528] --------> GPIO 34 (ADC1_CH6) 
                   |
                Resistor 10k
                   |
                 [GND]

  [SG90 Servo] --------> Señal a GPIO 13 (PWM)
                   |
                 Alimentación VCC a 5V (Línea VIN)
                 Tierra GND común

  [Buzzer Pasivo] -----> Señal a GPIO 14 (Canal LEDC PWM)
                   |
                 Tierra GND común
```

---

### 2. Tabla de Conexión Pin a Pin (ESP32)

| Módulo / Componente | Pin del Módulo | Conexión en ESP32 / Protoboard | Función y Parámetros |
| :--- | :--- | :--- | :--- |
| **HC-SR04** | VCC | **5V (Línea VIN)** | Alimentación del transductor acústico |
| **HC-SR04** | GND | **GND** | Referencia de masa |
| **HC-SR04** | Trig | **GPIO 5** | Pulso digital de 10 µs |
| **HC-SR04** | Echo | **GPIO 18** | **Divisor 1 kΩ / 2 kΩ**: reduce 5V a 3.3V |
| **LDR GL5528** | Terminal 1 | **3V3** | Alimentación 3.3V de referencia analógica |
| **LDR GL5528** | Terminal 2 | **GPIO 34** | Divisor con R=10 kΩ a GND (ADC 0-4095) |
| **Servo SG90** | VCC (Rojo) | **5V (Línea VIN)** | Alimentación independiente de alta corriente |
| **Servo SG90** | GND (Negro/Marrón) | **GND** | Masa compartida |
| **Servo SG90** | PWM (Naranja/Amarillo) | **GPIO 13** | Señal 50 Hz (0° = Bloqueo, 90° = Apertura) |
| **Zumbador Pasivo** | Positivo (+) | **GPIO 14** | Tono de alarma 2000 Hz por LEDC |
| **Zumbador Pasivo** | Negativo (-) | **GND** | Masa compartida |
| **Pulsador** | Terminal A | **GPIO 4** | Entrada con `INPUT_PULLUP` interno |
| **Pulsador** | Terminal B | **GND** | Al presionar conecta a LOW (Desarme) |

---

### 3. Consideraciones Eléctricas de Seguridad en el Armado

1. **Protección de Entrada GPIO 18 (Echo HC-SR04):**  
   El pin Echo del HC-SR04 emite un tren de pulsos a 5 V. Si se conecta directamente al ESP32 (cuyos pines toleran un máximo estricto de 3.6 V), el SoC sufrirá degradación por sobretensión. Se intercaló un divisor resistivo simple:
   $$V_{out} = 5\text{ V} \times \frac{2\text{ k}\Omega}{1\text{ k}\Omega + 2\text{ k}\Omega} = 3.33\text{ V}$$
   garantizando un nivel TTL seguro para el GPIO 18.

2. **Aislamiento de Potencia del Servomotor SG90:**  
   El motor SG90 genera picos de consumo de hasta 700 mA durante el arranque del par de torsión. El pin `3V3` del ESP32 está alimentado por un regulador LDO de apenas 500 mA totales. Si el servo se alimenta de `3V3`, el ESP32 se reiniciará continuamente por caída de tensión (*Brownout Reset*). Por ello, el servo se conecta directamente a la línea `5V / VIN` proveniente del puerto USB.

3. **Canal ADC1 para el LDR:**  
   Se seleccionó **GPIO 34**, el cual pertenece al convertidor analógico-digital **ADC1**. Esto es vital porque en el ESP32 el conversor ADC2 se encuentra inhabilitado cuando el módem Wi-Fi está transmitiendo datos. Usar GPIO 34 permite medir luz en tiempo real sin interferencias con la red.
