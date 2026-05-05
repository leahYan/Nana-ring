import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { supabase } from '../lib/supabase'

/*
  consultation_reminders table is not yet in Supabase.
  To enable this feature, run the following SQL in the Supabase SQL Editor:

  create table consultation_reminders (
    id         uuid primary key default gen_random_uuid(),
    doctor_id  uuid references auth.users(id),
    patient_id uuid references auth.users(id),
    message    text not null,
    is_read    boolean default false,
    created_at timestamptz default now()
  );
  alter table consultation_reminders enable row level security;
  create policy "Patients read own reminders"
    on consultation_reminders for select
    using (patient_id = auth.uid());
  create policy "Doctors insert for linked patients"
    on consultation_reminders for insert
    with check (doctor_id = auth.uid() and is_linked_doctor(patient_id));
  create policy "Patients dismiss own reminders"
    on consultation_reminders for update
    using (patient_id = auth.uid())
    with check (patient_id = auth.uid());
*/

interface Reminder {
  id: string
  message: string
  created_at: string
}

interface Props {
  userId: string
}

export default function ConsultationBanner({ userId }: Props) {
  const queryClient = useQueryClient()

  const { data: reminder } = useQuery({
    queryKey: ['consultation-reminder', userId],
    queryFn: async (): Promise<Reminder | null> => {
      const { data, error } = await supabase
        .from('consultation_reminders')
        .select('id, message, created_at')
        .eq('patient_id', userId)
        .eq('is_read', false)
        .order('created_at', { ascending: false })
        .limit(1)
        .maybeSingle()
      // Table does not exist yet — treat as no reminder
      if (error?.code === '42P01') return null
      if (error) throw error
      return data
    },
    retry: false,
  })

  const dismiss = useMutation({
    mutationFn: async () => {
      if (!reminder) return
      const { error } = await supabase
        .from('consultation_reminders')
        .update({ is_read: true })
        .eq('id', reminder.id)
      if (error) throw error
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['consultation-reminder', userId] })
    },
  })

  if (!reminder) return null

  return (
    <div className="bg-amber-50 border border-amber-300 rounded-2xl p-5 flex items-start justify-between gap-4">
      <div className="flex-1 min-w-0">
        <p className="text-base font-semibold text-amber-900 mb-1">📅 Consultation Reminder</p>
        <p className="text-base text-amber-800 leading-relaxed">{reminder.message}</p>
      </div>
      <button
        onClick={() => dismiss.mutate()}
        disabled={dismiss.isPending}
        className="shrink-0 min-h-[44px] px-5 bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white text-base font-medium rounded-xl transition-colors"
      >
        {dismiss.isPending ? 'Dismissing…' : 'Dismiss'}
      </button>
    </div>
  )
}
