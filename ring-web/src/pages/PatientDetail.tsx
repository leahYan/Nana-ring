import { useState } from 'react'
import { useParams, Link, Navigate } from 'react-router-dom'
import { Session } from '@supabase/supabase-js'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { supabase } from '../lib/supabase'
import { useRole } from '../hooks/useRole'
import NavBar from '../components/NavBar'
import HealthMetrics from '../components/HealthMetrics'
import ReminderModal from '../components/ReminderModal'

interface Props { session: Session }

interface PatientProfile { id: string; full_name: string | null; email: string | null }

export default function PatientDetail({ session }: Props) {
  const { userId: targetUserId } = useParams<{ userId: string }>()
  const [showModal, setShowModal] = useState(false)
  const [reminderSuccess, setReminderSuccess] = useState(false)
  const queryClient = useQueryClient()

  const { data: doctorProfile, isLoading: doctorLoading } = useRole(session)

  const { data: patientProfile } = useQuery<PatientProfile | null>({
    queryKey: ['patient-profile', targetUserId],
    enabled: !!targetUserId,
    queryFn: async () => {
      const { data, error } = await supabase
        .from('user_profiles')
        .select('id, full_name, email')
        .eq('id', targetUserId!)
        .maybeSingle()
      if (error) throw error
      return data
    },
  })

  const sendReminder = useMutation({
    mutationFn: async () => {
      const doctorName = doctorProfile?.full_name?.trim() || 'Your doctor'
      const message = `Dr. ${doctorName} is suggesting you book a 15-minute consultation with them to discuss your latest health changes.`

      // Mark any existing unread reminders from this doctor to this patient as read
      await supabase
        .from('consultation_reminders')
        .update({ is_read: true })
        .eq('doctor_id', session.user.id)
        .eq('patient_id', targetUserId!)
        .eq('is_read', false)

      // Insert new reminder
      const { error } = await supabase
        .from('consultation_reminders')
        .insert({
          doctor_id: session.user.id,
          patient_id: targetUserId!,
          message,
        })

      if (error) {
        if (error.code === '42P01') {
          throw new Error(
            'Consultation reminders require database setup. Please ask your administrator to create the consultation_reminders table in Supabase.',
          )
        }
        throw error
      }
    },
    onSuccess: () => {
      setShowModal(false)
      setReminderSuccess(true)
      queryClient.invalidateQueries({ queryKey: ['consultation-reminder', targetUserId] })
      setTimeout(() => setReminderSuccess(false), 5000)
    },
  })

  if (!targetUserId) {
    return <Navigate to="/dashboard" replace />
  }

  if (doctorLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-stone-50">
        <p className="text-lg text-stone-500">Loading…</p>
      </div>
    )
  }

  if (doctorProfile?.role !== 'doctor') {
    return <Navigate to="/dashboard" replace />
  }

  const pName =
    patientProfile?.full_name?.trim() ||
    patientProfile?.email ||
    targetUserId.slice(0, 8) + '…'
  const doctorName = doctorProfile?.full_name?.trim() || 'Doctor'

  return (
    <div className="min-h-screen bg-stone-50">
      <NavBar userName={doctorProfile.full_name} role="doctor" />

      <div className="max-w-6xl mx-auto px-6 py-8 space-y-8">
        {/* Page header */}
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-4 flex-wrap">
            <Link
              to="/dashboard"
              className="text-base text-amber-700 hover:text-amber-800 font-medium transition-colors"
            >
              ← Back to patients
            </Link>
            <div>
              <h1 className="text-3xl font-bold text-stone-900">{pName}</h1>
              <p className="text-base text-stone-500 mt-0.5">Patient health summary</p>
            </div>
          </div>
          <button
            onClick={() => { setReminderSuccess(false); setShowModal(true) }}
            className="min-h-[48px] px-6 bg-amber-600 hover:bg-amber-700 text-white text-base font-semibold rounded-xl transition-colors shadow-sm"
          >
            Send Consultation Reminder
          </button>
        </div>

        {/* Reminder feedback */}
        {reminderSuccess && (
          <div className="bg-green-50 border border-green-200 rounded-2xl px-5 py-4">
            <p className="text-base text-green-800 font-medium">
              ✓ Reminder sent. {pName} will see it on their next dashboard visit.
            </p>
          </div>
        )}
        {sendReminder.error && (
          <div className="bg-red-50 border border-red-200 rounded-2xl px-5 py-4">
            <p className="text-base text-red-700">{(sendReminder.error as Error).message}</p>
          </div>
        )}

        <HealthMetrics userId={targetUserId} />
      </div>

      {showModal && (
        <ReminderModal
          doctorName={doctorName}
          onConfirm={() => sendReminder.mutate()}
          onCancel={() => setShowModal(false)}
          isLoading={sendReminder.isPending}
        />
      )}
    </div>
  )
}
