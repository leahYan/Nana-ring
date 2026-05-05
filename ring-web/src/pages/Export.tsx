import { useState } from 'react'
import { Session } from '@supabase/supabase-js'
import { useQuery } from '@tanstack/react-query'
import { supabase } from '../lib/supabase'
import { useRole } from '../hooks/useRole'
import NavBar from '../components/NavBar'

interface Props { session: Session }

interface PatientLink { patient_id: string }
interface PatientProfile { id: string; full_name: string | null; email: string | null }

const METRICS = [
  { id: 'heart_rate', label: 'Heart Rate' },
  { id: 'spo2', label: 'Blood Oxygen (SpO2)' },
  { id: 'blood_pressure', label: 'Blood Pressure' },
  { id: 'temperature', label: 'Temperature' },
  { id: 'steps', label: 'Steps / Activity' },
  { id: 'sleep', label: 'Sleep' },
  { id: 'sport_sessions', label: 'Sport Sessions' },
]

function patientLabel(profile: PatientProfile | undefined, id: string): string {
  if (!profile) return id.slice(0, 8) + '…'
  return profile.full_name?.trim() || profile.email || id.slice(0, 8) + '…'
}

export default function Export({ session }: Props) {
  const { data: profile } = useRole(session)
  const isDoctor = profile?.role === 'doctor'

  const today = new Date().toISOString().slice(0, 10)
  const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

  const [startDate, setStartDate] = useState(thirtyDaysAgo)
  const [endDate, setEndDate] = useState(today)
  const [selectedMetrics, setSelectedMetrics] = useState<string[]>(['heart_rate', 'spo2'])
  const [selectedPatient, setSelectedPatient] = useState<string>('')
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  // Doctor: fetch linked patients for the dropdown
  const { data: links } = useQuery<PatientLink[]>({
    queryKey: ['doctor-patients-export', session.user.id],
    enabled: isDoctor,
    queryFn: async () => {
      const { data, error } = await supabase
        .from('doctor_patient')
        .select('patient_id')
        .eq('doctor_id', session.user.id)
      if (error) throw error
      return (data ?? []) as PatientLink[]
    },
  })

  const patientIds = (links ?? []).map((l) => l.patient_id)

  const { data: patientProfiles } = useQuery<PatientProfile[]>({
    queryKey: ['patient-profiles-export', patientIds],
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

  function toggleMetric(id: string) {
    setSelectedMetrics((prev) =>
      prev.includes(id) ? prev.filter((m) => m !== id) : [...prev, id],
    )
  }

  async function handleDownload() {
    setError(null)
    setSuccess(false)

    if (!startDate || !endDate) {
      setError('Please select a start and end date.')
      return
    }
    if (startDate > endDate) {
      setError('Start date must be before end date.')
      return
    }
    if (selectedMetrics.length === 0) {
      setError('Please select at least one metric.')
      return
    }
    if (isDoctor && !selectedPatient) {
      setError('Please select a patient.')
      return
    }

    const apiBase = import.meta.env.VITE_API_BASE_URL
    if (!apiBase) {
      setError('VITE_API_BASE_URL is not configured. Please check your environment settings.')
      return
    }

    const userId = isDoctor ? selectedPatient : session.user.id

    setDownloading(true)
    try {
      const { data: sessionData } = await supabase.auth.getSession()
      const token = sessionData.session?.access_token

      const params = new URLSearchParams({
        user_id: userId,
        start: startDate,
        end: endDate,
        metrics: selectedMetrics.join(','),
      })

      const res = await fetch(`${apiBase}/export/csv?${params.toString()}`, {
        headers: { Authorization: `Bearer ${token}` },
      })

      if (!res.ok) {
        throw new Error(`Export failed: ${res.status} ${res.statusText}`)
      }

      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `nana-ring-${startDate}-to-${endDate}.csv`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      setSuccess(true)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setDownloading(false)
    }
  }

  return (
    <div className="min-h-screen bg-stone-50">
      <NavBar userName={profile?.full_name ?? null} role={profile?.role ?? ''} />

      <div className="max-w-2xl mx-auto px-6 py-10">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-stone-900">Export Health Data</h1>
          <p className="text-base text-stone-500 mt-1">
            Download a CSV file of health readings for a date range.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-stone-200 shadow-sm p-8 space-y-7">
          {/* Doctor: patient selector */}
          {isDoctor && (
            <div>
              <label className="block text-base font-semibold text-stone-700 mb-2">
                Patient
              </label>
              {patientIds.length === 0 ? (
                <p className="text-base text-stone-400 italic">No patients assigned to your account.</p>
              ) : (
                <select
                  value={selectedPatient}
                  onChange={(e) => setSelectedPatient(e.target.value)}
                  className="w-full min-h-[52px] border border-stone-300 rounded-xl px-4 text-base text-stone-900 focus:outline-none focus:ring-2 focus:ring-amber-500 bg-white"
                >
                  <option value="">Select a patient…</option>
                  {patientIds.map((id) => (
                    <option key={id} value={id}>
                      {patientLabel(profileMap[id], id)}
                    </option>
                  ))}
                </select>
              )}
            </div>
          )}

          {/* Date range */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-base font-semibold text-stone-700 mb-2">
                Start date
              </label>
              <input
                type="date"
                value={startDate}
                max={today}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full min-h-[52px] border border-stone-300 rounded-xl px-4 text-base text-stone-900 focus:outline-none focus:ring-2 focus:ring-amber-500"
              />
            </div>
            <div>
              <label className="block text-base font-semibold text-stone-700 mb-2">
                End date
              </label>
              <input
                type="date"
                value={endDate}
                max={today}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full min-h-[52px] border border-stone-300 rounded-xl px-4 text-base text-stone-900 focus:outline-none focus:ring-2 focus:ring-amber-500"
              />
            </div>
          </div>

          {/* Metric checkboxes */}
          <div>
            <p className="text-base font-semibold text-stone-700 mb-3">Metrics to include</p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {METRICS.map((m) => (
                <label
                  key={m.id}
                  className="flex items-center gap-3 min-h-[44px] cursor-pointer select-none"
                >
                  <input
                    type="checkbox"
                    checked={selectedMetrics.includes(m.id)}
                    onChange={() => toggleMetric(m.id)}
                    className="w-5 h-5 rounded accent-amber-600 cursor-pointer"
                  />
                  <span className="text-base text-stone-700">{m.label}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Feedback */}
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-xl px-4 py-3">
              <p className="text-base text-red-700">{error}</p>
            </div>
          )}
          {success && (
            <div className="bg-green-50 border border-green-200 rounded-xl px-4 py-3">
              <p className="text-base text-green-700 font-medium">
                ✓ Download started. Check your downloads folder.
              </p>
            </div>
          )}

          {/* Download button */}
          <button
            onClick={handleDownload}
            disabled={downloading}
            className="w-full min-h-[52px] bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white text-lg font-bold rounded-xl transition-colors"
          >
            {downloading ? 'Preparing download…' : 'Download CSV'}
          </button>
        </div>
      </div>
    </div>
  )
}
