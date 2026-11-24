package com.jagoo.app.data

/**
 * Texto del estado de un pedido según su estado real, su método de pago, su
 * receta y su motivo de cancelación. Mantiene coherentes la app, las
 * notificaciones y el panel. Sin dependencias de Android (se prueba con JVM).
 */
object OrderStatusText {

    fun label(o: ActiveOrder): String = when {
        o.rawStatus == "cancelled" -> "Cancelado"
        o.rawStatus == "pending" && o.prescriptionRequired && o.prescriptionStatus == "pending" -> "Receta en revisión"
        o.rawStatus == "pending" && o.paymentMethod == "transfer" -> when (o.paymentStatus) {
            "verifying" -> "Pago en verificación"
            "rejected"  -> "Comprobante rechazado"
            else        -> "Falta el comprobante"
        }
        o.rawStatus == "pending"   -> "Esperando confirmación"
        o.rawStatus == "accepted"  -> "Confirmado"
        else -> when (o.statusStep) {
            OrderStatusStep.CONFIRMED  -> "Confirmado"
            OrderStatusStep.PREPARING  -> "En preparación"
            OrderStatusStep.READY      -> "Listo para recoger"
            OrderStatusStep.IN_TRANSIT -> "En camino"
            OrderStatusStep.DELIVERED  -> "Entregado"
        }
    }

    fun description(o: ActiveOrder): String = when {
        o.rawStatus == "cancelled" -> when (o.cancelReason) {
            "TRANSFER_TIMEOUT"  -> "Se canceló porque no recibimos el comprobante de transferencia a tiempo."
            "RECETA_RECHAZADA"  -> "El negocio no aprobó la receta, por eso se canceló el pedido."
            else                -> "El pedido fue cancelado."
        }
        o.rawStatus == "pending" && o.prescriptionRequired && o.prescriptionStatus == "pending" ->
            "El negocio revisa tu receta antes de aceptar el pedido."
        o.rawStatus == "pending" && o.paymentMethod == "transfer" -> when (o.paymentStatus) {
            "verifying" -> "Estamos verificando tu comprobante."
            "rejected"  -> "Tu comprobante fue rechazado. Sube uno nuevo antes de que venza el plazo."
            else        -> "Sube el comprobante de tu transferencia para que podamos continuar."
        }
        o.rawStatus == "pending"  -> "El negocio todavía no confirma tu pedido."
        o.rawStatus == "accepted" -> "El negocio confirmó tu pedido."
        else -> when (o.statusStep) {
            OrderStatusStep.CONFIRMED  -> "El negocio confirmó tu pedido."
            OrderStatusStep.PREPARING  -> "Tu pedido se está preparando."
            OrderStatusStep.READY      -> "Esperando que el repartidor lo recoja."
            OrderStatusStep.IN_TRANSIT -> "El repartidor está en camino."
            OrderStatusStep.DELIVERED  -> "¡Tu pedido fue entregado!"
        }
    }
}
