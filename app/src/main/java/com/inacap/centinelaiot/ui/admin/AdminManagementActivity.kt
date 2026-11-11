package com.inacap.centinelaiot.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.data.database.model.Role
import com.inacap.centinelaiot.data.database.model.User
import com.inacap.centinelaiot.data.supabase.SupabaseClient
import com.inacap.centinelaiot.databinding.ActivityAdminManagementBinding
import com.inacap.centinelaiot.databinding.DialogCreateRoleBinding
import com.inacap.centinelaiot.databinding.DialogCreateUserBinding
import kotlinx.coroutines.launch

class AdminManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminManagementBinding
    private val dbHelper by lazy { CentinelaApp.instance.databaseHelper }
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }

    private lateinit var roleAdapter: RoleAdapter
    private lateinit var userAdapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificación estricta de seguridad OT / ISO 27400
        if (!sessionManager.canManageUsers(dbHelper)) {
            Toast.makeText(this, "Acceso Denegado: Se requiere rol de Administrador", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityAdminManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        setupRecyclerViews()
        setupFabActions()
        loadData()
    }

    private fun setupToolbar() {
        binding.toolbarAdmin.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupTabs() {
        binding.tabLayoutAdmin.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutTabRoles.visibility = View.VISIBLE
                        binding.layoutTabUsers.visibility = View.GONE
                    }
                    1 -> {
                        binding.layoutTabRoles.visibility = View.GONE
                        binding.layoutTabUsers.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerViews() {
        roleAdapter = RoleAdapter(emptyList()) { roleToDelete ->
            confirmDeleteRole(roleToDelete)
        }
        binding.rvRoles.layoutManager = LinearLayoutManager(this)
        binding.rvRoles.adapter = roleAdapter

        userAdapter = UserAdapter(emptyList()) { userToDelete ->
            confirmDeleteUser(userToDelete)
        }
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = userAdapter
    }

    private fun setupFabActions() {
        binding.fabAddRole.setOnClickListener {
            showCreateRoleDialog()
        }

        binding.fabAddUser.setOnClickListener {
            showCreateUserDialog()
        }
    }

    private fun loadData() {
        val roles = dbHelper.getAllRoles()
        roleAdapter.updateList(roles)

        val users = dbHelper.getAllUsers()
        userAdapter.updateList(users)
    }

    // ==========================================
    // DIÁLOGOS DE CREACIÓN DE ROLES
    // ==========================================

    private fun showCreateRoleDialog() {
        val dialogBinding = DialogCreateRoleBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnCancelRole.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveRole.setOnClickListener {
            val roleName = dialogBinding.etNewRoleName.text.toString().trim().uppercase()
            val roleDesc = dialogBinding.etNewRoleDesc.text.toString().trim()

            if (roleName.isBlank()) {
                Toast.makeText(this, "El nombre del rol no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (dbHelper.getRole(roleName) != null) {
                Toast.makeText(this, "El rol '$roleName' ya existe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newRole = Role(
                name = roleName,
                description = roleDesc,
                canDisarm = dialogBinding.switchPermDisarm.isChecked,
                canRemoteOpen = dialogBinding.switchPermRemoteOpen.isChecked,
                canUniversalGpio = dialogBinding.switchPermUniversalGpio.isChecked,
                canManageUsers = dialogBinding.switchPermManageUsers.isChecked,
                canViewAudit = dialogBinding.switchPermViewAudit.isChecked,
                isSystem = false,
                createdAt = System.currentTimeMillis()
            )

            val insertedId = dbHelper.insertRole(newRole)
            if (insertedId >= 0) {
                val ts = System.currentTimeMillis()
                val logDetails = "Rol '$roleName' creado con permisos personalizados"
                dbHelper.logEvent(
                    eventType = "ROLE_CREATED",
                    username = sessionManager.username,
                    details = logDetails,
                    severity = "INFO",
                    timestamp = ts
                )

                // Subir rol y log de auditoría a Supabase en segundo plano si está configurado
                lifecycleScope.launch {
                    val (pushed, pushMsg) = SupabaseClient.pushRole(newRole, sessionManager)
                    if (sessionManager.isSupabaseEnabled) {
                        SupabaseClient.pushAuditLog("ROLE_CREATED", sessionManager.username, logDetails, "INFO", ts, sessionManager)
                    }
                    val msg = if (pushed) "Rol '$roleName' guardado y sincronizado en Supabase" else "Rol '$roleName' guardado localmente ($pushMsg)"
                    Toast.makeText(this@AdminManagementActivity, msg, Toast.LENGTH_LONG).show()
                }

                loadData()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Error al guardar el rol en base de datos", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun confirmDeleteRole(role: Role) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar Rol")
            .setMessage("¿Deseas eliminar el rol '${role.name}'? Los usuarios que lo tengan asignado perderán sus privilegios.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                val deleted = dbHelper.deleteRole(role.name)
                if (deleted) {
                    val ts = System.currentTimeMillis()
                    val logDetails = "Rol '${role.name}' eliminado"
                    dbHelper.logEvent(
                        eventType = "ROLE_DELETED",
                        username = sessionManager.username,
                        details = logDetails,
                        severity = "WARNING",
                        timestamp = ts
                    )
                    lifecycleScope.launch {
                        if (sessionManager.isSupabaseEnabled) {
                            SupabaseClient.deleteRoleRemote(role.name, sessionManager)
                            SupabaseClient.pushAuditLog("ROLE_DELETED", sessionManager.username, logDetails, "WARNING", ts, sessionManager)
                        }
                    }
                    Toast.makeText(this, "Rol '${role.name}' eliminado", Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    Toast.makeText(this, "No se puede eliminar un rol del sistema", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // ==========================================
    // DIÁLOGOS DE CREACIÓN DE USUARIOS
    // ==========================================

    private fun showCreateUserDialog() {
        val dialogBinding = DialogCreateUserBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        // Cargar roles en el Spinner
        val roles = dbHelper.getAllRoles()
        val roleNames = roles.map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleNames)
        dialogBinding.spUserRole.adapter = spinnerAdapter

        // Seleccionar OPERATOR por defecto si existe
        val defaultIndex = roleNames.indexOf("OPERATOR").coerceAtLeast(0)
        dialogBinding.spUserRole.setSelection(defaultIndex)

        dialogBinding.btnCancelUser.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveUser.setOnClickListener {
            val username = dialogBinding.etNewUsername.text.toString().trim()
            val fullName = dialogBinding.etNewUserFullName.text.toString().trim()
            val password = dialogBinding.etNewUserPassword.text.toString().trim()
            val selectedRole = dialogBinding.spUserRole.selectedItem?.toString() ?: "OPERATOR"

            if (username.isBlank() || fullName.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val insertedId = dbHelper.insertUser(
                user = username,
                pass = password,
                role = selectedRole,
                fullName = fullName
            )

            if (insertedId > 0) {
                val ts = System.currentTimeMillis()
                val logDetails = "Nuevo usuario '@$username' registrado con rol '$selectedRole'"
                dbHelper.logEvent(
                    eventType = "USER_CREATED",
                    username = sessionManager.username,
                    details = logDetails,
                    severity = "INFO",
                    timestamp = ts
                )

                // Subir usuario y auditoría a Supabase en segundo plano si está conectado
                lifecycleScope.launch {
                    val userObj = dbHelper.getAllUsers().firstOrNull { it.username.equals(username, ignoreCase = true) }
                    if (userObj != null) {
                        val (pushed, pushMsg) = SupabaseClient.pushUser(userObj, sessionManager)
                        if (sessionManager.isSupabaseEnabled) {
                            SupabaseClient.pushAuditLog("USER_CREATED", sessionManager.username, logDetails, "INFO", ts, sessionManager)
                        }
                        val msg = if (pushed) "Usuario '@$username' guardado y sincronizado en Supabase" else "Usuario '@$username' guardado localmente ($pushMsg)"
                        Toast.makeText(this@AdminManagementActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }

                loadData()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Error al crear usuario (quizá el nombre ya está en uso)", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun confirmDeleteUser(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Dar de Baja Operador")
            .setMessage("¿Deseas revocar el acceso a '@${user.username}' (${user.fullName})?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar Acceso") { _, _ ->
                val deleted = dbHelper.deleteUser(user.username)
                if (deleted) {
                    val ts = System.currentTimeMillis()
                    val logDetails = "Usuario '@${user.username}' revocado por el Administrador"
                    dbHelper.logEvent(
                        eventType = "USER_REVOKED",
                        username = sessionManager.username,
                        details = logDetails,
                        severity = "WARNING",
                        timestamp = ts
                    )
                    lifecycleScope.launch {
                        if (sessionManager.isSupabaseEnabled) {
                            SupabaseClient.deleteUserRemote(user.username, sessionManager)
                            SupabaseClient.pushAuditLog("USER_REVOKED", sessionManager.username, logDetails, "WARNING", ts, sessionManager)
                        }
                    }
                    Toast.makeText(this, "Usuario '@${user.username}' revocado", Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    Toast.makeText(this, "No es posible eliminar al Administrador raíz", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
