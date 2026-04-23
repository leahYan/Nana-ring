import { useState } from 'react'
import { Session } from '@supabase/supabase-js'
import { useQuery } from '@tanstack/react-query'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { supabase } from '../lib/supabase'

interface Props { session: Session }

interface DoctorPatientLink { patient_id: string }
interface HeartRateRow {
  device_timestamp: number
  bpm: number
}

function formatTs(ms: number) {
  return new Date(ms).toLocaleDateString('en-AU', { month: 'short', day: 'numeric' })
}

export default function Dashboard({ session }: Props) {
  const doctorId = session.user.id
  const [selectedPatient, setSelectedPatient] = useState<string | null>(null)

  // Fetch patients linked to this doctor (TC-WEB-04).
  // RLS on doctor_patient returns only rows where doctor_id = auth.uid().
  const { data: links, isLoading: linksLoading } = useQuery({
    queryKey: ['doctor-patients', doctorId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('doctor_patient')
        .select('patient_id')
        .eq('doctor_id', doctorId)
      if (error) throw error
      return data as DoctorPatientLink[]
    },
  })

  // Fetch heart rate for selected patient (last 7 days).
  // RLS is_linked_doctor() enforces access — unlinked patients return zero rows (TC-WEB-05).
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

  return (
    <div className="min-h-screen p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-white">Nana Ring Dashboard</h1>
        <button
          onClick={handleSignOut}
          className="text-xs text-gray-400 hover:text-white transition-colors"
        >
          Sign out
        </button>
      </div>

      {/* Patient list (TC-WEB-04) */}
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
              className={`px-3 py-1.5 rounded-lg text-xs font-mono transition-colors ${
                selectedPatient === link.patient_id
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
              }`}
            >
              {link.patient_id.slice(0, 8)}…
            </button>
          ))}
        </div>
      </section>

      {/* Heart rate chart */}
      {selectedPatient && (
        <section className="bg-gray-900 rounded-2xl p-5">
          <h2 className="text-sm font-medium text-gray-300 mb-4">
            Heart Rate — last 7 days <span className="text-xs text-gray-500">(bpm)</span>
          </h2>

          {hrLoading && <p className="text-xs text-gray-500">Loading…</p>}

          {/* Empty state (TC-WEB-01 / TC-WEB-05) — never show fake data */}
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
