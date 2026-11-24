package com.jagoo.driver.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneRulesTest {
    @Test fun `numeros de relleno no se marcan`() {
        assertNull(PhoneRules.dialable("+593 99 999 8888"))
        assertNull(PhoneRules.dialable("+593 99 111 2222"))
        assertNull(PhoneRules.dialable("0999998888"))
    }

    @Test fun `vacio o nulo no se marca`() {
        assertNull(PhoneRules.dialable(null))
        assertNull(PhoneRules.dialable(""))
        assertNull(PhoneRules.dialable("   "))
    }

    @Test fun `muy corto o muy largo no se marca`() {
        assertNull(PhoneRules.dialable("12345"))
        assertNull(PhoneRules.dialable("1234567890123456"))
    }

    @Test fun `todos los digitos iguales no se marca`() {
        assertNull(PhoneRules.dialable("0000000000"))
    }

    @Test fun `numero real se limpia`() {
        assertEquals("0991234567", PhoneRules.dialable("099 123-4567"))
        assertEquals("+593991234567", PhoneRules.dialable("+593 99 123 4567"))
    }

    @Test fun `texto con letras sin digitos suficientes no se marca`() {
        assertNull(PhoneRules.dialable("TELEFONO"))
    }
}
