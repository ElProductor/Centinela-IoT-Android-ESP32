# Guía de Conexión a Supabase Cloud y Gestión Dinámica de Roles (RBAC) ☁️🛡️

Esta guía detalla la arquitectura de integración en la nube con **Supabase (PostgreSQL BaaS)** y el módulo de **Gestión Dinámica de Roles y Operadores** implementado en la aplicación móvil **Centinela-IoT**.

---

## 🌟 1. Arquitectura Híbrida Offline-First (Edge + Cloud)

La solución combina lo mejor de ambos mundos para entornos industriales OT:
1. **Operación Local Ininterrumpida (Edge / SQLite):**  
   Si la planta pierde la conectividad a Internet o se trabaja en contingencia con el punto de acceso del ESP32 (`192.168.4.1`), la aplicación sigue autenticando usuarios, validando roles RBAC y registrando la auditoría forense (*Audit Trail*) localmente en SQLite con SHA-256 + Salt.
2. **Sincronización en la Nube (Supabase BaaS):**  
   Al restablecerse el enlace WAN, la aplicación sincroniza bidireccionalmente los roles creados por el administrador, las cuentas de operadores autorizados y los registros de auditoría histórica en las tablas PostgreSQL de Supabase.

---

## 📜 2. Script SQL DDL para Supabase (Editor SQL)

