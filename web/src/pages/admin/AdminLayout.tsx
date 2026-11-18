import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { getMyProfile, type Profile } from '../../lib/supabase'
import AppLayout from '../../components/layout/AppLayout'

export default function AdminLayout() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getMyProfile().then(p => {
      setProfile(p)
      setLoading(false)
    })
  }, [])

  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center bg-[var(--color-bg)]">
        <div className="skeleton w-64 h-48" />
      </div>
    )
  }

  return (
    <AppLayout role="admin" userName={profile?.full_name ?? 'Admin'}>
      <Outlet />
    </AppLayout>
  )
}
