import { Session } from '@supabase/supabase-js'
import { useRole } from '../hooks/useRole'
import PatientDashboard from './PatientDashboard'
import DoctorList from './DoctorList'

interface Props { session: Session }

export default function Dashboard({ session }: Props) {
  const { data: profile, isLoading, error } = useRole(session)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-stone-50">
        <p className="text-lg text-stone-500">Loading your dashboard…</p>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-stone-50 p-6">
        <p className="text-base text-red-600 text-center">
          Could not load your profile. Please try signing out and signing in again.
        </p>
      </div>
    )
  }

  if (profile.role === 'patient') {
    return <PatientDashboard session={session} profile={profile} />
  }

  if (profile.role === 'doctor') {
    return <DoctorList session={session} profile={profile} />
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-stone-50 p-6">
      <p className="text-base text-stone-500 text-center">
        Unrecognised account role "{profile.role}". Please contact your administrator.
      </p>
    </div>
  )
}
