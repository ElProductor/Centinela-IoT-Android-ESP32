package com.inacap.centinelaiot.ui.admin

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.data.database.model.User
import com.inacap.centinelaiot.databinding.ItemUserBinding

class UserAdapter(
    private var users: List<User>,
    private val onDeleteClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    fun updateList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(private val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            val context = binding.root.context
            binding.tvUserFullName.text = user.fullName
            binding.tvUsername.text = "@${user.username}"
            binding.tvAvatarInitial.text = user.fullName.take(1).uppercase()

            binding.chipUserRole.text = user.role.uppercase()
            val color = when (user.role.uppercase()) {
                "ADMIN" -> ContextCompat.getColor(context, R.color.primary)
                "OPERATOR" -> ContextCompat.getColor(context, R.color.secondary)
                else -> ContextCompat.getColor(context, R.color.status_info)
            }
            binding.chipUserRole.setTextColor(color)

            // No permitir borrar el admin raíz
            if (user.username.equals("admin", ignoreCase = true)) {
                binding.btnDeleteUser.visibility = View.GONE
            } else {
                binding.btnDeleteUser.visibility = View.VISIBLE
                binding.btnDeleteUser.setOnClickListener {
                    onDeleteClick(user)
                }
            }
        }
    }
}
