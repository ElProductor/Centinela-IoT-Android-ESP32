package com.inacap.centinelaiot.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inacap.centinelaiot.data.database.model.Role
import com.inacap.centinelaiot.databinding.ItemRoleBinding

class RoleAdapter(
    private var roles: List<Role>,
    private val onDeleteClick: (Role) -> Unit
) : RecyclerView.Adapter<RoleAdapter.RoleViewHolder>() {

    fun updateList(newList: List<Role>) {
        roles = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoleViewHolder {
        val binding = ItemRoleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RoleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoleViewHolder, position: Int) {
        holder.bind(roles[position])
    }

    override fun getItemCount(): Int = roles.size

    inner class RoleViewHolder(private val binding: ItemRoleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(role: Role) {
            binding.tvRoleName.text = role.name
            binding.tvRoleDescription.text = if (role.description.isNotBlank()) role.description else "Sin descripción definida."

            binding.tvSystemBadge.visibility = if (role.isSystem) View.VISIBLE else View.GONE
            binding.btnDeleteRole.visibility = if (role.isSystem) View.GONE else View.VISIBLE

            binding.btnDeleteRole.setOnClickListener {
                onDeleteClick(role)
            }

            // Visibilidad de chips de permisos
            binding.chipDisarm.visibility = if (role.canDisarm) View.VISIBLE else View.GONE
            binding.chipRemoteOpen.visibility = if (role.canRemoteOpen) View.VISIBLE else View.GONE
            binding.chipUniversalGpio.visibility = if (role.canUniversalGpio) View.VISIBLE else View.GONE
            binding.chipManageUsers.visibility = if (role.canManageUsers) View.VISIBLE else View.GONE
            binding.chipViewAudit.visibility = if (role.canViewAudit) View.VISIBLE else View.GONE
        }
    }
}
