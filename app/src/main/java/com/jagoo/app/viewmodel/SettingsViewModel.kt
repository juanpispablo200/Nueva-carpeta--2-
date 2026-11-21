package com.jagoo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagoo.app.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SettingRow(
    val key:   String,
    val value: String
)

data class BankInfo(
    val bankName:      String = "Banco Pichincha",
    val accountType:   String = "Ahorros",
    val accountNumber: String = "2100123456",
    val holder:        String = "Jagoo Delivery S.A.",
    val ruc:           String = "1792345678001"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _bankInfo = MutableStateFlow(BankInfo())
    val bankInfo: StateFlow<BankInfo> = _bankInfo.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { loadSettings() }

    fun loadSettings() {
        viewModelScope.launch {
            try {
                val rows = SupabaseClient.client
                    .from("settings")
                    .select { filter { isIn("key", listOf(
                        "bank_name", "bank_account_type",
                        "bank_account_number", "bank_account_holder", "bank_ruc"
                    )) } }
                    .decodeList<SettingRow>()

                val map = rows.associate { it.key to it.value }
                _bankInfo.value = BankInfo(
                    bankName      = map["bank_name"]           ?: "Banco Pichincha",
                    accountType   = map["bank_account_type"]   ?: "Ahorros",
                    accountNumber = map["bank_account_number"] ?: "2100123456",
                    holder        = map["bank_account_holder"] ?: "Jagoo Delivery S.A.",
                    ruc           = map["bank_ruc"]            ?: "1792345678001"
                )
            } catch (_: Exception) {
                // Mantiene los valores por defecto si Supabase no responde
            } finally {
                _loading.value = false
            }
        }
    }
}
