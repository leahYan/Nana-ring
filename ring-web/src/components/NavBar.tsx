import { Link } from 'react-router-dom'
import { supabase } from '../lib/supabase'

interface Props {
  userName: string | null
  role: string
}

export default function NavBar({ userName, role }: Props) {
  async function handleSignOut() {
    await supabase.auth.signOut()
  }

  return (
    <nav className="bg-white border-b border-stone-200">
      <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-8">
          <Link to="/dashboard" className="text-xl font-bold text-amber-700">
            Nana Ring
          </Link>
          <Link
            to="/export"
            className="text-base text-stone-600 hover:text-stone-900 transition-colors"
          >
            Export Data
          </Link>
        </div>
        <div className="flex items-center gap-4">
          {userName && (
            <span className="text-base text-stone-600">
              {role === 'doctor' ? 'Dr. ' : ''}
              {userName}
            </span>
          )}
          <button
            onClick={handleSignOut}
            className="min-h-[44px] px-5 bg-stone-100 hover:bg-stone-200 text-stone-700 text-base font-medium rounded-xl transition-colors"
          >
            Sign Out
          </button>
        </div>
      </div>
    </nav>
  )
}
