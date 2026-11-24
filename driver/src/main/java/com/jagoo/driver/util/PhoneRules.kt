package com.jagoo.driver.util

/**
 * Un teléfono solo se puede marcar si parece real. Los números de relleno que
 * usaba la app antes ("+593 99 999 8888", "+593 99 111 2222") nunca se marcan.
 */
object PhoneRules {
    private val PLACEHOLDERS = setOf("593999998888", "593991112222", "0999998888", "0991112222")

    /** Devuelve el número listo para `tel:` (solo dígitos y `+` inicial) o null si no es marcable. */
    fun dialable(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length !in 7..15) return null
        if (digits in PLACEHOLDERS) return null
        if (digits.all { it == digits[0] }) return null          // 0000000, 1111111...
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }
}
