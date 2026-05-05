import { Session } from '@supabase/supabase-js'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { supabase } from '../lib/supabase'
import { UserProfile } from '../hooks/useRole'
import NavBar from '../components/NavBar'

interface Props {
  session: Session
  profile: UserProfile
}

interface PatientLink { patient_id: string }
interface PatientProfile { id: string; full_name: string | null; email: string | null }
interface LatestHR { bpm: number; device_timestamp: number }
interface LatestSpO2 { percent: number; device_timestamp: number }
interface LatestActivity { steps: number }

function formatTimestamp(ms: number): string {
  return new Date(ms).toLocaleString('en-AU', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function patientName(profile: PatientProfile | undefined, id: string): string {
  if (!profile) return id.slice(0, 8) + '…'
  return profile.full_name?.trim() || profile.email || id.slice(0, 8) + '…'
}

function PatientRow({ patientId, profile }: { patientId: string; profile: PatientProfile | undefined }) {
  const { data: latestHR } = useQuery<LatestHR | null>({
    queryKey: ['latest-hr', patientId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_heart_rate')
        .select('bpm, device_timestamp')
        .eq('user_id', patientId)
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data
    },
  })

  const { data: latestSpO2 } = useQuery<LatestSpO2 | null>({
    queryKey: ['latest-spo2', patientId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_spo2')
        .select('percent, device_timestamp')
        .eq('user_id', patientId)
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data
    },
  })

  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)

  const { data: todayActivity } = useQuery<LatestActivity | null>({
    queryKey: ['today-steps', patientId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_activity')
        .select('steps, device_timestamp')
        .eq('user_id', patientId)
        .gte('device_timestamp', todayStart.getTime())
        .order('device_timestamp', { ascending: false })
        .limit(1)
        .maybeSingle()
      if (error) throw error
      return data
    },
  })

  const lastSync = latestHR?.device_timestamp

  return (
    <tr className="border-b border-stone-100 hover:bg-stone-50 transition-colors">
      <td className="py-4 px-5 text-base font-medium text-stone-900">
        {patientName(profile, patientId)}
      </td>
      <td className="py-4 px-5 text-base text-stone-600">
        {lastSync ? formatTimestamp(lastSync) : <span className="text-stone-400">—</span>}
      </td>
      <td className="py-4 px-5 text-base text-stone-700">
        {latestHR ? `${latestHR.bpm} bpm` : <span className="text-stone-400">—</span>}
      </td>
      <td className="py-4 px-5 text-base text-stone-700">
        {latestSpO2 ? `${latestSpO2.percent}% SpO2` : <span className="text-stone-400">—</span>}
      </td>
      <td className="py-4 px-5 text-base text-stone-700">
        {todayActivity
          ? `${todayActivity.steps.toLocaleString()} steps`
          : <span className="text-stone-400">—</span>
        }
      </td>
      <td className="py-4 px-5">
        <Link
          to={`/dashboard/${patientId}`}
          className="inline-flex items-center justify-center min-h-[44px] px-5 bg-amber-600 hover:bg-amber-700 text-white text-base font-medium rounded-xl transition-colors"
        >
          View
        </Link>
      </td>
    </tr>
  )
}

export default function DoctorList({ session, profile }: Props) {
  const doctorId = session.user.id

  const { data: links, isLoading: linksLoading } = useQuery<PatientLink[]>({
    queryKey: ['doctor-patients', doctorId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('doctor_patient')
        .select('patient_id')
        .eq('doctor_id', doctorId)
      if (error) throw error
      return (data ?? []) as PatientLink[]
    },
  })

  const patientIds = (links ?? []).map((l) => l.patient_id)

  const { data: patientProfiles } = useQuery<PatientProfile[]>({
    queryKey: ['patient-profiles', patientIds],
    enabled: patientIds.length > 0,
    queryFn: async () => {
      const { data, error } = await supabase
        .from('user_profiles')
        .select('id, full_name, email')
        .in('id', patientIds)
      if (error) throw error
      return (data ?? []) as PatientProfile[]
    },
  })

  const profileMap = Object.fromEntries(
    (patientProfiles ?? []).map((p) => [p.id, p]),
  )

  return (
    <div className="min-h-screen bg-stone-50">
      <NavBar userName={profile.full_name} role="doctor" />

      <div className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-stone-900">My Patients</h1>
          <p className="text-base text-stone-500 mt-1">
            Showing all patients assigned to you
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
          {linksLoading && (
            <p className="text-base text-stone-400 py-16 text-center">Loading patient list…</p>
          )}

          {!linksLoading && (!links || links.length === 0) && (
            <p className="text-base text-stone-500 py-16 text-center italic px-6">
              No patients assigned yet. Contact your clinic administrator to link patients to your account.
            </p>
          )}

          {!linksLoading && links && links.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-stone-200 bg-stone-50">
                    <th className="py-4 px-5 text-left text-sm font-semibold text-stone-500 uppercase tracking-wide">
                      Patient
                    </th>
                    <th className="py-4 px-5 text-left text-sm font-semibold text-stone-500 uppercase tracking-wide">
                      Last Sync
                    </th>
                    <th className="py-4 px-5 text-left text-sm font-semibold text-stone-500 uppercase tracking-wide">
                      Heart Rate
                    </th>
                    <th className="py-4 px-5 text-left text-sm font-semibold text-stone-500 uppercase tracking-wide">
                      SpO2
                    </th>
                    <th className="py-4 px-5 text-left text-sm font-semibold text-stone-500 uppercase tracking-wide">
                      Steps Today
                    </th>
                    <th className="py-4 px-5 text-left text-sm font-semibold text-stone-500 uppercase tracking-wide">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {links.map((link) => (
                    <PatientRow
                      key={link.patient_id}
                      patientId={link.patient_id}
                      profile={profileMap[link.patient_id]}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
