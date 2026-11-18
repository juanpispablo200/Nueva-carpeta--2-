import { X, CheckCircle, AlertCircle, Info, AlertTriangle } from 'lucide-react'

export interface ToastData {
  id:      number
  type:    'success' | 'error' | 'info' | 'warn'
  message: string
}

const STYLES = {
  success: { bg: 'bg-emerald-50 border-emerald-200', text: 'text-emerald-800', Icon: CheckCircle,   iconCls: 'text-emerald-500' },
  error:   { bg: 'bg-red-50 border-red-200',         text: 'text-red-800',     Icon: AlertCircle,   iconCls: 'text-red-500'     },
  info:    { bg: 'bg-blue-50 border-blue-200',        text: 'text-blue-800',    Icon: Info,          iconCls: 'text-blue-500'    },
  warn:    { bg: 'bg-yellow-50 border-yellow-200',    text: 'text-yellow-800',  Icon: AlertTriangle, iconCls: 'text-yellow-500'  },
}

export default function Toast({ data, onDismiss }: { data: ToastData; onDismiss: () => void }) {
  const s = STYLES[data.type]
  return (
    <div
      role="alert"
      className={`flex items-start gap-3 px-4 py-3 rounded-[var(--radius-lg)] border shadow-[var(--shadow-lg)] animate-in ${s.bg}`}
    >
      <s.Icon size={16} className={`shrink-0 mt-0.5 ${s.iconCls}`} aria-hidden />
      <p className={`flex-1 text-sm font-medium ${s.text}`}>{data.message}</p>
      <button
        onClick={onDismiss}
        aria-label="Cerrar notificación"
        className={`shrink-0 ${s.text} hover:opacity-70 transition-opacity`}
      >
        <X size={14} />
      </button>
    </div>
  )
}
