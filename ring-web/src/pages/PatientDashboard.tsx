import { Session } from '@supabase/supabase-js'
import { UserProfile, displayName } from '../hooks/useRole'
import NavBar from '../components/NavBar'
import ConsultationBanner from '../components/ConsultationBanner'
import HealthMetrics from '../components/HealthMetrics'

interface Props {
  session: Session
  profile: UserProfile
}

export default function PatientDashboard({ session, profile }: Props) {
  const userId = session.user.id

  return (
    <div className="min-h-screen bg-stone-50">
      <NavBar userName={profile.full_name} role="patient" />

      <div className="max-w-6xl mx-auto px-6 py-8 space-y-8">
        <div>
          <h1 className="text-3xl font-bold text-stone-900">
            Good to see you, {displayName(profile, userId).split(' ')[0]}
          </h1>
          <p className="text-base text-stone-500 mt-1">Your health summary</p>
        </div>

        <ConsultationBanner userId={userId} />

        <HealthMetrics userId={userId} />
      </div>
    </div>
  )
}
