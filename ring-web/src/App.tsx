import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Session } from '@supabase/supabase-js'
import { supabase } from './lib/supabase'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'

export default function App() {
  const [session, setSession] = useState<Session | null | undefined>(undefined)

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session))
    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, s) => setSession(s))
    return () => subscription.unsubscribe()
  }, [])

  // Still loading
  if (session === undefined) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <span className="text-gray-400">Loading…</span>
      </div>
    )
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/login"
          element={session ? <Navigate to="/" replace /> : <Login />}
        />
        <Route
          path="/*"
          element={session ? <Dashboard session={session} /> : <Navigate to="/login" replace />}
        />
      </Routes>
    </BrowserRouter>
  )
}
