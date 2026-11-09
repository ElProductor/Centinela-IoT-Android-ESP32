<?php
// ==============================================================================
// PROYECTO: Centinela-IoT v3.0 - API Local MySQL (TI3042 - INACAP)
// ARCHIVO: api/guardar_datos.php — Endurecido bajo ISO/IEC 27400 (Anti SQL Injection)
// ESTÁNDAR: Validación de cabeceras API-Key y Sentencias Preparadas (Prepared Statements)
// ==============================================================================

header("Content-Type: application/json; charset=UTF-8");

// Validación de autenticación por cabecera HTTP personalizada
$apiKeyRecibida = $_SERVER['HTTP_X_API_KEY'] ?? '';
if ($apiKeyRecibida !== 'CentinelaOT_LocalKey_2026') {
    http_response_code(401);
    echo json_encode([
        "error" => "No autorizado bajo politica ISO/IEC 27400",
        "code" => 401
    ]);
    exit;
}

// Conexión segura a la base de datos local MySQL
$conn = new mysqli("localhost", "centinela_app_user", "Inacap2026_Secure!", "sensoresiot");
if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode([
        "error" => "Fallo de conexion con servidor MySQL local",
        "code" => 500
    ]);
    exit;
}

// Sanitización y parseo estricto de tipos de datos recibidos por POST
$temp    = isset($_POST["temperatura"]) ? floatval($_POST["temperatura"]) : 0.0;
$hum     = isset($_POST["humedad"]) ? floatval($_POST["humedad"]) : 0.0;
$dist    = isset($_POST["distancia_cm"]) ? intval($_POST["distancia_cm"]) : 0;
$luz     = isset($_POST["luz_adc"]) ? intval($_POST["luz_adc"]) : 0;
$alarma  = isset($_POST["alarma_activa"]) ? intval($_POST["alarma_activa"]) : 0;

// Sentencia preparada para erradicar cualquier vector de SQL Injection (SQLi)
$stmt = $conn->prepare("INSERT INTO sensores (fechatoma, temperatura, humedad, distancia_cm, luz_adc, alarma_activa) VALUES (NOW(), ?, ?, ?, ?, ?)");
$stmt->bind_param("ddiii", $temp, $hum, $dist, $luz, $alarma);

if ($stmt->execute()) {
    http_response_code(201);
    echo json_encode([
        "status" => "OK_MYSQL_LOCAL",
        "timestamp" => date("Y-m-d H:i:s"),
        "rows_inserted" => $stmt->affected_rows
    ]);
} else {
    http_response_code(500);
    echo json_encode([
        "error" => "Error al ejecutar insercion en base de datos",
        "code" => 500
    ]);
}

$stmt->close();
$conn->close();
?>