Para inicializar tu proyecto en Supabase:
1. Ingresa a tu panel en [supabase.com](https://supabase.com) y crea un nuevo proyecto (o abre uno existente).
2. Ve al menú **SQL Editor** en la barra lateral izquierda.
3. Copia y pega el siguiente script completo y haz clic en **Run**:

```sql
-- =========================================================
-- ESQUEMA POSTGRESQL PARA CENTINELA-IOT EN SUPABASE
-- =========================================================

-- 1. Tabla de Roles Dinámicos (RBAC)
CREATE TABLE IF NOT EXISTS public.roles (
    name TEXT PRIMARY KEY,
    description TEXT DEFAULT '',
    can_disarm BOOLEAN DEFAULT false,
    can_remote_open BOOLEAN DEFAULT false,
    can_universal_gpio BOOLEAN DEFAULT false,
    can_manage_users BOOLEAN DEFAULT false,
    can_view_audit BOOLEAN DEFAULT true,
    is_system BOOLEAN DEFAULT false,
    created_at BIGINT NOT NULL
);

-- 2. Tabla de Usuarios con Hashes Criptográficos (ISO 27400)
CREATE TABLE IF NOT EXISTS public.app_users (
    id BIGSERIAL PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    role TEXT NOT NULL REFERENCES public.roles(name) ON UPDATE CASCADE,
    full_name TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

-- 3. Tabla de Auditoría Inmutable (Audit Trail)
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    username TEXT NOT NULL,
    details TEXT NOT NULL,
    severity TEXT NOT NULL
);

-- 4. Tabla de Telemetría Histórica ESP32
CREATE TABLE IF NOT EXISTS public.telemetry (
    id BIGSERIAL PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    distancia REAL NOT NULL,
    luz INTEGER NOT NULL,
    alarma_activa BOOLEAN NOT NULL,
    pestillo_abierto BOOLEAN NOT NULL,
    modo TEXT NOT NULL
);

-- Roles de Fábrica Iniciales
INSERT INTO public.roles (name, description, can_disarm, can_remote_open, can_universal_gpio, can_manage_users, can_view_audit, is_system, created_at)
VALUES 
    ('ADMIN', 'Administrador Total OT: gestión de usuarios, roles y anulación', true, true, true, true, true, true, 1729000000000),
    ('OPERATOR', 'Operador de Planta Centinela: desarme y apertura remota', true, true, true, false, true, true, 1729000000000),
    ('MONITOR', 'Auditor de Seguridad Solo Lectura: inspección de logs', false, false, false, false, true, true, 1729000000000)
ON CONFLICT (name) DO NOTHING;

-- Habilitar Políticas de Seguridad a Nivel de Fila (RLS)
ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.telemetry ENABLE ROW LEVEL SECURITY;

-- Políticas de acceso para API Key anónima (Lectura y Escritura)
CREATE POLICY "Permitir acceso anonimo a roles" ON public.roles FOR ALL USING (true);
CREATE POLICY "Permitir acceso anonimo a usuarios" ON public.app_users FOR ALL USING (true);
CREATE POLICY "Permitir acceso anonimo a audit_logs" ON public.audit_logs FOR ALL USING (true);
CREATE POLICY "Permitir acceso anonimo a telemetria" ON public.telemetry FOR ALL USING (true);
```

---

## ⚙️ 3. Configuración en la Aplicación Android

1. En tu panel de Supabase, ve a **Project Settings -> API**.
2. Copia:
   - **Project URL:** `https://<tu-id-de-proyecto>.supabase.co`
   - **anon / public key:** `eyJhbGciOi...`
3. Abre la aplicación **Centinela-IoT** en tu teléfono o emulador.
4. Inicia sesión con la cuenta **`admin`** (`admin123`).
5. Ve a la pestaña **Ajustes** (icono de engranaje).
6. En la sección **☁️ Nube Supabase (PostgreSQL BaaS)**:
   - Activa el interruptor **Sincronización Cloud Supabase**.
   - Pega tu **URL** y tu **Anon Key**.
   - Presiona **PROBAR CONEXIÓN** para validar el enlace (verás el mensaje en verde con los roles detectados).
   - Presiona **SINCRONIZAR** para respaldar de inmediato todos los datos en la nube.

---

## 👑 4. Panel de Administración: Creación de Roles y Operadores

Cuando inicias sesión con una cuenta de rol **ADMIN** (o un rol que tenga activado el permiso `can_manage_users`), verás la tarjeta destacada:

> **👑 Gestión de Roles y Operadores (RBAC)**  
> Botón: **GESTIONAR ROLES Y USUARIOS**

Al presionar este botón se despliega el panel administrativo con dos pestañas:

### A. Pestaña "🛡️ Roles (RBAC)"
- Muestra las tarjetas de todos los roles existentes con sus permisos visuales activos.
- **Crear Nuevo Rol (+ Nuevo Rol):**
  - **Identificador:** Nombre en mayúsculas (ej: `SUPERVISOR_PLANTA`, `TECNICO_EXTERNO`, `JEFE_TURNO`).
  - **Descripción:** Finalidad del rol.
  - **Permisos granulares (Switches):**
    - 🔓 *Desarme Perimetral:* Permite armar y silenciar la alarma acústica.
    - 🚪 *Apertura Remota:* Permite accionar el servomotor SG90 a 90°.
    - ⚡ *Control GPIO Universal:* Permite comandar relés, deslizadores PWM y pines del Universal Hub.
    - 👥 *Administrar Usuarios y Roles:* Otorga facultades de administrador.
    - 📋 *Ver Auditoría Forense:* Permite consultar el registro Audit Trail.
  - Al presionar **Guardar Rol**, se almacena localmente en SQLite y se sube automáticamente a Supabase si la nube está conectada.

### B. Pestaña "👥 Operadores"
- Lista todos los operadores dados de alta, indicando su nombre, usuario `@handle` y badge del rol asignado.
- **Crear Nuevo Operador (+ Nuevo Usuario):**
  - **Nombre de Usuario:** Para inicio de sesión.
  - **Nombre Completo:** Para trazabilidad e informes de auditoría.
  - **Contraseña:** Se deriva y cifra criptográficamente mediante **SHA-256 + Salt único de 16 bytes**.
  - **Rol Asignado:** Menú desplegable que **carga automáticamente todos los roles** (incluyendo los nuevos roles recién creados por el admin).
  - Al guardarlo, se crea la cuenta y queda habilitada de inmediato para iniciar sesión tanto en modo local como sincronizada en Supabase.
