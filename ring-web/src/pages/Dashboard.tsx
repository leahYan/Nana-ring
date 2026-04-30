import { useState } from 'react'
import { Session } from '@supabase/supabase-js'
import { useQuery } from '@tanstack/react-query'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { supabase } from '../lib/supabase'

interface Props { session: Session }

interface UserProfile { id: string; role: string; full_name: string | null; email: string | null }
interface DoctorPatientLink { patient_id: string }
interface PatientProfile { id: string; full_name: string | null; email: string | null }
interface HeartRateRow { device_timestamp: number; bpm: number }

function formatTs(ms: number) {
  return new Date(ms).toLocaleDateString('en-AU', { month: 'short', day: 'numeric' })
}

function patientLabel(profile: PatientProfile | undefined, id: string) {
  if (!profile) return id.slice(0, 8) + '…'
  return profile.full_name || profile.email || id.slice(0, 8) + '…'
}

export default function Dashboard({ session }: Props) {
  const userId = session.user.id
  const [selectedPatient, setSelectedPatient] = useState<string | null>(null)

  // Verify the signed-in user is a doctor.
  const { data: userProfile, isLoading: profileLoading } = useQuery({
    queryKey: ['user-profile', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('user_profiles')
        .select('id, role, full_name, email')
        .eq('id', userId)
        .single()
      if (error) throw error
      return data as UserProfile
    },
  })

  // Fetch patients linked to this doctor.
  const { data: links, isLoading: linksLoading } = useQuery({
    queryKey: ['doctor-patients', userId],
    enabled: userProfile?.role === 'doctor',
    queryFn: async () => {
      const { data, error } = await supabase
        .from('doctor_patient')
        .select('patient_id')
        .eq('doctor_id', userId)
      if (error) throw error
      return data as DoctorPatientLink[]
    },
  })

  // Fetch patient profiles for display names.
  const patientIds = links?.map(l => l.patient_id) ?? []
  const { data: patientProfiles } = useQuery({
    queryKey: ['patient-profiles', patientIds],
    enabled: patientIds.length > 0,
    queryFn: async () => {
      const { data, error } = await supabase
        .from('user_profiles')
        .select('id, full_name, email')
        .in('id', patientIds)
      if (error) throw error
      return data as PatientProfile[]
    },
  })

  const profileMap = Object.fromEntries((patientProfiles ?? []).map(p => [p.id, p]))

  // Fetch heart rate for selected patient (last 7 days).
  const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
  const { data: heartRates, isLoading: hrLoading } = useQuery({
    queryKey: ['heart-rate', selectedPatient],
    enabled: !!selectedPatient,
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_heart_rate')
        .select('device_timestamp, bpm')
        .eq('user_id', selectedPatient!)
        .gte('device_timestamp', sevenDaysAgo)
        .order('device_timestamp', { ascending: true })
      if (error) throw error
      return data as HeartRateRow[]
    },
  })

  const chartData = (heartRates ?? []).map(r => ({
    time: formatTs(r.device_timestamp),
    bpm: r.bpm,
  }))

  async function handleSignOut() {
    await supabase.auth.signOut()
  }

  if (profileLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-gray-400 text-sm">Loading…</p>
      </div>
    )
  }

  if (userProfile?.role !== 'doctor') {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4">
        <p className="text-red-400 text-sm">
          Access denied — this dashboard is for doctors only.
        </p>
        <button
          onClick={handleSignOut}
          className="text-xs text-gray-400 hover:text-white transition-colors"
        >
          Sign out
        </button>
      </div>
    )
  }

  return (
    <div className="min-h-screen p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Nana Ring Dashboard</h1>
          {userProfile.full_name && (
            <p className="text-xs text-gray-400 mt-0.5">Dr. {userProfile.full_name}</p>
          )}
        </div>
        <button
          onClick={handleSignOut}
          className="text-xs text-gray-400 hover:text-white transition-colors"
        >
          Sign out
        </button>
      </div>

      {/* Patient list */}
      <section className="bg-gray-900 rounded-2xl p-5">
        <h2 className="text-sm font-medium text-gray-300 mb-3">Patients</h2>
        {linksLoading && <p className="text-xs text-gray-500">Loading patients…</p>}
        {!linksLoading && (!links || links.length === 0) && (
          <p className="text-xs text-gray-500">No patients linked to your account yet.</p>
        )}
        <div className="flex flex-wrap gap-2">
          {links?.map(link => (
            <button
              key={link.patient_id}
              onClick={() => setSelectedPatient(link.patient_id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                selectedPatient === link.patient_id
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
              }`}
            >
              {patientLabel(profileMap[link.patient_id], link.patient_id)}
            </button>
          ))}
        </div>
      </section>

      {/* Heart rate chart */}
      {selectedPatient && (
        <section className="bg-gray-900 rounded-2xl p-5">
          <h2 className="text-sm font-medium text-gray-300 mb-4">
            Heart Rate — last 7 days{' '}
            <span className="text-xs text-gray-500">(bpm)</span>
            {profileMap[selectedPatient] && (
              <span className="ml-2 text-xs text-blue-400">
                {patientLabel(profileMap[selectedPatient], selectedPatient)}
              </span>
            )}
          </h2>

          {hrLoading && <p className="text-xs text-gray-500">Loading…</p>}

          {!hrLoading && chartData.length === 0 && (
            <p className="text-sm text-gray-500 py-8 text-center">
              Waiting for Data — no readings in the last 7 days
            </p>
          )}

          {!hrLoading && chartData.length > 0 && (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                <XAxis dataKey="time" tick={{ fill: '#9CA3AF', fontSize: 11 }} />
                <YAxis
                  domain={['auto', 'auto']}
                  unit=" bpm"
                  tick={{ fill: '#9CA3AF', fontSize: 11 }}
                />
                <Tooltip
                  contentStyle={{ background: '#1F2937', border: 'none', borderRadius: 8 }}
                  labelStyle={{ color: '#D1D5DB' }}
                  itemStyle={{ color: '#60A5FA' }}
                />
                <Line
                  type="monotone"
                  dataKey="bpm"
                  stroke="#3B82F6"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </section>
      )}

      {!selectedPatient && links && links.length > 0 && (
        <p className="text-sm text-gray-500 text-center pt-8">
          Select a patient above to view their health data.
        </p>
      )}
    </div>
  )
}
