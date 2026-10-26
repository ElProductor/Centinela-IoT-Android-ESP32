# Diagrama de Conexiones Físicas: Placa ESP32 (Serigrafía D1, D2, D4...)
## Proyecto: Centinela-IoT: Nodo de Seguridad Edge Multiparamétrico

En la mayoría de las placas **ESP32 DevKit (30 o 38 pines)**, los pines no vienen marcados con la palabra "GPIO", sino con la letra **"D"** seguida del número (ej. **D4, D5, D13, D14, D18, D34**). 

> **Regla de oro:** En el ESP32, **D = GPIO**.  
> Por ejemplo: **D4 = GPIO 4**, **D13 = GPIO 13**, **D18 = GPIO 18**.

---

### 1. Mapa de Pines de tu Placa ESP32 (30 Pines Típica)

```text
                  +-------------------------+
                  |         ESP32           |
                  |     [Antena Wi-Fi]      |
                  |                         |
            [EN]  |                         |  [D23] 
   (GPIO 36) [VP]  |                         |  [D22] 
   (GPIO 39) [VN]  |                         |  [TX0] (GPIO 1)
   (GPIO 34) [D34] |                         |  [RX0] (GPIO 3)
   (GPIO 35) [D35] |                         |  [D21] 
   (GPIO 32) [D32] |                         |  [D19] 
   (GPIO 33) [D33] |                         |  [D18] (GPIO 18) <--- Echo (HC-SR04 vía divisor)
   (GPIO 25) [D25] |                         |  [D5]  (GPIO 5)  ---> Trig (HC-SR04)
   (GPIO 26) [D26] |                         |  [TX2] (GPIO 17)
   (GPIO 27) [D27] |                         |  [RX2] (GPIO 16)
   (GPIO 14) [D14] |                         |  [D4]  (GPIO 4)  <--- Pulsador de desarme
   (GPIO 12) [D12] |                         |  [D2]  
   (GPIO 13) [D13] |                         |  [D15] 
            [GND] |                         |  [GND] (Tierra)
   (Alim 5V) [VIN] |                         |  [3V3] (Salida 3.3V para LDR)
                  |       [USB / Puerto]    |
                  +-------------------------+
```

---

### 2. Tabla Directa: Qué Pin Conectar a Qué Componente

| Componente Físico | Pin del Componente | Conectar exactamente a tu placa ESP32 | Explicación |
| :--- | :--- | :--- | :--- |
| **Sensor Distancia (HC-SR04)** | **VCC** | Pin **VIN** *(o 5V)* | Alimentación de 5V del USB. |
| | **GND** | Pin **GND** | Tierra. |
| | **Trig** | Pin **D5** | Disparo del pulso ultrasónico. |
| | **Echo** | Pin **D18** *(con divisor 1k/2k)* | Entrada de retorno (protegida a 3.3V). |
| **Sensor de Luz (LDR GL5528)** | Pata 1 | Pin **3V3** | Alimentación limpia de 3.3V. |
| | Pata 2 | Pin **D34** *(con resistencia 10k a GND)* | Lectura analógica de luminosidad. |
| **Micro Servo SG90 (Pestillo)**| Cable Rojo | Pin **VIN** *(o 5V)* | Alimentación de fuerza a 5V. |
| | Cable Marrón/Negro | Pin **GND** | Tierra común. |
| | Cable Naranja/Amarillo | Pin **D13** | Señal de posición (0° / 90°). |
| **Zumbador Pasivo (Buzzer)** | Pata Larga / Positivo (+)| Pin **D14** | Señal PWM de la sirena (2 kHz). |
| | Pata Corta / Negativo (-)| Pin **GND** | Tierra común. |
| **Pulsador (Botón)** | Pata 1 | Pin **D4** | Entrada digital de confirmación. |
| | Pata 2 | Pin **GND** | Conexión a tierra al pulsar. |

---

### 3. Conexión de las Resistencias en la Protoboard

#### A. El Divisor del Pin Echo (Protege el pin D18):
El pin Echo entrega 5V y el pin D18 solo tolera 3.3V.
1. Del pin **Echo** del sensor saca un cable a una fila libre de la protoboard.
2. En esa misma fila, conecta una pata de la resistencia de **1 kΩ** (Marrón - Negro - Rojo).
3. La otra pata de la resistencia de **1 kΩ** ponla en otra fila.
4. En esa segunda fila conecta:
   * Un cable hacia el pin **D18** del ESP32.
   * Una pata de la resistencia de **2 kΩ** (Rojo - Negro - Rojo).
5. La otra pata de la resistencia de **2 kΩ** va a **GND**.

#### B. La Resistencia del Sensor de Luz (LDR en pin D34):
1. Una pata del LDR va a **3V3**.
2. La otra pata del LDR va al pin **D34** del ESP32.
3. Desde esa misma pata conectada a **D34**, coloca una resistencia de **10 kΩ** (Marrón - Negro - Naranja) que vaya a **GND**.
