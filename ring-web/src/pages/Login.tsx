import { FormEvent, useState } from 'react'
import { supabase } from '../lib/supabase'

export default function Login() {
  const [email, setEmail]     = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]     = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)

    const { error: authError } = await supabase.auth.signInWithPassword({ email, password })
    if (authError) setError(authError.message)

    setLoading(false)
  }

  return (
    <div className="flex items-center justify-center min-h-screen">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm bg-gray-900 rounded-2xl p-8 space-y-5 shadow-xl"
      >
        <h1 className="text-2xl font-semibold text-white">Nana Ring</h1>
        <p className="text-sm text-gray-400">Sign in with your doctor account</p>

        <div>
          <label className="block text-xs text-gray-400 mb-1">Email</label>
          <input
            type="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="w-full bg-gray-800 text-white rounded-lg px-3 py-2 text-sm border border-gray-700 focus:border-blue-500 outline-none"
          />
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1">Password</label>
          <input
            type="password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="w-full bg-gray-800 text-white rounded-lg px-3 py-2 text-sm border border-gray-700 focus:border-blue-500 outline-none"
          />
        </div>

        {error && <p className="text-red-400 text-xs">{error}</p>}

        <button
          type="submit"
          disabled={loading}
          className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white rounded-lg py-2 text-sm font-medium transition-colors"
        >
          {loading ? 'Signing in…' : 'Sign In'}
        </button>
      </form>
    </div>
  )
}
