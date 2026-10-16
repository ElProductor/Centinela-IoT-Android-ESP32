const express = require('express');
const cors = require('cors');

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json());

// Estado en memoria del nodo Centinela-IoT
let ultimoEstado = {
  dist: 120,
  luz: 1850,
  alarma: false,
  pestillo: 90,
  boton: false,
  timestamp: Date.now()
};

// Historial de auditoría
let logsAuditoria = [];

// Endpoint 1: Recibe telemetría desde el ESP32
app.post('/telemetria', (req, res) => {
  const { dist, luz, alarma, pestillo, boton } = req.body;
  ultimoEstado = {
    dist: dist !== undefined ? dist : ultimoEstado.dist,
    luz: luz !== undefined ? luz : ultimoEstado.luz,
    alarma: alarma !== undefined ? alarma : ultimoEstado.alarma,
    pestillo: pestillo !== undefined ? pestillo : ultimoEstado.pestillo,
    boton: boton !== undefined ? boton : ultimoEstado.boton,
    timestamp: Date.now()
  };

  // Si se detecta alarma, registrar evento de intrusión
  if (alarma && (!logsAuditoria.length || !logsAuditoria[logsAuditoria.length - 1].alarma)) {
    logsAuditoria.push({
      evento: 'INTRUSION_DETECTADA',
      dist,
      luz,
      fecha: new Date().toISOString()
    });
    console.log(`[ALERTA INTRUSIÓN] Distancia: ${dist} cm | Luz: ${luz} ADC`);
  }

  res.json({ status: 'ok', mensaje: 'Telemetría guardada' });
});

// Endpoint 2: Consulta de telemetría en tiempo real por la app móvil
app.get('/telemetria', (req, res) => {
  res.json(ultimoEstado);
});

// Endpoint 3: Recepción de comandos desde la app móvil hacia el ESP32
app.post('/comando', (req, res) => {
  const { comando, usuario, token } = req.body;
  console.log(`[COMANDO RECIBIDO] ${comando} de usuario: ${usuario || 'Anónimo'}`);

  if (comando === 'desbloquear') {
    ultimoEstado.pestillo = 90;
    ultimoEstado.alarma = false;
    logsAuditoria.push({
      evento: 'APERTURA_REMOTA_AUTORIZADA',
      usuario,
      fecha: new Date().toISOString()
    });
    res.json({ status: 'ok', mensaje: 'Pestillo liberado remotamente (90°)' });
  } else if (comando === 'bloquear') {
    ultimoEstado.pestillo = 0;
    res.json({ status: 'ok', mensaje: 'Pestillo bloqueado remotamente (0°)' });
  } else if (comando === 'silenciar') {
    ultimoEstado.alarma = false;
    res.json({ status: 'ok', mensaje: 'Alarma silenciada' });
  } else {
    res.json({ status: 'ok', mensaje: `Comando ${comando} procesado` });
  }
});

// Endpoint 4: Consulta de logs de auditoría
app.get('/auditoria', (req, res) => {
  res.json(logsAuditoria);
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Servidor Centinela-IoT activo en http://0.0.0.0:${PORT}`);
  console.log(`Rutas disponibles: GET/POST /telemetria, POST /comando, GET /auditoria`);
});
