import {
  createContext, useCallback, useContext,
  useRef, useState, type ReactNode,
} from 'react'
import Toast, { type ToastData } from './Toast'

interface ToastCtx {
  success: (msg: string) => void
  error:   (msg: string) => void
  info:    (msg: string) => void
  warn:    (msg: string) => void
}

const Ctx = createContext<ToastCtx | null>(null)

let _id = 0

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastData[]>([])
  const timers = useRef<Record<number, ReturnType<typeof setTimeout>>>({})

  const dismiss = useCallback((id: number) => {
    clearTimeout(timers.current[id])
    setToasts(t => t.filter(x => x.id !== id))
  }, [])

  const add = useCallback((type: ToastData['type'], message: string) => {
    const id = ++_id
    setToasts(t => [...t, { id, type, message }])
    timers.current[id] = setTimeout(() => dismiss(id), 4000)
  }, [dismiss])

  return (
    <Ctx.Provider value={{
      success: m => add('success', m),
      error:   m => add('error',   m),
      info:    m => add('info',    m),
      warn:    m => add('warn',    m),
    }}>
      {children}
      {/* Portal de toasts — esquina inferior derecha */}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 w-80 max-w-[calc(100vw-2rem)]"
      >
        {toasts.map(t => (
          <Toast key={t.id} data={t} onDismiss={() => dismiss(t.id)} />
        ))}
      </div>
    </Ctx.Provider>
  )
}

export function useToast(): ToastCtx {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useToast debe usarse dentro de <ToastProvider>')
  return ctx
}
