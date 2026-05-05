import { useQuery } from '@tanstack/react-query'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts'
import { supabase } from '../lib/supabase'

interface SleepRow {
  start_timestamp: number
  deep_minutes: number
  light_minutes: number
  rem_minutes: number
  awake_minutes: number
  total_minutes: number
}

interface Props { userId: string }

export default function SleepPanel({ userId }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ['sleep-panel', userId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('ring_sleep')
        .select('start_timestamp, deep_minutes, light_minutes, rem_minutes, awake_minutes, total_minutes')
        .eq('user_id', userId)
        .order('start_timestamp', { ascending: false })
        .limit(7)
      if (error) throw error
      return ((data ?? []) as SleepRow[]).reverse()
    },
  })

  const chartData = (data ?? []).map((s) => ({
    date: new Date(s.start_timestamp).toLocaleDateString('en-AU', { month: 'short', day: 'numeric' }),
    Deep: s.deep_minutes,
    Light: s.light_minutes,
    REM: s.rem_minutes,
    Awake: s.awake_minutes,
  }))

  return (
    <section className="bg-white rounded-2xl border border-stone-200 p-6 shadow-sm">
      <div className="mb-5">
        <h2 className="text-xl font-semibold text-stone-800">Sleep</h2>
        <p className="text-sm text-stone-500 mt-0.5">Last 7 nights — minutes per stage</p>
      </div>

      {isLoading && (
        <p className="text-base text-stone-400 py-14 text-center">Loading…</p>
      )}

      {!isLoading && chartData.length === 0 && (
        <p className="text-base text-stone-400 py-14 text-center italic">
          No sleep data recorded yet. Make sure the ring app is syncing.
        </p>
      )}

      {!isLoading && chartData.length > 0 && (
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={chartData} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#E7E5E4" vertical={false} />
            <XAxis dataKey="date" tick={{ fill: '#78716C', fontSize: 13 }} />
            <YAxis
              unit=" min"
              tick={{ fill: '#78716C', fontSize: 13 }}
              width={65}
            />
            <Tooltip
              contentStyle={{ background: '#fff', border: '1px solid #E7E5E4', borderRadius: 12, fontSize: 14 }}
              formatter={(val: number, name: string) => [`${val} min`, name]}
            />
            <Legend wrapperStyle={{ fontSize: 13, paddingTop: 8 }} />
            <Bar dataKey="Deep" stackId="sleep" fill="#4C1D95" radius={[0, 0, 0, 0]} />
            <Bar dataKey="Light" stackId="sleep" fill="#7C3AED" />
            <Bar dataKey="REM" stackId="sleep" fill="#A78BFA" />
            <Bar dataKey="Awake" stackId="sleep" fill="#FCD34D" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </section>
  )
}
